package com.app.model.dashboard;

public class UserPreferenceItemDTO {

    private int id;
    private String kpi;
    private int visible;
    private int orden;

    public int getId()      { return id; }
    public String getKpi()  { return kpi; }
    public int getVisible() { return visible; }
    public int getOrden()   { return orden; }
    public boolean isVisible() { return visible == 1; }

    public void setKpi(String kpi)      { this.kpi = kpi; }
    public void setVisible(int visible) { this.visible = visible; }
    public void setOrden(int orden)     { this.orden = orden; }
}
