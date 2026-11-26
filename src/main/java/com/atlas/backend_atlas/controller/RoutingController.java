package com.atlas.backend_atlas.controller;

import com.atlas.backend_atlas.dto.RouteResponse;
import com.atlas.backend_atlas.service.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
@Tag(name = "Rotas (OSRM)", description = "Cálculo de rota a pé. Exige JWT. Só devolve rota enquanto o servidor estiver "
        + "confirmado como perfil de PEDESTRE (veja OSRM_WALKING_CONFIRMED). A rota não garante acessibilidade nem segurança.")
public class RoutingController {

    private final RoutingService service;

    @GetMapping
    @Operation(summary = "Calcula a rota entre origem e destino",
            description = "Coordenadas em graus decimais (latitude, longitude). A resposta traz distância (m), duração (s), "
                    + "geometria (lista de latitude/longitude) e etapas. A inversão lon/lat do OSRM é feita no backend.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Rota calculada"),
            @ApiResponse(responseCode = "400", description = "Coordenada ausente/inválida ou origem = destino"),
            @ApiResponse(responseCode = "404", description = "Nenhuma rota encontrada"),
            @ApiResponse(responseCode = "429", description = "Limite de requisições ao provedor"),
            @ApiResponse(responseCode = "503", description = "Rota a pé não habilitada ou provedor indisponível"),
            @ApiResponse(responseCode = "504", description = "Provedor demorou demais")})
    public RouteResponse route(@RequestParam double originLat, @RequestParam double originLon,
                               @RequestParam double destinationLat, @RequestParam double destinationLon) {
        return service.calcular(originLat, originLon, destinationLat, destinationLon);
    }
}
