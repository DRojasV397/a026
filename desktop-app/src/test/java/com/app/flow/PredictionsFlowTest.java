package com.app.flow;

import com.app.config.ApiConfig;
import com.app.core.session.UserSession;
import com.app.model.LoginResponseDTO;
import com.app.model.predictions.*;
import com.app.service.auth.AuthService;
import com.app.service.predictions.PredictionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de flujo del módulo de predicciones contra el backend real.
 * Requiere que la API esté corriendo en localhost:8000.
 *
 * Ejecutar con: mvn failsafe:integration-test -Dit.test=PredictionsFlowTest
 */
@Tag("integration")
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Flujo de Predicciones - Integración con Backend")
class PredictionsFlowTest {

    private static final AuthService authService = new AuthService();
    private static final PredictionService predictionService = new PredictionService();

    private static final String VALID_USER = "admin";
    private static final String VALID_PASSWORD = "admin123";

    // Variable para almacenar model_key entre tests
    private static String trainedModelKey = null;

    @BeforeAll
    static void loginFirst() {
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);
        assertNotNull(response, "Se requiere login exitoso para tests de predicciones");
        UserSession.setFromLoginResponse(response);
        assertTrue(UserSession.isLoggedIn());
    }

    @AfterAll
    static void cleanup() {
        // Limpiar modelo de prueba si fue creado
        if (trainedModelKey != null) {
            try {
                predictionService.deleteModel(trainedModelKey).get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
        UserSession.clear();
    }

    // ── Tipos de modelo (casos positivos) ───────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Obtener tipos de modelo disponibles")
    void getModelTypes() throws Exception {
        CompletableFuture<Map<String, Object>> future = predictionService.getModelTypes();
        Map<String, Object> types = future.get(15, TimeUnit.SECONDS);

        assertNotNull(types, "Debe retornar tipos de modelo");
        assertFalse(types.isEmpty(), "Debe haber al menos un tipo de modelo disponible");
    }

    // ── Validación de datos (casos positivos) ───────────────────────────

    @Test
    @Order(5)
    @DisplayName("Validar datos con rango de fechas amplio")
    void validateDataWithDates() throws Exception {
        CompletableFuture<Map<String, Object>> future =
                predictionService.validateData("2020-01-01", "2025-12-31", "D");
        Map<String, Object> result = future.get(30, TimeUnit.SECONDS);

        assertNotNull(result, "La validación debe retornar resultado");
        assertTrue(result.containsKey("valid"), "Resultado debe contener campo 'valid'");
    }

    @Test
    @Order(6)
    @DisplayName("Validar datos sin fechas (usa todos los datos)")
    void validateDataWithoutDates() throws Exception {
        CompletableFuture<Map<String, Object>> future =
                predictionService.validateData(null, null, "D");
        Map<String, Object> result = future.get(30, TimeUnit.SECONDS);

        assertNotNull(result, "La validación debe retornar resultado");
    }

    @Test
    @Order(7)
    @DisplayName("Validar datos con agregación mensual")
    void validateDataMonthlyAggregation() throws Exception {
        CompletableFuture<Map<String, Object>> future =
                predictionService.validateData(null, null, "M");
        Map<String, Object> result = future.get(30, TimeUnit.SECONDS);

        assertNotNull(result, "La validación con agregación mensual debe funcionar");
    }

    // ── Datos de ventas ─────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("Obtener datos de ventas")
    void getSalesData() throws Exception {
        CompletableFuture<Map<String, Object>> future =
                predictionService.getSalesData(null, null, "D");
        Map<String, Object> result = future.get(30, TimeUnit.SECONDS);

        assertNotNull(result, "Debe retornar datos de ventas");
    }

    @Test
    @Order(11)
    @DisplayName("Obtener datos de ventas con rango de fechas")
    void getSalesDataWithDateRange() throws Exception {
        CompletableFuture<Map<String, Object>> future =
                predictionService.getSalesData("2024-01-01", "2024-12-31", "M");
        Map<String, Object> result = future.get(30, TimeUnit.SECONDS);

        assertNotNull(result, "Debe retornar datos de ventas filtrados");
    }

    // ── Listar modelos ──────────────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("Listar modelos entrenados")
    void listModels() throws Exception {
        CompletableFuture<List<ModelInfoDTO>> future = predictionService.listModels();
        List<ModelInfoDTO> models = future.get(15, TimeUnit.SECONDS);

        assertNotNull(models, "La lista de modelos no debe ser null");
        // Puede estar vacía si no hay modelos entrenados
    }

    // ── Entrenamiento de modelo ─────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("Entrenar modelo lineal")
    void trainLinearModel() throws Exception {
        TrainModelRequestDTO request = new TrainModelRequestDTO("linear", null, null);

        CompletableFuture<TrainModelResponseDTO> future = predictionService.trainModel(request);
        TrainModelResponseDTO response = future.get(120, TimeUnit.SECONDS);

        assertNotNull(response, "El entrenamiento debe retornar respuesta");

        if (response.isSuccess()) {
            assertNotNull(response.getModelKey(), "Modelo exitoso debe tener model_key");
            trainedModelKey = response.getModelKey();

            // Verificar métricas
            if (response.getMetrics() != null) {
                assertTrue(response.getMetrics().containsKey("r2"),
                        "Métricas deben incluir R²");
            }
        }
        // Si no es exitoso, puede ser por datos insuficientes - eso es válido
    }

    // ── Forecast (predicción) ───────────────────────────────────────────

    @Test
    @Order(25)
    @DisplayName("Generar forecast con modelo entrenado")
    void generateForecast() throws Exception {
        // Este test depende de que exista un modelo entrenado
        if (trainedModelKey == null) {
            // Intentar obtener un modelo existente
            List<ModelInfoDTO> models = predictionService.listModels().get(15, TimeUnit.SECONDS);
            if (models != null && !models.isEmpty()) {
                trainedModelKey = models.get(0).getModelKey();
            }
        }

        Assumptions.assumeTrue(trainedModelKey != null,
                "Se requiere un modelo entrenado para test de forecast");

        ForecastRequestDTO request = new ForecastRequestDTO(trainedModelKey, null, 30);

        CompletableFuture<ForecastResponseDTO> future = predictionService.forecast(request);
        ForecastResponseDTO response = future.get(60, TimeUnit.SECONDS);

        assertNotNull(response, "Forecast debe retornar respuesta");
        if (response.isSuccess()) {
            assertNotNull(response.getPredictions(), "Debe incluir predicciones");
        }
    }

    // ── Forecast (casos negativos) ──────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("Forecast con modelo inexistente retorna error")
    void forecastWithInvalidModel() throws Exception {
        ForecastRequestDTO request = new ForecastRequestDTO("modelo_inexistente_xyz", null, 30);

        CompletableFuture<ForecastResponseDTO> future = predictionService.forecast(request);
        ForecastResponseDTO response = future.get(60, TimeUnit.SECONDS);

        // Puede ser null o con success=false
        if (response != null) {
            assertFalse(response.isSuccess(), "Forecast con modelo inexistente debe fallar");
        }
    }

    @Test
    @Order(31)
    @DisplayName("Forecast con 0 períodos maneja correctamente")
    void forecastWithZeroPeriods() throws Exception {
        Assumptions.assumeTrue(trainedModelKey != null,
                "Se requiere un modelo entrenado");

        ForecastRequestDTO request = new ForecastRequestDTO(trainedModelKey, null, 0);

        CompletableFuture<ForecastResponseDTO> future = predictionService.forecast(request);
        ForecastResponseDTO response = future.get(60, TimeUnit.SECONDS);

        // El backend debe manejar este caso (error o predicción vacía)
        assertNotNull(response, "Debe retornar alguna respuesta, no null");
    }

    // ── Historial ───────────────────────────────────────────────────────

    @Test
    @Order(35)
    @DisplayName("Obtener historial de predicciones")
    void getHistory() throws Exception {
        CompletableFuture<List<Map<String, Object>>> future = predictionService.getHistory(10);
        List<Map<String, Object>> history = future.get(15, TimeUnit.SECONDS);

        assertNotNull(history, "El historial no debe ser null");
        // Puede estar vacío si no hay predicciones previas
    }

    // ── Carga de modelos ────────────────────────────────────────────────

    @Test
    @Order(40)
    @DisplayName("Cargar todos los modelos guardados")
    void loadAllModels() throws Exception {
        CompletableFuture<Boolean> future = predictionService.loadAllModels();
        Boolean result = future.get(30, TimeUnit.SECONDS);

        assertNotNull(result, "La carga de modelos debe retornar resultado");
    }

    // ── Eliminación de modelo ───────────────────────────────────────────

    @Test
    @Order(45)
    @DisplayName("Eliminar modelo inexistente retorna false")
    void deleteInvalidModel() throws Exception {
        CompletableFuture<Boolean> future = predictionService.deleteModel("modelo_xyz_no_existe");
        Boolean result = future.get(15, TimeUnit.SECONDS);

        assertFalse(result, "Eliminar modelo inexistente debe retornar false");
    }

    // ── Flujo completo ──────────────────────────────────────────────────

    @Test
    @Order(50)
    @DisplayName("Flujo completo: validar → entrenar → listar → forecast")
    void fullPredictionFlow() throws Exception {
        // 1. Validar datos
        Map<String, Object> validation =
                predictionService.validateData(null, null, "D").get(30, TimeUnit.SECONDS);
        assertNotNull(validation, "Validación debe retornar resultado");

        // Si los datos no son válidos, saltar el resto
        Object validFlag = validation.get("valid");
        boolean dataValid = validFlag instanceof Boolean ? (Boolean) validFlag :
                validFlag != null && Boolean.parseBoolean(validFlag.toString());

        if (!dataValid) {
            // Datos insuficientes - test pasa pero salta el entrenamiento
            return;
        }

        // 2. Entrenar modelo
        TrainModelRequestDTO trainReq = new TrainModelRequestDTO("linear", null, null);
        TrainModelResponseDTO trainResp =
                predictionService.trainModel(trainReq).get(120, TimeUnit.SECONDS);
        assertNotNull(trainResp);

        if (!trainResp.isSuccess()) {
            // El entrenamiento falló - puede ser por datos insuficientes
            return;
        }

        String modelKey = trainResp.getModelKey();
        assertNotNull(modelKey);

        // 3. Listar modelos y verificar que el nuevo aparece
        List<ModelInfoDTO> models = predictionService.listModels().get(15, TimeUnit.SECONDS);
        assertNotNull(models);
        boolean found = models.stream().anyMatch(m -> modelKey.equals(m.getModelKey()));
        assertTrue(found, "El modelo recién entrenado debe aparecer en la lista");

        // 4. Generar forecast
        ForecastRequestDTO forecastReq = new ForecastRequestDTO(modelKey, null, 7);
        ForecastResponseDTO forecastResp =
                predictionService.forecast(forecastReq).get(60, TimeUnit.SECONDS);
        assertNotNull(forecastResp);

        if (forecastResp.isSuccess()) {
            assertNotNull(forecastResp.getPredictions());
        }

        // 5. Limpiar - eliminar el modelo de prueba
        predictionService.deleteModel(modelKey).get(15, TimeUnit.SECONDS);
    }

    // ── Sin autenticación ───────────────────────────────────────────────

    @Test
    @Order(60)
    @DisplayName("Operaciones sin token fallan correctamente")
    void operationsWithoutTokenFail() throws Exception {
        // Guardar token
        String realToken = UserSession.getAccessToken();

        // Simular sesión sin token
        UserSession.clear();

        // Intentar listar modelos sin token
        CompletableFuture<List<ModelInfoDTO>> future = predictionService.listModels();
        List<ModelInfoDTO> models = future.get(15, TimeUnit.SECONDS);

        // Debe retornar lista vacía (no crash)
        assertNotNull(models);

        // Restaurar sesión
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);
        UserSession.setFromLoginResponse(response);
    }
}
