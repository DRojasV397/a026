package com.app.model.simulation;

import java.util.Map;

/**
 * DTO para la respuesta de POST /simulation/create
 */
public class CreateScenarioResponseDTO {

    private boolean success;
    private Map<String, Object> escenario;
    private String mensaje;
    private String error;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean v) { this.success = v; }

    public String getMensaje() { return mensaje; }
    public String getError()   { return error; }
    public void setError(String e) { this.error = e; }

    /**
     * Extrae el id_escenario del mapa de escenario.
     * Gson deserializa los números como Double en Map<String,Object>.
     */
    public int getIdEscenario() {
        if (escenario == null) return -1;
        Object id = escenario.get("id_escenario");
        if (id instanceof Double d) return d.intValue();
        if (id instanceof Integer i) return i;
        if (id instanceof Long l) return l.intValue();
        return -1;
    }
}
