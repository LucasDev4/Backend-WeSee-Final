package com.atlas.backend_atlas.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representa uma detecção "crua", exatamente como o serviço de IA
 * (FastAPI + YOLO) a devolve, antes de qualquer processamento. Posição,
 * proximidade e prioridade ainda não foram calculadas nesse estágio -
 * isso é feito por DetectionService a partir deste DTO, gerando um
 * DetectedObject para cada item válido.
 * <p>
 * Exemplo de item recebido do FastAPI:
 * {
 *   "class": "person",
 *   "confidence": 0.94,
 *   "x": 320,
 *   "y": 180,
 *   "width": 100,
 *   "height": 250
 * }
 * <p>
 * "class" é palavra reservada em Java, por isso o campo se chama
 * objectClass e é mapeado explicitamente para a chave JSON "class".
 * x/y são tratados como o canto superior esquerdo do bounding box
 * (convenção mais comum em detecções YOLO); width/height são as
 * dimensões do bounding box em pixels.
 */
public record RawDetection(

        @JsonProperty("class")
        String objectClass,

        Double confidence,

        @JsonAlias({"x_min", "left"})
        Double x,

        @JsonAlias({"y_min", "top"})
        Double y,

        @JsonAlias({"w"})
        Double width,

        @JsonAlias({"h"})
        Double height

) {}
