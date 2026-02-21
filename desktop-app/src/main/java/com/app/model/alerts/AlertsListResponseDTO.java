package com.app.model.alerts;

import java.util.List;
import java.util.Map;

/** Respuesta de GET /alerts (alertas activas) */
public class AlertsListResponseDTO {
    private boolean success;
    private int total;
    private int max_permitidas;
    private List<ApiAlertDTO> alertas;
    private Map<String, Integer> por_tipo;
    private Map<String, Integer> por_importancia;

    public boolean isSuccess()                       { return success; }
    public int getTotal()                            { return total; }
    public int getMaxPermitidas()                    { return max_permitidas; }
    public List<ApiAlertDTO> getAlertas()            { return alertas != null ? alertas : List.of(); }
    public Map<String, Integer> getPorTipo()         { return por_tipo; }
    public Map<String, Integer> getPorImportancia()  { return por_importancia; }
}
