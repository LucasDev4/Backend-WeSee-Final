package com.atlas.backend_atlas.dto;

import com.atlas.backend_atlas.entity.Permissao;
import com.atlas.backend_atlas.entity.StatusVinculo;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.entity.VinculoResponsavel;

import java.time.LocalDateTime;
import java.util.Set;

/** Visão de um vínculo para um dos participantes: mostra sempre "o outro lado". Sem e-mail, CPF ou telefone. */
public record VinculoResponse(
        Long id,
        Long relacionadoId,
        String nomeRelacionado,
        String tipoRelacionado,
        StatusVinculo status,
        Set<Permissao> permissoes,
        LocalDateTime criadoEm,
        LocalDateTime autorizadoEm,
        LocalDateTime revogadoEm
) {
    public static VinculoResponse de(VinculoResponsavel v, User quemConsulta) {
        boolean consultaEhResponsavel = v.getResponsavel().getId().equals(quemConsulta.getId());
        User outro = consultaEhResponsavel ? v.getPcd() : v.getResponsavel();
        return new VinculoResponse(
                v.getId(), outro.getId(), outro.getNome(), outro.getTipo(), v.getStatus(),
                v.getPermissoes(), v.getCriadoEm(), v.getAutorizadoEm(), v.getRevogadoEm());
    }
}
