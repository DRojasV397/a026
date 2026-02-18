package com.app.model.data.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para DTOs extendidos del modulo de gestion de datos.
 * Cubre: PreviewResponseDTO, QualityReportResponseDTO.
 */
@DisplayName("ExtendedDataDTOs - DTOs complementarios del modulo de datos")
class ExtendedDataDTOsTest {

    private static final Gson gson = new Gson();

    // ── PreviewResponseDTO ──────────────────────────────────────────────

    @Test
    @DisplayName("PreviewResponseDTO deserializa preview de datos de ventas")
    void previewResponseDeserializes() {
        String json = """
                {
                    "upload_id": "preview-001",
                    "total_rows": 500,
                    "preview_rows": 10,
                    "columns": ["fecha", "producto", "precio", "cantidad", "total"],
                    "data": [
                        {"fecha": "2024-01-01", "producto": "Leche", "precio": 25.50, "cantidad": 10, "total": 255.00},
                        {"fecha": "2024-01-02", "producto": "Pan", "precio": 15.00, "cantidad": 20, "total": 300.00}
                    ]
                }
                """;

        PreviewResponseDTO resp = gson.fromJson(json, PreviewResponseDTO.class);

        assertEquals("preview-001", resp.getUploadId());
        assertEquals(500, resp.getTotalRows());
        assertEquals(10, resp.getPreviewRows());
        assertNotNull(resp.getColumns());
        assertEquals(5, resp.getColumns().size());
        assertTrue(resp.getColumns().contains("fecha"));
        assertTrue(resp.getColumns().contains("total"));
        assertNotNull(resp.getData());
        assertEquals(2, resp.getData().size());
    }

    @Test
    @DisplayName("PreviewResponseDTO con datos vacios")
    void previewResponseEmptyData() {
        String json = """
                {
                    "upload_id": "preview-empty",
                    "total_rows": 0,
                    "preview_rows": 0,
                    "columns": [],
                    "data": []
                }
                """;

        PreviewResponseDTO resp = gson.fromJson(json, PreviewResponseDTO.class);

        assertEquals(0, resp.getTotalRows());
        assertEquals(0, resp.getPreviewRows());
        assertTrue(resp.getColumns().isEmpty());
        assertTrue(resp.getData().isEmpty());
    }

    @Test
    @DisplayName("PreviewResponseDTO con 10 filas de datos sinteticos")
    void previewResponseWith10SyntheticRows() {
        StringBuilder dataJson = new StringBuilder("[");
        for (int i = 0; i < 10; i++) {
            if (i > 0) dataJson.append(",");
            dataJson.append(String.format(
                    "{\"fecha\": \"2024-01-%02d\", \"producto\": \"Producto %d\", \"precio\": %.2f, \"cantidad\": %d, \"total\": %.2f}",
                    i + 1, i, 100.0 + i * 10, i + 1, (100.0 + i * 10) * (i + 1)
            ));
        }
        dataJson.append("]");

        String json = String.format("""
                {
                    "upload_id": "synth-preview",
                    "total_rows": 1000,
                    "preview_rows": 10,
                    "columns": ["fecha", "producto", "precio", "cantidad", "total"],
                    "data": %s
                }
                """, dataJson);

        PreviewResponseDTO resp = gson.fromJson(json, PreviewResponseDTO.class);

        assertEquals(10, resp.getData().size());
        assertEquals(1000, resp.getTotalRows());
    }

    // ── QualityReportResponseDTO ────────────────────────────────────────

    @Test
    @DisplayName("QualityReportResponseDTO deserializa reporte de calidad completo")
    void qualityReportResponseDeserializes() {
        String json = """
                {
                    "upload_id": "quality-001",
                    "overall_score": 92.5,
                    "total_rows": 500,
                    "valid_rows": 488,
                    "metrics": [
                        {"column": "fecha", "completeness": 100.0, "uniqueness": 98.5, "validity": 100.0, "outliers_count": 0},
                        {"column": "producto", "completeness": 99.0, "uniqueness": 45.0, "validity": 100.0, "outliers_count": 0},
                        {"column": "precio", "completeness": 98.5, "uniqueness": 60.0, "validity": 97.0, "outliers_count": 3},
                        {"column": "cantidad", "completeness": 100.0, "uniqueness": 30.0, "validity": 99.0, "outliers_count": 1},
                        {"column": "total", "completeness": 98.0, "uniqueness": 75.0, "validity": 96.5, "outliers_count": 5}
                    ],
                    "issues": ["3 outliers en columna precio", "5 outliers en columna total"],
                    "recommendations": ["Revisar outliers en columna total", "Verificar nulos en precio"]
                }
                """;

        QualityReportResponseDTO resp = gson.fromJson(json, QualityReportResponseDTO.class);

        assertEquals("quality-001", resp.getUploadId());
        assertEquals(92.5, resp.getOverallScore(), 0.01);
        assertEquals(500, resp.getTotalRows());
        assertEquals(488, resp.getValidRows());
        assertNotNull(resp.getMetrics());
        assertEquals(5, resp.getMetrics().size());

        // Verificar metricas de la primera columna
        QualityReportResponseDTO.QualityMetricDTO fechaMetric = resp.getMetrics().get(0);
        assertEquals("fecha", fechaMetric.getColumn());
        assertEquals(100.0, fechaMetric.getCompleteness(), 0.01);
        assertEquals(0, fechaMetric.getOutliersCount());

        // Verificar issues y recommendations
        assertNotNull(resp.getIssues());
        assertEquals(2, resp.getIssues().size());
        assertNotNull(resp.getRecommendations());
        assertEquals(2, resp.getRecommendations().size());
    }

    @Test
    @DisplayName("QualityReportResponseDTO con calidad perfecta (100%)")
    void qualityReportPerfectScore() {
        String json = """
                {
                    "upload_id": "perfect-001",
                    "overall_score": 100.0,
                    "total_rows": 200,
                    "valid_rows": 200,
                    "metrics": [
                        {"column": "fecha", "completeness": 100.0, "uniqueness": 100.0, "validity": 100.0, "outliers_count": 0},
                        {"column": "total", "completeness": 100.0, "uniqueness": 100.0, "validity": 100.0, "outliers_count": 0}
                    ],
                    "issues": [],
                    "recommendations": []
                }
                """;

        QualityReportResponseDTO resp = gson.fromJson(json, QualityReportResponseDTO.class);

        assertEquals(100.0, resp.getOverallScore(), 0.01);
        assertEquals(resp.getTotalRows(), resp.getValidRows());
        assertTrue(resp.getIssues().isEmpty());
        assertTrue(resp.getRecommendations().isEmpty());
    }

    @Test
    @DisplayName("QualityReportResponseDTO con calidad baja")
    void qualityReportLowScore() {
        String json = """
                {
                    "upload_id": "low-quality",
                    "overall_score": 35.0,
                    "total_rows": 100,
                    "valid_rows": 35,
                    "metrics": [
                        {"column": "fecha", "completeness": 50.0, "uniqueness": 20.0, "validity": 40.0, "outliers_count": 15}
                    ],
                    "issues": ["Alto porcentaje de nulos", "Muchos outliers", "Baja validez"],
                    "recommendations": ["Revisar fuente de datos", "Considerar limpieza manual"]
                }
                """;

        QualityReportResponseDTO resp = gson.fromJson(json, QualityReportResponseDTO.class);

        assertTrue(resp.getOverallScore() < 50.0);
        assertTrue(resp.getValidRows() < resp.getTotalRows());
        assertFalse(resp.getIssues().isEmpty());
    }
}
