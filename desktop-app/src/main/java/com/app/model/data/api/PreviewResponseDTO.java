package com.app.model.data.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Respuesta de GET /data/preview/{upload_id}.
 * Contiene vista previa de las filas cargadas.
 */
public class PreviewResponseDTO {

    @SerializedName("upload_id")
    private String uploadId;

    @SerializedName("total_rows")
    private int totalRows;

    @SerializedName("preview_rows")
    private int previewRows;

    private List<String> columns;

    private List<Map<String, Object>> data;

    public String getUploadId() { return uploadId; }
    public int getTotalRows() { return totalRows; }
    public int getPreviewRows() { return previewRows; }
    public List<String> getColumns() { return columns; }
    public List<Map<String, Object>> getData() { return data; }
}
