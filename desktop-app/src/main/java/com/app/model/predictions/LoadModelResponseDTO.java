package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * DTO para response de carga de modelo desde disco.
 * Mapea la respuesta de POST /predictions/models/load
 */
public class LoadModelResponseDTO {

    private boolean success;

    @SerializedName("model_key")
    private String modelKey;

    @SerializedName("model_type")
    private String modelType;

    @SerializedName("is_fitted")
    private Boolean isFitted;

    private Map<String, Double> metrics;

    @SerializedName("trained_at")
    private String trainedAt;

    private String path;
    private String error;

    public boolean isSuccess() { return success; }
    public String getModelKey() { return modelKey; }
    public String getModelType() { return modelType; }
    public Boolean getIsFitted() { return isFitted; }
    public Map<String, Double> getMetrics() { return metrics; }
    public String getTrainedAt() { return trainedAt; }
    public String getPath() { return path; }
    public String getError() { return error; }
}
