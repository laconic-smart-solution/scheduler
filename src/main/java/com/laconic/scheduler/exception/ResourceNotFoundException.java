package com.laconic.scheduler.exception;

/**
 * Resource was not found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
