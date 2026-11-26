package com.atlas.backend_atlas.service.geo;

import com.atlas.backend_atlas.config.GeoProperties;
import com.atlas.backend_atlas.dto.PlaceResult;
import com.atlas.backend_atlas.dto.ReverseGeocodeResponse;
import com.atlas.backend_atlas.exception.GeoException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente do Nominatim (OpenStreetMap): pesquisa de endereços (/search) e geocodificação reversa (/reverse).
 * Respeita a política do serviço público: User-Agent identificável (GeoConfig), no máximo 1 requisição por segundo
 * (MinIntervalLimiter, vale para a aplicação inteira), nada de autocomplete a cada tecla (o app só pesquisa quando
 * o usuário confirma) e cache (feito em GeocodingService). Política: https://operations.osmfoundation.org/policies/nominatim/
 */
@Component
public class NominatimClient implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(NominatimClient.class);
    private static final String SERVICO = "Nominatim";
    /** Meia-largura (em graus, ~33 km) da área que recebe preferência quando o app informa a posição atual. */
    private static final double VIEWBOX_HALF_DEGREES = 0.3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final GeoProperties.Nominatim cfg;
    private final MinIntervalLimiter limiter;

    public NominatimClient(@Qualifier("nominatimRestTemplate") RestTemplate restTemplate,
                           ObjectMapper objectMapper, GeoProperties props) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.cfg = props.getNominatim();
        this.limiter = new MinIntervalLimiter(cfg.getMinIntervalMs());
        if (cfg.getContactEmail() == null || cfg.getContactEmail().isBlank()) {
            log.warn("NOMINATIM_CONTACT_EMAIL não definido: a política do Nominatim público pede um contato da aplicação.");
        }
    }

    @Override
    public String providerName() {
        return "nominatim";
    }

    @Override
    public List<PlaceResult> search(String query, Double nearLat, Double nearLon, int limit) {
        UriComponentsBuilder b = base("/search")
                .queryParam("q", "{q}")
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1)
                .queryParam("limit", limit)
                .queryParam("accept-language", cfg.getLanguage());
        if (notBlank(cfg.getCountryCodes())) b.queryParam("countrycodes", cfg.getCountryCodes().trim());
        if (notBlank(cfg.getContactEmail())) b.queryParam("email", "{email}");
        if (nearLat != null && nearLon != null) {
            // viewbox = esquerda,topo,direita,base (lon,lat,lon,lat). Sem "bounded": é só preferência, não filtro.
            b.queryParam("viewbox", String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f",
                    nearLon - VIEWBOX_HALF_DEGREES, nearLat + VIEWBOX_HALF_DEGREES,
                    nearLon + VIEWBOX_HALF_DEGREES, nearLat - VIEWBOX_HALF_DEGREES));
        }
        URI uri = b.encode().buildAndExpand(Map.of("q", query, "email", emailOrEmpty())).toUri();

        JsonNode root = readJson(get(uri));
        if (!root.isArray()) {
            log.warn("{}: resposta de busca não é uma lista", SERVICO);
            throw GeoException.respostaInvalida("O serviço de busca retornou uma resposta inesperada.");
        }
        List<PlaceResult> out = new ArrayList<>();
        for (JsonNode n : root) {
            PlaceResult p = toPlace(n);
            if (p != null) out.add(p);
        }
        return out;
    }

    @Override
    public Optional<ReverseGeocodeResponse> reverse(double latitude, double longitude) {
        UriComponentsBuilder b = base("/reverse")
                .queryParam("lat", String.format(Locale.ROOT, "%.6f", latitude))
                .queryParam("lon", String.format(Locale.ROOT, "%.6f", longitude))
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1)
                .queryParam("zoom", 18)
                .queryParam("accept-language", cfg.getLanguage());
        if (notBlank(cfg.getContactEmail())) b.queryParam("email", "{email}");
        URI uri = b.encode().buildAndExpand(Map.of("email", emailOrEmpty())).toUri();

        JsonNode root = readJson(get(uri));
        if (!root.isObject()) {
            throw GeoException.respostaInvalida("O serviço de busca retornou uma resposta inesperada.");
        }
        // Nominatim responde 200 com {"error": "Unable to geocode"} quando não há endereço para o ponto
        if (root.has("error")) return Optional.empty();

        JsonNode addr = root.path("address");
        String displayName = text(root, "display_name");
        String road = AddressFormatter.road(addr);
        String number = AddressFormatter.first(addr, "house_number");
        String suburb = AddressFormatter.suburb(addr);
        String name = text(root, "name");
        if (displayName == null && road == null && suburb == null) return Optional.empty();
        return Optional.of(new ReverseGeocodeResponse(
                displayName,
                AddressFormatter.shortAddress(name, road, number, suburb, displayName),
                latitude, longitude,
                road, number, suburb,
                AddressFormatter.city(addr),
                AddressFormatter.first(addr, "state"),
                AddressFormatter.first(addr, "postcode"),
                AddressFormatter.first(addr, "country")));
    }

    // ---------------------------------------------------------------------------------------------------------------

    private PlaceResult toPlace(JsonNode n) {
        Double lat = parseCoordinate(n.path("lat"));
        Double lon = parseCoordinate(n.path("lon"));
        if (lat == null || lon == null || !GeoValidation.isValid(lat, lon)) return null;   // ignora item malformado

        JsonNode addr = n.path("address");
        String displayName = text(n, "display_name");
        String road = AddressFormatter.road(addr);
        String suburb = AddressFormatter.suburb(addr);
        String name = text(n, "name");
        if (name == null && displayName != null) name = displayName.split(",")[0].trim();
        String category = text(n, "category");
        if (category == null) category = text(n, "class");
        return new PlaceResult(
                name, displayName,
                AddressFormatter.shortAddress(name, road, AddressFormatter.first(addr, "house_number"), suburb, displayName),
                lat, lon,
                text(n, "type"), category,
                road, suburb, AddressFormatter.city(addr), AddressFormatter.first(addr, "state"));
    }

    /** O Nominatim envia lat/lon como TEXTO ("-23.56"). */
    private static Double parseCoordinate(JsonNode v) {
        if (v == null || v.isMissingNode() || v.isNull()) return null;
        if (v.isNumber()) return v.asDouble();
        try {
            return Double.valueOf(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText().trim() : null;
    }

    private UriComponentsBuilder base(String path) {
        return UriComponentsBuilder.fromUriString(cfg.getBaseUrl().trim()).path(path);
    }

    private String emailOrEmpty() {
        return cfg.getContactEmail() == null ? "" : cfg.getContactEmail().trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String get(URI uri) {
        if (!limiter.tryAcquire(cfg.getMaxQueueWaitMs())) {
            log.warn("{}: controle de frequência recusou a chamada (fila cheia)", SERVICO);
            throw GeoException.limite("O serviço de busca está ocupado no momento. Tente novamente em alguns segundos.");
        }
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                throw GeoException.respostaInvalida("O serviço de busca retornou uma resposta vazia.");
            }
            return body;
        } catch (RestClientException ex) {
            throw GeoHttpErrors.translate(ex, SERVICO, log);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            log.warn("{}: resposta não é JSON válido", SERVICO);
            throw GeoException.respostaInvalida("Não foi possível interpretar a resposta do serviço de busca.");
        }
    }
}
