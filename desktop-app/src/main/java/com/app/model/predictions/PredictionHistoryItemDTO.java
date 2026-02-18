package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;

/**
 * DTO para item del historial de predicciones.
 * Mapea la respuesta de GET /predictions/history
 */
public class PredictionHistoryItemDTO {

    private int id;
    private String fecha;

    @SerializedName("valor_predicho")
    private Double valorPredicho;

    @SerializedName("intervalo_inferior")
    private Double intervaloInferior;

    @SerializedName("intervalo_superior")
    private Double intervaloSuperior;

    private Double confianza;

    public int getId() { return id; }
    public String getFecha() { return fecha; }
    public Double getValorPredicho() { return valorPredicho; }
    public Double getIntervaloInferior() { return intervaloInferior; }
    public Double getIntervaloSuperior() { return intervaloSuperior; }
    public Double getConfianza() { return confianza; }
}
