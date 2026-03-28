package com.app.service.offline;

import com.app.config.ApiConfig;
import com.app.config.HttpClientProvider;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Verifica la conectividad con la API mediante HEAD /health (timeout 5s).
 * Expone una propiedad observable para binding reactivo en la UI.
 */
public final class ConnectivityService {

    private static final SimpleBooleanProperty onlineProp = new SimpleBooleanProperty(true);

    private ConnectivityService() {}

    /** Propiedad observable del estado online (para binding en la UI). */
    public static ReadOnlyBooleanProperty onlineProperty() {
        return onlineProp;
    }

    /** Retorna el estado actual de conectividad. */
    public static boolean isOnline() {
        return onlineProp.get();
    }

    /**
     * Verifica la conectividad de forma asíncrona (HEAD /health, timeout 5s).
     * Actualiza {@link #onlineProperty()} en el hilo de JavaFX.
     * Éxito = cualquier código HTTP < 500.
     */
    public static CompletableFuture<Boolean> checkAsync() {
        HttpClient client = HttpClientProvider.getClient();

        // El health endpoint está en la raíz (sin /api/v1)
        String baseUrl  = ApiConfig.getBaseUrl();
        String rootUrl  = baseUrl.contains("/api/") ? baseUrl.substring(0, baseUrl.indexOf("/api/")) : baseUrl;
        String healthUrl = rootUrl + "/health";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(5))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    boolean online = response.statusCode() < 500;
                    Platform.runLater(() -> onlineProp.set(online));
                    return online;
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> onlineProp.set(false));
                    return false;
                });
    }
}
