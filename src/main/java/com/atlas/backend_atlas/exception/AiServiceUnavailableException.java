package com.atlas.backend_atlas.exception;

/**
 * Lançada quando o serviço de IA (FastAPI/YOLO) não pôde ser contatado
 * (fora do ar, recusando conexão, erro 5xx) ou devolveu um status de
 * erro ao processar a imagem.
 */
public class AiServiceUnavailableException extends RuntimeException {

    public AiServiceUnavailableException(String message) {
        super(message);
    }
}
