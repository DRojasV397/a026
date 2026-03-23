package com.app.service.auth;

import com.app.config.ApiConfig;
import com.app.config.HttpClientProvider;
import com.app.core.session.UserSession;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para renovar el access token usando el refresh token almacenado en sesión.
 * Llama a POST /api/v1/auth/refresh y actualiza UserSession con el nuevo token.
 */
public class TokenRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(TokenRefreshService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public TokenRefreshService() {
        this.httpClient = HttpClientProvider.getClient();
    }

    /**
     * Renueva el access token de forma asíncrona.
     * Si tiene éxito, actualiza UserSession con el nuevo token y su expiración.
     *
     * @return CompletableFuture&lt;Boolean&gt; true si el refresh fue exitoso
     */
    public CompletableFuture<Boolean> refreshAsync() {
        String refreshToken = UserSession.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            logger.warn("No hay refresh token disponible para renovar la sesión");
            return CompletableFuture.completedFuture(false);
        }

        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refreshToken);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getRefreshUrl()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                            String newToken = json.has("access_token")
                                    ? json.get("access_token").getAsString() : null;
                            int expiresIn = json.has("expires_in")
                                    ? json.get("expires_in").getAsInt() : 1800;
                            if (newToken != null && !newToken.isBlank()) {
                                UserSession.updateAccessToken(newToken, expiresIn);
                                logger.info("Access token renovado exitosamente (expira en {}s)", expiresIn);
                                return true;
                            }
                        } catch (Exception e) {
                            logger.error("Error al parsear respuesta del refresh: {}", e.getMessage());
                        }
                    }
                    logger.warn("Refresh fallido - HTTP {}: {}", response.statusCode(), response.body());
                    return false;
                })
                .exceptionally(ex -> {
                    logger.error("Error de red al renovar token: {}", ex.getMessage());
                    return false;
                });
    }
}
