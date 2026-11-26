package com.atlas.backend_atlas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Parâmetros dos provedores de mapas (prefixo "atlas.geo." no application.properties).
 * Todas as URLs externas ficam AQUI (e nas variáveis de ambiente), nunca espalhadas pelo código.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "atlas.geo")
public class GeoProperties {

    private Nominatim nominatim = new Nominatim();
    private Osrm osrm = new Osrm();
    /** Texto de atribuição exigido pelas licenças do OpenStreetMap (o app exibe esse texto). */
    private String attribution = "Dados © colaboradores do OpenStreetMap";

    @Getter
    @Setter
    public static class Nominatim {
        private String baseUrl = "https://nominatim.openstreetmap.org";
        /** Identifica a aplicação (a política do Nominatim público exige User-Agent identificável). */
        private String userAgent = "WeSee-Backend/1.0";
        /** E-mail de contato da aplicação (enviado no parâmetro "email"; recomendado pela política). */
        private String contactEmail = "";
        /** Códigos de país separados por vírgula para restringir a busca (vazio = mundo todo). */
        private String countryCodes = "br";
        private String language = "pt-BR";
        /** Intervalo mínimo entre chamadas ao provedor. O Nominatim público permite no máximo 1 req/s por aplicação. */
        private long minIntervalMs = 1100;
        /** Quanto uma requisição aceita esperar pela sua vez antes de responder 429. */
        private long maxQueueWaitMs = 3000;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 8000;
        private long searchCacheTtlSeconds = 21600;
        private long reverseCacheTtlSeconds = 86400;
        private int cacheMaxEntries = 2000;
    }

    @Getter
    @Setter
    public static class Osrm {
        private String baseUrl = "https://routing.openstreetmap.de";
        /**
         * Caminho do serviço "route" ANTES das coordenadas. Depende do servidor:
         * FOSSGIS (padrão): /routed-foot/route/v1/driving  (o trecho final "driving" é uma peculiaridade deles; o
         * grafo é o de pedestres). OSRM próprio com perfil foot: /route/v1/foot.
         */
        private String routePath = "/routed-foot/route/v1/driving";
        /** Rótulo devolvido ao app em "profile" (informativo). */
        private String profileLabel = "foot";
        /**
         * O operador AFIRMA que baseUrl+routePath realmente calculam rotas de PEDESTRE. Padrão false de propósito:
         * só mude para true depois de conferir (scripts/verify_osrm_profile.py).
         */
        private boolean walkingConfirmed = false;
        /** Apenas desenvolvimento: permite devolver rota que NÃO é de caminhada, sempre rotulada como tal. */
        private boolean allowNonWalking = false;
        private long minIntervalMs = 1100;
        private long maxQueueWaitMs = 3000;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 10000;

        /** Servidores que, comprovadamente, só têm o grafo de CARRO e ignoram o segmento foot/bike da URL. */
        public boolean isKnownCarOnlyHost() {
            try {
                String host = URI.create(baseUrl.trim()).getHost();
                return host != null && host.toLowerCase(Locale.ROOT).equals("router.project-osrm.org");
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        /** true somente se o operador confirmou E o host não é um servidor sabidamente só-carro. */
        public boolean isWalkingEffective() {
            return walkingConfirmed && !isKnownCarOnlyHost();
        }
    }
}
