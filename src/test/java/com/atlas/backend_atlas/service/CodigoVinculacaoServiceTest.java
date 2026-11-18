package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.config.VinculoProperties;
import com.atlas.backend_atlas.dto.GerarCodigoResponse;
import com.atlas.backend_atlas.entity.*;
import com.atlas.backend_atlas.exception.VinculoException;
import com.atlas.backend_atlas.repository.CodigoVinculacaoRepository;
import com.atlas.backend_atlas.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.assertj.core.api.ThrowableAssert;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CodigoVinculacaoServiceTest {

    @Mock CodigoVinculacaoRepository repository;
    VinculoProperties props;
    Clock clock;
    CodigoVinculacaoService service;

    @BeforeEach
    void setUp() {
        props = new VinculoProperties();
        props.setCodigoSegredo("segredo-de-teste-com-mais-de-trinta-e-dois-caracteres");
        clock = Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneId.of("UTC"));
        service = new CodigoVinculacaoService(repository, props, clock);
        when(repository.save(any(CodigoVinculacao.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.existsByCodigoHash(any())).thenReturn(false);
        when(repository.countByPcdIdAndCriadoEmAfter(any(), any())).thenReturn(0L);
    }

    private static void assertStatus(ThrowableAssert.ThrowingCallable chamada, HttpStatus esperado) {
        assertThatThrownBy(chamada).isInstanceOfSatisfying(VinculoException.class,
                e -> assertThat(e.getStatus()).isEqualTo(esperado));
    }

    @Test
    void pcdAutenticadoGeraCodigoSeguroDeUsoUnicoComValidade() {
        GerarCodigoResponse resp = service.gerar(TestUsers.pcd(1L), null);

        assertThat(resp.codigo()).matches("\\d{6}");
        assertThat(resp.validadeSegundos()).isEqualTo(300);
        assertThat(resp.expiraEm()).isEqualTo(LocalDateTime.of(2026, 10, 17, 15, 5, 0));
        assertThat(resp.permissoes()).containsExactlyInAnyOrderElementsOf(Permissao.PADRAO);

        ArgumentCaptor<CodigoVinculacao> captor = ArgumentCaptor.forClass(CodigoVinculacao.class);
        verify(repository).save(captor.capture());
        CodigoVinculacao salvo = captor.getValue();
        assertThat(salvo.getStatus()).isEqualTo(StatusCodigo.DISPONIVEL);
        assertThat(salvo.getPcd().getId()).isEqualTo(1L);
        // o código em texto puro NÃO é persistido: só o HMAC
        assertThat(salvo.getCodigoHash()).isNotEqualTo(resp.codigo()).hasSize(64).isEqualTo(service.hash(resp.codigo()));
    }

    @Test
    void pcdPodeEscolherPermissoes() {
        GerarCodigoResponse resp = service.gerar(TestUsers.pcd(1L), EnumSet.of(Permissao.LOCALIZACAO, Permissao.HISTORICO_ROTAS));
        assertThat(resp.permissoes()).containsExactlyInAnyOrder(Permissao.LOCALIZACAO, Permissao.HISTORICO_ROTAS);
    }

    @Test
    void gerarNovoCodigoCancelaOsAnterioresDisponiveis() {
        service.gerar(TestUsers.pcd(1L), null);
        verify(repository).revogarDisponiveis(eq(1L), any(), eq(StatusCodigo.REVOGADO), eq(StatusCodigo.DISPONIVEL));
    }

    @Test
    void responsavelNaoGeraCodigoEmNomeDeUmPcd() {
        assertStatus(() -> service.gerar(TestUsers.responsavel(2L), null), HttpStatus.FORBIDDEN);
        verify(repository, never()).save(any());
    }

    @Test
    void contaDesativadaNaoGeraCodigo() {
        User desativado = TestUsers.build(3L, "Inativo", TipoUsuario.PCD_VISUAL, false);
        assertStatus(() -> service.gerar(desativado, null), HttpStatus.FORBIDDEN);
    }

    @Test
    void geracaoExcessivaRetorna429() {
        when(repository.countByPcdIdAndCriadoEmAfter(eq(1L), any())).thenReturn(5L);
        assertStatus(() -> service.gerar(TestUsers.pcd(1L), null), HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void normalizaCodigoDigitadoComEspacosOuTracos() {
        assertThat(service.normalizar("4827 3195")).isEqualTo("48273195");
        assertThat(service.normalizar("4827-3195")).isEqualTo("48273195");
        assertThat(service.formatoValido("482731")).isTrue();
        assertThat(service.formatoValido("123")).isFalse();
    }
}
