package com.app.model.data.api;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * DTO que representa una fila del historial de transacciones (venta o compra).
 * Mapeado desde GET /data/historicos
 */
public class HistoricoItemDTO {

    private String fecha;         // ISO-8601: "2026-01-15"
    private String producto;
    private double precioUnitario;
    private double cantidad;
    private double total;
    private String tipo;          // "VENTA" | "COMPRA"

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getFecha()          { return fecha != null ? fecha : ""; }
    public String getProducto()       { return producto != null ? producto : ""; }
    public double getPrecioUnitario() { return precioUnitario; }
    public double getCantidad()       { return cantidad; }
    public double getTotal()          { return total; }
    public String getTipo()           { return tipo != null ? tipo : ""; }

    // ── Métodos de formato para columnas de tabla ─────────────────────────────

    /** Convierte "2026-01-15" → "15/01/2026" */
    public String getFechaFormatted() {
        if (fecha == null || fecha.length() < 10) return fecha != null ? fecha : "";
        String[] parts = fecha.split("-");
        if (parts.length < 3) return fecha;
        return parts[2] + "/" + parts[1] + "/" + parts[0];
    }

    /** Formatea precio como "$1,234.56" */
    public String getPrecioFormatted() {
        return formatCurrency(precioUnitario);
    }

    /** Formatea cantidad: sin decimales si es entero ("10"), con si no ("10.5") */
    public String getCantidadFormatted() {
        if (cantidad == (long) cantidad) {
            return String.valueOf((long) cantidad);
        }
        return String.format("%.2f", cantidad);
    }

    /** Formatea total como "$1,234.56" */
    public String getTotalFormatted() {
        return formatCurrency(total);
    }

    private static String formatCurrency(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "$" + nf.format(value);
    }
}
