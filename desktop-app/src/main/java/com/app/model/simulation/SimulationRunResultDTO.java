package com.app.model.simulation;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * DTO para la respuesta de POST /simulation/{id}/run
 */
public class SimulationRunResultDTO {

    private boolean success;

    @SerializedName("id_escenario")
    private int idEscenario;

    private String nombre;
    private List<PeriodResult> resultados;
    private Resumen resumen;

    @SerializedName("fecha_ejecucion")
    private String fechaEjecucion;

    private String error;

    // ── Nested: resultado por período y KPI ──────────────────────────────────

    public static class PeriodResult {
        private String periodo;
        private String kpi;

        @SerializedName("valor_base")
        private double valorBase;

        @SerializedName("valor_simulado")
        private double valorSimulado;

        private double diferencia;

        @SerializedName("porcentaje_cambio")
        private double porcentajeCambio;

        public String getPeriodo()         { return periodo; }
        public String getKpi()             { return kpi; }
        public double getValorBase()       { return valorBase; }
        public double getValorSimulado()   { return valorSimulado; }
        public double getDiferencia()      { return diferencia; }
        public double getPorcentajeCambio(){ return porcentajeCambio; }
    }

    // ── Nested: resumen total ────────────────────────────────────────────────

    public static class Resumen {
        @SerializedName("total_ingresos_simulados")
        private double totalIngresosSimulados;

        @SerializedName("total_costos_simulados")
        private double totalCostosSimulados;

        @SerializedName("total_utilidad_simulada")
        private double totalUtilidadSimulada;

        @SerializedName("margen_promedio")
        private double margenPromedio;

        @SerializedName("variaciones_aplicadas")
        private Map<String, String> variacionesAplicadas;

        public double getTotalIngresosSimulados() { return totalIngresosSimulados; }
        public double getTotalCostosSimulados()   { return totalCostosSimulados; }
        public double getTotalUtilidadSimulada()  { return totalUtilidadSimulada; }
        public double getMargenPromedio()          { return margenPromedio; }
        public Map<String, String> getVariacionesAplicadas() { return variacionesAplicadas; }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public boolean isSuccess()              { return success; }
    public void setSuccess(boolean v)       { this.success = v; }
    public int getIdEscenario()             { return idEscenario; }
    public String getNombre()               { return nombre; }
    public List<PeriodResult> getResultados(){ return resultados; }
    public Resumen getResumen()             { return resumen; }
    public String getFechaEjecucion()       { return fechaEjecucion; }
    public String getError()               { return error; }
    public void setError(String e)         { this.error = e; }
}
