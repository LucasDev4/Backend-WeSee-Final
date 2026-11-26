package com.atlas.backend_atlas.dto;

public record UserResponse(

        Long id,

        String nome,

        String cpf,

        String email,

        String telefone,
        String tipo

) {}