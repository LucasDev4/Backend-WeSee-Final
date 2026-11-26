package com.atlas.backend_atlas.service.geo;

/**
 * Provedor de rotas. Hoje: {@link OsrmClient}. Trocar o servidor (OSRM próprio com perfil foot) é só configuração;
 * trocar de tecnologia é uma nova implementação desta interface.
 */
public interface RoutingProvider {

    String providerName();

    /** Calcula a rota. Sempre recebe e devolve (latitude, longitude): a inversão para o formato do provedor é interna. */
    RawRoute route(double originLat, double originLon, double destinationLat, double destinationLon);
}
