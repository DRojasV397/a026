package com.app.model.data.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Respuesta de GET /data/quality-report/{upload_id}.
 * Reporte de calidad de datos con metricas por columna.
 */
public class QualityReportResponseDTO {

    @SerializedName("upload_id")
    private String uploadId;

    @SerializedName("overall_score")
    private double overallScore;

    @SerializedName("total_rows")
    private int totalRows;

    @SerializedName("valid_rows")
    private int validRows;

    private List<QualityMetricDTO> metrics;
    private List<String> issues;
    private List<String> recommendations;

    public String getUploadId() { return uploadId; }
    public double getOverallScore() { return overallScore; }
    public int getTotalRows() { return totalRows; }
    public int getValidRows() { return validRows; }
    public List<QualityMetricDTO> getMetrics() { return metrics; }
    public List<String> getIssues() { return issues; }
    public List<String> getRecommendations() { return recommendations; }

    /**
     * Metrica de calidad de una columna individual.
     */
    public static class QualityMetricDTO {
        private String column;
        private double completeness;
        private double uniqueness;
        private double validity;

        @SerializedName("outliers_count")
        private int outliersCount;

        public String getColumn() { return column; }
        public double getCompleteness() { return completeness; }
        public double getUniqueness() { return uniqueness; }
        public double getValidity() { return validity; }
        public int getOutliersCount() { return outliersCount; }
    }
}
