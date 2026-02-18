package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * DTO para response de carga de todos los modelos.
 * Mapea la respuesta de POST /predictions/models/load-all
 */
public class LoadAllModelsResponseDTO {

    private boolean success;
    private List<Map<String, Object>> loaded;
    private List<Map<String, Object>> failed;

    @SerializedName("total_loaded")
    private int totalLoaded;

    @SerializedName("total_failed")
    private int totalFailed;

    private String message;

    public boolean isSuccess() { return success; }
    public List<Map<String, Object>> getLoaded() { return loaded; }
    public List<Map<String, Object>> getFailed() { return failed; }
    public int getTotalLoaded() { return totalLoaded; }
    public int getTotalFailed() { return totalFailed; }
    public String getMessage() { return message; }
}
