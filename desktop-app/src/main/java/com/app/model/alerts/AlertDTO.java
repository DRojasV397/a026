package com.app.model.alerts;

import java.time.LocalDateTime;

/**
 * DTO inmutable para representar una alerta del sistema.
 * Usado en las vistas de Alertas activas, Historial y Configuración.
 *
 * TODO: sustituir mocks por alertService.findAll() / alertService.findActive()
 */
public record AlertDTO(
        long   id,
        String type,          // RIESGO | OPORTUNIDAD | ADVERTENCIA
        String severity,      // CRITICA | ALTA | MEDIA | BAJA  (solo RIESGO/ADVERTENCIA)
        String impactLevel,   // ALTO | MEDIO | BAJO             (solo OPORTUNIDAD)
        String title,
        String description,
        String affectedMetric,
        double currentValue,
        double expectedValue,
        double deviationPercent,
        double confidenceLevel,
        String status,        // ACTIVA | RESUELTA | LEIDA
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,   // null si no resuelta
        String resolvedBy,          // null si no resuelta
        boolean read
) {
    /** Color hex según tipo y severidad — alineado con la paleta del módulo */
    public String colorHex() {
        return switch (type) {
            case "OPORTUNIDAD" -> "#58BD8B";
            case "RIESGO" -> switch (severity) {
                case "CRITICA", "ALTA" -> "#A03C48";
                default               -> "#D9A441";
            };
            default -> "#D9A441"; // ADVERTENCIA
        };
    }

    /** CSS style-class para el indicador lateral de color */
    public String colorStyleClass() {
        return switch (type) {
            case "OPORTUNIDAD" -> "alert-stripe-opportunity";
            case "RIESGO"      -> severity.equals("CRITICA") || severity.equals("ALTA")
                    ? "alert-stripe-critical"
                    : "alert-stripe-warning";
            default            -> "alert-stripe-warning";
        };
    }

    /** Etiqueta de severidad/impacto legible para UI */
    public String severityLabel() {
        if (type.equals("OPORTUNIDAD")) return "Impacto " + impactLevel;
        return severity.charAt(0) + severity.substring(1).toLowerCase();
    }
}