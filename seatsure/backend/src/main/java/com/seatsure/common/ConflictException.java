package com.seatsure.common;

/** 409: the request is valid but clashes with the current state (seat taken, duplicate email...). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
