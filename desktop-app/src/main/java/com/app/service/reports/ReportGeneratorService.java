package com.app.service.reports;

// OpenPDF (com.lowagie.text) — explicit imports to avoid conflicts with Apache POI
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

// Apache POI — use XSSF-specific types (XSSFRow, XSSFCell, XSSFFont) to avoid name clashes
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Genera archivos PDF (OpenPDF) y Excel (Apache POI) a partir de datos
 * tabulares obtenidos de la API de reportes.
 */
public class ReportGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(ReportGeneratorService.class);
    private static final DateTimeFormatter FMT_FILE    = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter FMT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public record GeneratedFile(Path path, long sizeKb) {}

    // ── Directory ─────────────────────────────────────────────────────────────

    public Path getReportsDir() {
        return Paths.get(System.getProperty("user.home"), "Documents", "Herradura", "Reportes");
    }

    // ── Entry points ──────────────────────────────────────────────────────────

    /**
     * Genera un reporte simple (una sola tabla de datos).
     */
    public GeneratedFile generate(String format, String name, String tipo,
                                   String dateRange, List<Map<String, Object>> rows) throws IOException {
        Path dir = getReportsDir();
        Files.createDirectories(dir);

        String safeName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String timestamp = LocalDateTime.now().format(FMT_FILE);
        String ext = format.equalsIgnoreCase("PDF") ? ".pdf" : ".xlsx";
        Path filePath = dir.resolve(safeName + "_" + timestamp + ext);

        if (format.equalsIgnoreCase("PDF")) {
            generatePdf(filePath, name, tipo, dateRange, rows);
        } else {
            generateExcel(filePath, name, tipo, dateRange, rows);
        }

        long sizeKb = Files.size(filePath) / 1024;
        logger.info("Reporte generado: {} ({} KB)", filePath.getFileName(), sizeKb);
        return new GeneratedFile(filePath, Math.max(1, sizeKb));
    }

    /**
     * Genera un informe de rentabilidad completo con tres secciones:
     * mensual, por categoría y por producto.
     * PDF: secciones consecutivas. Excel: tres hojas.
     */
    public GeneratedFile generateRentabilidadCompleto(
            String format, String name, String dateRange,
            List<Map<String, Object>> mensual,
            List<Map<String, Object>> porCategoria,
            List<Map<String, Object>> porProducto) throws IOException {

        Path dir = getReportsDir();
        Files.createDirectories(dir);

        String safeName  = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String timestamp = LocalDateTime.now().format(FMT_FILE);
        String ext       = format.equalsIgnoreCase("PDF") ? ".pdf" : ".xlsx";
        Path filePath    = dir.resolve(safeName + "_" + timestamp + ext);

        if (format.equalsIgnoreCase("PDF")) {
            generatePdfMultiSection(filePath, name, dateRange, mensual, porCategoria, porProducto);
        } else {
            generateExcelMultiSheet(filePath, name, dateRange, mensual, porCategoria, porProducto);
        }

        long sizeKb = Files.size(filePath) / 1024;
        logger.info("Informe completo generado: {} ({} KB)", filePath.getFileName(), sizeKb);
        return new GeneratedFile(filePath, Math.max(1, sizeKb));
    }

    // ── Column definitions per report type ───────────────────────────────────

    private String[] getHeaders(String tipo) {
        return switch (tipo.toLowerCase()) {
            case "ventas"                  -> new String[]{"Período", "Total Ventas ($)", "Transacciones"};
            case "compras"                 -> new String[]{"Período", "Total Compras ($)", "Transacciones"};
            case "rentabilidad"            -> new String[]{"Período", "Ingresos ($)", "Costos ($)", "Utilidad ($)", "Margen %"};
            case "rentabilidad_categoria"  -> new String[]{"Categoría", "Ingresos ($)", "Costos ($)", "Utilidad ($)", "Margen %"};
            case "rentabilidad_producto"   -> new String[]{"Producto", "Categoría", "Ingresos ($)", "Costos ($)", "Utilidad ($)", "Margen %"};
            case "productos"               -> new String[]{"Producto", "Categoría", "Cant. Vendida", "Ingresos Generados ($)"};
            default                        -> new String[]{"Datos"};
        };
    }

    private String[] getKeys(String tipo) {
        return switch (tipo.toLowerCase()) {
            case "ventas"                  -> new String[]{"periodo", "total", "cantidad"};
            case "compras"                 -> new String[]{"periodo", "total", "cantidad"};
            case "rentabilidad"            -> new String[]{"periodo", "ingresos", "costos", "utilidad", "margen"};
            case "rentabilidad_categoria"  -> new String[]{"categoria", "ingresos", "costos", "utilidad", "margen"};
            case "rentabilidad_producto"   -> new String[]{"nombre", "categoria", "ingresos", "costos", "utilidad", "margen"};
            case "productos"               -> new String[]{"nombre", "categoria", "cantidad_vendida", "ingresos_generados"};
            default                        -> new String[]{"value"};
        };
    }

    // ── PDF Generation ────────────────────────────────────────────────────────

    private void generatePdf(Path filePath, String name, String tipo,
                              String dateRange, List<Map<String, Object>> rows) throws IOException {
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(filePath.toFile()));

            // Footer via page event
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    try {
                        PdfContentByte cb = w.getDirectContent();
                        BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false);
                        cb.beginText();
                        cb.setFontAndSize(bf, 8);
                        cb.setColorFill(Color.GRAY);
                        cb.showTextAligned(Element.ALIGN_CENTER,
                                "Herradura BI  \u00B7  P\u00E1gina " + w.getPageNumber(),
                                d.getPageSize().getWidth() / 2, 20, 0);
                        cb.endText();
                    } catch (Exception ignored) { }
                }
            });

            doc.open();

            // ── Header block ──────────────────────────────────────────────────
            Color brandColor = new Color(0x1A, 0x1A, 0x2E);
            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, brandColor);
            Font subFont   = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY);
            Font metaFont  = new Font(Font.HELVETICA,  9, Font.NORMAL, Color.GRAY);

            Paragraph title = new Paragraph(name, titleFont);
            title.setAlignment(Element.ALIGN_LEFT);
            title.setSpacingAfter(4);
            doc.add(title);

            doc.add(new Paragraph("Tipo: " + capitalize(tipo) + "   \u00B7   Per\u00EDodo: " + dateRange, subFont));
            doc.add(new Paragraph("Generado: " + LocalDateTime.now().format(FMT_DISPLAY), metaFont));
            doc.add(new Paragraph(" "));

            // ── Table ─────────────────────────────────────────────────────────
            String[] headers = getHeaders(tipo);
            String[] keys    = getKeys(tipo);

            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100);
            table.setSpacingBefore(6);

            Color headerBg  = new Color(0x3B, 0x8E, 0xA5);
            Font  headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(headerBg);
                cell.setPadding(6);
                cell.setBorderColor(headerBg);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            Font  cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
            Color evenBg   = new Color(0xF0, 0xF7, 0xFF);
            Color borderC  = new Color(0xDD, 0xDD, 0xDD);
            boolean even   = false;

            List<Map<String, Object>> safeRows = rows != null ? rows : List.of();
            for (Map<String, Object> row : safeRows) {
                Color bg = even ? evenBg : Color.WHITE;
                even = !even;
                for (String key : keys) {
                    PdfPCell cell = new PdfPCell(new Phrase(objToStr(row.get(key)), cellFont));
                    cell.setBackgroundColor(bg);
                    cell.setPadding(5);
                    cell.setBorderColor(borderC);
                    table.addCell(cell);
                }
            }

            if (safeRows.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase(
                        "Sin datos para el período seleccionado.",
                        new Font(Font.HELVETICA, 9, Font.ITALIC, Color.GRAY)));
                empty.setColspan(headers.length);
                empty.setPadding(8);
                empty.setBorderColor(borderC);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(empty);
            }

            doc.add(table);

        } catch (DocumentException e) {
            throw new IOException("Error generando PDF: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    // ── Excel Generation ──────────────────────────────────────────────────────

    private void generateExcel(Path filePath, String name, String tipo,
                                String dateRange, List<Map<String, Object>> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFCellStyle headerStyle = buildExcelHeaderStyle(wb);
            XSSFCellStyle evenStyle   = buildExcelEvenStyle(wb);

            String rawName   = capitalize(tipo);
            String sheetName = rawName.length() > 31 ? rawName.substring(0, 31) : rawName;
            writeExcelSheet(wb, sheetName, tipo, rows, dateRange, name, headerStyle, evenStyle);

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                wb.write(fos);
            }
        }
    }

    // ── PDF Multi-section (Rentabilidad completo) ─────────────────────────────

    private void generatePdfMultiSection(
            Path filePath, String name, String dateRange,
            List<Map<String, Object>> mensual,
            List<Map<String, Object>> porCategoria,
            List<Map<String, Object>> porProducto) throws IOException {

        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(filePath.toFile()));
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    try {
                        PdfContentByte cb = w.getDirectContent();
                        BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false);
                        cb.beginText();
                        cb.setFontAndSize(bf, 8);
                        cb.setColorFill(Color.GRAY);
                        cb.showTextAligned(Element.ALIGN_CENTER,
                                "Herradura BI  \u00B7  P\u00E1gina " + w.getPageNumber(),
                                d.getPageSize().getWidth() / 2, 20, 0);
                        cb.endText();
                    } catch (Exception ignored) { }
                }
            });
            doc.open();

            Color brandColor = new Color(0x1A, 0x1A, 0x2E);
            Font titleFont   = new Font(Font.HELVETICA, 18, Font.BOLD,  brandColor);
            Font subFont     = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY);
            Font metaFont    = new Font(Font.HELVETICA,  9, Font.NORMAL, Color.GRAY);
            Font sectionFont = new Font(Font.HELVETICA, 13, Font.BOLD,  new Color(0x3B, 0x8E, 0xA5));

            // ── Document header ───────────────────────────────────────────────
            Paragraph title = new Paragraph(name, titleFont);
            title.setSpacingAfter(4);
            doc.add(title);
            doc.add(new Paragraph("Tipo: Rentabilidad completa   \u00B7   Per\u00EDodo: " + dateRange, subFont));
            doc.add(new Paragraph("Generado: " + LocalDateTime.now().format(FMT_DISPLAY), metaFont));
            doc.add(new Paragraph(" "));

            // ── Section 1: Mensual ────────────────────────────────────────────
            doc.add(new Paragraph("1. Rentabilidad Mensual", sectionFont));
            doc.add(new Paragraph(" "));
            doc.add(buildPdfTable(mensual, "rentabilidad"));
            doc.add(new Paragraph(" "));

            // ── Section 2: Por Categoría ──────────────────────────────────────
            doc.add(new Paragraph("2. Rentabilidad por Categor\u00EDa", sectionFont));
            doc.add(new Paragraph(" "));
            doc.add(buildPdfTable(porCategoria, "rentabilidad_categoria"));
            doc.add(new Paragraph(" "));

            // ── Section 3: Por Producto ───────────────────────────────────────
            doc.add(new Paragraph("3. Rentabilidad por Producto (Top 50)", sectionFont));
            doc.add(new Paragraph(" "));
            doc.add(buildPdfTable(porProducto, "rentabilidad_producto"));

        } catch (DocumentException e) {
            throw new IOException("Error generando PDF multi-sección: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    /** Construye una PdfPTable lista para insertar en el documento. */
    private PdfPTable buildPdfTable(List<Map<String, Object>> rows, String tipo) {
        String[] headers = getHeaders(tipo);
        String[] keys    = getKeys(tipo);

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);

        Color headerBg  = new Color(0x3B, 0x8E, 0xA5);
        Font  headerFont = new Font(Font.HELVETICA, 10, Font.BOLD,   Color.WHITE);
        Font  cellFont   = new Font(Font.HELVETICA,  9, Font.NORMAL, Color.BLACK);
        Color evenBg     = new Color(0xF0, 0xF7, 0xFF);
        Color borderC    = new Color(0xDD, 0xDD, 0xDD);

        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setPadding(6);
            cell.setBorderColor(headerBg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        boolean even = false;
        List<Map<String, Object>> safeRows = rows != null ? rows : List.of();
        for (Map<String, Object> row : safeRows) {
            Color bg = even ? evenBg : Color.WHITE;
            even = !even;
            for (String key : keys) {
                PdfPCell cell = new PdfPCell(new Phrase(objToStr(row.get(key)), cellFont));
                cell.setBackgroundColor(bg);
                cell.setPadding(5);
                cell.setBorderColor(borderC);
                table.addCell(cell);
            }
        }

        if (safeRows.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase(
                    "Sin datos para el per\u00EDodo seleccionado.",
                    new Font(Font.HELVETICA, 9, Font.ITALIC, Color.GRAY)));
            empty.setColspan(headers.length);
            empty.setPadding(8);
            empty.setBorderColor(borderC);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(empty);
        }
        return table;
    }

    // ── Excel Multi-sheet (Rentabilidad completo) ─────────────────────────────

    private void generateExcelMultiSheet(
            Path filePath, String name, String dateRange,
            List<Map<String, Object>> mensual,
            List<Map<String, Object>> porCategoria,
            List<Map<String, Object>> porProducto) throws IOException {

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // Shared styles
            XSSFCellStyle headerStyle = buildExcelHeaderStyle(wb);
            XSSFCellStyle evenStyle   = buildExcelEvenStyle(wb);

            writeExcelSheet(wb, "Mensual",          "rentabilidad",           mensual,      dateRange, name, headerStyle, evenStyle);
            writeExcelSheet(wb, "Por Categor\u00EDa", "rentabilidad_categoria", porCategoria, dateRange, name, headerStyle, evenStyle);
            writeExcelSheet(wb, "Por Producto",     "rentabilidad_producto",  porProducto,  dateRange, name, headerStyle, evenStyle);

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                wb.write(fos);
            }
        }
    }

    private void writeExcelSheet(XSSFWorkbook wb, String sheetName, String tipo,
                                  List<Map<String, Object>> rows, String dateRange,
                                  String reportName,
                                  XSSFCellStyle headerStyle, XSSFCellStyle evenStyle) {
        XSSFSheet sheet  = wb.createSheet(sheetName);
        String[]  headers = getHeaders(tipo);
        String[]  keys    = getKeys(tipo);

        // Info row
        XSSFRow infoRow = sheet.createRow(0);
        infoRow.createCell(0).setCellValue(reportName + "  \u2014  " + sheetName + "  \u2014  " + dateRange);

        // Header row
        XSSFRow headerRow = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            XSSFCell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        List<Map<String, Object>> safeRows = rows != null ? rows : List.of();
        int rowNum = 2;
        for (Map<String, Object> row : safeRows) {
            XSSFRow dataRow = sheet.createRow(rowNum);
            for (int i = 0; i < keys.length; i++) {
                XSSFCell cell = dataRow.createCell(i);
                Object val = row.get(keys[i]);
                if (val instanceof Number n) cell.setCellValue(n.doubleValue());
                else cell.setCellValue(val != null ? val.toString() : "");
                if (rowNum % 2 == 0) cell.setCellStyle(evenStyle);
            }
            rowNum++;
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 512);
        }
    }

    private XSSFCellStyle buildExcelHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x3B,(byte)0x8E,(byte)0xA5}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(new XSSFColor(new byte[]{(byte)0xFF,(byte)0xFF,(byte)0xFF}, null));
        s.setFont(f);
        return s;
    }

    private XSSFCellStyle buildExcelEvenStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xF0,(byte)0xF7,(byte)0xFF}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String objToStr(Object val) {
        if (val == null) return "";
        if (val instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
                return String.valueOf(d.longValue());
            return String.format("%.2f", d);
        }
        return val.toString();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s == null ? "" : s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
