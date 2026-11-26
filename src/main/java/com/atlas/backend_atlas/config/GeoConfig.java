package com.atlas.backend_atlas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * RestTemplates dos provedores de mapas (Nominatim e OSRM), com timeouts próprios. Mesmo padrão do aiRestTemplate:
 * SimpleClientHttpRequestFactory garante que um timeout vire java.net.SocketTimeoutException, que os clientes
 * traduzem em 504 (em vez de "serviço indisponível").
 */
@Configuration
public class GeoConfig {

    @Bean
    public RestTemplate nominatimRestTemplate(GeoProperties props) {
        GeoProperties.Nominatim n = props.getNominatim();
        RestTemplate rt = new RestTemplate(factory(n.getConnectTimeoutMs(), n.getReadTimeoutMs()));
        rt.setInterceptors(List.of(userAgentInterceptor(userAgent(n))));
        return rt;
    }

    @Bean
    public RestTemplate osrmRestTemplate(GeoProperties props) {
        GeoProperties.Osrm o = props.getOsrm();
        RestTemplate rt = new RestTemplate(factory(o.getConnectTimeoutMs(), o.getReadTimeoutMs()));
        rt.setInterceptors(List.of(userAgentInterceptor(userAgent(props.getNominatim()))));
        return rt;
    }

    private static ClientHttpRequestInterceptor userAgentInterceptor(String userAgent) {
        return (request, body, execution) -> {
            request.getHeaders().set(HttpHeaders.USER_AGENT, userAgent);
            return execution.execute(request, body);
        };
    }

    private static SimpleClientHttpRequestFactory factory(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(connectMs);
        f.setReadTimeout(readMs);
        return f;
    }

    /** "WeSee-Backend/1.0 (contato: fulano@dominio)" quando há e-mail de contato configurado. */
    static String userAgent(GeoProperties.Nominatim n) {
        String base = n.getUserAgent() == null || n.getUserAgent().isBlank() ? "WeSee-Backend/1.0" : n.getUserAgent().trim();
        String contact = n.getContactEmail() == null ? "" : n.getContactEmail().trim();
        return contact.isEmpty() ? base : base + " (contato: " + contact + ")";
    }
}
