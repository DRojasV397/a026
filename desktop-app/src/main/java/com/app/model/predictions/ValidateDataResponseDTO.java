package com.app.model.predictions;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * DTO para response de validacion de datos para prediccion.
 * Mapea la respuesta de POST /predictions/validate-data
 * RN-01.01: Minimo 6 meses de datos historicos.
 */
public class ValidateDataResponseDTO {

    private boolean valid;
    private List<String> issues;

    @SerializedName("data_points")
    private int dataPoints;

    @SerializedName("min_required")
    private int minRequired;

    @SerializedName("date_range")
    private Map<String, String> dateRange;

    public boolean isValid() { return valid; }
    public List<String> getIssues() { return issues; }
    public int getDataPoints() { return dataPoints; }
    public int getMinRequired() { return minRequired; }
    public Map<String, String> getDateRange() { return dateRange; }
}
