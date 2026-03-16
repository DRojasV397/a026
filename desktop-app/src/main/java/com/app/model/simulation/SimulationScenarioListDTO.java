package com.app.model.simulation;

import java.util.List;

/**
 * DTO wrapper para la lista de escenarios.
 * Mapea la respuesta de GET /simulation/scenarios
 */
public class SimulationScenarioListDTO {

    private boolean success;
    private int total;
    private List<SimulationScenarioSummaryDTO> escenarios;

    public boolean isSuccess()                              { return success; }
    public int     getTotal()                               { return total; }
    public List<SimulationScenarioSummaryDTO> getEscenarios(){ return escenarios; }
}
