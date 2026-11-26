package com.atlas.backend_atlas.service.geo;

import com.atlas.backend_atlas.dto.CoordinateDto;
import com.atlas.backend_atlas.dto.RouteStepResponse;

import java.util.List;

/** Rota "crua" devolvida por um {@link RoutingProvider}, já convertida para (latitude, longitude). */
public record RawRoute(double distanceMeters, double durationSeconds,
                       List<CoordinateDto> coordinates, List<RouteStepResponse> steps) {}
