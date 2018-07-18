package com.rockyrunstream.walmart;

/**
 * Optimistic lock approach failed. Client can repeat request
 */
public class OptimisticLockException extends ServiceException {

    public OptimisticLockException(String message) {
        super(message);
    }
}
