package com.app.model.dashboard;

import java.util.List;
import java.util.Map;

/**
 * DTO que mapea la respuesta de GET /dashboard/executive.
 *
 * Estructura:
 * {
 *   success, periodo, resumen_ventas, resumen_compras,
 *   kpis_financieros, alertas_activas, tendencias, top_productos
 * }
 */
public class ExecutiveDashboardDTO {

    private boolean success;
    private Periodo periodo;
    private ResumenVentas resumen_ventas;
    private ResumenCompras resumen_compras;
    private KpisFinancieros kpis_financieros;
    private AlertasActivas alertas_activas;
    private Tendencias tendencias;
    private TopProductos top_productos;
    private String fecha_generacion;

    // ── Getters ──────────────────────────────────────────────────────────────

    public boolean isSuccess()                  { return success; }
    public Periodo getPeriodo()                 { return periodo; }
    public ResumenVentas getResumenVentas()     { return resumen_ventas != null ? resumen_ventas : new ResumenVentas(); }
    public ResumenCompras getResumenCompras()   { return resumen_compras != null ? resumen_compras : new ResumenCompras(); }
    public KpisFinancieros getKpisFinancieros() { return kpis_financieros != null ? kpis_financieros : new KpisFinancieros(); }
    public AlertasActivas getAlertasActivas()   { return alertas_activas != null ? alertas_activas : new AlertasActivas(); }
    public Tendencias getTendencias()           { return tendencias != null ? tendencias : new Tendencias(); }
    public TopProductos getTopProductos()       { return top_productos != null ? top_productos : new TopProductos(); }
    public String getFechaGeneracion()          { return fecha_generacion; }

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    public static class Periodo {
        private String fecha_inicio;
        private String fecha_fin;
        public String getFechaInicio() { return fecha_inicio; }
        public String getFechaFin()    { return fecha_fin; }
    }

    public static class ResumenVentas {
        private double total;
        private int cantidad;
        private double ticket_promedio;
        private double variacion_periodo_anterior;
        private String tendencia;

        public double getTotal()                   { return total; }
        public int getCantidad()                   { return cantidad; }
        public double getTicketPromedio()          { return ticket_promedio; }
        public double getVariacion()               { return variacion_periodo_anterior; }
        public String getTendencia()               { return tendencia != null ? tendencia : "estable"; }
    }

    public static class ResumenCompras {
        private double total;
        private int cantidad;
        private double compra_promedio;
        private double variacion_periodo_anterior;
        private String tendencia;

        public double getTotal()                   { return total; }
        public int getCantidad()                   { return cantidad; }
        public double getCompraPromedio()          { return compra_promedio; }
        public double getVariacion()               { return variacion_periodo_anterior; }
        public String getTendencia()               { return tendencia != null ? tendencia : "estable"; }
    }

    public static class KpisFinancieros {
        private double ingresos_totales;
        private double costos_totales;
        private double utilidad_bruta;
        private double margen_bruto_porcentaje;
        private double roi_porcentaje;
        private String estado_financiero;

        public double getIngresosTotales()       { return ingresos_totales; }
        public double getCostosTotales()         { return costos_totales; }
        public double getUtilidadBruta()         { return utilidad_bruta; }
        public double getMargenBrutoPct()        { return margen_bruto_porcentaje; }
        public double getRoiPorcentaje()         { return roi_porcentaje; }
        public String getEstadoFinanciero()      { return estado_financiero != null ? estado_financiero : "sin_datos"; }
    }

    public static class AlertasActivas {
        private int total;
        private Map<String, Integer> por_tipo;
        private Map<String, Integer> por_importancia;
        private List<AlertaResumen> alertas;

        public int getTotal()                              { return total; }
        public Map<String, Integer> getPorTipo()           { return por_tipo; }
        public Map<String, Integer> getPorImportancia()    { return por_importancia; }
        public List<AlertaResumen> getAlertas()            { return alertas != null ? alertas : List.of(); }
    }

    public static class AlertaResumen {
        private int id;
        private String tipo;
        private String importancia;
        private String metrica;
        private double valor_actual;
        private double valor_esperado;
        private String creada_en;

        public int getId()               { return id; }
        public String getTipo()          { return tipo != null ? tipo : ""; }
        public String getImportancia()   { return importancia != null ? importancia : ""; }
        public String getMetrica()       { return metrica != null ? metrica : ""; }
        public double getValorActual()   { return valor_actual; }
        public double getValorEsperado() { return valor_esperado; }
        public String getCreadaEn()      { return creada_en; }
    }

    public static class Tendencias {
        private List<PuntoTendencia> ventas;
        private List<PuntoTendencia> compras;

        public List<PuntoTendencia> getVentas()  { return ventas != null ? ventas : List.of(); }
        public List<PuntoTendencia> getCompras() { return compras != null ? compras : List.of(); }
    }

    public static class PuntoTendencia {
        private String periodo;
        private double valor;
        public String getPeriodo() { return periodo; }
        public double getValor()   { return valor; }
    }

    public static class TopProductos {
        private List<ProductoTop> por_cantidad;
        private int total_productos_vendidos;

        public List<ProductoTop> getPorCantidad()     { return por_cantidad != null ? por_cantidad : List.of(); }
        public int getTotalProductosVendidos()         { return total_productos_vendidos; }
    }

    public static class ProductoTop {
        private int id_producto;
        private String nombre;
        private String categoria;
        private int cantidad_vendida;
        private double ingresos_generados;

        public int getIdProducto()          { return id_producto; }
        public String getNombre()           { return nombre != null ? nombre : ""; }
        public String getCategoria()        { return categoria != null ? categoria : ""; }
        public int getCantidadVendida()     { return cantidad_vendida; }
        public double getIngresosGenerados(){ return ingresos_generados; }
    }
}
