package io.justtrack

internal fun interface Transformer<A, B> {
    fun transform(value: A): B
}
