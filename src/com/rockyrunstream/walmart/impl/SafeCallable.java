package com.rockyrunstream.walmart.impl;

@FunctionalInterface
public interface SafeCallable<V> {
    /**
     * Computes a result without thrown checked exception
     *
     */
    V call() ;
}
