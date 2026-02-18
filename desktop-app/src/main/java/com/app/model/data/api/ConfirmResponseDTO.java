package com.app.model.data.api;

import com.google.gson.annotations.SerializedName;

/**
 * Respuesta de POST /data/confirm.
 */
public class ConfirmResponseDTO {

    @SerializedName("upload_id")
    private String uploadId;

    private boolean success;

    @SerializedName("records_inserted")
    private int recordsInserted;

    @SerializedName("records_updated")
    private int recordsUpdated;

    private String message;

    public String getUploadId() { return uploadId; }
    public boolean isSuccess() { return success; }
    public int getRecordsInserted() { return recordsInserted; }
    public int getRecordsUpdated() { return recordsUpdated; }
    public String getMessage() { return message; }
}
