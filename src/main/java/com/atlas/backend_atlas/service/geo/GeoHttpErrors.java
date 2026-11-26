package com.atlas.backend_atlas.service.geo;

import com.atlas.backend_atlas.exception.GeoException;
import org.slf4j.Logger;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;

/**
 * Traduz falhas HTTP dos provedores externos em {@link GeoException} com mensagem amigável.
 * Os logs NÃO incluem URL, parâmetros nem mensagem da exceção (a URL carrega o endereço pesquisado e as
 * coordenadas da pessoa): só o nome do serviço, o status e o tipo da falha.
 */
final class GeoHttpErrors {

    private GeoHttpErrors() {}

    static GeoException translate(RestClientException ex, String servico, Logger log) {
        if (ex instanceof HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            log.warn("{} respondeu HTTP {}", servico, status);
            if (status == 429) {
                return GeoException.limite("O serviço de mapas atingiu o limite de uso. Tente novamente em instantes.");
            }
            if (status == 403) {
                return GeoException.indisponivel("O serviço de mapas recusou a requisição no momento. Tente novamente mais tarde.");
            }
            return GeoException.indisponivel("O serviço de mapas está indisponível no momento. Tente novamente em instantes.");
        }
        if (ex instanceof ResourceAccessException) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                log.warn("{}: tempo limite excedido", servico);
                return GeoException.timeout("O serviço de mapas demorou demais para responder. Tente novamente.");
            }
            log.warn("{} inacessível ({})", servico, ex.getCause() == null ? "sem causa" : ex.getCause().getClass().getSimpleName());
            return GeoException.indisponivel("O serviço de mapas está indisponível no momento. Tente novamente em instantes.");
        }
        log.error("{}: falha inesperada ({})", servico, ex.getClass().getSimpleName());
        return GeoException.indisponivel("Não foi possível se comunicar com o serviço de mapas.");
    }
}
