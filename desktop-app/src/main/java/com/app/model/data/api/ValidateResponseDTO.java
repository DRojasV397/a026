package com.app.model.data.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Respuesta de POST /data/validate.
 */
public class ValidateResponseDTO {

    @SerializedName("upload_id")
    private String uploadId;

    private boolean valid;

    @SerializedName("data_type")
    private String dataType;

    private List<ColumnValidationDTO> columns;

    @SerializedName("missing_required")
    private List<String> missingRequired;

    private List<String> warnings;
    private List<String> errors;

    public String getUploadId() { return uploadId; }
    public boolean isValid() { return valid; }
    public String getDataType() { return dataType; }
    public List<ColumnValidationDTO> getColumns() { return columns; }
    public List<String> getMissingRequired() { return missingRequired; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getErrors() { return errors; }

    public static class ColumnValidationDTO {
        private String name;
        private boolean found;

        @SerializedName("suggested_mapping")
        private String suggestedMapping;

        @SerializedName("data_type")
        private String dataType;

        @SerializedName("null_count")
        private int nullCount;

        @SerializedName("null_percentage")
        private double nullPercentage;

        public String getName() { return name; }
        public boolean isFound() { return found; }
        public String getSuggestedMapping() { return suggestedMapping; }
        public String getDataType() { return dataType; }
        public int getNullCount() { return nullCount; }
        public double getNullPercentage() { return nullPercentage; }
    }
}
