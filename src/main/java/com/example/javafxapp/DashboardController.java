package com.example.javafxapp;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.TranslateTransition;
import org.reactfx.EventStream;
import org.reactfx.EventStreams;
import org.reactfx.value.Var;

import java.util.Arrays;
import java.util.List;

public class DashboardController {

    // Simple alert model
    public static class AlertItem {
        public final String type; // warning, error, info
        public final String message;
        public final String time;
        public AlertItem(String type, String message, String time) {
            this.type = type; this.message = message; this.time = time;
        }
    }

    // Sidebar
    @FXML private VBox sidebar;
    @FXML private javafx.scene.control.Button collapseBtn;
    @FXML private javafx.scene.control.Button expandBtn;
    @FXML private ToggleButton menuDashboard;
    @FXML private ToggleButton menuPredictive;
    @FXML private ToggleButton menuProfitability;
    @FXML private ToggleButton menuSimulation;
    @FXML private ToggleButton menuData;
    @FXML private ToggleButton menuAlerts;
    @FXML private ToggleButton menuReports;

    // Header
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private ToggleButton periodWeek;
    @FXML private ToggleButton periodMonth;
    @FXML private ToggleButton periodYear;

    // KPI fields
    @FXML private Label roiValue;
    @FXML private Label breakevenValue;
    @FXML private Label operatingProfitValue;
    @FXML private Label profitMarginValue;

    // Content
    @FXML private LineChart<String, Number> salesChart;
    @FXML private ListView<String> topProductsList;
    @FXML private ListView<AlertItem> recentAlertsList;

    // Reactive state
    private final Var<Boolean> sidebarCollapsedVar = Var.newSimpleVar(false);
    private final Var<String> sectionTitle = Var.newSimpleVar("Dashboard");
    private final Var<String> sectionSubtitle = Var.newSimpleVar("Resumen general de tu negocio");
    private final Var<String> currentPeriodVar = Var.newSimpleVar("Month");

    @FXML
    private void initialize() {
        // Bind header labels to reactive section vars
        titleLabel.textProperty().bind(sectionTitle);
        subtitleLabel.textProperty().bind(sectionSubtitle);

        // Attach theme-independent behavior and set up responsive behavior
        sidebar.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                scene.setFill(javafx.scene.paint.Color.web("#F5F9FC"));
                // Auto-collapse when width < 900 with debounce
                EventStreams.valuesOf(scene.widthProperty())
                        .successionEnds(java.time.Duration.ofMillis(120))
                        .map(w -> w.doubleValue() < 900)
                        .subscribe(forceCollapse -> {
                            if (forceCollapse) sidebarCollapsedVar.setValue(true);
                        });
            }
        });

        // Default texts (already set in FXML), ensure values
        roiValue.setText("24.5%");
        breakevenValue.setText("45,230"); // currency symbol omitted to avoid FXML EL, can be added in UI
        operatingProfitValue.setText("128,450");
        profitMarginValue.setText("18.7%");

        // Build chart with sample data
        setupSalesChart();

        // Populate products list
        topProductsList.getItems().setAll(
                Arrays.asList(
                        "Producto A - 85,420 (+12.5%)",
                        "Producto B - 72,350 (-15.2%)",
                        "Producto C - 68,900 (+8.3%)",
                        "Producto D - 54,200 (+20.1%)",
                        "Producto E - 48,750 (+5.7%)"
                )
        );

        // Recent alerts sample data
        if (recentAlertsList != null) {
            recentAlertsList.getItems().setAll(List.of(
                    new AlertItem("warning", "Caída en ventas del Producto B (−15%)", "Hace 2 horas"),
                    new AlertItem("info", "Oportunidad: Demanda +20% en Producto D", "Hace 4 horas"),
                    new AlertItem("error", "Stock crítico en 3 productos principales", "Hace 6 horas")
            ));
            recentAlertsList.setCellFactory(lv -> new ListCell<>() {
                private final HBox root = new HBox();
                private final StackPane iconWrap = new StackPane();
                private final Circle dot = new Circle(10);
                private final VBox texts = new VBox();
                private final Label msg = new Label();
                private final Label time = new Label();
                {
                    root.getStyleClass().add("alert-item");
                    dot.getStyleClass().add("alert-icon");
                    texts.setSpacing(2);
                    msg.getStyleClass().add("alert-message");
                    time.getStyleClass().add("alert-time");
                    iconWrap.getChildren().add(dot);
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    texts.getChildren().addAll(msg, time);
                    root.getChildren().addAll(iconWrap, texts);
                }
                @Override
                protected void updateItem(AlertItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                    } else {
                        dot.getStyleClass().removeAll("alert-warning","alert-error","alert-info");
                        String typ = item.type == null ? "info" : item.type;
                        switch (typ) {
                            case "warning" -> dot.getStyleClass().add("alert-warning");
                            case "error" -> dot.getStyleClass().add("alert-error");
                            default -> dot.getStyleClass().add("alert-info");
                        }
                        msg.setText(item.message);
                        time.setText(item.time);
                        setGraphic(root);
                    }
                }
            });
        }

        // Period buttons behave like a ToggleGroup (reactive)
        EventStreams.merge(
                EventStreams.eventsOf(periodWeek, javafx.event.ActionEvent.ACTION).map(e -> "Week"),
                EventStreams.eventsOf(periodMonth, javafx.event.ActionEvent.ACTION).map(e -> "Month"),
                EventStreams.eventsOf(periodYear, javafx.event.ActionEvent.ACTION).map(e -> "Year")
        ).subscribe(this::selectPeriod);

        // React to sidebar collapsed state to update UI
        EventStreams.valuesOf(sidebarCollapsedVar).subscribe(this::applySidebarState);

        // Apply initial state
        applySidebarState(false);
    }

    private void setupSalesChart() {
        // Axes are defined in FXML; just clear and add data
        salesChart.getData().clear();

        XYChart.Series<String, Number> real = new XYChart.Series<>();
        real.setName("Real");
        XYChart.Series<String, Number> predicted = new XYChart.Series<>();
        predicted.setName("Predicho");
        String[] months = {"Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic"};
        int[] valuesReal = {60,58,76,79,55,60,70,75,82,88,92,94};
        int[] valuesPred = {62,60,74,77,65,68,73,78,85,90,91,93};
        for (int i = 0; i < months.length; i++) {
            real.getData().add(new XYChart.Data<>(months[i], valuesReal[i]));
            predicted.getData().add(new XYChart.Data<>(months[i], valuesPred[i]));
        }
        salesChart.getData().addAll(real, predicted);
    }

    // Sidebar collapse/expand
    @FXML
    private void toggleSidebar() {
        sidebarCollapsedVar.setValue(!sidebarCollapsedVar.getValue());
    }

    @FXML
    private void collapseSidebar() {
        sidebarCollapsedVar.setValue(true);
    }

    @FXML
    private void expandSidebar() {
        sidebarCollapsedVar.setValue(false);
    }

    private void applySidebarState(boolean collapsed) {
        // Animate width change for smoother UX
        double target = collapsed ? 60 : 200;
        double current = sidebar.getWidth() <= 0 ? sidebar.getPrefWidth() : sidebar.getWidth();
        if (current <= 0) current = collapsed ? 200 : 60; // fallback
        Timeline tl = new Timeline(
                new KeyFrame(javafx.util.Duration.ZERO,
                        new KeyValue(sidebar.prefWidthProperty(), current),
                        new KeyValue(sidebar.minWidthProperty(), current),
                        new KeyValue(sidebar.maxWidthProperty(), current)
                ),
                new KeyFrame(javafx.util.Duration.millis(200),
                        new KeyValue(sidebar.prefWidthProperty(), target),
                        new KeyValue(sidebar.minWidthProperty(), target),
                        new KeyValue(sidebar.maxWidthProperty(), target)
                )
        );
        tl.play();

        // Subtle slide animation
        TranslateTransition tt = new TranslateTransition(Duration.millis(200), sidebar);
        tt.setFromX(collapsed ? 0 : -20);
        tt.setToX(0);
        tt.play();

        // Show only the appropriate top button
        if (collapseBtn != null) {
            collapseBtn.setVisible(!collapsed);
            collapseBtn.setManaged(!collapsed);
        }
        if (expandBtn != null) {
            expandBtn.setVisible(collapsed);
            expandBtn.setManaged(collapsed);
        }

        // Toggle style class for collapsed state (for CSS refinements)
        if (collapsed) {
            if (!sidebar.getStyleClass().contains("sidebar-collapsed")) sidebar.getStyleClass().add("sidebar-collapsed");
        } else {
            sidebar.getStyleClass().remove("sidebar-collapsed");
        }

        // Update each menu button to show only icon when collapsed
        for (ToggleButton b : getMenuButtons()) {
            if (b == null) continue;
            b.setContentDisplay(collapsed ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
            b.setAlignment(collapsed ? javafx.geometry.Pos.CENTER : javafx.geometry.Pos.CENTER_LEFT);
            b.setPrefHeight(36);
            b.setMaxWidth(Double.MAX_VALUE);
            if (collapsed) {
                b.setTooltip(new Tooltip(b.getText()));
            } else {
                b.setTooltip(null);
            }
        }
        // Additional tweaks (e.g., hide text portions) can be handled via CSS .sidebar-collapsed rules
    }

    private java.util.List<ToggleButton> getMenuButtons() {
        return java.util.Arrays.asList(menuDashboard, menuPredictive, menuProfitability, menuSimulation, menuData, menuAlerts, menuReports);
    }

    // Navigation methods to update title/subtitle
    @FXML private void navDashboard() { setSection("Dashboard", "Resumen general de tu negocio"); }
    @FXML private void navPredictive() { setSection("Predictive Analysis", "Modelos y pronósticos"); }
    @FXML private void navProfitability() { setSection("Profitability Evaluation", "Márgenes y costos"); }
    @FXML private void navSimulation() { setSection("Scenario Simulation", "Hipótesis y escenarios"); }
    @FXML private void navData() { setSection("Data Management", "Fuentes y limpieza de datos"); }
    @FXML private void navAlerts() { setSection("Alerts Management", "Alertas y umbrales"); }
    @FXML private void navReports() { setSection("Reports Generation", "Informes y exportaciones"); }

    private void setSection(String title, String subtitle) {
        sectionTitle.setValue(title);
        sectionSubtitle.setValue(subtitle);
    }

    // Period change
    @FXML
    private void onPeriodChange() {
        String selected = periodMonth.isSelected() ? "Month" : periodYear.isSelected() ? "Year" : "Week";
        selectPeriod(selected);
    }

    private void selectPeriod(String p) {
        currentPeriodVar.setValue(p);
        periodWeek.setSelected("Week".equals(p));
        periodMonth.setSelected("Month".equals(p));
        periodYear.setSelected("Year".equals(p));
        // Here you could reload chart/data based on selected period
    }

    // Logout action
    @FXML
    private void handleLogout() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/example/javafxapp/login-view.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 960, 720);
            // Inherit global Atlantafx theme; add base styles.css
            scene.getStylesheets().add(Main.class.getResource("/com/example/javafxapp/styles.css").toExternalForm());
            javafx.stage.Stage loginStage = new javafx.stage.Stage();
            loginStage.setTitle("Sistema BI - Login");
            loginStage.setScene(scene);
            loginStage.show();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // Close current dashboard window
        Stage stage = (Stage) sidebar.getScene().getWindow();
        stage.close();
    }
}
