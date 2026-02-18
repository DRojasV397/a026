package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;

/**
 * DTO para request de seleccion automatica de modelo.
 * Mapea a POST /predictions/auto-select
 */
public class AutoSelectRequestDTO {

    @SerializedName("fecha_inicio")
    private String fechaInicio;

    @SerializedName("fecha_fin")
    private String fechaFin;

    public AutoSelectRequestDTO() {}

    public AutoSelectRequestDTO(String fechaInicio, String fechaFin) {
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
    }

    public String getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(String fechaInicio) { this.fechaInicio = fechaInicio; }

    public String getFechaFin() { return fechaFin; }
    public void setFechaFin(String fechaFin) { this.fechaFin = fechaFin; }
}
