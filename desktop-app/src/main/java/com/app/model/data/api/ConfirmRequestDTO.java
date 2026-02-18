package com.app.model.data.api;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Request para POST /data/confirm.
 */
public class ConfirmRequestDTO {

    @SerializedName("upload_id")
    private String uploadId;

    @SerializedName("data_type")
    private String dataType;

    @SerializedName("column_mappings")
    private Map<String, String> columnMappings;

    public ConfirmRequestDTO(String uploadId, String dataType, Map<String, String> columnMappings) {
        this.uploadId = uploadId;
        this.dataType = dataType;
        this.columnMappings = columnMappings;
    }

    public String getUploadId() { return uploadId; }
    public String getDataType() { return dataType; }
    public Map<String, String> getColumnMappings() { return columnMappings; }
}
