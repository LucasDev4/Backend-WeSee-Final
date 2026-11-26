package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.config.VinculoProperties;
import com.atlas.backend_atlas.entity.TentativaVinculacao;
import com.atlas.backend_atlas.exception.VinculoException;
import com.atlas.backend_atlas.repository.TentativaVinculacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Limita tentativas falhas de uso de código (anti força bruta). */
@Service
@RequiredArgsConstructor
public class TentativaVinculacaoService {

    private final TentativaVinculacaoRepository repository;
    private final VinculoProperties props;
    private final Clock clock;

    @Transactional(readOnly = true)
    public void exigirSemBloqueio(Long usuarioId) {
        LocalDateTime desde = LocalDateTime.now(clock).minusMinutes(props.getJanelaTentativasMinutos());
        if (repository.countByUsuarioIdAndOcorridoEmAfter(usuarioId, desde) >= props.getMaxTentativasFalhas()) {
            throw VinculoException.tentativasExcessivas(
                    "Muitas tentativas inválidas. Aguarde alguns minutos antes de tentar novamente.");
        }
    }

    /** REQUIRES_NEW: a falha é gravada mesmo que a transação do chamador seja desfeita pela exceção. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFalha(Long usuarioId) {
        repository.save(TentativaVinculacao.builder().usuarioId(usuarioId).ocorridoEm(LocalDateTime.now(clock)).build());
    }
}
