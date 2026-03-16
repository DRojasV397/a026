package com.app.service.simulation;

import com.app.config.ApiConfig;
import com.app.core.session.UserSession;
import com.app.model.simulation.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio HTTP para consumir los endpoints de /simulation de la API FastAPI.
 * Todas las operaciones son asíncronas (CompletableFuture).
 *
 * Endpoints cubiertos:
 * - GET  /simulation/scenarios            -> listScenarios()
 * - POST /simulation/create              -> createScenario()
 * - PUT  /simulation/{id}/parameters     -> setParameters()
 * - POST /simulation/{id}/run            -> runSimulation()
 * - DELETE /simulation/{id}             -> deleteScenario()
 * - POST /simulation/compare             -> compareScenarios()
 */
public class SimulationApiService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationApiService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public SimulationApiService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    // ── List Scenarios ───────────────────────────────────────────────────────

    /**
     * Lista todos los escenarios.
     * GET /simulation/scenarios
     */
    public CompletableFuture<List<SimulationScenarioSummaryDTO>> listScenarios() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getSimulationScenariosUrl()))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        SimulationScenarioListDTO result = gson.fromJson(response.body(), SimulationScenarioListDTO.class);
                        return result.getEscenarios() != null
                                ? result.getEscenarios()
                                : List.<SimulationScenarioSummaryDTO>of();
                    }
                    logger.warn("ListScenarios failed - HTTP {}", response.statusCode());
                    return List.<SimulationScenarioSummaryDTO>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error al listar escenarios: {}", ex.getMessage());
                    return List.<SimulationScenarioSummaryDTO>of();
                });
    }

    // ── Create Scenario ──────────────────────────────────────────────────────

    /**
     * Crea un nuevo escenario.
     * POST /simulation/create
     */
    public CompletableFuture<CreateScenarioResponseDTO> createScenario(String nombre, String descripcion, int periodos) {
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", nombre);
        if (descripcion != null && !descripcion.isBlank()) body.put("descripcion", descripcion);
        body.put("periodos", periodos);
        body.put("basado_en_historico", true);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getSimulationCreateUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("CreateScenario HTTP {}: {}", response.statusCode(), response.body());
                    if (response.statusCode() == 200 || response.statusCode() == 201) {
                        return gson.fromJson(response.body(), CreateScenarioResponseDTO.class);
                    }
                    CreateScenarioResponseDTO err = new CreateScenarioResponseDTO();
                    err.setSuccess(false);
                    // Try to parse error detail from response
                    try {
                        Map<String, Object> errBody = gson.fromJson(response.body(),
                                new TypeToken<Map<String, Object>>(){}.getType());
                        Object detail = errBody.get("detail");
                        err.setError(detail != null ? detail.toString() : "HTTP " + response.statusCode());
                    } catch (Exception e) {
                        err.setError("HTTP " + response.statusCode());
                    }
                    return err;
                })
                .exceptionally(ex -> {
                    logger.error("Error al crear escenario: {}", ex.getMessage());
                    CreateScenarioResponseDTO err = new CreateScenarioResponseDTO();
                    err.setSuccess(false);
                    err.setError(ex.getMessage());
                    return err;
                });
    }

    // ── Set Parameters ───────────────────────────────────────────────────────

    /**
     * Modifica los parámetros de un escenario.
     * PUT /simulation/{id}/parameters
     *
     * @param params lista de {parametro, valorActual} — los nombres deben coincidir
     *               con los del backend: variacion_precio, variacion_costo, variacion_demanda, etc.
     */
    public CompletableFuture<Boolean> setParameters(int idEscenario, List<Map<String, Object>> params) {
        Map<String, Object> body = new HashMap<>();
        body.put("parametros", params);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getSimulationParametersUrl(idEscenario)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("SetParameters HTTP {}", response.statusCode());
                    return response.statusCode() == 200;
                })
                .exceptionally(ex -> {
                    logger.error("Error al configurar parámetros: {}", ex.getMessage());
                    return false;
                });
    }

    // ── Run Simulation ───────────────────────────────────────────────────────

    /**
     * Ejecuta la simulación de un escenario.
     * POST /simulation/{id}/run
     * Timeout largo (60s) porque puede tardar.
     */
    public CompletableFuture<SimulationRunResultDTO> runSimulation(int idEscenario, String granularidad) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guardar_resultados", true);
        body.put("granularidad", granularidad != null ? granularidad : "semanal");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getSimulationRunUrl(idEscenario)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("RunSimulation HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), SimulationRunResultDTO.class);
                    }
                    logger.warn("RunSimulation failed: {}", response.body());
                    SimulationRunResultDTO err = new SimulationRunResultDTO();
                    err.setSuccess(false);
                    err.setError("HTTP " + response.statusCode());
                    return err;
                })
                .exceptionally(ex -> {
                    logger.error("Error al ejecutar simulación: {}", ex.getMessage());
                    SimulationRunResultDTO err = new SimulationRunResultDTO();
                    err.setSuccess(false);
                    err.setError(ex.getMessage());
                    return err;
                });
    }

    // ── Delete Scenario ──────────────────────────────────────────────────────

    /**
     * Elimina un escenario.
     * DELETE /simulation/{id}
     */
    public CompletableFuture<Boolean> deleteScenario(int idEscenario) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getSimulationDeleteUrl(idEscenario)))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .DELETE()
                .build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200 || response.statusCode() == 204)
                .exceptionally(ex -> {
                    logger.error("Error al eliminar escenario: {}", ex.getMessage());
                    return false;
                });
    }

    // ── Compare Scenarios ────────────────────────────────────────────────────

    /**
     * Compara múltiples escenarios.
     * POST /simulation/compare
     */
    public CompletableFuture<Map<String, Object>> compareScenarios(List<Integer> ids) {
        Map<String, Object> body = Map.of("escenario_ids", ids);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getSimulationCompareUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(),
                                new TypeToken<Map<String, Object>>(){}.getType());
                    }
                    logger.warn("CompareScenarios failed - HTTP {}", response.statusCode());
                    return Map.<String, Object>of("success", false);
                })
                .exceptionally(ex -> {
                    logger.error("Error al comparar escenarios: {}", ex.getMessage());
                    return Map.of("success", false);
                });
    }
}
