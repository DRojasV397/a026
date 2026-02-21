package com.app.service.dashboard;

import com.app.config.ApiConfig;
import com.app.core.session.UserSession;
import com.app.model.dashboard.ExecutiveDashboardDTO;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para consumir el endpoint GET /dashboard/executive de la API FastAPI.
 */
public class DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public DashboardService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private String authHeader() {
        return "Bearer " + UserSession.getAccessToken();
    }

    /**
     * GET /dashboard/executive
     * Retorna el dashboard ejecutivo con KPIs, alertas, top productos y tendencias.
     */
    public CompletableFuture<ExecutiveDashboardDTO> getExecutiveDashboard() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getDashboardExecutiveUrl()))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("GET /dashboard/executive → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        ExecutiveDashboardDTO dto = gson.fromJson(response.body(), ExecutiveDashboardDTO.class);
                        if (dto != null && dto.isSuccess()) {
                            return dto;
                        }
                    }
                    logger.warn("getExecutiveDashboard falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en getExecutiveDashboard: {}", ex.getMessage());
                    return null;
                });
    }
}
