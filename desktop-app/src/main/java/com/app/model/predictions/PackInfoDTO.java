package com.app.model.predictions;

import java.util.Map;

/**
 * Información de un pack de modelos entrenado.
 * Usado en GET /predictions/packs
 */
public class PackInfoDTO {

    private int    pack_id;
    private String pack_key;
    private String nombre;
    private String creado_en;
    private VersionInfo ventas;
    private VersionInfo compras;

    public PackInfoDTO() {}

    /** Información de la versión de un submodelo del pack. */
    public static class VersionInfo {
        private Integer version_id;
        private Double  precision;
        private Map<String, Object> metricas;

        public Integer getVersion_id()              { return version_id; }
        public void    setVersion_id(Integer id)    { this.version_id = id; }
        public Double  getPrecision()               { return precision; }
        public void    setPrecision(Double p)       { this.precision = p; }
        public Map<String, Object> getMetricas()    { return metricas; }
        public void    setMetricas(Map<String, Object> m){ this.metricas = m; }

        public double getR2Score() {
            if (metricas == null) return 0.0;
            Object val = metricas.get("r2_score");
            return val instanceof Number ? ((Number) val).doubleValue() : 0.0;
        }
    }

    public int    getPack_id()              { return pack_id; }
    public void   setPack_id(int id)        { this.pack_id = id; }

    public String getPack_key()             { return pack_key; }
    public void   setPack_key(String k)     { this.pack_key = k; }

    public String getNombre()               { return nombre; }
    public void   setNombre(String n)       { this.nombre = n; }

    public String getCreado_en()            { return creado_en; }
    public void   setCreado_en(String c)    { this.creado_en = c; }

    public VersionInfo getVentas()          { return ventas; }
    public void   setVentas(VersionInfo v)  { this.ventas = v; }

    public VersionInfo getCompras()         { return compras; }
    public void   setCompras(VersionInfo c) { this.compras = c; }

    public String getDisplayName() {
        return nombre != null && !nombre.isEmpty() ? nombre : pack_key;
    }
}
