package com.app.model.data.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Respuesta de POST /data/clean.
 */
public class CleanResponseDTO {

    @SerializedName("upload_id")
    private String uploadId;

    private String status;
    private CleaningResultDTO result;
    private String message;

    public String getUploadId() { return uploadId; }
    public String getStatus() { return status; }
    public CleaningResultDTO getResult() { return result; }
    public String getMessage() { return message; }

    public static class CleaningResultDTO {
        @SerializedName("original_rows")
        private int originalRows;

        @SerializedName("cleaned_rows")
        private int cleanedRows;

        @SerializedName("removed_rows")
        private int removedRows;

        @SerializedName("duplicates_removed")
        private int duplicatesRemoved;

        @SerializedName("nulls_handled")
        private int nullsHandled;

        @SerializedName("outliers_detected")
        private int outliersDetected;

        @SerializedName("quality_score")
        private double qualityScore;

        private List<String> warnings;

        public int getOriginalRows() { return originalRows; }
        public int getCleanedRows() { return cleanedRows; }
        public int getRemovedRows() { return removedRows; }
        public int getDuplicatesRemoved() { return duplicatesRemoved; }
        public int getNullsHandled() { return nullsHandled; }
        public int getOutliersDetected() { return outliersDetected; }
        public double getQualityScore() { return qualityScore; }
        public List<String> getWarnings() { return warnings; }

        public double getRetentionPercent() {
            return originalRows > 0 ? (cleanedRows * 100.0 / originalRows) : 0;
        }
    }
}
