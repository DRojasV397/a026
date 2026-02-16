package com.app.model.data;

import java.util.List;
import java.util.Map;

/**
 * Resultado de CU-02: Validar estructura de datos cargados.
 *
 * @param structureValid   false si faltan columnas obligatorias → detiene el pipeline
 * @param missingColumns   columnas faltantes (FA-01)
 * @param totalRows        filas de datos (sin cabecera)
 * @param validRows        filas que pasaron todas las reglas
 * @param invalidRows      filas con al menos un error
 * @param rowErrors        mapa fila → lista de mensajes de error
 * @param log              resumen de cada regla ejecutada
 */
public record ValidationReport(
        boolean structureValid,
        List<String> missingColumns,
        int totalRows,
        int validRows,
        int invalidRows,
        Map<Integer, List<String>> rowErrors,
        List<String> log
) {
    /** Construye un reporte de fallo estructural (FA-01) */
    public static ValidationReport structureFailed(List<String> missing) {
        return new ValidationReport(false, missing, 0, 0, 0, Map.of(), List.of());
    }
}