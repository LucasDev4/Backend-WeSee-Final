package com.atlas.backend_atlas.exception;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email já cadastrado: " + email);
    }
}
