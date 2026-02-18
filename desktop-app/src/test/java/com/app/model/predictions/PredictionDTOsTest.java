package com.app.model.predictions;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para DTOs de predicciones - verifica serialización/deserialización JSON
 * que es crítica para la comunicación con el backend FastAPI.
 */
class PredictionDTOsTest {

    private static final Gson gson = new Gson();

    // ── TrainModelRequestDTO ──────────────────────────────────────────────

    @Test
    @DisplayName("TrainModelRequestDTO serializa correctamente con model_type")
    void trainRequestSerializes() {
        TrainModelRequestDTO req = new TrainModelRequestDTO("linear", "2024-01-01", "2025-01-01");
        String json = gson.toJson(req);

        assertTrue(json.contains("\"model_type\":\"linear\""));
        assertTrue(json.contains("\"fecha_inicio\":\"2024-01-01\""));
        assertTrue(json.contains("\"fecha_fin\":\"2025-01-01\""));
    }

    @Test
    @DisplayName("TrainModelRequestDTO permite valores null en fechas")
    void trainRequestNullDates() {
        TrainModelRequestDTO req = new TrainModelRequestDTO("arima", null, null);
        String json = gson.toJson(req);

        assertTrue(json.contains("\"model_type\":\"arima\""));
    }

    // ── TrainModelResponseDTO ─────────────────────────────────────────────

    @Test
    @DisplayName("TrainModelResponseDTO deserializa respuesta exitosa del backend")
    void trainResponseDeserializesSuccess() {
        String json = """
                {
                    "success": true,
                    "model_id": 123,
                    "model_key": "linear_20250101_20250601",
                    "model_type": "linear",
                    "metrics": {"r2": 0.85, "mae": 1234.56, "rmse": 1500.0},
                    "meets_r2_threshold": true,
                    "recommendation": "Modelo aprobado",
                    "training_samples": 200,
                    "test_samples": 60
                }
                """;

        TrainModelResponseDTO resp = gson.fromJson(json, TrainModelResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertEquals(123, resp.getModelId());
        assertEquals("linear_20250101_20250601", resp.getModelKey());
        assertEquals("linear", resp.getModelType());
        assertNotNull(resp.getMetrics());
        assertEquals(0.85, resp.getMetrics().get("r2"), 0.001);
        assertEquals(1234.56, resp.getMetrics().get("mae"), 0.01);
        assertTrue(resp.getMeetsR2Threshold());
        assertEquals("Modelo aprobado", resp.getRecommendation());
        assertEquals(200, resp.getTrainingSamples());
        assertEquals(60, resp.getTestSamples());
    }

    @Test
    @DisplayName("TrainModelResponseDTO deserializa respuesta con error")
    void trainResponseDeserializesError() {
        String json = """
                {
                    "success": false,
                    "error": "Datos insuficientes",
                    "issues": ["Menos de 180 días de datos"]
                }
                """;

        TrainModelResponseDTO resp = gson.fromJson(json, TrainModelResponseDTO.class);

        assertFalse(resp.isSuccess());
        assertEquals("Datos insuficientes", resp.getError());
        assertNotNull(resp.getIssues());
        assertEquals(1, resp.getIssues().size());
    }

    @Test
    @DisplayName("TrainModelResponseDTO con R² bajo umbral")
    void trainResponseBelowThreshold() {
        String json = """
                {
                    "success": true,
                    "model_key": "arima_test",
                    "metrics": {"r2": 0.45},
                    "meets_r2_threshold": false,
                    "recommendation": "Intente con más datos"
                }
                """;

        TrainModelResponseDTO resp = gson.fromJson(json, TrainModelResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertFalse(resp.getMeetsR2Threshold());
        assertEquals(0.45, resp.getMetrics().get("r2"), 0.001);
    }

    // ── ForecastRequestDTO ────────────────────────────────────────────────

    @Test
    @DisplayName("ForecastRequestDTO serializa con model_key y periods")
    void forecastRequestSerializes() {
        ForecastRequestDTO req = new ForecastRequestDTO("linear_key", null, 30);
        String json = gson.toJson(req);

        assertTrue(json.contains("\"model_key\":\"linear_key\""));
        assertTrue(json.contains("\"periods\":30"));
    }

    // ── ForecastResponseDTO ───────────────────────────────────────────────

    @Test
    @DisplayName("ForecastResponseDTO deserializa predicciones con dates y values")
    void forecastResponseDeserializes() {
        String json = """
                {
                    "success": true,
                    "predictions": {
                        "dates": ["2025-07-01", "2025-07-02", "2025-07-03"],
                        "values": [15000.0, 15200.0, 14800.0],
                        "lower_ci": [14000.0, 14200.0, 13800.0],
                        "upper_ci": [16000.0, 16200.0, 15800.0]
                    },
                    "model_type": "linear",
                    "periods": 3
                }
                """;

        ForecastResponseDTO resp = gson.fromJson(json, ForecastResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getPredictions());
        assertEquals("linear", resp.getModelType());
        assertEquals(3, resp.getPeriods());

        @SuppressWarnings("unchecked")
        List<String> dates = (List<String>) resp.getPredictions().get("dates");
        assertEquals(3, dates.size());
        assertEquals("2025-07-01", dates.get(0));
    }

    @Test
    @DisplayName("ForecastResponseDTO deserializa error con sugerencia")
    void forecastResponseError() {
        String json = """
                {
                    "success": false,
                    "error": "Modelo no encontrado",
                    "suggestion": "Entrene el modelo primero"
                }
                """;

        ForecastResponseDTO resp = gson.fromJson(json, ForecastResponseDTO.class);

        assertFalse(resp.isSuccess());
        assertEquals("Modelo no encontrado", resp.getError());
        assertEquals("Entrene el modelo primero", resp.getSuggestion());
    }

    // ── ModelInfoDTO ──────────────────────────────────────────────────────

    @Test
    @DisplayName("ModelInfoDTO deserializa info de modelo guardado")
    void modelInfoDeserializes() {
        String json = """
                {
                    "model_key": "sarima_20240101_20250101",
                    "model_type": "sarima",
                    "is_fitted": true,
                    "metrics": {"r2": 0.92, "mae": 500.0},
                    "trained_at": "2025-06-15T10:30:00"
                }
                """;

        ModelInfoDTO info = gson.fromJson(json, ModelInfoDTO.class);

        assertEquals("sarima_20240101_20250101", info.getModelKey());
        assertEquals("sarima", info.getModelType());
        assertTrue(info.isFitted());
        assertEquals(0.92, info.getMetrics().get("r2"), 0.001);
        assertEquals("2025-06-15T10:30:00", info.getTrainedAt());
    }

    // ── AutoSelectResponseDTO ─────────────────────────────────────────────

    @Test
    @DisplayName("AutoSelectResponseDTO deserializa selección automática")
    void autoSelectResponseDeserializes() {
        String json = """
                {
                    "success": true,
                    "best_model": {
                        "model_type": "sarima",
                        "r2": 0.95
                    },
                    "meets_r2_threshold": true,
                    "recommendation": "SARIMA es el mejor modelo para estos datos"
                }
                """;

        AutoSelectResponseDTO resp = gson.fromJson(json, AutoSelectResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getBestModel());
        assertEquals("sarima", resp.getBestModel().get("model_type"));
        assertTrue(resp.getMeetsR2Threshold());
        assertEquals("SARIMA es el mejor modelo para estos datos", resp.getRecommendation());
    }
}
