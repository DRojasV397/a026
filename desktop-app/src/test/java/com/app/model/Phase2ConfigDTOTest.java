package com.app.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para Phase2ConfigDTO - validaciones de configuración de predicciones.
 * Verifica reglas de negocio: RN-01.01 (mínimo 6 meses), horizonte 1-6 meses.
 */
class Phase2ConfigDTOTest {

    private Phase2ConfigDTO config;

    @BeforeEach
    void setUp() {
        config = new Phase2ConfigDTO();
    }

    @Test
    @DisplayName("Config nueva no es válida por defecto")
    void newConfigIsNotValid() {
        assertFalse(config.isValid());
        assertFalse(config.isHasEnoughData());
        assertFalse(config.isHasValidVariables());
        assertTrue(config.isHasNoErrors()); // sin errores al inicio
    }

    // ── Validación de fechas ──────────────────────────────────────────────

    @Test
    @DisplayName("Fecha fin antes de fecha inicio produce error")
    void endBeforeStartProducesError() {
        config.setStartDate(LocalDate.of(2025, 6, 1));
        config.setEndDate(LocalDate.of(2025, 1, 1));

        assertFalse(config.isHasNoErrors());
        assertFalse(config.getErrorMessage().isEmpty());
        assertTrue(config.getErrorMessage().contains("posterior"));
    }

    @Test
    @DisplayName("Fechas iguales producen error")
    void sameDatesProduceError() {
        LocalDate same = LocalDate.of(2025, 6, 1);
        config.setStartDate(same);
        config.setEndDate(same);

        assertFalse(config.isHasNoErrors());
        assertTrue(config.getErrorMessage().contains("iguales"));
    }

    @Test
    @DisplayName("Fechas válidas no producen error")
    void validDatesNoError() {
        config.setStartDate(LocalDate.of(2025, 1, 1));
        config.setEndDate(LocalDate.of(2025, 12, 31));

        assertTrue(config.isHasNoErrors());
        assertTrue(config.getErrorMessage().isEmpty());
    }

    // ── RN-01.01: Mínimo 6 meses ─────────────────────────────────────────

    @Test
    @DisplayName("RN-01.01: Menos de 6 meses de datos es insuficiente")
    void lessThan6MonthsInsufficientData() {
        config.setStartDate(LocalDate.of(2025, 1, 1));
        config.setEndDate(LocalDate.of(2025, 4, 1)); // 3 meses

        assertFalse(config.isHasEnoughData());
        assertTrue(config.getErrorMessage().contains("6 meses"));
    }

    @Test
    @DisplayName("RN-01.01: Exactamente 6 meses es suficiente")
    void exactly6MonthsIsSufficient() {
        config.setStartDate(LocalDate.of(2025, 1, 1));
        config.setEndDate(LocalDate.of(2025, 7, 1)); // exactamente 6 meses

        assertTrue(config.isHasEnoughData());
    }

    @Test
    @DisplayName("RN-01.01: Más de 6 meses es suficiente")
    void moreThan6MonthsIsSufficient() {
        config.setStartDate(LocalDate.of(2024, 1, 1));
        config.setEndDate(LocalDate.of(2025, 6, 1)); // 17 meses

        assertTrue(config.isHasEnoughData());
    }

    @Test
    @DisplayName("RN-01.01: 5 meses no es suficiente")
    void fiveMonthsInsufficient() {
        config.setStartDate(LocalDate.of(2025, 1, 1));
        config.setEndDate(LocalDate.of(2025, 6, 1)); // 5 meses

        assertFalse(config.isHasEnoughData());
    }

    // ── Variables ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sin variables seleccionadas no es válido")
    void noVariablesNotValid() {
        assertFalse(config.isHasValidVariables());
    }

    @Test
    @DisplayName("Lista vacía de variables no es válido")
    void emptyVariablesNotValid() {
        config.setSelectedVariables(new ArrayList<>());
        assertFalse(config.isHasValidVariables());
    }

    @Test
    @DisplayName("Al menos 1 variable seleccionada es válido")
    void oneVariableIsValid() {
        config.setSelectedVariables(List.of("Ventas"));
        assertTrue(config.isHasValidVariables());
    }

    @Test
    @DisplayName("Múltiples variables seleccionadas es válido")
    void multipleVariablesValid() {
        config.setSelectedVariables(List.of("Ventas", "Precio", "Stock"));
        assertTrue(config.isHasValidVariables());
    }

    // ── Horizonte de predicción ───────────────────────────────────────────

    @Test
    @DisplayName("Horizonte de 0 meses hace config inválida")
    void horizon0Invalid() {
        setupValidConfig();
        config.setPredictionHorizon(0);
        assertFalse(config.isValid());
    }

    @Test
    @DisplayName("Horizonte de 1 mes es válido")
    void horizon1Valid() {
        setupValidConfig();
        config.setPredictionHorizon(1);
        assertTrue(config.isValid());
    }

    @Test
    @DisplayName("Horizonte de 6 meses es válido")
    void horizon6Valid() {
        setupValidConfig();
        config.setPredictionHorizon(6);
        assertTrue(config.isValid());
    }

    @Test
    @DisplayName("Horizonte de 7 meses hace config inválida")
    void horizon7Invalid() {
        setupValidConfig();
        config.setPredictionHorizon(7);
        assertFalse(config.isValid());
    }

    // ── Configuración completa válida ─────────────────────────────────────

    @Test
    @DisplayName("Configuración completa válida pasa todas las validaciones")
    void fullValidConfig() {
        setupValidConfig();
        assertTrue(config.isValid());
        assertTrue(config.isHasEnoughData());
        assertTrue(config.isHasValidVariables());
        assertTrue(config.isHasNoErrors());
    }

    // ── División de datos ─────────────────────────────────────────────────

    @Test
    @DisplayName("División de datos es 70% entrenamiento / 30% validación")
    void dataSplitIs70_30() {
        assertEquals(70, config.getTrainPercentage());
        assertEquals(30, config.getValidationPercentage());
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private void setupValidConfig() {
        config.setStartDate(LocalDate.of(2024, 1, 1));
        config.setEndDate(LocalDate.of(2025, 1, 1)); // 12 meses
        config.setSelectedVariables(List.of("Ventas", "Precio"));
        config.setPredictionHorizon(3);
    }
}
