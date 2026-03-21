package com.app.model.predictions;

import java.util.List;
import java.util.Map;

/**
 * Response del entrenamiento de un pack ventas + compras.
 * POST /predictions/train-pack
 */
public class PackTrainResponseDTO {

    private boolean success;
    private Integer pack_id;
    private String  pack_key;
    private SubModelResult ventas;
    private SubModelResult compras;
    private String  error;
    private List<String> issues;

    public PackTrainResponseDTO() {}

    /** Resultado de un submodelo del pack (ventas o compras). */
    public static class SubModelResult {
        private String  model_key;
        private Map<String, Object> metrics;
        private Boolean meets_r2_threshold;

        public String  getModel_key()                  { return model_key; }
        public void    setModel_key(String model_key)  { this.model_key = model_key; }
        public Map<String, Object> getMetrics()        { return metrics; }
        public void    setMetrics(Map<String, Object> m){ this.metrics = m; }
        public Boolean getMeets_r2_threshold()         { return meets_r2_threshold; }
        public void    setMeets_r2_threshold(Boolean v){ this.meets_r2_threshold = v; }

        public double getR2Score() {
            if (metrics == null) return 0.0;
            Object val = metrics.get("r2_score");
            return val instanceof Number ? ((Number) val).doubleValue() : 0.0;
        }

        public double getRmse() {
            if (metrics == null) return 0.0;
            Object val = metrics.get("rmse");
            return val instanceof Number ? ((Number) val).doubleValue() : 0.0;
        }
    }

    public boolean isSuccess()              { return success; }
    public void    setSuccess(boolean s)    { this.success = s; }

    public Integer getPack_id()             { return pack_id; }
    public void    setPack_id(Integer id)   { this.pack_id = id; }

    public String  getPack_key()            { return pack_key; }
    public void    setPack_key(String k)    { this.pack_key = k; }

    public SubModelResult getVentas()       { return ventas; }
    public void    setVentas(SubModelResult v){ this.ventas = v; }

    public SubModelResult getCompras()      { return compras; }
    public void    setCompras(SubModelResult c){ this.compras = c; }

    public String  getError()               { return error; }
    public void    setError(String e)       { this.error = e; }

    public List<String> getIssues()         { return issues; }
    public void    setIssues(List<String> i){ this.issues = i; }
}
