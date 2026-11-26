package com.atlas.backend_atlas.service.geo;

import com.atlas.backend_atlas.config.GeoProperties;
import com.atlas.backend_atlas.exception.GeoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OsrmClientTest {

    private static final String OK = """
            {"code":"Ok","routes":[{"distance":1234.5,"duration":900.2,
              "geometry":{"type":"LineString","coordinates":[[-46.6559,-23.5614],[-46.6500,-23.5600],[-46.6478,-23.5632]]},
              "legs":[{"steps":[
                {"name":"Avenida Paulista","distance":600,"duration":430,"maneuver":{"type":"depart","location":[-46.6559,-23.5614]}},
                {"name":"Rua Afonso de Freitas","distance":634.5,"duration":470.2,"maneuver":{"type":"turn","modifier":"right","location":[-46.6500,-23.5600]}},
                {"name":"","distance":0,"duration":0,"maneuver":{"type":"arrive","modifier":"left","location":[-46.6478,-23.5632]}}
              ]}]}]}
            """;

    private RestTemplate rt;
    private MockRestServiceServer server;
    private OsrmClient client;

    @BeforeEach
    void setUp() {
        GeoProperties props = new GeoProperties();
        props.getOsrm().setMinIntervalMs(0);
        props.getOsrm().setBaseUrl("https://osrm.exemplo.org");
        props.getOsrm().setRoutePath("/route/v1/foot");
        rt = new RestTemplate();
        server = MockRestServiceServer.bindTo(rt).build();
        client = new OsrmClient(rt, new ObjectMapper(), props);
    }

    @Test
    void enviaLongitudeLatitudePedeGeojsonEConverteParaLatitudeLongitude() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://osrm.exemplo.org/route/v1/foot/-46.655900,-23.561400;-46.647800,-23.563200?")))
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("geometries=geojson")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        RawRoute r = client.route(-23.5614, -46.6559, -23.5632, -46.6478);

        assertThat(r.distanceMeters()).isEqualTo(1234.5);
        assertThat(r.durationSeconds()).isEqualTo(900.2);
        assertThat(r.coordinates()).hasSize(3);
        assertThat(r.coordinates().get(0).latitude()).isEqualTo(-23.5614);      // NÃO invertido
        assertThat(r.coordinates().get(0).longitude()).isEqualTo(-46.6559);
        assertThat(r.steps()).hasSize(3);
        assertThat(r.steps().get(1).instruction()).isEqualTo("Vire à direita na Rua Afonso de Freitas");
        assertThat(r.steps().get(1).location().latitude()).isEqualTo(-23.5600);
        assertThat(r.steps().get(2).instruction()).isEqualTo("Você chegou ao destino, à esquerda");
        assertThat(r.steps().get(2).streetName()).isNull();
        server.verify();
    }

    private GeoException falha(String corpo, HttpStatus status) {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(corpo));
        return org.junit.jupiter.api.Assertions.assertThrows(GeoException.class, () -> client.route(-23.5, -46.6, -23.6, -46.7));
    }

    @Test
    void semRotaViraNaoEncontrado() {
        assertThat(falha("{\"code\":\"NoRoute\",\"message\":\"x\"}", HttpStatus.BAD_REQUEST).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(falha("{\"code\":\"NoSegment\"}", HttpStatus.BAD_REQUEST).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void codigoNoRouteComHttp200TambemViraNaoEncontrado() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"code\":\"NoRoute\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.route(-23.5, -46.6, -23.6, -46.7))
                .isInstanceOfSatisfying(GeoException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void errosDoProvedorViramMensagemAmigavel() {
        assertThat(falha("", HttpStatus.TOO_MANY_REQUESTS).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(falha("", HttpStatus.BAD_GATEWAY).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void respostaInvalidaViraBadGateway() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("isto não é json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.route(-23.5, -46.6, -23.6, -46.7))
                .isInstanceOfSatisfying(GeoException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.reset();
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(
                "{\"code\":\"Ok\",\"routes\":[{\"distance\":1,\"duration\":1,\"geometry\":{\"coordinates\":[[999,999],[1,1]]}}]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.route(-23.5, -46.6, -23.6, -46.7))
                .isInstanceOfSatisfying(GeoException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
