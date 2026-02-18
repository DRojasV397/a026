package com.app.model.predictions;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para DTOs extendidos de predicciones.
 * Cubre los DTOs nuevos: SalesDataRequestDTO, SalesDataResponseDTO,
 * ValidateDataResponseDTO, PredictionHistoryItemDTO, LoadModelResponseDTO,
 * SavedModelInfoDTO, AutoSelectRequestDTO, ModelTypesResponseDTO,
 * DeleteModelResponseDTO, LoadAllModelsResponseDTO.
 */
@DisplayName("ExtendedPredictionDTOs - DTOs complementarios del modulo de predicciones")
class ExtendedPredictionDTOsTest {

    private static final Gson gson = new Gson();

    // ── SalesDataRequestDTO ─────────────────────────────────────────────

    @Test
    @DisplayName("SalesDataRequestDTO serializa con fechas y agregacion diaria")
    void salesDataRequestSerializesWithDailyAggregation() {
        SalesDataRequestDTO req = new SalesDataRequestDTO("2024-01-01", "2024-12-31", "D");
        String json = gson.toJson(req);

        assertTrue(json.contains("\"fecha_inicio\":\"2024-01-01\""));
        assertTrue(json.contains("\"fecha_fin\":\"2024-12-31\""));
        assertTrue(json.contains("\"aggregation\":\"D\""));
    }

    @Test
    @DisplayName("SalesDataRequestDTO usa agregacion diaria por defecto")
    void salesDataRequestDefaultAggregation() {
        SalesDataRequestDTO req = new SalesDataRequestDTO();
        assertEquals("D", req.getAggregation());
    }

    @Test
    @DisplayName("SalesDataRequestDTO serializa con agregacion mensual")
    void salesDataRequestMonthlyAggregation() {
        SalesDataRequestDTO req = new SalesDataRequestDTO(null, null, "M");
        String json = gson.toJson(req);
        assertTrue(json.contains("\"aggregation\":\"M\""));
    }

    @Test
    @DisplayName("SalesDataRequestDTO serializa con agregacion semanal")
    void salesDataRequestWeeklyAggregation() {
        SalesDataRequestDTO req = new SalesDataRequestDTO("2024-06-01", "2024-12-31", "W");
        String json = gson.toJson(req);
        assertTrue(json.contains("\"aggregation\":\"W\""));
    }

    // ── SalesDataResponseDTO ────────────────────────────────────────────

    @Test
    @DisplayName("SalesDataResponseDTO deserializa respuesta con datos de ventas")
    void salesDataResponseDeserializes() {
        String json = """
                {
                    "success": true,
                    "data": [
                        {"fecha": "2024-01-01", "total": 15000.50},
                        {"fecha": "2024-01-02", "total": 12300.75},
                        {"fecha": "2024-01-03", "total": 18200.00}
                    ],
                    "count": 3,
                    "aggregation": "D"
                }
                """;

        SalesDataResponseDTO resp = gson.fromJson(json, SalesDataResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(3, resp.getCount());
        assertEquals("D", resp.getAggregation());
        assertEquals(3, resp.getData().size());
    }

    @Test
    @DisplayName("SalesDataResponseDTO con datos vacios")
    void salesDataResponseEmpty() {
        String json = """
                {
                    "success": true,
                    "data": [],
                    "count": 0,
                    "aggregation": "M"
                }
                """;

        SalesDataResponseDTO resp = gson.fromJson(json, SalesDataResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(0, resp.getCount());
        assertTrue(resp.getData().isEmpty());
    }

    // ── ValidateDataResponseDTO ─────────────────────────────────────────

    @Test
    @DisplayName("ValidateDataResponseDTO deserializa validacion exitosa")
    void validateDataResponseValidDeserializes() {
        String json = """
                {
                    "valid": true,
                    "issues": [],
                    "data_points": 365,
                    "min_required": 180,
                    "date_range": {
                        "start": "2024-01-01",
                        "end": "2024-12-31"
                    }
                }
                """;

        ValidateDataResponseDTO resp = gson.fromJson(json, ValidateDataResponseDTO.class);

        assertTrue(resp.isValid());
        assertNotNull(resp.getIssues());
        assertTrue(resp.getIssues().isEmpty());
        assertEquals(365, resp.getDataPoints());
        assertEquals(180, resp.getMinRequired());
        assertNotNull(resp.getDateRange());
        assertEquals("2024-01-01", resp.getDateRange().get("start"));
        assertEquals("2024-12-31", resp.getDateRange().get("end"));
    }

    @Test
    @DisplayName("ValidateDataResponseDTO deserializa datos insuficientes con issues")
    void validateDataResponseInvalid() {
        String json = """
                {
                    "valid": false,
                    "issues": [
                        "Solo 90 dias de datos, se requieren minimo 180",
                        "Rango de fechas menor a 6 meses"
                    ],
                    "data_points": 90,
                    "min_required": 180,
                    "date_range": {
                        "start": "2024-10-01",
                        "end": "2024-12-31"
                    }
                }
                """;

        ValidateDataResponseDTO resp = gson.fromJson(json, ValidateDataResponseDTO.class);

        assertFalse(resp.isValid());
        assertEquals(2, resp.getIssues().size());
        assertEquals(90, resp.getDataPoints());
        assertTrue(resp.getDataPoints() < resp.getMinRequired());
    }

    // ── PredictionHistoryItemDTO ────────────────────────────────────────

    @Test
    @DisplayName("PredictionHistoryItemDTO deserializa item del historial")
    void predictionHistoryItemDeserializes() {
        String json = """
                {
                    "id": 42,
                    "fecha": "2025-03-15",
                    "valor_predicho": 25000.50,
                    "intervalo_inferior": 22000.00,
                    "intervalo_superior": 28000.00,
                    "confianza": 0.95
                }
                """;

        PredictionHistoryItemDTO item = gson.fromJson(json, PredictionHistoryItemDTO.class);

        assertEquals(42, item.getId());
        assertEquals("2025-03-15", item.getFecha());
        assertEquals(25000.50, item.getValorPredicho(), 0.01);
        assertEquals(22000.00, item.getIntervaloInferior(), 0.01);
        assertEquals(28000.00, item.getIntervaloSuperior(), 0.01);
        assertEquals(0.95, item.getConfianza(), 0.001);
    }

    @Test
    @DisplayName("PredictionHistoryItemDTO deserializa lista de historial")
    void predictionHistoryListDeserializes() {
        String json = """
                [
                    {"id": 1, "fecha": "2025-01-01", "valor_predicho": 10000.0, "confianza": 0.9},
                    {"id": 2, "fecha": "2025-01-02", "valor_predicho": 11000.0, "confianza": 0.88},
                    {"id": 3, "fecha": "2025-01-03", "valor_predicho": 9500.0, "confianza": 0.92}
                ]
                """;

        List<PredictionHistoryItemDTO> items = gson.fromJson(json,
                new TypeToken<List<PredictionHistoryItemDTO>>(){}.getType());

        assertNotNull(items);
        assertEquals(3, items.size());
        assertEquals(1, items.get(0).getId());
        assertEquals(11000.0, items.get(1).getValorPredicho(), 0.01);
    }

    @Test
    @DisplayName("PredictionHistoryItemDTO con campos null")
    void predictionHistoryItemWithNulls() {
        String json = """
                {
                    "id": 99,
                    "fecha": null,
                    "valor_predicho": null,
                    "intervalo_inferior": null,
                    "intervalo_superior": null,
                    "confianza": null
                }
                """;

        PredictionHistoryItemDTO item = gson.fromJson(json, PredictionHistoryItemDTO.class);

        assertEquals(99, item.getId());
        assertNull(item.getFecha());
        assertNull(item.getValorPredicho());
        assertNull(item.getConfianza());
    }

    // ── LoadModelResponseDTO ────────────────────────────────────────────

    @Test
    @DisplayName("LoadModelResponseDTO deserializa carga exitosa")
    void loadModelResponseSuccessDeserializes() {
        String json = """
                {
                    "success": true,
                    "model_key": "linear_20240101_20241231",
                    "model_type": "linear",
                    "is_fitted": true,
                    "metrics": {"r2": 0.87, "mae": 850.50},
                    "trained_at": "2025-01-15T10:30:00",
                    "path": "/models/linear_20240101_20241231.pkl"
                }
                """;

        LoadModelResponseDTO resp = gson.fromJson(json, LoadModelResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertEquals("linear_20240101_20241231", resp.getModelKey());
        assertEquals("linear", resp.getModelType());
        assertTrue(resp.getIsFitted());
        assertNotNull(resp.getMetrics());
        assertEquals(0.87, resp.getMetrics().get("r2"), 0.001);
        assertNotNull(resp.getPath());
        assertNull(resp.getError());
    }

    @Test
    @DisplayName("LoadModelResponseDTO deserializa error de carga")
    void loadModelResponseErrorDeserializes() {
        String json = """
                {
                    "success": false,
                    "error": "Archivo de modelo no encontrado"
                }
                """;

        LoadModelResponseDTO resp = gson.fromJson(json, LoadModelResponseDTO.class);

        assertFalse(resp.isSuccess());
        assertNotNull(resp.getError());
        assertNull(resp.getModelKey());
    }

    // ── SavedModelInfoDTO ───────────────────────────────────────────────

    @Test
    @DisplayName("SavedModelInfoDTO deserializa info de modelo en disco")
    void savedModelInfoDeserializes() {
        String json = """
                {
                    "model_key": "sarima_20230101_20241231",
                    "filename": "sarima_20230101_20241231.pkl",
                    "size_bytes": 524288,
                    "created_at": "2025-06-01T09:00:00",
                    "modified_at": "2025-06-01T09:00:00",
                    "is_loaded": true
                }
                """;

        SavedModelInfoDTO info = gson.fromJson(json, SavedModelInfoDTO.class);

        assertEquals("sarima_20230101_20241231", info.getModelKey());
        assertEquals("sarima_20230101_20241231.pkl", info.getFilename());
        assertEquals(524288, info.getSizeBytes());
        assertNotNull(info.getCreatedAt());
        assertNotNull(info.getModifiedAt());
        assertTrue(info.isLoaded());
    }

    @Test
    @DisplayName("SavedModelInfoDTO lista de modelos deserializa")
    void savedModelInfoListDeserializes() {
        String json = """
                [
                    {"model_key": "linear_1", "filename": "linear_1.pkl", "size_bytes": 1024, "created_at": "2025-01-01", "modified_at": "2025-01-01", "is_loaded": true},
                    {"model_key": "arima_1", "filename": "arima_1.pkl", "size_bytes": 2048, "created_at": "2025-02-01", "modified_at": "2025-02-01", "is_loaded": false}
                ]
                """;

        List<SavedModelInfoDTO> models = gson.fromJson(json,
                new TypeToken<List<SavedModelInfoDTO>>(){}.getType());

        assertEquals(2, models.size());
        assertTrue(models.get(0).isLoaded());
        assertFalse(models.get(1).isLoaded());
    }

    // ── AutoSelectRequestDTO ────────────────────────────────────────────

    @Test
    @DisplayName("AutoSelectRequestDTO serializa con fechas")
    void autoSelectRequestSerializesWithDates() {
        AutoSelectRequestDTO req = new AutoSelectRequestDTO("2023-01-01", "2024-12-31");
        String json = gson.toJson(req);

        assertTrue(json.contains("\"fecha_inicio\":\"2023-01-01\""));
        assertTrue(json.contains("\"fecha_fin\":\"2024-12-31\""));
    }

    @Test
    @DisplayName("AutoSelectRequestDTO serializa sin fechas")
    void autoSelectRequestSerializesWithoutDates() {
        AutoSelectRequestDTO req = new AutoSelectRequestDTO();
        String json = gson.toJson(req);

        assertNotNull(json);
        // Sin fechas, los campos seran null y pueden o no aparecer
    }

    // ── ModelTypesResponseDTO ───────────────────────────────────────────

    @Test
    @DisplayName("ModelTypesResponseDTO deserializa tipos de modelo")
    void modelTypesResponseDeserializes() {
        String json = """
                {
                    "model_types": [
                        {"id": "linear", "name": "Regresion Lineal", "description": "Modelo de regresion lineal", "use_case": "Tendencias lineales simples"},
                        {"id": "arima", "name": "ARIMA", "description": "AutoRegressive Integrated Moving Average", "use_case": "Series de tiempo"},
                        {"id": "sarima", "name": "SARIMA", "description": "Seasonal ARIMA", "use_case": "Patrones estacionales"},
                        {"id": "random_forest", "name": "Random Forest", "description": "Ensemble de arboles", "use_case": "Patrones complejos"}
                    ],
                    "r2_threshold": 0.7,
                    "max_forecast_days": 180
                }
                """;

        ModelTypesResponseDTO resp = gson.fromJson(json, ModelTypesResponseDTO.class);

        assertNotNull(resp.getModelTypes());
        assertEquals(4, resp.getModelTypes().size());
        assertEquals(0.7, resp.getR2Threshold(), 0.001);
        assertEquals(180, resp.getMaxForecastDays());

        ModelTypesResponseDTO.ModelTypeInfo linear = resp.getModelTypes().get(0);
        assertEquals("linear", linear.getId());
        assertEquals("Regresion Lineal", linear.getName());
        assertNotNull(linear.getDescription());
        assertNotNull(linear.getUseCase());
    }

    // ── DeleteModelResponseDTO ──────────────────────────────────────────

    @Test
    @DisplayName("DeleteModelResponseDTO deserializa eliminacion exitosa")
    void deleteModelResponseSuccessDeserializes() {
        String json = """
                {
                    "success": true,
                    "model_key": "linear_test_key",
                    "deleted_from_memory": true,
                    "deleted_from_disk": true
                }
                """;

        DeleteModelResponseDTO resp = gson.fromJson(json, DeleteModelResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertEquals("linear_test_key", resp.getModelKey());
        assertTrue(resp.getDeletedFromMemory());
        assertTrue(resp.getDeletedFromDisk());
        assertNull(resp.getError());
    }

    @Test
    @DisplayName("DeleteModelResponseDTO deserializa error de eliminacion")
    void deleteModelResponseErrorDeserializes() {
        String json = """
                {
                    "success": false,
                    "model_key": "no_existe",
                    "error": "Modelo no encontrado"
                }
                """;

        DeleteModelResponseDTO resp = gson.fromJson(json, DeleteModelResponseDTO.class);

        assertFalse(resp.isSuccess());
        assertNotNull(resp.getError());
    }

    // ── LoadAllModelsResponseDTO ────────────────────────────────────────

    @Test
    @DisplayName("LoadAllModelsResponseDTO deserializa carga masiva exitosa")
    void loadAllModelsResponseDeserializes() {
        String json = """
                {
                    "success": true,
                    "loaded": [
                        {"model_key": "linear_1", "model_type": "linear"},
                        {"model_key": "sarima_1", "model_type": "sarima"}
                    ],
                    "failed": [
                        {"model_key": "corrupted_model", "error": "Formato invalido"}
                    ],
                    "total_loaded": 2,
                    "total_failed": 1,
                    "message": "Carga completada con advertencias"
                }
                """;

        LoadAllModelsResponseDTO resp = gson.fromJson(json, LoadAllModelsResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getTotalLoaded());
        assertEquals(1, resp.getTotalFailed());
        assertEquals(2, resp.getLoaded().size());
        assertEquals(1, resp.getFailed().size());
        assertNotNull(resp.getMessage());
    }

    @Test
    @DisplayName("LoadAllModelsResponseDTO con ningun modelo guardado")
    void loadAllModelsResponseEmpty() {
        String json = """
                {
                    "success": true,
                    "loaded": [],
                    "failed": [],
                    "total_loaded": 0,
                    "total_failed": 0,
                    "message": "No hay modelos guardados"
                }
                """;

        LoadAllModelsResponseDTO resp = gson.fromJson(json, LoadAllModelsResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertEquals(0, resp.getTotalLoaded());
        assertTrue(resp.getLoaded().isEmpty());
    }
}
