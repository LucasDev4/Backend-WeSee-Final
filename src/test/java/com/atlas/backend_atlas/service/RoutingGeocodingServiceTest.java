package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.config.GeoProperties;
import com.atlas.backend_atlas.dto.CoordinateDto;
import com.atlas.backend_atlas.dto.PlaceResult;
import com.atlas.backend_atlas.dto.RouteResponse;
import com.atlas.backend_atlas.exception.GeoException;
import com.atlas.backend_atlas.service.geo.GeocodingProvider;
import com.atlas.backend_atlas.service.geo.RawRoute;
import com.atlas.backend_atlas.service.geo.RoutingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingGeocodingServiceTest {

    private static final RawRoute ROTA = new RawRoute(100, 70,
            List.of(new CoordinateDto(-23.5, -46.6), new CoordinateDto(-23.51, -46.61)), List.of());

    private static void exige(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(GeoException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    // ---------- rotas: rota de carro NUNCA vira rota de pedestre ----------

    @Test
    void semConfirmacaoDeCaminhadaARotaEhRecusadaESemChamarOProvedor() {
        RoutingProvider provider = mock(RoutingProvider.class);
        RoutingService s = new RoutingService(provider, new GeoProperties());       // walking-confirmed=false (padrão)
        exige(() -> s.calcular(-23.5, -46.6, -23.51, -46.61), HttpStatus.SERVICE_UNAVAILABLE);
        verify(provider, never()).route(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void hostSabidamenteSoCarroNaoViraCaminhadaMesmoConfirmado() {
        RoutingProvider provider = mock(RoutingProvider.class);
        GeoProperties p = new GeoProperties();
        p.getOsrm().setBaseUrl("https://router.project-osrm.org");
        p.getOsrm().setWalkingConfirmed(true);
        exige(() -> new RoutingService(provider, p).calcular(-23.5, -46.6, -23.51, -46.61), HttpStatus.SERVICE_UNAVAILABLE);
        verify(provider, never()).route(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void caminhadaConfirmadaDevolveRotaComPerfilEAvisoDeSeguranca() {
        RoutingProvider provider = mock(RoutingProvider.class);
        when(provider.providerName()).thenReturn("osrm");
        when(provider.route(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(ROTA);
        GeoProperties p = new GeoProperties();
        p.getOsrm().setWalkingConfirmed(true);
        RouteResponse r = new RoutingService(provider, p).calcular(-23.5, -46.6, -23.51, -46.61);
        assertThat(r.walking()).isTrue();
        assertThat(r.profile()).isEqualTo("foot");
        assertThat(r.warning()).isNull();
        assertThat(r.safetyNotice()).contains("não garante");
        assertThat(r.coordinates()).hasSize(2);
    }

    @Test
    void modoDemoRotulaARotaComoNaoCaminhada() {
        RoutingProvider provider = mock(RoutingProvider.class);
        when(provider.route(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(ROTA);
        GeoProperties p = new GeoProperties();
        p.getOsrm().setAllowNonWalking(true);
        RouteResponse r = new RoutingService(provider, p).calcular(-23.5, -46.6, -23.51, -46.61);
        assertThat(r.walking()).isFalse();
        assertThat(r.warning()).contains("NÃO deve ser seguido a pé");
    }

    @Test
    void validaOrigemEDestino() {
        RoutingProvider provider = mock(RoutingProvider.class);
        GeoProperties p = new GeoProperties();
        p.getOsrm().setWalkingConfirmed(true);
        RoutingService s = new RoutingService(provider, p);
        exige(() -> s.calcular(100, 0, -23.5, -46.6), HttpStatus.BAD_REQUEST);
        exige(() -> s.calcular(-23.5, -46.6, 0, 200), HttpStatus.BAD_REQUEST);
        exige(() -> s.calcular(-23.5, -46.6, -23.5, -46.6), HttpStatus.BAD_REQUEST);   // mesmo lugar
        verify(provider, never()).route(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    // ---------- geocodificação: validação e cache ----------

    private static PlaceResult lugar() {
        return new PlaceResult("Av. Paulista", "Av. Paulista, SP", "Av. Paulista", -23.56, -46.65, "secondary", "highway", null, null, null, null);
    }

    @Test
    void pesquisaValidaTextoECoordenadas() {
        GeocodingProvider provider = mock(GeocodingProvider.class);
        GeocodingService s = new GeocodingService(provider, new GeoProperties(), Clock.systemUTC());
        exige(() -> s.search(null, null, null, null), HttpStatus.BAD_REQUEST);
        exige(() -> s.search("   ", null, null, null), HttpStatus.BAD_REQUEST);
        exige(() -> s.search("ab", null, null, null), HttpStatus.BAD_REQUEST);
        exige(() -> s.search("hospital", -23.5, null, null), HttpStatus.BAD_REQUEST);
        exige(() -> s.search("hospital", 99.0, 10.0, null), HttpStatus.BAD_REQUEST);
        exige(() -> s.reverse(95, 0), HttpStatus.BAD_REQUEST);
        verify(provider, never()).search(any(), any(), any(), anyInt());
    }

    @Test
    void consultasRepetidasUsamOCacheENaoChamamOProvedorDeNovo() {
        GeocodingProvider provider = mock(GeocodingProvider.class);
        when(provider.providerName()).thenReturn("nominatim");
        when(provider.search(eq("avenida paulista"), any(), any(), anyInt())).thenReturn(List.of(lugar()));
        when(provider.search(eq("Avenida Paulista"), any(), any(), anyInt())).thenReturn(List.of(lugar()));
        GeocodingService s = new GeocodingService(provider, new GeoProperties(), Clock.systemUTC());

        s.search("Avenida Paulista", null, null, null);
        var segunda = s.search("  avenida   paulista ", null, null, null);   // mesma consulta normalizada

        assertThat(segunda.results()).hasSize(1);
        verify(provider, times(1)).search(any(), any(), any(), anyInt());
    }

    @Test
    void reversoSemEnderecoViraNaoEncontrado() {
        GeocodingProvider provider = mock(GeocodingProvider.class);
        when(provider.reverse(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        GeocodingService s = new GeocodingService(provider, new GeoProperties(), Clock.systemUTC());
        exige(() -> s.reverse(-23.5, -46.6), HttpStatus.NOT_FOUND);
    }
}
