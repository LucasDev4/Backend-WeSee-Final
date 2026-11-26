package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.entity.*;
import com.atlas.backend_atlas.exception.VinculoException;
import com.atlas.backend_atlas.repository.VinculoRepository;
import com.atlas.backend_atlas.support.TestUsers;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutorizacaoVinculoServiceTest {

    @Mock VinculoRepository repository;

    private AutorizacaoVinculoService service() { return new AutorizacaoVinculoService(repository); }

    private static void assertForbidden(ThrowableAssert.ThrowingCallable chamada) {
        assertThatThrownBy(chamada).isInstanceOfSatisfying(VinculoException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private VinculoResponsavel vinculoAtivo(User resp, User pcd, Permissao... permissoes) {
        VinculoResponsavel v = VinculoResponsavel.builder().id(7L).responsavel(resp).pcd(pcd).status(StatusVinculo.ATIVO)
                .permissoes(permissoes.length == 0 ? EnumSet.noneOf(Permissao.class) : EnumSet.of(permissoes[0], permissoes))
                .criadoPor(resp).build();
        when(repository.findByResponsavelIdAndPcdIdAndStatus(resp.getId(), pcd.getId(), StatusVinculo.ATIVO)).thenReturn(Optional.of(v));
        return v;
    }

    @Test
    void responsavelVinculadoComPermissaoAcessa() {
        User resp = TestUsers.responsavel(2L); User pcd = TestUsers.pcd(1L);
        VinculoResponsavel v = vinculoAtivo(resp, pcd, Permissao.LOCALIZACAO, Permissao.ALERTAS);
        assertThat(service().exigirAcesso(resp, 1L, Permissao.LOCALIZACAO)).isSameAs(v);
        assertThat(service().exigirAcesso(resp, 1L, Permissao.ALERTAS)).isSameAs(v);
    }

    @Test
    void responsavelNaoVinculadoNaoAcessaNenhumaCategoria() {
        User resp = TestUsers.responsavel(2L);
        when(repository.findByResponsavelIdAndPcdIdAndStatus(2L, 1L, StatusVinculo.ATIVO)).thenReturn(Optional.empty());
        for (Permissao p : Permissao.values()) {   // localização, status, alertas e histórico
            assertForbidden(() -> service().exigirAcesso(resp, 1L, p));
        }
    }

    @Test
    void alterarOIdDoPcdNaUrlNaoDaAcessoAOutraPessoa() {
        User resp = TestUsers.responsavel(2L);
        vinculoAtivo(resp, TestUsers.pcd(1L), Permissao.LOCALIZACAO);          // Bruno é responsável por Ana (id 1)
        when(repository.findByResponsavelIdAndPcdIdAndStatus(2L, 9L, StatusVinculo.ATIVO)).thenReturn(Optional.empty()); // Carlos (id 9)
        assertForbidden(() -> service().exigirAcesso(resp, 9L, Permissao.LOCALIZACAO));
    }

    @Test
    void vinculoRevogadoBloqueiaOAcessoImediatamente() {
        User resp = TestUsers.responsavel(2L);
        // o repositório só devolve vínculos ATIVO; revogado => nenhum resultado
        when(repository.findByResponsavelIdAndPcdIdAndStatus(2L, 1L, StatusVinculo.ATIVO)).thenReturn(Optional.empty());
        assertForbidden(() -> service().exigirAcesso(resp, 1L, Permissao.LOCALIZACAO));
    }

    @Test
    void permissaoRevogadaPeloPcdBloqueiaSemNovoLogin() {
        User resp = TestUsers.responsavel(2L); User pcd = TestUsers.pcd(1L);
        VinculoResponsavel v = vinculoAtivo(resp, pcd, Permissao.LOCALIZACAO);
        assertThat(service().exigirAcesso(resp, 1L, Permissao.LOCALIZACAO)).isSameAs(v);
        v.setPermissoes(EnumSet.noneOf(Permissao.class));                       // PCD retira o compartilhamento
        assertForbidden(() -> service().exigirAcesso(resp, 1L, Permissao.LOCALIZACAO));
    }

    @Test
    void permissaoNaoConcedidaEhNegada() {
        User resp = TestUsers.responsavel(2L);
        vinculoAtivo(resp, TestUsers.pcd(1L), Permissao.ALERTAS);
        assertForbidden(() -> service().exigirAcesso(resp, 1L, Permissao.HISTORICO_ROTAS));
    }

    @Test
    void usuarioPcdNaoUsaOperacoesDeResponsavel() {
        assertForbidden(() -> service().exigirAcesso(TestUsers.pcd(1L), 1L, Permissao.LOCALIZACAO));
    }

    @Test
    void pcdDesativadoNaoTemDadosExpostos() {
        User resp = TestUsers.responsavel(2L);
        vinculoAtivo(resp, TestUsers.build(1L, "Ana", TipoUsuario.PCD_VISUAL, false), Permissao.LOCALIZACAO);
        assertForbidden(() -> service().exigirAcesso(resp, 1L, Permissao.LOCALIZACAO));
    }
}
