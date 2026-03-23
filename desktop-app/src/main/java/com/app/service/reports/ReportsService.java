package com.app.service.reports;

import com.app.config.ApiConfig;
import com.app.config.HttpClientProvider;
import com.app.core.session.UserSession;
import com.app.model.reports.ReportTypeDTO;
import com.app.model.reports.ReportTypesResponseDTO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para consumir endpoints de /dashboard/reports de la API FastAPI.
 *
 * Endpoints cubiertos:
 * - GET  /dashboard/reports/types    → getReportTypes()
 * - POST /dashboard/reports/generate → generateReport()
 */
public class ReportsService {

    private static final Logger logger = LoggerFactory.getLogger(ReportsService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public ReportsService() {
        this.httpClient = HttpClientProvider.getClient();
    }

    private String authHeader() {
        return "Bearer " + UserSession.getAccessToken();
    }

    /**
     * GET /dashboard/reports/types
     * Retorna la lista de tipos de reportes disponibles, convertidos a ReportTypeDTO.
     */
    public CompletableFuture<List<ReportTypeDTO>> getReportTypes() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getReportTypesUrl()))
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("GET /dashboard/reports/types → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        ReportTypesResponseDTO dto = gson.fromJson(response.body(), ReportTypesResponseDTO.class);
                        if (dto != null && dto.isSuccess()) {
                            return dto.getTipos().stream()
                                    .map(ReportTypesResponseDTO.ApiReportType::toReportTypeDTO)
                                    .toList();
                        }
                    }
                    logger.warn("getReportTypes falló - HTTP {}: {}", response.statusCode(), response.body());
                    return List.<ReportTypeDTO>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error en getReportTypes: {}", ex.getMessage());
                    return List.of();
                });
    }

    /**
     * POST /dashboard/reports/generate con formato="json".
     * Retorna el body completo parseado como Map, incluyendo la clave "reporte"
     * con los datos tabulares. Retorna un Map vacío en caso de error.
     *
     * @param tipo       "ventas" | "compras" | "rentabilidad" | "productos"
     * @param fechaInicio fecha inicio del periodo
     * @param fechaFin    fecha fin del periodo
     * @param agruparPor "dia" | "semana" | "mes"
     * @param topN        número de productos (para tipo=productos, 1-100)
     */
    public CompletableFuture<Map<String, Object>> fetchReportData(
            String tipo,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String agruparPor,
            int topN
    ) {
        JsonObject body = new JsonObject();
        body.addProperty("tipo", tipo);
        body.addProperty("fecha_inicio", fechaInicio.toString());
        body.addProperty("fecha_fin", fechaFin.toString());
        body.addProperty("formato", "json");
        body.addProperty("agrupar_por", agruparPor);
        body.addProperty("top_n", topN);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getGenerateReportUrl()))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.debug("POST /dashboard/reports/generate → HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        Map<String, Object> result = gson.fromJson(response.body(), mapType);
                        return result != null ? result : Map.<String, Object>of();
                    }
                    logger.warn("fetchReportData falló - HTTP {}: {}", response.statusCode(), response.body());
                    return Map.<String, Object>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error en fetchReportData: {}", ex.getMessage());
                    return Map.of();
                });
    }
}
