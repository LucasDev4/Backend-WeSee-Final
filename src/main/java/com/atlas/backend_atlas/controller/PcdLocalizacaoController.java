package com.atlas.backend_atlas.controller;

import com.atlas.backend_atlas.dto.LocalizacaoPcdRequest;
import com.atlas.backend_atlas.dto.PublicarLocalizacaoResponse;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.service.LocalizacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** O app do PCD publica aqui a própria posição. A identidade vem SEMPRE do token (nunca do corpo). */
@RestController
@RequestMapping("/api/pcd/localizacao")
@RequiredArgsConstructor
@Tag(name = "Localização do PCD (publicação)", description = "Somente PCD_VISUAL. Só guarda a posição se houver um "
        + "responsável vinculado com a permissão LOCALIZACAO; guarda apenas a última posição.")
public class PcdLocalizacaoController {

    private final LocalizacaoService service;

    @PostMapping
    @Operation(summary = "PCD publica sua localização atual")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Processado (armazenada=false se ninguém está autorizado)"),
            @ApiResponse(responseCode = "400", description = "Coordenadas inválidas"),
            @ApiResponse(responseCode = "403", description = "Conta não é PCD_VISUAL")})
    public PublicarLocalizacaoResponse publicar(@AuthenticationPrincipal User pcd, @RequestBody @Valid LocalizacaoPcdRequest request) {
        return service.publicar(pcd, request.latitude(), request.longitude(), request.precisaoMetros());
    }

    @DeleteMapping
    @Operation(summary = "PCD apaga a última localização guardada")
    public ResponseEntity<Void> apagar(@AuthenticationPrincipal User pcd) {
        service.apagar(pcd);
        return ResponseEntity.noContent().build();
    }
}
