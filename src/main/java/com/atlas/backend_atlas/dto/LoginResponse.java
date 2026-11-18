package com.atlas.backend_atlas.dto;

public record LoginResponse(

        String token,

        String nome,

        String email,

        // PCD_VISUAL ou RESPONSAVEL: o app usa para abrir a área certa
        String tipo

) {}