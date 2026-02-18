package com.app.model.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para ValidationResultDTO (resultado combinado de CU-02 + CU-03).
 * Verifica el umbral de retención del 70% y el modelo de reglas de validación.
 */
@DisplayName("ValidationResultDTO - Resultado combinado de validación y limpieza")
class ValidationResultDTOTest {

    // ── meetsThreshold() ─────────────────────────────────────────────────

    @Test
    @DisplayName("meetsThreshold retorna true cuando retención >= 70%")
    void meetsThresholdReturnsTrueAbove70() {
        ValidationResultDTO result = makeResult(1000, 800, 200, 50, 30, 10, 80.0, List.of());
        assertTrue(result.meetsThreshold());
    }

    @Test
    @DisplayName("meetsThreshold retorna true exactamente en 70.0%")
    void meetsThresholdReturnsTrueAt70() {
        ValidationResultDTO result = makeResult(1000, 700, 300, 100, 20, 5, 70.0, List.of());
        assertTrue(result.meetsThreshold());
    }

    @Test
    @DisplayName("meetsThreshold retorna false cuando retención < 70%")
    void meetsThresholdReturnsFalseBelow70() {
        ValidationResultDTO result = makeResult(1000, 600, 400, 200, 10, 0, 60.0, List.of());
        assertFalse(result.meetsThreshold());
    }

    @Test
    @DisplayName("meetsThreshold retorna false en 69.99% (borde inferior)")
    void meetsThresholdReturnsFalseAt69Point99() {
        ValidationResultDTO result = makeResult(1000, 699, 301, 0, 0, 0, 69.99, List.of());
        assertFalse(result.meetsThreshold());
    }

    @Test
    @DisplayName("meetsThreshold retorna true con 100% (sin limpieza)")
    void meetsThresholdReturnsTrueAt100() {
        ValidationResultDTO result = makeResult(500, 500, 0, 0, 0, 0, 100.0, List.of());
        assertTrue(result.meetsThreshold());
    }

    // ── Campos principales ────────────────────────────────────────────────

    @Test
    @DisplayName("totalRecords, validRecords, invalidRecords son correctos")
    void countsAreCorrect() {
        ValidationResultDTO result = makeResult(300, 280, 20, 10, 5, 3, 93.3, List.of());

        assertEquals(300, result.totalRecords());
        assertEquals(280, result.validRecords());
        assertEquals(20, result.invalidRecords());
    }

    @Test
    @DisplayName("duplicatesRemoved es correcto")
    void duplicatesRemovedIsCorrect() {
        ValidationResultDTO result = makeResult(500, 480, 20, 10, 5, 15, 96.0, List.of());
        assertEquals(10, result.duplicatesRemoved());
    }

    @Test
    @DisplayName("estimatedValues (valores imputados) es correcto")
    void estimatedValuesIsCorrect() {
        ValidationResultDTO result = makeResult(200, 195, 5, 0, 8, 22, 97.5, List.of());
        assertEquals(22, result.estimatedValues());
    }

    @Test
    @DisplayName("outliersDetected es correcto")
    void outliersDetectedIsCorrect() {
        ValidationResultDTO result = makeResult(400, 390, 10, 5, 7, 0, 97.5, List.of());
        assertEquals(7, result.outliersDetected());
    }

    @Test
    @DisplayName("retentionPercent es correcto")
    void retentionPercentIsCorrect() {
        ValidationResultDTO result = makeResult(1000, 850, 150, 30, 15, 20, 85.0, List.of());
        assertEquals(85.0, result.retentionPercent(), 0.001);
    }

    // ── ValidationRuleResult (reglas de validación) ────────────────────

    @Test
    @DisplayName("ruleResults contiene las 6 reglas de validación")
    void ruleResultsContains6Rules() {
        List<ValidationResultDTO.ValidationRuleResult> rules = List.of(
                new ValidationResultDTO.ValidationRuleResult("Columnas obligatorias", true, 0, "OK"),
                new ValidationResultDTO.ValidationRuleResult("Formato fechas válido", true, 0, "OK"),
                new ValidationResultDTO.ValidationRuleResult("Valores monetarios > 0", true, 0, "OK"),
                new ValidationResultDTO.ValidationRuleResult("Cantidades > 0", false, 3, "3 filas con cantidad <= 0"),
                new ValidationResultDTO.ValidationRuleResult("Campos vacíos <= 50%", true, 0, "OK"),
                new ValidationResultDTO.ValidationRuleResult("Sin fechas futuras", true, 0, "OK")
        );

        ValidationResultDTO result = makeResult(100, 97, 3, 0, 0, 0, 97.0, rules);

        assertEquals(6, result.ruleResults().size());
    }

    @Test
    @DisplayName("ValidationRuleResult con regla fallida captura filas afectadas")
    void ruleResultWithFailureCapturesAffectedRows() {
        ValidationResultDTO.ValidationRuleResult rule = new ValidationResultDTO.ValidationRuleResult(
                "Valores monetarios positivos",
                false,
                7,
                "7 filas con monto <= 0"
        );

        assertFalse(rule.passed());
        assertEquals(7, rule.affectedRows());
        assertEquals("Valores monetarios positivos", rule.ruleName());
        assertTrue(rule.detail().contains("monto"));
    }

    @Test
    @DisplayName("ValidationRuleResult con regla exitosa tiene 0 filas afectadas")
    void ruleResultWithSuccessHasZeroAffectedRows() {
        ValidationResultDTO.ValidationRuleResult rule = new ValidationResultDTO.ValidationRuleResult(
                "Columnas obligatorias presentes",
                true,
                0,
                "Todas las columnas requeridas están presentes"
        );

        assertTrue(rule.passed());
        assertEquals(0, rule.affectedRows());
    }

    @Test
    @DisplayName("ruleResults puede estar vacía (caso inicial)")
    void ruleResultsCanBeEmpty() {
        ValidationResultDTO result = makeResult(0, 0, 0, 0, 0, 0, 0.0, List.of());
        assertNotNull(result.ruleResults());
        assertTrue(result.ruleResults().isEmpty());
    }

    // ── Caso de uso sintético: dataset de ventas completo ─────────────────

    @Test
    @DisplayName("Escenario completo: 1000 filas de ventas con limpieza exitosa")
    void syntheticSalesDataScenario() {
        List<ValidationResultDTO.ValidationRuleResult> rules = List.of(
                new ValidationResultDTO.ValidationRuleResult("Columnas obligatorias presentes", true, 0, "OK"),
                new ValidationResultDTO.ValidationRuleResult("Formato de fechas válido", true, 0, "Todas las fechas en ISO 8601"),
                new ValidationResultDTO.ValidationRuleResult("Valores monetarios positivos", true, 0, "OK"),
                new ValidationResultDTO.ValidationRuleResult("Cantidades > 0", true, 0, "OK"),
                new ValidationResultDTO.ValidationRuleResult("Campos vacíos <= 50%", true, 0, "Máx 3% nulls"),
                new ValidationResultDTO.ValidationRuleResult("Sin fechas futuras", true, 0, "OK")
        );

        ValidationResultDTO result = makeResult(1000, 978, 22, 15, 8, 12, 97.8, rules);

        assertTrue(result.meetsThreshold());
        assertEquals(1000, result.totalRecords());
        assertEquals(978, result.validRecords());
        assertEquals(22, result.invalidRecords());
        assertEquals(6, result.ruleResults().size());

        long passedRules = result.ruleResults().stream()
                .filter(ValidationResultDTO.ValidationRuleResult::passed)
                .count();
        assertEquals(6, passedRules);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ValidationResultDTO makeResult(
            int total, int valid, int invalid, int dupes, int outliers,
            int estimated, double retention, List<ValidationResultDTO.ValidationRuleResult> rules
    ) {
        return new ValidationResultDTO(total, valid, invalid, dupes, estimated, outliers, retention, rules);
    }
}
