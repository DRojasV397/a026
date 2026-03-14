package com.app.service.profitability;

import com.app.config.ApiConfig;
import com.app.core.session.UserSession;
import com.app.model.profitability.*;
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
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
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
                        return gson.fromJson(response.body(), ProductsResponseDTO.class);
                    }
                    logger.warn("getProductProfitability falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en getProductProfitability: {}", ex.getMessage());
                    return null;
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
                        return gson.fromJson(response.body(), CategoriesResponseDTO.class);
                    }
                    logger.warn("getCategoryProfitability falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en getCategoryProfitability: {}", ex.getMessage());
                    return null;
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
                        return gson.fromJson(response.body(), IndicatorsResponseDTO.class);
                    }
                    logger.warn("calculateIndicators falló - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en calculateIndicators: {}", ex.getMessage());
                    return null;
                });
    }

    /** Versión sin parámetros — calcula el último mes por defecto */
    public CompletableFuture<IndicatorsResponseDTO> calculateIndicators() {
        return calculateIndicators(null, null, null, null);
    }
}
