package com.atlas.backend_atlas.service.geo;

import com.atlas.backend_atlas.config.GeoProperties;
import com.atlas.backend_atlas.dto.PlaceResult;
import com.atlas.backend_atlas.dto.ReverseGeocodeResponse;
import com.atlas.backend_atlas.exception.GeoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NominatimClientTest {

    private MockRestServiceServer server;
    private NominatimClient client;

    @BeforeEach
    void setUp() {
        GeoProperties props = new GeoProperties();
        props.getNominatim().setMinIntervalMs(0);
        props.getNominatim().setBaseUrl("https://nominatim.exemplo.org");
        props.getNominatim().setContactEmail("contato@wesee.exemplo");
        RestTemplate rt = new RestTemplate();
        server = MockRestServiceServer.bindTo(rt).build();
        client = new NominatimClient(rt, new ObjectMapper(), props);
    }

    @Test
    void pesquisaConverteLatLonTextoParaNumeroEMontaEnderecoCurto() {
        server.expect(requestTo(containsString("/search?q=Avenida%20Paulista")))
                .andExpect(requestTo(containsString("format=jsonv2")))
                .andExpect(requestTo(containsString("countrycodes=br")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"lat":"-23.5614","lon":"-46.6559","category":"highway","type":"secondary","name":"Avenida Paulista",
                          "display_name":"Avenida Paulista, Bela Vista, São Paulo, Brasil",
                          "address":{"road":"Avenida Paulista","suburb":"Bela Vista","city":"São Paulo","state":"São Paulo"}},
                         {"lat":"lixo","lon":"-46.6","display_name":"descartado"}]
                        """, MediaType.APPLICATION_JSON));

        List<PlaceResult> r = client.search("Avenida Paulista", null, null, 5);

        assertThat(r).hasSize(1);                                   // o item malformado é ignorado
        assertThat(r.get(0).latitude()).isEqualTo(-23.5614);
        assertThat(r.get(0).longitude()).isEqualTo(-46.6559);
        assertThat(r.get(0).shortAddress()).isEqualTo("Avenida Paulista - Bela Vista");
        assertThat(r.get(0).city()).isEqualTo("São Paulo");
        assertThat(r.get(0).category()).isEqualTo("highway");
        server.verify();
    }

    @Test
    void textoComCaracteresEspeciaisVaiCodificado() {
        server.expect(requestTo(containsString("q=Rua%20A%26B%2B1"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.search("Rua A&B+1", null, null, 5)).isEmpty();
        server.verify();
    }

    @Test
    void reversoDevolveEnderecoEErroDoNominatimViraVazio() {
        server.expect(requestTo(containsString("/reverse?lat=-23.561400&lon=-46.655900")))
                .andRespond(withSuccess("""
                        {"display_name":"1578, Avenida Paulista, Bela Vista, São Paulo, Brasil",
                         "address":{"house_number":"1578","road":"Avenida Paulista","suburb":"Bela Vista","city":"São Paulo","state":"São Paulo","postcode":"01310-200","country":"Brasil"}}
                        """, MediaType.APPLICATION_JSON));
        ReverseGeocodeResponse r = client.reverse(-23.5614, -46.6559).orElseThrow();
        assertThat(r.shortAddress()).isEqualTo("Avenida Paulista, 1578 - Bela Vista");
        assertThat(r.latitude()).isEqualTo(-23.5614);

        server.reset();
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"error\":\"Unable to geocode\"}", MediaType.APPLICATION_JSON));
        assertThat(client.reverse(0.0, 0.0)).isEmpty();
    }

    @Test
    void falhasExternasViramGeoException() {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.search("hospital", null, null, 5))
                .isInstanceOfSatisfying(GeoException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        server.reset();
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> client.search("hospital", null, null, 5))
                .isInstanceOfSatisfying(GeoException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.reset();
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"nao\":\"lista\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.search("hospital", null, null, 5))
                .isInstanceOfSatisfying(GeoException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
