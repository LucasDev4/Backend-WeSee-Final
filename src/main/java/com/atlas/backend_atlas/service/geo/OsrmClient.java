package com.atlas.backend_atlas.service.geo;

import com.atlas.backend_atlas.config.GeoProperties;
import com.atlas.backend_atlas.dto.CoordinateDto;
import com.atlas.backend_atlas.dto.RouteStepResponse;
import com.atlas.backend_atlas.exception.GeoException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cliente do OSRM (serviço "route"). Pede geometria em GeoJSON ({@code geometries=geojson}) com {@code steps=true}.
 * <p>
 * ATENÇÃO ÀS COORDENADAS: o OSRM usa a ordem <b>longitude,latitude</b> (na URL e no GeoJSON). Este é o ÚNICO lugar do
 * sistema onde a inversão acontece: a interface {@link RoutingProvider} recebe e devolve (latitude, longitude).
 * <p>
 * Este cliente NÃO sabe se o servidor configurado calcula rotas a pé ou de carro: quem decide isso é o
 * {@code RoutingService}, a partir da configuração (atlas.geo.osrm.*).
 */
@Component
public class OsrmClient implements RoutingProvider {

    private static final Logger log = LoggerFactory.getLogger(OsrmClient.class);
    private static final String SERVICO = "OSRM";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final GeoProperties.Osrm cfg;
    private final MinIntervalLimiter limiter;

    public OsrmClient(@Qualifier("osrmRestTemplate") RestTemplate restTemplate,
                      ObjectMapper objectMapper, GeoProperties props) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.cfg = props.getOsrm();
        this.limiter = new MinIntervalLimiter(cfg.getMinIntervalMs());
    }

    @Override
    public String providerName() {
        return "osrm";
    }

    @Override
    public RawRoute route(double originLat, double originLon, double destinationLat, double destinationLon) {
        // OSRM: {longitude},{latitude};{longitude},{latitude}
        String coords = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", originLon, originLat, destinationLon, destinationLat);
        URI uri = URI.create(trimEnd(cfg.getBaseUrl().trim()) + normalizePath(cfg.getRoutePath()) + "/" + coords
                + "?overview=full&geometries=geojson&steps=true&alternatives=false&annotations=false&generate_hints=false");

        if (!limiter.tryAcquire(cfg.getMaxQueueWaitMs())) {
            log.warn("{}: controle de frequência recusou a chamada (fila cheia)", SERVICO);
            throw GeoException.limite("O serviço de rotas está ocupado no momento. Tente novamente em alguns segundos.");
        }

        String body;
        try {
            body = restTemplate.getForObject(uri, String.class);
        } catch (HttpStatusCodeException ex) {
            // O OSRM costuma devolver JSON {"code":"NoRoute"...} junto com HTTP 400: tenta entender antes de dar erro genérico
            GeoException mapped = mapErrorBody(ex.getResponseBodyAsString());
            if (mapped != null) throw mapped;
            throw GeoHttpErrors.translate(ex, SERVICO, log);
        } catch (RestClientException ex) {
            throw GeoHttpErrors.translate(ex, SERVICO, log);
        }
        if (body == null || body.isBlank()) {
            throw GeoException.respostaInvalida("O serviço de rotas retornou uma resposta vazia.");
        }
        return parse(body);
    }

    // ---------------------------------------------------------------------------------------------------------------

    RawRoute parse(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            log.warn("{}: resposta não é JSON válido", SERVICO);
            throw GeoException.respostaInvalida("Não foi possível interpretar a resposta do serviço de rotas.");
        }
        String code = root.path("code").asText("");
        if (!"Ok".equals(code)) throw mapCode(code);

        JsonNode routes = root.path("routes");
        if (!routes.isArray() || routes.size() == 0) {
            throw GeoException.naoEncontrado("Não foi encontrada uma rota entre a origem e o destino.");
        }
        JsonNode route = routes.get(0);

        double distance = route.path("distance").asDouble(Double.NaN);
        double duration = route.path("duration").asDouble(Double.NaN);
        if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(duration) || duration < 0) {
            throw invalidResponse();
        }

        List<CoordinateDto> coordinates = new ArrayList<>();
        JsonNode line = route.path("geometry").path("coordinates");
        if (!line.isArray()) throw invalidResponse();
        for (JsonNode c : line) {
            CoordinateDto p = fromLonLat(c);
            if (p == null) throw invalidResponse();
            coordinates.add(p);
        }
        if (coordinates.size() < 2) throw invalidResponse();

        List<RouteStepResponse> steps = new ArrayList<>();
        for (JsonNode leg : route.path("legs")) {
            for (JsonNode s : leg.path("steps")) {
                steps.add(toStep(s));
            }
        }
        return new RawRoute(distance, duration, coordinates, steps);
    }

    private static RouteStepResponse toStep(JsonNode s) {
        JsonNode m = s.path("maneuver");
        String type = textOrNull(m.path("type"));
        String modifier = textOrNull(m.path("modifier"));
        String name = textOrNull(s.path("name"));
        Integer exit = m.path("exit").isInt() ? m.path("exit").asInt() : null;
        double dist = s.path("distance").asDouble(0);
        double dur = s.path("duration").asDouble(0);
        return new RouteStepResponse(
                StepInstructions.build(type, modifier, name, exit),   // null se a manobra não for reconhecida
                type, modifier, name,
                Double.isFinite(dist) ? dist : 0, Double.isFinite(dur) ? dur : 0,
                fromLonLat(m.path("location")));
    }

    /** [longitude, latitude] (formato GeoJSON/OSRM) -> CoordinateDto(latitude, longitude). null se inválido. */
    static CoordinateDto fromLonLat(JsonNode pair) {
        if (pair == null || !pair.isArray() || pair.size() < 2 || !pair.get(0).isNumber() || !pair.get(1).isNumber()) {
            return null;
        }
        double lon = pair.get(0).asDouble();
        double lat = pair.get(1).asDouble();
        return GeoValidation.isValid(lat, lon) ? new CoordinateDto(lat, lon) : null;
    }

    private GeoException mapErrorBody(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            String code = objectMapper.readTree(body).path("code").asText("");
            if (code.equals("NoRoute") || code.equals("NoSegment") || code.equals("TooBig")) return mapCode(code);
        } catch (JsonProcessingException ignored) {
            // corpo não é JSON: cai no tratamento genérico por status HTTP
        }
        return null;
    }

    private static GeoException mapCode(String code) {
        return switch (code) {
            case "NoRoute" -> GeoException.naoEncontrado("Não foi encontrada uma rota entre a origem e o destino.");
            case "NoSegment" -> GeoException.naoEncontrado("Não encontramos uma via próxima à origem ou ao destino.");
            case "TooBig" -> GeoException.invalido("A rota solicitada é grande demais para ser calculada.");
            default -> invalidResponse();
        };
    }

    private static GeoException invalidResponse() {
        return GeoException.respostaInvalida("O serviço de rotas retornou uma resposta inesperada.");
    }

    private static String textOrNull(JsonNode n) {
        return n.isTextual() && !n.asText().isBlank() ? n.asText().trim() : null;
    }

    private static String trimEnd(String url) {
        String u = url;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String normalizePath(String path) {
        String p = path == null ? "" : path.trim();
        if (p.isEmpty()) return "";
        if (!p.startsWith("/")) p = "/" + p;
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
