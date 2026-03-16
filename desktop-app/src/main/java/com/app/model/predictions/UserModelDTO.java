package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * DTO para modelos predictivos del usuario.
 * Mapea a GET /predictions/models/user
 */
public class UserModelDTO {

    @SerializedName("model_id")
    private int modelId;

    @SerializedName("version_id")
    private int versionId;

    @SerializedName("model_key")
    private String modelKey;

    @SerializedName("model_type")
    private String modelType;

    @SerializedName("nombre")
    private String nombre;

    @SerializedName("precision")
    private double precision;

    @SerializedName("metricas")
    private Map<String, Object> metricas;

    @SerializedName("estado")
    private String estado;

    @SerializedName("fecha_entrenamiento")
    private String fechaEntrenamiento;

    @SerializedName("is_loaded")
    private boolean isLoaded;

    public int getModelId() { return modelId; }
    public void setModelId(int modelId) { this.modelId = modelId; }

    public int getVersionId() { return versionId; }
    public void setVersionId(int versionId) { this.versionId = versionId; }

    public String getModelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public double getPrecision() { return precision; }
    public void setPrecision(double precision) { this.precision = precision; }

    public Map<String, Object> getMetricas() { return metricas; }
    public void setMetricas(Map<String, Object> metricas) { this.metricas = metricas; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getFechaEntrenamiento() { return fechaEntrenamiento; }
    public void setFechaEntrenamiento(String fechaEntrenamiento) { this.fechaEntrenamiento = fechaEntrenamiento; }

    public boolean isLoaded() { return isLoaded; }
    public void setLoaded(boolean loaded) { isLoaded = loaded; }
}
