package com.app.service.data;

import com.app.model.data.CleaningReport;
import com.app.model.data.ValidationReport;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CU-03: Limpiar datos automáticamente.
 * Recibe las filas originales (fila 0 = cabecera) y el reporte de validación.
 * Opera en orden exacto del flujo básico del CU.
 */
public class CleaningService {

    /**
     * Ejecuta el pipeline de limpieza completo.
     *
     * @param rows       filas del archivo; rows.get(0) es la cabecera
     * @param validation resultado previo de CU-02
     * @param tipo       "Ventas" o "Compras"
     * @return CleaningReport con métricas y filas limpias
     */
    public CleaningReport clean(List<List<String>> rows,
                                ValidationReport validation,
                                String tipo) {
        List<String> log      = new ArrayList<>();
        List<String> headers  = rows.get(0).stream()
                .map(DataSchema::normalize).toList();
        int originalCount     = rows.size() - 1; // sin cabecera

        // Trabajamos con copia mutable de las filas de datos
        List<List<String>> data = new ArrayList<>(rows.subList(1, rows.size()));

        log.add("[CU-03] Inicio de limpieza. Registros originales: " + originalCount);

        // ── Paso 1 — eliminar filas con >50% vacíos (FA-04 de CU-02) ─────────
        int totalFields     = headers.size();
        List<Integer> emptyRows = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            List<String> row = data.get(i);
            long empties = row.stream().filter(c -> c == null || c.isBlank()).count();
            if (totalFields > 0 && (double) empties / totalFields > 0.5)
                emptyRows.add(i);
        }
        // Eliminar en orden inverso para no romper índices
        for (int i = emptyRows.size() - 1; i >= 0; i--)
            data.remove((int) emptyRows.get(i));
        int discardedEmpty = emptyRows.size();
        log.add("[CU-03] Paso 1: filas descartadas por >50% vacíos → " + discardedEmpty);

        // ── Paso 2 — eliminar duplicados (conserva el más reciente) ──────────
        // Clave de duplicado: todas las celdas concatenadas
        int beforeDedup = data.size();
        LinkedHashMap<String, List<String>> dedupMap = new LinkedHashMap<>();
        for (List<String> row : data) {
            String key = String.join("|", row);
            dedupMap.put(key, row); // sobreescribe → conserva el último (más reciente en orden)
        }
        data = new ArrayList<>(dedupMap.values());
        int duplicatesRemoved = beforeDedup - data.size();
        log.add("[CU-03] Paso 2: duplicados eliminados → " + duplicatesRemoved);

        // ── Paso 3 — detectar outliers ±3 desviaciones estándar ──────────────
        List<Integer> idxNumericas = DataSchema.getNumericas(tipo).stream()
                .map(col -> headers.indexOf(col))
                .filter(i -> i >= 0)
                .toList();

        int outliersFlagged = 0;
        Set<Integer> outlierRowIndices = new HashSet<>();

        for (int colIdx : idxNumericas) {
            String colName = headers.get(colIdx);
            List<Double> nums = extractNumericColumn(data, colIdx);
            if (nums.size() < 2) continue;

            double mean   = nums.stream().mapToDouble(d -> d).average().orElse(0);
            double stdDev = stdDev(nums, mean);
            double lower  = mean - 3 * stdDev;
            double upper  = mean + 3 * stdDev;

            for (int i = 0; i < data.size(); i++) {
                String cell = safeGet(data.get(i), colIdx)
                        .replace(",", "").replace("$", "").trim();
                if (cell.isBlank()) continue;
                try {
                    double val = Double.parseDouble(cell);
                    if (val < lower || val > upper) {
                        outlierRowIndices.add(i);
                        log.add(String.format("[CU-03] Outlier fila %d col '%s': %.2f (rango [%.2f, %.2f])",
                                i + 2, colName, val, lower, upper));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        outliersFlagged = outlierRowIndices.size();
        log.add("[CU-03] Paso 3: filas con valores atípicos señaladas → " + outliersFlagged
                + " (NO eliminadas, requieren revisión manual)");

        // ── Paso 4 — imputar valores faltantes con mediana de la columna ─────
        int imputedValues = 0;
        for (int colIdx : idxNumericas) {
            List<Double> nums    = extractNumericColumn(data, colIdx);
            if (nums.isEmpty()) continue;
            double median        = median(nums);

            for (List<String> row : data) {
                String cell = safeGet(row, colIdx).trim();
                if (cell.isBlank()) {
                    if (colIdx < row.size())
                        row.set(colIdx, String.format("%.2f", median));
                    else
                        while (row.size() <= colIdx) row.add(String.format("%.2f", median));
                    imputedValues++;
                }
            }
        }
        log.add("[CU-03] Paso 4: valores faltantes imputados con mediana → " + imputedValues);

        // ── Paso 5 — estandarizar strings: trim + normalizar espacios ─────────
        for (List<String> row : data) {
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);
                if (cell != null)
                    row.set(i, cell.trim().replaceAll("\\s+", " "));
            }
        }
        log.add("[CU-03] Paso 5: strings estandarizados (trim + espacios normalizados)");

        // ── Verificar umbral 70% (FA-01 CU-03) ───────────────────────────────
        double retentionPercent = originalCount > 0
                ? (double) data.size() / originalCount * 100 : 100.0;
        boolean meetsThreshold  = retentionPercent >= 70.0;

        log.add(String.format("[CU-03] Limpieza completada. Procesados: %d, Duplicados: %d, " +
                        "Vacíos descartados: %d, Valores imputados: %d",
                data.size(), duplicatesRemoved, discardedEmpty, imputedValues));
        log.add(String.format("[CU-03] Retención: %.1f%% %s",
                retentionPercent, meetsThreshold ? "✔" : "⚠ MENOR AL 70% — requiere confirmación"));

        log.forEach(System.out::println);

        // Reconstruir con cabecera en posición 0
        List<List<String>> cleanWithHeader = new ArrayList<>();
        cleanWithHeader.add(rows.get(0)); // cabecera original
        cleanWithHeader.addAll(data);

        return new CleaningReport(
                originalCount, data.size(),
                duplicatesRemoved, discardedEmpty,
                outliersFlagged, imputedValues,
                retentionPercent, meetsThreshold,
                cleanWithHeader, log
        );
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

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

    private double stdDev(List<Double> nums, double mean) {
        double variance = nums.stream()
                .mapToDouble(n -> Math.pow(n - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    private double median(List<Double> nums) {
        List<Double> sorted = nums.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0
                : sorted.get(mid);
    }

    private String safeGet(List<String> row, int idx) {
        return (idx >= 0 && idx < row.size() && row.get(idx) != null)
                ? row.get(idx) : "";
    }
}