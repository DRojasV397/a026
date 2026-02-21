package com.app.model.profitability;

import java.util.List;

/** Respuesta de GET /profitability/products */
public class ProductsResponseDTO {
    private boolean success;
    private List<ProductProfitDTO> productos;
    private ResumenDTO resumen;

    public boolean isSuccess()               { return success; }
    public List<ProductProfitDTO> getProductos() { return productos != null ? productos : List.of(); }
    public ResumenDTO getResumen()           { return resumen; }

    public static class ResumenDTO {
        private int total_productos;
        private int productos_rentables;
        private int productos_no_rentables;
        private double porcentaje_rentables;
        private double umbral_rentabilidad;

        public int getTotalProductos()        { return total_productos; }
        public int getProductosRentables()    { return productos_rentables; }
        public int getProductosNoRentables()  { return productos_no_rentables; }
        public double getPorcentajeRentables(){ return porcentaje_rentables; }
        public double getUmbralRentabilidad() { return umbral_rentabilidad; }
    }
}
