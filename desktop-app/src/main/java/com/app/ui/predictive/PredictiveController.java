package com.app.ui.predictive;

import com.app.model.Phase2ConfigDTO;
import com.app.model.PredictiveModelDTO;
import com.app.model.ResultKpiDTO;
import com.app.model.TrainingResult;
import com.app.model.predictions.ForecastRequestDTO;
import com.app.model.predictions.ForecastResponseDTO;
import com.app.model.predictions.TrainModelRequestDTO;
import com.app.model.predictions.TrainModelResponseDTO;
import com.app.service.alerts.WarningService;
import com.app.service.predictions.PredictionService;
import com.app.ui.components.cards.ResultKpiCard;
import com.app.ui.components.charts.BarChartCardController;
import com.app.ui.components.charts.LineChartCardController;
import javafx.scene.chart.XYChart;
import javafx.animation.Animation;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.IOException;
import java.util.*;

public class PredictiveController {

    @FXML private FlowPane modelsContainer;
    @FXML private Label detailTitle;
    @FXML private Label detailDescription;
    @FXML private Label detailExtra;
    @FXML private Label trainingInfo;
    @FXML private Label lblResult;
    @FXML private Label lblMetric;
    @FXML private Label lblTrainingTime;

    @FXML private ImageView trainingIcon;

    @FXML private HBox trainingMetrics;

    @FXML private Button btnNext;
    @FXML private Button btnPrevious;
    @FXML private Button btnTrain;


    @FXML private VBox phase1Content;
    @FXML private VBox phase2Content;
    @FXML private VBox phase3Content;
    @FXML private VBox phase4Content;

    @FXML private FlowPane resultsKpiContainer;
    @FXML private VBox resultsChartsContainer;
    @FXML private VBox trainingSummaryContainer;
    @FXML private VBox modelInfoContainer;
    @FXML private VBox detailDescriptionContainer;

    @FXML private VBox parametersContainer;
    @FXML private VBox parametersSummary;

    private Phase2ConfigDTO phase2Config;
    private List<CheckBox> variableCheckboxes;
    private CheckBox selectAllCheckbox;
    private Slider horizonSlider;
    private Label horizonValueLabel;
    private TableView<Map<String, String>> previewTable;

    @FXML private HBox predictiveFooter;

    @FXML private ImageView timeIcon;
    @FXML private ImageView metricIcon;
    @FXML private ImageView resultIcon;
    @FXML private Label trainingTitleTxt;

    @FXML private StackPane phase1;
    @FXML private StackPane phase2;
    @FXML private StackPane phase3;
    @FXML private StackPane phase4;

    private List<StackPane> phases;

    private int currentPhaseIndex = 0;
    private PredictiveModelDTO selectedModel;
    private boolean validPhase2 = false;
    private boolean validPhase3 = false;
    private final List<VBox> modelCards = new ArrayList<>();

    private RotateTransition loadingRotation;

    private final PredictionService predictionService = new PredictionService();
    private TrainModelResponseDTO lastTrainResponse;
    private ForecastResponseDTO lastForecastResponse;

    @FXML
    private void initialize() {
        phases = List.of(phase1, phase2, phase3, phase4);

        currentPhaseIndex = 0; // Fase 1
        updatePhases();
        updatePhaseView();

        loadModels();
        clearDetailPanel();
        updateFooterState();
    }

    /* =========================
       MODELOS MOCK
       ========================= */
    private List<PredictiveModelDTO> getMockPredictiveModels() {
        return List.of(
                new PredictiveModelDTO(
                        "Regresión Lineal",
                        "Análisis de tendencia directa y relaciones proporcionales.",
                        List.of("Esencial", "Polinomial", "Ágil"),
                        "La opción más estable para proyecciones de crecimiento base.",
                        "📉", "85%", "< 1 min",
                        List.of(
                                new PredictiveModelDTO.ModelFeature("📐", "Complejidad", "Lineal / Polinomial", "#3498db"),
                                new PredictiveModelDTO.ModelFeature("🛡", "Regularización", "Lasso / Ridge / ElasticNet", "#2ecc71"),
                                new PredictiveModelDTO.ModelFeature("⚡", "Velocidad", "Entrenamiento instantáneo", "#f1c40f")
                        )
                ),
                new PredictiveModelDTO(
                        "Modelo ARIMA",
                        "Especializado en series de tiempo sin estacionalidad.",
                        List.of("Cronológico", "Autoregresivo", "Histórico"),
                        "Analiza el pasado inmediato para proyectar el futuro cercano.",
                        "📈", "92%", "1-2 min",
                        List.of(
                                new PredictiveModelDTO.ModelFeature("🔢", "Componentes", "AR (Lags) + I (Diff) + MA", "#3498db"),
                                new PredictiveModelDTO.ModelFeature("🤖", "Optimización", "Auto-order automático", "#9b59b6"),
                                new PredictiveModelDTO.ModelFeature("📍", "Estado", "Requiere estacionariedad", "#e74c3c")
                        )
                ),
                new PredictiveModelDTO(
                        "Modelo SARIMA",
                        "Potente para negocios con picos estacionales (ventas, feriados).",
                        List.of("Estacional", "Rítmico", "Recurrente"),
                        "Identifica ciclos anuales, mensuales o semanales con precisión.",
                        "🗓", "95%", "2-5 min",
                        List.of(
                                new PredictiveModelDTO.ModelFeature("🔄", "Ciclos", "Soporta periodos de 1-365 días", "#2ecc71"),
                                new PredictiveModelDTO.ModelFeature("📊", "Criterios", "Selección vía AIC / BIC", "#3498db"),
                                new PredictiveModelDTO.ModelFeature("⌛", "Carga", "Computacionalmente intensivo", "#e67e22")
                        )
                ),
                new PredictiveModelDTO(
                        "Random Forest",
                        "Sistema de múltiples árboles para decisiones multivariable.",
                        List.of("Jerárquico", "Robusto", "Multivariable"),
                        "Ideal cuando intervienen muchas variables (clima, precios, stock).",
                        "🌳", "98%", "3-8 min",
                        List.of(
                                new PredictiveModelDTO.ModelFeature("🌲", "Bosque", "Ensamble de 10-1000 árboles", "#27ae60"),
                                new PredictiveModelDTO.ModelFeature("🏗", "Muestreo", "Técnica Bootstrap (Bagging)", "#8e44ad"),
                                new PredictiveModelDTO.ModelFeature("🎯", "Precisión", "Mejor manejo de outliers", "#e74c3c")
                        )
                )
        );
    }

    private void loadModels() {
        for (PredictiveModelDTO model : getMockPredictiveModels()) {
            VBox card = createModelCard(model);
            modelCards.add(card);
            modelsContainer.getChildren().add(card);
        }
    }

    /* =========================
       CARD UI
       ========================= */
    private VBox createModelCard(PredictiveModelDTO model) {
        VBox card = new VBox(10);
        card.getStyleClass().add("model-card");

        Label icon = new Label(model.icon());
        icon.getStyleClass().add("model-icon");

        Label title = new Label(model.title());
        title.getStyleClass().add("model-title");

        Label description = new Label(model.description());
        description.setWrapText(true);

        FlowPane chips = new FlowPane(8, 8);
        for (String tag : model.tags()) {
            Label chip = new Label(tag);
            chip.getStyleClass().add("model-chip");
            chips.getChildren().add(chip);
        }

        HBox trainingTimeRow =
                createInfoRow("Tiempo de entrenamiento:", model.tiempoTrain());

        HBox precisionRow =
                createInfoRow("Precisión esperada:", model.presicion());

        Button selectButton = new Button("Seleccionar");
        selectButton.setMaxWidth(Double.MAX_VALUE);
        selectButton.getStyleClass().add("select-button");

        selectButton.setOnAction(e -> {
            selectModel(model, card, selectButton);
            e.consume(); // <--- IMPORTANTE: Evita que el clic se propague al card dos veces
        });

        card.setOnMouseClicked(e -> { // <--- CLIC EN EL CARD
            selectModel(model, card, selectButton);
        });

        card.getChildren().addAll(
                icon,
                title,
                description,
                new Separator(),
                chips,
                new Separator(),
                trainingTimeRow,
                precisionRow,
                selectButton
        );

        return card;
    }

    private HBox createInfoRow(String labelText, String valueText) {
        Label label = new Label(labelText);
        label.getStyleClass().add("info-label");

        Label value = new Label(valueText);
        value.getStyleClass().add("info-value");

        HBox row = new HBox(6, label, value);
        row.getStyleClass().add("info-row");

        return row;
    }


    /* =========================
       SELECCIÓN
       ========================= */
    private void selectModel(PredictiveModelDTO model, VBox selectedCard, Button button) {
        // Detectar si cambió el modelo
        boolean modelChanged = (selectedModel != null &&
                !selectedModel.title().equals(model.title()));

        selectedModel = model;

        modelCards.forEach(card -> card.getStyleClass().remove("selected"));
        modelCards.forEach(card ->
                ((Button) card.lookup(".select-button")).setText("Seleccionar")
        );

        selectedCard.getStyleClass().add("selected");
        button.setText("Seleccionado");

        updateDetailPane(model);
        updateFooterState();

        // Solo limpiar si el modelo cambió Y existe configuración previa
        if (modelChanged && phase2Config != null) {
            System.out.println("Modelo cambió, limpiando configuración de Fase 2");
            clearPhase2Config();
        }

    }

    private void updateDetailPane(PredictiveModelDTO model) {
        detailTitle.setText(model.title());
        detailExtra.setText(model.detailMessage());

        // Limpiamos el contenedor de la lista de características
        detailDescriptionContainer.getChildren().clear();
        detailDescriptionContainer.setSpacing(10);

        for (PredictiveModelDTO.ModelFeature feature : model.features()) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 5; -fx-background-color: #f8f9fa; -fx-background-radius: 5;");

            // Icono con color personalizado
            Label icon = new Label(feature.icon());
            icon.setStyle("-fx-font-size: 16px; -fx-text-fill: " + feature.color() + ";");
            icon.setMinWidth(25);

            // Textos (Título y Valor)
            VBox texts = new VBox(0);
            Label label = new Label(feature.label().toUpperCase());
            label.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #95a5a6;");

            Label value = new Label(feature.value());
            value.setStyle("-fx-font-size: 12px; -fx-text-fill: #34495e; -fx-font-weight: bold;");
            value.setWrapText(true);

            texts.getChildren().addAll(label, value);
            row.getChildren().addAll(icon, texts);

            detailDescriptionContainer.getChildren().add(row);
        }
    }

    private void clearDetailPanel() {
        // 1. Resetear los textos simples
        detailTitle.setText("Panel de Detalles");
        detailExtra.setText("Selecciona un modelo de la izquierda para comenzar la configuración.");

        // 2. Limpiar el contenedor de características (el VBox)
        detailDescriptionContainer.getChildren().clear();

        // 3. Opcional: Agregar un placeholder visual para que no se vea vacío
        Label placeholder = new Label("No hay un modelo seleccionado");
        placeholder.setStyle("-fx-text-fill: #bdc3c7; -fx-font-style: italic; -fx-padding: 20 0 0 0;");

        detailDescriptionContainer.getChildren().add(placeholder);
    }


    /**
     * Convierte el nombre del modelo en la UI al model_type que espera la API.
     */
    private String getApiModelType(String modelTitle) {
        return switch (modelTitle) {
            case "Regresión Lineal" -> "linear";
            case "Modelo ARIMA" -> "arima";
            case "Modelo SARIMA" -> "sarima";
            case "Random Forest" -> "random_forest";
            default -> "linear";
        };
    }

    /* =========================
       FOOTER / FASES
       ========================= */
    private void updatePhaseView() {
        phase1Content.setVisible(currentPhaseIndex == 0);
        phase1Content.setManaged(currentPhaseIndex == 0);

        phase2Content.setVisible(currentPhaseIndex == 1);
        phase2Content.setManaged(currentPhaseIndex == 1);

        phase3Content.setVisible(currentPhaseIndex == 2);
        phase3Content.setManaged(currentPhaseIndex == 2);

        phase4Content.setVisible(currentPhaseIndex == 3);
        phase4Content.setManaged(currentPhaseIndex == 3);
    }


    @FXML
    private void onNextPhase() {
        if (!canProceed()) {
            WarningService.show("Debes completar la información solicitada.");
            return;
        }

        goToNextPhase();
    }


    private void goToNextPhase() {
        if (currentPhaseIndex < phases.size() - 1) {
            currentPhaseIndex++;
            updatePhases();
            updatePhaseView();
            updateFooterState();

            if (currentPhaseIndex == 1) { // se soliicta la fase 2 y aún no es valida la información seleccionada
                if (phase2Config == null || !validPhase2) {
                    buildPhase2UI(); // Primera vez
                }
            }

            if(currentPhaseIndex == 2){
                showPhase3();
            }

            if(currentPhaseIndex == 3){
                showPhase4Results();
            }

        }
    }




    @FXML
    private void onPreviousPhase() {
        if (currentPhaseIndex > 0) {
            currentPhaseIndex--;
            updatePhases();
            updatePhaseView();
            updateFooterState();
        }
    }


    private boolean canProceed() {
        return switch (currentPhaseIndex) {
            case 0 -> selectedModel != null; // Fase 1
            case 1 -> validPhase2;                  // Fase 2 (ejemplo)
            case 2 -> validPhase3;                  // Fase 3
            case 3 -> true;                  // Fase 4
            default -> true;
        };
    }


    private void updateFooterState() {
        btnPrevious.setDisable(currentPhaseIndex == 0);
        btnNext.setDisable(!canProceed());
    }

    private void updatePhases() {
        for (int i = 0; i < phases.size(); i++) {
            StackPane phase = phases.get(i);
            Label label = (Label) phase.getChildren().getFirst();

            phase.getStyleClass().removeAll("current", "completed");

            if (i < currentPhaseIndex) {
                // FASE COMPLETADA
                phase.getStyleClass().add("completed");
                label.setText("✓");

            } else if (i == currentPhaseIndex) {
                // FASE ACTUAL
                phase.getStyleClass().add("current");
                label.setText(String.valueOf(i + 1));

            } else {
                // FASE FUTURA
                label.setText(String.valueOf(i + 1));
            }
        }
    }

    /**
     * Construye la UI completa de la Fase 2
     */
    private void buildPhase2UI() {
        // Si ya existe configuración, NO hacer nada
        if (phase2Config != null && !parametersContainer.getChildren().isEmpty()) {
            System.out.println("✓ Preservando datos existentes");
            return;
        }

        parametersContainer.getChildren().clear();
        parametersSummary.getChildren().clear();

        // Inicializar configuración
        phase2Config = new Phase2ConfigDTO();
        variableCheckboxes = new ArrayList<>();

        if (selectedModel == null) return;

        // 1. RANGO DE FECHAS
        VBox dateRangeCard = createDateRangeCard();
        parametersContainer.getChildren().add(dateRangeCard);

        // 2. VARIABLES A CONSIDERAR
        VBox variablesCard = createVariablesCard();
        parametersContainer.getChildren().add(variablesCard);

        // 3. HORIZONTE DE PREDICCIÓN
        VBox horizonCard = createHorizonCard();
        parametersContainer.getChildren().add(horizonCard);

        // 4. DIVISIÓN DE DATOS
        VBox splitCard = createDataSplitCard();
        parametersContainer.getChildren().add(splitCard);

        // 5. VISTA PREVIA DE DATOS
        VBox previewCard = createDataPreviewCard();
        parametersContainer.getChildren().add(previewCard);

        // 6. CARGAR SIDEBAR
        buildPhase2Sidebar();
    }

    /**
     * 1. RANGO DE FECHAS
     */
    private VBox createDateRangeCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("param-card");

        Label title = new Label("📅 Rango de Fechas");
        title.getStyleClass().add("param-card-title");

        HBox dateRow = new HBox(16);
        dateRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Fecha mínima
        VBox startDateBox = new VBox(6);
        HBox.setHgrow(startDateBox, Priority.ALWAYS);
        Label startLabel = new Label("Fecha Inicio");
        startLabel.getStyleClass().add("date-field-label");
        DatePicker startDatePicker = new DatePicker();
        startDatePicker.setMaxWidth(Double.MAX_VALUE);
        startDatePicker.getStyleClass().add("date-picker");
        startDatePicker.valueProperty().addListener((obs, old, newVal) -> {
            phase2Config.setStartDate(newVal);
            updatePhase2Summary();
            validatePhase2();
        });
        startDateBox.getChildren().addAll(startLabel, startDatePicker);

        // Fecha máxima
        VBox endDateBox = new VBox(6);
        HBox.setHgrow(endDateBox, Priority.ALWAYS);
        Label endLabel = new Label("Fecha Fin");
        endLabel.getStyleClass().add("date-field-label");
        DatePicker endDatePicker = new DatePicker();
        endDatePicker.setMaxWidth(Double.MAX_VALUE);
        endDatePicker.getStyleClass().add("date-picker");
        endDatePicker.valueProperty().addListener((obs, old, newVal) -> {
            phase2Config.setEndDate(newVal);
            updatePhase2Summary();
            validatePhase2();
        });
        endDateBox.getChildren().addAll(endLabel, endDatePicker);

        dateRow.getChildren().addAll(startDateBox, endDateBox);

        card.getChildren().addAll(title, dateRow);
        return card;
    }

    /**
     * 2. VARIABLES A CONSIDERAR
     */
    private VBox createVariablesCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("param-card");

        Label title = new Label("📊 Variables a Considerar");
        title.getStyleClass().add("param-card-title");

        // Checkbox "Seleccionar Todo"
        selectAllCheckbox = new CheckBox("Seleccionar Todo");
        selectAllCheckbox.getStyleClass().add("select-all-checkbox");
        selectAllCheckbox.setOnAction(e -> handleSelectAllVariables());

        Separator sep = new Separator();
        sep.getStyleClass().add("param-separator");

        // Grid de variables (depende del modelo)
        GridPane variablesGrid = new GridPane();
        variablesGrid.getStyleClass().add("variables-grid");
        variablesGrid.setHgap(16);
        variablesGrid.setVgap(10);

        List<String> variables = getVariablesForModel(selectedModel.title());

        int col = 0;
        int row = 0;
        for (String variable : variables) {
            CheckBox cb = new CheckBox(variable);
            cb.getStyleClass().add("variable-checkbox");
            cb.setOnAction(e -> handleVariableSelection());
            variableCheckboxes.add(cb);

            variablesGrid.add(cb, col, row);

            col++;
            if (col >= 2) { // 2 columnas
                col = 0;
                row++;
            }
        }

        card.getChildren().addAll(title, selectAllCheckbox, sep, variablesGrid);
        return card;
    }

    /**
     * Obtiene las variables según el modelo seleccionado
     */
    private List<String> getVariablesForModel(String modelName) {
        return switch (modelName) {
            case "Regresión Lineal" -> List.of(
                    "Ventas Históricas", "Tiempo (t)", "Tendencia Global"
            );
            case "Modelo ARIMA" -> List.of(
                    "Ventas (Lag-1)", "Ventas (Lag-2)", "Error Previo", // ARIMA usa sus propios lags y errores
                    "Diferencial (d)"
            );
            case "Modelo SARIMA" -> List.of(
                    "Ventas", "Mes del Año", "Día de la Semana", // Variables estacionales
                    "Festivos", "Temporada Alta"
            );
            case "Random Forest" -> List.of(
                    // El MD dice: "Múltiples features/variables", aquí metemos todo
                    "Ventas", "Precio", "Competencia", "Stock",
                    "Descuentos", "Clima", "Categoría", "Región"
            );
            default -> List.of("Variable Base", "Variable Tiempo");
        };
    }

    /**
     * Maneja selección de "Seleccionar Todo"
     */
    private void handleSelectAllVariables() {
        boolean selectAll = selectAllCheckbox.isSelected();
        variableCheckboxes.forEach(cb -> cb.setSelected(selectAll));
        handleVariableSelection();
    }

    /**
     * Maneja selección individual de variables
     */
    private void handleVariableSelection() {
        List<String> selected = variableCheckboxes.stream()
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .toList();

        phase2Config.setSelectedVariables(new ArrayList<>(selected));

        // Actualizar "Seleccionar Todo" si todas están seleccionadas
        boolean allSelected = variableCheckboxes.stream()
                .allMatch(CheckBox::isSelected);
        selectAllCheckbox.setSelected(allSelected);

        updatePhase2Summary();
        validatePhase2();
    }

    /**
     * 3. HORIZONTE DE PREDICCIÓN
     */
    private VBox createHorizonCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("param-card");
        card.setAlignment(javafx.geometry.Pos.CENTER);

        Label title = new Label("🔮 Horizonte de Predicción");
        title.getStyleClass().add("param-card-title");

        // Valor grande centrado
        HBox valueContainer = new HBox(6);
        valueContainer.setAlignment(javafx.geometry.Pos.CENTER);
        valueContainer.getStyleClass().add("horizon-value-container");

        horizonValueLabel = new Label("3");
        horizonValueLabel.getStyleClass().add("horizon-value");

        Label unitLabel = new Label("meses");
        unitLabel.getStyleClass().add("horizon-unit");

        valueContainer.getChildren().addAll(horizonValueLabel, unitLabel);

        // Slider
        horizonSlider = new Slider(1, 6, 3);
        horizonSlider.getStyleClass().add("horizon-slider");
        horizonSlider.setShowTickLabels(false);
        horizonSlider.setShowTickMarks(false);
        horizonSlider.setMajorTickUnit(1);
        horizonSlider.setMinorTickCount(0);
        horizonSlider.setSnapToTicks(true);

        horizonSlider.valueProperty().addListener((obs, old, newVal) -> {
            int months = newVal.intValue();
            horizonValueLabel.setText(String.valueOf(months));
            phase2Config.setPredictionHorizon(months);
            updatePhase2Summary();
            validatePhase2();
        });

        // Labels de extremos
        HBox labelsRow = new HBox();
        labelsRow.getStyleClass().add("horizon-labels");

        Label minLabel = new Label("1 mes");
        minLabel.getStyleClass().add("horizon-label-min");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Label maxLabel = new Label("6 meses");
        maxLabel.getStyleClass().add("horizon-label-max");

        labelsRow.getChildren().addAll(minLabel, spacer, maxLabel);

        // Recomendación
        HBox recommendation = new HBox(8);
        recommendation.setMaxWidth(Double.MAX_VALUE);
        recommendation.getStyleClass().add("horizon-recommendation");
        recommendation.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label recText = new Label("💡 Recomendación: 3-4 meses para predicciones más confiables");
        recText.setMaxWidth(Double.MAX_VALUE);
        recText.getStyleClass().add("horizon-recommendation-text");
        recText.setWrapText(true);

        recommendation.getChildren().add(recText);

        // Inicializar valor
        phase2Config.setPredictionHorizon(3);

        card.getChildren().addAll(title, valueContainer, horizonSlider, labelsRow, recommendation);
        return card;
    }

    /**
     * 4. DIVISIÓN DE DATOS
     */
    private VBox createDataSplitCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("param-card");

        Label title = new Label("📂 División de Datos");
        title.getStyleClass().add("param-card-title");

        // Campos bloqueados
        HBox fieldsRow = new HBox(24);
        fieldsRow.setAlignment(javafx.geometry.Pos.CENTER);
        fieldsRow.getStyleClass().add("split-fields-row");

        // Entrenamiento
        VBox trainBox = new VBox(6);
        HBox.setHgrow(trainBox, Priority.ALWAYS);
        trainBox.getStyleClass().add("split-field");
        Label trainLabel = new Label("🔒 Entrenamiento (%):");
        trainLabel.getStyleClass().add("split-field-label");
        TextField trainField = new TextField("70");
        trainField.setMaxWidth(Double.MAX_VALUE);
        trainField.setEditable(false);
        trainField.getStyleClass().add("split-field-input");
        trainBox.getChildren().addAll(trainLabel, trainField);

        // Validación
        VBox validBox = new VBox(6);
        HBox.setHgrow(validBox, Priority.ALWAYS);
        validBox.getStyleClass().add("split-field");
        Label validLabel = new Label("🔒 Validación (%):");
        validLabel.getStyleClass().add("split-field-label");
        TextField validField = new TextField("30");
        validField.setMaxWidth(Double.MAX_VALUE);
        validField.setEditable(false);
        validField.getStyleClass().add("split-field-input");
        validBox.getChildren().addAll(validLabel, validField);

        fieldsRow.getChildren().addAll(trainBox, validBox);

        // Barra de proporción
        HBox splitBar = new HBox(0);
        splitBar.setMaxWidth(Double.MAX_VALUE);
        splitBar.getStyleClass().add("split-bar");
        splitBar.setMaxWidth(400);

        Region trainPart = new Region();
        trainPart.getStyleClass().add("split-bar-train");
        trainPart.prefWidthProperty().bind(splitBar.widthProperty().multiply(0.7));

        Region validPart = new Region();
        validPart.getStyleClass().add("split-bar-validation");
        validPart.prefWidthProperty().bind(splitBar.widthProperty().multiply(0.3));

        splitBar.getChildren().addAll(trainPart, validPart);

        card.getChildren().addAll(title, fieldsRow, splitBar);
        return card;
    }

    /**
     * 5. VISTA PREVIA DE DATOS
     */
    private VBox createDataPreviewCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("preview-card");

        // Header con botón refresh
        HBox header = new HBox(12);
        header.getStyleClass().add("preview-header");
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label title = new Label("Vista Previa de Datos");
        title.getStyleClass().add("preview-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button refreshBtn = new Button("🔄");
        refreshBtn.getStyleClass().add("preview-refresh-btn");
        refreshBtn.setOnAction(e -> refreshPreviewTable());

        header.getChildren().addAll(title, spacer, refreshBtn);

        // Tabla
        previewTable = new TableView<>();
        previewTable.getStyleClass().add("preview-table");
        previewTable.setPrefHeight(300);

        // Columna de fecha (siempre visible)
        TableColumn<Map<String, String>, String> dateCol = new TableColumn<>("Fecha");
        dateCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().get("Fecha"))
        );
        dateCol.setPrefWidth(120);
        previewTable.getColumns().add(dateCol);

        // Las demás columnas se agregan dinámicamente

        card.getChildren().addAll(header, previewTable);
        return card;
    }

    /**
     * Refresca la tabla de vista previa
     */
    @FXML
    private void refreshPreviewTable() {
        if (phase2Config.getSelectedVariables() == null ||
                phase2Config.getSelectedVariables().isEmpty()) {
            return;
        }

        // Limpiar columnas excepto Fecha
        previewTable.getColumns().clear();

        // Re-agregar Fecha
        TableColumn<Map<String, String>, String> dateCol = new TableColumn<>("Fecha");
        dateCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().get("Fecha"))
        );
        dateCol.setPrefWidth(120);
        previewTable.getColumns().add(dateCol);

        // Agregar columnas de variables seleccionadas
        for (String variable : phase2Config.getSelectedVariables()) {
            TableColumn<Map<String, String>, String> col = new TableColumn<>(variable);
            col.setCellValueFactory(data ->
                    new javafx.beans.property.SimpleStringProperty(data.getValue().get(variable))
            );
            col.setPrefWidth(100);
            previewTable.getColumns().add(col);
        }

        // Cargar datos mock
        previewTable.getItems().setAll(getMockPreviewData());
    }

    /**
     * Datos mock para vista previa
     */
    private List<Map<String, String>> getMockPreviewData() {
        List<Map<String, String>> data = new ArrayList<>();

        for (int i = 1; i <= 100; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("Fecha", "2024-" + String.format("%02d", i) + "-01");

            for (String variable : phase2Config.getSelectedVariables()) {
                row.put(variable, "$" + (1000 + i * 100));
            }

            data.add(row);
        }

        return data;
    }

    /**
     * Construye el sidebar de resumen y validación
     */
    private void buildPhase2Sidebar() {
        parametersSummary.getChildren().clear();

        // Card 1: Resumen
        VBox summaryCard = createSummaryCard();
        parametersSummary.getChildren().add(summaryCard);

        // Card 2: Estado de Validación
        VBox validationCard = createValidationCard();
        parametersSummary.getChildren().add(validationCard);
    }

    /**
     * Card de Resumen
     */
    private VBox createSummaryCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("summary-card");

        Label title = new Label("Resumen");
        title.getStyleClass().add("summary-card-title");

        // Periodo
        HBox periodoRow = createSummaryItemRow(
                "📅",
                "Periodo",
                "No seleccionado"
        );
        periodoRow.setUserData("periodo"); // Para identificar después

        // Horizonte
        HBox horizonteRow = createSummaryItemRow(
                "🔮",
                "Horizonte",
                "3 meses"
        );
        horizonteRow.setUserData("horizonte");

        // Variables
        HBox variablesRow = createSummaryItemRow(
                "📊",
                "Variables",
                "0 seleccionadas"
        );
        variablesRow.setUserData("variables");

        // División
        HBox divisionRow = createSummaryItemRow(
                "📂",
                "División",
                "70% - 30%"
        );
        divisionRow.setUserData("division");

        card.getChildren().addAll(
                title,
                periodoRow,
                horizonteRow,
                variablesRow,
                divisionRow
        );

        return card;
    }

    /**
     * Crea una fila de resumen con emoji
     */
    private HBox createSummaryItemRow(String emoji, String label, String value) {
        HBox row = new HBox(8);
        row.getStyleClass().add("summary-item-row");
        row.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        Label emojiLabel = new Label(emoji);
        emojiLabel.getStyleClass().add("summary-item-emoji");

        VBox content = new VBox(2);
        content.getStyleClass().add("summary-item-content");

        Label labelText = new Label(label);
        labelText.getStyleClass().add("summary-item-label");

        Label valueText = new Label(value);
        valueText.getStyleClass().add("summary-item-value");
        valueText.setWrapText(true);
        valueText.setMaxWidth(200);

        content.getChildren().addAll(labelText, valueText);
        row.getChildren().addAll(emojiLabel, content);

        return row;
    }

    /**
     * Card de Estado de Validación
     */
    private VBox createValidationCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("validation-card");

        Label title = new Label("Estado de la Validación");
        title.getStyleClass().add("validation-card-title");

        // Item 1: Datos suficientes
        // Labels de validación
        Label validationDataLabel = new Label("Datos suficientes");
        HBox dataItem = createValidationItem("❌", validationDataLabel);
        dataItem.setUserData("data");

        // Item 2: Variables válidas
        Label validationVariablesLabel = new Label("Variables válidas");
        HBox variablesItem = createValidationItem("❌", validationVariablesLabel);
        variablesItem.setUserData("variables");

        // Item 3: Sin errores
        Label validationErrorsLabel = new Label("Sin errores detectados");
        HBox errorsItem = createValidationItem("✅", validationErrorsLabel);
        errorsItem.setUserData("errors");

        // Contenedor del mensaje de error
        VBox errorMessageContainer = new VBox(4);
        errorMessageContainer.setVisible(false);
        errorMessageContainer.setManaged(false);
        errorMessageContainer.getStyleClass().add("error-message-container");
        errorMessageContainer.setUserData("errorContainer"); // Para identificar después

        Label errorMessageLabel = new Label();
        errorMessageLabel.getStyleClass().add("error-message-text");
        errorMessageLabel.setWrapText(true);
        errorMessageLabel.setMaxWidth(Double.MAX_VALUE);

        errorMessageContainer.getChildren().add(errorMessageLabel);

        card.getChildren().addAll(
                title,
                dataItem,
                variablesItem,
                errorsItem,
                errorMessageContainer
        );

        return card;
    }

    /**
     * Crea un item de validación
     */
    private HBox createValidationItem(String emoji, Label textLabel) {
        HBox item = new HBox(8);
        item.getStyleClass().add("validation-item");
        item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label emojiLabel = new Label(emoji);
        emojiLabel.getStyleClass().add("validation-emoji");

        textLabel.getStyleClass().add("validation-text");

        item.getChildren().addAll(emojiLabel, textLabel);

        return item;
    }

    /**
     * Actualiza el resumen en el sidebar
     */
    private void updatePhase2Summary() {
        // Buscar y actualizar cada fila
        for (javafx.scene.Node node : parametersSummary.getChildren()) {
            if (node instanceof VBox summaryCard) {
                for (javafx.scene.Node item : summaryCard.getChildren()) {
                    if (item instanceof HBox row && row.getUserData() != null) {
                        String type = (String) row.getUserData();
                        updateSummaryRow(row, type);
                    }
                }
            }
        }
    }

    /**
     * Actualiza una fila específica del resumen
     */
    private void updateSummaryRow(HBox row, String type) {
        // Verificar que el row tenga al menos 2 hijos
        if (row.getChildren().size() < 2) {
            return;
        }

        // El segundo hijo debe ser un VBox (content)
        javafx.scene.Node secondChild = row.getChildren().get(1);

        if (!(secondChild instanceof VBox content)) {
            return;
        }

        Label valueLabel = (Label) content.getChildren().get(1);

        switch (type) {
            case "periodo":
                if (phase2Config.getStartDate() != null && phase2Config.getEndDate() != null) {
                    String periodo = phase2Config.getStartDate().toString() +
                            " → " +
                            phase2Config.getEndDate().toString();
                    valueLabel.setText(periodo);
                } else {
                    valueLabel.setText("No seleccionado");
                }
                break;

            case "horizonte":
                valueLabel.setText(phase2Config.getPredictionHorizon() + " meses");
                break;

            case "variables":
                int count = phase2Config.getSelectedVariables() != null ?
                        phase2Config.getSelectedVariables().size() : 0;
                valueLabel.setText(count + " seleccionada" + (count != 1 ? "s" : ""));
                break;

            case "division":
                valueLabel.setText(phase2Config.getTrainPercentage() + "% - " +
                        phase2Config.getValidationPercentage() + "%");
                break;
        }
    }

    /**
     * Valida la configuración de Fase 2 y actualiza UI
     */
    private void validatePhase2() {
        // Actualizar validaciones en el config
        // Ya se hace automáticamente en los setters

        // Actualizar emojis de validación
        updateValidationEmojis();

        // Actualizar estado de botón siguiente
        validPhase2 = phase2Config.isValid();
        updateFooterState();
    }

    /**
     * Actualiza los emojis de validación según el estado
     * CORREGIDO: La búsqueda del errorContainer estaba anidada incorrectamente
     */
    private void updateValidationEmojis() {
        // Buscar el validation card
        for (javafx.scene.Node node : parametersSummary.getChildren()) {
            if (!(node instanceof VBox card) ||
                    !card.getStyleClass().contains("validation-card")) {
                continue;
            }

            VBox errorContainer = null;
            Label errorMessageLabel = null;

            // ✅ CORRECCIÓN: Recorrer TODOS los hijos para encontrar tanto
            // los items de validación COMO el errorContainer
            for (javafx.scene.Node item : card.getChildren()) {

                // Actualizar items de validación (HBox con userData string)
                if (item instanceof HBox validItem &&
                        validItem.getUserData() instanceof String type) {

                    Label emojiLabel = (Label) validItem.getChildren().get(0);
                    Label textLabel = (Label) validItem.getChildren().get(1);

                    switch (type) {
                        case "data":
                            if (phase2Config.isHasEnoughData()) {
                                emojiLabel.setText("✅");
                                textLabel.getStyleClass().remove("invalid");
                                if (!textLabel.getStyleClass().contains("valid")) {
                                    textLabel.getStyleClass().add("valid");
                                }
                            } else {
                                emojiLabel.setText("❌");
                                textLabel.getStyleClass().remove("valid");
                                if (!textLabel.getStyleClass().contains("invalid")) {
                                    textLabel.getStyleClass().add("invalid");
                                }
                            }
                            break;

                        case "variables":
                            if (phase2Config.isHasValidVariables()) {
                                emojiLabel.setText("✅");
                                textLabel.getStyleClass().remove("invalid");
                                if (!textLabel.getStyleClass().contains("valid")) {
                                    textLabel.getStyleClass().add("valid");
                                }
                            } else {
                                emojiLabel.setText("❌");
                                textLabel.getStyleClass().remove("valid");
                                if (!textLabel.getStyleClass().contains("invalid")) {
                                    textLabel.getStyleClass().add("invalid");
                                }
                            }
                            break;

                        case "errors":
                            if (phase2Config.isHasNoErrors()) {
                                emojiLabel.setText("✅");
                                textLabel.getStyleClass().remove("invalid");
                                if (!textLabel.getStyleClass().contains("valid")) {
                                    textLabel.getStyleClass().add("valid");
                                }
                            } else {
                                emojiLabel.setText("⚠️");
                                textLabel.getStyleClass().remove("valid");
                                if (!textLabel.getStyleClass().contains("invalid")) {
                                    textLabel.getStyleClass().add("invalid");
                                }
                            }
                            break;
                    }
                }

                // Buscar errorContainer EN EL MISMO NIVEL
                else if (item instanceof VBox &&
                        "errorContainer".equals(item.getUserData())) {
                    errorContainer = (VBox) item;
                    if (!errorContainer.getChildren().isEmpty() &&
                            errorContainer.getChildren().getFirst() instanceof Label) {
                        errorMessageLabel = (Label) errorContainer.getChildren().getFirst();
                    }
                }
            }

            //Actualizar mensaje de error FUERA del loop
            if (errorContainer != null && errorMessageLabel != null) {
                String errorMsg = phase2Config.getErrorMessage();

                if (errorMsg != null && !errorMsg.isEmpty()) {
                    errorMessageLabel.setText(errorMsg);
                    errorContainer.setVisible(true);
                    errorContainer.setManaged(true);
                } else {
                    errorContainer.setVisible(false);
                    errorContainer.setManaged(false);
                }
            }
        }
    }

    /**
     * Limpia la configuración de Fase 2 al regresar o reentrenar
     */
    private void clearPhase2Config() {
        phase2Config = null;
        if(variableCheckboxes != null){
            variableCheckboxes.clear();
        }
        selectAllCheckbox = null;
        horizonSlider = null;
        horizonValueLabel = null;
        previewTable = null;
        validPhase2 = false;

        System.out.println("✓ Configuración de Fase 2 limpiada");
    }


    private void startLoadingAnimation() {
        loadingRotation = new RotateTransition(Duration.seconds(1.2), trainingIcon);
        loadingRotation.setFromAngle(0);
        loadingRotation.setToAngle(360);
        loadingRotation.setCycleCount(Animation.INDEFINITE);
        loadingRotation.play();
    }

    private void stopLoadingAnimation() {
        if (loadingRotation != null) {
            loadingRotation.stop();
        }
    }

    @FXML
    private void onTrainModel() {
        btnPrevious.setDisable(true);
        btnNext.setDisable(true);
        btnTrain.setDisable(true);
        trainingIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/loading.png"))
        ));
        trainingTitleTxt.setText("Validando datos antes de entrenar...");
        startLoadingAnimation();

        String modelType = getApiModelType(selectedModel.title());
        String fechaInicio = phase2Config.getStartDate() != null
                ? phase2Config.getStartDate().toString() : null;
        String fechaFin = phase2Config.getEndDate() != null
                ? phase2Config.getEndDate().toString() : null;

        // Paso 1: Validar datos con el backend antes de entrenar
        predictionService.validateData(fechaInicio, fechaFin, "D")
                .thenAccept(validationResult -> Platform.runLater(() -> {
                    boolean isValid = validationResult != null
                            && Boolean.TRUE.equals(validationResult.get("valid"));

                    if (!isValid) {
                        // Mostrar errores de validación
                        stopLoadingAnimation();
                        @SuppressWarnings("unchecked")
                        java.util.List<String> issues = validationResult != null
                                ? (java.util.List<String>) validationResult.get("issues")
                                : java.util.List.of("Error desconocido de validación");
                        String issueMsg = issues != null && !issues.isEmpty()
                                ? String.join("; ", issues)
                                : "Los datos no cumplen los requisitos mínimos";
                        showTrainingErrorWithMessage("Validación fallida: " + issueMsg);
                        return;
                    }

                    // Paso 2: Datos válidos, proceder al entrenamiento
                    trainingTitleTxt.setText("Entrenando el modelo, este proceso puede tardar unos minutos...");

                    TrainModelRequestDTO request = new TrainModelRequestDTO(modelType, fechaInicio, fechaFin);

                    predictionService.trainModel(request)
                            .thenAccept(response -> Platform.runLater(() -> {
                                stopLoadingAnimation();
                                if (response != null && response.isSuccess()) {
                                    lastTrainResponse = response;
                                    showTrainingResultFromApi(response);
                                } else {
                                    String errorMsg = response != null && response.getError() != null
                                            ? response.getError()
                                            : "Error desconocido al entrenar el modelo";
                                    showTrainingErrorWithMessage(errorMsg);
                                }
                            }))
                            .exceptionally(ex -> {
                                Platform.runLater(() -> {
                                    stopLoadingAnimation();
                                    showTrainingErrorWithMessage("Error de conexión: " + ex.getMessage());
                                });
                                return null;
                            });
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        stopLoadingAnimation();
                        showTrainingErrorWithMessage("Error al validar datos: " + ex.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Muestra resultado exitoso de entrenamiento con datos reales de la API.
     */
    private void showTrainingResultFromApi(TrainModelResponseDTO response) {
        trainingIcon.setRotate(0);
        trainingIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/check.png"))
        ));

        // Extraer métricas reales
        Map<String, Object> metrics = response.getMetrics();
        String r2 = metrics != null && metrics.containsKey("r2_score")
                ? String.format("%.4f", ((Number) metrics.get("r2_score")).doubleValue()) : "N/A";
        String mae = metrics != null && metrics.containsKey("mae")
                ? String.format("%.2f", ((Number) metrics.get("mae")).doubleValue()) : "N/A";

        lblTrainingTime.setText(response.getTrainingSamples() + " muestras");
        lblMetric.setText("R² = " + r2);
        lblResult.setText(Boolean.TRUE.equals(response.getMeetsR2Threshold()) ? "Aprobado" : "Bajo umbral");

        loadMetricIcons();

        trainingMetrics.setVisible(true);
        trainingMetrics.setManaged(true);

        String statusText = Boolean.TRUE.equals(response.getMeetsR2Threshold())
                ? "Modelo entrenado exitosamente. Clave: " + response.getModelKey()
                : "Modelo entrenado pero R² < 0.7. " +
                  (response.getRecommendation() != null ? response.getRecommendation() : "");
        trainingTitleTxt.setText(statusText);

        validPhase3 = true;
        updateFooterState();
    }

    /**
     * Muestra error de entrenamiento con mensaje específico.
     */
    private void showTrainingErrorWithMessage(String message) {
        trainingIcon.setRotate(0);
        trainingIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/fail.png"))
        ));
        trainingTitleTxt.setText("Error: " + message);
        btnTrain.setDisable(false);
        btnPrevious.setDisable(false);
    }

    private void showTrainingResult(TrainingResult result) {

        trainingIcon.setRotate(0);
        trainingIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/check.png"))
        ));

        lblTrainingTime.setText(result.time());
        lblMetric.setText(result.metric());
        lblResult.setText("Exitoso");

        loadMetricIcons();

        trainingMetrics.setVisible(true);
        trainingMetrics.setManaged(true);

        trainingTitleTxt.setText("El modelo fue entrenado correctamente y está listo para su uso.");

        validPhase3 = true;
        updateFooterState();
    }

    private void showTrainingError() {

        trainingIcon.setRotate(0);
        trainingIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/fail.png"))
        ));

        trainingTitleTxt.setText("El modelo fue entrenado incorrectamente y reintentalo más tarde.");

        btnTrain.setDisable(false);
        btnPrevious.setDisable(false);
    }

    private void showPhase3() {

        // 🔹 Estado inicial del icono
        trainingIcon.setRotate(0);
        trainingIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/brain.png"))
        ));

        // 🔹 Texto inicial
        trainingInfo.setText(
                "El entrenamiento se ejecutará con los parámetros configurados en la fase anterior."
        );

        // 🔹 Métricas ocultas
        trainingMetrics.setVisible(false);
        trainingMetrics.setManaged(false);

        // 🔹 Botón habilitado
        btnTrain.setDisable(false);
    }

    private void loadMetricIcons() {
        timeIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/time.png"))
        ));
        metricIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/metric.png"))
        ));
        resultIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/result.png"))
        ));
    }

    /**
     * Muestra los resultados en la Fase 4.
     * Usa datos reales si hay un lastTrainResponse, sino usa mock.
     */
    private void showPhase4Results() {
        if (lastTrainResponse != null) {
            loadResultsKpisFromApi();
            loadTrainingSummaryFromApi();
            loadModelInfoFromApi();

            // Solicitar forecast para alimentar los charts
            requestForecastForCharts();
        } else {
            loadResultsKpis();
            loadTrainingSummary();
            loadModelInfo();
        }
        loadResultsCharts();
        predictiveFooter.setVisible(false);
        predictiveFooter.setManaged(false);
    }

    /**
     * Carga KPIs reales desde la respuesta del entrenamiento.
     */
    private void loadResultsKpisFromApi() {
        resultsKpiContainer.getChildren().clear();

        Map<String, Object> metrics = lastTrainResponse.getMetrics();
        String r2 = metrics != null && metrics.containsKey("r2_score")
                ? String.format("%.1f%%", ((Number) metrics.get("r2_score")).doubleValue() * 100) : "N/A";
        String mae = metrics != null && metrics.containsKey("mae")
                ? String.format("$%.2f", ((Number) metrics.get("mae")).doubleValue()) : "N/A";
        String rmse = metrics != null && metrics.containsKey("rmse")
                ? String.format("$%.2f", ((Number) metrics.get("rmse")).doubleValue()) : "N/A";

        boolean meetsThreshold = Boolean.TRUE.equals(lastTrainResponse.getMeetsR2Threshold());

        List<ResultKpiDTO> kpis = List.of(
                new ResultKpiDTO(
                        "🎯", r2, "Precisión (R²)",
                        meetsThreshold ? "Supera umbral 0.7" : "Bajo umbral 0.7",
                        meetsThreshold ? "kpi-green" : "kpi-red",
                        meetsThreshold ? ResultKpiDTO.TrendType.POSITIVE : ResultKpiDTO.TrendType.NEGATIVE
                ),
                new ResultKpiDTO(
                        "📉", mae, "Error Absoluto Medio (MAE)",
                        "Mean Absolute Error",
                        "kpi-blue",
                        ResultKpiDTO.TrendType.NEUTRAL
                ),
                new ResultKpiDTO(
                        "📊", rmse, "Error Cuadrático (RMSE)",
                        "Root Mean Squared Error",
                        "kpi-purple",
                        ResultKpiDTO.TrendType.NEUTRAL
                ),
                new ResultKpiDTO(
                        "📦",
                        lastTrainResponse.getTrainingSamples() != null
                                ? lastTrainResponse.getTrainingSamples().toString() : "N/A",
                        "Muestras Entrenamiento",
                        lastTrainResponse.getTestSamples() != null
                                ? lastTrainResponse.getTestSamples() + " muestras de prueba" : "",
                        "kpi-green",
                        ResultKpiDTO.TrendType.NEUTRAL
                )
        );

        for (ResultKpiDTO kpi : kpis) {
            resultsKpiContainer.getChildren().add(ResultKpiCard.createKpiCard(kpi));
        }
    }

    /**
     * Carga resumen de entrenamiento con datos reales.
     */
    private void loadTrainingSummaryFromApi() {
        trainingSummaryContainer.getChildren().clear();

        Map<String, Object> metrics = lastTrainResponse.getMetrics();

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("Modelo", lastTrainResponse.getModelType() != null
                ? lastTrainResponse.getModelType() : "N/A");
        summary.put("Clave", lastTrainResponse.getModelKey() != null
                ? lastTrainResponse.getModelKey() : "N/A");
        if (metrics != null) {
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    summary.put(entry.getKey().toUpperCase(), String.format("%.4f", ((Number) entry.getValue()).doubleValue()));
                }
            }
        }
        summary.put("Muestras Entrenamiento", lastTrainResponse.getTrainingSamples() != null
                ? lastTrainResponse.getTrainingSamples().toString() : "N/A");
        summary.put("Muestras Prueba", lastTrainResponse.getTestSamples() != null
                ? lastTrainResponse.getTestSamples().toString() : "N/A");
        summary.put("Cumple R² >= 0.7", Boolean.TRUE.equals(lastTrainResponse.getMeetsR2Threshold())
                ? "Sí" : "No");

        for (Map.Entry<String, String> entry : summary.entrySet()) {
            trainingSummaryContainer.getChildren().add(createSummaryItem(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Carga información del modelo con datos reales.
     */
    private void loadModelInfoFromApi() {
        modelInfoContainer.getChildren().clear();

        Map<String, String> info = new LinkedHashMap<>();
        info.put("Modelo", selectedModel != null ? selectedModel.title() : "N/A");
        info.put("Tipo API", lastTrainResponse.getModelType() != null
                ? lastTrainResponse.getModelType() : "N/A");
        info.put("Clave", lastTrainResponse.getModelKey() != null
                ? lastTrainResponse.getModelKey() : "N/A");
        info.put("Entrenado", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        info.put("Estado", Boolean.TRUE.equals(lastTrainResponse.getMeetsR2Threshold())
                ? "Activo" : "Bajo rendimiento");
        if (lastTrainResponse.getRecommendation() != null) {
            info.put("Recomendación", lastTrainResponse.getRecommendation());
        }

        for (Map.Entry<String, String> entry : info.entrySet()) {
            modelInfoContainer.getChildren().add(createSummaryItem(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Solicita un forecast para alimentar los charts con datos reales.
     * Cuando el forecast llega, actualiza los charts automáticamente.
     */
    private void requestForecastForCharts() {
        if (lastTrainResponse == null || lastTrainResponse.getModelKey() == null) return;

        int periods = phase2Config != null ? phase2Config.getPredictionHorizon() * 30 : 30;
        // Limitar a 180 días (RN-03.03)
        periods = Math.min(periods, 180);

        ForecastRequestDTO forecastReq = new ForecastRequestDTO(
                lastTrainResponse.getModelKey(), null, periods
        );

        predictionService.forecast(forecastReq)
                .thenAccept(response -> Platform.runLater(() -> {
                    if (response != null && response.isSuccess()) {
                        lastForecastResponse = response;
                        // Recargar charts con datos reales del forecast
                        resultsChartsContainer.getChildren().clear();
                        loadForecastCharts();
                    }
                }))
                .exceptionally(ex -> {
                    System.out.println("Forecast para charts no disponible: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Carga los charts usando datos reales del forecast.
     */
    @SuppressWarnings("unchecked")
    private void loadForecastCharts() {
        Map<String, Object> predictions = lastForecastResponse.getPredictions();
        if (predictions == null) return;

        java.util.List<String> dates = (java.util.List<String>) predictions.get("dates");
        java.util.List<Number> values = (java.util.List<Number>) predictions.get("values");

        // Chart 1: Predicción de Ventas (LineChart con datos reales)
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/components/charts/LineChartCard.fxml")
            );
            VBox chartNode = loader.load();
            LineChartCardController controller = loader.getController();

            controller.setTitle("Predicción de Ventas");
            controller.setSubtitle("Forecast - " + lastForecastResponse.getPeriods() + " períodos");

            if (dates != null && values != null) {
                XYChart.Series<String, Number> forecastSeries = new XYChart.Series<>();
                forecastSeries.setName("Predicción");

                int step = Math.max(1, dates.size() / 30); // Limitar puntos para legibilidad
                for (int i = 0; i < dates.size(); i += step) {
                    String dateLabel = dates.get(i).length() > 10
                            ? dates.get(i).substring(5, 10) : dates.get(i);
                    forecastSeries.getData().add(new XYChart.Data<>(dateLabel, values.get(i)));
                }

                controller.loadCustomData(forecastSeries);
            } else {
                controller.loadData();
            }

            resultsChartsContainer.getChildren().add(chartNode);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Chart 2: Intervalos de Confianza (BarChart con valores y CI)
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/components/charts/BarChartCard.fxml")
            );
            VBox chartNode = loader.load();
            BarChartCardController controller = loader.getController();

            controller.setTitle("Predicción por Período");
            controller.setSubtitle("Valores predichos agrupados");

            if (dates != null && values != null) {
                XYChart.Series<String, Number> valueSeries = new XYChart.Series<>();
                valueSeries.setName("Valor Predicho");

                // Agrupar por semanas/meses para el bar chart
                int groupSize = Math.max(1, dates.size() / 12);
                for (int i = 0; i < dates.size(); i += groupSize) {
                    double sum = 0;
                    int count = 0;
                    for (int j = i; j < Math.min(i + groupSize, values.size()); j++) {
                        sum += values.get(j).doubleValue();
                        count++;
                    }
                    String label = dates.get(i).length() > 10
                            ? dates.get(i).substring(5, 10) : dates.get(i);
                    valueSeries.getData().add(new XYChart.Data<>(label, sum / count));
                }

                controller.loadCustomData(valueSeries);
            } else {
                controller.loadData();
            }

            resultsChartsContainer.getChildren().add(chartNode);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Chart 3: Métricas del Modelo (BarChart)
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/components/charts/BarChartCard.fxml")
            );
            VBox chartNode = loader.load();
            BarChartCardController controller = loader.getController();

            controller.setTitle("Métricas del Modelo");
            controller.setSubtitle("Indicadores de rendimiento");

            if (lastTrainResponse != null && lastTrainResponse.getMetrics() != null) {
                XYChart.Series<String, Number> metricsSeries = new XYChart.Series<>();
                metricsSeries.setName("Métricas");

                for (Map.Entry<String, Object> entry : lastTrainResponse.getMetrics().entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        metricsSeries.getData().add(
                                new XYChart.Data<>(entry.getKey().toUpperCase(), ((Number) entry.getValue()).doubleValue())
                        );
                    }
                }

                controller.loadCustomData(metricsSeries);
            } else {
                controller.loadData();
            }

            resultsChartsContainer.getChildren().add(chartNode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Carga las KPI cards de resultados
     */
    private void loadResultsKpis() {
        List<ResultKpiDTO> kpis = getMockResultKpis();

        resultsKpiContainer.getChildren().clear();

        for (ResultKpiDTO kpi : kpis) {
            VBox card = ResultKpiCard.createKpiCard(kpi);
            resultsKpiContainer.getChildren().add(card);
        }
    }

    /**
     * Datos de ejemplo para KPIs
     */
    private List<ResultKpiDTO> getMockResultKpis() {
        return List.of(
                new ResultKpiDTO(
                        "🎯",
                        "94.2%",
                        "Precisión del Modelo",
                        "Accuracy score",
                        "kpi-green",
                        ResultKpiDTO.TrendType.POSITIVE
                ),
                new ResultKpiDTO(
                        "📈",
                        "$156,240",
                        "Predicción Próximo Mes",
                        "+12.3% vs actual",
                        "kpi-blue",
                        ResultKpiDTO.TrendType.POSITIVE
                ),
                new ResultKpiDTO(
                        "⚡",
                        "2m 15s",
                        "Tiempo de Entrenamiento",
                        "Bajo umbral esperado",
                        "kpi-purple",
                        ResultKpiDTO.TrendType.NEUTRAL
                ),
                new ResultKpiDTO(
                        "🔍",
                        "98.7%",
                        "Confiabilidad",
                        "R² score",
                        "kpi-green",
                        ResultKpiDTO.TrendType.POSITIVE
                )
        );
    }

    /**
     * Carga los charts de resultados
     */
    private void loadResultsCharts() {
        Task<Void> loadChartsTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    // Chart 1: Predicción vs Real (LineChart)
                    Platform.runLater(() -> loadPredictionChart());
                    Thread.sleep(200);

                    // Chart 2: Distribución de Errores (BarChart)
                    Platform.runLater(() -> loadErrorDistributionChart());
                    Thread.sleep(200);

                    // Chart 3: Importancia de Variables (HorizontalBarChart)
                    Platform.runLater(() -> loadFeatureImportanceChart());

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }
        };

        new Thread(loadChartsTask).start();
    }

    /**
     * Carga chart de predicción vs real
     */
    private void loadPredictionChart() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/components/charts/LineChartCard.fxml")
            );
            VBox chartNode = loader.load();
            LineChartCardController controller = loader.getController();

            controller.setTitle("Predicción vs Datos Reales");
            controller.setSubtitle("Comparativa de resultados");

            // TODO: Cargar datos reales desde el resultado del modelo
            controller.loadData();

            resultsChartsContainer.getChildren().add(chartNode);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Carga chart de distribución de errores
     */
    private void loadErrorDistributionChart() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/components/charts/BarChartCard.fxml")
            );
            VBox chartNode = loader.load();
            BarChartCardController controller = loader.getController();

            controller.setTitle("Distribución de Errores");
            controller.setSubtitle("Error absoluto medio por período");

            controller.loadData();

            resultsChartsContainer.getChildren().add(chartNode);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Carga chart de importancia de variables
     */
    private void loadFeatureImportanceChart() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/components/charts/BarChartCard.fxml")
            );
            VBox chartNode = loader.load();
            BarChartCardController controller = loader.getController();

            controller.setTitle("Importancia de Variables");
            controller.setSubtitle("Features más relevantes del modelo");

            controller.loadData();

            resultsChartsContainer.getChildren().add(chartNode);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Carga el resumen del entrenamiento
     */
    private void loadTrainingSummary() {
        trainingSummaryContainer.getChildren().clear();

        Map<String, String> summary = Map.of(
                "Duración", "2m 15s",
                "Epochs", "100",
                "Accuracy", "94.2%",
                "Loss Final", "0.058",
                "Datos Entrenamiento", "1,250 registros",
                "Datos Validación", "320 registros"
        );

        for (Map.Entry<String, String> entry : summary.entrySet()) {
            HBox item = createSummaryItem(entry.getKey(), entry.getValue());
            trainingSummaryContainer.getChildren().add(item);
        }
    }

    /**
     * Carga la información del modelo
     */
    private void loadModelInfo() {
        modelInfoContainer.getChildren().clear();

        if (selectedModel == null) return;

        Map<String, String> info = Map.of(
                "Modelo", selectedModel.title(),
                "Tipo", "Serie Temporal",
                "Versión", "1.0",
                "Entrenado", java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                "Estado", "Activo"
        );

        for (Map.Entry<String, String> entry : info.entrySet()) {
            HBox item = createSummaryItem(entry.getKey(), entry.getValue());
            modelInfoContainer.getChildren().add(item);
        }
    }

    /**
     * Crea un item para el summary (key: value)
     */
    private HBox createSummaryItem(String key, String value) {
        HBox item = new HBox(8);
        item.getStyleClass().add("summary-item");
        item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label keyLabel = new Label(key + ":");
        keyLabel.getStyleClass().add("summary-key");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("summary-value");
        valueLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(valueLabel, javafx.scene.layout.Priority.ALWAYS);

        item.getChildren().addAll(keyLabel, valueLabel);

        return item;
    }

    // ============================================
    // ACCIONES RÁPIDAS - BOTONES
    // ============================================

    @FXML
    private void onSavePrediction() {
        System.out.println("Guardando predicción...");
        // TODO: Implementar guardado en BD
        WarningService.show("Predicción guardada exitosamente");
    }

    @FXML
    private void onGenerateReport() {
        System.out.println("Generando reporte...");
        // TODO: Generar PDF/Excel con resultados
        WarningService.show("Generando reporte...");
    }

    @FXML
    private void onRetrainModel() {
        System.out.println("Reentrenando modelo...");
        //Limpia fase 4
        clearPhase4Content();
        currentPhaseIndex = 1;
        predictiveFooter.setVisible(true); //Desactivo los botones para que no haya forma de regresar o seguir
        predictiveFooter.setManaged(true);
        updatePhases();
        updatePhaseView();
        updateFooterState();
        showPhase3();
    }

    @FXML
    private void onExportData() {
        System.out.println("Exportando datos...");
        // TODO: Exportar datos a CSV/Excel
        WarningService.show("Exportando datos...");
    }

    /**
     * Limpia todo el contenido de la Fase 4
     */
    private void clearPhase4Content() {
        if (resultsKpiContainer != null) {
            resultsKpiContainer.getChildren().clear();
        }

        if (resultsChartsContainer != null) {
            resultsChartsContainer.getChildren().clear();
        }

        if (trainingSummaryContainer != null) {
            trainingSummaryContainer.getChildren().clear();
        }

        if (modelInfoContainer != null) {
            modelInfoContainer.getChildren().clear();
        }

        validPhase3 = false;

        System.out.println("✓ Contenido de Fase 4 limpiado");
    }

}

