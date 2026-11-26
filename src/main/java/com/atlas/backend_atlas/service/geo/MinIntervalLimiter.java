package com.atlas.backend_atlas.service.geo;

import java.util.function.LongSupplier;

/**
 * Garante um intervalo mínimo entre chamadas a um provedor externo (ex.: 1 req/s do Nominatim público).
 * Cada chamada "reserva" o próximo horário livre; se a espera passar de maxWaitMillis, recusa (quem chamou
 * responde 429 em vez de empilhar requisições). Vale para a aplicação inteira, não por usuário.
 */
public final class MinIntervalLimiter {

    /** Dorme por N milissegundos (injetável para testes sem esperar de verdade). */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final long minIntervalNanos;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private long nextFreeNanos;
    private boolean started;

    public MinIntervalLimiter(long minIntervalMillis) {
        this(minIntervalMillis, System::nanoTime, Thread::sleep);
    }

    public MinIntervalLimiter(long minIntervalMillis, LongSupplier nanoTime, Sleeper sleeper) {
        this.minIntervalNanos = Math.max(0, minIntervalMillis) * 1_000_000L;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    /** @return true se pode chamar o provedor agora (esperou a sua vez); false se a fila está longa demais. */
    public boolean tryAcquire(long maxWaitMillis) {
        long waitNanos;
        synchronized (this) {
            long now = nanoTime.getAsLong();
            long slot = started ? Math.max(now, nextFreeNanos) : now;
            waitNanos = slot - now;
            if (waitNanos > Math.max(0, maxWaitMillis) * 1_000_000L) return false;
            started = true;
            nextFreeNanos = slot + minIntervalNanos;
        }
        if (waitNanos > 0) {
            try {
                sleeper.sleep((waitNanos + 999_999L) / 1_000_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
