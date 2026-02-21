package com.app.model.reports;

import java.util.List;

/**
 * Respuesta de GET /dashboard/reports/types
 *
 * {
 *   "success": true,
 *   "tipos": [
 *     { "tipo": "ventas", "descripcion": "...", "formatos": [...], "agrupaciones": [...] },
 *     ...
 *   ]
 * }
 */
public class ReportTypesResponseDTO {
    private boolean success;
    private List<ApiReportType> tipos;

    public boolean isSuccess() { return success; }
    public List<ApiReportType> getTipos() { return tipos != null ? tipos : List.of(); }

    public static class ApiReportType {
        private String tipo;
        private String descripcion;
        private List<String> formatos;
        private List<String> agrupaciones;

        public String getTipo()             { return tipo != null ? tipo : ""; }
        public String getDescripcion()      { return descripcion != null ? descripcion : ""; }
        public List<String> getFormatos()   { return formatos != null ? formatos : List.of(); }
        public List<String> getAgrupaciones() { return agrupaciones != null ? agrupaciones : List.of(); }

        /** Convierte al ReportTypeDTO que usa la UI. */
        public ReportTypeDTO toReportTypeDTO() {
            String name = switch (tipo) {
                case "ventas"         -> "Reporte de Ventas";
                case "compras"        -> "Reporte de Compras";
                case "rentabilidad"   -> "Reporte de Rentabilidad";
                case "productos"      -> "Reporte de Productos";
                default               -> tipo.substring(0, 1).toUpperCase() + tipo.substring(1);
            };
            String icon = switch (tipo) {
                case "ventas"         -> "/images/reports/report-predictive.png";
                case "compras"        -> "/images/reports/report-predictive.png";
                case "rentabilidad"   -> "/images/reports/report-profit.png";
                case "productos"      -> "/images/reports/report-profit.png";
                default               -> "/images/reports/report-predictive.png";
            };
            return new ReportTypeDTO(tipo, name, descripcion != null ? descripcion : "", icon);
        }
    }
}
