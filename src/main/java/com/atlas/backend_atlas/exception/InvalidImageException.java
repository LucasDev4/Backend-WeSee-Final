package com.atlas.backend_atlas.exception;

/**
 * Lançada quando a imagem enviada em POST /detect está ausente, vazia,
 * corrompida ou em um formato não suportado (fora de JPG/PNG).
 */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
