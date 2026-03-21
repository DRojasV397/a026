package com.app.model.predictions;

import java.util.List;
import java.util.Map;

/**
 * Response del forecast coordinado de un pack (ventas + compras).
 * POST /predictions/forecast-pack
 */
public class PackForecastResponseDTO {

    private boolean success;
    private String  pack_key;
    private Integer periods;
    private ForecastSeries ventas;
    private ForecastSeries compras;
    private String  error;

    public PackForecastResponseDTO() {}

    /**
     * Serie de predicciones para un submodelo del pack.
     * predictions.dates / values / lower_ci / upper_ci
     */
    public static class ForecastSeries {
        private Map<String, Object> predictions;
        private String              model_key;

        public Map<String, Object> getPredictions()      { return predictions; }
        public void setPredictions(Map<String, Object> p){ this.predictions = p; }
        public String getModel_key()                     { return model_key; }
        public void   setModel_key(String k)             { this.model_key = k; }

        @SuppressWarnings("unchecked")
        public List<String> getDates() {
            if (predictions == null) return List.of();
            Object d = predictions.get("dates");
            return d instanceof List ? (List<String>) d : List.of();
        }

        @SuppressWarnings("unchecked")
        public List<Double> getValues() {
            if (predictions == null) return List.of();
            Object v = predictions.get("values");
            if (!(v instanceof List)) return List.of();
            List<?> raw = (List<?>) v;
            return raw.stream()
                      .map(x -> x instanceof Number ? ((Number) x).doubleValue() : 0.0)
                      .toList();
        }

        @SuppressWarnings("unchecked")
        public List<Double> getLowerCi() {
            if (predictions == null) return List.of();
            Object v = predictions.get("lower_ci");
            if (!(v instanceof List)) return List.of();
            List<?> raw = (List<?>) v;
            return raw.stream()
                      .map(x -> x instanceof Number ? ((Number) x).doubleValue() : 0.0)
                      .toList();
        }

        @SuppressWarnings("unchecked")
        public List<Double> getUpperCi() {
            if (predictions == null) return List.of();
            Object v = predictions.get("upper_ci");
            if (!(v instanceof List)) return List.of();
            List<?> raw = (List<?>) v;
            return raw.stream()
                      .map(x -> x instanceof Number ? ((Number) x).doubleValue() : 0.0)
                      .toList();
        }
    }

    public boolean isSuccess()              { return success; }
    public void    setSuccess(boolean s)    { this.success = s; }

    public String  getPack_key()            { return pack_key; }
    public void    setPack_key(String k)    { this.pack_key = k; }

    public Integer getPeriods()             { return periods; }
    public void    setPeriods(Integer p)    { this.periods = p; }

    public ForecastSeries getVentas()       { return ventas; }
    public void    setVentas(ForecastSeries v){ this.ventas = v; }

    public ForecastSeries getCompras()      { return compras; }
    public void    setCompras(ForecastSeries c){ this.compras = c; }

    public String  getError()               { return error; }
    public void    setError(String e)       { this.error = e; }
}
