package com.app.service.dashboard;

import com.app.config.ApiConfig;
import com.app.config.HttpClientProvider;
import com.app.core.session.UserSession;
import com.app.model.dashboard.ExecutiveDashboardDTO;
import com.app.model.dashboard.PreferencesResponseDTO;
import com.app.model.dashboard.UserPreferenceItemDTO;
import com.app.service.offline.CacheService;
import com.app.service.offline.CacheService.CacheEntry;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para consumir el endpoint GET /dashboard/executive de la API FastAPI.
 */
public class DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public DashboardService() {
        this.httpClient = HttpClientProvider.getClient();
    }

    private String authHeader() {
        return "Bearer " + UserSession.getAccessToken();
    }

    /**
     * GET /dashboard/executive (período por defecto = último mes)
     */
    public CompletableFuture<ExecutiveDashboardDTO> getExecutiveDashboard() {
        LocalDate fin = LocalDate.now();
        return getExecutiveDashboard(fin.minusDays(30), fin);
    }

    /**
     * GET /dashboard/executive?fecha_inicio=...&fecha_fin=...
     */
    public CompletableFuture<ExecutiveDashboardDTO> getExecutiveDashboard(LocalDate inicio, LocalDate fin) {
        String userId   = String.valueOf(UserSession.getUserId());
        long   days     = ChronoUnit.DAYS.between(inicio, fin);
        String cacheKey = "dashboard_" + days + "d";

        if (UserSession.isOfflineMode()) {
            return CompletableFuture.completedFuture(
                    loadFromCache(userId, cacheKey, ExecutiveDashboardDTO.class));
        }

        String url = ApiConfig.getDashboardExecutiveUrl(inicio.toString(), fin.toString());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("GET /dashboard/executive [{}→{}] → HTTP {}", inicio, fin, response.statusCode());
                    if (response.statusCode() == 200) {
                        ExecutiveDashboardDTO dto = gson.fromJson(response.body(), ExecutiveDashboardDTO.class);
                        if (dto != null && dto.isSuccess()) {
                            CacheService.put(userId, cacheKey, response.body());
                            return dto;
                        }
                    }
                    logger.warn("getExecutiveDashboard falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en getExecutiveDashboard: {}", ex.getMessage());
                    return loadFromCache(userId, cacheKey, ExecutiveDashboardDTO.class);
                });
    }

    /**
     * GET /dashboard/users/{userId}/preferences
     */
    public CompletableFuture<PreferencesResponseDTO> getPreferences() {
        String userId   = String.valueOf(UserSession.getUserId());
        String cacheKey = "dashboard_preferences";

        if (UserSession.isOfflineMode()) {
            return CompletableFuture.completedFuture(
                    loadFromCache(userId, cacheKey, PreferencesResponseDTO.class));
        }

        String url = ApiConfig.getDashboardPreferencesUrl(UserSession.getUserId());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("GET /dashboard/preferences → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        CacheService.put(userId, cacheKey, response.body());
                        return gson.fromJson(response.body(), PreferencesResponseDTO.class);
                    }
                    logger.warn("getPreferences falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en getPreferences: {}", ex.getMessage());
                    return loadFromCache(userId, cacheKey, PreferencesResponseDTO.class);
                });
    }

    /**
     * PUT /dashboard/users/{userId}/preferences
     * Body: {"preferencias": [{"kpi":"...","valor":"1"/"0"}, ...]}
     */
    public CompletableFuture<Boolean> savePreferences(List<UserPreferenceItemDTO> prefs) {
        String url = ApiConfig.getDashboardPreferencesUrl(UserSession.getUserId());

        StringBuilder sb = new StringBuilder("{\"preferencias\":[");
        for (int i = 0; i < prefs.size(); i++) {
            UserPreferenceItemDTO p = prefs.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"kpi\":\"").append(p.getKpi())
              .append("\",\"valor\":\"").append(p.isVisible() ? "1" : "0").append("\"}");
        }
        sb.append("]}");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .PUT(HttpRequest.BodyPublishers.ofString(sb.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("PUT /dashboard/preferences → HTTP {}", response.statusCode());
                    return response.statusCode() == 200;
                })
                .exceptionally(ex -> {
                    logger.error("Error en savePreferences: {}", ex.getMessage());
                    return false;
                });
    }

    // ── Cache helper ──────────────────────────────────────────────────────────

    private <T> T loadFromCache(String userId, String key, Class<T> type) {
        CacheEntry entry = CacheService.get(userId, key);
        if (entry == null) return null;
        try {
            return gson.fromJson(entry.payload, type);
        } catch (Exception e) {
            logger.warn("[CACHE] Error al parsear caché key={}: {}", key, e.getMessage());
            return null;
        }
    }
}
