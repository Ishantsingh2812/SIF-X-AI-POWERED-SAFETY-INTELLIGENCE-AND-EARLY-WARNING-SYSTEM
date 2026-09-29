package com.sih.sif.exception;

/**
 * AiServiceUnavailableException.java
 *
 * Thrown when the external Python FastAPI AI service cannot be reached,
 * times out, or returns an unrecoverable HTTP error.
 */
public class AiServiceUnavailableException extends RuntimeException {

    public AiServiceUnavailableException(String message) {
        super(message);
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
