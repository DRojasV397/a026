package com.app.model.simulation;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO para un escenario de simulación (CU-24 a CU-28).
 *
 * @param id               identificador único
 * @param name             nombre descriptivo del escenario
 * @param description      descripción breve
 * @param periodMonths     período temporal a simular (1-6 meses según RN-05.02)
 * @param baseScenario     escenario base de referencia ("Actual", "Optimista", "Pesimista")
 * @param status           estado actual ("DRAFT", "CONFIGURED", "EXECUTED", "COMPARING")
 * @param modifiedVars     cantidad de variables modificadas respecto al base
 * @param variables        mapa de variables modificables con sus valores actuales
 * @param confidenceLevel  nivel de confianza de las proyecciones (0-100, RN-05.04)
 * @param createdAt        fecha de creación
 * @param createdBy        usuario que creó el escenario
 * @param executedAt       fecha de última ejecución (null si no se ha ejecutado)
 * @param accessLevel      nivel de acceso ("PRIVATE", "SHARED")
 */
public record ScenarioDTO(
        long id,
        String name,
        String description,
        int periodMonths,
        String baseScenario,
        String status,
        int modifiedVars,
        Map<String, VariableValue> variables,
        double confidenceLevel,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime executedAt,
        String accessLevel
) {
    /**
     * Valor de una variable modificable en el escenario.
     *
     * @param name        nombre de la variable (ej: "Precio unitario", "Volumen de ventas")
     * @param baseValue   valor base/original
     * @param currentValue valor actual (modificado por el usuario)
     * @param unit        unidad de medida ("$", "%", "unidades", etc.)
     * @param changePercent porcentaje de cambio respecto al base (-50 a +50 según RN-05.01)
     * @param min         valor mínimo permitido
     * @param max         valor máximo permitido
     */
    public record VariableValue(
            String name,
            double baseValue,
            double currentValue,
            String unit,
            double changePercent,
            double min,
            double max
    ) {
        public boolean isIncreased() {
            return currentValue > baseValue;
        }

        public boolean isDecreased() {
            return currentValue < baseValue;
        }

        public boolean isUnchanged() {
            return Math.abs(currentValue - baseValue) < 0.01;
        }
    }
}