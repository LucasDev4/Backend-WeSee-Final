package com.atlas.backend_atlas.exception;

import org.springframework.http.HttpStatus;

/**
 * Erro de busca de endereço / rota. A mensagem é feita para o usuário final (sem detalhes técnicos);
 * o GlobalExceptionHandler responde com o status indicado.
 */
public class GeoException extends RuntimeException {

    private final HttpStatus status;

    public GeoException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** 400: parâmetro ausente ou fora do intervalo. */
    public static GeoException invalido(String msg) { return new GeoException(HttpStatus.BAD_REQUEST, msg); }

    /** 404: endereço ou rota inexistente. */
    public static GeoException naoEncontrado(String msg) { return new GeoException(HttpStatus.NOT_FOUND, msg); }

    /** 429: limite de requisições (do nosso controle de frequência ou do provedor externo). */
    public static GeoException limite(String msg) { return new GeoException(HttpStatus.TOO_MANY_REQUESTS, msg); }

    /** 503: provedor indisponível (ou rota de pedestre não configurada). */
    public static GeoException indisponivel(String msg) { return new GeoException(HttpStatus.SERVICE_UNAVAILABLE, msg); }

    /** 504: o provedor não respondeu a tempo. */
    public static GeoException timeout(String msg) { return new GeoException(HttpStatus.GATEWAY_TIMEOUT, msg); }

    /** 502: o provedor respondeu algo que não conseguimos interpretar. */
    public static GeoException respostaInvalida(String msg) { return new GeoException(HttpStatus.BAD_GATEWAY, msg); }
}
