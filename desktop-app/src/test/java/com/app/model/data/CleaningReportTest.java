package com.app.model.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para el modelo CleaningReport (CU-03: Limpiar datos automáticamente).
 * Verifica campos, umbral de retención (RN-02: >= 70%) y datos de filas limpias.
 */
@DisplayName("CleaningReport - Modelo de reporte de limpieza")
class CleaningReportTest {

    // ── Umbral de retención (RN-02: >= 70%) ──────────────────────────────

    @Test
    @DisplayName("meetsThreshold es true cuando retención >= 70%")
    void meetsThresholdAbove70Percent() {
        CleaningReport report = makeReport(1000, 750, 0, 0, 0, 0, 75.0, true, List.of());
        assertTrue(report.meetsThreshold());
        assertEquals(75.0, report.retentionPercent(), 0.001);
    }

    @Test
    @DisplayName("meetsThreshold es true exactamente en 70%")
    void meetsThresholdExactly70Percent() {
        CleaningReport report = makeReport(1000, 700, 0, 0, 0, 0, 70.0, true, List.of());
        assertTrue(report.meetsThreshold());
        assertEquals(70.0, report.retentionPercent(), 0.001);
    }

    @Test
    @DisplayName("meetsThreshold es false cuando retención < 70%")
    void doesNotMeetThresholdBelow70Percent() {
        CleaningReport report = makeReport(1000, 500, 0, 0, 0, 0, 50.0, false, List.of());
        assertFalse(report.meetsThreshold());
    }

    @Test
    @DisplayName("meetsThreshold es false con 69.9% de retención")
    void doesNotMeetThresholdAt69Point9Percent() {
        CleaningReport report = makeReport(1000, 699, 0, 0, 0, 0, 69.9, false, List.of());
        assertFalse(report.meetsThreshold());
    }

    @Test
    @DisplayName("meetsThreshold es true con 100% de retención (sin limpieza)")
    void meetsThresholdAt100Percent() {
        CleaningReport report = makeReport(500, 500, 0, 0, 0, 0, 100.0, true, List.of());
        assertTrue(report.meetsThreshold());
    }

    // ── Contadores de limpieza ────────────────────────────────────────────

    @Test
    @DisplayName("Duplicados eliminados se registran correctamente")
    void duplicatesRemovedCountIsCorrect() {
        CleaningReport report = makeReport(1000, 980, 20, 0, 0, 0, 98.0, true, List.of());
        assertEquals(20, report.duplicatesRemoved());
        assertEquals(1000, report.originalCount());
        assertEquals(980, report.cleanCount());
    }

    @Test
    @DisplayName("Filas vacías descartadas (>50% nulls) se registran")
    void emptyRowsDiscardedCountIsCorrect() {
        CleaningReport report = makeReport(100, 88, 5, 7, 0, 0, 88.0, true, List.of());
        assertEquals(7, report.discardedEmpty());
        assertEquals(5, report.duplicatesRemoved());
    }

    @Test
    @DisplayName("Valores atípicos marcados con ±3σ se registran")
    void outliersFlaggerCountIsCorrect() {
        CleaningReport report = makeReport(200, 195, 0, 0, 15, 0, 97.5, true, List.of());
        assertEquals(15, report.outliersFlagged());
    }

    @Test
    @DisplayName("Valores imputados por mediana se registran")
    void imputedValuesCountIsCorrect() {
        CleaningReport report = makeReport(300, 290, 3, 7, 5, 42, 96.7, true, List.of());
        assertEquals(42, report.imputedValues());
    }

    // ── Filas limpias (cleanRows) ─────────────────────────────────────────

    @Test
    @DisplayName("cleanRows incluye cabecera en posición 0")
    void cleanRowsIncludesHeaderAtIndex0() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("fecha", "producto", "monto", "cantidad"));
        rows.add(List.of("2024-01-15", "Producto A", "1500.00", "10"));
        rows.add(List.of("2024-01-16", "Producto B", "2300.50", "5"));

        CleaningReport report = makeReport(2, 2, 0, 0, 0, 0, 100.0, true, rows);

        assertEquals(3, report.cleanRows().size()); // 1 header + 2 data rows
        assertEquals("fecha", report.cleanRows().get(0).get(0));
        assertEquals("producto", report.cleanRows().get(0).get(1));
    }

    @Test
    @DisplayName("cleanRows vacía cuando todos los registros son eliminados")
    void emptyCleanRowsWhenAllRemoved() {
        CleaningReport report = makeReport(10, 0, 10, 0, 0, 0, 0.0, false, List.of());
        assertTrue(report.cleanRows().isEmpty());
    }

    @Test
    @DisplayName("cleanRows contiene datos correctos de ventas")
    void cleanRowsContainCorrectSalesData() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("fecha", "producto", "precio", "cantidad", "total"));
        rows.add(List.of("2024-03-10", "Leche Entera", "850.00", "50", "42500.00"));
        rows.add(List.of("2024-03-11", "Pan Baguette", "420.00", "30", "12600.00"));

        CleaningReport report = makeReport(2, 2, 0, 0, 0, 0, 100.0, true, rows);

        assertEquals("Leche Entera", report.cleanRows().get(1).get(1));
        assertEquals("850.00", report.cleanRows().get(1).get(2));
        assertEquals("42500.00", report.cleanRows().get(1).get(4));
    }

    // ── Log de limpieza ───────────────────────────────────────────────────

    @Test
    @DisplayName("Log registra los pasos de limpieza")
    void logRecordsCleaningSteps() {
        List<String> log = List.of(
                "Paso 1: Eliminados 5 duplicados",
                "Paso 2: Descartadas 3 filas vacías (>50% nulls)",
                "Paso 3: Marcados 2 outliers (±3σ)",
                "Paso 4: Imputados 10 valores por mediana"
        );

        CleaningReport report = makeReport(100, 90, 5, 3, 2, 10, 90.0, true, List.of(), log);

        assertEquals(4, report.log().size());
        assertTrue(report.log().get(0).contains("duplicados"));
        assertTrue(report.log().get(3).contains("mediana"));
    }

    @Test
    @DisplayName("Log vacío cuando no hay pasos de limpieza")
    void emptyLogWhenNoCleaningNeeded() {
        CleaningReport report = makeReport(50, 50, 0, 0, 0, 0, 100.0, true, List.of());
        assertTrue(report.log().isEmpty());
    }

    // ── Caso borde: dataset de tamaño mínimo ─────────────────────────────

    @Test
    @DisplayName("Reporte con un solo registro limpio")
    void reportWithSingleCleanRecord() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("fecha", "monto"));
        rows.add(List.of("2024-01-01", "100.00"));

        CleaningReport report = makeReport(1, 1, 0, 0, 0, 0, 100.0, true, rows);

        assertEquals(1, report.originalCount());
        assertEquals(1, report.cleanCount());
        assertEquals(2, report.cleanRows().size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private CleaningReport makeReport(
            int original, int clean, int dupes, int empty, int outliers, int imputed,
            double retentionPct, boolean meetsThreshold, List<List<String>> rows
    ) {
        return new CleaningReport(original, clean, dupes, empty, outliers, imputed,
                retentionPct, meetsThreshold, rows, List.of());
    }

    private CleaningReport makeReport(
            int original, int clean, int dupes, int empty, int outliers, int imputed,
            double retentionPct, boolean meetsThreshold, List<List<String>> rows, List<String> log
    ) {
        return new CleaningReport(original, clean, dupes, empty, outliers, imputed,
                retentionPct, meetsThreshold, rows, log);
    }
}
