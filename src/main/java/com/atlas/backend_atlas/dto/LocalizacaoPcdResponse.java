package com.atlas.backend_atlas.dto;

/**
 * Última localização conhecida do PCD, como o responsável a recebe (dentro de DadosPcdResponse.dados).
 * [atualizadoEm] é ISO-8601 em UTC (horário do servidor); [idadeSegundos] é calculada no servidor, para não
 * depender do relógio do celular do responsável.
 */
public record LocalizacaoPcdResponse(
        double latitude,
        double longitude,
        Double precisaoMetros,
        String atualizadoEm,
        long idadeSegundos
) {}
