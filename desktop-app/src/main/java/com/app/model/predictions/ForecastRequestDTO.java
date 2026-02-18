package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;

/**
 * DTO para request de forecast.
 * Mapea a POST /predictions/forecast
 */
public class ForecastRequestDTO {

    @SerializedName("model_key")
    private String modelKey;

    @SerializedName("model_type")
    private String modelType;

    private int periods;

    public ForecastRequestDTO(String modelKey, String modelType, int periods) {
        this.modelKey = modelKey;
        this.modelType = modelType;
        this.periods = periods;
    }

    public String getModelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public int getPeriods() { return periods; }
    public void setPeriods(int periods) { this.periods = periods; }
}
