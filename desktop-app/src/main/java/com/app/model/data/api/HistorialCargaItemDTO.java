package com.app.model.data.api;

/**
 * DTO para un ítem del historial de cargas de datos.
 * Mapea la respuesta del endpoint GET /data/historial
 */
public class HistorialCargaItemDTO {

    private int    idHistorial;
    private String uploadId;
    private String tipoDatos;
    private String nombreArchivo;
    private int    registrosInsertados;
    private int    registrosActualizados;
    private int    cargadoPor;
    /** Fecha ISO-8601 devuelta por la API, ej: "2026-01-15T10:30:00" */
    private String cargadoEn;
    private String estado;

    public int    getIdHistorial()            { return idHistorial; }
    public String getUploadId()               { return uploadId; }
    public String getTipoDatos()              { return tipoDatos != null ? tipoDatos : ""; }
    public String getNombreArchivo()          { return nombreArchivo != null ? nombreArchivo : "Sin nombre"; }
    public int    getRegistrosInsertados()    { return registrosInsertados; }
    public int    getRegistrosActualizados()  { return registrosActualizados; }
    public int    getCargadoPor()             { return cargadoPor; }
    public String getCargadoEn()             { return cargadoEn; }
    public String getEstado()                { return estado != null ? estado : "desconocido"; }

    /** Devuelve la extensión del archivo en mayúsculas: "CSV", "XLSX" o "ARCHIVO". */
    public String getFileExtension() {
        if (nombreArchivo == null) return "ARCHIVO";
        String lower = nombreArchivo.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return "XLSX";
        if (lower.endsWith(".csv"))  return "CSV";
        return "ARCHIVO";
    }
}
