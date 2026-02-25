package com.app.service.predictions;

import com.app.config.ApiConfig;
import com.app.core.session.UserSession;
import com.app.model.predictions.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para consumir los endpoints de /predictions de la API FastAPI.
 * Todas las operaciones son asincronas (CompletableFuture).
 *
 * Endpoints cubiertos:
 * - POST /predictions/train          -> trainModel()
 * - POST /predictions/forecast       -> forecast()
 * - POST /predictions/auto-select    -> autoSelectModel()
 * - GET  /predictions/models         -> listModels()
 * - DELETE /predictions/models/{key} -> deleteModel()
 * - POST /predictions/models/load    -> loadModel()
 * - POST /predictions/models/load-all -> loadAllModels()
 * - GET  /predictions/models/saved   -> listSavedModels()
 * - POST /predictions/validate-data  -> validateData()
 * - POST /predictions/sales-data     -> getSalesData()
 * - GET  /predictions/history        -> getHistory(int limit)
 * - GET  /predictions/model-types    -> getModelTypes()
 */

/**
 * Servicio para consumir los endpoints de /predictions de la API FastAPI.
 * Todas las operaciones son asíncronas (CompletableFuture).
 */
public class PredictionService {

    private static final Logger logger = LoggerFactory.getLogger(PredictionService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public PredictionService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Entrena un modelo predictivo.
     * Timeout largo (120s) porque el training puede tardar.
     */
    public CompletableFuture<TrainModelResponseDTO> trainModel(TrainModelRequestDTO request) {
        String jsonBody = gson.toJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsTrainUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("Train response HTTP {}: {}", response.statusCode(), response.body());
                    if (response.statusCode() == 200 || response.statusCode() == 201) {
                        return gson.fromJson(response.body(), TrainModelResponseDTO.class);
                    }
                    TrainModelResponseDTO errorResponse = new TrainModelResponseDTO();
                    logger.warn("Train failed - HTTP {}: {}", response.statusCode(), response.body());
                    return errorResponse;
                })
                .exceptionally(ex -> {
                    logger.error("Error al entrenar modelo: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Genera predicciones usando un modelo entrenado.
     */
    public CompletableFuture<ForecastResponseDTO> forecast(ForecastRequestDTO request) {
        String jsonBody = gson.toJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsForecastUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("Forecast response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), ForecastResponseDTO.class);
                    }
                    logger.warn("Forecast failed - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error al generar forecast: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Auto-selección del mejor modelo. Timeout largo (180s).
     */
    public CompletableFuture<AutoSelectResponseDTO> autoSelectModel(String fechaInicio, String fechaFin) {
        Map<String, String> body = new HashMap<>();
        if (fechaInicio != null) body.put("fecha_inicio", fechaInicio);
        if (fechaFin != null) body.put("fecha_fin", fechaFin);
        String jsonBody = gson.toJson(body);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsAutoSelectUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("AutoSelect response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200 || response.statusCode() == 201) {
                        return gson.fromJson(response.body(), AutoSelectResponseDTO.class);
                    }
                    logger.warn("AutoSelect failed - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error en auto-select: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Lista todos los modelos entrenados.
     */
    public CompletableFuture<List<ModelInfoDTO>> listModels() {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsModelsUrl()))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(),
                                new TypeToken<List<ModelInfoDTO>>(){}.getType());
                    }
                    logger.warn("ListModels failed - HTTP {}", response.statusCode());
                    return List.<ModelInfoDTO>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error al listar modelos: {}", ex.getMessage());
                    return List.of();
                });
    }

    /**
     * Elimina un modelo por su clave.
     */
    public CompletableFuture<Boolean> deleteModel(String modelKey) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsDeleteModelUrl(modelKey)))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .DELETE()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200 || response.statusCode() == 204)
                .exceptionally(ex -> {
                    logger.error("Error al eliminar modelo: {}", ex.getMessage());
                    return false;
                });
    }

    /**
     * Carga todos los modelos guardados en disco.
     */
    public CompletableFuture<Boolean> loadAllModels() {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsLoadAllModelsUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(ex -> {
                    logger.error("Error al cargar modelos: {}", ex.getMessage());
                    return false;
                });
    }

    /**
     * Carga un modelo previamente entrenado desde disco.
     * POST /predictions/models/load
     */
    public CompletableFuture<LoadModelResponseDTO> loadModel(String modelKey) {
        Map<String, String> body = Map.of("model_key", modelKey);
        String jsonBody = gson.toJson(body);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsLoadModelUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("LoadModel response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), LoadModelResponseDTO.class);
                    }
                    logger.warn("LoadModel failed - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error al cargar modelo: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Lista modelos guardados en disco.
     * GET /predictions/models/saved
     */
    public CompletableFuture<List<SavedModelInfoDTO>> listSavedModels() {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsSavedModelsUrl()))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(),
                                new TypeToken<List<SavedModelInfoDTO>>(){}.getType());
                    }
                    logger.warn("ListSavedModels failed - HTTP {}", response.statusCode());
                    return List.<SavedModelInfoDTO>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error al listar modelos guardados: {}", ex.getMessage());
                    return List.of();
                });
    }

    /**
     * Obtiene el historial de predicciones.
     * GET /predictions/history
     */
    public CompletableFuture<List<PredictionHistoryItemDTO>> getHistory(int limit) {
        String url = ApiConfig.getPredictionsHistoryUrl() + "?limit=" + limit;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(),
                                new TypeToken<List<PredictionHistoryItemDTO>>(){}.getType());
                    }
                    return List.<PredictionHistoryItemDTO>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error al obtener historial tipado: {}", ex.getMessage());
                    return List.of();
                });
    }

    /**
     * Obtiene los tipos de modelo disponibles.
     * GET /predictions/model-types
     */
    public CompletableFuture<ModelTypesResponseDTO> getModelTypes() {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsModelTypesUrl()))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), ModelTypesResponseDTO.class);
                    }
                    logger.warn("GetModelTypes failed - HTTP {}", response.statusCode());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error al obtener tipos de modelo: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Valida que los datos cumplan los requisitos mínimos para predicción.
     * POST /predictions/validate-data
     * RN-01.01: Mínimo 6 meses de datos históricos.
     */
    public CompletableFuture<Map<String, Object>> validateData(String fechaInicio, String fechaFin, String aggregation) {
        Map<String, Object> body = new HashMap<>();
        if (fechaInicio != null) body.put("fecha_inicio", fechaInicio);
        if (fechaFin != null) body.put("fecha_fin", fechaFin);
        body.put("aggregation", aggregation != null ? aggregation : "D");
        String jsonBody = gson.toJson(body);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsValidateDataUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("ValidateData response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        Map<String, Object> result = gson.fromJson(response.body(),
                                new TypeToken<Map<String, Object>>(){}.getType());
                        return result;
                    }
                    logger.warn("ValidateData failed - HTTP {}: {}", response.statusCode(), response.body());
                    return Map.<String, Object>of("valid", false, "issues", List.of("Error HTTP " + response.statusCode()));
                })
                .exceptionally(ex -> {
                    logger.error("Error al validar datos: {}", ex.getMessage());
                    return Map.of("valid", false, "issues", List.of(ex.getMessage()));
                });
    }

    /**
     * Obtiene datos de ventas agregados para análisis.
     * POST /predictions/sales-data
     */
    public CompletableFuture<Map<String, Object>> getSalesData(String fechaInicio, String fechaFin, String aggregation) {
        Map<String, Object> body = new HashMap<>();
        if (fechaInicio != null) body.put("fecha_inicio", fechaInicio);
        if (fechaFin != null) body.put("fecha_fin", fechaFin);
        body.put("aggregation", aggregation != null ? aggregation : "D");
        String jsonBody = gson.toJson(body);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getPredictionsSalesDataUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        Map<String, Object> result = gson.fromJson(response.body(),
                                new TypeToken<Map<String, Object>>(){}.getType());
                        return result;
                    }
                    logger.warn("SalesData failed - HTTP {}", response.statusCode());
                    return Map.<String, Object>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error al obtener datos de ventas: {}", ex.getMessage());
                    return Map.of();
                });
    }

}
