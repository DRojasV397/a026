package com.app.model;

public record ResultKpiDTO(
        String icon,          // Emoji o ícono
        String value,         // Valor principal
        String label,         // Etiqueta descriptiva
        String subtitle,      // Texto secundario opcional
        String colorClass,    // Clase CSS para color
        TrendType trend       // Tipo de tendencia
) {
    public enum TrendType {
        POSITIVE,   // Verde, flecha arriba
        NEGATIVE,   // Rojo, flecha abajo
        NEUTRAL     // Gris, sin flecha
    }
}
