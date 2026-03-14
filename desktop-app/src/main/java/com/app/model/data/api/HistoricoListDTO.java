package com.app.model.data.api;

import java.util.List;

/**
 * DTO raíz de la respuesta de GET /data/historicos.
 */
public class HistoricoListDTO {

    private List<HistoricoItemDTO> items;
    private int total;

    public List<HistoricoItemDTO> getItems() {
        return items != null ? items : List.of();
    }

    public int getTotal() {
        return total;
    }
}
