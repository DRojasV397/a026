package com.app.model.profitability;

import java.util.List;

/** Respuesta de GET /profitability/ranking */
public class RankingResponseDTO {
    private boolean success;
    private List<ProductProfitDTO> ranking;
    private String metrica_ordenamiento;
    private String orden;
    private String descripcion;

    public boolean isSuccess()                    { return success; }
    public List<ProductProfitDTO> getRanking()    { return ranking != null ? ranking : List.of(); }
    public String getMetricaOrdenamiento()        { return metrica_ordenamiento; }
    public String getOrden()                      { return orden; }
    public String getDescripcion()                { return descripcion; }
}
