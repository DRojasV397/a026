package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * DTO para response de entrenamiento de modelo.
 * Mapea la respuesta de POST /predictions/train
 */
public class TrainModelResponseDTO {

    private boolean success;

    @SerializedName("model_id")
    private Integer modelId;

    @SerializedName("model_key")
    private String modelKey;

    @SerializedName("model_type")
    private String modelType;

    private Map<String, Object> metrics;

    @SerializedName("meets_r2_threshold")
    private Boolean meetsR2Threshold;

    private String recommendation;

    @SerializedName("training_samples")
    private Integer trainingSamples;

    @SerializedName("test_samples")
    private Integer testSamples;

    private String error;
    private List<String> issues;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Integer getModelId() { return modelId; }
    public String getModelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }

    public Boolean getMeetsR2Threshold() { return meetsR2Threshold; }
    public void setMeetsR2Threshold(Boolean meetsR2Threshold) { this.meetsR2Threshold = meetsR2Threshold; }

    public String getRecommendation() { return recommendation; }
    public Integer getTrainingSamples() { return trainingSamples; }
    public Integer getTestSamples() { return testSamples; }
    public String getError() { return error; }
    public List<String> getIssues() { return issues; }
}
