package com.atlas.backend_atlas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Objeto detectado já processado e pronto para o app cliente consumir:
 * além da classe e confiança originais do YOLO, já traz posição,
 * proximidade e prioridade calculadas por DetectionService.
 * <p>
 * Exemplo no JSON de resposta:
 * {
 *   "class": "person",
 *   "confidence": 0.94,
 *   "position": "CENTER",
 *   "proximity": "MEDIUM",
 *   "priority": "HIGH"
 * }
 */
public record DetectedObject(

        @JsonProperty("class")
        String className,
        // nome em português para falar/mostrar (ex.: "Pessoa", "Placa de trânsito")
        String label,

        Double confidence,

        Position position,

        Proximity proximity,

        Priority priority

) {}
