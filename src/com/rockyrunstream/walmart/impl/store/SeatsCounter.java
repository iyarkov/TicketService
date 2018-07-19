package com.rockyrunstream.walmart.impl.store;

public class SeatsCounter {

    private int reserved;
    private int pending;

    public SeatsCounter(int reserved, int pending) {
        this.reserved = reserved;
        this.pending = pending;
    }

    public int getReserved() {
        return reserved;
    }

    public int getPending() {
        return pending;
    }

    public int getTotal() {
        return reserved + pending;
    }
}
