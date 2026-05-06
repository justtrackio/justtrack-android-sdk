package io.justtrack

import androidx.collection.CircularArray

internal class RingBuffer<T>(private val capacity: Int = 100) {
    private val buffer = CircularArray<T>(capacity)

    fun add(element: T) {
        if (buffer.size() == capacity) {
            buffer.popFirst() // Remove the oldest element if the buffer is full
        }
        buffer.addLast(element)
    }

    fun getAllElements(): List<T> {
        val elements = mutableListOf<T>()
        for (i in 0 until buffer.size()) {
            elements.add(buffer.get(i))
        }
        return elements
    }
}
