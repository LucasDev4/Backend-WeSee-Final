package com.atlas.backend_atlas.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VinculoExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapeiaStatusESemExporDetalhesInternos() {
        assertThat(handler.handleVinculo(VinculoException.proibido("Acesso negado.")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleVinculo(VinculoException.naoEncontrado("x")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleVinculo(VinculoException.conflito("x")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleVinculo(VinculoException.invalido("x")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> r = handler.handleVinculo(VinculoException.tentativasExcessivas("Muitas tentativas"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(r.getBody()).containsEntry("status", 429).containsEntry("message", "Muitas tentativas");
        assertThat(r.getBody()).doesNotContainKeys("trace", "stackTrace", "exception");
    }
}
