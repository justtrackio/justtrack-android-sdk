package io.justtrack

import android.os.ConditionVariable
import io.justtrack.okhttp.Request
import io.justtrack.okhttp.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.internal.closeQuietly
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.net.ServerSocketFactory
import kotlin.concurrent.thread
import kotlin.random.Random

internal class OkHttpClientExecutorTest {
    private var server = MockWebServer()

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun test_connection_timeout(): Unit = runBlocking {
        // create a socket which doesn't accept any connections
        val serverSocket = withContext(Dispatchers.IO) {
            val inetSocketAddress = InetSocketAddress(InetAddress.getByName("localhost"), 0)
            val serverSocket = ServerSocketFactory.getDefault().createServerSocket()

            serverSocket.reuseAddress = false
            serverSocket.bind(inetSocketAddress, 1)
            serverSocket
        }

        // connect some times to the socket to exhaust its backlog
        val waitingSockets = ArrayList<ConditionVariable>()
        for (i in 0..2) {
            val cond = ConditionVariable()
            waitingSockets.add(cond)
            thread {
                val socket = Socket()
                cond.open()
                try {
                    socket.connect(
                        InetSocketAddress(
                            serverSocket.inetAddress,
                            serverSocket.localPort,
                        ),
                        1000,
                    )
                } catch (e: java.lang.Exception) {
                    // ignore timeout exception, we just want to starve the server socket of backlog slots
                }
                socket.closeQuietly()
            }
        }
        // wait for all connection attempts to be attempted
        waitingSockets.forEach { it.block() }

        val url = "http://${serverSocket.inetAddress.hostAddress}:${serverSocket.localPort}/"

        val executor = OkHttpClientExecutor(connectionTimeout = 100L, consoleLogger = null)

        val request = Request.Builder().url(url).build()

        executor.sendRequest(request)
            .onSuccess {
                Assert.fail("Didn't expect to get a response")
            }
            .onFailure { e ->
                e.printStackTrace()
                Assert.assertTrue(
                    "Unexpected exception: " + e.javaClass.canonicalName,
                    e is SocketTimeoutException,
                )
            }

        serverSocket.closeQuietly()
    }

    @Test
    fun test_read_timeout(): Unit = runBlocking {
        val executor = OkHttpClientExecutor(readTimeout = 100L, consoleLogger = null)
        val request = Request.Builder().url(server.url("/").toString()).build()

        launch {
            getRequest()
            delay(300)
            server.enqueue(MockResponse())
        }

        executor.sendRequest(request)
            .onSuccess {
                Assert.fail("Didn't expect to get a response")
            }
            .onFailure { e ->
                e.printStackTrace()
                Assert.assertTrue(e is SocketTimeoutException)
                // Somehow TimeoutException could produce different message
                val expectedMessage = listOf("Read timed out", "timeout")
                Assert.assertTrue(expectedMessage.contains(e.message))
            }
    }

    @Test
    fun test_write_timeout(): Unit = runBlocking {
        // create a socket which doesn't accept any connections
        val serverSocket = withContext(Dispatchers.IO) {
            val inetSocketAddress = InetSocketAddress(InetAddress.getByName("localhost"), 0)
            val serverSocket = ServerSocketFactory.getDefault().createServerSocket()

            serverSocket.receiveBufferSize = 8 * 1024
            serverSocket.reuseAddress = false
            serverSocket.bind(inetSocketAddress)
            serverSocket
        }
        val url = "http://${serverSocket.inetAddress.hostAddress}:${serverSocket.localPort}/"
        val executor = OkHttpClientExecutor(writeTimeout = 100L, consoleLogger = null)

        val request = Request.Builder()
            .url(url)
            .method("POST", getVeryLongJSON(1024 * 1024 * 5).toRequestBody())
            .build()

        launch {
            withContext(Dispatchers.IO) {
                val client = serverSocket.accept()
                val expectedPrefix = "POST / HTTP/1.1\r\n"
                val prefix = ByteArray(expectedPrefix.length)
                val inputStream = client.getInputStream()
                for (i in prefix.indices) {
                    val read = inputStream.read()
                    Assert.assertNotEquals(-1, read)
                    prefix[i] = read.toByte()
                }
                val prefixString = String(prefix, Charsets.UTF_8)
                Assert.assertEquals(expectedPrefix, prefixString)

                var eofReached = false
                while (!eofReached) {
                    delay(10)
                    repeat(8 * 1024) {
                        eofReached = inputStream.read() == -1
                    }
                }

                client.closeQuietly()
            }
        }

        executor.sendRequest(request)
            .onSuccess {
                Assert.fail("Expected the request to fail")
            }
            .onFailure { e ->
                Assert.assertTrue(
                    "Unexpected exception: " + e.javaClass.canonicalName,
                    e is SocketTimeoutException,
                )
                Assert.assertTrue(e.message!!.contains("timeout"))
            }

        serverSocket.closeQuietly()
    }

    @Test
    fun test_write_complete(): Unit = runBlocking {
        val body = getVeryLongJSON(1024 * 1024 * 10)
        val executor = OkHttpClientExecutor(writeTimeout = 10000, consoleLogger = null)

        val request = Request.Builder()
            .url(server.url("/").toString())
            .method("POST", body.toRequestBody())
            .build()

        launch {
            val remoteRequest = getRequest()
            val compressedBody = remoteRequest.body.readByteArray()

            @Suppress("BlockingMethodInNonBlockingContext")
            val uncompressedBody = GZIPInputStream(ByteArrayInputStream(compressedBody)).readBytes()

            server.enqueue(
                MockResponse().setBody(
                    String(
                        uncompressedBody,
                        Charsets.UTF_8,
                    ).reversed(),
                ),
            )
        }
        executor.sendRequest(request)
            .onSuccess { response ->
                Assert.assertEquals(body, response.body?.string()?.reversed())
            }
            .onFailure { e ->
                Assert.fail("Unexpected failure: ${e.message}")
            }
    }

    private suspend fun getRequest(): RecordedRequest = withContext(Dispatchers.IO) {
        val remoteRequest = server.takeRequest(30, TimeUnit.SECONDS)
            ?: throw AssertionError("No request was received")

        remoteRequest
    }

    private fun getVeryLongJSON(wantedLength: Int): String {
        val sb = StringBuilder(wantedLength)
        sb.append("{\"data\":\"")
        while (sb.length < wantedLength - 2) {
            sb.append(Random.nextInt('0'.code, '9'.code + 1).toChar())
        }
        sb.append("\"}")

        return sb.toString()
    }
}
