package com.shortener.application;

/** Raised when the redirect path is degraded (e.g. database circuit breaker open). */
public class ServiceTemporarilyUnavailableException extends RuntimeException {

    public ServiceTemporarilyUnavailableException(String message) {
        super(message);
    }
}
