package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * DTO para response de forecast.
 * Mapea la respuesta de POST /predictions/forecast
 */
public class ForecastResponseDTO {

    private boolean success;

    /**
     * Estructura de predictions del backend:
     * { "dates": [...], "values": [...], "lower_ci": [...], "upper_ci": [...] }
     */
    private Map<String, Object> predictions;

    @SerializedName("model_type")
    private String modelType;

    @SerializedName("model_metrics")
    private Map<String, Double> modelMetrics;

    private Integer periods;
    private String error;
    private String suggestion;

    public boolean isSuccess() { return success; }
    public Map<String, Object> getPredictions() { return predictions; }
    public String getModelType() { return modelType; }
    public Map<String, Double> getModelMetrics() { return modelMetrics; }
    public Integer getPeriods() { return periods; }
    public String getError() { return error; }
    public String getSuggestion() { return suggestion; }
}
