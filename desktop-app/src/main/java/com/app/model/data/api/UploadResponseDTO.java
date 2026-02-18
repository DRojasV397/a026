package com.app.model.data.api;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Respuesta de POST /data/upload.
 */
public class UploadResponseDTO {

    @SerializedName("upload_id")
    private String uploadId;

    private String filename;

    @SerializedName("file_type")
    private String fileType;

    @SerializedName("total_rows")
    private int totalRows;

    private String status;
    private String message;

    @SerializedName("column_info")
    private Map<String, Map<String, Object>> columnInfo;

    public String getUploadId() { return uploadId; }
    public String getFilename() { return filename; }
    public String getFileType() { return fileType; }
    public int getTotalRows() { return totalRows; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Map<String, Map<String, Object>> getColumnInfo() { return columnInfo; }
}
