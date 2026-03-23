package com.app.config;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Proveedor de instancia compartida de HttpClient.
 *
 * <p>Todos los servicios de la aplicación comparten un único HttpClient, evitando
 * la creación de 11 instancias separadas (una por servicio). HttpClient es
 * thread-safe y está diseñado para ser reutilizado.</p>
 *
 * <p>Uso en cualquier servicio:</p>
 * <pre>
 *     private final HttpClient httpClient = HttpClientProvider.getClient();
 * </pre>
 */
public final class HttpClientProvider {

    private static final HttpClient INSTANCE = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private HttpClientProvider() {
        // Clase utilitaria — no instanciar
    }

    /**
     * Retorna la instancia compartida de HttpClient.
     *
     * @return HttpClient singleton thread-safe
     */
    public static HttpClient getClient() {
        return INSTANCE;
    }
}
