package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.LoginRequest;
import com.atlas.backend_atlas.dto.LoginResponse;
import com.atlas.backend_atlas.dto.RegisterRequest;
import com.atlas.backend_atlas.dto.UserResponse;
import com.atlas.backend_atlas.entity.TipoUsuario;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.mapper.UserMapper;
import com.atlas.backend_atlas.repository.UserRepository;
import com.atlas.backend_atlas.security.JwtService;
import com.atlas.backend_atlas.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Garante que cadastro e login continuam independentes de qualquer vínculo (PCD sem responsável e vice-versa). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceVinculoCompatTest {

    @Mock UserRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserMapper mapper;
    @Mock AuthenticationManager authenticationManager;
    @InjectMocks AuthService service;

    private User registrar(String tipo) {
        RegisterRequest req = new RegisterRequest("Fulano", "fulano@teste.com", null, "123456", null, tipo);
        when(repository.existsByEmail("fulano@teste.com")).thenReturn(false);
        when(mapper.toEntity(req)).thenReturn(User.builder().nome("Fulano").tipo(tipo).build());
        when(passwordEncoder.encode("123456")).thenReturn("hash");
        when(mapper.toResponse(any(User.class))).thenReturn(new UserResponse(1L, "Fulano", null, "fulano@teste.com", null, tipo));

        service.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void pcdCadastraSemInformarResponsavel() {
        User salvo = registrar(TipoUsuario.PCD_VISUAL);
        assertThat(salvo.getTipo()).isEqualTo(TipoUsuario.PCD_VISUAL);
        assertThat(salvo.isAtivo()).isTrue();
        assertThat(salvo.getSenha()).isEqualTo("hash");
    }

    @Test
    void responsavelCadastraSemInformarPcd() {
        User salvo = registrar(TipoUsuario.RESPONSAVEL);
        assertThat(salvo.getTipo()).isEqualTo(TipoUsuario.RESPONSAVEL);
        assertThat(salvo.isAtivo()).isTrue();
    }

    @Test
    void semTipoInformadoAContaNasceComoPcdVisual() {
        User salvo = registrar(null);
        assertThat(salvo.getTipo()).isEqualTo(TipoUsuario.PCD_VISUAL);
    }

    @Test
    void loginDoPcdEDoResponsavelFuncionaSemVinculos() {
        for (User u : new User[]{TestUsers.pcd(1L), TestUsers.responsavel(2L)}) {
            when(repository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
            when(jwtService.generateToken(u.getEmail())).thenReturn("token-" + u.getId());
            LoginResponse r = service.login(new LoginRequest(u.getEmail(), "123456"));
            assertThat(r.token()).isEqualTo("token-" + u.getId());
            assertThat(r.tipo()).isEqualTo(u.getTipo());
        }
    }
}
