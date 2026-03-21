package com.app.model.profitability;

import java.util.List;

/** Respuesta de GET /profitability/projection */
public class ProjectionResponseDTO {
    private boolean success;
    private String pack_key;
    private String pack_nombre;
    private double precision_ventas;
    private double precision_compras;
    private int    periods;
    private String fecha_inicio_proyeccion;
    private String fecha_fin_proyeccion;
    private ProjectionGeneralDTO general;
    private List<ProjectionItemDTO> por_categoria;
    private List<ProjectionItemDTO> por_producto;

    public boolean isSuccess()                         { return success; }
    public String  getPackKey()                        { return pack_key   != null ? pack_key   : ""; }
    public String  getPackNombre()                     { return pack_nombre != null ? pack_nombre : pack_key != null ? pack_key : ""; }
    public double  getPrecisionVentas()                { return precision_ventas; }
    public double  getPrecisionCompras()               { return precision_compras; }
    public int     getPeriods()                        { return periods; }
    public String  getFechaInicioProyeccion()          { return fecha_inicio_proyeccion != null ? fecha_inicio_proyeccion : ""; }
    public String  getFechaFinProyeccion()             { return fecha_fin_proyeccion    != null ? fecha_fin_proyeccion    : ""; }
    public ProjectionGeneralDTO      getGeneral()      { return general; }
    public List<ProjectionItemDTO>   getPorCategoria() { return por_categoria != null ? por_categoria : List.of(); }
    public List<ProjectionItemDTO>   getPorProducto()  { return por_producto  != null ? por_producto  : List.of(); }
}
