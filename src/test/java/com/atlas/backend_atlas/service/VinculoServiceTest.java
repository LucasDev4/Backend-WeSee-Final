package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.config.VinculoProperties;
import com.atlas.backend_atlas.dto.VinculoResponse;
import com.atlas.backend_atlas.dto.VinculosResponse;
import com.atlas.backend_atlas.entity.*;
import com.atlas.backend_atlas.exception.VinculoException;
import com.atlas.backend_atlas.repository.CodigoVinculacaoRepository;
import com.atlas.backend_atlas.repository.VinculoRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VinculoServiceTest {

    static final String CODIGO = "482731";

    @Mock VinculoRepository vinculoRepository;
    @Mock CodigoVinculacaoRepository codigoRepository;
    @Mock TentativaVinculacaoService tentativas;

    VinculoProperties props;
    Clock clock;
    CodigoVinculacaoService codigoService;
    VinculoService service;
    LocalDateTime agora;

    @BeforeEach
    void setUp() {
        props = new VinculoProperties();
        props.setCodigoSegredo("segredo-de-teste-com-mais-de-trinta-e-dois-caracteres");
        clock = Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneId.of("UTC"));
        agora = LocalDateTime.now(clock);
        codigoService = new CodigoVinculacaoService(codigoRepository, props, clock);
        service = new VinculoService(vinculoRepository, codigoRepository, codigoService, tentativas, clock);

        when(vinculoRepository.save(any(VinculoResponsavel.class))).thenAnswer(i -> {
            VinculoResponsavel v = i.getArgument(0);
            if (v.getId() == null) v.setId(100L);
            return v;
        });
        when(vinculoRepository.existsByResponsavelIdAndPcdIdAndStatus(any(), any(), any())).thenReturn(false);
        when(codigoRepository.consumir(any(), eq(StatusCodigo.UTILIZADO), eq(StatusCodigo.DISPONIVEL), any(), any())).thenReturn(1);
    }

    private static void assertStatus(ThrowableAssert.ThrowingCallable chamada, HttpStatus esperado) {
        assertThatThrownBy(chamada).isInstanceOfSatisfying(VinculoException.class,
                e -> assertThat(e.getStatus()).isEqualTo(esperado));
    }

    private CodigoVinculacao registrar(String codigo, long id, User pcd, StatusCodigo status, LocalDateTime expira) {
        CodigoVinculacao c = CodigoVinculacao.builder().id(id).codigoHash(codigoService.hash(codigo)).pcd(pcd)
                .status(status).permissoes(EnumSet.of(Permissao.LOCALIZACAO, Permissao.ALERTAS))
                .criadoEm(agora.minusMinutes(1)).expiraEm(expira).build();
        when(codigoRepository.findByCodigoHash(codigoService.hash(codigo))).thenReturn(Optional.of(c));
        return c;
    }

    private VinculoResponsavel vinculo(long id, User resp, User pcd, StatusVinculo status) {
        return VinculoResponsavel.builder().id(id).responsavel(resp).pcd(pcd).status(status)
                .permissoes(EnumSet.of(Permissao.LOCALIZACAO)).criadoPor(resp).criadoEm(agora).atualizadoEm(agora).build();
    }

    // ---------- solicitar (usar o código) ----------

    @Test
    void responsavelComCodigoValidoCriaVinculoAtivoComConsentimentoRegistrado() {
        User pcd = TestUsers.pcd(1L); User resp = TestUsers.responsavel(2L);
        CodigoVinculacao codigo = registrar(CODIGO, 10L, pcd, StatusCodigo.DISPONIVEL, agora.plusMinutes(4));

        VinculoResponse r = service.solicitar(resp, "482 731");

        assertThat(r.status()).isEqualTo(StatusVinculo.ATIVO);
        assertThat(r.relacionadoId()).isEqualTo(1L);
        assertThat(r.nomeRelacionado()).isEqualTo(pcd.getNome());
        assertThat(r.tipoRelacionado()).isEqualTo(TipoUsuario.PCD_VISUAL);
        assertThat(r.permissoes()).containsExactlyInAnyOrder(Permissao.LOCALIZACAO, Permissao.ALERTAS);
        assertThat(r.autorizadoEm()).isEqualTo(agora);
        verify(codigoRepository).consumir(eq(10L), eq(StatusCodigo.UTILIZADO), eq(StatusCodigo.DISPONIVEL), eq(agora), eq(resp));
        ArgumentCaptor<VinculoResponsavel> salvo = ArgumentCaptor.forClass(VinculoResponsavel.class);
        verify(vinculoRepository).save(salvo.capture());
        assertThat(salvo.getValue().getCodigo()).isSameAs(codigo);       // consentimento: aponta para o código do PCD
        assertThat(salvo.getValue().getCriadoPor()).isSameAs(resp);
    }

    @Test
    void codigoInexistenteEInvalidoEContaComoTentativaFalha() {
        when(codigoRepository.findByCodigoHash(any())).thenReturn(Optional.empty());
        assertStatus(() -> service.solicitar(TestUsers.responsavel(2L), CODIGO), HttpStatus.BAD_REQUEST);
        verify(tentativas).registrarFalha(2L);
        verify(vinculoRepository, never()).save(any());
    }

    @Test
    void codigoComFormatoErradoEInvalido() {
        assertStatus(() -> service.solicitar(TestUsers.responsavel(2L), "123"), HttpStatus.BAD_REQUEST);
        verify(tentativas).registrarFalha(2L);
    }

    @Test
    void codigoExpiradoERecusado() {
        registrar(CODIGO, 10L, TestUsers.pcd(1L), StatusCodigo.DISPONIVEL, agora.minusSeconds(1));
        assertStatus(() -> service.solicitar(TestUsers.responsavel(2L), CODIGO), HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.solicitar(TestUsers.responsavel(2L), CODIGO)).hasMessageContaining("expirado");
        verify(codigoRepository, never()).consumir(any(), any(), any(), any(), any());
    }

    @Test
    void codigoJaUtilizadoNaoPodeSerReutilizado() {
        registrar(CODIGO, 10L, TestUsers.pcd(1L), StatusCodigo.UTILIZADO, agora.plusMinutes(4));
        assertStatus(() -> service.solicitar(TestUsers.responsavel(2L), CODIGO), HttpStatus.CONFLICT);
    }

    @Test
    void codigoRevogadoNaoPodeSerUtilizado() {
        registrar(CODIGO, 10L, TestUsers.pcd(1L), StatusCodigo.REVOGADO, agora.plusMinutes(4));
        assertStatus(() -> service.solicitar(TestUsers.responsavel(2L), CODIGO), HttpStatus.BAD_REQUEST);
    }

    @Test
    void tentativasExcessivasSaoBloqueadasAntesDeQualquerConsulta() {
        doThrow(VinculoException.tentativasExcessivas("Muitas tentativas")).when(tentativas).exigirSemBloqueio(2L);
        assertStatus(() -> service.solicitar(TestUsers.responsavel(2L), CODIGO), HttpStatus.TOO_MANY_REQUESTS);
        verify(codigoRepository, never()).findByCodigoHash(any());
    }

    @Test
    void pcdNaoPodeUsarCodigoDeVinculacao() {
        assertStatus(() -> service.solicitar(TestUsers.pcd(1L), CODIGO), HttpStatus.FORBIDDEN);
        verify(codigoRepository, never()).findByCodigoHash(any());
    }

    @Test
    void naoVinculaContasDoMesmoTipo() {
        // um código só pode ter sido gerado por PCD; se por algum motivo apontar para um não-PCD, é recusado
        User outroResponsavel = TestUsers.responsavel(5L);
        registrar(CODIGO, 10L, outroResponsavel, StatusCodigo.DISPONIVEL, agora.plusMinutes(4));
        assertStatus(() -> service.solicitar(TestUsers.responsavel(2L), CODIGO), HttpStatus.BAD_REQUEST);
        verify(vinculoRepository, never()).save(any());
    }

    @Test
    void naoCriaVinculoAtivoDuplicado() {
        User pcd = TestUsers.pcd(1L); User resp = TestUsers.responsavel(2L);
        registrar(CODIGO, 10L, pcd, StatusCodigo.DISPONIVEL, agora.plusMinutes(4));
        when(vinculoRepository.existsByResponsavelIdAndPcdIdAndStatus(2L, 1L, StatusVinculo.ATIVO)).thenReturn(true);
        assertStatus(() -> service.solicitar(resp, CODIGO), HttpStatus.CONFLICT);
        verify(codigoRepository, never()).consumir(any(), any(), any(), any(), any());
    }

    @Test
    void seOConsumoAtomicoFalharNenhumVinculoEhCriado() {
        registrar(CODIGO, 10L, TestUsers.pcd(1L), StatusCodigo.DISPONIVEL, agora.plusMinutes(4));
        when(codigoRepository.consumir(any(), any(), any(), any(), any())).thenReturn(0); // outra requisição consumiu primeiro
        assertStatus(() -> service.solicitar(TestUsers.responsavel(2L), CODIGO), HttpStatus.CONFLICT);
        verify(vinculoRepository, never()).save(any());
    }

    @Test
    void duasRequisicoesSimultaneasConsomemOMesmoCodigoUmaUnicaVez() throws Exception {
        registrar(CODIGO, 10L, TestUsers.pcd(1L), StatusCodigo.DISPONIVEL, agora.plusMinutes(4));
        AtomicInteger linhas = new AtomicInteger(1); // o UPDATE condicional afeta 1 linha só para o primeiro
        when(codigoRepository.consumir(any(), any(), any(), any(), any())).thenAnswer(i -> linhas.getAndSet(0));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Boolean>> tarefas = List.of(
                () -> tentar(TestUsers.responsavel(2L)), () -> tentar(TestUsers.responsavel(3L)));
        int sucessos = 0;
        for (Future<Boolean> f : pool.invokeAll(tarefas)) if (f.get()) sucessos++;
        pool.shutdown();
        assertThat(sucessos).isEqualTo(1);
    }

    private boolean tentar(User resp) {
        try { service.solicitar(resp, CODIGO); return true; } catch (VinculoException e) { return false; }
    }

    @Test
    void umPcdPodeTerMaisDeUmResponsavelEUmResponsavelMaisDeUmPcd() {
        User pcd1 = TestUsers.pcd(1L); User pcd2 = TestUsers.pcd(4L);
        User resp1 = TestUsers.responsavel(2L); User resp2 = TestUsers.responsavel(3L);
        registrar("111111", 11L, pcd1, StatusCodigo.DISPONIVEL, agora.plusMinutes(4));
        registrar("222222", 12L, pcd1, StatusCodigo.DISPONIVEL, agora.plusMinutes(4));
        registrar("333333", 13L, pcd2, StatusCodigo.DISPONIVEL, agora.plusMinutes(4));

        assertThat(service.solicitar(resp1, "111111").relacionadoId()).isEqualTo(1L);
        assertThat(service.solicitar(resp2, "222222").relacionadoId()).isEqualTo(1L);   // mesmo PCD, 2 responsáveis
        assertThat(service.solicitar(resp1, "333333").relacionadoId()).isEqualTo(4L);   // mesmo responsável, 2 PCDs
    }

    // ---------- listar / consultar ----------

    @Test
    void responsavelSemVinculosPermaneceSemVinculosEPcdTambem() {
        when(vinculoRepository.findByResponsavelIdOrderByCriadoEmDesc(2L)).thenReturn(List.of());
        when(vinculoRepository.findByPcdIdOrderByCriadoEmDesc(1L)).thenReturn(List.of());
        assertThat(service.listar(TestUsers.responsavel(2L), false).vinculos()).isEmpty();
        assertThat(service.listar(TestUsers.pcd(1L), false).vinculos()).isEmpty();
    }

    @Test
    void listaMostraOOutroLadoSemDadosPessoais() {
        User pcd = TestUsers.pcd(1L); User resp = TestUsers.responsavel(2L);
        when(vinculoRepository.findByPcdIdOrderByCriadoEmDesc(1L)).thenReturn(List.of(
                vinculo(7L, resp, pcd, StatusVinculo.ATIVO), vinculo(8L, resp, pcd, StatusVinculo.REVOGADO)));
        VinculosResponse ativos = service.listar(pcd, false);
        assertThat(ativos.vinculos()).hasSize(1);
        assertThat(ativos.vinculos().get(0).tipoRelacionado()).isEqualTo(TipoUsuario.RESPONSAVEL);
        assertThat(ativos.vinculos().get(0).nomeRelacionado()).isEqualTo(resp.getNome());
        assertThat(service.listar(pcd, true).vinculos()).hasSize(2);
    }

    @Test
    void terceiroNaoConsultaVinculoAlterandoOId() {
        when(vinculoRepository.findById(7L)).thenReturn(Optional.of(vinculo(7L, TestUsers.responsavel(2L), TestUsers.pcd(1L), StatusVinculo.ATIVO)));
        assertStatus(() -> service.buscar(TestUsers.responsavel(99L), 7L), HttpStatus.NOT_FOUND);
    }

    // ---------- revogar / permissões ----------

    @Test
    void participanteRevogaOVinculoSemApagarNenhumaConta() {
        User pcd = TestUsers.pcd(1L); User resp = TestUsers.responsavel(2L);
        VinculoResponsavel v = vinculo(7L, resp, pcd, StatusVinculo.ATIVO);
        when(vinculoRepository.findById(7L)).thenReturn(Optional.of(v));

        service.revogar(pcd, 7L);

        assertThat(v.getStatus()).isEqualTo(StatusVinculo.REVOGADO);
        assertThat(v.getRevogadoEm()).isEqualTo(agora);
        assertThat(v.getRevogadoPor()).isSameAs(pcd);
        assertThat(pcd.isEnabled()).isTrue();
        assertThat(resp.isEnabled()).isTrue();
        verify(vinculoRepository).save(v);
    }

    @Test
    void terceiroNaoRevogaVinculoAlheio() {
        VinculoResponsavel v = vinculo(7L, TestUsers.responsavel(2L), TestUsers.pcd(1L), StatusVinculo.ATIVO);
        when(vinculoRepository.findById(7L)).thenReturn(Optional.of(v));
        assertStatus(() -> service.revogar(TestUsers.pcd(50L), 7L), HttpStatus.NOT_FOUND);
        assertThat(v.getStatus()).isEqualTo(StatusVinculo.ATIVO);
    }

    @Test
    void somenteOPcdAlteraPermissoes() {
        User pcd = TestUsers.pcd(1L); User resp = TestUsers.responsavel(2L);
        VinculoResponsavel v = vinculo(7L, resp, pcd, StatusVinculo.ATIVO);
        when(vinculoRepository.findById(7L)).thenReturn(Optional.of(v));

        VinculoResponse r = service.atualizarPermissoes(pcd, 7L, Set.of(Permissao.ALERTAS));
        assertThat(r.permissoes()).containsExactly(Permissao.ALERTAS);

        // o responsável não concede permissões a si próprio
        assertStatus(() -> service.atualizarPermissoes(resp, 7L, EnumSet.allOf(Permissao.class)), HttpStatus.FORBIDDEN);
    }
}
