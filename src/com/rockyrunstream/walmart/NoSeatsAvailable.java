package com.rockyrunstream.walmart;

/**
 * Requested amount of seats not available. Client can repeat request
 */
public class NoSeatsAvailable extends ServiceException {

    private int requested;
    private int available;
    private int pending;


    public NoSeatsAvailable(int requested, int available, int pending) {
        super(String.format("No seats available, requested %d, availabled %d, pending %d", requested, available, pending));
        this.requested = requested;
        this.available = available;
        this.pending = pending;
    }
}
