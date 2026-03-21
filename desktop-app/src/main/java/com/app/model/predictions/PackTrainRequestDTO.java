package com.app.model.predictions;

import java.util.Map;

/**
 * Request para entrenar un pack de modelos ventas + compras.
 * POST /predictions/train-pack
 */
public class PackTrainRequestDTO {

    private String nombre;
    private String fecha_inicio;
    private String fecha_fin;
    /** hyperparameters: {"ventas": {...}, "compras": {...}} */
    private Map<String, Map<String, Object>> hyperparameters;
    private String ventas_model_type;

    public PackTrainRequestDTO() {}

    public PackTrainRequestDTO(String nombre, String fecha_inicio, String fecha_fin) {
        this.nombre       = nombre;
        this.fecha_inicio = fecha_inicio;
        this.fecha_fin    = fecha_fin;
    }

    public String getNombre()                          { return nombre; }
    public void   setNombre(String nombre)             { this.nombre = nombre; }

    public String getFecha_inicio()                    { return fecha_inicio; }
    public void   setFecha_inicio(String fecha_inicio) { this.fecha_inicio = fecha_inicio; }

    public String getFecha_fin()                       { return fecha_fin; }
    public void   setFecha_fin(String fecha_fin)       { this.fecha_fin = fecha_fin; }

    public Map<String, Map<String, Object>> getHyperparameters()           { return hyperparameters; }
    public void setHyperparameters(Map<String, Map<String, Object>> hp)    { this.hyperparameters = hp; }

    public String getVentas_model_type()                 { return ventas_model_type; }
    public void setVentas_model_type(String modelType)   { this.ventas_model_type = modelType; }
}
