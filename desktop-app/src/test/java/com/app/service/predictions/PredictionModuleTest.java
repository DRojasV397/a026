package com.app.service.predictions;

import com.app.model.Phase2ConfigDTO;
import com.app.model.predictions.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del módulo de predicciones.
 * Valida configuración por modelo, DTOs de request/response, y pipeline de predicción.
 * Cubre los 4 tipos de modelo: linear, arima, sarima, random_forest.
 */
@DisplayName("PredictionModule - Módulo de predicciones")
class PredictionModuleTest {

    private static final Gson gson = new Gson();

    // ══════════════════════════════════════════════════════════════════════
    // CONFIGURACIÓN POR MODELO (Phase2ConfigDTO)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Linear: configuración válida con variables de regresión lineal")
    void linearModelConfigIsValid() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                List.of("Ventas Históricas", "Tiempo (t)", "Tendencia Global"),
                3
        );

        assertTrue(config.isValid(), "Configuración de Regresión Lineal debe ser válida");
        assertTrue(config.isHasEnoughData(), "Debe tener suficientes datos (12 meses)");
        assertTrue(config.isHasValidVariables(), "Variables de regresión lineal son válidas");
        assertEquals(3, config.getPredictionHorizon());
        assertEquals(70, config.getTrainPercentage());
        assertEquals(30, config.getValidationPercentage());
    }

    @Test
    @DisplayName("ARIMA: configuración válida con variables específicas de ARIMA")
    void arimaModelConfigIsValid() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2023, 6, 1),
                LocalDate.of(2024, 6, 30),
                List.of("Lags (1,2)", "Error Previo", "Diferencial"),
                6
        );

        assertTrue(config.isValid(), "Configuración ARIMA debe ser válida");
        assertTrue(config.isHasEnoughData(), "13 meses = suficiente para ARIMA");
        assertEquals(6, config.getPredictionHorizon());
    }

    @Test
    @DisplayName("SARIMA: configuración válida con variables estacionales")
    void sarimaModelConfigIsValid() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 12, 31),
                List.of("Ventas", "Mes", "Día Semana", "Festivos", "Temporada Alta"),
                4
        );

        assertTrue(config.isValid(), "Configuración SARIMA debe ser válida");
        assertTrue(config.isHasEnoughData(), "24 meses = suficiente para SARIMA");
        assertTrue(config.isHasValidVariables(), "5 variables estacionales son válidas");
        assertEquals(4, config.getPredictionHorizon());
    }

    @Test
    @DisplayName("Random Forest: configuración válida con múltiples variables")
    void randomForestModelConfigIsValid() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2024, 12, 31),
                List.of("Ventas", "Precio", "Competencia", "Stock", "Descuentos",
                        "Clima", "Categoría", "Región"),
                2
        );

        assertTrue(config.isValid(), "Configuración Random Forest debe ser válida");
        assertTrue(config.isHasEnoughData(), "36 meses = suficiente para Random Forest");
        assertEquals(8, config.getSelectedVariables().size());
        assertEquals(2, config.getPredictionHorizon());
    }

    // ══════════════════════════════════════════════════════════════════════
    // VALIDACIONES DE FECHA - REGLA RN-01.01
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Exactamente 6 meses de datos - cumple RN-01.01")
    void exactly6MonthsMeetsRN0101() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 7, 1), // 6 meses exactos
                List.of("Ventas Históricas"),
                1
        );

        assertTrue(config.isHasEnoughData(), "6 meses exactos debe cumplir RN-01.01");
        assertTrue(config.isValid());
    }

    @Test
    @DisplayName("5 meses y 29 días - NO cumple RN-01.01")
    void less6MonthsDoesNotMeetRN0101() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 6, 30), // 5 meses y 29 días
                List.of("Ventas Históricas"),
                1
        );

        assertFalse(config.isHasEnoughData(), "Menos de 6 meses NO cumple RN-01.01");
        assertFalse(config.isValid());
        assertTrue(config.getErrorMessage().contains("6 meses"));
    }

    @Test
    @DisplayName("Fechas iguales generan error de configuración")
    void equalDatesGenerateError() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2024, 6, 15),
                LocalDate.of(2024, 6, 15),
                List.of("Ventas"),
                1
        );

        assertFalse(config.isHasNoErrors());
        assertFalse(config.isValid());
        assertTrue(config.getErrorMessage().contains("igual"));
    }

    @Test
    @DisplayName("Fecha fin anterior a fecha inicio genera error")
    void endDateBeforeStartDateGeneratesError() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2024, 12, 31),
                LocalDate.of(2024, 1, 1),
                List.of("Ventas"),
                1
        );

        assertFalse(config.isHasNoErrors());
        assertFalse(config.isValid());
        assertTrue(config.getErrorMessage().contains("posterior"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // HORIZONTE DE PREDICCIÓN
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Horizonte de 1 mes es válido (mínimo)")
    void horizon1MonthIsValid() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                List.of("Ventas"),
                1
        );
        assertTrue(config.isValid());
        assertEquals(1, config.getPredictionHorizon());
    }

    @Test
    @DisplayName("Horizonte de 6 meses es válido (máximo)")
    void horizon6MonthsIsValid() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                List.of("Ventas"),
                6
        );
        assertTrue(config.isValid());
        assertEquals(6, config.getPredictionHorizon());
    }

    @Test
    @DisplayName("Horizonte de 0 meses es inválido")
    void horizon0MonthsIsInvalid() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                List.of("Ventas"),
                0
        );
        assertFalse(config.isValid());
    }

    @Test
    @DisplayName("Horizonte de 7 meses es inválido (supera máximo)")
    void horizon7MonthsIsInvalid() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                List.of("Ventas"),
                7
        );
        assertFalse(config.isValid());
    }

    // ══════════════════════════════════════════════════════════════════════
    // TrainModelRequestDTO - SERIALIZACIÓN
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TrainModelRequest para linear con fechas sintéticas")
    void trainModelRequestLinearSerializes() {
        TrainModelRequestDTO req = new TrainModelRequestDTO(
                "linear", "2024-01-01", "2024-12-31"
        );
        String json = gson.toJson(req);

        assertTrue(json.contains("\"model_type\":\"linear\""));
        assertTrue(json.contains("\"fecha_inicio\":\"2024-01-01\""));
        assertTrue(json.contains("\"fecha_fin\":\"2024-12-31\""));
    }

    @Test
    @DisplayName("TrainModelRequest para ARIMA con 13 meses de datos")
    void trainModelRequestArimaSerializes() {
        TrainModelRequestDTO req = new TrainModelRequestDTO(
                "arima", "2023-06-01", "2024-06-30"
        );
        String json = gson.toJson(req);

        assertTrue(json.contains("\"model_type\":\"arima\""));
    }

    @Test
    @DisplayName("TrainModelRequest para SARIMA con 2 años de datos")
    void trainModelRequestSarimaSerializes() {
        TrainModelRequestDTO req = new TrainModelRequestDTO(
                "sarima", "2022-01-01", "2023-12-31"
        );
        String json = gson.toJson(req);

        assertTrue(json.contains("\"model_type\":\"sarima\""));
    }

    @Test
    @DisplayName("TrainModelRequest para random_forest serializa correctamente")
    void trainModelRequestRandomForestSerializes() {
        TrainModelRequestDTO req = new TrainModelRequestDTO(
                "random_forest", "2022-01-01", "2024-12-31"
        );
        String json = gson.toJson(req);

        assertTrue(json.contains("\"model_type\":\"random_forest\""));
        assertTrue(json.contains("\"fecha_inicio\":\"2022-01-01\""));
        assertTrue(json.contains("\"fecha_fin\":\"2024-12-31\""));
    }

    // ══════════════════════════════════════════════════════════════════════
    // ForecastRequestDTO - SERIALIZACIÓN
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ForecastRequest con 30 períodos (1 mes) serializa correctamente")
    void forecastRequestWith30Periods() {
        ForecastRequestDTO req = new ForecastRequestDTO("linear_key_20240101", null, 30);
        String json = gson.toJson(req);

        assertTrue(json.contains("\"model_key\":\"linear_key_20240101\""));
        assertTrue(json.contains("\"periods\":30"));
    }

    @Test
    @DisplayName("ForecastRequest con 180 períodos (6 meses = máximo RN-03.03)")
    void forecastRequestWith180PeriodsMaxRN0303() {
        ForecastRequestDTO req = new ForecastRequestDTO("sarima_key", "sarima", 180);
        String json = gson.toJson(req);

        assertTrue(json.contains("\"periods\":180"));
    }

    @Test
    @DisplayName("ForecastRequest para 3 meses = 90 períodos")
    void forecastRequestFor3Months() {
        int horizonMonths = 3;
        int periods = horizonMonths * 30; // 90 días

        ForecastRequestDTO req = new ForecastRequestDTO("arima_key", "arima", periods);
        String json = gson.toJson(req);

        assertTrue(json.contains("\"periods\":90"));
        assertEquals(90, periods);
    }

    // ══════════════════════════════════════════════════════════════════════
    // TrainModelResponseDTO - DESERIALIZACIÓN CON DATOS SINTÉTICOS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TrainResponse exitoso con R² >= 0.7 (umbral de aceptación)")
    void trainResponseSuccessWithR2AboveThreshold() {
        String json = """
                {
                    "success": true,
                    "model_id": 42,
                    "model_key": "linear_20240101_20241231",
                    "model_type": "linear",
                    "metrics": {"r2": 0.87, "mae": 850.50, "rmse": 1120.30},
                    "meets_r2_threshold": true,
                    "recommendation": "Modelo aceptado - R² = 0.87",
                    "training_samples": 255,
                    "test_samples": 110
                }
                """;

        TrainModelResponseDTO resp = gson.fromJson(json, TrainModelResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertTrue(resp.getMeetsR2Threshold());
        assertTrue(resp.getMetrics().get("r2") >= 0.7,
                "R² debe ser >= 0.7 para ser aceptado");
        assertEquals(255, resp.getTrainingSamples());
        assertEquals(110, resp.getTestSamples());
        assertNotNull(resp.getModelKey());
    }

    @Test
    @DisplayName("TrainResponse de SARIMA con alta precisión (R² = 0.95)")
    void trainResponseSarimaHighPrecision() {
        String json = """
                {
                    "success": true,
                    "model_key": "sarima_20220101_20241231",
                    "model_type": "sarima",
                    "metrics": {"r2": 0.95, "mae": 320.15, "rmse": 480.20},
                    "meets_r2_threshold": true,
                    "recommendation": "Excelente - SARIMA captura bien la estacionalidad",
                    "training_samples": 730,
                    "test_samples": 365
                }
                """;

        TrainModelResponseDTO resp = gson.fromJson(json, TrainModelResponseDTO.class);

        assertEquals("sarima", resp.getModelType());
        assertEquals(0.95, resp.getMetrics().get("r2"), 0.001);
        assertTrue(resp.getMeetsR2Threshold());
    }

    @Test
    @DisplayName("TrainResponse fallido con R² < 0.7 reporta issues")
    void trainResponseFailsWithLowR2() {
        String json = """
                {
                    "success": true,
                    "model_key": "arima_test",
                    "model_type": "arima",
                    "metrics": {"r2": 0.42},
                    "meets_r2_threshold": false,
                    "recommendation": "R² bajo. Considere más datos o un modelo diferente",
                    "issues": ["R² = 0.42 está por debajo del umbral de 0.7"]
                }
                """;

        TrainModelResponseDTO resp = gson.fromJson(json, TrainModelResponseDTO.class);

        assertTrue(resp.isSuccess()); // El entrenamiento ocurrió, pero R² es bajo
        assertFalse(resp.getMeetsR2Threshold());
        assertNotNull(resp.getIssues());
        assertFalse(resp.getIssues().isEmpty());
        assertTrue(resp.getMetrics().get("r2") < 0.7);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ForecastResponseDTO - DESERIALIZACIÓN CON DATOS SINTÉTICOS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ForecastResponse con 30 predicciones diarias (1 mes)")
    void forecastResponseWith30DailyPredictions() {
        // Generar JSON sintético con 30 fechas y valores
        StringBuilder datesJson = new StringBuilder("[");
        StringBuilder valuesJson = new StringBuilder("[");
        StringBuilder lowerJson = new StringBuilder("[");
        StringBuilder upperJson = new StringBuilder("[");

        for (int i = 0; i < 30; i++) {
            if (i > 0) { datesJson.append(","); valuesJson.append(",");
                lowerJson.append(","); upperJson.append(","); }
            datesJson.append("\"2025-01-").append(String.format("%02d", i + 1)).append("\"");
            double value = 15000 + Math.sin(i * 0.2) * 2000;
            valuesJson.append(String.format("%.2f", value));
            lowerJson.append(String.format("%.2f", value * 0.9));
            upperJson.append(String.format("%.2f", value * 1.1));
        }
        datesJson.append("]"); valuesJson.append("]");
        lowerJson.append("]"); upperJson.append("]");

        String json = String.format("""
                {
                    "success": true,
                    "predictions": {
                        "dates": %s,
                        "values": %s,
                        "lower_ci": %s,
                        "upper_ci": %s
                    },
                    "model_type": "linear",
                    "periods": 30
                }
                """, datesJson, valuesJson, lowerJson, upperJson);

        ForecastResponseDTO resp = gson.fromJson(json, ForecastResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getPredictions());
        assertEquals("linear", resp.getModelType());
        assertEquals(30, resp.getPeriods());

        @SuppressWarnings("unchecked")
        List<String> dates = (List<String>) resp.getPredictions().get("dates");
        @SuppressWarnings("unchecked")
        List<Double> values = (List<Double>) resp.getPredictions().get("values");

        assertNotNull(dates);
        assertNotNull(values);
        assertEquals(30, dates.size());
        assertEquals(30, values.size());

        // Verificar primera y última fecha
        assertEquals("2025-01-01", dates.get(0));
        assertEquals("2025-01-30", dates.get(29));

        // Verificar que los valores son positivos
        values.forEach(v -> assertTrue(v > 0, "Valor de predicción debe ser positivo"));
    }

    @Test
    @DisplayName("ForecastResponse con intervalos de confianza (lower_ci < value < upper_ci)")
    void forecastResponseConfidenceIntervalsAreConsistent() {
        String json = """
                {
                    "success": true,
                    "predictions": {
                        "dates": ["2025-03-01", "2025-03-02", "2025-03-03"],
                        "values": [18000.0, 18500.0, 17800.0],
                        "lower_ci": [16200.0, 16650.0, 16020.0],
                        "upper_ci": [19800.0, 20350.0, 19580.0]
                    },
                    "model_type": "sarima",
                    "periods": 3
                }
                """;

        ForecastResponseDTO resp = gson.fromJson(json, ForecastResponseDTO.class);

        @SuppressWarnings("unchecked")
        List<Double> values = (List<Double>) resp.getPredictions().get("values");
        @SuppressWarnings("unchecked")
        List<Double> lower = (List<Double>) resp.getPredictions().get("lower_ci");
        @SuppressWarnings("unchecked")
        List<Double> upper = (List<Double>) resp.getPredictions().get("upper_ci");

        for (int i = 0; i < values.size(); i++) {
            assertTrue(lower.get(i) < values.get(i),
                    "lower_ci debe ser menor al valor predicho en posición " + i);
            assertTrue(upper.get(i) > values.get(i),
                    "upper_ci debe ser mayor al valor predicho en posición " + i);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ModelInfoDTO - VERIFICACIÓN DE MODELO ENTRENADO
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ModelInfo de un modelo lineal entrenado con datos sintéticos")
    void modelInfoForTrainedLinearModel() {
        String json = """
                {
                    "model_key": "linear_20240101_20241231",
                    "model_type": "linear",
                    "is_fitted": true,
                    "metrics": {"r2": 0.87, "mae": 850.50, "rmse": 1120.30},
                    "trained_at": "2025-01-15T10:30:00"
                }
                """;

        ModelInfoDTO info = gson.fromJson(json, ModelInfoDTO.class);

        assertEquals("linear", info.getModelType());
        assertTrue(info.isFitted());
        assertNotNull(info.getModelKey());
        assertTrue(info.getMetrics().containsKey("r2"));
        assertTrue(info.getMetrics().containsKey("mae"));
        assertTrue(info.getMetrics().containsKey("rmse"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // AutoSelectResponseDTO
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AutoSelect elige el mejor modelo basado en R²")
    void autoSelectChoosesBestModelByR2() {
        String json = """
                {
                    "success": true,
                    "best_model": {
                        "model_type": "sarima",
                        "r2": 0.95,
                        "mae": 320.15
                    },
                    "meets_r2_threshold": true,
                    "all_models": {
                        "linear": {"r2": 0.78},
                        "arima": {"r2": 0.82},
                        "sarima": {"r2": 0.95},
                        "random_forest": {"r2": 0.89}
                    },
                    "recommendation": "SARIMA es el mejor modelo - R² = 0.95"
                }
                """;

        AutoSelectResponseDTO resp = gson.fromJson(json, AutoSelectResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertTrue(resp.getMeetsR2Threshold());
        assertNotNull(resp.getBestModel());
        assertEquals("sarima", resp.getBestModel().get("model_type"));
        assertTrue(resp.getRecommendation().contains("SARIMA"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // PIPELINE COMPLETO CON DATOS SINTÉTICOS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Pipeline completo: Configurar → Entrenar → Predecir")
    void completePredictionPipeline() {
        // 1. Configurar predicción con datos sintéticos de 2 años
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 12, 31),
                List.of("Ventas", "Precio", "Categoría"),
                3
        );
        assertTrue(config.isValid(), "Configuración debe ser válida");

        // 2. Construir request de entrenamiento
        TrainModelRequestDTO trainReq = new TrainModelRequestDTO(
                "random_forest",
                config.getStartDate().toString(),
                config.getEndDate().toString()
        );
        String trainJson = gson.toJson(trainReq);
        assertTrue(trainJson.contains("random_forest"));
        assertTrue(trainJson.contains("2023-01-01"));

        // 3. Simular respuesta de entrenamiento exitoso
        TrainModelResponseDTO trainResp = gson.fromJson("""
                {
                    "success": true,
                    "model_key": "rf_20230101_20241231",
                    "model_type": "random_forest",
                    "metrics": {"r2": 0.91, "mae": 450.0, "rmse": 620.0},
                    "meets_r2_threshold": true,
                    "training_samples": 550,
                    "test_samples": 180
                }
                """, TrainModelResponseDTO.class);

        assertTrue(trainResp.isSuccess());
        assertTrue(trainResp.getMeetsR2Threshold());

        // 4. Construir request de forecast (3 meses = 90 días)
        int periods = config.getPredictionHorizon() * 30;
        ForecastRequestDTO forecastReq = new ForecastRequestDTO(
                trainResp.getModelKey(),
                trainResp.getModelType(),
                periods
        );
        String forecastJson = gson.toJson(forecastReq);
        assertTrue(forecastJson.contains("\"model_key\":\"rf_20230101_20241231\""));
        assertTrue(forecastJson.contains("\"periods\":90"));

        // 5. Simular respuesta de forecast
        ForecastResponseDTO forecastResp = gson.fromJson("""
                {
                    "success": true,
                    "predictions": {
                        "dates": ["2025-01-01", "2025-01-02", "2025-01-03"],
                        "values": [22000.0, 23500.0, 21800.0],
                        "lower_ci": [19800.0, 21150.0, 19620.0],
                        "upper_ci": [24200.0, 25850.0, 23980.0]
                    },
                    "model_type": "random_forest",
                    "periods": 90
                }
                """, ForecastResponseDTO.class);

        assertTrue(forecastResp.isSuccess());
        assertNotNull(forecastResp.getPredictions());
        assertEquals(90, forecastResp.getPeriods());
    }

    @Test
    @DisplayName("Datos de split: 70% entrenamiento, 30% validación están fijos")
    void dataSplitIsFixed70_30() {
        Phase2ConfigDTO config = buildConfig(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                List.of("Ventas"),
                1
        );

        assertEquals(70, config.getTrainPercentage());
        assertEquals(30, config.getValidationPercentage());
        assertEquals(100, config.getTrainPercentage() + config.getValidationPercentage());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private Phase2ConfigDTO buildConfig(LocalDate start, LocalDate end,
                                         List<String> variables, int horizon) {
        Phase2ConfigDTO config = new Phase2ConfigDTO();
        config.setStartDate(start);
        config.setEndDate(end);
        config.setSelectedVariables(variables);
        config.setPredictionHorizon(horizon);
        return config;
    }
}
