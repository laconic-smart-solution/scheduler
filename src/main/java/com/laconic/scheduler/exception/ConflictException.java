package com.laconic.scheduler.exception;

/**
 * Request conflicts with current state (e.g. cancel already terminal).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
