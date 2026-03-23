package com.app.service.admin;

import com.app.config.ApiConfig;
import com.app.config.HttpClientProvider;
import com.app.core.session.UserSession;
import com.app.model.admin.AdminUserDTO;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para consumir los endpoints de /admin de la API FastAPI.
 * Gestiona operaciones CRUD de usuarios desde el panel de administracion.
 */
public class AdminApiService {

    private static final Logger logger = LoggerFactory.getLogger(AdminApiService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public AdminApiService() {
        this.httpClient = HttpClientProvider.getClient();
    }

    private String authHeader() {
        return "Bearer " + UserSession.getAccessToken();
    }

    /**
     * GET /admin/usuarios - Lista todos los usuarios.
     */
    public CompletableFuture<List<AdminUserDTO>> getUsuarios() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAdminUsuariosUrl()))
                .header("Authorization", authHeader())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        Type listType = new TypeToken<List<AdminUserDTO>>(){}.getType();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("GET /admin/usuarios - HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return (List<AdminUserDTO>) gson.fromJson(response.body(), listType);
                    }
                    logger.warn("Error al listar usuarios: HTTP {} - {}",
                            response.statusCode(), response.body());
                    return List.<AdminUserDTO>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error de conexion al listar usuarios: {}", ex.getMessage());
                    return List.of();
                });
    }

    /**
     * POST /admin/usuarios - Crea un nuevo usuario.
     */
    public CompletableFuture<AdminUserDTO> createUsuario(Map<String, Object> payload) {
        String json = gson.toJson(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAdminUsuariosUrl()))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("POST /admin/usuarios - HTTP {}", response.statusCode());
                    if (response.statusCode() == 201 || response.statusCode() == 200) {
                        return gson.fromJson(response.body(), AdminUserDTO.class);
                    }
                    logger.warn("Error al crear usuario: HTTP {} - {}",
                            response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error de conexion al crear usuario: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * PUT /admin/usuarios/{id} - Actualiza un usuario existente.
     */
    public CompletableFuture<AdminUserDTO> updateUsuario(int id, Map<String, Object> payload) {
        String json = gson.toJson(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAdminUsuarioUrl(id)))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("PUT /admin/usuarios/{} - HTTP {}", id, response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), AdminUserDTO.class);
                    }
                    logger.warn("Error al actualizar usuario {}: HTTP {} - {}",
                            id, response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error de conexion al actualizar usuario: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * PUT /admin/usuarios/{id}/modulos - Reemplaza la lista de modulos.
     */
    public CompletableFuture<Boolean> updateModulos(int id, List<String> modulos) {
        String json = gson.toJson(Map.of("modulos", modulos));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAdminUsuarioModulosUrl(id)))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("PUT /admin/usuarios/{}/modulos - HTTP {}", id, response.statusCode());
                    return response.statusCode() == 200;
                })
                .exceptionally(ex -> {
                    logger.error("Error de conexion al actualizar modulos: {}", ex.getMessage());
                    return false;
                });
    }

    /**
     * PUT /admin/usuarios/{id}/estado - Cambia estado Activo/Inactivo.
     */
    public CompletableFuture<Boolean> updateEstado(int id, String estado) {
        String json = gson.toJson(Map.of("estado", estado));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAdminUsuarioEstadoUrl(id)))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("PUT /admin/usuarios/{}/estado - HTTP {}", id, response.statusCode());
                    return response.statusCode() == 200;
                })
                .exceptionally(ex -> {
                    logger.error("Error de conexion al cambiar estado: {}", ex.getMessage());
                    return false;
                });
    }

    /**
     * DELETE /admin/usuarios/{id} - Elimina un usuario.
     */
    public CompletableFuture<Boolean> deleteUsuario(int id) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAdminUsuarioUrl(id)))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(30))
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("DELETE /admin/usuarios/{} - HTTP {}", id, response.statusCode());
                    return response.statusCode() == 204 || response.statusCode() == 200;
                })
                .exceptionally(ex -> {
                    logger.error("Error de conexion al eliminar usuario: {}", ex.getMessage());
                    return false;
                });
    }
}
