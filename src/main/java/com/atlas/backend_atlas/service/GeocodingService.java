package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.config.GeoProperties;
import com.atlas.backend_atlas.dto.GeocodingSearchResponse;
import com.atlas.backend_atlas.dto.PlaceResult;
import com.atlas.backend_atlas.dto.ReverseGeocodeResponse;
import com.atlas.backend_atlas.exception.GeoException;
import com.atlas.backend_atlas.service.geo.GeoValidation;
import com.atlas.backend_atlas.service.geo.GeocodingProvider;
import com.atlas.backend_atlas.service.geo.TtlCache;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pesquisa de endereços e geocodificação reversa. Valida a entrada, consulta o cache e só então chama o
 * {@link GeocodingProvider}. Não depende do Nominatim: trocar de provedor não muda esta classe.
 */
@Service
public class GeocodingService {

    static final int MIN_QUERY_LENGTH = 3;
    static final int MAX_QUERY_LENGTH = 200;
    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 10;

    private final GeocodingProvider provider;
    private final GeoProperties props;
    private final TtlCache<String, List<PlaceResult>> searchCache;
    private final TtlCache<String, ReverseGeocodeResponse> reverseCache;

    public GeocodingService(GeocodingProvider provider, GeoProperties props, Clock clock) {
        this.provider = provider;
        this.props = props;
        GeoProperties.Nominatim n = props.getNominatim();
        this.searchCache = new TtlCache<>(n.getSearchCacheTtlSeconds(), n.getCacheMaxEntries(), clock);
        this.reverseCache = new TtlCache<>(n.getReverseCacheTtlSeconds(), n.getCacheMaxEntries(), clock);
    }

    public GeocodingSearchResponse search(String q, Double nearLat, Double nearLon, Integer limit) {
        String query = q == null ? "" : q.trim().replaceAll("\\s+", " ");
        if (query.isEmpty()) {
            throw GeoException.invalido("Informe o endereço ou o nome do local a pesquisar.");
        }
        if (query.length() < MIN_QUERY_LENGTH) {
            throw GeoException.invalido("Digite pelo menos " + MIN_QUERY_LENGTH + " caracteres para pesquisar.");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw GeoException.invalido("O texto da pesquisa é longo demais.");
        }
        if ((nearLat == null) != (nearLon == null)) {
            throw GeoException.invalido("Informe latitude e longitude juntas, ou nenhuma das duas.");
        }
        if (nearLat != null) GeoValidation.exigirValida(nearLat, nearLon, "posição de referência");
        int max = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);

        // posição arredondada a ~1 km: consultas próximas compartilham o cache e a posição exata não vira chave
        String near = nearLat == null ? "-" : String.format(Locale.ROOT, "%.2f,%.2f", nearLat, nearLon);
        String key = query.toLowerCase(Locale.ROOT) + "|" + near + "|" + max;

        Optional<List<PlaceResult>> cached = searchCache.get(key);
        List<PlaceResult> results;
        if (cached.isPresent()) {
            results = cached.get();
        } else {
            results = List.copyOf(provider.search(query, nearLat, nearLon, max));
            searchCache.put(key, results);
        }
        return new GeocodingSearchResponse(provider.providerName(), props.getAttribution(), results);
    }

    public ReverseGeocodeResponse reverse(double lat, double lon) {
        GeoValidation.exigirValida(lat, lon, "localização");
        // ~11 m de precisão na chave: o cache vale para quem está parado ou andando devagar no mesmo ponto
        String key = String.format(Locale.ROOT, "%.4f,%.4f", lat, lon);

        Optional<ReverseGeocodeResponse> cached = reverseCache.get(key);
        if (cached.isPresent()) {
            ReverseGeocodeResponse c = cached.get();
            return new ReverseGeocodeResponse(c.displayName(), c.shortAddress(), lat, lon, c.road(), c.houseNumber(),
                    c.suburb(), c.city(), c.state(), c.postcode(), c.country());
        }
        ReverseGeocodeResponse found = provider.reverse(lat, lon)
                .orElseThrow(() -> GeoException.naoEncontrado("Não foi possível identificar um endereço para esta localização."));
        reverseCache.put(key, found);
        return found;
    }
}
