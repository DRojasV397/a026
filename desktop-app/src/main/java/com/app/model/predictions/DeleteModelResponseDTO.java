package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;

/**
 * DTO para response de eliminacion de modelo.
 * Mapea la respuesta de DELETE /predictions/models/{model_key}
 */
public class DeleteModelResponseDTO {

    private boolean success;

    @SerializedName("model_key")
    private String modelKey;

    @SerializedName("deleted_from_memory")
    private Boolean deletedFromMemory;

    @SerializedName("deleted_from_disk")
    private Boolean deletedFromDisk;

    private String error;

    public boolean isSuccess() { return success; }
    public String getModelKey() { return modelKey; }
    public Boolean getDeletedFromMemory() { return deletedFromMemory; }
    public Boolean getDeletedFromDisk() { return deletedFromDisk; }
    public String getError() { return error; }
}
