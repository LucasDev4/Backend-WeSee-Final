package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.VinculoResponse;
import com.atlas.backend_atlas.dto.VinculosResponse;
import com.atlas.backend_atlas.entity.*;
import com.atlas.backend_atlas.exception.VinculoException;
import com.atlas.backend_atlas.repository.CodigoVinculacaoRepository;
import com.atlas.backend_atlas.repository.VinculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Vínculos entre PCD e responsável.
 *
 * Decisão de consentimento: o código é gerado VOLUNTARIAMENTE pelo PCD autenticado, que escolhe as permissões.
 * Usar o código é o consentimento, então o vínculo nasce ATIVO (sem etapa de aprovação redundante).
 * O consentimento fica registrado: vínculo.codigo -> código (quando o PCD o gerou, para quem, até quando valia)
 * + utilizado_em/utilizado_por + autorizado_em.
 */
@Service
@RequiredArgsConstructor
public class VinculoService {

    private final VinculoRepository vinculoRepository;
    private final CodigoVinculacaoRepository codigoRepository;
    private final CodigoVinculacaoService codigoService;
    private final TentativaVinculacaoService tentativas;
    private final Clock clock;

    @Transactional
    public VinculoResponse solicitar(User responsavel, String codigoInformado) {
        VinculoRegras.exigirTipo(responsavel, TipoUsuario.RESPONSAVEL,
                "Somente contas de responsável podem usar um código de vinculação.");
        tentativas.exigirSemBloqueio(responsavel.getId());

        LocalDateTime agora = LocalDateTime.now(clock);
        String codigo = codigoService.normalizar(codigoInformado);
        if (!codigoService.formatoValido(codigo)) {
            throw falha(responsavel, VinculoException.invalido("Código inválido."));
        }
        CodigoVinculacao registro = codigoRepository.findByCodigoHash(codigoService.hash(codigo))
                .orElseThrow(() -> falha(responsavel, VinculoException.invalido("Código inválido.")));

        if (registro.getStatus() == StatusCodigo.UTILIZADO) {
            throw falha(responsavel, VinculoException.conflito("Este código já foi utilizado."));
        }
        if (registro.getStatus() == StatusCodigo.REVOGADO) {
            throw falha(responsavel, VinculoException.invalido("Este código foi cancelado. Peça um novo código."));
        }
        if (registro.getStatus() == StatusCodigo.EXPIRADO || !registro.getExpiraEm().isAfter(agora)) {
            throw falha(responsavel, VinculoException.invalido("Código expirado. Peça um novo código."));
        }

        User pcd = registro.getPcd();
        if (!VinculoRegras.ehPcdAtivo(pcd)) {
            throw falha(responsavel, VinculoException.invalido("Código inválido.")); // não revela detalhes da conta
        }
        if (vinculoRepository.existsByResponsavelIdAndPcdIdAndStatus(responsavel.getId(), pcd.getId(), StatusVinculo.ATIVO)) {
            throw VinculoException.conflito("Você já está vinculado a esta pessoa.");
        }

        // consumo atômico: só uma requisição consegue transformar DISPONIVEL em UTILIZADO
        int consumido = codigoRepository.consumir(registro.getId(), StatusCodigo.UTILIZADO, StatusCodigo.DISPONIVEL, agora, responsavel);
        if (consumido != 1) {
            throw falha(responsavel, VinculoException.conflito("Este código já foi utilizado."));
        }

        VinculoResponsavel vinculo = vinculoRepository.save(VinculoResponsavel.builder()
                .responsavel(responsavel).pcd(pcd).status(StatusVinculo.ATIVO)
                .permissoes(EnumSet.copyOf(registro.getPermissoes().isEmpty() ? Permissao.PADRAO : registro.getPermissoes()))
                .codigo(registro).criadoPor(responsavel)
                .criadoEm(agora).atualizadoEm(agora).autorizadoEm(agora).build());
        return VinculoResponse.de(vinculo, responsavel);
    }

    @Transactional(readOnly = true)
    public VinculosResponse listar(User usuario, boolean incluirRevogados) {
        List<VinculoResponsavel> todos = TipoUsuario.RESPONSAVEL.equals(usuario.getTipo())
                ? vinculoRepository.findByResponsavelIdOrderByCriadoEmDesc(usuario.getId())
                : vinculoRepository.findByPcdIdOrderByCriadoEmDesc(usuario.getId());
        return new VinculosResponse(todos.stream()
                .filter(v -> incluirRevogados || v.getStatus() == StatusVinculo.ATIVO)
                .map(v -> VinculoResponse.de(v, usuario)).toList());
    }

    @Transactional(readOnly = true)
    public VinculoResponse buscar(User usuario, Long id) {
        return VinculoResponse.de(doParticipante(usuario, id), usuario);
    }

    /** Qualquer participante pode encerrar. O acesso é bloqueado na hora (cada consulta checa o vínculo ATIVO). */
    @Transactional
    public void revogar(User usuario, Long id) {
        VinculoResponsavel vinculo = doParticipante(usuario, id);
        if (vinculo.getStatus() == StatusVinculo.REVOGADO) return; // idempotente
        LocalDateTime agora = LocalDateTime.now(clock);
        vinculo.setStatus(StatusVinculo.REVOGADO);
        vinculo.setRevogadoEm(agora);
        vinculo.setRevogadoPor(usuario);
        vinculo.setAtualizadoEm(agora);
        vinculoRepository.save(vinculo);
    }

    /** Somente o PCD controla o que é compartilhado; o responsável nunca altera as próprias permissões. */
    @Transactional
    public VinculoResponse atualizarPermissoes(User pcd, Long id, Set<Permissao> novas) {
        VinculoRegras.exigirTipo(pcd, TipoUsuario.PCD_VISUAL, "Somente a pessoa PCD pode alterar as permissões do vínculo.");
        VinculoResponsavel vinculo = doParticipante(pcd, id);
        if (vinculo.getStatus() != StatusVinculo.ATIVO) {
            throw VinculoException.conflito("O vínculo não está ativo.");
        }
        vinculo.setPermissoes(novas == null || novas.isEmpty() ? EnumSet.noneOf(Permissao.class) : EnumSet.copyOf(novas));
        vinculo.setAtualizadoEm(LocalDateTime.now(clock));
        return VinculoResponse.de(vinculoRepository.save(vinculo), pcd);
    }

    /** Vínculo só é visível para quem participa dele; para os demais responde 404 (não revela que existe). */
    private VinculoResponsavel doParticipante(User usuario, Long id) {
        VinculoResponsavel vinculo = vinculoRepository.findById(id)
                .orElseThrow(() -> VinculoException.naoEncontrado("Vínculo não encontrado."));
        boolean participa = vinculo.getResponsavel().getId().equals(usuario.getId())
                || vinculo.getPcd().getId().equals(usuario.getId());
        if (!participa) throw VinculoException.naoEncontrado("Vínculo não encontrado.");
        return vinculo;
    }

    private VinculoException falha(User responsavel, VinculoException erro) {
        tentativas.registrarFalha(responsavel.getId());
        return erro;
    }
}
