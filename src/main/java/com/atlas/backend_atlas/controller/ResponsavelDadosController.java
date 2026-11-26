package com.atlas.backend_atlas.controller;

import com.atlas.backend_atlas.dto.DadosPcdResponse;
import com.atlas.backend_atlas.entity.Permissao;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.service.AutorizacaoVinculoService;
import com.atlas.backend_atlas.service.LocalizacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dados do PCD para o responsável. A AUTORIZAÇÃO está completa (vínculo ativo + permissão, checados a cada
 * chamada). A LOCALIZAÇÃO já é coletada (POST /api/pcd/localizacao) e devolvida aqui após autorizar; status, alertas e
 * histórico de rotas ainda não são coletados e respondem disponivel=false, sem inventar dados.
 */
@RestController
@RequestMapping("/api/responsavel/pcds/{pcdId}")
@RequiredArgsConstructor
@Tag(name = "Dados do PCD (responsável)", description = "Exige vínculo ATIVO e a permissão correspondente. "
        + "Alterar o pcdId na URL não dá acesso a outra pessoa.")
public class ResponsavelDadosController {

    private static final String SEM_DADOS = "Dado ainda não disponível: depende da coleta pelo aplicativo do PCD (etapa futura).";

    private final AutorizacaoVinculoService autorizacao;
    private final LocalizacaoService localizacaoService;

    @GetMapping("/localizacao")
    @Operation(summary = "Última localização do PCD (permissão LOCALIZACAO)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Autorizado (disponivel=false se o aparelho ainda não enviou a posição)"),
            @ApiResponse(responseCode = "403", description = "Sem vínculo ativo ou sem permissão")})
    public DadosPcdResponse localizacao(@AuthenticationPrincipal User responsavel, @PathVariable Long pcdId) {
        autorizacao.exigirAcesso(responsavel, pcdId, Permissao.LOCALIZACAO);   // sempre ANTES de ler qualquer dado
        return localizacaoService.consultar(pcdId)
                .map(l -> DadosPcdResponse.disponivel(pcdId, Permissao.LOCALIZACAO, l))
                .orElseGet(() -> DadosPcdResponse.indisponivel(pcdId, Permissao.LOCALIZACAO,
                        "O aparelho da pessoa ainda não enviou a localização.", null));
    }

    @GetMapping("/status")
    @Operation(summary = "Status do dispositivo do PCD (permissão STATUS_DISPOSITIVO)")
    public DadosPcdResponse status(@AuthenticationPrincipal User responsavel, @PathVariable Long pcdId) {
        autorizacao.exigirAcesso(responsavel, pcdId, Permissao.STATUS_DISPOSITIVO);
        return DadosPcdResponse.indisponivel(pcdId, Permissao.STATUS_DISPOSITIVO, SEM_DADOS, null);
    }

    @GetMapping("/alertas")
    @Operation(summary = "Alertas do PCD (permissão ALERTAS)")
    public DadosPcdResponse alertas(@AuthenticationPrincipal User responsavel, @PathVariable Long pcdId) {
        autorizacao.exigirAcesso(responsavel, pcdId, Permissao.ALERTAS);
        return DadosPcdResponse.indisponivel(pcdId, Permissao.ALERTAS, SEM_DADOS, List.of());
    }

    @GetMapping("/historico")
    @Operation(summary = "Histórico de rotas do PCD (permissão HISTORICO_ROTAS)")
    public DadosPcdResponse historico(@AuthenticationPrincipal User responsavel, @PathVariable Long pcdId) {
        autorizacao.exigirAcesso(responsavel, pcdId, Permissao.HISTORICO_ROTAS);
        return DadosPcdResponse.indisponivel(pcdId, Permissao.HISTORICO_ROTAS, SEM_DADOS, List.of());
    }
}
