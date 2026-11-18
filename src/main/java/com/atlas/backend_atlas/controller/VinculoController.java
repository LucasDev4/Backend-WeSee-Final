package com.atlas.backend_atlas.controller;

import com.atlas.backend_atlas.dto.*;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.service.CodigoVinculacaoService;
import com.atlas.backend_atlas.service.VinculoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vinculos")
@RequiredArgsConstructor
@Tag(name = "Vínculos", description = "Vínculo opcional entre PCD visual e responsável. Todas as rotas exigem JWT. "
        + "A identidade vem SEMPRE do token (nunca do corpo da requisição).")
public class VinculoController {

    private final CodigoVinculacaoService codigoService;
    private final VinculoService vinculoService;

    @PostMapping("/codigos")
    @Operation(summary = "PCD gera um código temporário de vinculação",
            description = "Somente PCD_VISUAL. O código (6 dígitos, uso único, validade padrão de 5 min) é devolvido só aqui e "
                    + "só o hash fica no banco. Gerar um novo cancela os anteriores. Corpo opcional com as permissões a compartilhar "
                    + "(padrão: LOCALIZACAO, STATUS_DISPOSITIVO, ALERTAS). O código é uma AUTORIZAÇÃO de vinculação, não prova de parentesco ou tutela legal.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Código gerado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Conta não é PCD_VISUAL"),
            @ApiResponse(responseCode = "429", description = "Códigos demais em pouco tempo")})
    public ResponseEntity<GerarCodigoResponse> gerarCodigo(
            @AuthenticationPrincipal User pcd, @RequestBody(required = false) GerarCodigoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(codigoService.gerar(pcd, request == null ? null : request.permissoes()));
    }

    @PostMapping("/solicitar")
    @Operation(summary = "Responsável usa o código de um PCD",
            description = "Somente RESPONSAVEL. Valida código, validade e uso único (consumo atômico). Como o PCD gerou o código "
                    + "voluntariamente, o vínculo é criado já ATIVO (o consentimento fica registrado no vínculo). "
                    + "Não é possível escolher o PCD: só o código identifica a pessoa.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vínculo ativado"),
            @ApiResponse(responseCode = "400", description = "Código inválido, expirado ou cancelado"),
            @ApiResponse(responseCode = "403", description = "Conta não é RESPONSAVEL"),
            @ApiResponse(responseCode = "409", description = "Código já utilizado ou vínculo já existente"),
            @ApiResponse(responseCode = "429", description = "Tentativas inválidas demais")})
    public ResponseEntity<VinculoResponse> solicitar(
            @AuthenticationPrincipal User responsavel, @RequestBody @Valid SolicitarVinculoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vinculoService.solicitar(responsavel, request.codigo()));
    }

    @GetMapping
    @Operation(summary = "Lista os vínculos do usuário autenticado",
            description = "PCD vê seus responsáveis; responsável vê seus PCDs. Só ativos, a menos que incluirRevogados=true.")
    public VinculosResponse listar(@AuthenticationPrincipal User usuario,
                                   @RequestParam(defaultValue = "false") boolean incluirRevogados) {
        return vinculoService.listar(usuario, incluirRevogados);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um vínculo", description = "Somente participantes; para os demais responde 404.")
    public VinculoResponse buscar(@AuthenticationPrincipal User usuario, @PathVariable Long id) {
        return vinculoService.buscar(usuario, id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Encerra (revoga) um vínculo",
            description = "Qualquer participante. O acesso do responsável é bloqueado imediatamente. Nenhuma conta é apagada.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Revogado"),
            @ApiResponse(responseCode = "404", description = "Vínculo inexistente ou de terceiros")})
    public ResponseEntity<Void> revogar(@AuthenticationPrincipal User usuario, @PathVariable Long id) {
        vinculoService.revogar(usuario, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/permissoes")
    @Operation(summary = "PCD define o que o responsável pode ver",
            description = "Somente o PCD do vínculo. Vale na hora, sem novo login. [] = não compartilhar nada.")
    public VinculoResponse atualizarPermissoes(@AuthenticationPrincipal User pcd, @PathVariable Long id,
                                               @RequestBody @Valid AtualizarPermissoesRequest request) {
        return vinculoService.atualizarPermissoes(pcd, id, request.permissoes());
    }
}
