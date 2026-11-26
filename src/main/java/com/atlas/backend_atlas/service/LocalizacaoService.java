package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.LocalizacaoPcdResponse;
import com.atlas.backend_atlas.dto.PublicarLocalizacaoResponse;
import com.atlas.backend_atlas.entity.LocalizacaoPcd;
import com.atlas.backend_atlas.entity.Permissao;
import com.atlas.backend_atlas.entity.StatusVinculo;
import com.atlas.backend_atlas.entity.TipoUsuario;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.repository.LocalizacaoPcdRepository;
import com.atlas.backend_atlas.repository.VinculoRepository;
import com.atlas.backend_atlas.service.geo.GeoValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Localização compartilhada do PCD com o responsável.
 * <ul>
 *   <li><b>Publicar</b>: só o próprio PCD (identidade vem do token). A posição só é gravada se existir pelo menos um
 *       vínculo ATIVO com a permissão LOCALIZACAO; caso contrário nada fica guardado (e qualquer posição antiga é apagada).</li>
 *   <li><b>Consultar</b>: SEM autorização aqui. Quem chama (ResponsavelDadosController) deve passar antes por
 *       {@link AutorizacaoVinculoService#exigirAcesso}.</li>
 *   <li>Guarda-se só a última posição (sem histórico de trajeto).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LocalizacaoService {

    private final LocalizacaoPcdRepository repository;
    private final VinculoRepository vinculoRepository;
    private final Clock clock;

    @Transactional
    public PublicarLocalizacaoResponse publicar(User pcd, double latitude, double longitude, Double precisaoMetros) {
        VinculoRegras.exigirTipo(pcd, TipoUsuario.PCD_VISUAL, "Somente a pessoa PCD visual compartilha a própria localização.");
        GeoValidation.exigirValida(latitude, longitude, "localização");

        if (!temResponsavelAutorizado(pcd.getId())) {
            repository.deleteById(pcd.getId());   // não mantém posição guardada sem ninguém autorizado a vê-la
            return new PublicarLocalizacaoResponse(false, "Nenhum responsável com permissão de localização: nada foi guardado.");
        }

        LocalizacaoPcd atual = repository.findById(pcd.getId()).orElseGet(() -> {
            LocalizacaoPcd nova = new LocalizacaoPcd();
            nova.setPcdId(pcd.getId());
            return nova;
        });
        atual.setLatitude(latitude);
        atual.setLongitude(longitude);
        atual.setPrecisaoMetros(precisaoMetros);
        atual.setAtualizadoEm(LocalDateTime.now(clock));
        repository.save(atual);
        return new PublicarLocalizacaoResponse(true, "Localização atualizada.");
    }

    /** O PCD apaga a última posição guardada. */
    @Transactional
    public void apagar(User pcd) {
        VinculoRegras.exigirTipo(pcd, TipoUsuario.PCD_VISUAL, "Somente a pessoa PCD visual gerencia a própria localização.");
        repository.deleteById(pcd.getId());
    }

    /** Última posição do PCD, ou vazio. NÃO autoriza: chame depois de AutorizacaoVinculoService.exigirAcesso. */
    @Transactional(readOnly = true)
    public Optional<LocalizacaoPcdResponse> consultar(Long pcdId) {
        return repository.findById(pcdId).map(this::paraResposta);
    }

    private LocalizacaoPcdResponse paraResposta(LocalizacaoPcd l) {
        Instant quando = l.getAtualizadoEm().atZone(clock.getZone()).toInstant();
        long idade = Math.max(0, Duration.between(quando, clock.instant()).getSeconds());
        return new LocalizacaoPcdResponse(l.getLatitude(), l.getLongitude(), l.getPrecisaoMetros(), quando.toString(), idade);
    }

    private boolean temResponsavelAutorizado(Long pcdId) {
        return vinculoRepository.findByPcdIdAndStatus(pcdId, StatusVinculo.ATIVO).stream()
                .anyMatch(v -> v.getPermissoes() != null && v.getPermissoes().contains(Permissao.LOCALIZACAO)
                        && v.getResponsavel() != null && v.getResponsavel().isEnabled());
    }
}
