package com.app.service.profile;

import com.app.config.ApiConfig;
import com.app.core.session.UserSession;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para consumir los endpoints de perfil de usuario de la API.
 * Maneja: obtener usuario, actualizar perfil, cambiar contraseña.
 */
public class ProfileApiService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileApiService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public ProfileApiService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Obtiene los datos de un usuario por ID.
     * GET /usuarios/{usuario_id}
     */
    public CompletableFuture<UserProfileResponse> getUserById(int userId) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getUserUrl(userId)))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("GetUser response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), UserProfileResponse.class);
                    }
                    logger.warn("GetUser failed - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error al obtener usuario: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Actualiza el perfil de un usuario.
     * PUT /usuarios/{usuario_id}
     * Solo envía los campos que el backend acepta: nombreCompleto, email.
     */
    public CompletableFuture<UserProfileResponse> updateProfile(int userId, String nombreCompleto, String email) {
        Map<String, String> body = new HashMap<>();
        if (nombreCompleto != null) body.put("nombreCompleto", nombreCompleto);
        if (email != null) body.put("email", email);
        String jsonBody = gson.toJson(body);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getUserUrl(userId)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("UpdateProfile response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), UserProfileResponse.class);
                    }
                    logger.warn("UpdateProfile failed - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error al actualizar perfil: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Cambia la contraseña del usuario autenticado.
     * PUT /auth/password
     */
    public CompletableFuture<ChangePasswordResponse> changePassword(
            String currentPassword, String newPassword, String confirmPassword) {
        Map<String, String> body = new HashMap<>();
        body.put("current_password", currentPassword);
        body.put("new_password", newPassword);
        body.put("confirm_password", confirmPassword);
        String jsonBody = gson.toJson(body);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getChangePasswordUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("ChangePassword response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), ChangePasswordResponse.class);
                    }
                    logger.warn("ChangePassword failed - HTTP {}: {}", response.statusCode(), response.body());
                    // Parsear error del backend
                    ChangePasswordResponse errorResp = new ChangePasswordResponse();
                    errorResp.success = false;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> errorBody = gson.fromJson(response.body(), Map.class);
                        errorResp.message = errorBody.getOrDefault("detail",
                                "Error HTTP " + response.statusCode());
                    } catch (Exception e) {
                        errorResp.message = "Error HTTP " + response.statusCode();
                    }
                    return errorResp;
                })
                .exceptionally(ex -> {
                    logger.error("Error al cambiar contraseña: {}", ex.getMessage());
                    ChangePasswordResponse errorResp = new ChangePasswordResponse();
                    errorResp.success = false;
                    errorResp.message = "Error de conexión: " + ex.getMessage();
                    return errorResp;
                });
    }

    /**
     * Respuesta de GET/PUT /usuarios/{id}
     */
    public static class UserProfileResponse {
        @SerializedName("idUsuario")
        public int idUsuario;

        @SerializedName("nombreCompleto")
        public String nombreCompleto;

        @SerializedName("nombreUsuario")
        public String nombreUsuario;

        @SerializedName("email")
        public String email;

        @SerializedName("estado")
        public String estado;

        @SerializedName("creadoEn")
        public String creadoEn;
    }

    /**
     * Respuesta de PUT /auth/password
     */
    public static class ChangePasswordResponse {
        public String message;
        public boolean success;
    }
}
