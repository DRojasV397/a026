package com.app.service.data;

import java.util.List;

/**
 * Define las columnas obligatorias y opcionales por tipo de archivo.
 * Fuente: RN-01.02 (Ventas) y RN-01.03 (Compras)
 */
public class DataSchema {

    // ── Columnas obligatorias ─────────────────────────────────────────────────

    public static final List<String> COLUMNAS_VENTAS = List.of(
            "fecha",
            "producto",
            "precio_unitario",
            "cantidad",
            "monto_final"
    );

    public static final List<String> COLUMNAS_COMPRAS = List.of(
            "fecha",
            "proveedor",
            "producto",
            "precio_unitario",
            "cantidad",
            "monto_final"
    );

    // ── Columnas numéricas por tipo (usadas en validación y limpieza) ─────────

    public static final List<String> NUMERICAS_VENTAS = List.of(
            "precio_unitario", "cantidad", "monto_final"
    );

    public static final List<String> NUMERICAS_COMPRAS = List.of(
            "precio_unitario", "cantidad", "monto_final"
    );

    // ── Columnas de fecha ─────────────────────────────────────────────────────

    public static final String COLUMNA_FECHA = "fecha";

    // ─────────────────────────────────────────────────────────────────────────

    public static List<String> getRequired(String tipo) {
        return tipo.equalsIgnoreCase("Ventas") ? COLUMNAS_VENTAS : COLUMNAS_COMPRAS;
    }

    public static List<String> getNumericas(String tipo) {
        return tipo.equalsIgnoreCase("Ventas") ? NUMERICAS_VENTAS : NUMERICAS_COMPRAS;
    }

    /**
     * Normaliza un header leído del archivo para comparación:
     * trim, lowercase, espacios → guion bajo.
     */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase().replace(" ", "_");
    }
}