package com.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para ApiConfig - verifica que todas las URLs de los módulos integrados
 * estén correctamente formadas.
 */
class ApiConfigTest {

    private static final String BASE = "http://localhost:8000/api/v1";

    // ── Auth URLs ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login URL debe apuntar a /auth/login/json")
    void loginUrl() {
        assertEquals(BASE + "/auth/login/json", ApiConfig.getLoginUrl());
    }

    @Test
    @DisplayName("Verify URL debe apuntar a /auth/verify")
    void verifyUrl() {
        assertEquals(BASE + "/auth/verify", ApiConfig.getVerifyUrl());
    }

    @Test
    @DisplayName("Refresh URL debe apuntar a /auth/refresh")
    void refreshUrl() {
        assertEquals(BASE + "/auth/refresh", ApiConfig.getRefreshUrl());
    }

    @Test
    @DisplayName("Me URL debe apuntar a /auth/me")
    void meUrl() {
        assertEquals(BASE + "/auth/me", ApiConfig.getMeUrl());
    }

    // ── Profile URLs ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Change Password URL debe apuntar a /auth/password")
    void changePasswordUrl() {
        assertEquals(BASE + "/auth/password", ApiConfig.getChangePasswordUrl());
    }

    @Test
    @DisplayName("Register URL debe apuntar a /auth/register")
    void registerUrl() {
        assertEquals(BASE + "/auth/register", ApiConfig.getRegisterUrl());
    }

    @Test
    @DisplayName("User URL debe incluir el ID del usuario")
    void userUrl() {
        assertEquals(BASE + "/usuarios/42", ApiConfig.getUserUrl(42));
        assertEquals(BASE + "/usuarios/1", ApiConfig.getUserUrl(1));
    }

    @Test
    @DisplayName("Users URL debe apuntar a /usuarios")
    void usersUrl() {
        assertEquals(BASE + "/usuarios", ApiConfig.getUsersUrl());
    }

    // ── Predictions URLs ─────────────────────────────────────────────────

    @Test
    @DisplayName("Predictions Train URL correcta")
    void predictionsTrainUrl() {
        assertEquals(BASE + "/predictions/train", ApiConfig.getPredictionsTrainUrl());
    }

    @Test
    @DisplayName("Predictions Forecast URL correcta")
    void predictionsForecastUrl() {
        assertEquals(BASE + "/predictions/forecast", ApiConfig.getPredictionsForecastUrl());
    }

    @Test
    @DisplayName("Predictions AutoSelect URL correcta")
    void predictionsAutoSelectUrl() {
        assertEquals(BASE + "/predictions/auto-select", ApiConfig.getPredictionsAutoSelectUrl());
    }

    @Test
    @DisplayName("Predictions Models URL correcta")
    void predictionsModelsUrl() {
        assertEquals(BASE + "/predictions/models", ApiConfig.getPredictionsModelsUrl());
    }

    @Test
    @DisplayName("Predictions Delete Model URL incluye model key")
    void predictionsDeleteModelUrl() {
        assertEquals(BASE + "/predictions/models/linear_2026",
                ApiConfig.getPredictionsDeleteModelUrl("linear_2026"));
    }

    @Test
    @DisplayName("Predictions History URL correcta")
    void predictionsHistoryUrl() {
        assertEquals(BASE + "/predictions/history", ApiConfig.getPredictionsHistoryUrl());
    }

    @Test
    @DisplayName("Predictions Validate Data URL correcta")
    void predictionsValidateDataUrl() {
        assertEquals(BASE + "/predictions/validate-data", ApiConfig.getPredictionsValidateDataUrl());
    }

    @Test
    @DisplayName("Predictions Model Types URL correcta")
    void predictionsModelTypesUrl() {
        assertEquals(BASE + "/predictions/model-types", ApiConfig.getPredictionsModelTypesUrl());
    }

    @Test
    @DisplayName("Predictions Load All Models URL correcta")
    void predictionsLoadAllModelsUrl() {
        assertEquals(BASE + "/predictions/models/load-all", ApiConfig.getPredictionsLoadAllModelsUrl());
    }

    @Test
    @DisplayName("Predictions Sales Data URL correcta")
    void predictionsSalesDataUrl() {
        assertEquals(BASE + "/predictions/sales-data", ApiConfig.getPredictionsSalesDataUrl());
    }

    // ── Data Management URLs ─────────────────────────────────────────────

    @Test
    @DisplayName("Data Upload URL correcta")
    void dataUploadUrl() {
        assertEquals(BASE + "/data/upload", ApiConfig.getDataUploadUrl());
    }

    @Test
    @DisplayName("Data Validate URL correcta")
    void dataValidateUrl() {
        assertEquals(BASE + "/data/validate", ApiConfig.getDataValidateUrl());
    }

    @Test
    @DisplayName("Data Preview URL incluye upload ID")
    void dataPreviewUrl() {
        assertEquals(BASE + "/data/preview/abc-123", ApiConfig.getDataPreviewUrl("abc-123"));
    }

    @Test
    @DisplayName("Data Clean URL correcta")
    void dataCleanUrl() {
        assertEquals(BASE + "/data/clean", ApiConfig.getDataCleanUrl());
    }

    @Test
    @DisplayName("Data Confirm URL correcta")
    void dataConfirmUrl() {
        assertEquals(BASE + "/data/confirm", ApiConfig.getDataConfirmUrl());
    }

    @Test
    @DisplayName("Data Quality Report URL incluye upload ID")
    void dataQualityReportUrl() {
        assertEquals(BASE + "/data/quality-report/xyz-456",
                ApiConfig.getDataQualityReportUrl("xyz-456"));
    }

    @Test
    @DisplayName("Data Delete URL incluye upload ID")
    void dataDeleteUrl() {
        assertEquals(BASE + "/data/delete-789", ApiConfig.getDataDeleteUrl("delete-789"));
    }

    @Test
    @DisplayName("Data Sheets URL incluye upload ID")
    void dataSheetsUrl() {
        assertEquals(BASE + "/data/sheets/sheet-001",
                ApiConfig.getDataSheetsUrl("sheet-001"));
    }

    // ── Base URL ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Base URL es http://localhost:8000/api/v1")
    void baseUrl() {
        assertEquals(BASE, ApiConfig.getBaseUrl());
    }
}
