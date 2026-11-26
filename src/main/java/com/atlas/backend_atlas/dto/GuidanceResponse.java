package com.atlas.backend_atlas.dto;

/**
 * Orientação em linguagem natural gerada por GuidanceService a partir do
 * objeto mais urgente da cena, pronta para ser exibida ou falada (TTS) ao
 * usuário com deficiência visual.
 * <p>
 * Exemplo:
 * {
 *   "message": "Pessoa à sua frente",
 *   "direction": "CENTER",
 *   "priority": "HIGH"
 * }
 * <p>
 * Quando nenhum objeto relevante é detectado, direction vem nulo e
 * priority vem LOW, com uma mensagem informando que não há riscos à
 * vista.
 */
public record GuidanceResponse(

        String message,

        Position direction,

        Priority priority

) {}
