package com.atlas.backend_atlas.dto;

/**
 * Prioridade de alerta de um objeto detectado.
 *
 * A ordem de declaração é importante: é uma ordem crescente de urgência
 * (LOW < MEDIUM < HIGH) usada para escolher o objeto mais urgente da cena
 * (ver GuidanceService) e para ordenar a lista de objetos na resposta.
 */
public enum Priority {
    LOW,
    MEDIUM,
    HIGH
}
