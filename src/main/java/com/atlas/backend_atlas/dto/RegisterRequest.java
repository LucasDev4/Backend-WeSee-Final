package com.atlas.backend_atlas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 150)
        String email,

        @Pattern(regexp = "\\d{11}", message = "CPF deve conter exatamente 11 números")
        String cpf,

        @NotBlank(message = "Senha é obrigatória")
        @Pattern(regexp = "\\d{6}", message = "A senha deve conter exatamente 6 números")
        String senha,

        @Pattern(regexp = "\\d{10,11}", message = "Telefone deve conter DDD + número, apenas dígitos (10 ou 11 números)")
        String telefone,

        @Pattern(regexp = "PCD_VISUAL|RESPONSAVEL", message = "Tipo deve ser PCD_VISUAL ou RESPONSAVEL")
        String tipo

) {}