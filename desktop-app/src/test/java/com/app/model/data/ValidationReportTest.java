package com.app.model.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para el modelo ValidationReport (CU-02: Validar estructura de datos).
 * Verifica construcción, campos y el método de fábrica structureFailed().
 */
@DisplayName("ValidationReport - Modelo de reporte de validación")
class ValidationReportTest {

    // ── Constructor completo ──────────────────────────────────────────────

    @Test
    @DisplayName("Constructor completo inicializa todos los campos")
    void constructorSetsAllFields() {
        List<String> missing = List.of();
        Map<Integer, List<String>> errors = Map.of(1, List.of("Fecha inválida"));
        List<String> log = List.of("Regla 1: OK", "Regla 2: FAIL");

        ValidationReport report = new ValidationReport(
                true, missing, 100, 95, 5, errors, log
        );

        assertTrue(report.structureValid());
        assertEquals(missing, report.missingColumns());
        assertEquals(100, report.totalRows());
        assertEquals(95, report.validRows());
        assertEquals(5, report.invalidRows());
        assertNotNull(report.rowErrors());
        assertEquals(1, report.rowErrors().size());
        assertEquals(2, report.log().size());
    }

    // ── structureFailed() factory ─────────────────────────────────────────

    @Test
    @DisplayName("structureFailed() crea reporte con structureValid=false")
    void structureFailedCreatesInvalidReport() {
        List<String> missingCols = List.of("fecha", "monto");
        ValidationReport report = ValidationReport.structureFailed(missingCols);

        assertFalse(report.structureValid());
        assertEquals(missingCols, report.missingColumns());
        assertEquals(2, report.missingColumns().size());
    }

    @Test
    @DisplayName("structureFailed() pone contadores en cero")
    void structureFailedZerosCounts() {
        ValidationReport report = ValidationReport.structureFailed(List.of("col1"));

        assertEquals(0, report.totalRows());
        assertEquals(0, report.validRows());
        assertEquals(0, report.invalidRows());
    }

    @Test
    @DisplayName("structureFailed() tiene mapa de errores vacío")
    void structureFailedEmptyRowErrors() {
        ValidationReport report = ValidationReport.structureFailed(List.of("col1"));

        assertNotNull(report.rowErrors());
        assertTrue(report.rowErrors().isEmpty());
    }

    @Test
    @DisplayName("structureFailed() tiene log vacío")
    void structureFailedEmptyLog() {
        ValidationReport report = ValidationReport.structureFailed(List.of("col1"));

        assertNotNull(report.log());
        assertTrue(report.log().isEmpty());
    }

    // ── Validación positiva ───────────────────────────────────────────────

    @Test
    @DisplayName("Reporte válido con todos los registros correctos")
    void validReportWithAllRowsCorrect() {
        ValidationReport report = new ValidationReport(
                true, List.of(), 500, 500, 0, Map.of(), List.of("Todas las reglas: OK")
        );

        assertTrue(report.structureValid());
        assertTrue(report.missingColumns().isEmpty());
        assertEquals(500, report.totalRows());
        assertEquals(500, report.validRows());
        assertEquals(0, report.invalidRows());
    }

    @Test
    @DisplayName("Reporte con errores por fila captura múltiples errores")
    void reportWithMultipleRowErrors() {
        Map<Integer, List<String>> rowErrors = Map.of(
                1, List.of("Fecha inválida"),
                5, List.of("Monto negativo", "Campo vacío"),
                10, List.of("Fecha futura")
        );

        ValidationReport report = new ValidationReport(
                true, List.of(), 200, 197, 3, rowErrors, List.of()
        );

        assertEquals(3, report.rowErrors().size());
        assertTrue(report.rowErrors().containsKey(5));
        assertEquals(2, report.rowErrors().get(5).size());
    }

    // ── Casos borde ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Reporte con una sola fila válida")
    void reportWithSingleRow() {
        ValidationReport report = new ValidationReport(
                true, List.of(), 1, 1, 0, Map.of(), List.of()
        );

        assertEquals(1, report.totalRows());
        assertEquals(1, report.validRows());
        assertEquals(0, report.invalidRows());
    }

    @Test
    @DisplayName("structureFailed() con lista vacía de columnas faltantes")
    void structureFailedEmptyMissingList() {
        ValidationReport report = ValidationReport.structureFailed(List.of());

        assertFalse(report.structureValid());
        assertTrue(report.missingColumns().isEmpty());
    }

    @Test
    @DisplayName("Todos los registros inválidos - caso extremo")
    void reportAllInvalidRows() {
        ValidationReport report = new ValidationReport(
                false, List.of("fecha"), 50, 0, 50, Map.of(), List.of("FA-01: Columna fecha faltante")
        );

        assertFalse(report.structureValid());
        assertEquals(50, report.totalRows());
        assertEquals(0, report.validRows());
        assertEquals(50, report.invalidRows());
    }

    @Test
    @DisplayName("structureFailed() con múltiples columnas faltantes (FA-01)")
    void structureFailedWithManyMissingColumns() {
        List<String> missing = List.of("fecha", "monto", "cantidad", "producto", "cliente");
        ValidationReport report = ValidationReport.structureFailed(missing);

        assertFalse(report.structureValid());
        assertEquals(5, report.missingColumns().size());
        assertTrue(report.missingColumns().contains("fecha"));
        assertTrue(report.missingColumns().contains("monto"));
        assertTrue(report.missingColumns().contains("cantidad"));
    }
}
