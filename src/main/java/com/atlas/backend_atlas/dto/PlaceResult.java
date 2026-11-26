package com.atlas.backend_atlas.dto;

/** Um resultado de busca de endereço/local, independente do provedor (Nominatim hoje). Campos ausentes vêm null. */
public record PlaceResult(
        String name,
        String displayName,
        String shortAddress,
        double latitude,
        double longitude,
        String type,
        String category,
        String road,
        String suburb,
        String city,
        String state
) {}
