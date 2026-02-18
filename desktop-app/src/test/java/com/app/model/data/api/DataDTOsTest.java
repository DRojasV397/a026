package com.app.model.data.api;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para DTOs del módulo de gestión de datos.
 * Verifica serialización/deserialización correcta contra el backend.
 */
class DataDTOsTest {

    private static final Gson gson = new Gson();

    // ── UploadResponseDTO ─────────────────────────────────────────────────

    @Test
    @DisplayName("UploadResponseDTO deserializa respuesta de upload")
    void uploadResponseDeserializes() {
        String json = """
                {
                    "upload_id": "uuid-abc-123",
                    "filename": "ventas_2024.csv",
                    "file_type": "csv",
                    "total_rows": 1500,
                    "status": "uploaded",
                    "message": "Archivo subido exitosamente",
                    "column_info": {
                        "fecha": {"type": "date", "nullable": false},
                        "monto": {"type": "numeric", "nullable": true}
                    }
                }
                """;

        UploadResponseDTO resp = gson.fromJson(json, UploadResponseDTO.class);

        assertEquals("uuid-abc-123", resp.getUploadId());
        assertEquals("ventas_2024.csv", resp.getFilename());
        assertEquals("csv", resp.getFileType());
        assertEquals(1500, resp.getTotalRows());
        assertEquals("uploaded", resp.getStatus());
        assertEquals("Archivo subido exitosamente", resp.getMessage());
        assertNotNull(resp.getColumnInfo());
        assertEquals(2, resp.getColumnInfo().size());
    }

    // ── ValidateRequestDTO ────────────────────────────────────────────────

    @Test
    @DisplayName("ValidateRequestDTO serializa con upload_id y data_type")
    void validateRequestSerializes() {
        ValidateRequestDTO req = new ValidateRequestDTO("uuid-abc", "ventas");
        String json = gson.toJson(req);

        assertTrue(json.contains("\"upload_id\":\"uuid-abc\""));
        assertTrue(json.contains("\"data_type\":\"ventas\""));
    }

    // ── ValidateResponseDTO ───────────────────────────────────────────────

    @Test
    @DisplayName("ValidateResponseDTO deserializa validación exitosa")
    void validateResponseDeserializes() {
        String json = """
                {
                    "upload_id": "uuid-abc",
                    "valid": true,
                    "data_type": "ventas",
                    "missing_required": [],
                    "warnings": ["Columna 'descuento' tiene 5% de nulos"]
                }
                """;

        ValidateResponseDTO resp = gson.fromJson(json, ValidateResponseDTO.class);

        assertEquals("uuid-abc", resp.getUploadId());
        assertTrue(resp.isValid());
        assertEquals("ventas", resp.getDataType());
        assertNotNull(resp.getMissingRequired());
        assertTrue(resp.getMissingRequired().isEmpty());
        assertEquals(1, resp.getWarnings().size());
    }

    @Test
    @DisplayName("ValidateResponseDTO deserializa validación con errores")
    void validateResponseWithErrors() {
        String json = """
                {
                    "upload_id": "uuid-abc",
                    "valid": false,
                    "data_type": "ventas",
                    "missing_required": ["fecha", "monto"],
                    "errors": ["Falta columna obligatoria: fecha"]
                }
                """;

        ValidateResponseDTO resp = gson.fromJson(json, ValidateResponseDTO.class);

        assertFalse(resp.isValid());
        assertEquals(2, resp.getMissingRequired().size());
        assertTrue(resp.getMissingRequired().contains("fecha"));
    }

    // ── CleanRequestDTO ───────────────────────────────────────────────────

    @Test
    @DisplayName("CleanRequestDTO serializa con upload_id")
    void cleanRequestSerializes() {
        CleanRequestDTO req = new CleanRequestDTO("uuid-clean");
        String json = gson.toJson(req);

        assertTrue(json.contains("\"upload_id\":\"uuid-clean\""));
    }

    // ── CleanResponseDTO ──────────────────────────────────────────────────

    @Test
    @DisplayName("CleanResponseDTO deserializa resultado de limpieza")
    void cleanResponseDeserializes() {
        String json = """
                {
                    "upload_id": "uuid-clean",
                    "status": "cleaned",
                    "message": "Limpieza completada",
                    "result": {
                        "original_rows": 1500,
                        "cleaned_rows": 1480,
                        "removed_rows": 20,
                        "duplicates_removed": 10,
                        "nulls_handled": 8,
                        "outliers_detected": 2,
                        "quality_score": 0.95
                    }
                }
                """;

        CleanResponseDTO resp = gson.fromJson(json, CleanResponseDTO.class);

        assertEquals("uuid-clean", resp.getUploadId());
        assertEquals("cleaned", resp.getStatus());
        assertNotNull(resp.getResult());
    }

    // ── ConfirmRequestDTO ─────────────────────────────────────────────────

    @Test
    @DisplayName("ConfirmRequestDTO serializa con mappings")
    void confirmRequestSerializes() {
        Map<String, String> mappings = Map.of("fecha", "date_column", "monto", "amount_column");
        ConfirmRequestDTO req = new ConfirmRequestDTO("uuid-confirm", "ventas", mappings);
        String json = gson.toJson(req);

        assertTrue(json.contains("\"upload_id\":\"uuid-confirm\""));
        assertTrue(json.contains("\"data_type\":\"ventas\""));
        assertTrue(json.contains("\"column_mappings\""));
    }

    // ── ConfirmResponseDTO ────────────────────────────────────────────────

    @Test
    @DisplayName("ConfirmResponseDTO deserializa confirmación exitosa")
    void confirmResponseDeserializes() {
        String json = """
                {
                    "upload_id": "uuid-confirm",
                    "success": true,
                    "records_inserted": 1480,
                    "records_updated": 0,
                    "message": "Datos insertados exitosamente"
                }
                """;

        ConfirmResponseDTO resp = gson.fromJson(json, ConfirmResponseDTO.class);

        assertEquals("uuid-confirm", resp.getUploadId());
        assertTrue(resp.isSuccess());
        assertEquals(1480, resp.getRecordsInserted());
        assertEquals(0, resp.getRecordsUpdated());
        assertEquals("Datos insertados exitosamente", resp.getMessage());
    }
}
