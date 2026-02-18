package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * DTO para response de auto-seleccion de modelo.
 * Mapea la respuesta de POST /predictions/auto-select
 */
public class AutoSelectResponseDTO {

    private boolean success;

    @SerializedName("best_model")
    private Map<String, Object> bestModel;

    @SerializedName("meets_r2_threshold")
    private Boolean meetsR2Threshold;

    @SerializedName("all_models")
    private Map<String, Object> allModels;

    private String recommendation;
    private String error;
    private List<String> issues;

    public boolean isSuccess() { return success; }
    public Map<String, Object> getBestModel() { return bestModel; }
    public Boolean getMeetsR2Threshold() { return meetsR2Threshold; }
    public Map<String, Object> getAllModels() { return allModels; }
    public String getRecommendation() { return recommendation; }
    public String getError() { return error; }
    public List<String> getIssues() { return issues; }
}
