package io.justtrack.integrations

internal fun interface ProxyHandler {
    @Throws(Exception::class)
    fun handle(args: Array<Any?>): Any?
}
