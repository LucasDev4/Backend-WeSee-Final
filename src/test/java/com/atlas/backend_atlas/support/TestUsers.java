package com.atlas.backend_atlas.support;

import com.atlas.backend_atlas.entity.TipoUsuario;
import com.atlas.backend_atlas.entity.User;

public final class TestUsers {
    private TestUsers() {}

    public static User pcd(long id) { return build(id, "Ana PCD " + id, TipoUsuario.PCD_VISUAL, true); }
    public static User responsavel(long id) { return build(id, "Bruno Resp " + id, TipoUsuario.RESPONSAVEL, true); }

    public static User build(long id, String nome, String tipo, boolean ativo) {
        return User.builder().id(id).nome(nome).email("u" + id + "@teste.com").senha("hash")
                .role("USER").tipo(tipo).ativo(ativo).build();
    }
}
