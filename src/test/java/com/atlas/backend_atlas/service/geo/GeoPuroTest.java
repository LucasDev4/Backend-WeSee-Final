package com.atlas.backend_atlas.service.geo;

import com.atlas.backend_atlas.exception.GeoException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Classes sem Spring: instruções em pt-BR, validação de coordenadas, cache com TTL e limite de frequência. */
class GeoPuroTest {

    @Test
    void instrucoesTraduzemSoOQueOOsrmDeu() {
        assertThat(StepInstructions.build("turn", "right", "Rua Afonso de Freitas", null)).isEqualTo("Vire à direita na Rua Afonso de Freitas");
        assertThat(StepInstructions.build("turn", "left", "", null)).isEqualTo("Vire à esquerda");
        assertThat(StepInstructions.build("depart", null, null, null)).isEqualTo("Comece a caminhar");
        assertThat(StepInstructions.build("arrive", "right", "", null)).isEqualTo("Você chegou ao destino, à direita");
        assertThat(StepInstructions.build("roundabout", null, "", 2)).isEqualTo("Na rotatória, pegue a 2ª saída");
        assertThat(StepInstructions.build("new name", "straight", "Largo do Arouche", null)).isEqualTo("Siga em frente no Largo do Arouche");
    }

    @Test
    void manobraDesconhecidaNaoGeraInstrucaoInventada() {
        assertThat(StepInstructions.build("notification", "right", "X", null)).isNull();
        assertThat(StepInstructions.build("tipo_novo", null, null, null)).isNull();
        assertThat(StepInstructions.build("turn", "xyz", "Rua A", null)).isNull();
        assertThat(StepInstructions.build(null, null, null, null)).isNull();
    }

    @Test
    void validaCoordenadas() {
        assertThat(GeoValidation.isValid(-23.5614, -46.6559)).isTrue();
        assertThat(GeoValidation.isValid(91, 0)).isFalse();
        assertThat(GeoValidation.isValid(0, 181)).isFalse();
        assertThat(GeoValidation.isValid(Double.NaN, 0)).isFalse();
        assertThatThrownBy(() -> GeoValidation.exigirValida(100, 0, "origem"))
                .isInstanceOfSatisfying(GeoException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(GeoValidation.distanciaMetros(-23.5614, -46.6559, -23.5632, -46.6478)).isBetween(800.0, 1000.0);
    }

    @Test
    void cacheExpiraELimitaTamanho() {
        long[] agora = {1_000_000L};
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(agora[0]); }
        };
        TtlCache<String, String> cache = new TtlCache<>(10, 3, clock);
        cache.put("a", "1");
        assertThat(cache.get("a")).contains("1");
        agora[0] += 11_000;
        assertThat(cache.get("a")).isEqualTo(Optional.empty());
        cache.put("a", "1"); cache.put("b", "2"); cache.put("c", "3"); cache.put("d", "4");
        assertThat(cache.size()).isEqualTo(3);
        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("d")).contains("4");
        TtlCache<String, String> desligado = new TtlCache<>(0, 3, clock);
        desligado.put("a", "1");
        assertThat(desligado.get("a")).isEmpty();
    }

    @Test
    void limitadorEsperaARespeitaOIntervaloERecusaFilaLonga() {
        AtomicLong nano = new AtomicLong();
        List<Long> sonos = new ArrayList<>();
        MinIntervalLimiter l = new MinIntervalLimiter(1000, nano::get, ms -> { sonos.add(ms); nano.addAndGet(ms * 1_000_000L); });
        assertThat(l.tryAcquire(3000)).isTrue();
        assertThat(sonos).isEmpty();
        assertThat(l.tryAcquire(3000)).isTrue();
        assertThat(sonos).containsExactly(1000L);

        AtomicLong parado = new AtomicLong();
        MinIntervalLimiter l2 = new MinIntervalLimiter(1000, parado::get, ms -> { });
        l2.tryAcquire(1500);
        l2.tryAcquire(1500);
        assertThat(l2.tryAcquire(1500)).isFalse();     // a 3ª teria de esperar 2 s
        parado.addAndGet(Duration.ofSeconds(5).toNanos());
        assertThat(l2.tryAcquire(1500)).isTrue();
    }
}
