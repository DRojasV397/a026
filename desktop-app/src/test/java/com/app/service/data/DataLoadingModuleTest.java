package com.app.service.data;

import com.app.model.data.*;
import com.app.model.data.api.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del módulo de carga de datos (sin llamadas HTTP).
 * Valida: creación de archivos sintéticos, estructura multipart,
 * detección de tipo MIME y modelos de datos.
 */
@DisplayName("DataLoadingModule - Módulo de carga de datos")
class DataLoadingModuleTest {

    private static Path tempDir;

    @BeforeAll
    static void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sani_data_loading_");
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DETECCIÓN DE TIPO MIME (lógica de DataApiService)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Archivo .csv detecta MIME type text/csv")
    void csvFileDetectsMimeType() {
        String filename = "ventas_2024.csv";
        String mimeType = detectMimeType(filename);
        assertEquals("text/csv", mimeType);
    }

    @Test
    @DisplayName("Archivo .xlsx detecta MIME type application/vnd.openxmlformats...")
    void xlsxFileDetectsMimeType() {
        String filename = "compras_enero.xlsx";
        String mimeType = detectMimeType(filename);
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", mimeType);
    }

    @Test
    @DisplayName("Archivo CSV mayúsculas detecta MIME type correcto")
    void csvUppercaseDetectsMimeType() {
        String filename = "datos.CSV";
        // La lógica usa endsWith(".csv") por lo que mayúsculas NO matchean
        // Este test documenta el comportamiento actual
        String mimeType = detectMimeType(filename);
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", mimeType);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ESTRUCTURA DEL CUERPO MULTIPART (buildMultipartBody logic)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Cuerpo multipart contiene boundary correcto")
    void multipartBodyContainsBoundary() {
        String boundary = "test-boundary-123";
        byte[] fileBytes = "fecha,monto\n2024-01-01,1000".getBytes();
        byte[] body = buildMultipartBody(boundary, "test.csv", "text/csv", fileBytes);

        String bodyStr = new String(body, StandardCharsets.ISO_8859_1);
        assertTrue(bodyStr.contains("--" + boundary));
        assertTrue(bodyStr.contains("--" + boundary + "--"));
    }

    @Test
    @DisplayName("Cuerpo multipart contiene Content-Disposition con filename")
    void multipartBodyContainsContentDisposition() {
        String boundary = "abc123";
        byte[] fileBytes = "data".getBytes();
        byte[] body = buildMultipartBody(boundary, "ventas.csv", "text/csv", fileBytes);

        String bodyStr = new String(body, StandardCharsets.ISO_8859_1);
        assertTrue(bodyStr.contains("Content-Disposition: form-data"));
        assertTrue(bodyStr.contains("name=\"file\""));
        assertTrue(bodyStr.contains("filename=\"ventas.csv\""));
    }

    @Test
    @DisplayName("Cuerpo multipart contiene Content-Type del archivo")
    void multipartBodyContainsContentType() {
        String boundary = "boundary-xyz";
        byte[] fileBytes = "data".getBytes();
        byte[] body = buildMultipartBody(boundary, "archivo.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileBytes);

        String bodyStr = new String(body, StandardCharsets.ISO_8859_1);
        assertTrue(bodyStr.contains("Content-Type: application/vnd.openxmlformats"));
    }

    @Test
    @DisplayName("Cuerpo multipart tiene longitud correcta")
    void multipartBodyHasCorrectLength() {
        String boundary = "b1";
        byte[] fileContent = "hello,world\n2024-01-01,1000".getBytes();
        byte[] body = buildMultipartBody(boundary, "test.csv", "text/csv", fileContent);

        // El cuerpo debe ser mayor que el contenido del archivo
        assertTrue(body.length > fileContent.length);
        // El cuerpo debe contener los bytes del archivo
        assertTrue(containsBytes(body, fileContent));
    }

    @Test
    @DisplayName("Cuerpo multipart con archivo CSV sintético completo")
    void multipartBodyWithSyntheticCsvFile() throws IOException {
        Path csvFile = createSyntheticCsvFile(100);
        byte[] fileBytes = Files.readAllBytes(csvFile);
        String boundary = UUID.randomUUID().toString();

        byte[] body = buildMultipartBody(boundary, csvFile.getFileName().toString(),
                "text/csv", fileBytes);

        String bodyStr = new String(body, StandardCharsets.ISO_8859_1);
        assertTrue(bodyStr.contains("--" + boundary + "--"));
        assertTrue(body.length > fileBytes.length);
        // Verificar que el archivo CSV está en el cuerpo
        assertTrue(containsBytes(body, "fecha,producto,precio".getBytes()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // VALIDACIÓN DE REQUEST DTOs
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ValidateRequestDTO serializa correctamente para VENTAS")
    void validateRequestForVentasSerializes() {
        Gson gson = new Gson();
        ValidateRequestDTO req = new ValidateRequestDTO("upload-123", "ventas");
        String json = gson.toJson(req);

        assertTrue(json.contains("\"upload_id\":\"upload-123\""));
        assertTrue(json.contains("\"data_type\":\"ventas\""));
    }

    @Test
    @DisplayName("ValidateRequestDTO serializa correctamente para COMPRAS")
    void validateRequestForComprasSerializes() {
        Gson gson = new Gson();
        ValidateRequestDTO req = new ValidateRequestDTO("upload-456", "compras");
        String json = gson.toJson(req);

        assertTrue(json.contains("\"upload_id\":\"upload-456\""));
        assertTrue(json.contains("\"data_type\":\"compras\""));
    }

    @Test
    @DisplayName("CleanRequestDTO serializa con upload_id correcto")
    void cleanRequestSerializesCorrectly() {
        Gson gson = new Gson();
        CleanRequestDTO req = new CleanRequestDTO("upload-789");
        String json = gson.toJson(req);

        assertTrue(json.contains("\"upload_id\":\"upload-789\""));
    }

    @Test
    @DisplayName("ConfirmRequestDTO serializa con column_mappings")
    void confirmRequestWithColumnMappingsSerializes() {
        Gson gson = new Gson();
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("fecha", "date");
        mappings.put("monto", "amount");
        mappings.put("producto", "product");

        ConfirmRequestDTO req = new ConfirmRequestDTO("upload-confirm", "ventas", mappings);
        String json = gson.toJson(req);

        assertTrue(json.contains("\"upload_id\":\"upload-confirm\""));
        assertTrue(json.contains("\"data_type\":\"ventas\""));
        assertTrue(json.contains("\"column_mappings\""));
        assertTrue(json.contains("\"fecha\":\"date\""));
    }

    // ══════════════════════════════════════════════════════════════════════
    // VALIDACIÓN DE RESPONSE DTOs CON DATOS SINTÉTICOS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("UploadResponseDTO deserializa respuesta con datos sintéticos de ventas")
    void uploadResponseDeserializesWithSyntheticData() {
        Gson gson = new Gson();
        String json = """
                {
                    "upload_id": "synth-ventas-001",
                    "filename": "ventas_2024_sintetico.csv",
                    "file_type": "csv",
                    "total_rows": 365,
                    "status": "uploaded",
                    "message": "Archivo cargado con datos sintéticos",
                    "column_info": {
                        "fecha": {"type": "date", "nullable": false},
                        "producto": {"type": "string", "nullable": false},
                        "precio": {"type": "numeric", "nullable": false},
                        "cantidad": {"type": "integer", "nullable": false},
                        "total": {"type": "numeric", "nullable": false}
                    }
                }
                """;

        UploadResponseDTO resp = gson.fromJson(json, UploadResponseDTO.class);

        assertEquals("synth-ventas-001", resp.getUploadId());
        assertEquals("ventas_2024_sintetico.csv", resp.getFilename());
        assertEquals(365, resp.getTotalRows());
        assertEquals(5, resp.getColumnInfo().size());
        assertTrue(resp.getColumnInfo().containsKey("fecha"));
        assertTrue(resp.getColumnInfo().containsKey("total"));
    }

    @Test
    @DisplayName("ValidateResponseDTO deserializa validación exitosa de datos sintéticos")
    void validateResponseDeserializesSuccessfulValidation() {
        Gson gson = new Gson();
        String json = """
                {
                    "upload_id": "synth-ventas-001",
                    "valid": true,
                    "data_type": "ventas",
                    "missing_required": [],
                    "warnings": []
                }
                """;

        ValidateResponseDTO resp = gson.fromJson(json, ValidateResponseDTO.class);

        assertTrue(resp.isValid());
        assertTrue(resp.getMissingRequired().isEmpty());
    }

    @Test
    @DisplayName("CleanResponseDTO deserializa limpieza de dataset sintético de 365 filas")
    void cleanResponseDeserializesFor365RowDataset() {
        Gson gson = new Gson();
        String json = """
                {
                    "upload_id": "synth-ventas-001",
                    "status": "cleaned",
                    "message": "Limpieza completada",
                    "result": {
                        "original_rows": 365,
                        "cleaned_rows": 360,
                        "removed_rows": 5,
                        "duplicates_removed": 3,
                        "nulls_handled": 2,
                        "outliers_detected": 4,
                        "quality_score": 0.98
                    }
                }
                """;

        CleanResponseDTO resp = gson.fromJson(json, CleanResponseDTO.class);

        assertEquals("synth-ventas-001", resp.getUploadId());
        assertEquals("cleaned", resp.getStatus());
        assertNotNull(resp.getResult());
        assertEquals(365, resp.getResult().getOriginalRows());
        assertEquals(360, resp.getResult().getCleanedRows());
    }

    @Test
    @DisplayName("ConfirmResponseDTO deserializa confirmación con registros insertados")
    void confirmResponseDeserializesWithInsertedRecords() {
        Gson gson = new Gson();
        String json = """
                {
                    "upload_id": "synth-ventas-001",
                    "success": true,
                    "records_inserted": 360,
                    "records_updated": 0,
                    "message": "Datos sintéticos insertados exitosamente"
                }
                """;

        ConfirmResponseDTO resp = gson.fromJson(json, ConfirmResponseDTO.class);

        assertTrue(resp.isSuccess());
        assertEquals(360, resp.getRecordsInserted());
        assertEquals(0, resp.getRecordsUpdated());
    }

    // ══════════════════════════════════════════════════════════════════════
    // MODELO UploadedFileDTO CON DATOS SINTÉTICOS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("UploadedFileDTO registra carga de CSV sintético de ventas")
    void uploadedFileDtoRecordsSyntheticCsvUpload() {
        LocalDateTime now = LocalDateTime.now();
        UploadedFileDTO dto = new UploadedFileDTO(
                1L, "ventas_sintetico_365.csv", "CSV", "VENTAS",
                now, "PROCESADO", 25L, 365, "tester"
        );

        assertEquals("CSV", dto.fileType());
        assertEquals("VENTAS", dto.dataType());
        assertEquals("PROCESADO", dto.status());
        assertEquals(365, dto.rowCount());
        assertTrue(dto.sizeKb() > 0);
    }

    @Test
    @DisplayName("Pipeline completo: UploadedFileDTO → ValidationReport → CleaningReport")
    void completePipelineModelsAreConsistent() {
        // Simular el pipeline de carga con datos sintéticos

        // 1. Archivo cargado
        UploadedFileDTO file = new UploadedFileDTO(
                1L, "ventas.csv", "CSV", "VENTAS",
                LocalDateTime.now(), "PROCESADO", 50L, 500, "admin"
        );

        // 2. Validación
        ValidationReport validation = new ValidationReport(
                true, List.of(), 500, 495, 5,
                Map.of(3, List.of("Fecha inválida")), List.of("Validación completa")
        );

        // 3. Limpieza
        List<List<String>> cleanRows = new ArrayList<>();
        cleanRows.add(List.of("fecha", "producto", "precio", "cantidad", "total"));
        for (int i = 0; i < 488; i++) {
            cleanRows.add(List.of("2024-01-" + String.format("%02d", (i % 28) + 1),
                    "Producto " + i, "1000.00", "5", "5000.00"));
        }
        CleaningReport cleaning = new CleaningReport(
                500, 488, 7, 5, 3, 12, 97.6, true, cleanRows, List.of()
        );

        // 4. Resultado combinado
        ValidationResultDTO result = new ValidationResultDTO(
                500, 495, 5, 7, 12, 3, 97.6, List.of()
        );

        // Verificar consistencia
        assertEquals(file.rowCount(), validation.totalRows());
        assertEquals(validation.totalRows(), cleaning.originalCount());
        assertEquals(cleaning.originalCount(), result.totalRecords());
        assertTrue(result.meetsThreshold());
        assertTrue(cleaning.meetsThreshold());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /** Replica la lógica de DataApiService.uploadFile() para detección de MIME type. */
    private String detectMimeType(String filename) {
        return filename.endsWith(".csv") ? "text/csv"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    /** Replica la lógica de DataApiService.buildMultipartBody(). */
    private byte[] buildMultipartBody(String boundary, String filename,
                                       String mimeType, byte[] fileBytes) {
        String CRLF = "\r\n";
        StringBuilder header = new StringBuilder();
        header.append("--").append(boundary).append(CRLF);
        header.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(filename).append("\"").append(CRLF);
        header.append("Content-Type: ").append(mimeType).append(CRLF);
        header.append(CRLF);

        String footer = CRLF + "--" + boundary + "--" + CRLF;

        byte[] headerBytes = header.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] footerBytes = footer.getBytes(StandardCharsets.ISO_8859_1);

        byte[] result = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, result, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, result,
                headerBytes.length + fileBytes.length, footerBytes.length);

        return result;
    }

    private boolean containsBytes(byte[] source, byte[] target) {
        outer:
        for (int i = 0; i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private Path createSyntheticCsvFile(int rows) throws IOException {
        Path file = tempDir.resolve("synth_" + rows + ".csv");
        StringBuilder sb = new StringBuilder("fecha,producto,precio,cantidad,total\n");
        for (int i = 0; i < rows; i++) {
            sb.append("2024-01-").append(String.format("%02d", (i % 28) + 1))
              .append(",Producto ").append(i)
              .append(",1000.00,5,5000.00\n");
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        return file;
    }
}
