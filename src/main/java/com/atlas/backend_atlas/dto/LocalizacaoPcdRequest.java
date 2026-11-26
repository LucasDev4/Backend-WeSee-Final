package com.atlas.backend_atlas.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Corpo de POST /api/pcd/localizacao. A identidade (quem é o PCD) vem SEMPRE do token; o horário é o do servidor. */
public record LocalizacaoPcdRequest(
        @NotNull(message = "Latitude é obrigatória")
        @DecimalMin(value = "-90.0", message = "Latitude deve estar entre -90 e 90")
        @DecimalMax(value = "90.0", message = "Latitude deve estar entre -90 e 90")
        Double latitude,

        @NotNull(message = "Longitude é obrigatória")
        @DecimalMin(value = "-180.0", message = "Longitude deve estar entre -180 e 180")
        @DecimalMax(value = "180.0", message = "Longitude deve estar entre -180 e 180")
        Double longitude,

        @PositiveOrZero(message = "Precisão não pode ser negativa")
        Double precisaoMetros
) {}
