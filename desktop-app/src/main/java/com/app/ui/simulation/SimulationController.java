package com.app.ui.simulation;

import com.app.model.simulation.ScenarioDTO;
import com.app.model.simulation.ScenarioDTO.VariableValue;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controlador para Simulación de Escenarios (CU-24 a CU-28).
 * Implementa lazy loading y threading para optimizar rendimiento.
 */
public class SimulationController {

    @FXML private ScrollPane mainScroll;
    @FXML private FlowPane scenariosGrid;
    @FXML private VBox emptyState;

    // Panel de edición (CU-25)
    @FXML private VBox editionPanel;
    @FXML private TextField txtScenarioName;
    @FXML private TextArea txtScenarioDesc;
    @FXML private ComboBox<Integer> cmbPeriod;
    @FXML private ComboBox<String> cmbBaseScenario;
    @FXML private VBox variablesList;
    @FXML private Button btnSaveScenario;
    @FXML private Button btnExecute;

    // Panel de vista previa lateral
    @FXML private VBox previewPanel;
    @FXML private VBox changesPreview;

    // Comparación
    @FXML private VBox comparisonPanel;
    @FXML private ComboBox<String> cmbCompare1;
    @FXML private ComboBox<String> cmbCompare2;
    @FXML private Button btnCompare;

    private List<ScenarioDTO> scenarios;
    private ScenarioDTO currentScenario;
    private Map<String, Slider> variableSliders = new HashMap<>();

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    @FXML
    public void initialize() {
        setupUI();
        Platform.runLater(this::loadScenarios);
    }

    private void setupUI() {
        if (cmbPeriod != null) {
            cmbPeriod.getItems().addAll(1, 2, 3, 4, 5, 6);
            cmbPeriod.setValue(3);
        }
        if (cmbBaseScenario != null) {
            cmbBaseScenario.getItems().addAll("Actual", "Optimista", "Pesimista");
            cmbBaseScenario.setValue("Actual");
        }
    }

    private void loadScenarios() {
        Task<List<ScenarioDTO>> task = new Task<>() {
            @Override protected List<ScenarioDTO> call() {
                return getMockScenarios();
            }
            @Override protected void succeeded() {
                scenarios = getValue();
                renderScenarioGrid();
            }
        };
        new Thread(task).start();
    }

    private void renderScenarioGrid() {
        scenariosGrid.getChildren().clear();

        // Card "+" siempre primero
        scenariosGrid.getChildren().add(buildNewScenarioCard());

        // Cards de escenarios existentes
        for (ScenarioDTO s : scenarios) {
            scenariosGrid.getChildren().add(buildScenarioCard(s));
        }

        emptyState.setVisible(scenarios.isEmpty());
        emptyState.setManaged(scenarios.isEmpty());
    }

    private VBox buildNewScenarioCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("scenario-card-new");
        card.setAlignment(Pos.CENTER);
        card.setOnMouseClicked(e -> onCreateNew());

        Label plus = new Label("+");
        plus.getStyleClass().add("new-scenario-icon");
        Label lbl = new Label("Nuevo escenario");
        lbl.getStyleClass().add("new-scenario-label");

        card.getChildren().addAll(plus, lbl);
        return card;
    }

    private VBox buildScenarioCard(ScenarioDTO s) {
        VBox card = new VBox(8);
        card.getStyleClass().add("scenario-card");

        // Header con nombre
        Label name = new Label(s.name());
        name.getStyleClass().add("scenario-card-name");

        // Descripción
        Label desc = new Label(s.description());
        desc.getStyleClass().add("scenario-card-desc");
        desc.setWrapText(true);

        // Meta info
        HBox meta = new HBox(12);
        meta.setAlignment(Pos.CENTER_LEFT);
        Label period = new Label(s.periodMonths() + " meses");
        period.getStyleClass().add("scenario-meta");
        Label vars = new Label(s.modifiedVars() + " var. modificadas");
        vars.getStyleClass().add("scenario-meta");
        meta.getChildren().addAll(period, vars);

        // Badge de estado
        Label status = new Label(statusLabel(s.status()));
        status.getStyleClass().addAll("scenario-badge",
                "badge-" + s.status().toLowerCase());

        // Botones de acción
        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button btnExecute = new Button("Ejecutar");
        btnExecute.getStyleClass().add("btn-scenario-execute");
        btnExecute.setOnAction(e -> onExecuteScenario(s));

        Button btnEdit = createIconButton("/images/simulation/icon-edit.png");
        btnEdit.setOnAction(e -> onEditScenario(s));

        Button btnDelete = createIconButton("/images/simulation/icon-delete.png");
        btnDelete.setOnAction(e -> onDeleteScenario(s));

        actions.getChildren().addAll(btnExecute, btnEdit, btnDelete);

        card.getChildren().addAll(name, desc, meta, status, actions);
        return card;
    }

    private Button createIconButton(String iconPath) {
        Button btn = new Button();
        btn.getStyleClass().add("btn-icon-sm");
        try {
            ImageView icon = new ImageView(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream(iconPath))));
            icon.setFitWidth(16);
            icon.setFitHeight(16);
            btn.setGraphic(icon);
        } catch (Exception e) {
            btn.setText("•");
        }
        return btn;
    }

    @FXML
    private void onCreateNew() {
        System.out.println("[SIMULATION] Crear nuevo escenario");
        currentScenario = null;
        showEditionPanel(true);
        buildVariablesEditor(getDefaultVariables());
    }

    private void onEditScenario(ScenarioDTO s) {
        System.out.printf("[SIMULATION] Editar escenario id=%d%n", s.id());
        currentScenario = s;
        showEditionPanel(true);
        txtScenarioName.setText(s.name());
        txtScenarioDesc.setText(s.description());
        cmbPeriod.setValue(s.periodMonths());
        cmbBaseScenario.setValue(s.baseScenario());
        buildVariablesEditor(s.variables());
    }

    private void onExecuteScenario(ScenarioDTO s) {
        System.out.printf("[SIMULATION] Ejecutar escenario id=%d%n", s.id());
        // TODO: mostrar progreso y resultados
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Simulación");
        info.setContentText("Ejecutando escenario '" + s.name() + "'...");
        info.showAndWait();
    }

    private void onDeleteScenario(ScenarioDTO s) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar escenario");
        confirm.setContentText("¿Eliminar '" + s.name() + "'?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                scenarios.remove(s);
                renderScenarioGrid();
            }
        });
    }

    private void buildVariablesEditor(Map<String, VariableValue> vars) {
        variablesList.getChildren().clear();
        variableSliders.clear();
        changesPreview.getChildren().clear();

        vars.forEach((key, v) -> {
            VBox varBox = new VBox(6);
            varBox.getStyleClass().add("variable-editor-row");

            Label lbl = new Label(v.name());
            lbl.getStyleClass().add("variable-label");

            HBox sliderRow = new HBox(10);
            sliderRow.setAlignment(Pos.CENTER_LEFT);

            Slider slider = new Slider(v.min(), v.max(), v.currentValue());
            slider.setShowTickMarks(true);
            slider.setMajorTickUnit((v.max() - v.min()) / 4);
            HBox.setHgrow(slider, Priority.ALWAYS);

            Label valueLbl = new Label(String.format("%.0f %s",
                    v.currentValue(), v.unit()));
            valueLbl.getStyleClass().add("variable-value-label");
            valueLbl.setMinWidth(80);

            slider.valueProperty().addListener((obs, old, newVal) -> {
                double val = newVal.doubleValue();
                valueLbl.setText(String.format("%.0f %s", val, v.unit()));
                updatePreview(key, v, val);
            });

            sliderRow.getChildren().addAll(slider, valueLbl);
            varBox.getChildren().addAll(lbl, sliderRow);
            variablesList.getChildren().add(varBox);
            variableSliders.put(key, slider);

            // Agregar a preview inicial
            updatePreview(key, v, v.currentValue());
        });
    }

    private void updatePreview(String key, VariableValue v, double newVal) {
        // Buscar o crear fila en preview
        changesPreview.getChildren().removeIf(n ->
                n.getUserData() != null && n.getUserData().equals(key));

        double change = ((newVal - v.baseValue()) / v.baseValue()) * 100;
        if (Math.abs(change) < 0.1) return; // sin cambio significativo

        HBox row = new HBox(8);
        row.setUserData(key);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("preview-change-row");

        Label icon = new Label(change > 0 ? "▲" : "▼");
        icon.getStyleClass().add(change > 0 ? "change-up" : "change-down");

        Label varName = new Label(v.name());
        varName.getStyleClass().add("preview-var-name");
        HBox.setHgrow(varName, Priority.ALWAYS);

        Label changeLbl = new Label(String.format("%+.1f%%", change));
        changeLbl.getStyleClass().add(change > 0 ? "change-up" : "change-down");

        row.getChildren().addAll(icon, varName, changeLbl);
        changesPreview.getChildren().add(row);
    }

    private void showEditionPanel(boolean show) {
        editionPanel.setVisible(show);
        editionPanel.setManaged(show);
    }

    @FXML
    private void onSaveScenario() {
        System.out.println("[SIMULATION] Guardar escenario");
        // TODO: construir ScenarioDTO con valores de sliders
        showEditionPanel(false);
        loadScenarios();
    }

    @FXML
    private void onCompareScenarios() {
        System.out.println("[SIMULATION] Comparar escenarios");
        // TODO: mostrar vista de comparación
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "DRAFT" -> "Borrador";
            case "CONFIGURED" -> "Configurado";
            case "EXECUTED" -> "Ejecutado";
            default -> status;
        };
    }

//    private void showEditionPanel(boolean b) {
//    }

    // ══════════════════════════════════════════════════════════════════════
    //  MOCK DATA
    // ══════════════════════════════════════════════════════════════════════

    private List<ScenarioDTO> getMockScenarios() {
        return List.of(
                new ScenarioDTO(1L, "Incremento de precios 10%",
                        "Simula aumento general de precios en 10%",
                        3, "Actual", "CONFIGURED", 2,
                        getDefaultVariables(), 85.0,
                        LocalDateTime.now().minusDays(5), "Admin",
                        null, "SHARED"),
                new ScenarioDTO(2L, "Reducción de costos",
                        "Optimización de costos operativos -15%",
                        6, "Optimista", "EXECUTED", 3,
                        getDefaultVariables(), 78.0,
                        LocalDateTime.now().minusDays(10), "Admin",
                        LocalDateTime.now().minusDays(2), "PRIVATE")
        );
    }

    private Map<String, VariableValue> getDefaultVariables() {
        Map<String, VariableValue> vars = new LinkedHashMap<>();
        vars.put("precio", new VariableValue("Precio unitario",
                100, 100, "$", 0, 50, 150));
        vars.put("volumen", new VariableValue("Volumen de ventas",
                1000, 1000, "unidades", 0, 500, 1500));
        vars.put("costos", new VariableValue("Costos variables",
                60, 60, "$", 0, 30, 90));
        vars.put("demanda", new VariableValue("Demanda proyectada",
                100, 100, "%", 0, 50, 150));
        return vars;
    }
}