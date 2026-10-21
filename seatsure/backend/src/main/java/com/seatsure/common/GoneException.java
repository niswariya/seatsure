package com.seatsure.common;

/** 410: the resource existed but is no longer usable (e.g. an expired seat hold). */
public class GoneException extends RuntimeException {
    public GoneException(String message) {
        super(message);
    }
}
