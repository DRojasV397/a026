package com.app.model.data.api;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Request para POST /data/validate.
 */
public class ValidateRequestDTO {

    @SerializedName("upload_id")
    private String uploadId;

    @SerializedName("data_type")
    private String dataType;

    @SerializedName("column_mappings")
    private Map<String, String> columnMappings;

    public ValidateRequestDTO(String uploadId, String dataType) {
        this.uploadId = uploadId;
        this.dataType = dataType;
    }

    public String getUploadId() { return uploadId; }
    public String getDataType() { return dataType; }
    public Map<String, String> getColumnMappings() { return columnMappings; }
    public void setColumnMappings(Map<String, String> columnMappings) { this.columnMappings = columnMappings; }
}
