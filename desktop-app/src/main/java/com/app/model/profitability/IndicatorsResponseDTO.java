package com.app.model.profitability;

/** Respuesta de POST /profitability/indicators */
public class IndicatorsResponseDTO {
    private boolean success;
    private IndicatorsData indicators;
    private SummaryData summary;
    private String error;

    public boolean isSuccess()           { return success; }
    public IndicatorsData getIndicators(){ return indicators; }
    public SummaryData getSummary()      { return summary; }
    public String getError()             { return error; }

    public static class IndicatorsData {
        private double ingresos_totales;
        private double costos_totales;
        private double utilidad_bruta;
        private double margen_bruto;
        private double utilidad_operativa;
        private double margen_operativo;
        private double utilidad_neta;
        private double margen_neto;
        private double roa;
        private double roe;
        private double rotacion_inventario;

        public double getIngresosTotales()    { return ingresos_totales; }
        public double getCostosTotales()      { return costos_totales; }
        public double getUtilidadBruta()      { return utilidad_bruta; }
        public double getMargenBruto()        { return margen_bruto; }
        public double getUtilidadOperativa()  { return utilidad_operativa; }
        public double getMargenOperativo()    { return margen_operativo; }
        public double getUtilidadNeta()       { return utilidad_neta; }
        public double getMargenNeto()         { return margen_neto; }
        public double getRoa()                { return roa; }
        public double getRoe()                { return roe; }
        public double getRotacionInventario() { return rotacion_inventario; }
    }

    public static class SummaryData {
        private String periodo;
        private int total_ventas;
        private int total_compras;
        private boolean rentable;

        public String getPeriodo()      { return periodo; }
        public int getTotalVentas()     { return total_ventas; }
        public int getTotalCompras()    { return total_compras; }
        public boolean isRentable()     { return rentable; }
    }
}
