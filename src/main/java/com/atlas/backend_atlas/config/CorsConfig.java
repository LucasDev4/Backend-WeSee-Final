package com.atlas.backend_atlas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Configuração de CORS liberada para desenvolvimento (Swagger, testes via
// navegador, futuro dashboard web do responsável). O app Android nativo não
// é afetado por CORS - essa regra vale para chamadas feitas por navegador.
// Antes de ir para produção, trocar allowedOriginPatterns("*") pelos
// domínios reais do frontend/dashboard.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
