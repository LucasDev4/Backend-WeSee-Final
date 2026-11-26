package com.atlas.backend_atlas.dto;

import java.util.List;

/**
 * Resposta do endpoint POST /detect: a lista completa de objetos
 * detectados (já classificados e ordenados do mais para o menos urgente)
 * e a orientação principal a ser comunicada ao usuário.
 * <p>
 * Exemplo:
 * {
 *   "objects": [
 *     { "class": "person", "confidence": 0.94, "position": "CENTER", "proximity": "MEDIUM", "priority": "HIGH" }
 *   ],
 *   "guidance": {
 *     "message": "Pessoa à sua frente",
 *     "direction": "CENTER",
 *     "priority": "HIGH"
 *   }
 * }
 */
public record DetectionResponse(

        List<DetectedObject> objects,

        GuidanceResponse guidance,
        // frase com TUDO que a câmera vê, em português (ex.: "Vejo duas pessoas à sua frente e um carro à sua direita.")
        String scene

) {}
