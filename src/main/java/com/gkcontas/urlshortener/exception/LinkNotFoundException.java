package com.gkcontas.urlshortener.exception;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String code) {
        super("Link not found with code %s".formatted(code));
    }
}
