package com.app.model.profitability;

import java.util.List;

/** Respuesta de GET /profitability/categories */
public class CategoriesResponseDTO {
    private boolean success;
    private List<CategoryProfitDTO> categorias;
    private ResumenDTO resumen;

    public boolean isSuccess()                  { return success; }
    public List<CategoryProfitDTO> getCategorias() { return categorias != null ? categorias : List.of(); }
    public ResumenDTO getResumen()              { return resumen; }

    public static class ResumenDTO {
        private int total_categorias;
        private double ingresos_totales;
        private double utilidad_total;

        public int getTotalCategorias()     { return total_categorias; }
        public double getIngresosTotales()  { return ingresos_totales; }
        public double getUtilidadTotal()    { return utilidad_total; }
    }
}
