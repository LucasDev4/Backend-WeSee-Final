package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.config.GeoProperties;
import com.atlas.backend_atlas.dto.RouteResponse;
import com.atlas.backend_atlas.exception.GeoException;
import com.atlas.backend_atlas.service.geo.GeoValidation;
import com.atlas.backend_atlas.service.geo.RawRoute;
import com.atlas.backend_atlas.service.geo.RoutingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cálculo de rotas. REGRA CENTRAL DO WESEE: uma rota de carro nunca é apresentada como rota de pedestre.
 * O servidor só é tratado como "caminhada" se o operador confirmou (OSRM_WALKING_CONFIRMED=true) E o host não é um
 * servidor sabidamente só-carro (router.project-osrm.org ignora o segmento foot/bike da URL e devolve a rota de carro).
 * Sem isso, a rota é recusada (503), a menos que OSRM_ALLOW_NON_WALKING_DEMO=true (só desenvolvimento): nesse caso
 * a resposta vem rotulada walking=false, com aviso.
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    static final String SAFETY_NOTICE = "A rota é calculada a partir do mapa e não garante calçadas acessíveis, travessias "
            + "seguras nem ausência de obstáculos ou obras. Continue atento ao ambiente.";
    static final String NON_WALKING_WARNING = "ATENÇÃO: o servidor de rotas não está confirmado como perfil de pedestre. "
            + "Este trajeto pode ser de carro e NÃO deve ser seguido a pé.";

    private final RoutingProvider provider;
    private final GeoProperties props;

    public RoutingService(RoutingProvider provider, GeoProperties props) {
        this.provider = provider;
        this.props = props;
        GeoProperties.Osrm o = props.getOsrm();
        if (o.isWalkingConfirmed() && o.isKnownCarOnlyHost()) {
            log.error("OSRM_WALKING_CONFIRMED=true, mas o host configurado só serve rotas de CARRO (ignora o perfil foot). "
                    + "Rotas a pé ficam BLOQUEADAS. Use um OSRM com perfil de caminhada.");
        } else if (!o.isWalkingConfirmed()) {
            log.warn("Rotas a pé desabilitadas: defina OSRM_WALKING_CONFIRMED=true somente depois de confirmar que o "
                    + "servidor OSRM calcula rotas de pedestre (scripts/verify_osrm_profile.py).");
        }
    }

    public RouteResponse calcular(double originLat, double originLon, double destinationLat, double destinationLon) {
        GeoValidation.exigirValida(originLat, originLon, "origem");
        GeoValidation.exigirValida(destinationLat, destinationLon, "destino");
        if (GeoValidation.distanciaMetros(originLat, originLon, destinationLat, destinationLon) < 5.0) {
            throw GeoException.invalido("A origem e o destino são o mesmo lugar.");
        }

        GeoProperties.Osrm cfg = props.getOsrm();
        boolean walking = cfg.isWalkingEffective();
        if (!walking && !cfg.isAllowNonWalking()) {
            throw GeoException.indisponivel("O cálculo de rotas a pé ainda não está habilitado neste servidor.");
        }

        RawRoute raw = provider.route(originLat, originLon, destinationLat, destinationLon);
        return new RouteResponse(
                provider.providerName(),
                walking ? cfg.getProfileLabel() : "unverified",
                walking,
                walking ? null : NON_WALKING_WARNING,
                SAFETY_NOTICE,
                props.getAttribution(),
                raw.distanceMeters(), raw.durationSeconds(),
                raw.coordinates(), raw.steps());
    }
}
