package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;

/**
 * DTO para request de entrenamiento de modelo.
 * Mapea a POST /predictions/train
 */
public class TrainModelRequestDTO {

    @SerializedName("model_type")
    private String modelType;

    @SerializedName("fecha_inicio")
    private String fechaInicio;

    @SerializedName("fecha_fin")
    private String fechaFin;

    @SerializedName("hyperparameters")
    private Object hyperparameters;

    public TrainModelRequestDTO(String modelType, String fechaInicio, String fechaFin) {
        this.modelType = modelType;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
    }

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public String getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(String fechaInicio) { this.fechaInicio = fechaInicio; }

    public String getFechaFin() { return fechaFin; }
    public void setFechaFin(String fechaFin) { this.fechaFin = fechaFin; }

    public Object getHyperparameters() { return hyperparameters; }
    public void setHyperparameters(Object hyperparameters) { this.hyperparameters = hyperparameters; }
}
