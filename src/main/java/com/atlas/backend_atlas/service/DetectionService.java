package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.DetectedObject;
import com.atlas.backend_atlas.dto.DetectionRequest;
import com.atlas.backend_atlas.dto.DetectionResponse;
import com.atlas.backend_atlas.dto.GuidanceResponse;
import com.atlas.backend_atlas.dto.Position;
import com.atlas.backend_atlas.dto.Priority;
import com.atlas.backend_atlas.dto.Proximity;
import com.atlas.backend_atlas.dto.RawDetection;
import com.atlas.backend_atlas.exception.ImageTooLargeException;
import com.atlas.backend_atlas.exception.InvalidImageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orquestra o fluxo completo de processamento de uma imagem enviada em
 * POST /detect:
 * <ol>
 *   <li>valida a imagem recebida do app cliente (presença, formato, tamanho);</li>
 *   <li>envia a imagem para o serviço de IA e recebe as detecções brutas do YOLO;</li>
 *   <li>valida cada detecção e filtra por confiança mínima do sistema;</li>
 *   <li>classifica posição, proximidade e prioridade de cada objeto válido;</li>
 *   <li>delega ao GuidanceService a geração da orientação principal.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png");

    private final AiDetectionClient aiDetectionClient;
    private final PositionClassifier positionClassifier;
    private final ProximityClassifier proximityClassifier;
    private final PriorityRules priorityRules;
    private final GuidanceService guidanceService;

    @Value("${atlas.detection.min-confidence}")
    private double minConfidence;

    @Value("${atlas.detection.max-image-size-bytes}")
    private long maxImageSizeBytes;

    @Value("${atlas.detection.default-image-width}")
    private int defaultImageWidth;

    @Value("${atlas.detection.default-image-height}")
    private int defaultImageHeight;

    public DetectionResponse processImage(MultipartFile image) {
        validateImage(image);

        DetectionRequest raw = aiDetectionClient.detect(image);

        int imageWidth = (raw.imageWidth() != null && raw.imageWidth() > 0) ? raw.imageWidth() : defaultImageWidth;
        int imageHeight = (raw.imageHeight() != null && raw.imageHeight() > 0) ? raw.imageHeight() : defaultImageHeight;

        List<RawDetection> rawDetections = raw.detections() != null ? raw.detections() : List.of();

        List<DetectedObject> objects = rawDetections.stream()
                .filter(this::isValidDetection)
                .filter(d -> d.confidence() >= minConfidence)
                .map(d -> toDetectedObject(d, imageWidth, imageHeight))
                .sorted(
                        Comparator.comparing(DetectedObject::priority)
                                .thenComparing(DetectedObject::proximity)
                                .reversed()
                )
                .collect(Collectors.toList());

        GuidanceResponse guidance = guidanceService.generate(objects);

        String scene = guidanceService.describeScene(objects);
        return new DetectionResponse(objects, guidance, scene);
    }

    private boolean isValidDetection(RawDetection d) {
        boolean valid = d != null
                && d.objectClass() != null && !d.objectClass().isBlank()
                && d.confidence() != null && d.confidence() >= 0.0 && d.confidence() <= 1.0
                && d.x() != null && d.y() != null
                && d.width() != null && d.width() > 0
                && d.height() != null && d.height() > 0;

        if (!valid) {
            log.warn("Detecção descartada por dados inválidos vindos da IA: {}", d);
        }
        return valid;
    }

    private DetectedObject toDetectedObject(RawDetection d, int imageWidth, int imageHeight) {
        Position position = positionClassifier.classify(d, imageWidth);
        Proximity proximity = proximityClassifier.classify(d, imageWidth, imageHeight);
        Priority priority = priorityRules.resolve(d.objectClass(), proximity);

        return new DetectedObject(d.objectClass(), priorityRules.label(d.objectClass()), d.confidence(), position, proximity, priority);
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidImageException("Nenhuma imagem enviada.");
        }

        if (image.getSize() > maxImageSizeBytes) {
            throw new ImageTooLargeException(
                    "Imagem excede o tamanho máximo permitido de "
                            + (maxImageSizeBytes / (1024 * 1024)) + "MB.");
        }

        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidImageException("Formato de imagem inválido. Envie um arquivo JPG ou PNG.");
        }
    }
}
