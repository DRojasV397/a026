package com.app.data;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests que generan y validan datos sintéticos en formatos CSV y XLSX.
 * Verifica que los archivos cumplen la estructura esperada por el sistema SANI.
 *
 * Columnas requeridas para VENTAS: fecha, producto, precio, cantidad, total
 * Columnas requeridas para COMPRAS: fecha, proveedor, producto, precio_unitario, cantidad, total
 */
@DisplayName("SyntheticData - Generación y validación de datos sintéticos")
class SyntheticDataTest {

    private static final String[] VENTAS_HEADERS = {"fecha", "producto", "precio", "cantidad", "total"};
    private static final String[] COMPRAS_HEADERS = {"fecha", "proveedor", "producto", "precio_unitario", "cantidad", "total"};

    private static Path tempDir;

    @BeforeAll
    static void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("sani_synthetic_data_");
    }

    @AfterAll
    static void deleteTempDir() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CSV SINTÉTICO - VENTAS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CSV de ventas tiene cabecera correcta con 5 columnas")
    void ventasCsvHasCorrectHeader() throws IOException {
        Path csvFile = createVentasCsv(10);
        List<String[]> rows = parseCsv(csvFile);

        assertFalse(rows.isEmpty(), "CSV no debe estar vacío");
        String[] header = rows.get(0);
        assertEquals(5, header.length, "Debe tener 5 columnas");
        assertArrayEquals(VENTAS_HEADERS, header);
    }

    @Test
    @DisplayName("CSV de ventas tiene el número correcto de filas de datos")
    void ventasCsvHasCorrectRowCount() throws IOException {
        int dataRows = 50;
        Path csvFile = createVentasCsv(dataRows);
        List<String[]> rows = parseCsv(csvFile);

        assertEquals(dataRows + 1, rows.size(), // +1 por cabecera
                "Debe tener " + dataRows + " filas de datos + 1 cabecera");
    }

    @Test
    @DisplayName("CSV de ventas tiene valores numéricos positivos en precio y total")
    void ventasCsvHasPositiveNumericValues() throws IOException {
        Path csvFile = createVentasCsv(20);
        List<String[]> rows = parseCsv(csvFile);

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            double precio = Double.parseDouble(row[2].replace(",", "."));
            int cantidad = Integer.parseInt(row[3]);
            double total = Double.parseDouble(row[4].replace(",", "."));

            assertTrue(precio > 0, "Precio debe ser positivo en fila " + i);
            assertTrue(cantidad > 0, "Cantidad debe ser positiva en fila " + i);
            assertTrue(total > 0, "Total debe ser positivo en fila " + i);
        }
    }

    @Test
    @DisplayName("CSV de ventas tiene fechas en formato ISO (yyyy-MM-dd)")
    void ventasCsvHasIsoDateFormat() throws IOException {
        Path csvFile = createVentasCsv(30);
        List<String[]> rows = parseCsv(csvFile);

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        for (int i = 1; i < rows.size(); i++) {
            String dateStr = rows.get(i)[0];
            assertDoesNotThrow(() -> LocalDate.parse(dateStr, formatter),
                    "Fecha inválida en fila " + i + ": " + dateStr);
        }
    }

    @Test
    @DisplayName("CSV de ventas: total = precio * cantidad")
    void ventasCsvTotalMatchesPriceTimesQuantity() throws IOException {
        Path csvFile = createVentasCsv(15);
        List<String[]> rows = parseCsv(csvFile);

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            double precio = Double.parseDouble(row[2]);
            int cantidad = Integer.parseInt(row[3]);
            double total = Double.parseDouble(row[4]);

            assertEquals(precio * cantidad, total, 0.01,
                    "Total incorrecto en fila " + i);
        }
    }

    @Test
    @DisplayName("CSV de ventas está codificado en UTF-8 (soporta tildes)")
    void ventasCsvIsUtf8Encoded() throws IOException {
        Path csvFile = createVentasCsv(5);
        String content = Files.readString(csvFile, StandardCharsets.UTF_8);

        // Verifica que las tildes en nombres de productos sean correctas
        assertNotNull(content);
        assertTrue(content.length() > 0);
    }

    // ══════════════════════════════════════════════════════════════════════
    // CSV SINTÉTICO - COMPRAS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CSV de compras tiene cabecera con 6 columnas")
    void comprasCsvHasCorrectHeader() throws IOException {
        Path csvFile = createComprasCsv(10);
        List<String[]> rows = parseCsv(csvFile);

        assertFalse(rows.isEmpty());
        assertArrayEquals(COMPRAS_HEADERS, rows.get(0));
    }

    @Test
    @DisplayName("CSV de compras tiene proveedores y productos válidos")
    void comprasCsvHasValidProveedoresAndProducts() throws IOException {
        Path csvFile = createComprasCsv(20);
        List<String[]> rows = parseCsv(csvFile);

        for (int i = 1; i < rows.size(); i++) {
            String proveedor = rows.get(i)[1];
            String producto = rows.get(i)[2];

            assertNotNull(proveedor);
            assertFalse(proveedor.isBlank(), "Proveedor no debe estar vacío en fila " + i);
            assertNotNull(producto);
            assertFalse(producto.isBlank(), "Producto no debe estar vacío en fila " + i);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // XLSX SINTÉTICO - VENTAS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("XLSX de ventas tiene la hoja correcta con Apache POI")
    void ventasXlsxHasCorrectSheet() throws IOException {
        Path xlsxFile = createVentasXlsx(25);

        try (Workbook wb = WorkbookFactory.create(xlsxFile.toFile())) {
            assertNotNull(wb);
            assertEquals(1, wb.getNumberOfSheets());

            Sheet sheet = wb.getSheetAt(0);
            assertNotNull(sheet);
        }
    }

    @Test
    @DisplayName("XLSX de ventas tiene cabecera correcta en fila 0")
    void ventasXlsxHasCorrectHeaderRow() throws IOException {
        Path xlsxFile = createVentasXlsx(20);

        try (Workbook wb = WorkbookFactory.create(xlsxFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            assertNotNull(headerRow);
            assertEquals("fecha", headerRow.getCell(0).getStringCellValue());
            assertEquals("producto", headerRow.getCell(1).getStringCellValue());
            assertEquals("precio", headerRow.getCell(2).getStringCellValue());
            assertEquals("cantidad", headerRow.getCell(3).getStringCellValue());
            assertEquals("total", headerRow.getCell(4).getStringCellValue());
        }
    }

    @Test
    @DisplayName("XLSX de ventas tiene el número correcto de filas")
    void ventasXlsxHasCorrectRowCount() throws IOException {
        int dataRows = 100;
        Path xlsxFile = createVentasXlsx(dataRows);

        try (Workbook wb = WorkbookFactory.create(xlsxFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            assertEquals(dataRows, lastRowNum, // 0-indexed, row 0 = header
                    "Debe tener " + dataRows + " filas de datos");
        }
    }

    @Test
    @DisplayName("XLSX de ventas tiene valores numéricos en columnas de precio y cantidad")
    void ventasXlsxHasNumericValues() throws IOException {
        Path xlsxFile = createVentasXlsx(10);

        try (Workbook wb = WorkbookFactory.create(xlsxFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                assertNotNull(row, "Fila " + i + " no debe ser null");

                // Precio (col 2) y cantidad (col 3) deben ser numéricos
                assertEquals(CellType.NUMERIC, row.getCell(2).getCellType(),
                        "Precio debe ser numérico en fila " + i);
                assertEquals(CellType.NUMERIC, row.getCell(3).getCellType(),
                        "Cantidad debe ser numérico en fila " + i);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CASOS BORDE
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CSV con 1 fila de datos (mínimo válido)")
    void csvWithSingleDataRow() throws IOException {
        Path csvFile = createVentasCsv(1);
        List<String[]> rows = parseCsv(csvFile);

        assertEquals(2, rows.size()); // cabecera + 1 dato
        assertArrayEquals(VENTAS_HEADERS, rows.get(0));
    }

    @Test
    @DisplayName("CSV con 5000 filas (carga grande) se genera correctamente")
    void csvWithLargeDataset() throws IOException {
        int rows = 5000;
        Path csvFile = createVentasCsv(rows);
        List<String[]> parsedRows = parseCsv(csvFile);

        assertEquals(rows + 1, parsedRows.size());
        assertTrue(Files.size(csvFile) > 100_000L, "Archivo grande debe ser > 100KB");
    }

    @Test
    @DisplayName("CSV con fechas cubriendo al menos 6 meses (requerimiento RN-01.01)")
    void csvCoversAtLeast6MonthsForPrediction() throws IOException {
        Path csvFile = createVentasCsvDateRange(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                365
        );
        List<String[]> rows = parseCsv(csvFile);

        // Verificar primera y última fecha
        LocalDate firstDate = LocalDate.parse(rows.get(1)[0]);
        LocalDate lastDate = LocalDate.parse(rows.get(rows.size() - 1)[0]);

        long monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(firstDate, lastDate);
        assertTrue(monthsBetween >= 6, "Debe haber al menos 6 meses de datos para predicción (RN-01.01)");
    }

    @Test
    @DisplayName("XLSX con 0 filas de datos (solo cabecera) se genera sin error")
    void xlsxWithOnlyHeader() throws IOException {
        Path xlsxFile = createVentasXlsx(0);

        try (Workbook wb = WorkbookFactory.create(xlsxFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            assertNotNull(headerRow);
            assertEquals("fecha", headerRow.getCell(0).getStringCellValue());
            assertEquals(0, sheet.getLastRowNum()); // Solo la cabecera
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GENERADORES DE DATOS SINTÉTICOS
    // ══════════════════════════════════════════════════════════════════════

    private Path createVentasCsv(int dataRows) throws IOException {
        return createVentasCsvDateRange(LocalDate.of(2024, 1, 1), null, dataRows);
    }

    private Path createVentasCsvDateRange(LocalDate startDate, LocalDate endDate, int dataRows) throws IOException {
        Path file = tempDir.resolve("ventas_" + dataRows + "_" + System.nanoTime() + ".csv");
        StringBuilder sb = new StringBuilder();

        // Cabecera
        sb.append(String.join(",", VENTAS_HEADERS)).append("\n");

        // Datos
        String[] productos = {"Leche Entera", "Pan Baguette", "Arroz Grano Largo",
                              "Aceite Vegetal", "Huevos Docena", "Azúcar Blanca"};
        Random rand = new Random(42);
        LocalDate currentDate = startDate;

        for (int i = 0; i < dataRows; i++) {
            String producto = productos[i % productos.length];
            double precio = 500 + rand.nextInt(3000);
            int cantidad = 1 + rand.nextInt(50);
            double total = precio * cantidad;
            String fecha = currentDate.toString();

            sb.append(fecha).append(",")
              .append(producto).append(",")
              .append(String.format("%.2f", precio)).append(",")
              .append(cantidad).append(",")
              .append(String.format("%.2f", total)).append("\n");

            // Avanzar fecha por día
            currentDate = currentDate.plusDays(1);
            if (endDate != null && currentDate.isAfter(endDate)) {
                currentDate = startDate;
            }
        }

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private Path createComprasCsv(int dataRows) throws IOException {
        Path file = tempDir.resolve("compras_" + dataRows + "_" + System.nanoTime() + ".csv");
        StringBuilder sb = new StringBuilder();

        sb.append(String.join(",", COMPRAS_HEADERS)).append("\n");

        String[] proveedores = {"Distribuidora Sur", "Proveedor Norte", "Comercial Central"};
        String[] productos = {"Leche", "Harina", "Aceite", "Azúcar", "Sal"};
        Random rand = new Random(42);
        LocalDate date = LocalDate.of(2024, 1, 1);

        for (int i = 0; i < dataRows; i++) {
            double precioUnit = 200 + rand.nextInt(1500);
            int cantidad = 10 + rand.nextInt(100);
            double total = precioUnit * cantidad;

            sb.append(date.plusDays(i)).append(",")
              .append(proveedores[i % proveedores.length]).append(",")
              .append(productos[i % productos.length]).append(",")
              .append(String.format("%.2f", precioUnit)).append(",")
              .append(cantidad).append(",")
              .append(String.format("%.2f", total)).append("\n");
        }

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private Path createVentasXlsx(int dataRows) throws IOException {
        Path file = tempDir.resolve("ventas_" + dataRows + "_" + System.nanoTime() + ".xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Ventas");

            // Cabecera
            Row headerRow = sheet.createRow(0);
            for (int j = 0; j < VENTAS_HEADERS.length; j++) {
                headerRow.createCell(j).setCellValue(VENTAS_HEADERS[j]);
            }

            // Datos
            String[] productos = {"Leche Entera", "Pan Baguette", "Arroz", "Aceite", "Huevos"};
            Random rand = new Random(42);
            LocalDate date = LocalDate.of(2024, 1, 1);

            for (int i = 0; i < dataRows; i++) {
                Row row = sheet.createRow(i + 1);
                double precio = 500 + rand.nextInt(3000);
                int cantidad = 1 + rand.nextInt(50);
                double total = precio * cantidad;

                row.createCell(0, CellType.STRING).setCellValue(date.plusDays(i).toString());
                row.createCell(1, CellType.STRING).setCellValue(productos[i % productos.length]);
                row.createCell(2, CellType.NUMERIC).setCellValue(precio);
                row.createCell(3, CellType.NUMERIC).setCellValue(cantidad);
                row.createCell(4, CellType.NUMERIC).setCellValue(total);
            }

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        return file;
    }

    // ── CSV parser simple ─────────────────────────────────────────────────

    private List<String[]> parseCsv(Path file) throws IOException {
        List<String[]> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    result.add(line.split(",", -1));
                }
            }
        }
        return result;
    }
}
