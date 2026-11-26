package com.atlas.backend_atlas.dto;

/** Ponto geográfico no formato que o Android usa (latitude, longitude). O OSRM usa lon,lat: a troca é feita só no OsrmClient. */
public record CoordinateDto(double latitude, double longitude) {}
