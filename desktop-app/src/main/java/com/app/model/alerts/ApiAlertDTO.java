package com.app.model.alerts;

import com.app.model.alerts.AlertDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * DTO que mapea exactamente la respuesta JSON de la API de alertas.
 *
 * Estructura de _alert_to_dict() del backend:
 * {
 *   "id_alerta": int,
 *   "tipo": "Riesgo" | "Oportunidad" | "Anomalia" | "Tendencia" | "Umbral",
 *   "importancia": "Alta" | "Media" | "Baja",
 *   "metrica": String,
 *   "valor_actual": double,
 *   "valor_esperado": double,
 *   "nivel_confianza": double,
 *   "estado": "Activa" | "Leida" | "Resuelta" | "Ignorada",
 *   "creada_en": ISO datetime string
 * }
 */
public class ApiAlertDTO {
    private long id_alerta;
    private String tipo;
    private String importancia;
    private String metrica;
    private double valor_actual;
    private double valor_esperado;
    private double nivel_confianza;
    private String estado;
    private String creada_en;

    // ── Getters ────────────────────────────────────────────────────────────────

    public long getIdAlerta()         { return id_alerta; }
    public String getTipo()           { return tipo != null ? tipo : ""; }
    public String getImportancia()    { return importancia != null ? importancia : ""; }
    public String getMetrica()        { return metrica != null ? metrica : ""; }
    public double getValorActual()    { return valor_actual; }
    public double getValorEsperado()  { return valor_esperado; }
    public double getNivelConfianza() { return nivel_confianza; }
    public String getEstado()         { return estado != null ? estado : "Activa"; }
    public String getCreadaEn()       { return creada_en; }

    // ── Conversión a AlertDTO del UI ──────────────────────────────────────────

    /**
     * Convierte este DTO de API al AlertDTO usado por el AlertsController.
     * Genera título y descripción a partir de los campos disponibles.
     */
    public AlertDTO toAlertDTO() {
        String type       = mapTipo(tipo);
        String severity   = mapSeverity(tipo, importancia);
        String impactLevel = mapImpactLevel(tipo, importancia);
        String title      = generateTitle(tipo, metrica);
        String description = generateDescription(tipo, metrica, valor_actual, valor_esperado);
        double deviation  = calcDeviation();
        String status     = estado != null ? estado.toUpperCase() : "ACTIVA";
        LocalDateTime createdAt = parseDateTime(creada_en);
        boolean read      = "Leida".equalsIgnoreCase(estado) || "Resuelta".equalsIgnoreCase(estado);

        return new AlertDTO(
                id_alerta,
                type,
                severity,
                impactLevel,
                title,
                description,
                metrica != null ? metrica : "",
                valor_actual,
                valor_esperado,
                deviation,
                nivel_confianza,
                status,
                createdAt,
                null,   // resolvedAt — no viene de la API básica
                null,   // resolvedBy — no viene de la API básica
                read
        );
    }

    // ── Métodos privados de mapeo ──────────────────────────────────────────────

    private String mapTipo(String tipo) {
        if (tipo == null) return "ADVERTENCIA";
        return switch (tipo) {
            case "Riesgo"    -> "RIESGO";
            case "Oportunidad" -> "OPORTUNIDAD";
            case "Anomalia", "Tendencia", "Umbral" -> "ADVERTENCIA";
            default          -> "ADVERTENCIA";
        };
    }

    /** Severity aplica solo para RIESGO y ADVERTENCIA */
    private String mapSeverity(String tipo, String importancia) {
        if ("Oportunidad".equals(tipo)) return null;
        if (importancia == null) return "MEDIA";
        return switch (importancia) {
            case "Alta"  -> "ALTA";
            case "Baja"  -> "BAJA";
            default      -> "MEDIA";
        };
    }

    /** ImpactLevel aplica solo para OPORTUNIDAD */
    private String mapImpactLevel(String tipo, String importancia) {
        if (!"Oportunidad".equals(tipo)) return null;
        if (importancia == null) return "MEDIO";
        return switch (importancia) {
            case "Alta" -> "ALTO";
            case "Baja" -> "BAJO";
            default     -> "MEDIO";
        };
    }

    private String generateTitle(String tipo, String metrica) {
        String m = metrica != null ? metrica : "métrica";
        if (tipo == null) return "Alerta en " + m;
        return switch (tipo) {
            case "Riesgo"     -> "Riesgo detectado en " + m;
            case "Oportunidad"-> "Oportunidad en " + m;
            case "Anomalia"   -> "Anomalía en " + m;
            case "Tendencia"  -> "Tendencia en " + m;
            case "Umbral"     -> "Umbral superado en " + m;
            default           -> "Alerta en " + m;
        };
    }

    private String generateDescription(String tipo, String metrica, double actual, double esperado) {
        String m = metrica != null ? metrica : "la métrica";
        double dev = calcDeviation();
        String direction = dev >= 0 ? "superó" : "cayó por debajo de";
        return String.format(
                "El valor actual de %s (%.2f) %s el valor esperado (%.2f). Desviación: %.1f%%.",
                m, actual, direction, esperado, dev
        );
    }

    private double calcDeviation() {
        if (valor_esperado == 0) return 0;
        return ((valor_actual - valor_esperado) / Math.abs(valor_esperado)) * 100;
    }

    private LocalDateTime parseDateTime(String isoStr) {
        if (isoStr == null || isoStr.isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(isoStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                // Try without milliseconds
                return LocalDateTime.parse(isoStr.substring(0, 19),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            } catch (Exception ex) {
                return LocalDateTime.now();
            }
        }
    }
}
