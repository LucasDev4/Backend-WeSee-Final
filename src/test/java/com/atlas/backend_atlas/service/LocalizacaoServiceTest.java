package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.PublicarLocalizacaoResponse;
import com.atlas.backend_atlas.entity.*;
import com.atlas.backend_atlas.exception.GeoException;
import com.atlas.backend_atlas.exception.VinculoException;
import com.atlas.backend_atlas.repository.LocalizacaoPcdRepository;
import com.atlas.backend_atlas.repository.VinculoRepository;
import com.atlas.backend_atlas.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalizacaoServiceTest {

    @Mock LocalizacaoPcdRepository repository;
    @Mock VinculoRepository vinculos;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneOffset.UTC);

    private LocalizacaoService service() { return new LocalizacaoService(repository, vinculos, clock); }

    private VinculoResponsavel vinculo(User resp, User pcd, Permissao... p) {
        return VinculoResponsavel.builder().id(1L).responsavel(resp).pcd(pcd).status(StatusVinculo.ATIVO)
                .permissoes(p.length == 0 ? EnumSet.noneOf(Permissao.class) : EnumSet.of(p[0], p)).criadoPor(resp).build();
    }

    @Test
    void responsavelNaoPodePublicarLocalizacao() {
        assertThatThrownBy(() -> service().publicar(TestUsers.responsavel(2L), -23.5, -46.6, 5.0))
                .isInstanceOfSatisfying(VinculoException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).save(any());
    }

    @Test
    void semResponsavelComPermissaoNadaEhGuardadoEPosicaoAntigaEhApagada() {
        User pcd = TestUsers.pcd(1L);
        when(vinculos.findByPcdIdAndStatus(1L, StatusVinculo.ATIVO))
                .thenReturn(List.of(vinculo(TestUsers.responsavel(2L), pcd, Permissao.ALERTAS)));   // sem LOCALIZACAO
        PublicarLocalizacaoResponse r = service().publicar(pcd, -23.5, -46.6, 5.0);
        assertThat(r.armazenada()).isFalse();
        verify(repository, never()).save(any());
        verify(repository).deleteById(1L);
    }

    @Test
    void comResponsavelAutorizadoGuardaAUltimaPosicaoComHorarioDoServidor() {
        User pcd = TestUsers.pcd(1L);
        when(vinculos.findByPcdIdAndStatus(1L, StatusVinculo.ATIVO))
                .thenReturn(List.of(vinculo(TestUsers.responsavel(2L), pcd, Permissao.LOCALIZACAO)));
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThat(service().publicar(pcd, -23.5614, -46.6559, 12.0).armazenada()).isTrue();

        ArgumentCaptor<LocalizacaoPcd> cap = ArgumentCaptor.forClass(LocalizacaoPcd.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getPcdId()).isEqualTo(1L);                 // id do TOKEN, não do corpo
        assertThat(cap.getValue().getLatitude()).isEqualTo(-23.5614);
        assertThat(cap.getValue().getAtualizadoEm()).isEqualTo(LocalDateTime.of(2026, 11, 25, 15, 0));
    }

    @Test
    void coordenadaInvalidaEhRecusada() {
        assertThatThrownBy(() -> service().publicar(TestUsers.pcd(1L), 123.0, 0.0, null))
                .isInstanceOfSatisfying(GeoException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void consultaCalculaAIdadeNoServidor() {
        LocalizacaoPcd l = LocalizacaoPcd.builder().pcdId(1L).latitude(-23.5).longitude(-46.6).precisaoMetros(8.0)
                .atualizadoEm(LocalDateTime.of(2026, 11, 25, 14, 58, 30)).build();
        when(repository.findById(1L)).thenReturn(Optional.of(l));
        var r = service().consultar(1L).orElseThrow();
        assertThat(r.idadeSegundos()).isEqualTo(90);
        assertThat(r.atualizadoEm()).isEqualTo("2026-11-25T14:58:30Z");
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThat(service().consultar(9L)).isEmpty();
    }
}
