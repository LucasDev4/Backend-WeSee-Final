package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.entity.TipoUsuario;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.exception.VinculoException;

/** Regras de tipo de conta. O tipo vem SEMPRE do banco (usuário carregado pelo JWT), nunca do cliente. */
final class VinculoRegras {

    private VinculoRegras() {}

    static void exigirTipo(User usuario, String tipoEsperado, String mensagem) {
        if (usuario == null || !usuario.isEnabled() || !tipoEsperado.equals(usuario.getTipo())) {
            throw VinculoException.proibido(mensagem);
        }
    }

    static boolean ehPcdAtivo(User usuario) {
        return usuario != null && usuario.isEnabled() && TipoUsuario.PCD_VISUAL.equals(usuario.getTipo());
    }
}
