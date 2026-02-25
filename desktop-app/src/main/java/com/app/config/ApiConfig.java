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

    public static String getPredictionsLoadModelUrl() {
        return BASE_URL + "/predictions/models/load";
    }

    public static String getPredictionsSavedModelsUrl() {
        return BASE_URL + "/predictions/models/saved";
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

    public static String getDataHistorialUrl() {
        return BASE_URL + "/data/historial";
    }

    // ── Products Catalog ────────────────────────────────────────────────────

    public static String getProductosUrl() {
        return BASE_URL + "/productos/";
    }

    // ── Profitability Module ─────────────────────────────────────────────────

    public static String getProfitabilityIndicatorsUrl() {
        return BASE_URL + "/profitability/indicators";
    }

    public static String getProfitabilityProductsUrl() {
        return BASE_URL + "/profitability/products";
    }

    public static String getProfitabilityNonProfitableUrl() {
        return BASE_URL + "/profitability/products/non-profitable";
    }

    public static String getProfitabilityCategoriesUrl() {
        return BASE_URL + "/profitability/categories";
    }

    public static String getProfitabilityTrendsUrl() {
        return BASE_URL + "/profitability/trends";
    }

    public static String getProfitabilityRankingUrl() {
        return BASE_URL + "/profitability/ranking";
    }

    public static String getProfitabilitySummaryUrl() {
        return BASE_URL + "/profitability/summary";
    }

    // ── Alerts Module ────────────────────────────────────────────────────────

    public static String getAlertsUrl() {
        return BASE_URL + "/alerts";
    }

    public static String getAlertHistoryUrl() {
        return BASE_URL + "/alerts/history";
    }

    public static String getAlertsSummaryUrl() {
        return BASE_URL + "/alerts/summary";
    }

    public static String getAlertReadUrl(int idAlerta) {
        return BASE_URL + "/alerts/" + idAlerta + "/read";
    }

    public static String getAlertStatusUrl(int idAlerta) {
        return BASE_URL + "/alerts/" + idAlerta + "/status";
    }

    public static String getAlertsConfigUrl() {
        return BASE_URL + "/alerts/config";
    }

    public static String getAlertsAnalyzeUrl() {
        return BASE_URL + "/alerts/analyze";
    }

    // ── Dashboard Module ─────────────────────────────────────────────────────

    public static String getDashboardExecutiveUrl() {
        return BASE_URL + "/dashboard/executive";
    }

    public static String getDashboardKpiUrl(String kpiName) {
        return BASE_URL + "/dashboard/kpi/" + kpiName;
    }

    public static String getDashboardPredictionsUrl() {
        return BASE_URL + "/dashboard/predictions";
    }

    // ── Reports Module (via dashboard) ───────────────────────────────────────

    public static String getReportTypesUrl() {
        return BASE_URL + "/dashboard/reports/types";
    }

    public static String getGenerateReportUrl() {
        return BASE_URL + "/dashboard/reports/generate";
    }

    public static String getReportsListUrl() {
        return BASE_URL + "/dashboard/reports";
    }

    public static String getReportByIdUrl(int idReporte) {
        return BASE_URL + "/dashboard/reports/" + idReporte;
    }
}
