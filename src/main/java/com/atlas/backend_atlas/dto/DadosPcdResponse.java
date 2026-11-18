package com.atlas.backend_atlas.dto;

import com.atlas.backend_atlas.entity.Permissao;

/**
 * Resposta dos dados protegidos do PCD. [disponivel] = false quando o dado ainda não é coletado/armazenado
 * pelo sistema (o acesso foi AUTORIZADO, mas não há o que mostrar). Nunca contém dados inventados.
 */
public record DadosPcdResponse(Long pcdId, String categoria, boolean disponivel, String mensagem, Object dados) {

    public static DadosPcdResponse indisponivel(Long pcdId, Permissao categoria, String mensagem, Object dadosVazios) {
        return new DadosPcdResponse(pcdId, categoria.name(), false, mensagem, dadosVazios);
    }
}
