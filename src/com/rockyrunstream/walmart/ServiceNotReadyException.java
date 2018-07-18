package com.rockyrunstream.walmart;

/**
 * Throws if service is not ready to process request.
 * Examples:
 *  - Invalid service configuration
 *  - Not initialized
 *  - Database connection failed
 *
 * Client can repeat request
 */
public class ServiceNotReadyException extends ServiceException {

    public ServiceNotReadyException(String message) {
        super(message);
    }
}
