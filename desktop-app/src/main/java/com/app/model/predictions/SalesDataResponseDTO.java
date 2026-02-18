package com.app.model.predictions;

import java.util.List;
import java.util.Map;

/**
 * DTO para response de datos de ventas.
 * Mapea la respuesta de POST /predictions/sales-data
 */
public class SalesDataResponseDTO {

    private boolean success;
    private List<Map<String, Object>> data;
    private int count;
    private String aggregation;

    public boolean isSuccess() { return success; }
    public List<Map<String, Object>> getData() { return data; }
    public int getCount() { return count; }
    public String getAggregation() { return aggregation; }
}
