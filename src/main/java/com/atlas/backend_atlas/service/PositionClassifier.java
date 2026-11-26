package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.Position;
import com.atlas.backend_atlas.dto.RawDetection;
import org.springframework.stereotype.Component;

/**
 * Classifica a posição horizontal de um objeto detectado dentro da
 * imagem (esquerda / centro / direita), dividindo a largura da imagem em
 * três faixas iguais.
 * <p>
 * x/y em RawDetection são tratados como o canto superior esquerdo do
 * bounding box (convenção mais comum em detecções YOLO), então o centro
 * horizontal do objeto é x + width / 2.
 */
@Component
public class PositionClassifier {

    public Position classify(RawDetection detection, int imageWidth) {
        if (imageWidth <= 0) {
            return Position.CENTER;
        }

        double centerX = detection.x() + (detection.width() / 2.0);
        double clampedCenterX = Math.max(0, Math.min(centerX, imageWidth));

        double leftBoundary = imageWidth / 3.0;
        double rightBoundary = imageWidth * 2.0 / 3.0;

        if (clampedCenterX < leftBoundary) {
            return Position.LEFT;
        }
        if (clampedCenterX > rightBoundary) {
            return Position.RIGHT;
        }
        return Position.CENTER;
    }
}
