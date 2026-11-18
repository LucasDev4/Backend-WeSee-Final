package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.config.VinculoProperties;
import com.atlas.backend_atlas.dto.GerarCodigoResponse;
import com.atlas.backend_atlas.entity.*;
import com.atlas.backend_atlas.exception.VinculoException;
import com.atlas.backend_atlas.repository.CodigoVinculacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Set;

/**
 * Geração dos códigos de vinculação (somente PCD autenticado). O código em texto puro nunca é gravado nem
 * logado: o banco guarda HMAC-SHA256(código, segredo do servidor), o que impede recuperar o código mesmo
 * com um vazamento do banco (um hash simples de um código curto seria quebrado em segundos).
 */
@Service
@RequiredArgsConstructor
public class CodigoVinculacaoService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CodigoVinculacaoRepository repository;
    private final VinculoProperties props;
    private final Clock clock;

    @Transactional
    public GerarCodigoResponse gerar(User pcd, Set<Permissao> solicitadas) {
        VinculoRegras.exigirTipo(pcd, TipoUsuario.PCD_VISUAL,
                "Somente usuários PCD visual podem gerar códigos de vinculação.");

        LocalDateTime agora = LocalDateTime.now(clock);
        long recentes = repository.countByPcdIdAndCriadoEmAfter(pcd.getId(), agora.minusMinutes(props.getJanelaGeracaoMinutos()));
        if (recentes >= props.getMaxGeracoesJanela()) {
            throw VinculoException.tentativasExcessivas("Muitos códigos gerados em pouco tempo. Aguarde alguns minutos.");
        }

        // um código novo cancela os anteriores que ainda estavam disponíveis
        repository.revogarDisponiveis(pcd.getId(), agora, StatusCodigo.REVOGADO, StatusCodigo.DISPONIVEL);

        Set<Permissao> permissoes = (solicitadas == null || solicitadas.isEmpty())
                ? EnumSet.copyOf(Permissao.PADRAO) : EnumSet.copyOf(solicitadas);

        for (int tentativa = 0; tentativa < 10; tentativa++) {
            String codigo = gerarDigitos();
            String hash = hash(codigo);
            if (repository.existsByCodigoHash(hash)) continue; // colisão (raríssima): sorteia outro
            LocalDateTime expira = agora.plusSeconds(props.getCodigoValidadeSegundos());
            repository.save(CodigoVinculacao.builder()
                    .codigoHash(hash).pcd(pcd).status(StatusCodigo.DISPONIVEL).permissoes(permissoes)
                    .criadoEm(agora).expiraEm(expira).build());
            return new GerarCodigoResponse(codigo, expira, props.getCodigoValidadeSegundos(), permissoes);
        }
        throw new VinculoException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível gerar um código agora. Tente novamente.");
    }

    /** Aceita "4827 3195" ou "4827-3195": mantém só os dígitos. */
    public String normalizar(String informado) {
        return informado == null ? "" : informado.replaceAll("\\D", "");
    }

    public boolean formatoValido(String codigoNormalizado) {
        return codigoNormalizado != null && codigoNormalizado.length() == props.getCodigoTamanho();
    }

    public String hash(String codigo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getCodigoSegredo().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao proteger o código de vinculação", e);
        }
    }

    private String gerarDigitos() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < props.getCodigoTamanho(); i++) sb.append(RANDOM.nextInt(10));
        return sb.toString();
    }
}
