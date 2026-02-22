package com.app.ui.simulation;

import com.app.model.simulation.ScenarioDTO;
import com.app.model.simulation.ScenarioDTO.VariableValue;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SimulationController — Módulo de Simulación de Escenarios
 *
 * NOTA: Actualmente usa datos mock.
 *       Para conectar con un servicio real, sustituye los métodos getMock*()
 *       y en logEvent() reemplaza el TODO por la llamada asíncrona correspondiente.
 */
public class SimulationController {

    @FXML private ScrollPane   mainScroll;
    @FXML private FlowPane     scenariosGrid;
    @FXML private VBox         emptyState;
    @FXML private Label        scenarioCountLabel;

    @FXML private VBox              editionPanel;
    @FXML private Label             editionPanelTitle;
    @FXML private ComboBox<String>  cmbTrainedModel;
    @FXML private HBox              modelInfoBox;
    @FXML private Label             modelInfoIcon;
    @FXML private Label             modelInfoName;
    @FXML private Label             modelInfoMeta;
    @FXML private TextField         txtScenarioName;
    @FXML private TextArea          txtScenarioDesc;
    @FXML private ComboBox<Integer> cmbPeriod;
    @FXML private ComboBox<String>  cmbBaseScenario;
    @FXML private VBox              variablesList;
    @FXML private Button            btnSaveScenario;
    @FXML private Button            btnExecute;

    @FXML private VBox        processingPanel;
    @FXML private ImageView   processingIcon;
    @FXML private Label       processingTitle;
    @FXML private Label       processingSubtitle;
    @FXML private ProgressBar processingBar;
    @FXML private Label       processingStep;

    @FXML private VBox                      resultsPanel;
    @FXML private Label                     resultsTitleLabel;
    @FXML private Label                     resultsMetaLabel;
    @FXML private HBox                      resultKpisBox;
    @FXML private LineChart<String, Number> comparisonChart;
    @FXML private BarChart<String, Number>  monthlyDeltaChart;
    @FXML private LineChart<String, Number> variablesChart;
    @FXML private VBox                      changesSummaryContainer;

    @FXML private VBox             comparisonPanel;
    @FXML private Label            comparisonNoticeLabel;
    @FXML private ComboBox<String> cmbCompare1;
    @FXML private ComboBox<String> cmbCompare2;
    @FXML private Button           btnCompare;
    @FXML private VBox             comparisonResultContainer;

    // ─── Caché de eventos ────────────────────────────────────────────────────
    public enum ActionType {
        SCENARIO_CREATE_STARTED, SCENARIO_EDIT_STARTED, SCENARIO_SAVED_DRAFT,
        SCENARIO_DELETED, MODEL_SELECTED, VARIABLE_ADJUSTED,
        SIMULATION_EXECUTED, SIMULATION_RESULTS_VIEWED,
        COMPARISON_OPENED, COMPARISON_EXECUTED, RESULTS_EXPORTED, NEW_SIMULATION_STARTED
    }

    public record UserActionEvent(ActionType type, LocalDateTime timestamp, Map<String, Object> payload) {
        @Override public String toString() {
            return "[" + timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + "] "
                    + type + " → " + payload;
        }
    }

    private final List<UserActionEvent> userEventLog = new ArrayList<>();

    private void logEvent(ActionType type, Map<String, Object> payload) {
        UserActionEvent e = new UserActionEvent(type, LocalDateTime.now(),
                payload != null ? Collections.unmodifiableMap(payload) : Map.of());
        userEventLog.add(e);
        // TODO: eventService.sendAsync(e);
        System.out.printf("[EVENT] %s%n", e);
    }

    public List<UserActionEvent> getUserEventLog() { return Collections.unmodifiableList(userEventLog); }

    // ─── Estado ──────────────────────────────────────────────────────────────
    private List<ScenarioDTO>   scenarios       = new ArrayList<>();
    private ScenarioDTO         currentScenario = null;
    private ScenarioDTO         lastExecuted    = null;
    private Map<String, Slider> variableSliders = new LinkedHashMap<>();
    private RotateTransition    iconRotation;

    private static final String[] MONTHS =
            {"Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic"};

    // ═════════════════════════════════════════════════════════════════════════
    //  INIT
    // ═════════════════════════════════════════════════════════════════════════

    @FXML public void initialize() {
        setupCombos();
        hideImmediate(editionPanel);
        hideImmediate(processingPanel);
        hideImmediate(resultsPanel);
        hideImmediate(comparisonPanel);
        Platform.runLater(this::loadScenarios);
    }

    private void setupCombos() {
        cmbPeriod.getItems().addAll(1,2,3,4,5,6);
        cmbPeriod.setValue(3);
        cmbBaseScenario.getItems().addAll("Actual","Optimista","Pesimista");
        cmbBaseScenario.setValue("Actual");
        getMockTrainedModels().forEach(m -> cmbTrainedModel.getItems().add(m.displayName()));
        cmbTrainedModel.getSelectionModel().selectedItemProperty()
                .addListener((obs,o,sel) -> onModelSelected(sel));
    }

    private void hideImmediate(VBox p) {
        p.setVisible(false); p.setManaged(false);
        p.setOpacity(1);    p.setTranslateY(0);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  GRID
    // ═════════════════════════════════════════════════════════════════════════

    private void loadScenarios() {
        Task<List<ScenarioDTO>> t = new Task<>() {
            @Override protected List<ScenarioDTO> call() { return getMockScenarios(); }
            @Override protected void succeeded() {
                scenarios = new ArrayList<>(getValue());
                renderScenarioGrid();
                refreshComparisonPanel();
            }
        };
        new Thread(t).start();
    }

    private void renderScenarioGrid() {
        scenariosGrid.getChildren().clear();
        scenariosGrid.getChildren().add(buildNewCard());
        scenarios.forEach(s -> scenariosGrid.getChildren().add(buildScenarioCard(s)));
        boolean empty = scenarios.isEmpty();
        emptyState.setVisible(empty); emptyState.setManaged(empty);
    }

    private VBox buildNewCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("scenario-card-new");
        card.setAlignment(Pos.CENTER);
        card.setOnMouseClicked(e -> onCreateNew());
        card.getChildren().addAll(
                lbl("+", "new-scenario-icon"),
                lbl("Nuevo escenario", "new-scenario-label"),
                lbl("Crea y ejecuta un escenario personalizado", "new-scenario-hint")
        );
        return card;
    }

    private VBox buildScenarioCard(ScenarioDTO s) {
        VBox card = new VBox(10);
        card.getStyleClass().add("scenario-card");

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label name = lbl(s.name(), "scenario-card-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        head.getChildren().addAll(name, lbl(statusLabel(s.status()), "scenario-badge", "badge-" + s.status().toLowerCase()));

        Label desc = lbl(s.description(), "scenario-card-desc");
        desc.setWrapText(true);

        HBox chips = new HBox(8);
        chips.setAlignment(Pos.CENTER_LEFT);
        chips.getChildren().addAll(chip("📅 " + s.periodMonths() + " meses"), chip("⚙ " + s.modifiedVars() + " vars"), chip("👤 " + s.author()));
        if (s.modelName() != null && !s.modelName().isBlank()) {
            Label mc = chip("🧠 " + s.modelName().split("—")[0].trim());
            mc.getStyleClass().add("chip-model"); chips.getChildren().add(mc);
        }

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button bEx = new Button("▶  Ejecutar"); bEx.getStyleClass().add("btn-scenario-execute"); bEx.setOnAction(e -> onExecuteScenario(s));
        Button bEd = new Button("✏  Editar");   bEd.getStyleClass().add("btn-icon-sm");           bEd.setOnAction(e -> onEditScenario(s));
        Button bDl = new Button("🗑");           bDl.getStyleClass().addAll("btn-icon-sm","btn-icon-danger"); bDl.setOnAction(e -> onDeleteScenario(s));
        actions.getChildren().addAll(bEx, bEd, bDl);

        card.getChildren().addAll(head, desc, chips, new Separator(), actions);
        return card;
    }

    private Label chip(String t) { Label l = new Label(t); l.getStyleClass().add("scenario-chip"); return l; }

    // ═════════════════════════════════════════════════════════════════════════
    //  MODELO ENTRENADO
    // ═════════════════════════════════════════════════════════════════════════

    private void onModelSelected(String displayName) {
        if (displayName == null) return;
        getMockTrainedModels().stream().filter(m -> m.displayName().equals(displayName)).findFirst().ifPresent(info -> {
            modelInfoName.setText(info.displayName());
            modelInfoMeta.setText("Tipo: " + info.type() + "  ·  R² " + info.r2() + "  ·  Entrenado " + info.trainedDate());
            modelInfoBox.setVisible(true); modelInfoBox.setManaged(true);
            buildVariablesEditor(getVariablesForModel(info.type()));
            logEvent(ActionType.MODEL_SELECTED, Map.of("model",displayName,"type",info.type(),"r2",info.r2()));
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EDITOR DE VARIABLES
    // ═════════════════════════════════════════════════════════════════════════

    private void buildVariablesEditor(Map<String, VariableValue> vars) {
        variablesList.getChildren().clear();
        variableSliders.clear();
        vars.forEach((key, v) -> {
            VBox row = new VBox(10); row.getStyleClass().add("variable-editor-row");
            HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT);
            Label icon = lbl(varIcon(key), "var-row-icon");
            Label nameLbl = lbl(v.name(), "variable-label"); HBox.setHgrow(nameLbl, Priority.ALWAYS);
            Label baseLbl = lbl("Base: " + fmt(v.baseValue()) + " " + v.unit(), "variable-base-value");
            header.getChildren().addAll(icon, nameLbl, baseLbl);

            double min = v.baseValue() * 0.50, max = v.baseValue() * 1.50;
            Slider slider = new Slider(min, max, v.currentValue());
            slider.setSnapToTicks(false); slider.getStyleClass().add("var-slider");
            HBox.setHgrow(slider, Priority.ALWAYS);

            Label valueLbl = lbl(fmt(v.currentValue()) + " " + v.unit(), "variable-value-label"); valueLbl.setMinWidth(100);
            Label pctLbl   = lbl("0%", "variable-percent-label", "variable-percent-neu");           pctLbl.setMinWidth(58);
            Label warnLbl  = lbl("⚠  Valor ajustado al límite ±50%", "variable-warn-label");
            warnLbl.setVisible(false); warnLbl.setManaged(false);

            slider.valueProperty().addListener((obs, old, nv) -> {
                double val = nv.doubleValue(); boolean clamped = false;
                if (val < min) { val = min; slider.setValue(min); clamped = true; }
                if (val > max) { val = max; slider.setValue(max); clamped = true; }
                double pct = ((val - v.baseValue()) / v.baseValue()) * 100;
                valueLbl.setText(fmt(val) + " " + v.unit());
                pctLbl.setText(String.format("%+.1f%%", pct));
                pctLbl.getStyleClass().removeAll("variable-percent-pos","variable-percent-neg","variable-percent-neu");
                pctLbl.getStyleClass().add(pct > 0.5 ? "variable-percent-pos" : pct < -0.5 ? "variable-percent-neg" : "variable-percent-neu");
                warnLbl.setVisible(clamped); warnLbl.setManaged(clamped);
                row.getStyleClass().removeAll("variable-warn"); if (clamped) row.getStyleClass().add("variable-warn");
                final double fv = val;
                logEvent(ActionType.VARIABLE_ADJUSTED, Map.of("variable",key,"name",v.name(),"value",fv,"pct",pct));
            });

            HBox sliderRow = new HBox(12); sliderRow.setAlignment(Pos.CENTER_LEFT);
            sliderRow.getChildren().addAll(slider, valueLbl, pctLbl);
            row.getChildren().addAll(header, sliderRow, warnLbl);
            variablesList.getChildren().add(row);
            variableSliders.put(key, slider);
        });
    }

    private String varIcon(String key) {
        return switch (key) {
            case "precio"    -> "💲";
            case "volumen"   -> "📦";
            case "costos"    -> "💸";
            case "demanda"   -> "📊";
            case "ventas"    -> "🛒";
            case "estacion"  -> "🌤";
            case "tendencia" -> "📈";
            case "stock"     -> "🗃";
            case "descuento" -> "🏷";
            default          -> "⚙";
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  FORMULARIO
    // ═════════════════════════════════════════════════════════════════════════

    @FXML private void onCreateNew() {
        currentScenario = null;
        editionPanelTitle.setText("Nuevo escenario");
        cmbTrainedModel.getSelectionModel().clearSelection();
        modelInfoBox.setVisible(false); modelInfoBox.setManaged(false);
        txtScenarioName.clear(); txtScenarioDesc.clear();
        variablesList.getChildren().clear();
        logEvent(ActionType.SCENARIO_CREATE_STARTED, Map.of());
        if (resultsPanel.isVisible()) slideOut(resultsPanel, () -> slideIn(editionPanel));
        else slideIn(editionPanel);
    }

    private void onEditScenario(ScenarioDTO s) {
        currentScenario = s;
        editionPanelTitle.setText("Editar: " + s.name());
        txtScenarioName.setText(s.name()); txtScenarioDesc.setText(s.description());
        cmbPeriod.setValue(s.periodMonths()); cmbBaseScenario.setValue(s.baseScenario());
        buildVariablesEditor(s.variables());
        logEvent(ActionType.SCENARIO_EDIT_STARTED, Map.of("scenarioId",s.id(),"scenarioName",s.name()));
        if (resultsPanel.isVisible()) slideOut(resultsPanel, () -> slideIn(editionPanel));
        else slideIn(editionPanel);
    }

    @FXML private void onCloseEdition() { slideOut(editionPanel, null); }

    @FXML private void onSaveScenario() {
        if (!validateForm(false)) return;
        ScenarioDTO sc = buildScenarioFromForm();
        if (currentScenario != null) scenarios.replaceAll(s -> s.id() == currentScenario.id() ? sc : s);
        else scenarios.add(sc);
        logEvent(ActionType.SCENARIO_SAVED_DRAFT, Map.of("scenarioName",sc.name(),"period",sc.periodMonths()));
        slideOut(editionPanel, () -> { renderScenarioGrid(); refreshComparisonPanel(); });
    }

    @FXML private void onExecuteFromForm() {
        if (!validateForm(true)) return;
        ScenarioDTO sc = buildScenarioFromForm();
        slideOut(editionPanel, () -> runSimulation(sc));
    }

    private void onExecuteScenario(ScenarioDTO s) {
        if (editionPanel.isVisible()) slideOut(editionPanel, () -> runSimulation(s));
        else runSimulation(s);
    }

    private boolean validateForm(boolean requireModel) {
        if (requireModel && cmbTrainedModel.getSelectionModel().isEmpty()) {
            showAlert(Alert.AlertType.WARNING,"Modelo requerido","Selecciona un modelo predictivo entrenado."); return false;
        }
        if (txtScenarioName.getText().isBlank()) {
            showAlert(Alert.AlertType.WARNING,"Nombre requerido","El escenario debe tener un nombre."); return false;
        }
        if (cmbPeriod.getValue() == null) {
            showAlert(Alert.AlertType.WARNING,"Período requerido","Selecciona el período de simulación."); return false;
        }
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ANIMACIONES
    // ═════════════════════════════════════════════════════════════════════════

    private void slideIn(VBox panel) {
        panel.setOpacity(0); panel.setTranslateY(-18);
        panel.setVisible(true); panel.setManaged(true);
        new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(panel.opacityProperty(),    0,  Interpolator.EASE_OUT),
                        new KeyValue(panel.translateYProperty(),-18, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(260),
                        new KeyValue(panel.opacityProperty(),    1,  Interpolator.EASE_OUT),
                        new KeyValue(panel.translateYProperty(), 0,  Interpolator.EASE_OUT))
        ).play();
        Platform.runLater(() -> mainScroll.setVvalue(panel == resultsPanel ? 0.3 : 0.2));
    }

    private void slideOut(VBox panel, Runnable after) {
        if (!panel.isVisible()) { if (after != null) Platform.runLater(after); return; }
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(panel.opacityProperty(),    1,  Interpolator.EASE_IN),
                        new KeyValue(panel.translateYProperty(), 0,  Interpolator.EASE_IN)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(panel.opacityProperty(),    0,  Interpolator.EASE_IN),
                        new KeyValue(panel.translateYProperty(),-14, Interpolator.EASE_IN))
        );
        tl.setOnFinished(e -> {
            panel.setVisible(false); panel.setManaged(false);
            panel.setTranslateY(0);  panel.setOpacity(1);
            if (after != null) after.run();
        });
        tl.play();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SIMULACIÓN
    // ═════════════════════════════════════════════════════════════════════════

    private void runSimulation(ScenarioDTO sc) {
        slideIn(processingPanel);
        startProcessingAnimation();
        logEvent(ActionType.SIMULATION_EXECUTED, Map.of(
                "scenarioId",sc.id(),"scenarioName",sc.name(),
                "period",sc.periodMonths(),"baseScenario",sc.baseScenario()));

        String[] steps = {
                "Cargando modelo predictivo base…",
                "Aplicando variaciones configuradas…",
                "Calculando proyección de " + sc.periodMonths() + " meses…",
                "Generando comparativa base vs simulado…",
                "Preparando resultados…"
        };

        Task<ScenarioDTO> task = new Task<>() {
            @Override protected ScenarioDTO call() throws InterruptedException {
                for (String step : steps) { Platform.runLater(() -> processingStep.setText(step)); Thread.sleep(650); }
                return new ScenarioDTO(sc.id(),sc.name(),sc.description(),sc.periodMonths(),sc.baseScenario(),
                        "EXECUTED",sc.modifiedVars(),sc.variables(),92.0,sc.createdAt(),sc.author(),
                        LocalDateTime.now(),sc.visibility(),sc.modelName());
            }
            @Override protected void succeeded() {
                ScenarioDTO ex = getValue();
                scenarios.replaceAll(s -> s.id() == ex.id() ? ex : s);
                lastExecuted = ex;
                stopProcessingAnimation();
                renderScenarioGrid();
                refreshComparisonPanel();
                slideOut(processingPanel, () -> { buildResults(ex); slideIn(resultsPanel);
                    logEvent(ActionType.SIMULATION_RESULTS_VIEWED, Map.of("scenarioId",ex.id(),"scenarioName",ex.name()));
                });
            }
            @Override protected void failed() {
                stopProcessingAnimation(); slideOut(processingPanel, null);
                showAlert(Alert.AlertType.ERROR,"Error en simulación","Ocurrió un error al procesar. Intenta de nuevo.");
            }
        };
        new Thread(task).start();
    }

    private void startProcessingAnimation() {
        // FIX #8: loading.png en lugar de brain.png
        try {
            var s = getClass().getResourceAsStream("/images/icons/loading.png");
            if (s != null) processingIcon.setImage(new Image(s));
        } catch (Exception ignored) {}
        processingTitle.setText("Procesando simulación…");
        processingSubtitle.setText("Aplicando variaciones al modelo predictivo base");
        processingStep.setText("Iniciando…");
        processingIcon.setRotate(0);
        iconRotation = new RotateTransition(Duration.seconds(1.4), processingIcon);
        iconRotation.setFromAngle(0); iconRotation.setToAngle(360);
        iconRotation.setCycleCount(Animation.INDEFINITE); iconRotation.play();
    }

    private void stopProcessingAnimation() {
        if (iconRotation != null) { iconRotation.stop(); processingIcon.setRotate(0); }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  RESULTADOS
    // ═════════════════════════════════════════════════════════════════════════

    private void buildResults(ScenarioDTO s) {
        resultsTitleLabel.setText("Resultados — " + s.name());
        String ml = cmbTrainedModel.getValue() != null ? "  ·  " + cmbTrainedModel.getValue() : "";
        resultsMetaLabel.setText(s.periodMonths() + " meses  ·  Base: " + s.baseScenario() + ml + "  ·  " + formatDateTime(s.executedAt()));
        buildResultKpis(s);
        buildComparisonChart(s);
        buildMonthlyDeltaChart(s);
        buildVariablesChart(s);
        buildChangesSummary(s);
    }

    private void buildResultKpis(ScenarioDTO s) {
        resultKpisBox.getChildren().clear();
        List<String[]> kpis = List.of(
                new String[]{"📈","+12.4%",       "Variación proyectada","vs escenario base","positive"},
                new String[]{"💰","$184,320",      "Ingreso estimado",    "Suma del período", "neutral"},
                new String[]{"⚡",fmt(s.confidence())+"%","Confianza",   "Índice del modelo","neutral"},
                new String[]{"📉","-3.2%",         "Costo incremental",  "Impacto en costos","negative"}
        );
        kpis.forEach(k -> {
            VBox card = new VBox(6); card.getStyleClass().add("result-kpi-card"); HBox.setHgrow(card, Priority.ALWAYS);
            card.getChildren().addAll(lbl(k[0],"result-kpi-icon"), lbl(k[1],"result-kpi-value","result-kpi-"+k[4]),
                    lbl(k[2],"result-kpi-label"), lbl(k[3],"result-kpi-sub"));
            resultKpisBox.getChildren().add(card);
        });
    }

    private void buildComparisonChart(ScenarioDTO s) {
        comparisonChart.getData().clear();
        XYChart.Series<String, Number> base = new XYChart.Series<>(); base.setName("Escenario base");
        XYChart.Series<String, Number> sim  = new XYChart.Series<>(); sim.setName("Simulado");
        Random rng = new Random(42 + s.id()); double bv = 15000, sv = 15000;
        for (int i = 0; i < s.periodMonths() * 4; i++) {
            String l = MONTHS[(i/4)%12] + " S" + (i%4+1);
            bv += rng.nextGaussian()*400+120; sv += rng.nextGaussian()*380+200+(i*18);
            base.getData().add(new XYChart.Data<>(l, Math.max(bv,1000)));
            sim.getData().add(new XYChart.Data<>(l, Math.max(sv,1000)));
        }
        comparisonChart.getData().addAll(base, sim);
    }

    private void buildMonthlyDeltaChart(ScenarioDTO s) {
        monthlyDeltaChart.getData().clear();

        XYChart.Series<String, Number> delta = new XYChart.Series<>();
        delta.setName("Variacion mensual (%)");   // aparece en leyenda

        Random rng = new Random(7 + s.id());
        for (int i = 0; i < s.periodMonths(); i++) {
            double val = 5 + rng.nextGaussian() * 4;
            XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(MONTHS[i % 12], val);
            delta.getData().add(dataPoint);
        }
        monthlyDeltaChart.getData().add(delta);

        // Agregar labels con valor encima de cada barra, después de que los nodos existan
        Platform.runLater(() -> {
            for (XYChart.Data<String, Number> d : delta.getData()) {
                if (d.getNode() != null) {
                    double pct = d.getYValue().doubleValue();
                    Label lbl = new Label(String.format("%.1f%%", pct));
                    lbl.getStyleClass().add("bar-data-label");
                    // Posicionar encima de la barra
                    StackPane bar = (StackPane) d.getNode();
                    bar.getChildren().add(lbl);
                    StackPane.setAlignment(lbl, pct >= 0 ? Pos.TOP_CENTER : Pos.BOTTOM_CENTER);
                }
            }
        });
    }

    private void buildVariablesChart(ScenarioDTO s) {
        variablesChart.getData().clear();
        Random rng = new Random(13 + s.id()); int idx = 0;
        for (Map.Entry<String, VariableValue> e : s.variables().entrySet()) {
            VariableValue vv = e.getValue();
            XYChart.Series<String, Number> ser = new XYChart.Series<>(); ser.setName(vv.name());
            double val = vv.currentValue();
            for (int i = 0; i < s.periodMonths(); i++) {
                val += rng.nextGaussian()*(vv.currentValue()*0.03)+(vv.currentValue()*0.01);
                ser.getData().add(new XYChart.Data<>(MONTHS[i%12], Math.max(val,0)));
            }
            variablesChart.getData().add(ser);
            if (++idx >= 4) break;
        }
    }

    /**
     * FIX #4: Tarjetas visuales en cuadrícula — barra de progreso + badge de porcentaje
     */
    private void buildChangesSummary(ScenarioDTO s) {
        changesSummaryContainer.getChildren().clear();
        FlowPane grid = new FlowPane(14, 14);
        grid.setPrefWrapLength(9999);

        for (Map.Entry<String, VariableValue> entry : s.variables().entrySet()) {
            String key = entry.getKey();
            VariableValue v = entry.getValue();
            double newVal = variableSliders.containsKey(key) ? variableSliders.get(key).getValue() : v.currentValue();
            double pct    = ((newVal - v.baseValue()) / v.baseValue()) * 100;
            boolean up = pct > 0.5, down = pct < -0.5;
            double progress = Math.min(Math.max((newVal - v.min()) / Math.max(v.max()-v.min(),1), 0), 1);

            VBox card = new VBox(10); card.getStyleClass().add("summary-var-card");

            // Cabecera
            HBox head = new HBox(8); head.setAlignment(Pos.CENTER_LEFT);
            head.getChildren().addAll(lbl(varIcon(key),"summary-var-icon"), lbl(v.name(),"summary-var-name"));

            // Valores base → nuevo
            HBox vals = new HBox(10); vals.setAlignment(Pos.CENTER_LEFT);
            vals.getChildren().addAll(
                    lbl(fmt(v.baseValue())+" "+v.unit(), "summary-base-val"),
                    lbl("→", "summary-arrow", up?"arrow-up":down?"arrow-down":"arrow-neu"),
                    lbl(fmt(newVal)+" "+v.unit(), "summary-new-val", up?"val-up":down?"val-down":"val-neu")
            );

            // Barra de progreso
            StackPane barBg = new StackPane(); barBg.getStyleClass().add("var-bar-bg"); barBg.setPrefHeight(6);
            HBox fill = new HBox(); fill.getStyleClass().addAll("var-bar-fill", up?"bar-positive":down?"bar-negative":"bar-neutral");
            fill.setPrefHeight(6);
            barBg.widthProperty().addListener((obs, oldW, w) -> fill.setPrefWidth(w.doubleValue()*progress));
            barBg.getChildren().add(fill); StackPane.setAlignment(fill, Pos.CENTER_LEFT);

            // Badge
            HBox footer = new HBox(); footer.setAlignment(Pos.CENTER_RIGHT);
            footer.getChildren().add(lbl(String.format("%+.1f%%",pct), "var-pct-badge",
                    up?"badge-positive":down?"badge-negative":"badge-neutral"));

            card.getChildren().addAll(head, vals, barBg, footer);
            grid.getChildren().add(card);
        }
        changesSummaryContainer.getChildren().add(grid);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ACCIONES RESULTADOS
    // ═════════════════════════════════════════════════════════════════════════

    @FXML private void onNewSimulation() { logEvent(ActionType.NEW_SIMULATION_STARTED, Map.of()); slideOut(resultsPanel, this::onCreateNew); }
    @FXML private void onExportResults() {
        logEvent(ActionType.RESULTS_EXPORTED, Map.of("scenarioId", lastExecuted != null ? lastExecuted.id() : -1));
        showAlert(Alert.AlertType.INFORMATION,"Exportación","Los resultados han sido exportados correctamente.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  COMPARACIÓN
    // ═════════════════════════════════════════════════════════════════════════

    private void refreshComparisonPanel() {
        List<ScenarioDTO> ex = getExecutedScenarios();
        if (ex.size() < 2) { hideImmediate(comparisonPanel); return; }
        comparisonPanel.setVisible(true); comparisonPanel.setManaged(true);
        List<String> names = ex.stream().map(ScenarioDTO::name).toList();
        cmbCompare1.getItems().setAll(names); cmbCompare2.getItems().setAll(names);
        comparisonResultContainer.getChildren().clear();
        comparisonNoticeLabel.setText("Selecciona dos escenarios ejecutados con el mismo período y variables.");
        logEvent(ActionType.COMPARISON_OPENED, Map.of("executedCount", ex.size()));
    }

    @FXML private void onCompareScenarios() {
        String nA = cmbCompare1.getValue(), nB = cmbCompare2.getValue();
        if (nA == null || nB == null) { showAlert(Alert.AlertType.WARNING,"Selección incompleta","Selecciona ambos escenarios."); return; }
        if (nA.equals(nB))           { showAlert(Alert.AlertType.WARNING,"Selección inválida","Selecciona dos escenarios distintos."); return; }

        List<ScenarioDTO> ex = getExecutedScenarios();
        ScenarioDTO sA = ex.stream().filter(s->s.name().equals(nA)).findFirst().orElse(null);
        ScenarioDTO sB = ex.stream().filter(s->s.name().equals(nB)).findFirst().orElse(null);
        if (sA == null || sB == null) return;

        if (sA.periodMonths() != sB.periodMonths()) {
            showAlert(Alert.AlertType.WARNING,"Período incompatible",
                    String.format("'%s' tiene %d mes(es) y '%s' tiene %d mes(es). Deben coincidir.",
                            sA.name(),sA.periodMonths(),sB.name(),sB.periodMonths())); return;
        }
        Set<String> kA = sA.variables().keySet(), kB = sB.variables().keySet();
        if (!kA.equals(kB)) {
            Set<String> oA=new HashSet<>(kA); oA.removeAll(kB);
            Set<String> oB=new HashSet<>(kB); oB.removeAll(kA);
            showAlert(Alert.AlertType.WARNING,"Variables incompatibles",
                    "No comparten las mismas variables." +
                            (oA.isEmpty()?"":" Solo en '"+sA.name()+"': "+oA) +
                            (oB.isEmpty()?"":" Solo en '"+sB.name()+"': "+oB)); return;
        }

        buildComparisonResult(sA, sB);
        logEvent(ActionType.COMPARISON_EXECUTED, Map.of("scenarioA",sA.name(),"scenarioB",sB.name(),"period",sA.periodMonths()));
    }

    /**
     * FIX #4: Comparación rediseñada — cabeceras, tarjetas por variable con barras dobles, KPIs y gráfica.
     */
    private void buildComparisonResult(ScenarioDTO sA, ScenarioDTO sB) {
        comparisonResultContainer.getChildren().clear();

        // Cabeceras
        HBox heads = new HBox(16); heads.setAlignment(Pos.CENTER);
        HBox.setHgrow(buildScenarioHeaderCard(sA,"A","cmp-header-a"), Priority.ALWAYS);
        HBox.setHgrow(buildScenarioHeaderCard(sB,"B","cmp-header-b"), Priority.ALWAYS);
        VBox hA = buildScenarioHeaderCard(sA,"A","cmp-header-a");
        VBox hB = buildScenarioHeaderCard(sB,"B","cmp-header-b");
        HBox.setHgrow(hA, Priority.ALWAYS); HBox.setHgrow(hB, Priority.ALWAYS);
        heads.getChildren().addAll(hA, lbl("⚖","cmp-vs-icon"), hB);
        comparisonResultContainer.getChildren().add(heads);

        // Sección de variables
        comparisonResultContainer.getChildren().add(lbl("Comparativa de variables","cmp-section-label"));

        Random rA = new Random(sA.id()*31L), rB = new Random(sB.id()*31L);
        for (Map.Entry<String, VariableValue> e : sA.variables().entrySet()) {
            String key = e.getKey();
            VariableValue vA = e.getValue(), vB = sB.variables().get(key);
            double simA = vA.currentValue() * (1 + rA.nextGaussian()*0.07 + 0.04);
            double simB = vB.currentValue() * (1 + rB.nextGaussian()*0.07 + 0.03);
            double diff = simA - simB;
            double diffPct = simB != 0 ? (diff / Math.abs(simB)) * 100 : 0;
            comparisonResultContainer.getChildren().add(buildVariableCompareCard(key,vA,vB,simA,simB,diff,diffPct));
        }

        // KPIs totales
        comparisonResultContainer.getChildren().add(buildTotalsKpiRow(sA, sB));

        // Gráfica
        comparisonResultContainer.getChildren().add(lbl("Tendencia comparativa por período","cmp-section-label"));
        comparisonResultContainer.getChildren().add(buildScenarioCompareChart(sA, sB));
    }

    private VBox buildScenarioHeaderCard(ScenarioDTO s, String letter, String styleClass) {
        VBox card = new VBox(6); card.getStyleClass().addAll("cmp-scenario-header", styleClass);
        HBox top = new HBox(10); top.setAlignment(Pos.CENTER_LEFT);
        top.getChildren().addAll(lbl(letter,"cmp-letter-badge",styleClass+"-letter"), lbl(s.name(),"cmp-header-name"));
        card.getChildren().addAll(top,
                lbl("📅 "+s.periodMonths()+" meses  ·  ⚙ "+s.modifiedVars()+" variables","cmp-header-meta"),
                lbl("Base: "+s.baseScenario()+"  ·  "+formatDateTime(s.executedAt()),"cmp-header-meta2"));
        return card;
    }

    private VBox buildVariableCompareCard(String key, VariableValue vA, VariableValue vB,
                                          double simA, double simB, double diff, double diffPct) {
        VBox card = new VBox(12); card.getStyleClass().add("cmp-var-card");
        HBox head = new HBox(8); head.setAlignment(Pos.CENTER_LEFT);
        head.getChildren().addAll(lbl(varIcon(key),"cmp-var-icon"), lbl(vA.name(),"cmp-var-name"));

        HBox body = new HBox(16); body.setAlignment(Pos.CENTER_LEFT);
        VBox colA = buildVarColumn("A",simA,vA.baseValue(),vA.unit(),"col-a");
        VBox colB = buildVarColumn("B",simB,vB.baseValue(),vB.unit(),"col-b");
        HBox.setHgrow(colA, Priority.ALWAYS); HBox.setHgrow(colB, Priority.ALWAYS);

        Separator vSep = new Separator(Orientation.VERTICAL);

        boolean ahead = diff > 0.5, behind = diff < -0.5;
        VBox diffBox = new VBox(4); diffBox.setAlignment(Pos.CENTER); diffBox.getStyleClass().add("cmp-diff-box");
        diffBox.getChildren().addAll(
                lbl(ahead?"▲":behind?"▼":"●","diff-arrow-icon", ahead?"diff-icon-positive":behind?"diff-icon-negative":"diff-icon-neutral"),
                lbl(String.format("%+.1f%%",diffPct),"diff-pct", ahead?"diff-positive":behind?"diff-negative":"diff-neutral"),
                lbl("A vs B","diff-label")
        );

        body.getChildren().addAll(colA, vSep, colB, diffBox);
        card.getChildren().addAll(head, body);
        return card;
    }

    private VBox buildVarColumn(String letter, double value, double base, String unit, String style) {
        VBox col = new VBox(6); col.getStyleClass().addAll("cmp-var-col", style);
        double pct = ((value-base)/base)*100;
        double progress = Math.min(Math.max((value-base*0.5)/base,0),1);
        boolean up = pct > 0.5, down = pct < -0.5;

        StackPane barBg = new StackPane(); barBg.getStyleClass().add("cmp-mini-bar-bg"); barBg.setPrefHeight(5);
        HBox fill = new HBox(); fill.getStyleClass().addAll("cmp-mini-bar-fill", up?"bar-positive":down?"bar-negative":"bar-neutral");
        fill.setPrefHeight(5);
        barBg.widthProperty().addListener((obs, oldW, w) -> fill.setPrefWidth(w.doubleValue()*Math.min(progress,1.0)));
        barBg.getChildren().add(fill); StackPane.setAlignment(fill, Pos.CENTER_LEFT);

        col.getChildren().addAll(
                lbl("Escenario "+letter,"cmp-col-letter",style+"-label"),
                lbl(fmt(value)+" "+unit,"cmp-col-value",up?"val-up":down?"val-down":"val-neu"),
                barBg,
                lbl("Base: "+fmt(base)+" "+unit,"cmp-col-base"),
                lbl(String.format("%+.1f%%",pct),"cmp-col-pct",up?"badge-positive":down?"badge-negative":"badge-neutral")
        );
        return col;
    }

    private HBox buildTotalsKpiRow(ScenarioDTO sA, ScenarioDTO sB) {
        HBox row = new HBox(16); row.setAlignment(Pos.CENTER); row.getStyleClass().add("cmp-totals-row");
        double tA = 184320 + new Random(sA.id()).nextInt(20000);
        double tB = 184320 + new Random(sB.id()).nextInt(20000);
        double diff = tA - tB;
        row.getChildren().addAll(
                buildKpiBlock("Ingreso " + firstWord(sA.name()), "$" + fmtMoney(tA), "neutral"),
                lbl("vs","cmp-vs-small"),
                buildKpiBlock("Ingreso " + firstWord(sB.name()), "$" + fmtMoney(tB), "neutral"),
                // FIX #1: formato correcto sin %+$
                buildKpiBlock("Diferencia neta", (diff >= 0 ? "+" : "-") + "$" + fmtMoney(Math.abs(diff)), diff >= 0 ? "positive" : "negative")
        );
        return row;
    }

    private VBox buildKpiBlock(String label, String value, String style) {
        VBox b = new VBox(4); b.getStyleClass().add("cmp-kpi-block"); b.setAlignment(Pos.CENTER);
        HBox.setHgrow(b, Priority.ALWAYS);
        b.getChildren().addAll(lbl(value,"cmp-kpi-value","result-kpi-"+style), lbl(label,"cmp-kpi-label"));
        return b;
    }

    private VBox buildScenarioCompareChart(ScenarioDTO sA, ScenarioDTO sB) {
        @SuppressWarnings("unchecked")
        LineChart<String,Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());
        chart.setAnimated(false); chart.setLegendVisible(true);
        chart.setPrefHeight(240); chart.getStyleClass().add("sim-line-chart");

        XYChart.Series<String,Number> sa = new XYChart.Series<>(); sa.setName(sA.name());
        XYChart.Series<String,Number> sb = new XYChart.Series<>(); sb.setName(sB.name());
        Random rA = new Random(sA.id()*17L), rB = new Random(sB.id()*17L);
        double va = 14000+sA.id()*200.0, vb = 14000+sB.id()*150.0;
        for (int i = 0; i < sA.periodMonths(); i++) {
            va += rA.nextGaussian()*500+200; vb += rB.nextGaussian()*400+350;
            sa.getData().add(new XYChart.Data<>(MONTHS[i%12], Math.max(va,1000)));
            sb.getData().add(new XYChart.Data<>(MONTHS[i%12], Math.max(vb,1000)));
        }
        chart.getData().addAll(sa, sb);

        VBox w = new VBox(8); w.getStyleClass().add("chart-card-sim"); w.getChildren().add(chart);
        return w;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ELIMINACIÓN
    // ═════════════════════════════════════════════════════════════════════════

    private void onDeleteScenario(ScenarioDTO s) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("Eliminar escenario"); c.setHeaderText("¿Eliminar '" + s.name() + "'?");
        c.setContentText("Esta acción no se puede deshacer.");
        ButtonType del = new ButtonType("Eliminar", ButtonBar.ButtonData.OK_DONE);
        ButtonType no  = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        c.getButtonTypes().setAll(del, no);
        c.showAndWait().ifPresent(r -> {
            if (r == del) {
                logEvent(ActionType.SCENARIO_DELETED, Map.of("scenarioId",s.id(),"scenarioName",s.name()));
                scenarios.remove(s);
                if (lastExecuted != null && lastExecuted.id() == s.id()) { lastExecuted = null; slideOut(resultsPanel, null); }
                renderScenarioGrid(); refreshComparisonPanel();
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private List<ScenarioDTO> getExecutedScenarios() {
        return scenarios.stream().filter(s -> "EXECUTED".equals(s.status())).collect(Collectors.toList());
    }

    private Label lbl(String text, String... styles) {
        Label l = new Label(text);
        for (String s : styles) Arrays.stream(s.split("\\s+")).filter(p->!p.isBlank()).forEach(l.getStyleClass()::add);
        return l;
    }

    private String fmt(double v) { return v == Math.floor(v) ? String.format("%.0f",v) : String.format("%.1f",v); }
    private String fmtMoney(double v) { return String.format("%,.0f", v); }
    private String firstWord(String s) { return s.split(" ")[0]; }
    private String formatDateTime(LocalDateTime dt) {
        return dt == null ? "—" : dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private ScenarioDTO buildScenarioFromForm() {
        String sel = cmbTrainedModel.getValue();
        Map<String, VariableValue> base = sel != null ? getVariablesForSelectedModel(sel) : getMockVariables();
        Map<String, VariableValue> vars = new LinkedHashMap<>();
        base.forEach((key,v) -> {
            double val = variableSliders.containsKey(key) ? variableSliders.get(key).getValue() : v.currentValue();
            vars.put(key, new VariableValue(v.name(),val,v.baseValue(),v.unit(),((val-v.baseValue())/v.baseValue())*100,v.min(),v.max()));
        });
        long id = currentScenario != null ? currentScenario.id() : System.currentTimeMillis();
        return new ScenarioDTO(id,txtScenarioName.getText().trim(),txtScenarioDesc.getText().trim(),
                cmbPeriod.getValue()!=null?cmbPeriod.getValue():3, cmbBaseScenario.getValue(),
                "CONFIGURED",variableSliders.size(),vars,85.0,LocalDateTime.now(),"Usuario",null,"PRIVATE",sel);
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    private String statusLabel(String s) {
        return switch (s) { case "DRAFT"->"Borrador"; case "CONFIGURED"->"Configurado"; case "EXECUTED"->"✓ Ejecutado"; default->s; };
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MOCK DATA
    // ═════════════════════════════════════════════════════════════════════════

    private List<TrainedModelInfo> getMockTrainedModels() {
        return List.of(
                new TrainedModelInfo("SARIMA — Ventas Generales",  "SARIMA",        "0.94","12/01/2026"),
                new TrainedModelInfo("Random Forest — Demanda",    "Random Forest", "0.91","08/01/2026"),
                new TrainedModelInfo("Regresión Lineal — Costos",  "Lineal",        "0.88","03/01/2026"),
                new TrainedModelInfo("ARIMA — Ingresos Mensuales", "ARIMA",         "0.87","28/12/2025")
        );
    }

    private List<ScenarioDTO> getMockScenarios() {
        return new ArrayList<>(List.of(
                new ScenarioDTO(1L,"Incremento de precios 10%","Simula aumento general de precios en 10%",
                        3,"Actual","EXECUTED",2,getMockVariables(),91.0,
                        LocalDateTime.now().minusDays(5),"Admin",LocalDateTime.now().minusDays(4),"SHARED","SARIMA — Ventas Generales"),
                new ScenarioDTO(2L,"Reducción de costos","Optimización de costos operativos -15%",
                        3,"Optimista","EXECUTED",3,getMockVariables(),78.0,
                        LocalDateTime.now().minusDays(10),"Admin",LocalDateTime.now().minusDays(2),"PRIVATE","Random Forest — Demanda"),
                new ScenarioDTO(3L,"Escenario pesimista","Caída de demanda con incremento de costos",
                        6,"Pesimista","CONFIGURED",4,getMockVariables(),0.0,
                        LocalDateTime.now().minusDays(1),"Usuario",null,"PRIVATE",null)
        ));
    }

    private Map<String, VariableValue> getMockVariables() {
        Map<String, VariableValue> m = new LinkedHashMap<>();
        m.put("precio",  new VariableValue("Precio unitario",    100, 100,"$",        0,  50,  150));
        m.put("volumen", new VariableValue("Volumen de ventas", 1000,1000,"unidades",  0, 500, 1500));
        m.put("costos",  new VariableValue("Costos variables",    60,  60,"$",         0,  30,   90));
        m.put("demanda", new VariableValue("Demanda proyectada", 100, 100,"%",          0,  50,  150));
        return m;
    }

    private Map<String, VariableValue> getVariablesForModel(String type) {
        return switch (type) {
            case "SARIMA","ARIMA" -> {
                Map<String,VariableValue> m = new LinkedHashMap<>();
                m.put("ventas",    new VariableValue("Ventas históricas",  12000,12000,"$",  0, 6000,18000));
                m.put("estacion",  new VariableValue("Factor estacional",    100,  100,"%",  0,   50,  150));
                m.put("tendencia", new VariableValue("Tendencia base",       100,  100,"%",  0,   50,  150));
                yield m;
            }
            case "Random Forest" -> {
                Map<String,VariableValue> m = new LinkedHashMap<>();
                m.put("precio",    new VariableValue("Precio",             100, 100,"$",  0,  50, 150));
                m.put("stock",     new VariableValue("Stock disponible",   500, 500,"u",  0, 250, 750));
                m.put("descuento", new VariableValue("Descuento aplicado",  10,  10,"%",  0,   5,  15));
                m.put("demanda",   new VariableValue("Demanda proyectada", 100, 100,"%",  0,  50, 150));
                yield m;
            }
            default -> getMockVariables();
        };
    }

    private Map<String, VariableValue> getVariablesForSelectedModel(String displayName) {
        return getMockTrainedModels().stream().filter(m -> m.displayName().equals(displayName))
                .findFirst().map(m -> getVariablesForModel(m.type())).orElse(getMockVariables());
    }

    private record TrainedModelInfo(String displayName, String type, String r2, String trainedDate) {}
}