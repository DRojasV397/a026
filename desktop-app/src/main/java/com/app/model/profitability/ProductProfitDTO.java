package com.app.model.profitability;

/** Rentabilidad de un producto — mapea a profitability_service.ProductProfitability.to_dict() */
public class ProductProfitDTO {
    private int id_producto;
    private String nombre;
    private String categoria;
    private int unidades_vendidas;
    private double ingresos;
    private double costo_total;
    private double utilidad;
    private double margen;
    private double precio_promedio_venta;
    private double costo_promedio;
    private boolean es_rentable;
    private int ranking;

    public int getIdProducto()           { return id_producto; }
    public String getNombre()            { return nombre != null ? nombre : ""; }
    public String getCategoria()         { return categoria != null ? categoria : ""; }
    public int getUnidadesVendidas()     { return unidades_vendidas; }
    public double getIngresos()          { return ingresos; }
    public double getCostoTotal()        { return costo_total; }
    public double getUtilidad()          { return utilidad; }
    public double getMargen()            { return margen; }
    public double getPrecioPromedioVenta() { return precio_promedio_venta; }
    public double getCostoPromedio()     { return costo_promedio; }
    public boolean isEsRentable()        { return es_rentable; }
    public int getRanking()              { return ranking; }

    /** Convierte margen a String formateado para UI: "25.3%" */
    public String getMargenFormateado() {
        return String.format("%.1f%%", margen);
    }

    /** Convierte ingresos a String formateado: "$1,234.56" */
    public String getIngresosFormateado() {
        return String.format("$%,.2f", ingresos);
    }

    /** Convierte utilidad a String formateado (con signo negativo si aplica) */
    public String getUtilidadFormateada() {
        if (utilidad < 0) return String.format("-$%,.2f", Math.abs(utilidad));
        return String.format("$%,.2f", utilidad);
    }

    /** Estado de rentabilidad para pill de UI */
    public String getEstado() {
        if (margen >= 20) return "Excelente";
        if (margen >= 10) return "Bueno";
        if (margen >= 0)  return "Regular";
        return "Crítico";
    }
}
