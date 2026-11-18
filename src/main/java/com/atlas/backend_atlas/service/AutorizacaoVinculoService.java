package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.entity.*;
import com.atlas.backend_atlas.exception.VinculoException;
import com.atlas.backend_atlas.repository.VinculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponto ÚNICO de autorização dos dados de um PCD. Toda rota que devolve dados de uma pessoa PCD para um
 * responsável deve chamar {@link #exigirAcesso} (checa o vínculo ATIVO e a permissão A CADA requisição,
 * então uma revogação vale imediatamente, sem novo login). Ser "RESPONSAVEL" não concede acesso a ninguém.
 */
@Service
@RequiredArgsConstructor
public class AutorizacaoVinculoService {

    private static final String NEGADO = "Acesso negado.";

    private final VinculoRepository vinculoRepository;

    @Transactional(readOnly = true)
    public VinculoResponsavel exigirAcesso(User responsavel, Long pcdId, Permissao permissao) {
        if (responsavel == null || !responsavel.isEnabled() || !TipoUsuario.RESPONSAVEL.equals(responsavel.getTipo())) {
            throw VinculoException.proibido(NEGADO);
        }
        // mesma resposta para "PCD inexistente" e "sem vínculo": não permite descobrir contas pelo ID
        VinculoResponsavel vinculo = vinculoRepository
                .findByResponsavelIdAndPcdIdAndStatus(responsavel.getId(), pcdId, StatusVinculo.ATIVO)
                .orElseThrow(() -> VinculoException.proibido(NEGADO));
        if (!VinculoRegras.ehPcdAtivo(vinculo.getPcd())) {
            throw VinculoException.proibido(NEGADO);
        }
        if (vinculo.getPermissoes() == null || !vinculo.getPermissoes().contains(permissao)) {
            throw VinculoException.proibido("A pessoa não compartilhou este tipo de informação com você.");
        }
        return vinculo;
    }
}
