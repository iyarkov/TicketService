package com.rockyrunstream.walmart;

/**
 * Unexpected problem. The client must not repeat request
 */
public class InternalServiceException extends ServiceException {

    public InternalServiceException(String message) {
        super(message);
    }

    public InternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
