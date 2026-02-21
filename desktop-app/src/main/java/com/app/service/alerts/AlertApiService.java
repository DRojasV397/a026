package com.app.service.alerts;

import com.app.config.ApiConfig;
import com.app.core.session.UserSession;
import com.app.model.alerts.AlertDTO;
import com.app.model.alerts.AlertsListResponseDTO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para consumir endpoints de /alerts de la API FastAPI.
 * Separa la responsabilidad de la API del AlertService existente (que usaba BD directa).
 *
 * Endpoints cubiertos:
 * - GET  /alerts           → getActiveAlerts()
 * - GET  /alerts/history   → getAlertHistory()
 * - PUT  /alerts/{id}/read → markAsRead()
 * - PUT  /alerts/{id}/status → changeStatus()
 * - GET  /alerts/config    → getConfig()
 * - POST /alerts/config    → saveConfig()
 * - POST /alerts/analyze   → analyzeAndGenerate()
 */
public class AlertApiService {

    private static final Logger logger = LoggerFactory.getLogger(AlertApiService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public AlertApiService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private String authHeader() {
        return "Bearer " + UserSession.getAccessToken();
    }

    // ── Alertas Activas ───────────────────────────────────────────────────────

    /**
     * GET /alerts
     * Retorna lista de AlertDTO ya mapeados (listos para la UI).
     */
    public CompletableFuture<List<AlertDTO>> getActiveAlerts() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAlertsUrl()))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("GET /alerts → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        AlertsListResponseDTO dto = gson.fromJson(response.body(), AlertsListResponseDTO.class);
                        if (dto != null && dto.isSuccess()) {
                            return dto.getAlertas().stream()
                                    .map(a -> a.toAlertDTO())
                                    .toList();
                        }
                    }
                    logger.warn("getActiveAlerts falló - HTTP {}: {}", response.statusCode(), response.body());
                    return List.<AlertDTO>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error en getActiveAlerts: {}", ex.getMessage());
                    return List.of();
                });
    }

    // ── Historial ─────────────────────────────────────────────────────────────

    /**
     * GET /alerts/history
     * Retorna historial de alertas (todas, no solo activas).
     */
    public CompletableFuture<List<AlertDTO>> getAlertHistory() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAlertHistoryUrl()))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("GET /alerts/history → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        AlertsListResponseDTO dto = gson.fromJson(response.body(), AlertsListResponseDTO.class);
                        if (dto != null && dto.isSuccess()) {
                            return dto.getAlertas().stream()
                                    .map(a -> a.toAlertDTO())
                                    .toList();
                        }
                    }
                    logger.warn("getAlertHistory falló - HTTP {}: {}", response.statusCode(), response.body());
                    return List.<AlertDTO>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error en getAlertHistory: {}", ex.getMessage());
                    return List.of();
                });
    }

    // ── Acciones ─────────────────────────────────────────────────────────────

    /**
     * PUT /alerts/{id}/read
     * Marca una alerta como leída.
     */
    public CompletableFuture<Boolean> markAsRead(long idAlerta) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAlertReadUrl((int) idAlerta)))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(ex -> {
                    logger.error("Error en markAsRead({}): {}", idAlerta, ex.getMessage());
                    return false;
                });
    }

    /**
     * PUT /alerts/{id}/status
     * Cambia el estado de una alerta. Estado válido: "Activa", "Leida", "Resuelta", "Ignorada"
     */
    public CompletableFuture<Boolean> changeStatus(long idAlerta, String nuevoEstado) {
        JsonObject body = new JsonObject();
        body.addProperty("estado", nuevoEstado);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAlertStatusUrl((int) idAlerta)))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(ex -> {
                    logger.error("Error en changeStatus({}): {}", idAlerta, ex.getMessage());
                    return false;
                });
    }

    /**
     * POST /alerts/analyze
     * Analiza datos de ventas y genera alertas automáticas.
     */
    public CompletableFuture<Boolean> analyzeAndGenerate() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAlertsAnalyzeUrl()))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(ex -> {
                    logger.error("Error en analyzeAndGenerate: {}", ex.getMessage());
                    return false;
                });
    }

    /**
     * POST /alerts/config
     * Configura umbrales de alertas.
     */
    public CompletableFuture<Boolean> saveConfig(Double riskThreshold,
                                                  Double opportunityThreshold,
                                                  Double anomalyThreshold) {
        JsonObject body = new JsonObject();
        if (riskThreshold != null)        body.addProperty("risk_threshold", riskThreshold);
        if (opportunityThreshold != null) body.addProperty("opportunity_threshold", opportunityThreshold);
        if (anomalyThreshold != null)     body.addProperty("anomaly_rate_threshold", anomalyThreshold);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getAlertsConfigUrl()))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(ex -> {
                    logger.error("Error en saveConfig: {}", ex.getMessage());
                    return false;
                });
    }
}
