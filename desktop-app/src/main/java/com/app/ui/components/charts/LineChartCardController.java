package com.app.ui.components.charts;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

/**
 * Controlador para gráficos de líneas
 * Ideal para mostrar tendencias y evolución temporal
 * OPTIMIZADO: Usa lazy loading y threading para mejor performance
 */
public class LineChartCardController extends BaseChartController {

    @FXML
    private LineChart<String, Number> lineChart;

    private boolean dataLoaded = false;

    @FXML
    private void initialize() {
        // Configuración inicial del gráfico
        setupChart();
    }

    private void setupChart() {
        if (lineChart != null) {
            lineChart.setAnimated(false); // Desactivar animaciones para mejor performance
            lineChart.setLegendVisible(true);
            lineChart.setCreateSymbols(false); // No crear símbolos en cada punto para ahorrar memoria
        }
    }

    @Override
    public void loadData() {
        if (dataLoaded) return; // Evitar cargar datos múltiples veces

        // Cargar datos en background thread
        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() {
                // Simular carga de datos (aquí irían tus queries a la BD)
                try {
                    Thread.sleep(100); // Simular latencia
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }

            @Override
            protected void succeeded() {
                // Actualizar UI en JavaFX thread
                Platform.runLater(() -> {
                    XYChart.Series<String, Number> series1 = new XYChart.Series<>();
                    series1.setName("2024");

                    series1.getData().add(new XYChart.Data<>("Ene", 23));
                    series1.getData().add(new XYChart.Data<>("Feb", 45));
                    series1.getData().add(new XYChart.Data<>("Mar", 38));
                    series1.getData().add(new XYChart.Data<>("Abr", 52));

                    lineChart.getData().clear();
                    lineChart.getData().add(series1);
                    dataLoaded = true;
                });
            }
        };

        new Thread(loadTask).start();
    }

    /**
     * Carga datos personalizados en el gráfico
     */
    public void loadCustomData(XYChart.Series<String, Number>... series) {
        Platform.runLater(() -> {
            lineChart.getData().clear();
            lineChart.getData().addAll(series);
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
        if (lineChart != null) {
            Platform.runLater(() -> lineChart.getData().clear());
            dataLoaded = false;
        }
    }
}