package com.app.service.data;

import com.app.model.data.ValidationReport;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * CU-02: Validar estructura de datos cargados.
 * Recibe las filas del archivo (fila 0 = cabecera) y el tipo (Ventas/Compras).
 * No modifica los datos — solo reporta errores.
 */
public class ValidationService {

    // Formatos de fecha aceptados (CU-02 paso 3)
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
    );

    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Ejecuta todas las reglas de validación del CU-02.
     *
     * @param rows filas del archivo; rows.get(0) es la cabecera
     * @param tipo "Ventas" o "Compras"
     * @return ValidationReport con resultado completo
     */
    public ValidationReport validate(List<List<String>> rows, String tipo) {
        List<String> log = new ArrayList<>();

        // ── Cabecera ──────────────────────────────────────────────────────────
        List<String> rawHeaders = rows.get(0);
        List<String> headers    = rawHeaders.stream()
                .map(DataSchema::normalize)
                .toList();

        log.add("[CU-02] Tipo de archivo: " + tipo);
        log.add("[CU-02] Columnas detectadas: " + headers);

        // ── Regla 1 — columnas obligatorias (FA-01) ───────────────────────────
        List<String> required = DataSchema.getRequired(tipo);
        List<String> missing  = required.stream()
                .filter(col -> !headers.contains(col))
                .toList();

        if (!missing.isEmpty()) {
            log.add("[CU-02] FA-01 Estructura inválida. Faltan columnas: " + missing);
            log.forEach(System.out::println);
            return ValidationReport.structureFailed(missing);
        }
        log.add("[CU-02] ✔ Regla 1: columnas obligatorias presentes");

        // Índices de columnas clave
        int idxFecha    = headers.indexOf(DataSchema.COLUMNA_FECHA);
        List<Integer> idxNumericas = DataSchema.getNumericas(tipo).stream()
                .map(headers::indexOf)
                .filter(i -> i >= 0)
                .toList();

        // ── Validar filas de datos ────────────────────────────────────────────
        List<List<String>> dataRows = rows.subList(1, rows.size());
        int totalFields  = headers.size();
        int validRows    = 0;
        int invalidRows  = 0;
        int badDates     = 0;
        int badNumerics  = 0;
        int badQuantity  = 0;
        int tooManyEmpty = 0;
        int futureDates  = 0;

        Map<Integer, List<String>> rowErrors = new LinkedHashMap<>();

        for (int i = 0; i < dataRows.size(); i++) {
            List<String> row    = dataRows.get(i);
            List<String> errors = new ArrayList<>();
            int rowNum          = i + 2; // +2: 1-based + cabecera

            // ── Regla 5 — campos vacíos ≤ 50% (FA-04) ────────────────────────
            long emptyCount = row.stream()
                    .filter(cell -> cell == null || cell.isBlank())
                    .count();
            double emptyRatio = totalFields > 0 ? (double) emptyCount / totalFields : 0;
            if (emptyRatio > 0.5) {
                errors.add(String.format("Fila %d: %.0f%% campos vacíos (>50%%)", rowNum, emptyRatio * 100));
                tooManyEmpty++;
            }

            // ── Regla 2 — formato de fecha (FA-02) ───────────────────────────
            if (idxFecha >= 0 && idxFecha < row.size()) {
                String fechaStr = row.get(idxFecha).trim();
                if (!fechaStr.isBlank()) {
                    LocalDate parsed = parseDate(fechaStr);
                    if (parsed == null) {
                        errors.add("Fila " + rowNum + ": fecha con formato inválido → '" + fechaStr + "'");
                        badDates++;
                    } else if (parsed.isAfter(LocalDate.now())) {
                        // ── Regla 6 — sin fechas futuras ─────────────────────
                        errors.add("Fila " + rowNum + ": fecha futura → " + fechaStr);
                        futureDates++;
                    }
                }
            }

            // ── Regla 3 — valores monetarios positivos (FA-03) ───────────────
            // ── Regla 4 — cantidades > 0 ─────────────────────────────────────
            for (int idxNum : idxNumericas) {
                if (idxNum >= row.size()) continue;
                String colName = headers.get(idxNum);
                String cell    = row.get(idxNum).trim().replace(",", "").replace("$", "");
                if (cell.isBlank()) continue;

                try {
                    double val = Double.parseDouble(cell);
                    if (colName.equals("cantidad") && val <= 0) {
                        errors.add("Fila " + rowNum + ": cantidad debe ser > 0 → " + val);
                        badQuantity++;
                    } else if (!colName.equals("cantidad") && val < 0) {
                        errors.add("Fila " + rowNum + ": valor monetario negativo en '" + colName + "' → " + val);
                        badNumerics++;
                    }
                } catch (NumberFormatException e) {
                    errors.add("Fila " + rowNum + ": valor no numérico en '" + colName + "' → '" + cell + "'");
                    badNumerics++;
                }
            }

            if (errors.isEmpty()) {
                validRows++;
            } else {
                invalidRows++;
                rowErrors.put(rowNum, errors);
            }
        }

        // ── Resumen en consola ────────────────────────────────────────────────
        log.add("[CU-02] ✔ Regla 2: fechas inválidas → " + badDates);
        log.add("[CU-02] ✔ Regla 3: valores monetarios inválidos → " + badNumerics);
        log.add("[CU-02] ✔ Regla 4: cantidades inválidas → " + badQuantity);
        log.add("[CU-02] ✔ Regla 5: filas con >50% vacíos → " + tooManyEmpty);
        log.add("[CU-02] ✔ Regla 6: fechas futuras → " + futureDates);
        log.add(String.format("[CU-02] Validación completada. %d registros válidos, %d con errores.",
                validRows, invalidRows));

        // Detalle de errores por fila
        if (!rowErrors.isEmpty()) {
            log.add("[CU-02] Detalle de errores:");
            rowErrors.forEach((rowNum, errs) ->
                    errs.forEach(e -> log.add("  " + e)));
        }

        log.forEach(System.out::println);

        return new ValidationReport(true, List.of(),
                dataRows.size(), validRows, invalidRows, rowErrors, log);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(raw, fmt); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}