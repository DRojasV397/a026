package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;

/**
 * DTO para informacion de modelo guardado en disco.
 * Mapea la respuesta de GET /predictions/models/saved
 */
public class SavedModelInfoDTO {

    @SerializedName("model_key")
    private String modelKey;

    private String filename;

    @SerializedName("size_bytes")
    private long sizeBytes;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("modified_at")
    private String modifiedAt;

    @SerializedName("is_loaded")
    private boolean isLoaded;

    public String getModelKey() { return modelKey; }
    public String getFilename() { return filename; }
    public long getSizeBytes() { return sizeBytes; }
    public String getCreatedAt() { return createdAt; }
    public String getModifiedAt() { return modifiedAt; }
    public boolean isLoaded() { return isLoaded; }
}
