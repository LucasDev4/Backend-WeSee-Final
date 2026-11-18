package com.atlas.backend_atlas.controller;

import com.atlas.backend_atlas.dto.DetectionResponse;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.service.DetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// Rota protegida: assim como /user/**, cai na regra padrão do
// SecurityConfig (.anyRequest().authenticated()) - só /auth/** e o
// Swagger são públicos. Ou seja, POST /detect já exige um JWT válido no
// header Authorization sem precisar de nenhuma configuração extra aqui.
@Slf4j
@RestController
@RequestMapping("/detect")
@RequiredArgsConstructor
@Tag(name = "Detecção", description = "Processa imagens capturadas pelo app e retorna a orientação para o usuário")
public class DetectionController {

    private final DetectionService detectionService;

    @Operation(summary = "Envia uma imagem para a IA (YOLO) e devolve os objetos detectados junto com a orientação a ser comunicada ao usuário")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DetectionResponse> detect(
            @RequestParam("image") MultipartFile image,
            @AuthenticationPrincipal User user
    ) {
        // Defesa extra: na prática nunca deveria acontecer, pois a rota já
        // exige autenticação via SecurityConfig antes de chegar aqui, mas
        // evita um NPE silencioso caso a regra de segurança mude no futuro.
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        }

        log.info("Detecção solicitada por userId={}", user.getId());

        DetectionResponse response = detectionService.processImage(image);

        return ResponseEntity.ok(response);
    }
}
