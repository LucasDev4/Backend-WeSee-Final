package com.atlas.backend_atlas.exception;

/**
 * Lançada quando o serviço de IA (FastAPI/YOLO) demora mais do que o
 * tempo limite configurado (atlas.ai.connect-timeout-ms /
 * atlas.ai.read-timeout-ms) para responder.
 */
public class AiTimeoutException extends RuntimeException {

    public AiTimeoutException(String message) {
        super(message);
    }
}
