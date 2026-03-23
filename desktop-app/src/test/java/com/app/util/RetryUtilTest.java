package com.app.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para RetryUtil — reintentos asíncronos ante errores transitorios.
 *
 * <p>Se usa delayMs=0 para evitar esperas reales en CI.
 * Los tests de agotamiento de reintentos usan un timeout breve (5 s).</p>
 */
class RetryUtilTest {

    // ─── helper ──────────────────────────────────────────────────────────────

    /** Espera el resultado con timeout de 5 segundos. */
    private static <T> T await(CompletableFuture<T> cf) throws Exception {
        return cf.get(5, TimeUnit.SECONDS);
    }

    /** Desenvuelve la causa raíz de una ExecutionException. */
    private static Throwable cause(ExecutionException ex) {
        return ex.getCause() != null ? ex.getCause() : ex;
    }

    // ─── éxito sin retries ────────────────────────────────────────────────────

    @Test
    @DisplayName("Éxito en el 1er intento: no se ejecutan retries")
    void successOnFirstAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);

        String result = await(RetryUtil.withRetry(
                () -> CompletableFuture.supplyAsync(() -> {
                    calls.incrementAndGet();
                    return "ok";
                }),
                3, 0
        ));

        assertEquals("ok", result);
        assertEquals(1, calls.get(), "Solo debe haberse llamado una vez");
    }

    // ─── errores no transitorios ──────────────────────────────────────────────

    @Test
    @DisplayName("RuntimeException genérica NO es transitoria: no se reintenta")
    void nonTransientRuntimeNotRetried() {
        AtomicInteger calls = new AtomicInteger(0);
        RuntimeException original = new RuntimeException("bad input");

        CompletableFuture<String> cf = RetryUtil.withRetry(
                () -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(original);
                },
                3, 0
        );

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> cf.get(5, TimeUnit.SECONDS));
        assertSame(original, cause(ex), "Debe propagarse la excepción original");
        assertEquals(1, calls.get(), "No debe haber reintentos para errores no transitorios");
    }

    @Test
    @DisplayName("maxAttempts=1: sin reintentos aunque la excepción sea transitoria")
    void maxAttemptsOneNoRetry() {
        AtomicInteger calls = new AtomicInteger(0);

        CompletableFuture<String> cf = RetryUtil.withRetry(
                () -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(new IOException("refused"));
                },
                1, 0
        );

        assertThrows(ExecutionException.class, () -> cf.get(5, TimeUnit.SECONDS));
        assertEquals(1, calls.get(), "maxAttempts=1 no debe reintentar");
    }

    // ─── errores transitorios ─────────────────────────────────────────────────

    @Test
    @DisplayName("IOException es transitoria: se reintenta y recupera en 3er intento")
    void ioExceptionRetriedAndRecovers() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);

        String result = await(RetryUtil.withRetry(
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 3) {
                        return CompletableFuture.failedFuture(new IOException("connection reset"));
                    }
                    return CompletableFuture.completedFuture("recovered");
                },
                3, 0
        ));

        assertEquals("recovered", result);
        assertEquals(3, calls.get(), "Debe haber 2 reintentos (3 llamadas totales)");
    }

    @Test
    @DisplayName("HttpTimeoutException es transitoria: se reintenta")
    void httpTimeoutIsRetried() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);

        String result = await(RetryUtil.withRetry(
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 2) {
                        return CompletableFuture.failedFuture(new HttpTimeoutException("timeout"));
                    }
                    return CompletableFuture.completedFuture("ok");
                },
                3, 0
        ));

        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("RuntimeException con '503' en mensaje es transitoria")
    void message503IsTransient() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);

        String result = await(RetryUtil.withRetry(
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 2) {
                        return CompletableFuture.failedFuture(
                                new RuntimeException("HTTP 503 Service Unavailable"));
                    }
                    return CompletableFuture.completedFuture("done");
                },
                3, 0
        ));

        assertEquals("done", result);
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("RuntimeException con 'Connection refused' es transitoria")
    void connectionRefusedIsTransient() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);

        String result = await(RetryUtil.withRetry(
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 2) {
                        return CompletableFuture.failedFuture(
                                new RuntimeException("Connection refused"));
                    }
                    return CompletableFuture.completedFuture("alive");
                },
                3, 0
        ));

        assertEquals("alive", result);
        assertEquals(2, calls.get());
    }

    // ─── agotamiento de reintentos ────────────────────────────────────────────

    @Test
    @DisplayName("Se agotan maxAttempts: se propaga la última excepción")
    void exhaustedAttemptsPropagate() {
        AtomicInteger calls = new AtomicInteger(0);

        CompletableFuture<String> cf = RetryUtil.withRetry(
                () -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(new IOException("always fails"));
                },
                3, 0
        );

        assertThrows(ExecutionException.class, () -> cf.get(5, TimeUnit.SECONDS));
        assertEquals(3, calls.get(), "Debe haberse llamado exactamente maxAttempts veces");
    }

    // ─── overload por defecto ─────────────────────────────────────────────────

    @Test
    @DisplayName("withRetry(action) — éxito inmediato funciona con overload por defecto")
    void defaultOverloadSuccess() throws Exception {
        String result = await(RetryUtil.withRetry(
                () -> CompletableFuture.completedFuture("default-ok")
        ));
        assertEquals("default-ok", result);
    }

    @Test
    @DisplayName("withRetry(action) — error no transitorio se propaga sin retries")
    void defaultOverloadNonTransientPropagate() {
        RuntimeException error = new RuntimeException("logic error");
        CompletableFuture<String> cf = RetryUtil.withRetry(
                () -> CompletableFuture.failedFuture(error)
        );
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> cf.get(5, TimeUnit.SECONDS));
        assertSame(error, cause(ex));
    }
}
