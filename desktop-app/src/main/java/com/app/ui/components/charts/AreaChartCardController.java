package com.app.ui.components.charts;

import javafx.fxml.FXML;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.XYChart;

/**
 * Controlador para gráficos de área
 * Ideal para mostrar volúmenes y acumulaciones a lo largo del tiempo
 */
public class AreaChartCardController extends BaseChartController {

    @FXML
    private AreaChart<String, Number> areaChart;

    @FXML
    private void initialize() {
        setupChart();
    }

    private void setupChart() {
        if (areaChart != null) {
            areaChart.setAnimated(true);
            areaChart.setLegendVisible(true);
            areaChart.setCreateSymbols(true);
        }
    }

    @Override
    public void loadData() {
        // Datos de ejemplo - Reemplazar con datos reales
        XYChart.Series<String, Number> series1 = new XYChart.Series<>();
        series1.setName("Ingresos");

        series1.getData().add(new XYChart.Data<>("Q1", 120));
        series1.getData().add(new XYChart.Data<>("Q2", 145));
        series1.getData().add(new XYChart.Data<>("Q3", 168));
        series1.getData().add(new XYChart.Data<>("Q4", 185));

        XYChart.Series<String, Number> series2 = new XYChart.Series<>();
        series2.setName("Gastos");

        series2.getData().add(new XYChart.Data<>("Q1", 80));
        series2.getData().add(new XYChart.Data<>("Q2", 95));
        series2.getData().add(new XYChart.Data<>("Q3", 110));
        series2.getData().add(new XYChart.Data<>("Q4", 125));

        areaChart.getData().clear();
        areaChart.getData().addAll(series1, series2);
    }

    /**
     * Carga datos personalizados en el gráfico
     */
    public void loadCustomData(XYChart.Series<String, Number>... series) {
        areaChart.getData().clear();
        areaChart.getData().addAll(series);
    }

    @Override
    public void refreshData() {
        loadData();
    }

    @Override
    public void clearData() {
        if (areaChart != null) {
            areaChart.getData().clear();
        }
    }
}