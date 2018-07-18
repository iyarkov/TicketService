package com.rockyrunstream.walmart;

/**
 * Request parameters are invalid. Client must not repeat request
 */
public class BadRequestException extends ServiceException {
    public BadRequestException(String message) {
        super(message);
    }
}
