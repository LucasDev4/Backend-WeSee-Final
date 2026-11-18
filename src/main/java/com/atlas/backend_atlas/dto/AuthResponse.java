package com.atlas.backend_atlas.dto;

public record AuthResponse(
        String token,
        String tipo,
        Long userId,
        String nome,
        String role
) {
    public AuthResponse(String token, Long userId, String nome, String role) {
        this(token, "Bearer", userId, nome, role);
    }
}