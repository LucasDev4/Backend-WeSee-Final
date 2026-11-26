package com.atlas.backend_atlas.dto;

/** Endereço aproximado de uma coordenada (GET /api/geocoding/reverse). Campos ausentes vêm null. */
public record ReverseGeocodeResponse(
        String displayName,
        String shortAddress,
        double latitude,
        double longitude,
        String road,
        String houseNumber,
        String suburb,
        String city,
        String state,
        String postcode,
        String country
) {}
