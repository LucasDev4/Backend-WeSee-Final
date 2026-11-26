package com.atlas.backend_atlas.dto;

/**
 * Proximidade aproximada do objeto detectado em relação ao usuário,
 * estimada a partir da área do bounding box em relação à área da imagem.
 *
 * A ordem de declaração é importante: é uma ordem crescente de proximidade
 * (FAR < MEDIUM < NEAR) usada para comparar/ordenar objetos por urgência.
 */
public enum Proximity {
    FAR,
    MEDIUM,
    NEAR
}
