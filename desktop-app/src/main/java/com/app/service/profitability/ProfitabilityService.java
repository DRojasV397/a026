package com.app.service.profitability;

import com.app.config.ApiConfig;
import com.app.config.HttpClientProvider;
import com.app.core.session.UserSession;
import com.app.model.profitability.*;
import com.app.service.offline.CacheService;
import com.app.service.offline.CacheService.CacheEntry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para consumir endpoints de /profitability de la API FastAPI.
 * Todas las operaciones son asíncronas (CompletableFuture).
 *
 * Endpoints cubiertos:
 * - POST /profitability/indicators  → calculateIndicators()
 * - GET  /profitability/products    → getProductProfitability()
 * - GET  /profitability/categories  → getCategoryProfitability()
 * - GET  /profitability/ranking     → getRanking()
 * - GET  /profitability/products/non-profitable → getNonProfitable()
 */
public class ProfitabilityService {

    private static final Logger logger = LoggerFactory.getLogger(ProfitabilityService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public ProfitabilityService() {
        this.httpClient = HttpClientProvider.getClient();
    }

    private String authHeader() {
        return "Bearer " + UserSession.getAccessToken();
    }

    // ── Productos ─────────────────────────────────────────────────────────────

    /**
     * GET /profitability/products[?fecha_inicio=&fecha_fin=]
     * Obtiene rentabilidad de todos los productos para el rango dado.
     */
    public CompletableFuture<ProductsResponseDTO> getProductProfitability(LocalDate from, LocalDate to) {
        String userId   = String.valueOf(UserSession.getUserId());
        String cacheKey = (from != null && to != null)
                ? "profit_products_" + ChronoUnit.DAYS.between(from, to) + "d"
                : "profit_products_default";

        if (UserSession.isOfflineMode()) {
            return CompletableFuture.completedFuture(
                    loadFromCache(userId, cacheKey, ProductsResponseDTO.class));
        }

        StringBuilder url = new StringBuilder(ApiConfig.getProfitabilityProductsUrl());
        boolean first = true;
        if (from != null) { url.append("?fecha_inicio=").append(from); first = false; }
        if (to   != null) { url.append(first ? "?" : "&").append("fecha_fin=").append(to); }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("GET /profitability/products → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        CacheService.put(userId, cacheKey, response.body());
                        return gson.fromJson(response.body(), ProductsResponseDTO.class);
                    }
                    logger.warn("getProductProfitability falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en getProductProfitability: {}", ex.getMessage());
                    return loadFromCache(userId, cacheKey, ProductsResponseDTO.class);
                });
    }

    /** Versión sin fechas — usa el default del backend (último mes). */
    public CompletableFuture<ProductsResponseDTO> getProductProfitability() {
        return getProductProfitability(null, null);
    }

    /**
     * GET /profitability/products?solo_no_rentables=true
     * Obtiene solo productos con margen < 10%.
     */
    public CompletableFuture<ProductsResponseDTO> getNonProfitable() {
        String url = ApiConfig.getProfitabilityProductsUrl() + "?solo_no_rentables=true";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), ProductsResponseDTO.class);
                    }
                    logger.warn("getNonProfitable falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en getNonProfitable: {}", ex.getMessage());
                    return null;
                });
    }

    // ── Categorías ────────────────────────────────────────────────────────────

    /**
     * GET /profitability/categories[?fecha_inicio=&fecha_fin=]
     * Obtiene rentabilidad por categoría para el rango dado.
     */
    public CompletableFuture<CategoriesResponseDTO> getCategoryProfitability(LocalDate from, LocalDate to) {
        String userId   = String.valueOf(UserSession.getUserId());
        String cacheKey = (from != null && to != null)
                ? "profit_categories_" + ChronoUnit.DAYS.between(from, to) + "d"
                : "profit_categories_default";

        if (UserSession.isOfflineMode()) {
            return CompletableFuture.completedFuture(
                    loadFromCache(userId, cacheKey, CategoriesResponseDTO.class));
        }

        StringBuilder url = new StringBuilder(ApiConfig.getProfitabilityCategoriesUrl());
        boolean first = true;
        if (from != null) { url.append("?fecha_inicio=").append(from); first = false; }
        if (to   != null) { url.append(first ? "?" : "&").append("fecha_fin=").append(to); }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("GET /profitability/categories → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        CacheService.put(userId, cacheKey, response.body());
                        return gson.fromJson(response.body(), CategoriesResponseDTO.class);
                    }
                    logger.warn("getCategoryProfitability falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en getCategoryProfitability: {}", ex.getMessage());
                    return loadFromCache(userId, cacheKey, CategoriesResponseDTO.class);
                });
    }

    /** Versión sin fechas — usa el default del backend (último mes). */
    public CompletableFuture<CategoriesResponseDTO> getCategoryProfitability() {
        return getCategoryProfitability(null, null);
    }

    // ── Ranking ───────────────────────────────────────────────────────────────

    /**
     * GET /profitability/ranking?metric=margen&limit=4&ascending=false
     * Obtiene ranking de productos por métrica. Por defecto: top 4 por margen.
     */
    public CompletableFuture<RankingResponseDTO> getRanking(String metric, int limit, boolean ascending) {
        String url = ApiConfig.getProfitabilityRankingUrl()
                + "?metric=" + metric
                + "&limit=" + limit
                + "&ascending=" + ascending;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("GET /profitability/ranking → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), RankingResponseDTO.class);
                    }
                    logger.warn("getRanking falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en getRanking: {}", ex.getMessage());
                    return null;
                });
    }

    // ── Indicadores Financieros ───────────────────────────────────────────────

    /**
     * POST /profitability/indicators
     * Calcula indicadores financieros para el rango de fechas dado.
     * Opcionalmente acepta activos y patrimonio para ROA/ROE.
     */
    public CompletableFuture<IndicatorsResponseDTO> calculateIndicators(
            LocalDate fechaInicio, LocalDate fechaFin,
            Double activosTotales, Double patrimonio) {

        String userId   = String.valueOf(UserSession.getUserId());
        String cacheKey = (fechaInicio != null && fechaFin != null)
                ? "profit_indicators_" + ChronoUnit.DAYS.between(fechaInicio, fechaFin) + "d"
                : "profit_indicators_default";

        if (UserSession.isOfflineMode()) {
            return CompletableFuture.completedFuture(
                    loadFromCache(userId, cacheKey, IndicatorsResponseDTO.class));
        }

        JsonObject body = new JsonObject();
        if (fechaInicio != null)    body.addProperty("fecha_inicio", fechaInicio.toString());
        if (fechaFin != null)       body.addProperty("fecha_fin", fechaFin.toString());
        if (activosTotales != null) body.addProperty("activos_totales", activosTotales);
        if (patrimonio != null)     body.addProperty("patrimonio", patrimonio);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getProfitabilityIndicatorsUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("POST /profitability/indicators → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        CacheService.put(userId, cacheKey, response.body());
                        return gson.fromJson(response.body(), IndicatorsResponseDTO.class);
                    }
                    logger.warn("calculateIndicators falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en calculateIndicators: {}", ex.getMessage());
                    return loadFromCache(userId, cacheKey, IndicatorsResponseDTO.class);
                });
    }

    /** Versión sin parámetros — calcula el último mes por defecto */
    public CompletableFuture<IndicatorsResponseDTO> calculateIndicators() {
        return calculateIndicators(null, null, null, null);
    }

    // ── Proyección Futura ─────────────────────────────────────────────────────

    /**
     * GET /profitability/projection?periods=N
     * Genera proyección de rentabilidad usando el mejor pack activo.
     */
    public CompletableFuture<ProjectionResponseDTO> getProjection(int periods) {
        String userId   = String.valueOf(UserSession.getUserId());
        String cacheKey = "profit_projection_" + periods + "p";

        if (UserSession.isOfflineMode()) {
            return CompletableFuture.completedFuture(
                    loadFromCache(userId, cacheKey, ProjectionResponseDTO.class));
        }

        String url = ApiConfig.getProfitabilityProjectionUrl() + "?periods=" + periods;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(90))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("GET /profitability/projection → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        CacheService.put(userId, cacheKey, response.body());
                        return gson.fromJson(response.body(), ProjectionResponseDTO.class);
                    }
                    logger.warn("getProjection falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en getProjection: {}", ex.getMessage());
                    return loadFromCache(userId, cacheKey, ProjectionResponseDTO.class);
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
