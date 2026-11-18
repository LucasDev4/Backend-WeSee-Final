package com.atlas.backend_atlas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitarVinculoRequest(
        @NotBlank(message = "Código é obrigatório")
        @Size(max = 32, message = "Código inválido")
        String codigo
) {}
