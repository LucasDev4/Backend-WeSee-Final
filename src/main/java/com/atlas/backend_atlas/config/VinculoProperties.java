package com.atlas.backend_atlas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Parâmetros dos códigos de vinculação (prefixo "vinculo." no application.properties). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vinculo")
public class VinculoProperties {
    /** Dígitos do código (o app Android usa 6 caixas: manter igual a VINCULO_CODE_LENGTH). 8 = bem mais seguro. */
    private int codigoTamanho = 6;
    private int codigoValidadeSegundos = 300;
    /** Máximo de códigos que um PCD pode gerar na janela abaixo. */
    private int maxGeracoesJanela = 5;
    private int janelaGeracaoMinutos = 10;
    /** Máximo de tentativas falhas de um usuário na janela abaixo (depois: 429). */
    private int maxTentativasFalhas = 5;
    private int janelaTentativasMinutos = 15;
    /** Segredo do HMAC que protege os códigos no banco (env VINCULO_CODE_SECRET; padrão: jwt.secret). */
    private String codigoSegredo = "";
}
