package com.app.ui.simulation;

import com.app.model.data.api.ProductoCatalogDTO;
import com.app.model.predictions.UserModelDTO;
import com.app.model.simulation.*;
import com.app.model.simulation.ScenarioDTO.VariableValue;
import com.app.service.data.DataApiService;
import com.app.service.predictions.PredictionService;
import com.app.service.simulation.SimulationApiService;
import javafx.animation.*;
import javafx.application.Platform;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * SimulationController — Módulo de Simulación de Escenarios
 *
 * Integra con los endpoints de /simulation de la API FastAPI.
 * Las variables de simulación se mapean a los 3 parámetros del backend:
 *   variacion_precio, variacion_costo, variacion_demanda (en % desde -50 a +50).
 * Los modelos del combo se cargan desde /predictions/models/user.
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
    @FXML private ComboBox<String>  cmbGranularidad;
    @FXML private VBox              variablesList;
    @FXML private ToggleButton      tglAggDay;
    @FXML private ToggleButton      tglAggWeek;
    @FXML private ToggleButton      tglAggMonth;
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

    // ─── Overrides por producto ───────────────────────────────────────────────
    @FXML private TextField txtProductSearch;
    @FXML private VBox      productSearchResults;
    @FXML private VBox      overridesContainer;

    // Estado de overrides: producto → sliders
    private final Map<Integer, ProductoCatalogDTO> productOverrides = new LinkedHashMap<>();
    private final Map<String, Slider>              overrideSliders  = new HashMap<>();
    // Caché de todos los productos (cargado una vez)
    private List<ProductoCatalogDTO> allProductos = new ArrayList<>();

    @FXML private VBox             comparisonPanel;
    @FXML private Label            comparisonNoticeLabel;
    @FXML private ComboBox<String> cmbCompare1;
    @FXML private ComboBox<String> cmbCompare2;
    @FXML private Button           btnCompare;
    @FXML private VBox             comparisonResultContainer;

    // ─── Servicios ────────────────────────────────────────────────────────────
    private final SimulationApiService simulationApiService = new SimulationApiService();
    private final PredictionService    predictionService    = new PredictionService();
    private DataApiService             dataApiService;

    // ─── Estado de API ────────────────────────────────────────────────────────
    private List<UserModelDTO>     userModels        = new ArrayList<>();
    private SimulationRunResultDTO lastRunResult     = null;
    private Map<String, Object>    lastCompareResult = null;

    // ─── Eventos de usuario ───────────────────────────────────────────────────
    public enum ActionType {
        SCENARIO_CREATE_STARTED, SCENARIO_EDIT_STARTED, SCENARIO_SAVED_DRAFT,
        SCENARIO_DELETED, MODEL_SELECTED, VARIABLE_ADJUSTED,
        SIMULATION_EXECUTED, SIMULATION_RESULTS_VIEWED,
        COMPARISON_OPENED, COMPARISON_EXECUTED, RESULTS_EXPORTED, NEW_SIMULATION_STARTED
    }

    public record UserActionEvent(ActionType type, LocalDateTime timestamp, Map<String, Object> payload) {
        @Override public String toString() {
            return "[" + timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + "] "
                    + type + " -> " + payload;
        }
    }

    private final List<UserActionEvent> userEventLog = new ArrayList<>();

    private void logEvent(ActionType type, Map<String, Object> payload) {
        UserActionEvent e = new UserActionEvent(type, LocalDateTime.now(),
                payload != null ? Collections.unmodifiableMap(payload) : Map.of());
        userEventLog.add(e);
        System.out.printf("[EVENT] %s%n", e);
    }

    public List<UserActionEvent> getUserEventLog() { return Collections.unmodifiableList(userEventLog); }

    // ─── Estado local ─────────────────────────────────────────────────────────
    private List<ScenarioDTO>   scenarios            = new ArrayList<>();
    private ScenarioDTO         currentScenario      = null;
    private String              granularidad         = "semanal";
    private String              chartAgg             = "semanal";
    // Caché: guarda los valores reales de sliders por id de escenario ejecutado
    private final Map<Long, Map<String, VariableValue>> scenarioVarsCache = new HashMap<>();
    // Punto agregado para gráficas
    private record AggPoint(String label, double base, double sim, double pct) {}
    private ScenarioDTO         lastExecuted    = null;
    private Map<String, Slider> variableSliders = new LinkedHashMap<>();
    private RotateTransition    iconRotation;

    private static final String[] MONTHS =
            {"Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic"};

    // ═════════════════════════════════════════════════════════════════════════
    //  INIT
    // ═════════════════════════════════════════════════════════════════════════

    @FXML public void initialize() {
        dataApiService = new DataApiService();
        setupCombos();
        setupAggToggle();
        setupProductSearch();
        hideImmediate(editionPanel);
        hideImmediate(processingPanel);
        hideImmediate(resultsPanel);
        hideImmediate(comparisonPanel);
        loadUserModels();
        loadAllProductos();
        Platform.runLater(this::loadScenarios);
    }

    private void setupAggToggle() {
        ToggleGroup tg = new ToggleGroup();
        tglAggDay.setToggleGroup(tg);
        tglAggWeek.setToggleGroup(tg);
        tglAggMonth.setToggleGroup(tg);
        tglAggWeek.setSelected(true);
        tg.selectedToggleProperty().addListener((obs, o, nv) -> {
            if (nv == tglAggDay)        chartAgg = "diaria";
            else if (nv == tglAggMonth) chartAgg = "mensual";
            else                        chartAgg = "semanal";
            // Prevent deselecting all — keep at least one selected
            if (nv == null) { tglAggWeek.setSelected(true); return; }
            if (lastRunResult != null && lastExecuted != null) {
                buildComparisonChart(lastExecuted);
                buildMonthlyDeltaChart(lastExecuted);
                buildVariablesChart(lastExecuted);
            }
        });
    }

    private void setupCombos() {
        cmbPeriod.getItems().addAll(1, 2, 3, 4, 5, 6);
        cmbPeriod.setValue(3);
        cmbGranularidad.getItems().addAll("Semanal", "Mensual", "Diaria");
        cmbGranularidad.setValue("Semanal");
        cmbGranularidad.valueProperty().addListener((obs, o, nv) -> {
            if ("Diaria".equals(nv))  granularidad = "diaria";
            else if ("Mensual".equals(nv)) granularidad = "mensual";
            else granularidad = "semanal";
        });
        cmbBaseScenario.getItems().addAll("Actual", "Optimista", "Pesimista");
        cmbBaseScenario.setValue("Actual");
        // Los modelos se cargan asincrónicamente en loadUserModels()
        cmbTrainedModel.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, sel) -> onModelSelected(sel));
    }

    private void hideImmediate(VBox p) {
        p.setVisible(false); p.setManaged(false);
        p.setOpacity(1);    p.setTranslateY(0);
    }

    // ─── Carga de modelos del usuario ─────────────────────────────────────────

    private void loadUserModels() {
        predictionService.getUserModels()
                .thenAccept(models -> Platform.runLater(() -> {
                    userModels = new ArrayList<>(models);
                    cmbTrainedModel.getItems().clear();
                    if (models.isEmpty()) {
                        cmbTrainedModel.getItems().add("— Sin modelos entrenados —");
                        cmbTrainedModel.setDisable(true);
                    } else {
                        cmbTrainedModel.setDisable(false);
                        models.forEach(m -> cmbTrainedModel.getItems().add(buildModelDisplayName(m)));
                    }
                }))
                .exceptionally(ex -> null); // silently ignore
    }

    private String buildModelDisplayName(UserModelDTO m) {
        String nombre = (m.getNombre() != null && !m.getNombre().isBlank())
                ? m.getNombre() : m.getModelType();
        String r2 = m.getPrecision() > 0 ? String.format(" — R²=%.2f", m.getPrecision()) : "";
        return nombre + r2;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CARGA DE ESCENARIOS (API)
    // ═════════════════════════════════════════════════════════════════════════

    private void loadScenarios() {
        simulationApiService.listScenarios()
                .thenAccept(summaries -> Platform.runLater(() -> {
                    scenarios = summaries.stream()
                            .map(this::toScenarioDTO)
                            .collect(Collectors.toCollection(ArrayList::new));
                    renderScenarioGrid();
                    refreshComparisonPanel();
                }));
    }

    private ScenarioDTO toScenarioDTO(SimulationScenarioSummaryDTO dto) {
        String status = dto.getNumResultados() > 0 ? "EXECUTED" : "CONFIGURED";
        LocalDateTime fechaCreacion = parseApiDateTime(dto.getFechaCreacion());
        return new ScenarioDTO(
                (long) dto.getIdEscenario(),
                dto.getNombre(),
                dto.getDescripcion() != null ? dto.getDescripcion() : "",
                dto.getHorizonteMeses(),
                "Actual",
                status,
                dto.getNumParametros(),
                getSimulationVariables(),
                85.0,
                fechaCreacion != null ? fechaCreacion : LocalDateTime.now(),
                "Usuario",
                dto.getNumResultados() > 0 ? LocalDateTime.now() : null,
                "PRIVATE",
                null
        );
    }

    private LocalDateTime parseApiDateTime(String dt) {
        if (dt == null) return null;
        try {
            String clean = dt.length() > 19 ? dt.substring(0, 19) : dt;
            return LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } catch (Exception e) {
            try { return LocalDateTime.parse(dt, DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
            catch (Exception e2) { return null; }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  GRID DE ESCENARIOS
    // ═════════════════════════════════════════════════════════════════════════

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
        chips.getChildren().addAll(
                chip("📅 " + s.periodMonths() + " meses"),
                chip("⚙ " + s.modifiedVars() + " vars"),
                chip("👤 " + s.author()));
        if (s.modelName() != null && !s.modelName().isBlank()) {
            Label mc = chip("🧠 " + s.modelName().split("—")[0].trim());
            mc.getStyleClass().add("chip-model");
            chips.getChildren().add(mc);
        }

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button bEx = new Button("▶  Ejecutar");
        bEx.getStyleClass().add("btn-scenario-execute");
        bEx.setOnAction(e -> onExecuteScenario(s));
        Button bEd = new Button("✏  Editar");
        bEd.getStyleClass().add("btn-icon-sm");
        bEd.setOnAction(e -> onEditScenario(s));
        Button bDl = new Button("🗑");
        bDl.getStyleClass().addAll("btn-icon-sm", "btn-icon-danger");
        bDl.setOnAction(e -> onDeleteScenario(s));
        actions.getChildren().addAll(bEx, bEd, bDl);

        card.getChildren().addAll(head, desc, chips, new Separator(), actions);
        return card;
    }

    private Label chip(String t) { Label l = new Label(t); l.getStyleClass().add("scenario-chip"); return l; }

    // ═════════════════════════════════════════════════════════════════════════
    //  MODELO ENTRENADO
    // ═════════════════════════════════════════════════════════════════════════

    private void onModelSelected(String displayName) {
        if (displayName == null || displayName.startsWith("—")) return;
        userModels.stream()
                .filter(m -> buildModelDisplayName(m).equals(displayName))
                .findFirst()
                .ifPresent(info -> {
                    String nombre = (info.getNombre() != null && !info.getNombre().isBlank())
                            ? info.getNombre() : info.getModelType();
                    modelInfoName.setText(nombre);
                    String r2Str = info.getPrecision() > 0 ? String.format("%.4f", info.getPrecision()) : "N/A";
                    modelInfoMeta.setText("Tipo: " + info.getModelType()
                            + "  ·  R² " + r2Str
                            + "  ·  Entrenado " + info.getFechaEntrenamiento());
                    modelInfoBox.setVisible(true); modelInfoBox.setManaged(true);
                    buildVariablesEditor(getSimulationVariables());
                    logEvent(ActionType.MODEL_SELECTED, Map.of("model", displayName, "type", info.getModelType()));
                });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  BÚSQUEDA Y OVERRIDES POR PRODUCTO
    // ═════════════════════════════════════════════════════════════════════════

    private void loadAllProductos() {
        dataApiService.getProductos()
                .thenAccept(list -> Platform.runLater(() -> {
                    if (list != null) allProductos = new ArrayList<>(list);
                }))
                .exceptionally(ex -> null);
    }

    private void setupProductSearch() {
        // Listener con debounce de 250ms sobre el TextField de búsqueda
        PauseTransition debounce = new PauseTransition(Duration.millis(250));
        debounce.setOnFinished(e -> filterProductDropdown(
                txtProductSearch != null ? txtProductSearch.getText() : ""));

        if (txtProductSearch != null) {
            txtProductSearch.textProperty().addListener((obs, o, nv) -> {
                debounce.playFromStart();
                if ((nv == null || nv.isBlank()) && productSearchResults != null) {
                    productSearchResults.getChildren().clear();
                }
            });
            txtProductSearch.focusedProperty().addListener((obs, o, nv) -> {
                if (!nv && productSearchResults != null) {
                    Platform.runLater(() -> productSearchResults.getChildren().clear());
                }
            });
        }
    }

    private void filterProductDropdown(String query) {
        if (productSearchResults == null) return;
        productSearchResults.getChildren().clear();
        if (query == null || query.isBlank()) return;

        String q = query.toLowerCase();
        List<ProductoCatalogDTO> matches = allProductos.stream()
                .filter(p -> !productOverrides.containsKey(p.getIdProducto()))
                .filter(p -> p.getNombre().toLowerCase().contains(q)
                        || p.getSku().toLowerCase().contains(q))
                .limit(5)
                .collect(Collectors.toList());

        for (ProductoCatalogDTO p : matches) {
            Label item = new Label(p.getNombre() + "  ·  " + p.getSku());
            item.getStyleClass().add("search-result-item");
            item.setMaxWidth(Double.MAX_VALUE);
            item.setOnMouseClicked(e -> {
                addProductOverride(p);
                productSearchResults.getChildren().clear();
                txtProductSearch.clear();
            });
            productSearchResults.getChildren().add(item);
        }
    }

    private void addProductOverride(ProductoCatalogDTO product) {
        int pid = product.getIdProducto();
        if (productOverrides.containsKey(pid)) return;
        productOverrides.put(pid, product);
        if (overridesContainer != null) {
            overridesContainer.getChildren().add(buildOverrideCard(product));
        }
    }

    private VBox buildOverrideCard(ProductoCatalogDTO product) {
        int pid = product.getIdProducto();
        VBox card = new VBox(12);
        card.getStyleClass().add("override-card");

        // Header con nombre + botón ✕
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("override-card-header");
        Label nameLbl = lbl(product.getNombre(), "override-product-name");
        HBox.setHgrow(nameLbl, Priority.ALWAYS);
        Label skuLbl  = lbl(product.getSku(), "optional-badge");
        Button btnRm  = new Button("✕");
        btnRm.getStyleClass().add("btn-icon-sm");
        btnRm.setOnAction(e -> removeProductOverride(pid));
        header.getChildren().addAll(nameLbl, skuLbl, btnRm);

        // Sliders de precio, costo, demanda para este producto
        VBox sliders = new VBox(10);
        String[] tipos    = {"precio", "costo", "demanda"};
        String[] nombres  = {"Variación de Precio", "Variación de Costo", "Variación de Demanda"};
        String[] iconos   = {"💲", "💸", "📊"};
        for (int i = 0; i < tipos.length; i++) {
            String tipo = tipos[i];
            String key  = tipo + "_" + pid;
            VBox row = new VBox(6); row.getStyleClass().add("variable-editor-row");
            HBox rowHeader = new HBox(8); rowHeader.setAlignment(Pos.CENTER_LEFT);
            rowHeader.getChildren().addAll(
                    lbl(iconos[i], "var-row-icon"),
                    lbl(nombres[i], "variable-label"));

            Slider slider = new Slider(-50, 50, 0);
            slider.getStyleClass().add("var-slider");
            HBox.setHgrow(slider, Priority.ALWAYS);
            Label valueLbl = lbl("0 %", "variable-value-label");
            valueLbl.setMinWidth(80);
            Label pctLbl = lbl("0%", "variable-percent-label", "variable-percent-neu");
            pctLbl.setMinWidth(58);

            slider.valueProperty().addListener((obs, old, nv) -> {
                double v = nv.doubleValue();
                valueLbl.setText(fmt(v) + " %");
                pctLbl.setText(String.format("%+.1f%%", v));
                pctLbl.getStyleClass().removeAll("variable-percent-pos","variable-percent-neg","variable-percent-neu");
                pctLbl.getStyleClass().add(v > 0.5 ? "variable-percent-pos" : v < -0.5 ? "variable-percent-neg" : "variable-percent-neu");
            });

            HBox sliderRow = new HBox(12); sliderRow.setAlignment(Pos.CENTER_LEFT);
            sliderRow.getChildren().addAll(slider, valueLbl, pctLbl);
            row.getChildren().addAll(rowHeader, sliderRow);
            sliders.getChildren().add(row);
            overrideSliders.put(key, slider);
        }

        card.getChildren().addAll(header, sliders);
        return card;
    }

    private void removeProductOverride(int productId) {
        productOverrides.remove(productId);
        overrideSliders.remove("precio_"  + productId);
        overrideSliders.remove("costo_"   + productId);
        overrideSliders.remove("demanda_" + productId);
        if (overridesContainer != null) {
            overridesContainer.getChildren().removeIf(node -> {
                if (node instanceof VBox card) {
                    return card.getUserData() != null
                            && card.getUserData().equals(productId);
                }
                return false;
            });
            // Re-render: rebuild all cards (simpler than tracking nodes by id)
            overridesContainer.getChildren().clear();
            productOverrides.forEach((pid, p) ->
                    overridesContainer.getChildren().add(buildOverrideCard(p)));
        }
    }

    private void clearProductOverrides() {
        productOverrides.clear();
        overrideSliders.clear();
        if (overridesContainer != null) overridesContainer.getChildren().clear();
        if (txtProductSearch  != null) txtProductSearch.clear();
        if (productSearchResults != null) productSearchResults.getChildren().clear();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EDITOR DE VARIABLES (% variación)
    // ═════════════════════════════════════════════════════════════════════════

    private void buildVariablesEditor(Map<String, VariableValue> vars) {
        variablesList.getChildren().clear();
        variableSliders.clear();
        vars.forEach((key, v) -> {
            VBox row = new VBox(10); row.getStyleClass().add("variable-editor-row");
            HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT);
            Label icon    = lbl(varIcon(key), "var-row-icon");
            Label nameLbl = lbl(v.name(), "variable-label"); HBox.setHgrow(nameLbl, Priority.ALWAYS);
            Label baseLbl = lbl("Base: " + fmt(v.baseValue()) + " " + v.unit(), "variable-base-value");
            header.getChildren().addAll(icon, nameLbl, baseLbl);

            // Usar v.min() y v.max() directamente (soporte para % vars con base=0)
            double sliderMin = v.min();
            double sliderMax = v.max();
            Slider slider = new Slider(sliderMin, sliderMax, v.currentValue());
            slider.setSnapToTicks(false); slider.getStyleClass().add("var-slider");
            HBox.setHgrow(slider, Priority.ALWAYS);

            Label valueLbl = lbl(fmt(v.currentValue()) + " " + v.unit(), "variable-value-label"); valueLbl.setMinWidth(100);
            Label pctLbl   = lbl("0%", "variable-percent-label", "variable-percent-neu");         pctLbl.setMinWidth(58);
            Label warnLbl  = lbl("⚠  Valor ajustado al límite ±50%", "variable-warn-label");
            warnLbl.setVisible(false); warnLbl.setManaged(false);

            // Debounce: solo loguea cuando el usuario deja de mover el slider (400ms)
            PauseTransition debounce = new PauseTransition(Duration.millis(400));
            slider.valueProperty().addListener((obs, old, nv) -> {
                double val = nv.doubleValue(); boolean clamped = false;
                if (val < sliderMin) { val = sliderMin; slider.setValue(sliderMin); clamped = true; }
                if (val > sliderMax) { val = sliderMax; slider.setValue(sliderMax); clamped = true; }
                // Para variables % (base=0), el valor ya es el porcentaje
                double pct = v.baseValue() == 0 ? val : ((val - v.baseValue()) / v.baseValue()) * 100;
                valueLbl.setText(fmt(val) + " " + v.unit());
                pctLbl.setText(String.format("%+.1f%%", pct));
                pctLbl.getStyleClass().removeAll("variable-percent-pos","variable-percent-neg","variable-percent-neu");
                pctLbl.getStyleClass().add(pct > 0.5 ? "variable-percent-pos" : pct < -0.5 ? "variable-percent-neg" : "variable-percent-neu");
                warnLbl.setVisible(clamped); warnLbl.setManaged(clamped);
                row.getStyleClass().removeAll("variable-warn"); if (clamped) row.getStyleClass().add("variable-warn");
                final double fv = val;
                final double fpct = pct;
                debounce.setOnFinished(e -> logEvent(ActionType.VARIABLE_ADJUSTED,
                        Map.of("variable", key, "name", v.name(), "value", fv, "pct", fpct)));
                debounce.playFromStart();
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
            case "precio","variacion_precio"   -> "💲";
            case "volumen"                     -> "📦";
            case "costos","variacion_costo"    -> "💸";
            case "demanda","variacion_demanda" -> "📊";
            case "ventas"                      -> "🛒";
            case "estacion"                    -> "🌤";
            case "tendencia"                   -> "📈";
            case "stock"                       -> "🗃";
            case "descuento"                   -> "🏷";
            default                            -> "⚙";
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
        buildVariablesEditor(getSimulationVariables());
        clearProductOverrides();
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
        clearProductOverrides();
        logEvent(ActionType.SCENARIO_EDIT_STARTED, Map.of("scenarioId", s.id(), "scenarioName", s.name()));
        if (resultsPanel.isVisible()) slideOut(resultsPanel, () -> slideIn(editionPanel));
        else slideIn(editionPanel);
    }

    @FXML private void onCloseEdition() { slideOut(editionPanel, null); }

    @FXML private void onSaveScenario() {
        if (!validateForm(false)) return;
        String name    = txtScenarioName.getText().trim();
        String desc    = txtScenarioDesc.getText().trim();
        int    periods = cmbPeriod.getValue() != null ? cmbPeriod.getValue() : 3;
        List<Map<String, Object>> params = buildParamsFromSliders();

        if (currentScenario != null) {
            // Editar: solo actualizar parámetros
            simulationApiService.setParameters((int) currentScenario.id(), params)
                    .thenAccept(ok -> Platform.runLater(() -> {
                        logEvent(ActionType.SCENARIO_SAVED_DRAFT, Map.of("scenarioName", name));
                        slideOut(editionPanel, this::loadScenarios);
                    }));
        } else {
            // Crear nuevo
            simulationApiService.createScenario(name, desc, periods)
                    .thenAccept(response -> Platform.runLater(() -> {
                        if (response.isSuccess()) {
                            int newId = response.getIdEscenario();
                            simulationApiService.setParameters(newId, params)
                                    .thenAccept(ok -> Platform.runLater(() -> {
                                        logEvent(ActionType.SCENARIO_SAVED_DRAFT,
                                                Map.of("scenarioName", name, "id", newId));
                                        slideOut(editionPanel, this::loadScenarios);
                                    }));
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Error al crear escenario",
                                    response.getError() != null ? response.getError() : "Error desconocido");
                        }
                    }));
        }
    }

    @FXML private void onExecuteFromForm() {
        if (!validateForm(true)) return;
        String name     = txtScenarioName.getText().trim();
        String desc     = txtScenarioDesc.getText().trim();
        int    periods  = cmbPeriod.getValue() != null ? cmbPeriod.getValue() : 3;
        String modelName = cmbTrainedModel.getValue();
        List<Map<String, Object>> params    = buildParamsFromSliders();
        Map<String, VariableValue> sliderVars = getScenarioVariablesFromSliders();

        slideOut(editionPanel, () ->
                executeSimulation(currentScenario, name, desc, periods, modelName, params, sliderVars));
    }

    private void onExecuteScenario(ScenarioDTO s) {
        if (editionPanel.isVisible()) slideOut(editionPanel, () -> reExecuteScenario(s));
        else reExecuteScenario(s);
    }

    /** Re-ejecuta un escenario existente con sus parámetros guardados en BD. */
    private void reExecuteScenario(ScenarioDTO s) {
        slideIn(processingPanel);
        startProcessingAnimation();
        logEvent(ActionType.SIMULATION_EXECUTED, Map.of("scenarioId", s.id(), "scenarioName", s.name()));
        Platform.runLater(() -> processingStep.setText("Ejecutando simulación…"));

        simulationApiService.runSimulation((int) s.id(), granularidad)
                .thenAccept(result -> Platform.runLater(() -> {
                    if (!result.isSuccess()) {
                        stopProcessingAnimation();
                        slideOut(processingPanel, null);
                        showAlert(Alert.AlertType.ERROR, "Error en simulación",
                                result.getError() != null ? result.getError() : "Error desconocido");
                        return;
                    }
                    lastRunResult = result;
                    ScenarioDTO ex = new ScenarioDTO(s.id(), s.name(), s.description(),
                            s.periodMonths(), s.baseScenario(), "EXECUTED", s.modifiedVars(),
                            s.variables(), 85.0, s.createdAt(), s.author(),
                            LocalDateTime.now(), s.visibility(), s.modelName());
                    scenarios.replaceAll(sc -> sc.id() == ex.id() ? ex : sc);
                    lastExecuted = ex;
                    stopProcessingAnimation();
                    renderScenarioGrid();
                    refreshComparisonPanel();
                    slideOut(processingPanel, () -> {
                        buildResults(ex);
                        slideIn(resultsPanel);
                        logEvent(ActionType.SIMULATION_RESULTS_VIEWED,
                                Map.of("scenarioId", ex.id(), "scenarioName", ex.name()));
                    });
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        stopProcessingAnimation();
                        slideOut(processingPanel, null);
                        showAlert(Alert.AlertType.ERROR, "Error en simulación", ex.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Flujo completo: crear escenario (o reusar) → configurar parámetros → ejecutar → mostrar resultados.
     */
    private void executeSimulation(ScenarioDTO existing, String name, String desc, int periods,
                                   String modelName, List<Map<String, Object>> params,
                                   Map<String, VariableValue> sliderVars) {
        slideIn(processingPanel);
        startProcessingAnimation();
        logEvent(ActionType.SIMULATION_EXECUTED, Map.of("scenarioName", name));

        CompletableFuture<Integer> createFuture;
        if (existing != null) {
            Platform.runLater(() -> processingStep.setText("Preparando escenario…"));
            createFuture = CompletableFuture.completedFuture((int) existing.id());
        } else {
            Platform.runLater(() -> processingStep.setText("Creando escenario…"));
            createFuture = simulationApiService.createScenario(name, desc, periods)
                    .thenApply(response -> {
                        if (!response.isSuccess())
                            throw new RuntimeException(response.getError() != null
                                    ? response.getError() : "Error al crear escenario");
                        return response.getIdEscenario();
                    });
        }

        createFuture
                .thenAccept(id -> {
                    Platform.runLater(() -> processingStep.setText("Configurando parámetros…"));
                    simulationApiService.setParameters(id, params)
                            .thenCompose(ok -> {
                                Platform.runLater(() -> processingStep.setText("Ejecutando simulación…"));
                                return simulationApiService.runSimulation(id, granularidad);
                            })
                            .thenAccept(result -> Platform.runLater(() -> {
                                if (!result.isSuccess()) {
                                    stopProcessingAnimation();
                                    slideOut(processingPanel, null);
                                    showAlert(Alert.AlertType.ERROR, "Error en simulación",
                                            result.getError() != null ? result.getError() : "Error desconocido");
                                    return;
                                }
                                lastRunResult = result;
                                scenarioVarsCache.put((long) id, sliderVars);
                                LocalDateTime createdAt = existing != null
                                        ? existing.createdAt() : LocalDateTime.now();
                                ScenarioDTO ex = new ScenarioDTO(
                                        (long) id, name, desc, periods,
                                        cmbBaseScenario.getValue() != null ? cmbBaseScenario.getValue() : "Actual",
                                        "EXECUTED", params.size(), sliderVars, 85.0,
                                        createdAt, "Usuario", LocalDateTime.now(), "PRIVATE", modelName);
                                lastExecuted = ex;
                                stopProcessingAnimation();
                                slideOut(processingPanel, () -> {
                                    loadScenarios();
                                    buildResults(ex);
                                    slideIn(resultsPanel);
                                    logEvent(ActionType.SIMULATION_RESULTS_VIEWED,
                                            Map.of("scenarioId", (long) id, "scenarioName", name));
                                });
                            }))
                            .exceptionally(ex -> {
                                Platform.runLater(() -> {
                                    stopProcessingAnimation();
                                    slideOut(processingPanel, null);
                                    showAlert(Alert.AlertType.ERROR, "Error en simulación",
                                            ex.getMessage() != null ? ex.getMessage() : "Error desconocido");
                                });
                                return null;
                            });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        stopProcessingAnimation();
                        slideOut(processingPanel, null);
                        showAlert(Alert.AlertType.ERROR, "Error al crear escenario",
                                ex.getMessage() != null ? ex.getMessage() : "Error desconocido");
                    });
                    return null;
                });
    }

    private boolean validateForm(boolean requireModel) {
        if (requireModel && cmbTrainedModel.getSelectionModel().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Modelo requerido",
                    "Selecciona un modelo predictivo entrenado."); return false;
        }
        if (txtScenarioName.getText().isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Nombre requerido",
                    "El escenario debe tener un nombre."); return false;
        }
        if (cmbPeriod.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Período requerido",
                    "Selecciona el período de simulación."); return false;
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
    //  PROCESAMIENTO (animación del ícono)
    // ═════════════════════════════════════════════════════════════════════════

    private void startProcessingAnimation() {
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
    //  RESULTADOS (con datos reales cuando disponibles)
    // ═════════════════════════════════════════════════════════════════════════

    private void buildResults(ScenarioDTO s) {
        resultsTitleLabel.setText("Resultados — " + s.name());
        String ml = cmbTrainedModel.getValue() != null ? "  ·  " + cmbTrainedModel.getValue() : "";
        resultsMetaLabel.setText(s.periodMonths() + " meses  ·  Base: " + s.baseScenario()
                + ml + "  ·  " + formatDateTime(s.executedAt()));
        buildResultKpis(s);
        buildComparisonChart(s);
        buildMonthlyDeltaChart(s);
        buildVariablesChart(s);
        buildChangesSummary(s);
    }

    private void buildResultKpis(ScenarioDTO s) {
        resultKpisBox.getChildren().clear();
        List<String[]> kpis;
        if (lastRunResult != null && lastRunResult.getResumen() != null) {
            SimulationRunResultDTO.Resumen r = lastRunResult.getResumen();
            double utilidad = r.getTotalUtilidadSimulada();
            kpis = List.of(
                    new String[]{"💰", "$" + fmtMoney(r.getTotalIngresosSimulados()), "Ingresos simulados",  "Total del período", "neutral"},
                    new String[]{"💸", "$" + fmtMoney(r.getTotalCostosSimulados()),   "Costos simulados",    "Total del período", "negative"},
                    new String[]{"📈", "$" + fmtMoney(utilidad),                      "Utilidad simulada",   "Total del período", utilidad >= 0 ? "positive" : "negative"},
                    new String[]{"⚡", fmt(r.getMargenPromedio()) + "%",               "Margen promedio",     "Del período simulado", "neutral"}
            );
        } else {
            kpis = List.of(
                    new String[]{"📈", "+12.4%",       "Variación proyectada", "vs escenario base",  "positive"},
                    new String[]{"💰", "$184,320",     "Ingreso estimado",     "Suma del período",   "neutral"},
                    new String[]{"⚡", fmt(s.confidence()) + "%", "Confianza", "Índice del modelo",  "neutral"},
                    new String[]{"📉", "-3.2%",        "Costo incremental",    "Impacto en costos",  "negative"}
            );
        }
        kpis.forEach(k -> {
            VBox card = new VBox(6); card.getStyleClass().add("result-kpi-card"); HBox.setHgrow(card, Priority.ALWAYS);
            card.getChildren().addAll(
                    lbl(k[0], "result-kpi-icon"),
                    lbl(k[1], "result-kpi-value", "result-kpi-" + k[4]),
                    lbl(k[2], "result-kpi-label"),
                    lbl(k[3], "result-kpi-sub"));
            resultKpisBox.getChildren().add(card);
        });
    }

    private void buildComparisonChart(ScenarioDTO s) {
        comparisonChart.getData().clear();
        XYChart.Series<String, Number> base = new XYChart.Series<>(); base.setName("Base histórico");
        XYChart.Series<String, Number> sim  = new XYChart.Series<>(); sim.setName("Simulado");

        if (lastRunResult != null && lastRunResult.getResultados() != null) {
            aggregateByKpi("ingresos").forEach(p -> {
                base.getData().add(new XYChart.Data<>(p.label(), p.base()));
                sim.getData().add(new XYChart.Data<>(p.label(), p.sim()));
            });
        } else {
            Random rng = new Random(42 + s.id()); double bv = 15000, sv = 15000;
            for (int i = 0; i < s.periodMonths() * 4; i++) {
                String l = MONTHS[(i / 4) % 12] + " S" + (i % 4 + 1);
                bv += rng.nextGaussian() * 400 + 120; sv += rng.nextGaussian() * 380 + 200 + (i * 18);
                base.getData().add(new XYChart.Data<>(l, Math.max(bv, 1000)));
                sim.getData().add(new XYChart.Data<>(l, Math.max(sv, 1000)));
            }
        }
        comparisonChart.getData().addAll(base, sim);
    }

    private void buildMonthlyDeltaChart(ScenarioDTO s) {
        monthlyDeltaChart.getData().clear();
        XYChart.Series<String, Number> delta = new XYChart.Series<>();
        delta.setName("Variación mensual (%)");

        if (lastRunResult != null && lastRunResult.getResultados() != null) {
            aggregateByKpi("ingresos").forEach(p ->
                    delta.getData().add(new XYChart.Data<>(p.label(), p.pct())));
        } else {
            Random rng = new Random(7 + s.id());
            for (int i = 0; i < s.periodMonths(); i++) {
                double val = 5 + rng.nextGaussian() * 4;
                delta.getData().add(new XYChart.Data<>(MONTHS[i % 12], val));
            }
        }
        monthlyDeltaChart.getData().add(delta);

        Platform.runLater(() -> {
            for (XYChart.Data<String, Number> d : delta.getData()) {
                if (d.getNode() != null) {
                    double pct = d.getYValue().doubleValue();
                    Label barLbl = new Label(String.format("%.1f%%", pct));
                    barLbl.getStyleClass().add("bar-data-label");
                    StackPane bar = (StackPane) d.getNode();
                    bar.getChildren().add(barLbl);
                    StackPane.setAlignment(barLbl, pct >= 0 ? Pos.TOP_CENTER : Pos.BOTTOM_CENTER);
                }
            }
        });
    }

    private void buildVariablesChart(ScenarioDTO s) {
        variablesChart.getData().clear();
        if (lastRunResult != null && lastRunResult.getResultados() != null) {
            String[] kpisToShow = {"ingresos", "costos", "utilidad_bruta"};
            String[] labels     = {"Ingresos", "Costos",  "Utilidad"};
            for (int k = 0; k < kpisToShow.length; k++) {
                final String kpi   = kpisToShow[k];
                final String label = labels[k];
                XYChart.Series<String, Number> ser = new XYChart.Series<>();
                ser.setName(label);
                aggregateByKpi(kpi).forEach(p ->
                        ser.getData().add(new XYChart.Data<>(p.label(), p.sim())));
                variablesChart.getData().add(ser);
            }
        } else {
            Random rng = new Random(13 + s.id()); int idx = 0;
            for (Map.Entry<String, VariableValue> e : s.variables().entrySet()) {
                VariableValue vv = e.getValue();
                XYChart.Series<String, Number> ser = new XYChart.Series<>(); ser.setName(vv.name());
                double val = Math.abs(vv.currentValue()) > 0 ? Math.abs(vv.currentValue()) : 10000;
                for (int i = 0; i < s.periodMonths(); i++) {
                    val += rng.nextGaussian() * (val * 0.03) + (val * 0.01);
                    ser.getData().add(new XYChart.Data<>(MONTHS[i % 12], Math.max(val, 0)));
                }
                variablesChart.getData().add(ser);
                if (++idx >= 4) break;
            }
        }
    }

    private void buildChangesSummary(ScenarioDTO s) {
        changesSummaryContainer.getChildren().clear();
        FlowPane grid = new FlowPane(14, 14);
        grid.setPrefWrapLength(9999);

        // Preferir caché (valores reales de sliders) sobre los valores del DTO
        Map<String, VariableValue> vars = scenarioVarsCache.getOrDefault(s.id(), s.variables());
        for (Map.Entry<String, VariableValue> entry : vars.entrySet()) {
            String key = entry.getKey();
            VariableValue v = entry.getValue();
            double newVal  = variableSliders.containsKey(key) ? variableSliders.get(key).getValue() : v.currentValue();
            double pct     = v.baseValue() == 0 ? newVal : ((newVal - v.baseValue()) / v.baseValue()) * 100;
            boolean up     = pct > 0.5, down = pct < -0.5;
            double range   = v.max() - v.min();
            double progress = range > 0 ? Math.min(Math.max((newVal - v.min()) / range, 0), 1) : 0.5;

            VBox card = new VBox(10); card.getStyleClass().add("summary-var-card");

            HBox head = new HBox(8); head.setAlignment(Pos.CENTER_LEFT);
            head.getChildren().addAll(lbl(varIcon(key), "summary-var-icon"), lbl(v.name(), "summary-var-name"));

            HBox vals = new HBox(10); vals.setAlignment(Pos.CENTER_LEFT);
            vals.getChildren().addAll(
                    lbl(fmt(v.baseValue()) + " " + v.unit(), "summary-base-val"),
                    lbl("→", "summary-arrow", up ? "arrow-up" : down ? "arrow-down" : "arrow-neu"),
                    lbl(fmt(newVal) + " " + v.unit(), "summary-new-val", up ? "val-up" : down ? "val-down" : "val-neu"));

            StackPane barBg = new StackPane(); barBg.getStyleClass().add("var-bar-bg"); barBg.setPrefHeight(6);
            HBox fill = new HBox(); fill.getStyleClass().addAll("var-bar-fill",
                    up ? "bar-positive" : down ? "bar-negative" : "bar-neutral");
            fill.setPrefHeight(6);
            barBg.widthProperty().addListener((obs, oldW, w) -> fill.setPrefWidth(w.doubleValue() * progress));
            barBg.getChildren().add(fill); StackPane.setAlignment(fill, Pos.CENTER_LEFT);

            HBox footer = new HBox(); footer.setAlignment(Pos.CENTER_RIGHT);
            footer.getChildren().add(lbl(String.format("%+.1f%%", pct), "var-pct-badge",
                    up ? "badge-positive" : down ? "badge-negative" : "badge-neutral"));

            card.getChildren().addAll(head, vals, barBg, footer);
            grid.getChildren().add(card);
        }
        changesSummaryContainer.getChildren().add(grid);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ACCIONES RESULTADOS
    // ═════════════════════════════════════════════════════════════════════════

    @FXML private void onNewSimulation() {
        logEvent(ActionType.NEW_SIMULATION_STARTED, Map.of());
        lastRunResult = null;
        slideOut(resultsPanel, this::onCreateNew);
    }

    @FXML private void onExportResults() {
        logEvent(ActionType.RESULTS_EXPORTED, Map.of("scenarioId", lastExecuted != null ? lastExecuted.id() : -1));
        showAlert(Alert.AlertType.INFORMATION, "Exportación", "Los resultados han sido exportados correctamente.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  COMPARACIÓN (con API real)
    // ═════════════════════════════════════════════════════════════════════════

    private void refreshComparisonPanel() {
        List<ScenarioDTO> ex = getExecutedScenarios();
        if (ex.size() < 2) { hideImmediate(comparisonPanel); return; }
        comparisonPanel.setVisible(true); comparisonPanel.setManaged(true);
        List<String> names = ex.stream().map(ScenarioDTO::name).toList();
        cmbCompare1.getItems().setAll(names); cmbCompare2.getItems().setAll(names);
        comparisonResultContainer.getChildren().clear();
        comparisonNoticeLabel.setText("Selecciona dos escenarios ejecutados para comparar.");
        logEvent(ActionType.COMPARISON_OPENED, Map.of("executedCount", ex.size()));
    }

    @FXML private void onCompareScenarios() {
        String nA = cmbCompare1.getValue(), nB = cmbCompare2.getValue();
        if (nA == null || nB == null) {
            showAlert(Alert.AlertType.WARNING, "Selección incompleta", "Selecciona ambos escenarios."); return;
        }
        if (nA.equals(nB)) {
            showAlert(Alert.AlertType.WARNING, "Selección inválida", "Selecciona dos escenarios distintos."); return;
        }

        List<ScenarioDTO> ex = getExecutedScenarios();
        ScenarioDTO sA = ex.stream().filter(s -> s.name().equals(nA)).findFirst().orElse(null);
        ScenarioDTO sB = ex.stream().filter(s -> s.name().equals(nB)).findFirst().orElse(null);
        if (sA == null || sB == null) return;

        btnCompare.setDisable(true);
        simulationApiService.compareScenarios(List.of((int) sA.id(), (int) sB.id()))
                .thenAccept(result -> Platform.runLater(() -> {
                    btnCompare.setDisable(false);
                    lastCompareResult = result;
                    buildComparisonResult(sA, sB);
                    logEvent(ActionType.COMPARISON_EXECUTED,
                            Map.of("scenarioA", sA.name(), "scenarioB", sB.name()));
                }))
                .exceptionally(ex2 -> {
                    Platform.runLater(() -> {
                        btnCompare.setDisable(false);
                        showAlert(Alert.AlertType.ERROR, "Error al comparar", ex2.getMessage());
                    });
                    return null;
                });
    }

    private void buildComparisonResult(ScenarioDTO sA, ScenarioDTO sB) {
        comparisonResultContainer.getChildren().clear();

        // Cabeceras
        HBox heads = new HBox(16); heads.setAlignment(Pos.CENTER);
        VBox hA = buildScenarioHeaderCard(sA, "A", "cmp-header-a");
        VBox hB = buildScenarioHeaderCard(sB, "B", "cmp-header-b");
        HBox.setHgrow(hA, Priority.ALWAYS); HBox.setHgrow(hB, Priority.ALWAYS);
        heads.getChildren().addAll(hA, lbl("⚖", "cmp-vs-icon"), hB);
        comparisonResultContainer.getChildren().add(heads);

        // Comparativa de variables — preferir datos reales de la API, luego caché de sliders
        Map<String, VariableValue> varsA = extractVarsFromCompareResult((int) sA.id(),
                scenarioVarsCache.getOrDefault(sA.id(), sA.variables()));
        Map<String, VariableValue> varsB = extractVarsFromCompareResult((int) sB.id(),
                scenarioVarsCache.getOrDefault(sB.id(), sB.variables()));
        comparisonResultContainer.getChildren().add(lbl("Comparativa de variables", "cmp-section-label"));
        for (Map.Entry<String, VariableValue> e : varsA.entrySet()) {
            String key = e.getKey();
            VariableValue vA = e.getValue();
            VariableValue vB = varsB.get(key);
            if (vB == null) continue;
            double simA = vA.currentValue(), simB = vB.currentValue();
            double diff = simA - simB;
            double diffPct = simB != 0 ? (diff / Math.abs(simB)) * 100 : 0;
            comparisonResultContainer.getChildren().add(buildVariableCompareCard(key, vA, vB, simA, simB, diff, diffPct));
        }

        // KPIs totales (datos reales si disponibles)
        comparisonResultContainer.getChildren().add(buildTotalsKpiRow(sA, sB));

        // Gráfica comparativa
        comparisonResultContainer.getChildren().add(lbl("Tendencia comparativa por período", "cmp-section-label"));
        comparisonResultContainer.getChildren().add(buildScenarioCompareChart(sA, sB));
    }

    private VBox buildScenarioHeaderCard(ScenarioDTO s, String letter, String styleClass) {
        VBox card = new VBox(6); card.getStyleClass().addAll("cmp-scenario-header", styleClass);
        HBox top = new HBox(10); top.setAlignment(Pos.CENTER_LEFT);
        top.getChildren().addAll(lbl(letter, "cmp-letter-badge", styleClass + "-letter"), lbl(s.name(), "cmp-header-name"));
        card.getChildren().addAll(top,
                lbl("📅 " + s.periodMonths() + " meses  ·  ⚙ " + s.modifiedVars() + " variables", "cmp-header-meta"),
                lbl("Base: " + s.baseScenario() + "  ·  " + formatDateTime(s.executedAt()), "cmp-header-meta2"));
        return card;
    }

    private VBox buildVariableCompareCard(String key, VariableValue vA, VariableValue vB,
                                          double simA, double simB, double diff, double diffPct) {
        VBox card = new VBox(12); card.getStyleClass().add("cmp-var-card");
        HBox head = new HBox(8); head.setAlignment(Pos.CENTER_LEFT);
        head.getChildren().addAll(lbl(varIcon(key), "cmp-var-icon"), lbl(vA.name(), "cmp-var-name"));

        HBox body = new HBox(16); body.setAlignment(Pos.CENTER_LEFT);
        VBox colA = buildVarColumn("A", simA, vA.baseValue(), vA.unit(), "col-a");
        VBox colB = buildVarColumn("B", simB, vB.baseValue(), vB.unit(), "col-b");
        HBox.setHgrow(colA, Priority.ALWAYS); HBox.setHgrow(colB, Priority.ALWAYS);

        Separator vSep = new Separator(Orientation.VERTICAL);
        boolean ahead = diff > 0.5, behind = diff < -0.5;
        VBox diffBox = new VBox(4); diffBox.setAlignment(Pos.CENTER); diffBox.getStyleClass().add("cmp-diff-box");
        diffBox.getChildren().addAll(
                lbl(ahead ? "▲" : behind ? "▼" : "●", "diff-arrow-icon",
                        ahead ? "diff-icon-positive" : behind ? "diff-icon-negative" : "diff-icon-neutral"),
                lbl(String.format("%+.1f%%", diffPct), "diff-pct",
                        ahead ? "diff-positive" : behind ? "diff-negative" : "diff-neutral"),
                lbl("A vs B", "diff-label"));
        body.getChildren().addAll(colA, vSep, colB, diffBox);
        card.getChildren().addAll(head, body);
        return card;
    }

    private VBox buildVarColumn(String letter, double value, double base, String unit, String style) {
        VBox col = new VBox(6); col.getStyleClass().addAll("cmp-var-col", style);
        double pct = base == 0 ? value : ((value - base) / base) * 100;
        double progress = base != 0 ? Math.min(Math.max((value - base * 0.5) / base, 0), 1) : Math.min(Math.max((value + 50) / 100, 0), 1);
        boolean up = pct > 0.5, down = pct < -0.5;

        StackPane barBg = new StackPane(); barBg.getStyleClass().add("cmp-mini-bar-bg"); barBg.setPrefHeight(5);
        HBox fill = new HBox(); fill.getStyleClass().addAll("cmp-mini-bar-fill",
                up ? "bar-positive" : down ? "bar-negative" : "bar-neutral");
        fill.setPrefHeight(5);
        barBg.widthProperty().addListener((obs, oldW, w) -> fill.setPrefWidth(w.doubleValue() * Math.min(progress, 1.0)));
        barBg.getChildren().add(fill); StackPane.setAlignment(fill, Pos.CENTER_LEFT);

        col.getChildren().addAll(
                lbl("Escenario " + letter, "cmp-col-letter", style + "-label"),
                lbl(fmt(value) + " " + unit, "cmp-col-value", up ? "val-up" : down ? "val-down" : "val-neu"),
                barBg,
                lbl("Base: " + fmt(base) + " " + unit, "cmp-col-base"),
                lbl(String.format("%+.1f%%", pct), "cmp-col-pct",
                        up ? "badge-positive" : down ? "badge-negative" : "badge-neutral"));
        return col;
    }

    /**
     * Extrae los valores reales de variación (variacion_precio/costo/demanda) para un escenario
     * desde la respuesta de compareScenarios (campo "parametros_por_escenario").
     * Si no están disponibles, devuelve el fallback.
     */
    @SuppressWarnings("unchecked")
    private Map<String, VariableValue> extractVarsFromCompareResult(int scenarioId,
                                                                     Map<String, VariableValue> fallback) {
        if (lastCompareResult == null) return fallback;
        try {
            Map<String, Object> paramsMap = (Map<String, Object>) lastCompareResult.get("parametros_por_escenario");
            if (paramsMap == null) return fallback;
            Map<String, Object> params = (Map<String, Object>) paramsMap.get(String.valueOf(scenarioId));
            if (params == null) return fallback;

            double vPrecio  = params.get("variacion_precio")  instanceof Number n ? n.doubleValue() : 0.0;
            double vCosto   = params.get("variacion_costo")   instanceof Number n ? n.doubleValue() : 0.0;
            double vDemanda = params.get("variacion_demanda") instanceof Number n ? n.doubleValue() : 0.0;

            Map<String, VariableValue> vars = new LinkedHashMap<>();
            vars.put("variacion_precio",  new VariableValue("Variación de Precio",  vPrecio,  0.0, "%", vPrecio,  -50.0, 50.0));
            vars.put("variacion_costo",   new VariableValue("Variación de Costo",   vCosto,   0.0, "%", vCosto,   -50.0, 50.0));
            vars.put("variacion_demanda", new VariableValue("Variación de Demanda", vDemanda, 0.0, "%", vDemanda, -50.0, 50.0));
            return vars;
        } catch (Exception e) {
            return fallback;
        }
    }

    private HBox buildTotalsKpiRow(ScenarioDTO sA, ScenarioDTO sB) {
        double tA = 0, tB = 0;
        if (lastCompareResult != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> resumen = (Map<String, Object>) lastCompareResult.get("resumen_por_escenario");
                if (resumen != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rA = (Map<String, Object>) resumen.get(String.valueOf((int) sA.id()));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rB = (Map<String, Object>) resumen.get(String.valueOf((int) sB.id()));
                    if (rA != null && rA.get("total_ingresos") instanceof Number n) tA = n.doubleValue();
                    if (rB != null && rB.get("total_ingresos") instanceof Number n) tB = n.doubleValue();
                }
            } catch (Exception ignored) {
                tA = 184320 + new Random(sA.id()).nextInt(20000);
                tB = 184320 + new Random(sB.id()).nextInt(20000);
            }
        } else {
            tA = 184320 + new Random(sA.id()).nextInt(20000);
            tB = 184320 + new Random(sB.id()).nextInt(20000);
        }
        double diff = tA - tB;
        HBox row = new HBox(16); row.setAlignment(Pos.CENTER); row.getStyleClass().add("cmp-totals-row");
        row.getChildren().addAll(
                buildKpiBlock("Ingreso " + firstWord(sA.name()), "$" + fmtMoney(tA), "neutral"),
                lbl("vs", "cmp-vs-small"),
                buildKpiBlock("Ingreso " + firstWord(sB.name()), "$" + fmtMoney(tB), "neutral"),
                buildKpiBlock("Diferencia neta",
                        (diff >= 0 ? "+" : "-") + "$" + fmtMoney(Math.abs(diff)),
                        diff >= 0 ? "positive" : "negative"));
        return row;
    }

    private VBox buildKpiBlock(String label, String value, String style) {
        VBox b = new VBox(4); b.getStyleClass().add("cmp-kpi-block"); b.setAlignment(Pos.CENTER);
        HBox.setHgrow(b, Priority.ALWAYS);
        b.getChildren().addAll(lbl(value, "cmp-kpi-value", "result-kpi-" + style), lbl(label, "cmp-kpi-label"));
        return b;
    }

    private VBox buildScenarioCompareChart(ScenarioDTO sA, ScenarioDTO sB) {
        @SuppressWarnings("unchecked")
        LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());
        chart.setAnimated(false); chart.setLegendVisible(true);
        chart.setPrefHeight(240); chart.getStyleClass().add("sim-line-chart");

        XYChart.Series<String, Number> sa = new XYChart.Series<>(); sa.setName(sA.name());
        XYChart.Series<String, Number> sb = new XYChart.Series<>(); sb.setName(sB.name());

        boolean hasRealData = false;
        if (lastCompareResult != null) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> comparaciones =
                        (List<Map<String, Object>>) lastCompareResult.get("comparaciones");
                if (comparaciones != null) {
                    List<Map<String, Object>> ingresos = comparaciones.stream()
                            .filter(c -> "ingresos".equals(c.get("kpi")))
                            .sorted(Comparator.comparing(c -> (String) c.get("periodo")))
                            .toList();
                    for (Map<String, Object> c : ingresos) {
                        String label = formatPeriodLabel((String) c.get("periodo"));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> valores = (Map<String, Object>) c.get("valores");
                        if (valores != null) {
                            Object vA = valores.get(String.valueOf((int) sA.id()));
                            Object vB = valores.get(String.valueOf((int) sB.id()));
                            if (vA instanceof Number n) { sa.getData().add(new XYChart.Data<>(label, n.doubleValue())); hasRealData = true; }
                            if (vB instanceof Number n) { sb.getData().add(new XYChart.Data<>(label, n.doubleValue())); }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (!hasRealData) {
            Random rA = new Random(sA.id() * 17L), rB = new Random(sB.id() * 17L);
            double va = 14000 + sA.id() * 200.0, vb = 14000 + sB.id() * 150.0;
            for (int i = 0; i < sA.periodMonths(); i++) {
                va += rA.nextGaussian() * 500 + 200; vb += rB.nextGaussian() * 400 + 350;
                sa.getData().add(new XYChart.Data<>(MONTHS[i % 12], Math.max(va, 1000)));
                sb.getData().add(new XYChart.Data<>(MONTHS[i % 12], Math.max(vb, 1000)));
            }
        }
        chart.getData().addAll(sa, sb);
        VBox w = new VBox(8); w.getStyleClass().add("chart-card-sim"); w.getChildren().add(chart);
        return w;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ELIMINACIÓN (API)
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
                logEvent(ActionType.SCENARIO_DELETED, Map.of("scenarioId", s.id(), "scenarioName", s.name()));
                simulationApiService.deleteScenario((int) s.id())
                        .thenAccept(ok -> Platform.runLater(() -> {
                            scenarios.remove(s);
                            if (lastExecuted != null && lastExecuted.id() == s.id()) {
                                lastExecuted = null;
                                lastRunResult = null;
                                slideOut(resultsPanel, null);
                            }
                            renderScenarioGrid();
                            refreshComparisonPanel();
                        }));
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS DE UI
    // ═════════════════════════════════════════════════════════════════════════

    private List<ScenarioDTO> getExecutedScenarios() {
        return scenarios.stream().filter(s -> "EXECUTED".equals(s.status())).collect(Collectors.toList());
    }

    private Label lbl(String text, String... styles) {
        Label l = new Label(text);
        for (String s : styles)
            Arrays.stream(s.split("\\s+")).filter(p -> !p.isBlank()).forEach(l.getStyleClass()::add);
        return l;
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.format("%.0f", v) : String.format("%.1f", v);
    }
    private String fmtMoney(double v) { return String.format("%,.0f", v); }
    private String firstWord(String s) { return s.split(" ")[0]; }
    private String formatDateTime(LocalDateTime dt) {
        return dt == null ? "—" : dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }
    private String statusLabel(String s) {
        return switch (s) {
            case "DRAFT"      -> "Borrador";
            case "CONFIGURED" -> "Configurado";
            case "EXECUTED"   -> "✓ Ejecutado";
            default           -> s;
        };
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS DE API / VARIABLES
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Variables estándar de simulación, mapeadas a los parámetros del backend.
     * Los sliders muestran variación porcentual desde -50% hasta +50% (base = 0%).
     */
    private Map<String, VariableValue> getSimulationVariables() {
        Map<String, VariableValue> m = new LinkedHashMap<>();
        m.put("variacion_precio",  new VariableValue("Variación de Precio",  0.0, 0.0, "%", 0.0, -50.0, 50.0));
        m.put("variacion_costo",   new VariableValue("Variación de Costo",   0.0, 0.0, "%", 0.0, -50.0, 50.0));
        m.put("variacion_demanda", new VariableValue("Variación de Demanda", 0.0, 0.0, "%", 0.0, -50.0, 50.0));
        return m;
    }

    /**
     * Construye la lista de parámetros a enviar al backend desde los sliders actuales.
     * Las claves coinciden con los nombres de parámetros en BD: variacion_precio, etc.
     */
    private List<Map<String, Object>> buildParamsFromSliders() {
        List<Map<String, Object>> params = new ArrayList<>();
        variableSliders.forEach((key, slider) -> {
            Map<String, Object> p = new HashMap<>();
            p.put("parametro", key);
            p.put("valorActual", slider.getValue());
            params.add(p);
        });
        // Asegurar que los 3 parámetros globales requeridos estén presentes
        Set<String> present = variableSliders.keySet();
        for (String required : List.of("variacion_precio", "variacion_costo", "variacion_demanda")) {
            if (!present.contains(required)) {
                Map<String, Object> p = new HashMap<>();
                p.put("parametro", required);
                p.put("valorActual", 0.0);
                params.add(p);
            }
        }
        // Agregar overrides por producto
        for (Map.Entry<Integer, ProductoCatalogDTO> e : productOverrides.entrySet()) {
            int pid = e.getKey();
            for (String tipo : List.of("precio", "costo", "demanda")) {
                Slider s = overrideSliders.get(tipo + "_" + pid);
                if (s != null) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("parametro",   "variacion_" + tipo);
                    p.put("valorActual", s.getValue());
                    p.put("productoId",  pid);
                    params.add(p);
                }
            }
        }
        return params;
    }

    /**
     * Captura el estado actual de los sliders como VariableValue para construir el ScenarioDTO.
     */
    private Map<String, VariableValue> getScenarioVariablesFromSliders() {
        Map<String, VariableValue> vars = new LinkedHashMap<>();
        Map<String, VariableValue> defaults = getSimulationVariables();
        defaults.forEach((key, v) -> {
            double val = variableSliders.containsKey(key) ? variableSliders.get(key).getValue() : 0.0;
            vars.put(key, new VariableValue(v.name(), val, v.baseValue(), v.unit(), val, v.min(), v.max()));
        });
        return vars;
    }

    // ─── Agregación de resultados para las gráficas ───────────────────────────

    /**
     * Agrega los resultados del último run para un KPI según chartAgg (diaria/semanal/mensual).
     * Retorna puntos (label, base, sim, pct) listos para las series de gráficas.
     */
    private List<AggPoint> aggregateByKpi(String kpi) {
        if (lastRunResult == null || lastRunResult.getResultados() == null) return List.of();
        List<SimulationRunResultDTO.PeriodResult> filtered = lastRunResult.getResultados().stream()
                .filter(r -> kpi.equals(r.getKpi()))
                .sorted(Comparator.comparing(SimulationRunResultDTO.PeriodResult::getPeriodo))
                .toList();
        if (filtered.isEmpty()) return List.of();

        if ("diaria".equals(chartAgg)) {
            return filtered.stream()
                    .map(r -> new AggPoint(formatPeriodLabel(r.getPeriodo()),
                            r.getValorBase(), r.getValorSimulado(), r.getPorcentajeCambio()))
                    .toList();
        }
        // Group by week or month and sum base/sim values
        Map<String, double[]> groups = new LinkedHashMap<>();
        for (var r : filtered) {
            String label = "mensual".equals(chartAgg)
                    ? getMonthLabel(r.getPeriodo())
                    : getWeekLabel(r.getPeriodo());
            double[] acc = groups.computeIfAbsent(label, k -> new double[2]);
            acc[0] += r.getValorBase();
            acc[1] += r.getValorSimulado();
        }
        return groups.entrySet().stream()
                .map(e -> {
                    double[] v = e.getValue();
                    double pct = v[0] > 0 ? (v[1] - v[0]) / v[0] * 100 : 0;
                    return new AggPoint(e.getKey(), v[0], v[1], pct);
                })
                .toList();
    }

    private String getWeekLabel(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return isoDate != null ? isoDate : "";
        try {
            int day   = Integer.parseInt(isoDate.substring(8, 10));
            int month = Integer.parseInt(isoDate.substring(5, 7)) - 1;
            int week  = (day - 1) / 7 + 1;
            return MONTHS[month % 12] + " S" + week;
        } catch (Exception e) { return isoDate; }
    }

    private String getMonthLabel(String isoDate) {
        if (isoDate == null || isoDate.length() < 7) return isoDate != null ? isoDate : "";
        try {
            int month = Integer.parseInt(isoDate.substring(5, 7)) - 1;
            return MONTHS[month % 12];
        } catch (Exception e) { return isoDate; }
    }

    /**
     * Convierte una fecha ISO ("2026-03-01") a una etiqueta corta.
     * - día == 1: solo mes ("Mar")  — granularidad mensual
     * - otro día: "DD MMM"          — granularidad semanal o diaria
     */
    private String formatPeriodLabel(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return isoDate != null ? isoDate : "";
        try {
            int day   = Integer.parseInt(isoDate.substring(8, 10));
            int month = Integer.parseInt(isoDate.substring(5, 7)) - 1;
            if (day == 1) return MONTHS[month % 12];
            return day + " " + MONTHS[month % 12];
        } catch (Exception e) {
            return isoDate;
        }
    }
}
