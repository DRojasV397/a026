package com.app.ui.components.charts;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;

/**
 * Controlador para gráficos circulares (Pie Chart)
 * Ideal para mostrar proporciones y porcentajes
 * OPTIMIZADO: Usa lazy loading
 */
public class PieChartCardController extends BaseChartController {

    @FXML
    private PieChart pieChart;

    private boolean dataLoaded = false;

    @FXML
    private void initialize() {
        setupChart();
    }

    private void setupChart() {
        if (pieChart != null) {
            pieChart.setAnimated(false);
            pieChart.setLegendVisible(true);
            pieChart.setLabelsVisible(true);
            pieChart.setStartAngle(90);
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
                    ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
                            new PieChart.Data("Producto A", 35),
                            new PieChart.Data("Producto B", 28),
                            new PieChart.Data("Producto C", 20),
                            new PieChart.Data("Otros", 17)
                    );

                    pieChart.setData(pieChartData);
                    addPercentageLabels();
                    dataLoaded = true;
                });
            }
        };

        new Thread(loadTask).start();
    }

    public void loadCustomData(ObservableList<PieChart.Data> data) {
        Platform.runLater(() -> {
            pieChart.setData(data);
            addPercentageLabels();
            dataLoaded = true;
        });
    }

    private void addPercentageLabels() {
        double total = pieChart.getData().stream()
                .mapToDouble(PieChart.Data::getPieValue)
                .sum();

        pieChart.getData().forEach(data -> {
            double percentage = (data.getPieValue() / total) * 100;
            String label = String.format("%s (%.1f%%)", data.getName(), percentage);
            data.setName(label);
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
        if (pieChart != null) {
            Platform.runLater(() -> pieChart.getData().clear());
            dataLoaded = false;
        }
    }
}