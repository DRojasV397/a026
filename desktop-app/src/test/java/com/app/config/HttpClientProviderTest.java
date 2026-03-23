package com.app.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para HttpClientProvider — singleton compartido de HttpClient.
 * Verifica que sea un verdadero singleton thread-safe con la configuración esperada.
 */
class HttpClientProviderTest {

    // ── Contrato del singleton ────────────────────────────────────────────────

    @Test
    @DisplayName("getClient() retorna instancia no nula")
    void getClientReturnsNonNull() {
        assertNotNull(HttpClientProvider.getClient());
    }

    @Test
    @DisplayName("getClient() retorna siempre la misma referencia (singleton)")
    void getClientIsSingleton() {
        HttpClient first  = HttpClientProvider.getClient();
        HttpClient second = HttpClientProvider.getClient();
        assertSame(first, second,
                "Debe retornar exactamente la misma instancia en llamadas sucesivas");
    }

    @Test
    @DisplayName("Llamadas concurrentes retornan la misma instancia")
    void getClientIsThreadSafe() throws InterruptedException {
        HttpClient[] results = new HttpClient[8];
        Thread[] threads = new Thread[results.length];

        for (int i = 0; i < threads.length; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> results[idx] = HttpClientProvider.getClient());
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        HttpClient expected = HttpClientProvider.getClient();
        for (HttpClient c : results) {
            assertSame(expected, c, "Todos los hilos deben obtener la misma instancia");
        }
    }

    // ── No instanciable ───────────────────────────────────────────────────────

    @Test
    @DisplayName("La clase no expone constructores públicos")
    void classHasNoPublicConstructor() {
        assertEquals(0, HttpClientProvider.class.getConstructors().length,
                "HttpClientProvider es una clase utilitaria: no debe tener constructores públicos");
    }

    @Test
    @DisplayName("La clase es final (no extensible)")
    void classIsFinal() {
        // java.lang.reflect.Modifier.isFinal(...)
        assertTrue(java.lang.reflect.Modifier.isFinal(HttpClientProvider.class.getModifiers()),
                "HttpClientProvider debe ser final");
    }

    // ── Configuración del cliente ─────────────────────────────────────────────

    @Test
    @DisplayName("La instancia usa HTTP/1.1 (version configurada)")
    void clientUsesHttp11() {
        assertEquals(HttpClient.Version.HTTP_1_1,
                HttpClientProvider.getClient().version(),
                "HttpClientProvider debe configurar HTTP/1.1");
    }

    @Test
    @DisplayName("La instancia define connectTimeout (no vacío)")
    void clientHasConnectTimeout() {
        assertTrue(HttpClientProvider.getClient().connectTimeout().isPresent(),
                "Se debe haber configurado un connectTimeout");
    }

    @Test
    @DisplayName("connectTimeout es exactamente 15 segundos")
    void connectTimeoutIs15Seconds() {
        var timeout = HttpClientProvider.getClient().connectTimeout().orElseThrow();
        assertEquals(15L, timeout.getSeconds(),
                "El connectTimeout debe ser 15 segundos");
    }
}
