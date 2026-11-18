package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.Proximity;
import com.atlas.backend_atlas.dto.RawDetection;
import org.springframework.stereotype.Component;

/**
 * Classifica a proximidade aproximada de um objeto detectado a partir da
 * área que o seu bounding box ocupa em relação à área total da imagem:
 * quanto maior a área relativa, mais perto o objeto está da câmera (e,
 * portanto, do usuário).
 * <p>
 * Limiares (ajustáveis conforme calibração com o modelo YOLO em uso):
 * - área relativa >= 15% -> NEAR   (muito próximo, risco de colisão)
 * - área relativa >= 5%  -> MEDIUM (distância média)
 * - área relativa <  5%  -> FAR    (distante)
 */
@Component
public class ProximityClassifier {

    private static final double NEAR_THRESHOLD = 0.15;
    private static final double MEDIUM_THRESHOLD = 0.05;

    public Proximity classify(RawDetection detection, int imageWidth, int imageHeight) {
        double imageArea = (double) imageWidth * (double) imageHeight;
        if (imageArea <= 0) {
            return Proximity.MEDIUM;
        }

        double objectArea = detection.width() * detection.height();
        double ratio = objectArea / imageArea;

        if (ratio >= NEAR_THRESHOLD) {
            return Proximity.NEAR;
        }
        if (ratio >= MEDIUM_THRESHOLD) {
            return Proximity.MEDIUM;
        }
        return Proximity.FAR;
    }
}
