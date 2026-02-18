package com.app.config;

public class ApiConfig {

    private static final String BASE_URL = "http://localhost:8000/api/v1";

    private ApiConfig() {}

    public static String getBaseUrl() {
        return BASE_URL;
    }

    public static String getLoginUrl() {
        return BASE_URL + "/auth/login/json";
    }

    public static String getVerifyUrl() {
        return BASE_URL + "/auth/verify";
    }

    public static String getRefreshUrl() {
        return BASE_URL + "/auth/refresh";
    }

    public static String getMeUrl() {
        return BASE_URL + "/auth/me";
    }

    // ── User Profile Module ─────────────────────────────────────────────────

    public static String getChangePasswordUrl() {
        return BASE_URL + "/auth/password";
    }

    public static String getRegisterUrl() {
        return BASE_URL + "/auth/register";
    }

    public static String getUserUrl(int userId) {
        return BASE_URL + "/usuarios/" + userId;
    }

    public static String getUsersUrl() {
        return BASE_URL + "/usuarios";
    }

    // ── Predictions Module ──────────────────────────────────────────────────

    public static String getPredictionsTrainUrl() {
        return BASE_URL + "/predictions/train";
    }

    public static String getPredictionsForecastUrl() {
        return BASE_URL + "/predictions/forecast";
    }

    public static String getPredictionsAutoSelectUrl() {
        return BASE_URL + "/predictions/auto-select";
    }

    public static String getPredictionsModelsUrl() {
        return BASE_URL + "/predictions/models";
    }

    public static String getPredictionsDeleteModelUrl(String modelKey) {
        return BASE_URL + "/predictions/models/" + modelKey;
    }

    public static String getPredictionsHistoryUrl() {
        return BASE_URL + "/predictions/history";
    }

    public static String getPredictionsValidateDataUrl() {
        return BASE_URL + "/predictions/validate-data";
    }

    public static String getPredictionsModelTypesUrl() {
        return BASE_URL + "/predictions/model-types";
    }

    public static String getPredictionsLoadAllModelsUrl() {
        return BASE_URL + "/predictions/models/load-all";
    }

    public static String getPredictionsSalesDataUrl() {
        return BASE_URL + "/predictions/sales-data";
    }

    // ── Data Management Module ──────────────────────────────────────────────

    public static String getDataUploadUrl() {
        return BASE_URL + "/data/upload";
    }

    public static String getDataValidateUrl() {
        return BASE_URL + "/data/validate";
    }

    public static String getDataPreviewUrl(String uploadId) {
        return BASE_URL + "/data/preview/" + uploadId;
    }

    public static String getDataCleanUrl() {
        return BASE_URL + "/data/clean";
    }

    public static String getDataConfirmUrl() {
        return BASE_URL + "/data/confirm";
    }

    public static String getDataQualityReportUrl(String uploadId) {
        return BASE_URL + "/data/quality-report/" + uploadId;
    }

    public static String getDataDeleteUrl(String uploadId) {
        return BASE_URL + "/data/" + uploadId;
    }

    public static String getDataSheetsUrl(String uploadId) {
        return BASE_URL + "/data/sheets/" + uploadId;
    }
}
