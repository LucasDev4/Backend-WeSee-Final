package com.atlas.backend_atlas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

// Beans genéricos do projeto. O RestTemplate abaixo é usado exclusivamente
// pelo AiDetectionClient para conversar com o serviço de IA (FastAPI/YOLO)
// em POST /detect, com timeouts de conexão/leitura configuráveis via
// application.properties (atlas.ai.connect-timeout-ms / read-timeout-ms).
//
// Usamos SimpleClientHttpRequestFactory (baseado em HttpURLConnection) em
// vez de deixar o Spring auto-detectar a implementação HTTP disponível no
// classpath: assim garantimos que um timeout sempre lança
// java.net.SocketTimeoutException, que é o que AiDetectionClient trata
// para diferenciar "IA lenta/timeout" de "IA indisponível".
@Configuration
public class BeanConfig {

    @Bean
    public RestTemplate aiRestTemplate(
            @Value("${atlas.ai.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${atlas.ai.read-timeout-ms}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
