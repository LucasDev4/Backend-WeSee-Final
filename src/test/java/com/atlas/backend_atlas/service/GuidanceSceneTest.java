package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceSceneTest {

    private final PriorityRules rules = new PriorityRules();
    private final GuidanceService service = new GuidanceService(rules);

    private DetectedObject obj(String cls, Position pos, Proximity prox) {
        return new DetectedObject(cls, rules.label(cls), 0.9, pos, prox, rules.resolve(cls, prox));
    }

    @Test
    void catalogoCobreAsClassesDoModeloWeSee() {
        List<String> classes = List.of("Bike", "Building", "Car", "Person", "Stairs", "Traffic sign", "Electrical Pole", "Road",
                "Motorcycle", "Dustbin", "Dog", "Manhole", "Tree", "Guard rail", "Pedestrian crosswalk", "Truck", "Bus", "Bench",
                "Traffic Cone", "Fire hydrant", "Traffic Barrel", "Plant Pot", "Electrical Box", "Chair", "Bicycle Rack");
        for (String c : classes) {
            // nenhuma classe do modelo pode cair no rótulo em inglês
            assertThat(rules.label(c)).as(c).isNotEqualToIgnoringCase(c);
            assertThat(rules.plural(c)).as(c + " plural").isNotBlank();
        }
        assertThat(rules.label("Traffic sign")).isEqualTo("Placa de trânsito");
        assertThat(rules.isContext("Road")).isTrue();
        assertThat(rules.isContext("Building")).isTrue();
        assertThat(rules.isContext("Person")).isFalse();
    }

    @Test
    void descreveATodaAAcenaComContagemDirecaoEProximidade() {
        String s = service.describeScene(List.of(
                obj("Person", Position.CENTER, Proximity.MEDIUM), obj("Person", Position.CENTER, Proximity.FAR),
                obj("Car", Position.RIGHT, Proximity.NEAR), obj("Stairs", Position.LEFT, Proximity.MEDIUM)));
        assertThat(s).startsWith("Vejo ").endsWith(".");
        assertThat(s).contains("duas pessoas à sua frente").contains("um carro muito próximo à sua direita").contains("uma escada à sua esquerda");
    }

    @Test
    void feminino_e_plural_concordam() {
        String s = service.describeScene(List.of(obj("Chair", Position.LEFT, Proximity.NEAR), obj("Chair", Position.LEFT, Proximity.NEAR),
                obj("Chair", Position.LEFT, Proximity.NEAR)));
        assertThat(s).isEqualTo("Vejo várias cadeiras muito próximas à sua esquerda.");
    }

    @Test
    void semObjetosDizCaminhoLivre() {
        assertThat(service.describeScene(List.of())).contains("Caminho livre");
        assertThat(service.generate(List.of()).priority()).isEqualTo(Priority.LOW);
    }

    @Test
    void ruaEPredioDescrevemACenaMasNaoViramAlerta() {
        List<DetectedObject> so = List.of(obj("Road", Position.CENTER, Proximity.NEAR), obj("Building", Position.LEFT, Proximity.NEAR));
        assertThat(service.describeScene(so)).contains("uma rua à sua frente").doesNotContain("muito próxim");
        assertThat(service.generate(so).priority()).isEqualTo(Priority.LOW);
        assertThat(service.generate(so).direction()).isNull();
    }

    @Test
    void alertaPrincipalEOObjetoMaisUrgente() {
        List<DetectedObject> todos = List.of(obj("Road", Position.CENTER, Proximity.NEAR), obj("Chair", Position.LEFT, Proximity.FAR),
                obj("Person", Position.CENTER, Proximity.NEAR));
        GuidanceResponse g = service.generate(todos);
        assertThat(g.message()).isEqualTo("Pessoa muito próxima à sua frente");
        assertThat(g.priority()).isEqualTo(Priority.HIGH);
    }
}
