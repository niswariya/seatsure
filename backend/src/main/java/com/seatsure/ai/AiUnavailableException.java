package com.seatsure.ai;

/** Bedrock is down, throttling, or model access isn't granted. Mapped to 503. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
