package com.app.model.profitability;

/** Rentabilidad de una categoría — mapea a profitability_service.CategoryProfitability.to_dict() */
public class CategoryProfitDTO {
    private int id_categoria;
    private String nombre;
    private int num_productos;
    private int unidades_vendidas;
    private double ingresos;
    private double costo_total;
    private double utilidad;
    private double margen;
    private int productos_rentables;
    private int productos_no_rentables;

    public int getIdCategoria()          { return id_categoria; }
    public String getNombre()            { return nombre != null ? nombre : ""; }
    public int getNumProductos()         { return num_productos; }
    public int getUnidadesVendidas()     { return unidades_vendidas; }
    public double getIngresos()          { return ingresos; }
    public double getCostoTotal()        { return costo_total; }
    public double getUtilidad()          { return utilidad; }
    public double getMargen()            { return margen; }
    public int getProductosRentables()   { return productos_rentables; }
    public int getProductosNoRentables() { return productos_no_rentables; }

    public String getMargenFormateado() {
        return String.format("%.1f%%", margen);
    }

    public String getIngresosFormateado() {
        return String.format("$%,.2f", ingresos);
    }

    public String getUtilidadFormateada() {
        if (utilidad < 0) return String.format("-$%,.2f", Math.abs(utilidad));
        return String.format("$%,.2f", utilidad);
    }

    public String getEstado() {
        if (margen >= 20) return "Excelente";
        if (margen >= 10) return "Bueno";
        if (margen >= 0)  return "Regular";
        return "Crítico";
    }
}
