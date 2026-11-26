package com.atlas.backend_atlas.exception;

/**
 * Lançada quando a imagem enviada em POST /detect excede o tamanho
 * máximo permitido (atlas.detection.max-image-size-bytes).
 */
public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException(String message) {
        super(message);
    }
}
