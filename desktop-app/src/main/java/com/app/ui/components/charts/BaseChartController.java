package com.app.ui.components.charts;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Clase base abstracta para todos los controladores de gráficos
 * Proporciona funcionalidad común como título, subtítulo y contenedor
 */
public abstract class BaseChartController {

    @FXML
    protected VBox cardContainer;

    @FXML
    protected Label chartTitle;

    @FXML
    protected Label chartSubtitle;

    @FXML
    protected VBox chartContent;

    /**
     * Establece el título del gráfico
     */
    public void setTitle(String title) {
        if (chartTitle != null) {
            chartTitle.setText(title);
        }
    }

    /**
     * Establece el subtítulo del gráfico
     */
    public void setSubtitle(String subtitle) {
        if (chartSubtitle != null) {
            chartSubtitle.setText(subtitle);
            chartSubtitle.setVisible(subtitle != null && !subtitle.isEmpty());
            chartSubtitle.setManaged(subtitle != null && !subtitle.isEmpty());
        }
    }

    /**
     * Inicializa el gráfico con datos
     * Este método debe ser implementado por cada tipo de gráfico
     */
    public abstract void loadData();

    /**
     * Actualiza los datos del gráfico
     */
    public abstract void refreshData();

    /**
     * Limpia los datos del gráfico
     */
    public void clearData() {
        // Implementación por defecto vacía
        // Las subclases pueden sobreescribir si necesitan
    }
}