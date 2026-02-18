package com.app.data;

import com.app.model.data.api.*;
import com.app.model.predictions.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests que cargan datos sinteticos desde archivos JSON en resources.
 * Valida que los archivos sinteticos son bien formados y deserializan
 * correctamente en los DTOs del sistema.
 */
@DisplayName("SyntheticJsonData - Datos sinteticos JSON para pruebas")
class SyntheticJsonDataTest {

    private static final Gson gson = new Gson();

    // ── Helpers ─────────────────────────────────────────────────────────

    private <T> T loadJson(String resourcePath, Class<T> clazz) {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        assertNotNull(is, "Recurso no encontrado: " + resourcePath);
        return gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), clazz);
    }

    // ── Train Response - Exitoso ────────────────────────────────────────

    @Test
    @DisplayName("train_response_success.json deserializa como TrainModelResponseDTO exitoso")
    void trainResponseSuccessLoadsFromFile() {
        TrainModelResponseDTO resp = loadJson(
                "/synthetic/train_response_success.json", TrainModelResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertEquals("random_forest_20230101_20241231", resp.getModelKey());
        assertEquals("random_forest", resp.getModelType());
        assertTrue(resp.getMeetsR2Threshold());
        assertNotNull(resp.getMetrics());
        assertTrue(resp.getMetrics().get("r2") >= 0.7,
                "R2 debe cumplir umbral de aceptacion (>= 0.7)");
        assertEquals(550, resp.getTrainingSamples());
        assertEquals(180, resp.getTestSamples());
        assertNotNull(resp.getRecommendation());
    }

    // ── Train Response - R2 Bajo ────────────────────────────────────────

    @Test
    @DisplayName("train_response_low_r2.json deserializa modelo con R2 bajo umbral")
    void trainResponseLowR2LoadsFromFile() {
        TrainModelResponseDTO resp = loadJson(
                "/synthetic/train_response_low_r2.json", TrainModelResponseDTO.class);

        assertTrue(resp.isSuccess()); // El entrenamiento ocurrio, pero R2 es bajo
        assertFalse(resp.getMeetsR2Threshold());
        assertTrue(resp.getMetrics().get("r2") < 0.7,
                "R2 debe estar bajo el umbral");
        assertNotNull(resp.getIssues());
        assertFalse(resp.getIssues().isEmpty());
    }

    // ── Forecast Response ───────────────────────────────────────────────

    @Test
    @DisplayName("forecast_response_30days.json deserializa 30 predicciones con CI")
    @SuppressWarnings("unchecked")
    void forecastResponseLoadsFromFile() {
        ForecastResponseDTO resp = loadJson(
                "/synthetic/forecast_response_30days.json", ForecastResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertEquals("random_forest", resp.getModelType());
        assertEquals(30, resp.getPeriods());
        assertNotNull(resp.getPredictions());

        List<String> dates = (List<String>) resp.getPredictions().get("dates");
        List<Number> values = (List<Number>) resp.getPredictions().get("values");
        List<Number> lowerCi = (List<Number>) resp.getPredictions().get("lower_ci");
        List<Number> upperCi = (List<Number>) resp.getPredictions().get("upper_ci");

        assertEquals(30, dates.size());
        assertEquals(30, values.size());
        assertEquals(30, lowerCi.size());
        assertEquals(30, upperCi.size());

        // Verificar consistencia de intervalos de confianza
        for (int i = 0; i < values.size(); i++) {
            double val = values.get(i).doubleValue();
            double lower = lowerCi.get(i).doubleValue();
            double upper = upperCi.get(i).doubleValue();

            assertTrue(lower < val, "lower_ci debe ser menor que value en posicion " + i);
            assertTrue(upper > val, "upper_ci debe ser mayor que value en posicion " + i);
            assertTrue(val > 0, "Prediccion debe ser positiva en posicion " + i);
        }
    }

    // ── Auto Select Response ────────────────────────────────────────────

    @Test
    @DisplayName("auto_select_response.json deserializa seleccion automatica")
    void autoSelectResponseLoadsFromFile() {
        AutoSelectResponseDTO resp = loadJson(
                "/synthetic/auto_select_response.json", AutoSelectResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertTrue(resp.getMeetsR2Threshold());
        assertNotNull(resp.getBestModel());
        assertEquals("sarima", resp.getBestModel().get("model_type"));
        assertNotNull(resp.getAllModels());
        assertNotNull(resp.getRecommendation());
        assertTrue(resp.getRecommendation().contains("SARIMA"));
    }

    // ── Upload Response ─────────────────────────────────────────────────

    @Test
    @DisplayName("upload_response_ventas.json deserializa respuesta de upload sintetico")
    void uploadResponseLoadsFromFile() {
        UploadResponseDTO resp = loadJson(
                "/synthetic/upload_response_ventas.json", UploadResponseDTO.class);

        assertEquals("synth-ventas-365", resp.getUploadId());
        assertEquals("ventas_2024_sintetico.csv", resp.getFilename());
        assertEquals("csv", resp.getFileType());
        assertEquals(365, resp.getTotalRows());
        assertEquals("uploaded", resp.getStatus());
        assertNotNull(resp.getColumnInfo());
        assertEquals(5, resp.getColumnInfo().size());
        assertTrue(resp.getColumnInfo().containsKey("fecha"));
        assertTrue(resp.getColumnInfo().containsKey("total"));
    }

    // ── Quality Report Response ─────────────────────────────────────────

    @Test
    @DisplayName("quality_report_response.json deserializa reporte de calidad")
    void qualityReportResponseLoadsFromFile() {
        QualityReportResponseDTO resp = loadJson(
                "/synthetic/quality_report_response.json", QualityReportResponseDTO.class);

        assertEquals("synth-ventas-365", resp.getUploadId());
        assertTrue(resp.getOverallScore() > 90.0);
        assertEquals(365, resp.getTotalRows());
        assertEquals(358, resp.getValidRows());
        assertNotNull(resp.getMetrics());
        assertEquals(5, resp.getMetrics().size());
        assertNotNull(resp.getIssues());
        assertFalse(resp.getIssues().isEmpty());
        assertNotNull(resp.getRecommendations());
        assertFalse(resp.getRecommendations().isEmpty());

        // Verificar metricas por columna
        QualityReportResponseDTO.QualityMetricDTO fechaMetric = resp.getMetrics().get(0);
        assertEquals("fecha", fechaMetric.getColumn());
        assertEquals(100.0, fechaMetric.getCompleteness(), 0.01);
    }
}
