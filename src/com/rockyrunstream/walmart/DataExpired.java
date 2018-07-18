package com.rockyrunstream.walmart;

/**
 * Throws when seats hold expired. Client must start reservation process again
 */
public class DataExpired extends ServiceException {

    public DataExpired(String message) {
        super(message);
    }
}
