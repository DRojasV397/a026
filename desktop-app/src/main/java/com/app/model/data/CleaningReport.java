package com.app.model.data;

import java.util.List;

/**
 * Resultado de CU-03: Limpiar datos automáticamente.
 *
 * @param originalCount    filas antes de limpiar
 * @param cleanCount       filas después de limpiar
 * @param duplicatesRemoved duplicados eliminados
 * @param discardedEmpty   filas descartadas por >50% vacíos
 * @param outliersFlagged  filas marcadas con valores atípicos (no eliminadas)
 * @param imputedValues    valores faltantes estimados (imputados)
 * @param retentionPercent porcentaje de registros conservados
 * @param meetsThreshold   true si retentionPercent >= 70%
 * @param cleanRows        filas limpias incluyendo cabecera en posición 0
 * @param log              bitácora de cada paso de limpieza
 */
public record CleaningReport(
        int originalCount,
        int cleanCount,
        int duplicatesRemoved,
        int discardedEmpty,
        int outliersFlagged,
        int imputedValues,
        double retentionPercent,
        boolean meetsThreshold,
        List<List<String>> cleanRows,
        List<String> log
) {}