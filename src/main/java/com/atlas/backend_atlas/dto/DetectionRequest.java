package com.atlas.backend_atlas.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * Payload retornado pelo serviço de IA (FastAPI/YOLO) para uma imagem
 * enviada em POST /detect. É o ponto de entrada do processamento feito
 * por DetectionService/GuidanceService: contém as detecções brutas e,
 * quando disponíveis, as dimensões da imagem original (necessárias para
 * calcular posição e proximidade dos objetos).
 * <p>
 * imageWidth/imageHeight são opcionais: se o FastAPI não os enviar, o
 * DetectionService usa valores padrão configuráveis
 * (atlas.detection.default-image-width/height).
 * <p>
 * Os @JsonAlias cobrem variações comuns de nomenclatura que um serviço
 * Python/FastAPI costuma usar (snake_case) sem exigir que o contrato da
 * IA siga exatamente o camelCase usado aqui.
 */
public record DetectionRequest(

        @JsonAlias({"image_width", "img_width"})
        Integer imageWidth,

        @JsonAlias({"image_height", "img_height"})
        Integer imageHeight,

        @JsonAlias({"objects", "results", "predictions"})
        List<RawDetection> detections

) {}
