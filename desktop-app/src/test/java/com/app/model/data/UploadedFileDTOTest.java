package com.app.model.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para UploadedFileDTO (historial de cargas de archivos).
 * Verifica campos, tipos de archivo soportados y estados del sistema.
 */
@DisplayName("UploadedFileDTO - Registro de archivo cargado")
class UploadedFileDTOTest {

    private static final LocalDateTime UPLOAD_TIME = LocalDateTime.of(2024, 3, 15, 10, 30, 0);

    // ── Construcción correcta ─────────────────────────────────────────────

    @Test
    @DisplayName("Constructor inicializa todos los campos correctamente")
    void constructorSetsAllFields() {
        UploadedFileDTO dto = new UploadedFileDTO(
                1L, "ventas_2024.csv", "CSV", "VENTAS",
                UPLOAD_TIME, "PROCESADO", 512L, 1500, "admin"
        );

        assertEquals(1L, dto.id());
        assertEquals("ventas_2024.csv", dto.fileName());
        assertEquals("CSV", dto.fileType());
        assertEquals("VENTAS", dto.dataType());
        assertEquals(UPLOAD_TIME, dto.uploadedAt());
        assertEquals("PROCESADO", dto.status());
        assertEquals(512L, dto.sizeKb());
        assertEquals(1500, dto.rowCount());
        assertEquals("admin", dto.uploadedBy());
    }

    // ── Tipos de archivo soportados ───────────────────────────────────────

    @Test
    @DisplayName("Tipo de archivo CSV es válido")
    void csvFileTypeIsSupported() {
        UploadedFileDTO dto = makeDTO("datos.csv", "CSV", "VENTAS", "PROCESADO", 100, 50L);
        assertEquals("CSV", dto.fileType());
        assertTrue(dto.fileName().endsWith(".csv"));
    }

    @Test
    @DisplayName("Tipo de archivo XLSX es válido")
    void xlsxFileTypeIsSupported() {
        UploadedFileDTO dto = makeDTO("compras_enero.xlsx", "XLSX", "COMPRAS", "PROCESADO", 250, 98L);
        assertEquals("XLSX", dto.fileType());
        assertTrue(dto.fileName().endsWith(".xlsx"));
    }

    // ── Tipos de datos soportados ─────────────────────────────────────────

    @Test
    @DisplayName("Tipo de datos VENTAS es válido")
    void ventasDataTypeIsSupported() {
        UploadedFileDTO dto = makeDTO("ventas_q1.csv", "CSV", "VENTAS", "PROCESADO", 800, 200L);
        assertEquals("VENTAS", dto.dataType());
    }

    @Test
    @DisplayName("Tipo de datos COMPRAS es válido")
    void comprasDataTypeIsSupported() {
        UploadedFileDTO dto = makeDTO("compras_q1.xlsx", "XLSX", "COMPRAS", "PROCESADO", 400, 150L);
        assertEquals("COMPRAS", dto.dataType());
    }

    // ── Estados del sistema ───────────────────────────────────────────────

    @Test
    @DisplayName("Estado PROCESADO es válido")
    void procesadoStatusIsValid() {
        UploadedFileDTO dto = makeDTO("archivo.csv", "CSV", "VENTAS", "PROCESADO", 100, 50L);
        assertEquals("PROCESADO", dto.status());
    }

    @Test
    @DisplayName("Estado ERROR es válido")
    void errorStatusIsValid() {
        UploadedFileDTO dto = makeDTO("malo.csv", "CSV", "VENTAS", "ERROR", -1, 10L);
        assertEquals("ERROR", dto.status());
        assertEquals(-1, dto.rowCount()); // -1 cuando hubo error
    }

    @Test
    @DisplayName("Estado PENDIENTE es válido")
    void pendienteStatusIsValid() {
        UploadedFileDTO dto = makeDTO("pendiente.xlsx", "XLSX", "COMPRAS", "PENDIENTE", 0, 75L);
        assertEquals("PENDIENTE", dto.status());
    }

    // ── Metadatos del archivo ─────────────────────────────────────────────

    @Test
    @DisplayName("ID único identifica el archivo")
    void idIsUnique() {
        UploadedFileDTO dto1 = makeDTO(1L, "archivo1.csv", "CSV", "VENTAS", "PROCESADO", 100, 50L);
        UploadedFileDTO dto2 = makeDTO(2L, "archivo2.csv", "CSV", "VENTAS", "PROCESADO", 100, 50L);

        assertNotEquals(dto1.id(), dto2.id());
    }

    @Test
    @DisplayName("Tamaño en KB es correcto")
    void sizeKbIsCorrect() {
        UploadedFileDTO dto = makeDTO("grande.xlsx", "XLSX", "VENTAS", "PROCESADO", 5000, 10240L);
        assertEquals(10240L, dto.sizeKb()); // ~10 MB
    }

    @Test
    @DisplayName("Contador de filas es correcto para archivo grande")
    void rowCountIsCorrectForLargeFile() {
        UploadedFileDTO dto = makeDTO("anual.csv", "CSV", "VENTAS", "PROCESADO", 50000, 8192L);
        assertEquals(50000, dto.rowCount());
    }

    @Test
    @DisplayName("uploadedAt registra la fecha y hora de carga")
    void uploadedAtIsCorrect() {
        LocalDateTime specificTime = LocalDateTime.of(2024, 12, 31, 23, 59, 59);
        UploadedFileDTO dto = new UploadedFileDTO(
                99L, "fin_año.csv", "CSV", "VENTAS",
                specificTime, "PROCESADO", 100L, 365, "usuario1"
        );
        assertEquals(specificTime, dto.uploadedAt());
        assertEquals(2024, dto.uploadedAt().getYear());
        assertEquals(12, dto.uploadedAt().getMonthValue());
    }

    @Test
    @DisplayName("uploadedBy identifica el usuario que realizó la carga")
    void uploadedByIdentifiesUser() {
        UploadedFileDTO dto = makeDTO("reporte.csv", "CSV", "VENTAS", "PROCESADO", 100, 50L, "gerente");
        assertEquals("gerente", dto.uploadedBy());
    }

    // ── Caso de uso: dataset de ventas sintético ──────────────────────────

    @Test
    @DisplayName("Escenario completo: carga exitosa de datos de ventas anuales")
    void syntheticAnnualSalesUploadScenario() {
        LocalDateTime uploadTime = LocalDateTime.of(2024, 6, 1, 9, 0, 0);
        UploadedFileDTO dto = new UploadedFileDTO(
                101L, "ventas_2023_completo.csv", "CSV", "VENTAS",
                uploadTime, "PROCESADO", 2048L, 12000, "admin"
        );

        assertEquals(101L, dto.id());
        assertEquals("CSV", dto.fileType());
        assertEquals("VENTAS", dto.dataType());
        assertEquals("PROCESADO", dto.status());
        assertEquals(12000, dto.rowCount()); // ~1000 filas/mes * 12 meses
        assertTrue(dto.sizeKb() > 0);
        assertNotNull(dto.uploadedAt());
    }

    @Test
    @DisplayName("Escenario: carga fallida retorna rowCount = -1")
    void failedUploadReturnsMinusOneRowCount() {
        UploadedFileDTO dto = makeDTO("corrupto.xlsx", "XLSX", "COMPRAS", "ERROR", -1, 512L);
        assertEquals(-1, dto.rowCount());
        assertEquals("ERROR", dto.status());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private UploadedFileDTO makeDTO(String name, String type, String dataType,
                                    String status, int rows, long sizeKb) {
        return new UploadedFileDTO(1L, name, type, dataType, UPLOAD_TIME, status, sizeKb, rows, "admin");
    }

    private UploadedFileDTO makeDTO(String name, String type, String dataType,
                                    String status, int rows, long sizeKb, String user) {
        return new UploadedFileDTO(1L, name, type, dataType, UPLOAD_TIME, status, sizeKb, rows, user);
    }

    private UploadedFileDTO makeDTO(long id, String name, String type, String dataType,
                                    String status, int rows, long sizeKb) {
        return new UploadedFileDTO(id, name, type, dataType, UPLOAD_TIME, status, sizeKb, rows, "admin");
    }
}
