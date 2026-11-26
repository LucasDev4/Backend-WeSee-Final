package com.atlas.backend_atlas.controller;

import com.atlas.backend_atlas.dto.GeocodingSearchResponse;
import com.atlas.backend_atlas.dto.ReverseGeocodeResponse;
import com.atlas.backend_atlas.service.GeocodingService;
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
@RequestMapping("/api/geocoding")
@RequiredArgsConstructor
@Tag(name = "Endereços (Nominatim)", description = "Pesquisa de endereços e geocodificação reversa. Exige JWT. "
        + "A pesquisa deve ser disparada quando o usuário CONFIRMA (não a cada tecla): o Nominatim público não permite autocomplete.")
public class GeocodingController {

    private final GeocodingService service;

    @GetMapping("/search")
    @Operation(summary = "Pesquisa endereço ou local",
            description = "lat/lon (opcionais, juntos) só dão preferência a resultados próximos. limit: 1 a 10 (padrão 5).")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista (pode ser vazia se nada for encontrado)"),
            @ApiResponse(responseCode = "400", description = "Texto curto/ausente ou coordenadas inválidas"),
            @ApiResponse(responseCode = "429", description = "Limite de requisições ao provedor"),
            @ApiResponse(responseCode = "503", description = "Provedor indisponível")})
    public GeocodingSearchResponse search(@RequestParam String q,
                                          @RequestParam(required = false) Double lat,
                                          @RequestParam(required = false) Double lon,
                                          @RequestParam(required = false) Integer limit) {
        return service.search(q, lat, lon, limit);
    }

    @GetMapping("/reverse")
    @Operation(summary = "Endereço aproximado de uma coordenada")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Endereço encontrado"),
            @ApiResponse(responseCode = "400", description = "Coordenadas inválidas"),
            @ApiResponse(responseCode = "404", description = "Nenhum endereço para o ponto")})
    public ReverseGeocodeResponse reverse(@RequestParam double lat, @RequestParam double lon) {
        return service.reverse(lat, lon);
    }
}
