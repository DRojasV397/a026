package com.app.model.data.api;

import java.util.List;

/**
 * DTO wrapper para la respuesta del historial de cargas.
 * Mapea GET /data/historial → { items: [...], total: N }
 */
public class HistorialCargaListDTO {

    private List<HistorialCargaItemDTO> items;
    private int total;

    public List<HistorialCargaItemDTO> getItems() {
        return items != null ? items : List.of();
    }

    public int getTotal() { return total; }
}
