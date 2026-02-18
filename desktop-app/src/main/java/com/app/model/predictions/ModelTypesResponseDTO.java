package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * DTO para response de tipos de modelo disponibles.
 * Mapea la respuesta de GET /predictions/model-types
 */
public class ModelTypesResponseDTO {

    @SerializedName("model_types")
    private List<ModelTypeInfo> modelTypes;

    @SerializedName("r2_threshold")
    private double r2Threshold;

    @SerializedName("max_forecast_days")
    private int maxForecastDays;

    public List<ModelTypeInfo> getModelTypes() { return modelTypes; }
    public double getR2Threshold() { return r2Threshold; }
    public int getMaxForecastDays() { return maxForecastDays; }

    /**
     * Informacion de un tipo de modelo disponible.
     */
    public static class ModelTypeInfo {
        private String id;
        private String name;
        private String description;

        @SerializedName("use_case")
        private String useCase;

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getUseCase() { return useCase; }
    }
}
