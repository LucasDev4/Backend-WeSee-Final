package com.atlas.backend_atlas.exception;

import org.springframework.http.HttpStatus;

/** Erro de regra de negócio dos vínculos; o GlobalExceptionHandler responde com o status indicado. */
public class VinculoException extends RuntimeException {

    private final HttpStatus status;

    public VinculoException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static VinculoException invalido(String msg) { return new VinculoException(HttpStatus.BAD_REQUEST, msg); }
    public static VinculoException proibido(String msg) { return new VinculoException(HttpStatus.FORBIDDEN, msg); }
    public static VinculoException naoEncontrado(String msg) { return new VinculoException(HttpStatus.NOT_FOUND, msg); }
    public static VinculoException conflito(String msg) { return new VinculoException(HttpStatus.CONFLICT, msg); }
    public static VinculoException tentativasExcessivas(String msg) { return new VinculoException(HttpStatus.TOO_MANY_REQUESTS, msg); }
}
