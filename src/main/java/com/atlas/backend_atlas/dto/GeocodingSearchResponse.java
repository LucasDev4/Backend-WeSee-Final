package com.atlas.backend_atlas.dto;

import java.util.List;

/** Resposta de GET /api/geocoding/search. [attribution] deve ser exibido ao usuário (licença ODbL do OpenStreetMap). */
public record GeocodingSearchResponse(String provider, String attribution, List<PlaceResult> results) {}
