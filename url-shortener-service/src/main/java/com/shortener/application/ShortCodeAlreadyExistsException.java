package com.shortener.application;

public class ShortCodeAlreadyExistsException extends RuntimeException {

    public ShortCodeAlreadyExistsException(String shortCode) {
        super("Short code is already in use: " + shortCode);
    }
}
