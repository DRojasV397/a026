package com.app.service.data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * CU-04: Transformar y normalizar datos.
 * Recibe las filas limpias (fila 0 = cabecera) del CleaningReport.
 * Devuelve las filas transformadas listas para análisis.
 */
public class TransformService {

    private static final DateTimeFormatter FMT_OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<DateTimeFormatter> FMT_IN = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
    );

    /**
     * Ejecuta el pipeline de transformación del CU-04.
     *
     * @param cleanRows filas limpias; cleanRows.get(0) es la cabecera
     * @param tipo      "Ventas" o "Compras"
     * @return filas transformadas con cabecera actualizada en posición 0
     */
    public List<List<String>> transform(List<List<String>> cleanRows, String tipo) {
        List<String> log = new ArrayList<>();

        List<String> headers = cleanRows.get(0).stream()
                .map(DataSchema::normalize).toList();
        List<List<String>> data = new ArrayList<>();
        for (int i = 1; i < cleanRows.size(); i++)
            data.add(new ArrayList<>(cleanRows.get(i))); // copia mutable

        int idxFecha = headers.indexOf(DataSchema.COLUMNA_FECHA);
        List<Integer> idxNumericas = DataSchema.getNumericas(tipo).stream()
                .map(col -> headers.indexOf(col))
                .filter(i -> i >= 0)
                .toList();

        log.add("[CU-04] Inicio de transformación. Filas: " + data.size());

        // ── Paso 1 — normalizar fechas a yyyy-MM-dd ───────────────────────────
        int badDates = 0;
        if (idxFecha >= 0) {
            for (int i = 0; i < data.size(); i++) {
                String raw    = safeGet(data.get(i), idxFecha).trim();
                LocalDate date = parseDate(raw);
                if (date != null) {
                    data.get(i).set(idxFecha, date.format(FMT_OUT));
                } else {
                    badDates++;
                    log.add("[CU-04] FA-01 Fecha no convertible en fila " + (i + 2) + ": '" + raw + "'");
                }
            }
        }
        log.add("[CU-04] Paso 1: fechas normalizadas a yyyy-MM-dd. No convertibles: " + badDates);

        // ── Paso 2 — estandarizar montos a 2 decimales ───────────────────────
        int idxMonto = headers.indexOf("monto_final");
        int idxPrecio = headers.indexOf("precio_unitario");
        for (List<String> row : data) {
            formatDecimal(row, idxMonto);
            formatDecimal(row, idxPrecio);
        }
        log.add("[CU-04] Paso 2: valores monetarios formateados a 2 decimales");

        // ── Paso 3 — normalizar numéricos Min-Max ─────────────────────────────
        int outOfRange = 0;
        for (int colIdx : idxNumericas) {
            List<Double> nums = extractNumericColumn(data, colIdx);
            if (nums.isEmpty()) continue;

            double min = nums.stream().mapToDouble(d -> d).min().orElse(0);
            double max = nums.stream().mapToDouble(d -> d).max().orElse(1);
            double range = max - min;
            String colName = headers.get(colIdx);

            for (List<String> row : data) {
                String cell = safeGet(row, colIdx).replace(",", "").replace("$", "").trim();
                if (cell.isBlank()) continue;
                try {
                    double val       = Double.parseDouble(cell);
                    double normalized = range == 0 ? 0 : (val - min) / range;

                    // FA-02: advertir si queda fuera de [0,1] por errores previos
                    if (normalized < 0 || normalized > 1) {
                        outOfRange++;
                        log.add("[CU-04] FA-02 Valor fuera de rango tras Min-Max en '" + colName + "': " + normalized);
                    }
                    row.set(colIdx, String.format(Locale.US, "%.4f", normalized));
                } catch (NumberFormatException ignored) {}
            }
        }
        log.add("[CU-04] Paso 3: normalización Min-Max aplicada. Valores fuera de rango: " + outOfRange);

        // ── Paso 4 — crear variables derivadas de fecha ───────────────────────
        // Agrega columnas: dia_semana, mes, trimestre al final de cada fila
        List<String> newHeaders = new ArrayList<>(cleanRows.get(0));
        newHeaders.add("dia_semana");
        newHeaders.add("mes");
        newHeaders.add("trimestre");

        if (idxFecha >= 0) {
            for (List<String> row : data) {
                String fechaStr = safeGet(row, idxFecha).trim();
                LocalDate date  = parseDate(fechaStr);
                if (date != null) {
                    row.add(date.getDayOfWeek().getDisplayName(
                            java.time.format.TextStyle.FULL,
                            new Locale("es", "MX")));
                    row.add(date.getMonth().getDisplayName(
                            java.time.format.TextStyle.FULL,
                            new Locale("es", "MX")));
                    row.add("Q" + ((date.getMonthValue() - 1) / 3 + 1));
                } else {
                    row.add(""); row.add(""); row.add("");
                }
            }
        }
        log.add("[CU-04] Paso 4: variables derivadas creadas (dia_semana, mes, trimestre)");
        log.add("[CU-04] Transformación completada. Los datos están listos para análisis.");

        log.forEach(System.out::println);

        // Reconstruir con cabecera actualizada
        List<List<String>> result = new ArrayList<>();
        result.add(newHeaders);
        result.addAll(data);
        return result;
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private void formatDecimal(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) return;
        String cell = row.get(idx).replace(",", "").replace("$", "").trim();
        if (cell.isBlank()) return;
        try {
            double val = Double.parseDouble(cell);
            row.set(idx, String.format(Locale.US, "%.2f", val));
        } catch (NumberFormatException ignored) {}
    }

    private List<Double> extractNumericColumn(List<List<String>> data, int colIdx) {
        List<Double> result = new ArrayList<>();
        for (List<String> row : data) {
            String cell = safeGet(row, colIdx).replace(",", "").replace("$", "").trim();
            if (!cell.isBlank()) {
                try { result.add(Double.parseDouble(cell)); }
                catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (DateTimeFormatter fmt : FMT_IN) {
            try { return LocalDate.parse(raw, fmt); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private String safeGet(List<String> row, int idx) {
        return (idx >= 0 && idx < row.size() && row.get(idx) != null)
                ? row.get(idx) : "";
    }
}