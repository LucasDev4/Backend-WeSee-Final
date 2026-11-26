package com.atlas.backend_atlas.dto;

/**
 * Posição horizontal aproximada de um objeto detectado dentro da imagem,
 * usada tanto no objeto individual (DetectedObject) quanto na direção
 * sugerida na orientação final (GuidanceResponse).
 */
public enum Position {
    LEFT,
    CENTER,
    RIGHT
}
