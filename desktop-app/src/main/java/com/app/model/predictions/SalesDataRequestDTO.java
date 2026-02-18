package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;

/**
 * DTO para request de datos de ventas.
 * Mapea a POST /predictions/sales-data y POST /predictions/validate-data
 */
public class SalesDataRequestDTO {

    @SerializedName("fecha_inicio")
    private String fechaInicio;

    @SerializedName("fecha_fin")
    private String fechaFin;

    private String aggregation;

    public SalesDataRequestDTO() {
        this.aggregation = "D";
    }

    public SalesDataRequestDTO(String fechaInicio, String fechaFin, String aggregation) {
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.aggregation = aggregation != null ? aggregation : "D";
    }

    public String getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(String fechaInicio) { this.fechaInicio = fechaInicio; }

    public String getFechaFin() { return fechaFin; }
    public void setFechaFin(String fechaFin) { this.fechaFin = fechaFin; }

    public String getAggregation() { return aggregation; }
    public void setAggregation(String aggregation) { this.aggregation = aggregation; }
}
