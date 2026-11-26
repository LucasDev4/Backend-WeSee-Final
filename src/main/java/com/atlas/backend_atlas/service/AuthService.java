package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.LoginRequest;
import com.atlas.backend_atlas.dto.LoginResponse;
import com.atlas.backend_atlas.dto.RegisterRequest;
import com.atlas.backend_atlas.dto.UserResponse;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.exception.EmailAlreadyExistsException;
import com.atlas.backend_atlas.mapper.UserMapper;
import com.atlas.backend_atlas.repository.UserRepository;
import com.atlas.backend_atlas.security.JwtService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final UserMapper mapper;

    private final AuthenticationManager authenticationManager;

    /**
     * Cadastro
     */
    public UserResponse register(RegisterRequest request) {

        String emailNormalizado = normalizarEmail(request.email());

        if (repository.existsByEmail(emailNormalizado)) {
            throw new EmailAlreadyExistsException(emailNormalizado);
        }

        User user = mapper.toEntity(request);

        // O UserMapper (MapStruct) só copia os campos presentes no RegisterRequest.
        // role, ativo e createdAt precisam ser definidos aqui manualmente,
        // senão role fica null (viola NOT NULL do banco) e ativo fica false
        // (usuário criado desativado, o que bloquearia o login).
        user.setEmail(emailNormalizado);
        user.setSenha(passwordEncoder.encode(request.senha()));
        user.setRole("USER");
        if (user.getTipo() == null || user.getTipo().isBlank()) {
            user.setTipo("PCD_VISUAL");
        }
        user.setAtivo(true);
        user.setCreatedAt(LocalDateTime.now());

        repository.save(user);

        return mapper.toResponse(user);
    }

    /**
     * Login
     */
    public LoginResponse login(LoginRequest request) {

        String emailNormalizado = normalizarEmail(request.email());

        authenticationManager.authenticate(

                new UsernamePasswordAuthenticationToken(

                        emailNormalizado,

                        request.senha()

                )

        );

        User user = repository.findByEmail(emailNormalizado)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Usuário não encontrado."));

        String token = jwtService.generateToken(user.getEmail());

        return new LoginResponse(

                token,

                user.getNome(),

                user.getEmail(),
                user.getTipo()

        );

    }

    // "Lucas@GMAIL.com" e "lucas@gmail.com" precisam ser tratados como o
    // mesmo e-mail, tanto na checagem de duplicidade quanto no login.
    private String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

}