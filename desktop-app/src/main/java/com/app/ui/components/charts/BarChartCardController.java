package com.app.ui.components.charts;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;

/**
 * Controlador para gráficos de barras
 * Ideal para comparaciones entre categorías
 * OPTIMIZADO: Usa lazy loading y threading
 */
public class BarChartCardController extends BaseChartController {

    @FXML
    private BarChart<String, Number> barChart;

    private boolean dataLoaded = false;

    @FXML
    private void initialize() {
        setupChart();
    }

    private void setupChart() {
        if (barChart != null) {
            barChart.setAnimated(false); // Desactivar animaciones
            barChart.setLegendVisible(true);
            barChart.setCategoryGap(10);
            barChart.setBarGap(3);
        }
    }

    @Override
    public void loadData() {
        if (dataLoaded) return;

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName("Ventas por Región");

                    series.getData().add(new XYChart.Data<>("Norte", 150));
                    series.getData().add(new XYChart.Data<>("Sur", 230));
                    series.getData().add(new XYChart.Data<>("Este", 180));
                    series.getData().add(new XYChart.Data<>("Oeste", 210));

                    barChart.getData().clear();
                    barChart.getData().add(series);
                    dataLoaded = true;
                });
            }
        };

        new Thread(loadTask).start();
    }

    public void loadCustomData(XYChart.Series<String, Number>... series) {
        Platform.runLater(() -> {
            barChart.getData().clear();
            barChart.getData().addAll(series);
            dataLoaded = true;
        });
    }

    @Override
    public void refreshData() {
        dataLoaded = false;
        clearData();
        loadData();
    }

    @Override
    public void clearData() {
        if (barChart != null) {
            Platform.runLater(() -> barChart.getData().clear());
            dataLoaded = false;
        }
    }
}