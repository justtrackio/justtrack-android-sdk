package io.justtrack

import org.junit.Assert.assertEquals
import org.junit.Test

internal class RingBufferTest {
    @Test
    fun `supports default capacity and evicts oldest values`() {
        val defaultBuffer = RingBuffer<Int>()
        defaultBuffer.add(1)
        assertEquals(listOf(1), defaultBuffer.getAllElements())

        val buffer = RingBuffer<Int>(2)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)

        assertEquals(listOf(2, 3), buffer.getAllElements())
    }
}
