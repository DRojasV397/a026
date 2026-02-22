package com.app.model.simulation;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO inmutable que representa un escenario de simulación.
 *
 * Campos añadidos respecto a la versión anterior:
 *   - modelName : nombre del modelo predictivo usado en la simulación
 */
public record ScenarioDTO(
        long              id,
        String            name,
        String            description,
        int               periodMonths,
        String            baseScenario,
        String            status,          // DRAFT | CONFIGURED | EXECUTED
        int               modifiedVars,
        Map<String, VariableValue> variables,
        double            confidence,
        LocalDateTime     createdAt,
        String            author,
        LocalDateTime     executedAt,      // null si no se ha ejecutado
        String            visibility,      // PRIVATE | SHARED
        String            modelName        // nombre del modelo predictivo; null si no asignado
) {

    /**
     * Valor de una variable dentro del escenario.
     *
     * @param name          Nombre legible de la variable
     * @param currentValue  Valor actual ajustado por el usuario
     * @param baseValue     Valor base del que parte la variación
     * @param unit          Unidad de medida ($, %, unidades, …)
     * @param changePercent Porcentaje de cambio respecto al base (calculado)
     * @param min           Límite mínimo permitido (baseValue × 0.5)
     * @param max           Límite máximo permitido (baseValue × 1.5)
     */
    public record VariableValue(
            String name,
            double currentValue,
            double baseValue,
            String unit,
            double changePercent,
            double min,
            double max
    ) {}
}