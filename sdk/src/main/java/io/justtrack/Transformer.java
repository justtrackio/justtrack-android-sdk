package io.justtrack;

interface Transformer<A, B> {
    B transform(A value);
}
