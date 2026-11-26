package com.atlas.backend_atlas.service.geo;

import com.atlas.backend_atlas.dto.PlaceResult;
import com.atlas.backend_atlas.dto.ReverseGeocodeResponse;

import java.util.List;
import java.util.Optional;

/**
 * Provedor de pesquisa de endereços e geocodificação reversa. Hoje: {@link NominatimClient}.
 * Para trocar de provedor (instância própria do Nominatim, Photon, uma API paga com autocomplete...) basta uma nova
 * implementação desta interface; {@code GeocodingService}, controllers e o app Android não mudam.
 */
public interface GeocodingProvider {

    /** Nome curto do provedor (aparece na resposta). */
    String providerName();

    /** Pesquisa por endereço/nome de local. [nearLat]/[nearLon] (opcionais) só dão preferência a resultados próximos. */
    List<PlaceResult> search(String query, Double nearLat, Double nearLon, int limit);

    /** Endereço aproximado da coordenada; vazio se não houver. */
    Optional<ReverseGeocodeResponse> reverse(double latitude, double longitude);
}
