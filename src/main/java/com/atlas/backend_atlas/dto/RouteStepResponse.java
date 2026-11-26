package com.atlas.backend_atlas.dto;

/**
 * Um trecho da rota. [instruction] é a tradução (pt-BR) da manobra devolvida pelo OSRM e é null quando o tipo de
 * manobra não é reconhecido (nada é inventado). [maneuverType]/[maneuverModifier] são os dados originais do OSRM.
 */
public record RouteStepResponse(
        String instruction,
        String maneuverType,
        String maneuverModifier,
        String streetName,
        double distanceMeters,
        double durationSeconds,
        CoordinateDto location
) {}
