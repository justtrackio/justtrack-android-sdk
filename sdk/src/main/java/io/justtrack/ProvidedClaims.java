package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;

class ProvidedClaims implements Iterable<String> {
    private @Nullable String firstClaim;
    private @Nullable String secondClaim;
    private boolean timedOut;

    ProvidedClaims() {
        firstClaim = null;
        secondClaim = null;
        timedOut = false;
    }

    boolean isTimedOut() {
        return timedOut;
    }

    void setTimedOut() {
        this.timedOut = true;
    }

    void addClaim(@NonNull String claim) {
        if (firstClaim == null) {
            firstClaim = claim;
        } else if (secondClaim == null) {
            secondClaim = claim;
        } else {
            throw new IllegalStateException("Can not set more than two claims");
        }
    }

    @NonNull
    @Override
    public Iterator<String> iterator() {
        return new ClaimsIterator();
    }

    private class ClaimsIterator implements Iterator<String> {
        private int position = 0;

        @Override
        public boolean hasNext() {
            switch (position) {
                case 0:
                    return firstClaim != null;
                case 1:
                    return secondClaim != null;
                default:
                    return false;
            }
        }

        @Override
        public String next() {
            switch (position) {
                case 0:
                    if (firstClaim == null) {
                        throw new NoSuchElementException();
                    }
                    position++;

                    return firstClaim;
                case 1:
                    if (secondClaim == null) {
                        throw new NoSuchElementException();
                    }
                    position++;

                    return secondClaim;
                default:
                    throw new NoSuchElementException();
            }
        }
    }
}
