package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * DTO para informacion de modelo entrenado.
 * Mapea la respuesta de GET /predictions/models
 */
public class ModelInfoDTO {

    @SerializedName("model_key")
    private String modelKey;

    @SerializedName("model_type")
    private String modelType;

    @SerializedName("is_fitted")
    private boolean isFitted;

    private Map<String, Double> metrics;

    @SerializedName("trained_at")
    private String trainedAt;

    public String getModelKey() { return modelKey; }
    public String getModelType() { return modelType; }
    public boolean isFitted() { return isFitted; }
    public Map<String, Double> getMetrics() { return metrics; }
    public String getTrainedAt() { return trainedAt; }
}
