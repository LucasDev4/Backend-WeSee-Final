package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.DetectionRequest;
import com.atlas.backend_atlas.dto.RawDetection;
import com.atlas.backend_atlas.exception.AiResponseException;
import com.atlas.backend_atlas.exception.AiServiceUnavailableException;
import com.atlas.backend_atlas.exception.AiTimeoutException;
import com.atlas.backend_atlas.exception.InvalidImageException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

/**
 * Responsável exclusivamente por conversar com o serviço de IA (
 * YOLO): envia a imagem recebida em POST /detect e devolve as detecções
 * brutas já desserializadas em DetectionRequest.
 * <p>
 * Erros de rede/infra da IA (indisponibilidade, timeout, resposta em
 * formato inesperado) são traduzidos aqui em exceções de negócio,
 * tratadas de forma padronizada pelo GlobalExceptionHandler.
 */
@Slf4j
@Service
public class AiDetectionClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String detectUrl;

    public AiDetectionClient(
            @Qualifier("aiRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${atlas.ai.base-url}") String baseUrl,
            @Value("${atlas.ai.detect-path}") String detectPath
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.detectUrl = baseUrl + detectPath;
    }

    public DetectionRequest detect(MultipartFile image) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", toResource(image));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;

        try {
            response = restTemplate.postForEntity(detectUrl, requestEntity, String.class);

        } catch (ResourceAccessException ex) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                log.warn("Timeout ao aguardar resposta da IA em {}", detectUrl);
                throw new AiTimeoutException("Tempo limite excedido ao aguardar resposta da IA.");
            }
            log.warn("Serviço de IA indisponível em {}: {}", detectUrl, ex.getMessage());
            throw new AiServiceUnavailableException("Serviço de IA indisponível no momento. Tente novamente em instantes.");

        } catch (HttpStatusCodeException ex) {
            log.warn("Serviço de IA retornou erro {} em {}: {}", ex.getStatusCode(), detectUrl, ex.getResponseBodyAsString());
            throw new AiServiceUnavailableException("Serviço de IA retornou um erro ao processar a imagem.");

        } catch (RestClientException ex) {
            log.error("Falha inesperada ao chamar o serviço de IA em {}", detectUrl, ex);
            throw new AiServiceUnavailableException("Não foi possível se comunicar com o serviço de IA.");
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new AiServiceUnavailableException("Serviço de IA retornou status " + response.getStatusCode() + ".");
        }

        return parseResponse(response.getBody());
    }

    private DetectionRequest parseResponse(String json) {
        if (json == null || json.isBlank()) {
            throw new AiResponseException("O serviço de IA retornou uma resposta vazia.");
        }

        String trimmed = json.trim();

        try {
            // Alguns serviços FastAPI devolvem apenas o array de
            // detecções, sem um objeto "wrapper" com as dimensões da
            // imagem - aqui ambos os formatos são aceitos.
            if (trimmed.startsWith("[")) {
                List<RawDetection> detections =
                        objectMapper.readValue(trimmed, new TypeReference<List<RawDetection>>() {});
                return new DetectionRequest(null, null, detections);
            }
            return objectMapper.readValue(trimmed, DetectionRequest.class);

        } catch (JsonProcessingException ex) {
            log.error("Resposta da IA em formato inesperado: {}", trimmed, ex);
            throw new AiResponseException("Não foi possível interpretar a resposta do serviço de IA.");
        }
    }

    private Resource toResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
        } catch (IOException ex) {
            throw new InvalidImageException("Não foi possível ler o arquivo de imagem enviado.");
        }
    }
}
