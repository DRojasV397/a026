package com.app.model.data;

import java.util.List;

/**
 * Resultado completo del proceso de validación y limpieza (CU-02, CU-03).
 *
 * @param totalRecords       Registros originales
 * @param validRecords       Registros que pasaron validación
 * @param invalidRecords     Registros con errores
 * @param duplicatesRemoved  Duplicados eliminados en limpieza
 * @param estimatedValues    Valores imputados por mediana
 * @param outliersDetected   Valores atípicos señalados
 * @param retentionPercent   Porcentaje de registros conservados
 * @param ruleResults        Resultado por cada regla de validación
 */
public record ValidationResultDTO(
        int                   totalRecords,
        int                   validRecords,
        int                   invalidRecords,
        int                   duplicatesRemoved,
        int                   estimatedValues,
        int                   outliersDetected,
        double                retentionPercent,
        List<ValidationRuleResult> ruleResults
) {
    /** @return true si el porcentaje de retención es igual o mayor al 70% */
    public boolean meetsThreshold() {
        return retentionPercent >= 70.0;
    }

    /**
     * Resultado de una regla de validación individual.
     *
     * @param ruleName    Nombre legible de la regla
     * @param passed      Si la regla se cumplió
     * @param affectedRows Filas que fallaron esta regla (0 si passed=true)
     * @param detail      Detalle adicional (columnas faltantes, etc.)
     */
    public record ValidationRuleResult(
            String  ruleName,
            boolean passed,
            int     affectedRows,
            String  detail
    ) {}
}