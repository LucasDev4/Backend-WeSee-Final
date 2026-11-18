package com.atlas.backend_atlas.controller;

import com.atlas.backend_atlas.dto.UserResponse;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Rota protegida usada apenas para validar se o JwtAuthenticationFilter está
// autenticando corretamente. @AuthenticationPrincipal injeta o usuário que o
// filtro já colocou no SecurityContext - não bate no banco de novo.
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper mapper;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(mapper.toResponse(user));
    }
}
