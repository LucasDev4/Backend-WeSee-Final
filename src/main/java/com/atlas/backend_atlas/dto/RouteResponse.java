package com.atlas.backend_atlas.dto;

import java.util.List;

/**
 * Resposta de GET /api/routes.
 * [walking] = true só quando o servidor está configurado e confirmado como perfil de PEDESTRE. Se for false, a rota NÃO
 * é de caminhada e [warning] explica (o app mostra o aviso). [safetyNotice] lembra que nenhuma rota garante calçada,
 * travessia segura ou ausência de obstáculos.
 */
public record RouteResponse(
        String provider,
        String profile,
        boolean walking,
        String warning,
        String safetyNotice,
        String attribution,
        double distanceMeters,
        double durationSeconds,
        List<CoordinateDto> coordinates,
        List<RouteStepResponse> steps
) {}
