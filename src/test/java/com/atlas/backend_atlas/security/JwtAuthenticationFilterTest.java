package com.atlas.backend_atlas.security;

import com.atlas.backend_atlas.entity.TipoUsuario;
import com.atlas.backend_atlas.entity.User;
import com.atlas.backend_atlas.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, users);

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private MockHttpServletRequest comToken() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer token-qualquer");
        return req;
    }

    @Test
    void tokenValidoAutenticaOUsuario() throws Exception {
        User u = TestUsers.responsavel(2L);
        when(jwtService.extractEmail(anyString())).thenReturn(u.getEmail());
        when(users.loadUserByUsername(u.getEmail())).thenReturn(u);
        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        filter.doFilter(comToken(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void contaDesativadaNaoAutenticaMesmoComTokenValido() throws Exception {
        User u = TestUsers.build(3L, "Inativo", TipoUsuario.RESPONSAVEL, false);
        when(jwtService.extractEmail(anyString())).thenReturn(u.getEmail());
        when(users.loadUserByUsername(u.getEmail())).thenReturn(u);
        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        filter.doFilter(comToken(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenExpiradoOuInvalidoNaoEstouraErroNemAutentica() throws Exception {
        when(jwtService.extractEmail(anyString())).thenThrow(new RuntimeException("expirado"));
        filter.doFilter(comToken(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
