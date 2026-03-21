package com.app.model.profitability;

/** Indicadores generales proyectados — mapea a projection["general"]. */
public class ProjectionGeneralDTO {
    private double ingresos_proyectados;
    private double costos_proyectados;
    private double utilidad_proyectada;
    private double margen_bruto;
    private double margen_operativo;
    private double margen_neto;
    private double variacion_ingresos;
    private double variacion_utilidad;

    public double getIngresosProyectados()  { return ingresos_proyectados; }
    public double getCostosProyectados()    { return costos_proyectados; }
    public double getUtilidadProyectada()   { return utilidad_proyectada; }
    public double getMargenBruto()          { return margen_bruto; }
    public double getMargenOperativo()      { return margen_operativo; }
    public double getMargenNeto()           { return margen_neto; }
    public double getVariacionIngresos()    { return variacion_ingresos; }
    public double getVariacionUtilidad()    { return variacion_utilidad; }
}
