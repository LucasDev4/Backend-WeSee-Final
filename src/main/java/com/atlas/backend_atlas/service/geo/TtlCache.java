package com.atlas.backend_atlas.service.geo;

import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cache em memória, pequeno e thread-safe, com validade (TTL) e tamanho máximo (descarta o mais antigo).
 * Existe para respeitar a política do Nominatim público (cachear consultas repetidas) sem adicionar dependência.
 * Fica só na memória do processo: nada de coordenadas é gravado em disco ou no banco por aqui.
 */
public final class TtlCache<K, V> {

    private record Entry<V>(V value, long expiresAtMillis) {}

    private final long ttlMillis;
    private final int maxEntries;
    private final Clock clock;
    private final Map<K, Entry<V>> map = new LinkedHashMap<>();

    public TtlCache(long ttlSeconds, int maxEntries, Clock clock) {
        this.ttlMillis = Math.max(0, ttlSeconds) * 1000L;
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
    }

    public synchronized Optional<V> get(K key) {
        Entry<V> e = map.get(key);
        if (e == null) return Optional.empty();
        if (e.expiresAtMillis() <= clock.millis()) {
            map.remove(key);
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    public synchronized void put(K key, V value) {
        if (ttlMillis == 0) return;                 // TTL 0 desliga o cache
        long now = clock.millis();
        map.remove(key);                            // reinserir move para o "mais novo"
        map.put(key, new Entry<>(value, now + ttlMillis));
        if (map.size() > maxEntries) evict(now);
    }

    public synchronized int size() {
        return map.size();
    }

    private void evict(long now) {
        // 1º remove os expirados; se ainda passar do limite, remove os mais antigos
        map.values().removeIf(e -> e.expiresAtMillis() <= now);
        Iterator<K> it = map.keySet().iterator();
        while (map.size() > maxEntries && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
