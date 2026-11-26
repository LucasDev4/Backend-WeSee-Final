package com.atlas.backend_atlas.exception;

/**
 * Lançada quando o serviço de IA (FastAPI/YOLO) responde com sucesso
 * (HTTP 2xx), mas o corpo da resposta vem vazio ou em um formato que
 * não pôde ser interpretado como detecções válidas.
 */
public class AiResponseException extends RuntimeException {

    public AiResponseException(String message) {
        super(message);
    }
}
