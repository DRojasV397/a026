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
}
