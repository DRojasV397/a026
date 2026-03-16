package com.app.model.simulation;

import com.google.gson.annotations.SerializedName;

/**
 * DTO para un ítem de la lista de escenarios.
 * Mapea la respuesta de GET /simulation/scenarios → "escenarios"[]
 */
public class SimulationScenarioSummaryDTO {

    @SerializedName("id_escenario")
    private int idEscenario;

    private String nombre;
    private String descripcion;

    @SerializedName("horizonte_meses")
    private int horizonteMeses;

    @SerializedName("fecha_creacion")
    private String fechaCreacion;

    @SerializedName("num_parametros")
    private int numParametros;

    @SerializedName("num_resultados")
    private int numResultados;

    @SerializedName("total_ingresos_simulados")
    private double totalIngresosSimulados;

    @SerializedName("total_utilidad_simulada")
    private double totalUtilidadSimulada;

    public int    getIdEscenario()            { return idEscenario; }
    public String getNombre()                 { return nombre; }
    public String getDescripcion()            { return descripcion; }
    public int    getHorizonteMeses()         { return horizonteMeses > 0 ? horizonteMeses : 6; }
    public String getFechaCreacion()          { return fechaCreacion; }
    public int    getNumParametros()          { return numParametros; }
    public int    getNumResultados()          { return numResultados; }
    public double getTotalIngresosSimulados() { return totalIngresosSimulados; }
    public double getTotalUtilidadSimulada()  { return totalUtilidadSimulada; }
}
