package com.app.model.profitability;

import java.text.DecimalFormat;

/**
 * Ítem de proyección — sirve tanto para productos como para categorías.
 * Mapea a los objetos dentro de projection["por_producto"] y projection["por_categoria"].
 */
public class ProjectionItemDTO {
    // Presente en productos; vacío en categorías
    private int id_producto;
    private String categoria;

    // Común
    private String nombre;
    private double ingresos_proyectados;
    private double costos_proyectados;
    private double utilidad_proyectada;
    private double margen_proyectado;

    private static final DecimalFormat df  = new DecimalFormat("#,##0.00");

    public int    getIdProducto()          { return id_producto; }
    public String getNombre()              { return nombre   != null ? nombre   : ""; }
    public String getCategoria()           { return categoria != null ? categoria : ""; }
    public double getIngresosProyectados() { return ingresos_proyectados; }
    public double getCostosProyectados()   { return costos_proyectados; }
    public double getUtilidadProyectada()  { return utilidad_proyectada; }
    public double getMargenProyectado()    { return margen_proyectado; }

    public String getIngresosFormateado() {
        return String.format("$%,.2f", ingresos_proyectados);
    }
    public String getCostosFormateado() {
        return String.format("$%,.2f", costos_proyectados);
    }
    public String getUtilidadFormateada() {
        if (utilidad_proyectada < 0) return String.format("-$%,.2f", Math.abs(utilidad_proyectada));
        return String.format("$%,.2f", utilidad_proyectada);
    }
    public String getMargenFormateado() {
        return String.format("%.1f%%", margen_proyectado);
    }
    public String getEstado() {
        if (margen_proyectado >= 20) return "Excelente";
        if (margen_proyectado >= 10) return "Bueno";
        if (margen_proyectado >= 0)  return "Regular";
        return "Crítico";
    }
}
