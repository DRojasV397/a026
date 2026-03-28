package com.app.ui.predictive;

import com.app.core.session.UserSession;
import com.app.model.Phase2ConfigDTO;
import com.app.model.PredictiveModelDTO;
import com.app.model.ResultKpiDTO;
import com.app.model.TrainingResult;
import com.app.model.predictions.ForecastRequestDTO;
import com.app.model.predictions.ForecastResponseDTO;
import com.app.model.predictions.PackForecastResponseDTO;
import com.app.model.predictions.PackInfoDTO;
import com.app.model.predictions.PackTrainRequestDTO;
import com.app.model.predictions.PackTrainResponseDTO;
import com.app.model.predictions.TrainModelResponseDTO;
import com.app.model.predictions.UserModelDTO;
import com.app.service.alerts.WarningService;
import com.app.service.predictions.PredictionService;
import com.app.service.reports.ReportGeneratorService;
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

    // Controles de hiperparámetros avanzados (Regresión Múltiple)
    private ComboBox<String> regularizationCombo;
    private Slider alphaSlider;
    private Label alphaValueLabel;
    private ComboBox<String> polynomialCombo;
    private CheckBox logTransformCheck;
    private CheckBox autoTuneCheck;

    // Controles de hiperparámetros avanzados (Ensemble)
    private List<CheckBox> ensembleBaseModelChecks;
    private ComboBox<String> ensembleMetaLearnerCombo;

    // Controles de hiperparámetros avanzados (XGBoost)
    private Slider xgboostNEstimatorsSlider;
    private ComboBox<String> xgboostMaxDepthCombo;
    private Slider xgboostLearningRateSlider;
    private Slider xgboostSubsampleSlider;

    // Controles de hiperparámetros avanzados (Prophet)
    private Slider prophetChangepointSlider;
    private CheckBox prophetYearlySeasonalityCheck;
    private CheckBox prophetWeeklySeasonalityCheck;

    // Controles de hiperparámetros avanzados (Pack Ventas + Compras)
    private ComboBox<String> packVentasRegularizationCombo;
    private Slider packVentasAlphaSlider;
    private Label packVentasAlphaValueLabel;
    private ComboBox<String> packComprasRegularizationCombo;
    private Slider packComprasAlphaSlider;
    private Label packComprasAlphaValueLabel;

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

    @FXML private VBox savedModelsSection;
    @FXML private FlowPane savedModelsContainer;
    @FXML private Label savedModelsEmptyLabel;

    private int currentPhaseIndex = 0;
    private PredictiveModelDTO selectedModel;
    private UserModelDTO selectedSavedModel;
    private boolean validPhase2 = false;
    private boolean validPhase3 = false;
    private final List<VBox> modelCards = new ArrayList<>();
    private final List<VBox> savedModelCards = new ArrayList<>();

    private RotateTransition loadingRotation;

    private final PredictionService predictionService = new PredictionService();
    private final ReportGeneratorService reportGenerator = new ReportGeneratorService();
    private TrainModelResponseDTO lastTrainResponse;
    private ForecastResponseDTO lastForecastResponse;
    private PackTrainResponseDTO lastPackTrainResponse;
    private PackForecastResponseDTO lastPackForecastResponse;
    private PackInfoDTO selectedSavedPack;
    private final List<VBox> savedPackCards = new ArrayList<>();
    private String lastPreviewDateRange = null;

    @FXML
    private void initialize() {
        phases = List.of(phase1, phase2, phase3, phase4);

        currentPhaseIndex = 0; // Fase 1
        updatePhases();
        updatePhaseView();

        loadModels();
        loadUserModels();
        clearDetailPanel();
        updateFooterState();

        if (UserSession.isOfflineMode()) applyOfflineRestrictions();
    }

    private void applyOfflineRestrictions() {
        btnTrain.setDisable(true);
        btnNext.setDisable(true);
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
                        "📉", "Alta", "Instantáneo",
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
                        "📈", "Muy Alta", "Rápido",
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
                        "🗓", "Excelente", "Moderado",
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
                        "🌳", "Superior", "Intensivo",
                        List.of(
                                new PredictiveModelDTO.ModelFeature("🌲", "Bosque", "Ensamble de 10-1000 árboles", "#27ae60"),
                                new PredictiveModelDTO.ModelFeature("🏗", "Muestreo", "Técnica Bootstrap (Bagging)", "#8e44ad"),
                                new PredictiveModelDTO.ModelFeature("🎯", "Precisión", "Mejor manejo de outliers", "#e74c3c")
                        )
                ),
                new PredictiveModelDTO(
                        "Regresión Múltiple",
                        "Regresión enriquecida con compras, lags y variables exógenas.",
                        List.of("Exógeno", "Lags", "Configurable"),
                        "Supera a la regresión lineal al incorporar datos de compras como predictor y lags de autocorrelación.",
                        "📐", "Alta", "Rápido",
                        List.of(
                                new PredictiveModelDTO.ModelFeature("🛒", "Variable exógena", "Datos de compras como predictor", "#e67e22"),
                                new PredictiveModelDTO.ModelFeature("🔁", "Autocorrelación", "Lags 1, 7, 14 y 30 días", "#3498db"),
                                new PredictiveModelDTO.ModelFeature("🛡", "Regularización", "Ridge / Lasso / ElasticNet", "#2ecc71")
                        )
                ),
                new PredictiveModelDTO(
                        "Modelo Ensemble",
                        "Combina múltiples modelos con un meta-learner que aprende los pesos óptimos.",
                        List.of("Stacking", "Meta-Learner", "Multi-Modelo"),
                        "Supera modelos individuales capturando patrones complementarios: tendencia, lags y no-linealidad en conjunto.",
                        "🧩", "Superior", "Intensivo",
                        List.of(
                                new PredictiveModelDTO.ModelFeature("🔗", "Técnica", "Stacking OOF temporal (sin data leakage)", "#9b59b6"),
                                new PredictiveModelDTO.ModelFeature("🤖", "Meta-Learner", "Ridge aprende pesos óptimos por modelo base", "#3498db"),
                                new PredictiveModelDTO.ModelFeature("📐", "Modelos Base", "Reg. Lineal + Reg. Múltiple + Random Forest", "#27ae60")
                        )
                ),
                new PredictiveModelDTO(
                        "XGBoost",
                        "Gradient Boosting de alta precisión con regularización integrada para series de tiempo.",
                        List.of("Gradient Boosting", "Regularización", "Lags"),
                        "Combina boosting secuencial con regularización L1/L2 para capturar relaciones no lineales con lags automáticos.",
                        "⚡", "Superior", "Moderado",
                        List.of(
                                new PredictiveModelDTO.ModelFeature("🚀", "Técnica", "Boosting secuencial de árboles", "#e67e22"),
                                new PredictiveModelDTO.ModelFeature("🛡", "Regularización", "L1 (reg_alpha) y L2 (reg_lambda)", "#2ecc71"),
                                new PredictiveModelDTO.ModelFeature("🔁", "Lags", "Automáticos: 1d, 7d, 14d, 30d", "#3498db")
                        )
                ),
                new PredictiveModelDTO(
                        "Prophet",
                        "Modelo de Facebook para series de tiempo de negocio con estacionalidad múltiple.",
                        List.of("Facebook", "Estacional", "Tendencia"),
                        "Detecta tendencia adaptativa y estacionalidades anuales y semanales de forma automática, robusto ante outliers.",
                        "🔮", "Alta", "Moderado",
                        List.of(
                                new PredictiveModelDTO.ModelFeature("📅", "Estacionalidad", "Anual + semanal automática", "#9b59b6"),
                                new PredictiveModelDTO.ModelFeature("📈", "Tendencia", "Adaptativa con changepoints", "#3498db"),
                                new PredictiveModelDTO.ModelFeature("🎯", "Confianza", "Intervalos de confianza nativos (80%)", "#27ae60")
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

    private void loadUserModels() {
        if (savedModelsContainer == null || savedModelsSection == null) return;

        // Cargar modelos individuales
        predictionService.getUserModels()
                .thenAccept(models -> Platform.runLater(() -> {
                    savedModelCards.clear();
                    savedModelsContainer.getChildren().clear();
                    savedPackCards.clear();

                    boolean hasContent = (models != null && !models.isEmpty());
                    if (hasContent) {
                        if (savedModelsEmptyLabel != null) {
                            savedModelsEmptyLabel.setVisible(false);
                            savedModelsEmptyLabel.setManaged(false);
                        }
                        savedModelsContainer.setVisible(true);
                        savedModelsContainer.setManaged(true);
                        for (UserModelDTO model : models) {
                            VBox card = createSavedModelCard(model);
                            savedModelCards.add(card);
                            savedModelsContainer.getChildren().add(card);
                        }
                    }

                    // Cargar packs del usuario en la misma sección
                    predictionService.getUserPacks()
                            .thenAccept(packs -> Platform.runLater(() -> {
                                if (packs != null && !packs.isEmpty()) {
                                    if (savedModelsEmptyLabel != null) {
                                        savedModelsEmptyLabel.setVisible(false);
                                        savedModelsEmptyLabel.setManaged(false);
                                    }
                                    savedModelsContainer.setVisible(true);
                                    savedModelsContainer.setManaged(true);
                                    for (PackInfoDTO pack : packs) {
                                        VBox card = createSavedPackCard(pack);
                                        savedPackCards.add(card);
                                        savedModelsContainer.getChildren().add(card);
                                    }
                                } else if (!hasContent) {
                                    if (savedModelsEmptyLabel != null) {
                                        savedModelsEmptyLabel.setVisible(true);
                                        savedModelsEmptyLabel.setManaged(true);
                                    }
                                    savedModelsContainer.setVisible(false);
                                    savedModelsContainer.setManaged(false);
                                }
                            }))
                            .exceptionally(ex -> {
                                System.out.println("Error cargando packs del usuario: " + ex.getMessage());
                                return null;
                            });
                }))
                .exceptionally(ex -> {
                    System.out.println("Error cargando modelos del usuario: " + ex.getMessage());
                    return null;
                });
    }

    private VBox createSavedModelCard(UserModelDTO model) {
        VBox card = new VBox(10);
        card.getStyleClass().add("model-card");
        card.setMaxWidth(280);

        String displayName = (model.getNombre() != null && !model.getNombre().isEmpty())
                ? model.getNombre()
                : model.getModelType();
        Label nameLabel = new Label(displayName);
        nameLabel.getStyleClass().add("model-title");
        nameLabel.setWrapText(true);

        Label typeLabel = new Label("Tipo: " + (model.getModelType() != null ? model.getModelType() : "N/A"));
        typeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        String r2Text = model.getPrecision() > 0
                ? String.format("R² = %.4f", model.getPrecision())
                : "R² = N/A";
        Label r2Label = new Label(r2Text);
        r2Label.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");

        String fecha = model.getFechaEntrenamiento() != null
                ? model.getFechaEntrenamiento().substring(0, Math.min(10, model.getFechaEntrenamiento().length()))
                : "—";
        Label fechaLabel = new Label("Entrenado: " + fecha);
        fechaLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6;");

        // Badge de estado
        Label estadoBadge = new Label(model.getEstado() != null ? model.getEstado() : "Activo");
        estadoBadge.setStyle("-fx-background-color: #e8f8f5; -fx-text-fill: #27ae60; "
                + "-fx-padding: 2 8; -fx-background-radius: 10; -fx-font-size: 10px;");

        Button useBtn = new Button("Usar este modelo");
        useBtn.setMaxWidth(Double.MAX_VALUE);
        useBtn.getStyleClass().add("select-button");
        useBtn.setOnAction(e -> {
            selectSavedModel(model, card, useBtn);
            e.consume();
        });
        card.setOnMouseClicked(e -> selectSavedModel(model, card, useBtn));

        card.getChildren().addAll(nameLabel, typeLabel, r2Label, fechaLabel, estadoBadge,
                new Separator(), useBtn);
        return card;
    }

    private VBox createSavedPackCard(PackInfoDTO pack) {
        VBox card = new VBox(10);
        card.getStyleClass().add("model-card");
        card.setMaxWidth(280);

        Label badgePack = new Label("📦 Modelo");
        badgePack.setStyle("-fx-background-color: #fef9e7; -fx-text-fill: #e67e22; "
                + "-fx-padding: 2 8; -fx-background-radius: 10; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label nameLabel = new Label(pack.getDisplayName());
        nameLabel.getStyleClass().add("model-title");
        nameLabel.setWrapText(true);

        String ventasR2 = pack.getVentas() != null
                ? String.format("Ventas R²=%.4f", pack.getVentas().getR2Score()) : "Ventas: N/A";
        String comprasR2 = pack.getCompras() != null
                ? String.format("Compras R²=%.4f", pack.getCompras().getR2Score()) : "Compras: N/A";
        Label metricsLabel = new Label(ventasR2 + "\n" + comprasR2);
        metricsLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
        metricsLabel.setWrapText(true);

        String fecha = pack.getCreado_en() != null
                ? pack.getCreado_en().substring(0, Math.min(10, pack.getCreado_en().length())) : "—";
        Label fechaLabel = new Label("Creado: " + fecha);
        fechaLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6;");

        Button useBtn = new Button("Usar este modelo");
        useBtn.setMaxWidth(Double.MAX_VALUE);
        useBtn.getStyleClass().add("select-button");
        useBtn.setOnAction(e -> {
            selectSavedPack(pack, card, useBtn);
            e.consume();
        });
        card.setOnMouseClicked(e -> selectSavedPack(pack, card, useBtn));

        card.getChildren().addAll(badgePack, nameLabel, metricsLabel, fechaLabel, new Separator(), useBtn);
        return card;
    }

    private void selectSavedPack(PackInfoDTO pack, VBox card, Button btn) {
        selectedSavedPack = pack;
        selectedSavedModel = null;
        selectedModel = null;

        savedPackCards.forEach(c -> c.getStyleClass().remove("selected"));
        savedModelCards.forEach(c -> {
            c.getStyleClass().remove("selected");
            Button b = (Button) c.lookup(".select-button");
            if (b != null) b.setText("Usar este modelo");
        });
        modelCards.forEach(c -> {
            c.getStyleClass().remove("selected");
            Button b = (Button) c.lookup(".select-button");
            if (b != null) b.setText("Seleccionar");
        });

        card.getStyleClass().add("selected");
        btn.setText("Seleccionado");

        updateFooterState();
    }

    private void selectSavedModel(UserModelDTO model, VBox card, Button btn) {
        selectedSavedModel = model;
        selectedSavedPack = null;
        selectedModel = findModelDTOByType(model.getModelType());

        savedModelCards.forEach(c -> c.getStyleClass().remove("selected"));
        savedPackCards.forEach(c -> {
            c.getStyleClass().remove("selected");
            Button b = (Button) c.lookup(".select-button");
            if (b != null) b.setText("Usar este modelo");
        });
        card.getStyleClass().add("selected");
        btn.setText("Seleccionado");

        // Deseleccionar nuevos modelos
        modelCards.forEach(c -> {
            c.getStyleClass().remove("selected");
            Button selectBtn = (Button) c.lookup(".select-button");
            if (selectBtn != null) selectBtn.setText("Seleccionar");
        });

        updateFooterState();
    }

    private PredictiveModelDTO findModelDTOByType(String modelType) {
        if (modelType == null) return null;
        for (PredictiveModelDTO dto : getMockPredictiveModels()) {
            if (modelType.equals(getApiModelType(dto.title()))) {
                return dto;
            }
        }
        return getMockPredictiveModels().get(0);
    }

    private TrainModelResponseDTO buildTrainResponseFromSavedModel(UserModelDTO saved) {
        TrainModelResponseDTO response = new TrainModelResponseDTO();
        response.setSuccess(true);
        response.setModelKey(saved.getModelKey());
        response.setModelType(saved.getModelType());
        response.setMetrics(saved.getMetricas());
        response.setMeetsR2Threshold(saved.getPrecision() >= 0.5);
        return response;
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
                createInfoRow("Rendimiento típico:", model.presicion());

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
        selectedSavedModel = null;
        selectedSavedPack = null;

        modelCards.forEach(card -> card.getStyleClass().remove("selected"));
        modelCards.forEach(card ->
                ((Button) card.lookup(".select-button")).setText("Seleccionar")
        );

        // Deseleccionar saved model cards
        savedModelCards.forEach(c -> {
            c.getStyleClass().remove("selected");
            Button btn = (Button) c.lookup(".select-button");
            if (btn != null) btn.setText("Usar este modelo");
        });
        savedPackCards.forEach(c -> {
            c.getStyleClass().remove("selected");
            Button btn = (Button) c.lookup(".select-button");
            if (btn != null) btn.setText("Usar este pack");
        });

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
            case "Regresión Múltiple" -> "multiple_regression";
            case "Modelo Ensemble" -> "ensemble";
            case "XGBoost" -> "xgboost";
            case "Prophet" -> "prophet";
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
        // Saltar directo a Fase 4 si hay un modelo guardado seleccionado
        if (currentPhaseIndex == 0 && selectedSavedModel != null) {
            lastTrainResponse = buildTrainResponseFromSavedModel(selectedSavedModel);
            lastPackTrainResponse = null;
            currentPhaseIndex = 3;
            updatePhases();
            updatePhaseView();
            updateFooterState();
            showPhase4Results();
            return;
        }

        // Saltar directo a Fase 4 si hay un pack guardado seleccionado
        if (currentPhaseIndex == 0 && selectedSavedPack != null) {
            lastPackTrainResponse = buildPackResponseFromSavedPack(selectedSavedPack);
            lastTrainResponse = null;
            currentPhaseIndex = 3;
            updatePhases();
            updatePhaseView();
            updateFooterState();
            showPhase4Results();
            return;
        }

        if (currentPhaseIndex < phases.size() - 1) {
            currentPhaseIndex++;
            updatePhases();
            updatePhaseView();
            updateFooterState();

            if (currentPhaseIndex == 1) { // se solicita la fase 2 y aún no es valida la información seleccionada
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
            case 0 -> selectedModel != null || selectedSavedModel != null || selectedSavedPack != null; // Fase 1
            case 1 -> validPhase2;                  // Fase 2
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

        // 0. NOMBRE DEL MODELO
        VBox nameCard = createModelNameCard();
        parametersContainer.getChildren().add(nameCard);

        // 1. RANGO DE FECHAS
        VBox dateRangeCard = createDateRangeCard();
        parametersContainer.getChildren().add(dateRangeCard);

        // 2. VARIABLES A CONSIDERAR
        VBox variablesCard = createVariablesCard();
        parametersContainer.getChildren().add(variablesCard);

        // 2.5 PARÁMETROS AVANZADOS — según modelo seleccionado
        if ("Regresión Múltiple".equals(selectedModel.title())) {
            VBox advancedCard = createMultipleRegressionParamsCard();
            parametersContainer.getChildren().add(advancedCard);
        } else if ("Modelo Ensemble".equals(selectedModel.title())) {
            VBox ensembleCard = createEnsembleParamsCard();
            parametersContainer.getChildren().add(ensembleCard);
        } else if ("XGBoost".equals(selectedModel.title())) {
            VBox xgboostCard = createXGBoostParamsCard();
            parametersContainer.getChildren().add(xgboostCard);
        } else if ("Prophet".equals(selectedModel.title())) {
            VBox prophetCard = createProphetParamsCard();
            parametersContainer.getChildren().add(prophetCard);
        }

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
     * 0. NOMBRE DEL MODELO (opcional)
     */
    private VBox createModelNameCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("param-card");

        Label title = new Label("Nombre del Modelo (opcional)");
        title.getStyleClass().add("param-card-title");

        Label note = new Label("Asigna un nombre descriptivo para identificar este modelo más tarde.");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6; -fx-font-style: italic;");
        note.setWrapText(true);

        TextField nameField = new TextField();
        nameField.setPromptText("Ej: Modelo ventas Q1 2024");
        nameField.setMaxWidth(Double.MAX_VALUE);
        nameField.textProperty().addListener((obs, old, newVal) ->
            phase2Config.setModelName(newVal.trim())
        );

        card.getChildren().addAll(title, note, nameField);
        return card;
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

        Label title = new Label("📊 Características del Modelo");
        title.getStyleClass().add("param-card-title");

        Label note = new Label("Variables que este modelo considera internamente para el análisis");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6; -fx-font-style: italic;");
        note.setWrapText(true);

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

        card.getChildren().addAll(title, note, selectAllCheckbox, sep, variablesGrid);
        return card;
    }

    /**
     * Card de hiperparámetros avanzados exclusivo de Regresión Múltiple.
     * Permite configurar: regularización, alpha, uso de compras y grado polinomial.
     */
    private VBox createMultipleRegressionParamsCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("param-card");

        Label title = new Label("⚙️ Parámetros de Regresión Múltiple");
        title.getStyleClass().add("param-card-title");

        Label note = new Label("Configura el tipo de regularización y las variables exógenas");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6; -fx-font-style: italic;");
        note.setWrapText(true);

        // ── Fila 1: Regularización ──────────────────────────────────────────
        VBox regBox = new VBox(6);
        Label regLabel = new Label("Tipo de Regularización");
        regLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        regularizationCombo = new ComboBox<>();
        regularizationCombo.getItems().addAll("Ridge (L2)", "Lasso (L1)", "ElasticNet (L1+L2)", "Ninguna");
        regularizationCombo.setValue("Ridge (L2)");
        regularizationCombo.setMaxWidth(Double.MAX_VALUE);
        regularizationCombo.setStyle("-fx-font-size: 12px;");

        Label regHint = new Label("Ridge: estabilidad general. Lasso: selección automática de features. ElasticNet: combinación.");
        regHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        regHint.setWrapText(true);

        regBox.getChildren().addAll(regLabel, regularizationCombo, regHint);

        // ── Fila 2: Alpha (fuerza de regularización) ────────────────────────
        VBox alphaBox = new VBox(6);
        HBox alphaHeader = new HBox(8);
        alphaHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label alphaLabel = new Label("Alpha (fuerza de regularización)");
        alphaLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Region alphaSpacerH = new Region();
        HBox.setHgrow(alphaSpacerH, Priority.ALWAYS);
        alphaValueLabel = new Label("1.00");
        alphaValueLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #3498db;");
        alphaHeader.getChildren().addAll(alphaLabel, alphaSpacerH, alphaValueLabel);

        alphaSlider = new Slider(0.01, 10.0, 1.0);
        alphaSlider.setShowTickLabels(false);
        alphaSlider.setShowTickMarks(false);
        alphaSlider.setMajorTickUnit(1.0);
        alphaSlider.setBlockIncrement(0.1);

        alphaSlider.valueProperty().addListener((obs, old, newVal) ->
            alphaValueLabel.setText(String.format("%.2f", newVal.doubleValue()))
        );

        HBox alphaLabelsRow = new HBox();
        Label alphaMin = new Label("0.01 (mínimo)");
        alphaMin.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        Region alphaSpacer = new Region();
        HBox.setHgrow(alphaSpacer, Priority.ALWAYS);
        Label alphaMax = new Label("10.0 (máximo)");
        alphaMax.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        alphaLabelsRow.getChildren().addAll(alphaMin, alphaSpacer, alphaMax);

        alphaBox.getChildren().addAll(alphaHeader, alphaSlider, alphaLabelsRow);

        // ── Fila 3: Grado polinomial ────────────────────────────────────────
        VBox polyBox = new VBox(6);
        Label polyLabel = new Label("Tendencia Temporal");
        polyLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        polynomialCombo = new ComboBox<>();
        polynomialCombo.getItems().addAll("Lineal (grado 1)", "Cuadrático (grado 2)");
        polynomialCombo.setValue("Lineal (grado 1)");
        polynomialCombo.setMaxWidth(Double.MAX_VALUE);
        polynomialCombo.setStyle("-fx-font-size: 12px;");

        Label polyHint = new Label("Cuadrático: captura aceleración o desaceleración en tendencia de ventas.");
        polyHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        polyHint.setWrapText(true);

        polyBox.getChildren().addAll(polyLabel, polynomialCombo, polyHint);

        // ── Fila 5: Opciones avanzadas de entrenamiento ─────────────────────
        VBox trainingOptsBox = new VBox(8);
        Label trainingOptsLabel = new Label("Opciones de Entrenamiento");
        trainingOptsLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        logTransformCheck = new CheckBox("Transformación logarítmica del target");
        logTransformCheck.setSelected(false);
        logTransformCheck.setStyle("-fx-font-size: 12px; -fx-text-fill: #2c3e50;");
        Label logHint = new Label("Actívalo si las ventas tienen varianza multiplicativa (crece con el nivel). Desactivado por defecto para datos con tendencia aditiva.");
        logHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        logHint.setWrapText(true);

        autoTuneCheck = new CheckBox("Auto-tuning de alpha (RidgeCV / LassoCV)");
        autoTuneCheck.setSelected(true);
        autoTuneCheck.setStyle("-fx-font-size: 12px; -fx-text-fill: #2c3e50;");
        Label autoTuneHint = new Label("Selecciona automáticamente la fuerza de regularización óptima usando validación cruzada temporal.");
        autoTuneHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        autoTuneHint.setWrapText(true);

        trainingOptsBox.getChildren().addAll(
                trainingOptsLabel,
                logTransformCheck, logHint,
                autoTuneCheck, autoTuneHint
        );

        card.getChildren().addAll(title, note, new Separator(), regBox, alphaBox,
                new Separator(), polyBox,
                new Separator(), trainingOptsBox);
        return card;
    }

    /**
     * Card de hiperparámetros para el Modelo Ensemble.
     * Permite seleccionar modelos base, meta-learner y uso de compras.
     */
    private VBox createEnsembleParamsCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("param-card");

        Label title = new Label("⚙️ Parámetros del Ensemble");
        title.getStyleClass().add("param-card-title");

        Label note = new Label("Configura los modelos base que se combinarán y el meta-learner");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6; -fx-font-style: italic;");
        note.setWrapText(true);

        // ── Modelos base ────────────────────────────────────────────────────
        VBox baseModelsBox = new VBox(8);
        Label baseLabel = new Label("Modelos Base");
        baseLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        ensembleBaseModelChecks = new ArrayList<>();
        String[] baseNames = {"Regresión Lineal", "Regresión Múltiple", "Random Forest"};
        for (String name : baseNames) {
            CheckBox cb = new CheckBox(name);
            cb.setSelected(true);
            cb.setStyle("-fx-font-size: 12px; -fx-text-fill: #2c3e50;");
            ensembleBaseModelChecks.add(cb);
            baseModelsBox.getChildren().add(cb);
        }

        Label baseHint = new Label("Selecciona al menos 2 modelos para el stacking.");
        baseHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        baseHint.setWrapText(true);
        baseModelsBox.getChildren().addAll(baseLabel, baseHint);
        // Reorder: label first, then checkboxes, then hint
        baseModelsBox.getChildren().clear();
        baseModelsBox.getChildren().add(baseLabel);
        for (CheckBox cb : ensembleBaseModelChecks) {
            baseModelsBox.getChildren().add(cb);
        }
        baseModelsBox.getChildren().add(baseHint);

        // ── Meta-Learner ────────────────────────────────────────────────────
        VBox metaBox = new VBox(6);
        Label metaLabel = new Label("Meta-Learner");
        metaLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        ensembleMetaLearnerCombo = new ComboBox<>();
        ensembleMetaLearnerCombo.getItems().addAll(
                "Ridge (recomendado)",
                "Promedio Ponderado por R²"
        );
        ensembleMetaLearnerCombo.setValue("Ridge (recomendado)");
        ensembleMetaLearnerCombo.setMaxWidth(Double.MAX_VALUE);
        ensembleMetaLearnerCombo.setStyle("-fx-font-size: 12px;");

        Label metaHint = new Label("Ridge: aprende pesos por regresión regularizada. Promedio Ponderado: pesos iguales a R² de cada modelo base.");
        metaHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        metaHint.setWrapText(true);

        metaBox.getChildren().addAll(metaLabel, ensembleMetaLearnerCombo, metaHint);

        card.getChildren().addAll(title, note, new Separator(), baseModelsBox,
                new Separator(), metaBox);
        return card;
    }

    /**
     * Card de hiperparámetros para XGBoost.
     */
    private VBox createXGBoostParamsCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("param-card");

        Label title = new Label("⚙️ Parámetros XGBoost");
        title.getStyleClass().add("param-card-title");

        Label note = new Label("Configura el boosting y la regularización del modelo");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6; -fx-font-style: italic;");
        note.setWrapText(true);

        // ── n_estimators ─────────────────────────────────────────
        VBox nEstBox = new VBox(6);
        HBox nEstHeader = new HBox(8);
        nEstHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label nEstLabel = new Label("Número de árboles (n_estimators)");
        nEstLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Region nEstSpacer = new Region();
        HBox.setHgrow(nEstSpacer, Priority.ALWAYS);
        Label nEstValueLbl = new Label("100");
        nEstValueLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #e67e22;");
        nEstHeader.getChildren().addAll(nEstLabel, nEstSpacer, nEstValueLbl);

        xgboostNEstimatorsSlider = new Slider(50, 500, 100);
        xgboostNEstimatorsSlider.setMajorTickUnit(50);
        xgboostNEstimatorsSlider.setBlockIncrement(10);
        xgboostNEstimatorsSlider.setSnapToTicks(false);
        xgboostNEstimatorsSlider.valueProperty().addListener((obs, old, nv) ->
            nEstValueLbl.setText(String.valueOf((int) nv.doubleValue()))
        );

        HBox nEstLabels = new HBox();
        Label nEstMin = new Label("50 (mínimo)");
        nEstMin.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        Region nEstSp2 = new Region();
        HBox.setHgrow(nEstSp2, Priority.ALWAYS);
        Label nEstMax = new Label("500 (máximo)");
        nEstMax.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        nEstLabels.getChildren().addAll(nEstMin, nEstSp2, nEstMax);
        nEstBox.getChildren().addAll(nEstHeader, xgboostNEstimatorsSlider, nEstLabels);

        // ── max_depth ─────────────────────────────────────────────
        VBox depthBox = new VBox(6);
        Label depthLabel = new Label("Profundidad máxima (max_depth)");
        depthLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        xgboostMaxDepthCombo = new ComboBox<>();
        xgboostMaxDepthCombo.getItems().addAll("3", "4", "5", "6", "8", "10");
        xgboostMaxDepthCombo.setValue("6");
        xgboostMaxDepthCombo.setMaxWidth(Double.MAX_VALUE);
        xgboostMaxDepthCombo.setStyle("-fx-font-size: 12px;");

        Label depthHint = new Label("Valores bajos evitan overfitting; valores altos capturan más complejidad.");
        depthHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        depthHint.setWrapText(true);
        depthBox.getChildren().addAll(depthLabel, xgboostMaxDepthCombo, depthHint);

        // ── learning_rate ──────────────────────────────────────────
        VBox lrBox = new VBox(6);
        HBox lrHeader = new HBox(8);
        lrHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label lrLabel = new Label("Tasa de aprendizaje (learning_rate)");
        lrLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Region lrSpacer = new Region();
        HBox.setHgrow(lrSpacer, Priority.ALWAYS);
        Label lrValueLbl = new Label("0.10");
        lrValueLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #3498db;");
        lrHeader.getChildren().addAll(lrLabel, lrSpacer, lrValueLbl);

        xgboostLearningRateSlider = new Slider(0.01, 0.30, 0.10);
        xgboostLearningRateSlider.setBlockIncrement(0.01);
        xgboostLearningRateSlider.valueProperty().addListener((obs, old, nv) ->
            lrValueLbl.setText(String.format("%.2f", nv.doubleValue()))
        );

        HBox lrLabels = new HBox();
        Label lrMin = new Label("0.01");
        lrMin.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        Region lrSp2 = new Region();
        HBox.setHgrow(lrSp2, Priority.ALWAYS);
        Label lrMax = new Label("0.30");
        lrMax.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        lrLabels.getChildren().addAll(lrMin, lrSp2, lrMax);
        lrBox.getChildren().addAll(lrHeader, xgboostLearningRateSlider, lrLabels);

        // ── subsample ──────────────────────────────────────────────
        VBox ssBox = new VBox(6);
        HBox ssHeader = new HBox(8);
        ssHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label ssLabel = new Label("Submuestreo por árbol (subsample)");
        ssLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Region ssSpacer = new Region();
        HBox.setHgrow(ssSpacer, Priority.ALWAYS);
        Label ssValueLbl = new Label("0.80");
        ssValueLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2ecc71;");
        ssHeader.getChildren().addAll(ssLabel, ssSpacer, ssValueLbl);

        xgboostSubsampleSlider = new Slider(0.50, 1.00, 0.80);
        xgboostSubsampleSlider.setBlockIncrement(0.05);
        xgboostSubsampleSlider.valueProperty().addListener((obs, old, nv) ->
            ssValueLbl.setText(String.format("%.2f", nv.doubleValue()))
        );

        HBox ssLabels = new HBox();
        Label ssMin = new Label("0.50");
        ssMin.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        Region ssSp2 = new Region();
        HBox.setHgrow(ssSp2, Priority.ALWAYS);
        Label ssMax = new Label("1.00");
        ssMax.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        ssLabels.getChildren().addAll(ssMin, ssSp2, ssMax);
        ssBox.getChildren().addAll(ssHeader, xgboostSubsampleSlider, ssLabels);

        card.getChildren().addAll(title, note, new Separator(),
                nEstBox, new Separator(), depthBox, new Separator(),
                lrBox, new Separator(), ssBox);
        return card;
    }

    /**
     * Card de hiperparámetros para Prophet.
     */
    private VBox createProphetParamsCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("param-card");

        Label title = new Label("⚙️ Parámetros Prophet");
        title.getStyleClass().add("param-card-title");

        Label note = new Label("Configura la flexibilidad de la tendencia y las estacionalidades");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6; -fx-font-style: italic;");
        note.setWrapText(true);

        // ── changepoint_prior_scale ────────────────────────────────
        VBox cpBox = new VBox(6);
        HBox cpHeader = new HBox(8);
        cpHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label cpLabel = new Label("Flexibilidad de tendencia (changepoint_prior_scale)");
        cpLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        cpLabel.setWrapText(true);
        Region cpSpacer = new Region();
        HBox.setHgrow(cpSpacer, Priority.ALWAYS);
        Label cpValueLbl = new Label("0.05");
        cpValueLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #9b59b6;");
        cpHeader.getChildren().addAll(cpLabel, cpSpacer, cpValueLbl);

        prophetChangepointSlider = new Slider(0.01, 0.50, 0.05);
        prophetChangepointSlider.setBlockIncrement(0.01);
        prophetChangepointSlider.valueProperty().addListener((obs, old, nv) ->
            cpValueLbl.setText(String.format("%.2f", nv.doubleValue()))
        );

        HBox cpLabels = new HBox();
        Label cpMin = new Label("0.01 (tendencia rígida)");
        cpMin.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        Region cpSp2 = new Region();
        HBox.setHgrow(cpSp2, Priority.ALWAYS);
        Label cpMax = new Label("0.50 (muy flexible)");
        cpMax.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        cpLabels.getChildren().addAll(cpMin, cpSp2, cpMax);

        Label cpHint = new Label("Valores bajos = tendencia suave; valores altos = tendencia reactiva a cambios bruscos.");
        cpHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        cpHint.setWrapText(true);

        cpBox.getChildren().addAll(cpHeader, prophetChangepointSlider, cpLabels, cpHint);

        // ── Estacionalidades ──────────────────────────────────────
        VBox seasonBox = new VBox(8);
        Label seasonLabel = new Label("Estacionalidades");
        seasonLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        prophetYearlySeasonalityCheck = new CheckBox("Estacionalidad anual (patrón mensual del año)");
        prophetYearlySeasonalityCheck.setSelected(true);
        prophetYearlySeasonalityCheck.setStyle("-fx-font-size: 12px; -fx-text-fill: #2c3e50;");

        prophetWeeklySeasonalityCheck = new CheckBox("Estacionalidad semanal (patrón día de la semana)");
        prophetWeeklySeasonalityCheck.setSelected(true);
        prophetWeeklySeasonalityCheck.setStyle("-fx-font-size: 12px; -fx-text-fill: #2c3e50;");

        Label seasonHint = new Label("Activa las que correspondan a tu negocio. Desactívalas si los datos no tienen ese patrón.");
        seasonHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        seasonHint.setWrapText(true);

        seasonBox.getChildren().addAll(seasonLabel,
                prophetYearlySeasonalityCheck,
                prophetWeeklySeasonalityCheck,
                seasonHint);

        card.getChildren().addAll(title, note, new Separator(), cpBox, new Separator(), seasonBox);
        return card;
    }

    /**
     * Card de hiperparámetros para el Pack Ventas + Compras.
     * Permite configurar regularización y alpha para cada submodelo.
     */
    private VBox createPackParamsCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("param-card");

        Label title = new Label("⚙️ Parámetros del Modelo Ventas + Compras");
        title.getStyleClass().add("param-card-title");

        Label note = new Label("Configura la regularización para cada submodelo");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6; -fx-font-style: italic;");
        note.setWrapText(true);

        // ── Submodelo de VENTAS ─────────────────────────────────────────────
        Label ventasHeader = new Label("Modelo de Ventas");
        ventasHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2980b9;");

        Label ventasNote = new Label("Target: ventas diarias. Variable exógena: compras históricas (fijo).");
        ventasNote.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        ventasNote.setWrapText(true);

        VBox ventasRegBox = new VBox(6);
        Label ventasRegLabel = new Label("Regularización (Ventas)");
        ventasRegLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        packVentasRegularizationCombo = new ComboBox<>();
        packVentasRegularizationCombo.getItems().addAll("Lasso (L1)", "Ridge (L2)", "ElasticNet (L1+L2)");
        packVentasRegularizationCombo.setValue("Lasso (L1)");
        packVentasRegularizationCombo.setMaxWidth(Double.MAX_VALUE);
        packVentasRegularizationCombo.setStyle("-fx-font-size: 12px;");
        ventasRegBox.getChildren().addAll(ventasRegLabel, packVentasRegularizationCombo);

        VBox ventasAlphaBox = new VBox(6);
        HBox ventasAlphaHeader = new HBox(8);
        ventasAlphaHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label ventasAlphaLbl = new Label("Alpha (Ventas)");
        ventasAlphaLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Region ventasSpacer = new Region();
        HBox.setHgrow(ventasSpacer, Priority.ALWAYS);
        packVentasAlphaValueLabel = new Label("1.00");
        packVentasAlphaValueLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #3498db;");
        ventasAlphaHeader.getChildren().addAll(ventasAlphaLbl, ventasSpacer, packVentasAlphaValueLabel);

        packVentasAlphaSlider = new Slider(0.01, 10.0, 1.0);
        packVentasAlphaSlider.setBlockIncrement(0.1);
        packVentasAlphaSlider.valueProperty().addListener((obs, old, nv) ->
                packVentasAlphaValueLabel.setText(String.format("%.2f", nv.doubleValue()))
        );
        ventasAlphaBox.getChildren().addAll(ventasAlphaHeader, packVentasAlphaSlider);

        // ── Submodelo de COMPRAS ────────────────────────────────────────────
        Separator midSep = new Separator();
        midSep.setStyle("-fx-padding: 6 0;");

        Label comprasHeader = new Label("Modelo de Compras");
        comprasHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");

        Label comprasNote = new Label("Target: compras diarias. Variable exógena: ventas históricas (fijo).");
        comprasNote.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d;");
        comprasNote.setWrapText(true);

        VBox comprasRegBox = new VBox(6);
        Label comprasRegLabel = new Label("Regularización (Compras)");
        comprasRegLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        packComprasRegularizationCombo = new ComboBox<>();
        packComprasRegularizationCombo.getItems().addAll("Lasso (L1)", "Ridge (L2)", "ElasticNet (L1+L2)");
        packComprasRegularizationCombo.setValue("Lasso (L1)");
        packComprasRegularizationCombo.setMaxWidth(Double.MAX_VALUE);
        packComprasRegularizationCombo.setStyle("-fx-font-size: 12px;");
        comprasRegBox.getChildren().addAll(comprasRegLabel, packComprasRegularizationCombo);

        VBox comprasAlphaBox = new VBox(6);
        HBox comprasAlphaHeader = new HBox(8);
        comprasAlphaHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label comprasAlphaLbl = new Label("Alpha (Compras)");
        comprasAlphaLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Region comprasSpacer = new Region();
        HBox.setHgrow(comprasSpacer, Priority.ALWAYS);
        packComprasAlphaValueLabel = new Label("1.00");
        packComprasAlphaValueLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
        comprasAlphaHeader.getChildren().addAll(comprasAlphaLbl, comprasSpacer, packComprasAlphaValueLabel);

        packComprasAlphaSlider = new Slider(0.01, 10.0, 1.0);
        packComprasAlphaSlider.setBlockIncrement(0.1);
        packComprasAlphaSlider.valueProperty().addListener((obs, old, nv) ->
                packComprasAlphaValueLabel.setText(String.format("%.2f", nv.doubleValue()))
        );
        comprasAlphaBox.getChildren().addAll(comprasAlphaHeader, packComprasAlphaSlider);

        card.getChildren().addAll(title, note, new Separator(),
                ventasHeader, ventasNote, ventasRegBox, ventasAlphaBox,
                midSep,
                comprasHeader, comprasNote, comprasRegBox, comprasAlphaBox);
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
            case "Regresión Múltiple" -> List.of(
                    "Ventas Históricas", "Datos de Compras", "Lags (1/7/14/30d)",
                    "Tendencia Temporal", "Estacionalidad", "Día de la Semana"
            );
            case "Modelo Ensemble" -> List.of(
                    "Ventas Históricas", "Lags Temporales", "Tendencia",
                    "Datos de Compras", "Estacionalidad", "Patrones No-Lineales"
            );
            case "XGBoost" -> List.of(
                    "Ventas diarias", "Lags (1d,7d,14d,30d)", "Medias móviles", "Tendencia"
            );
            case "Prophet" -> List.of(
                    "Ventas diarias", "Estacionalidad anual", "Estacionalidad semanal", "Tendencia"
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

        Label title = new Label("Vista Previa de Datos — Ventas y Compras");
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
        loadPreviewFromApi();
    }

    /**
     * Carga la vista previa de datos desde la API con datos reales.
     * Para el modelo Pack, carga ventas Y compras en columnas separadas.
     */
    private void loadPreviewFromApi() {
        if (previewTable == null) return;
        if (phase2Config == null || phase2Config.getStartDate() == null
                || phase2Config.getEndDate() == null) return;

        String fechaInicio = phase2Config.getStartDate().toString();
        String fechaFin    = phase2Config.getEndDate().toString();

        previewTable.setPlaceholder(new Label("Cargando datos..."));
        previewTable.getItems().clear();
        previewTable.getColumns().clear();

        {
            // Siempre cargar ventas y compras en paralelo y combinar por fecha
            java.util.concurrent.CompletableFuture<Map<String, Object>> ventasFuture =
                    predictionService.getSalesData(fechaInicio, fechaFin, "M");
            java.util.concurrent.CompletableFuture<Map<String, Object>> comprasFuture =
                    predictionService.getPurchasesData(fechaInicio, fechaFin, "M");

            ventasFuture.thenCombine(comprasFuture, (ventasResult, comprasResult) -> {
                return new Map[]{ventasResult, comprasResult};
            }).thenAccept(results -> Platform.runLater(() -> {
                Map<String, Object> ventasResult = results[0];
                Map<String, Object> comprasResult = results[1];

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ventasRows = (ventasResult != null && Boolean.TRUE.equals(ventasResult.get("success")))
                        ? (List<Map<String, Object>>) ventasResult.get("data") : List.of();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> comprasRows = (comprasResult != null && Boolean.TRUE.equals(comprasResult.get("success")))
                        ? (List<Map<String, Object>>) comprasResult.get("data") : List.of();

                if (ventasRows.isEmpty() && comprasRows.isEmpty()) {
                    previewTable.setPlaceholder(new Label("Sin datos en el rango seleccionado."));
                    return;
                }

                // Indexar compras por fecha para combinar
                Map<String, Double> comprasPorFecha = new HashMap<>();
                for (Map<String, Object> row : comprasRows) {
                    String fecha = String.valueOf(row.getOrDefault("fecha", ""));
                    Object total = row.get("total");
                    comprasPorFecha.put(fecha, total instanceof Number ? ((Number) total).doubleValue() : 0.0);
                }

                TableColumn<Map<String, String>, String> fechaCol = new TableColumn<>("Fecha");
                fechaCol.setCellValueFactory(d ->
                        new javafx.beans.property.SimpleStringProperty(d.getValue().get("fecha")));
                fechaCol.setPrefWidth(120);

                TableColumn<Map<String, String>, String> ventasCol = new TableColumn<>("Total Ventas");
                ventasCol.setCellValueFactory(d ->
                        new javafx.beans.property.SimpleStringProperty(d.getValue().get("ventas")));
                ventasCol.setPrefWidth(140);

                TableColumn<Map<String, String>, String> comprasCol = new TableColumn<>("Total Compras");
                comprasCol.setCellValueFactory(d ->
                        new javafx.beans.property.SimpleStringProperty(d.getValue().get("compras")));
                comprasCol.setPrefWidth(140);

                previewTable.getColumns().clear();
                previewTable.getColumns().addAll(fechaCol, ventasCol, comprasCol);

                java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance(
                        new java.util.Locale("es", "MX"));
                List<Map<String, String>> tableData = new ArrayList<>();
                for (Map<String, Object> row : ventasRows) {
                    String fecha = String.valueOf(row.getOrDefault("fecha", ""));
                    Object ventasTotal = row.get("total");
                    Double comprasTotal = comprasPorFecha.getOrDefault(fecha, null);

                    Map<String, String> r = new HashMap<>();
                    r.put("fecha", fecha);
                    r.put("ventas", ventasTotal instanceof Number
                            ? nf.format(((Number) ventasTotal).doubleValue()) : "N/A");
                    r.put("compras", comprasTotal != null
                            ? nf.format(comprasTotal) : "—");
                    tableData.add(r);
                }
                previewTable.getItems().setAll(tableData);
            })).exceptionally(ex -> {
                Platform.runLater(() ->
                        previewTable.setPlaceholder(new Label("Error al cargar datos.")));
                return null;
            });

        }
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

        // Item 2: Sin errores
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
        updateValidationEmojis();
        validPhase2 = phase2Config.isValid();
        updateFooterState();

        // Auto-cargar preview cuando las fechas sean válidas
        if (phase2Config.isHasEnoughData() && previewTable != null) {
            String range = phase2Config.getStartDate() + "_" + phase2Config.getEndDate();
            if (!range.equals(lastPreviewDateRange)) {
                lastPreviewDateRange = range;
                loadPreviewFromApi();
            }
        }
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
        lastPreviewDateRange = null;
        validPhase2 = false;

        // Limpiar controles de Regresión Múltiple
        regularizationCombo = null;
        alphaSlider = null;
        alphaValueLabel = null;
        polynomialCombo = null;
        logTransformCheck = null;
        autoTuneCheck = null;

        // Limpiar controles de Ensemble
        ensembleBaseModelChecks = null;
        ensembleMetaLearnerCombo = null;

        // Limpiar controles de XGBoost
        xgboostNEstimatorsSlider = null;
        xgboostMaxDepthCombo = null;
        xgboostLearningRateSlider = null;
        xgboostSubsampleSlider = null;

        // Limpiar controles de Prophet
        prophetChangepointSlider = null;
        prophetYearlySeasonalityCheck = null;
        prophetWeeklySeasonalityCheck = null;

        // Limpiar controles de Pack
        packVentasRegularizationCombo = null;
        packVentasAlphaSlider = null;
        packVentasAlphaValueLabel = null;
        packComprasRegularizationCombo = null;
        packComprasAlphaSlider = null;
        packComprasAlphaValueLabel = null;

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

                    // ── Todos los modelos usan el flujo Pack (ventas + compras) ─────────
                    PackTrainRequestDTO packRequest = new PackTrainRequestDTO();
                    if (phase2Config.getModelName() != null && !phase2Config.getModelName().isEmpty()) {
                        packRequest.setNombre(phase2Config.getModelName());
                    }
                    packRequest.setFecha_inicio(fechaInicio);
                    packRequest.setFecha_fin(fechaFin);
                    packRequest.setVentas_model_type(modelType);

                    Map<String, Map<String, Object>> hp = new HashMap<>();
                    Map<String, Object> ventasHp = new HashMap<>();

                    // Hiperparámetros específicos por modelo de ventas
                    if ("Regresión Múltiple".equals(selectedModel.title())) {
                        if (regularizationCombo != null) {
                            String regVal = switch (regularizationCombo.getValue()) {
                                case "Lasso (L1)" -> "lasso";
                                case "ElasticNet (L1+L2)" -> "elasticnet";
                                case "Ninguna" -> "none";
                                default -> "ridge";
                            };
                            ventasHp.put("regularization", regVal);
                        }
                        if (alphaSlider != null) {
                            ventasHp.put("alpha", alphaSlider.getValue());
                        }
                        if (polynomialCombo != null) {
                            int degree = polynomialCombo.getValue().contains("2") ? 2 : 1;
                            ventasHp.put("polynomial_degree", degree);
                        }
                        ventasHp.put("log_transform", logTransformCheck != null && logTransformCheck.isSelected());
                        ventasHp.put("auto_tune", autoTuneCheck == null || autoTuneCheck.isSelected());
                    } else if ("Modelo Ensemble".equals(selectedModel.title())) {
                        if (ensembleBaseModelChecks != null) {
                            List<String> baseModels = new ArrayList<>();
                            for (CheckBox cb : ensembleBaseModelChecks) {
                                if (cb.isSelected()) {
                                    String apiName = switch (cb.getText()) {
                                        case "Regresión Lineal" -> "linear";
                                        case "Regresión Múltiple" -> "multiple_regression";
                                        case "Random Forest" -> "random_forest";
                                        default -> cb.getText();
                                    };
                                    baseModels.add(apiName);
                                }
                            }
                            if (!baseModels.isEmpty()) {
                                ventasHp.put("base_models", baseModels);
                            }
                        }
                        if (ensembleMetaLearnerCombo != null) {
                            String mlVal = ensembleMetaLearnerCombo.getValue() != null
                                    && ensembleMetaLearnerCombo.getValue().contains("Promedio")
                                    ? "weighted_avg" : "ridge";
                            ventasHp.put("meta_learner", mlVal);
                        }
                    } else if ("XGBoost".equals(selectedModel.title()) && xgboostNEstimatorsSlider != null) {
                        ventasHp.put("n_estimators", (int) xgboostNEstimatorsSlider.getValue());
                        ventasHp.put("max_depth", Integer.parseInt(xgboostMaxDepthCombo.getValue()));
                        ventasHp.put("learning_rate",
                                Math.round(xgboostLearningRateSlider.getValue() * 1000.0) / 1000.0);
                        ventasHp.put("subsample",
                                Math.round(xgboostSubsampleSlider.getValue() * 100.0) / 100.0);
                    } else if ("Prophet".equals(selectedModel.title()) && prophetChangepointSlider != null) {
                        ventasHp.put("changepoint_prior_scale",
                                Math.round(prophetChangepointSlider.getValue() * 1000.0) / 1000.0);
                        ventasHp.put("yearly_seasonality", prophetYearlySeasonalityCheck.isSelected());
                        ventasHp.put("weekly_seasonality", prophetWeeklySeasonalityCheck.isSelected());
                    }

                    ventasHp.put("use_compras", true);
                    hp.put("ventas", ventasHp);

                    Map<String, Object> comprasHp = new HashMap<>();
                    comprasHp.put("use_ventas", true);
                    comprasHp.put("auto_tune", true);
                    hp.put("compras", comprasHp);

                    packRequest.setHyperparameters(hp);

                    predictionService.trainPack(packRequest)
                            .thenAccept(packResponse -> Platform.runLater(() -> {
                                stopLoadingAnimation();
                                if (packResponse != null && packResponse.isSuccess()) {
                                    lastPackTrainResponse = packResponse;
                                    lastTrainResponse = null;
                                    showPackTrainingResultFromApi(packResponse);
                                } else {
                                    String errorMsg = packResponse != null && packResponse.getError() != null
                                            ? packResponse.getError()
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

        // Recargar lista de modelos del usuario para reflejar el nuevo modelo
        loadUserModels();
    }

    /**
     * Muestra resultado de entrenamiento de pack con métricas de ventas y compras.
     */
    private void showPackTrainingResultFromApi(PackTrainResponseDTO response) {
        trainingIcon.setRotate(0);
        trainingIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/check.png"))
        ));

        String ventasR2 = response.getVentas() != null
                ? String.format("%.4f", response.getVentas().getR2Score()) : "N/A";
        String comprasR2 = response.getCompras() != null
                ? String.format("%.4f", response.getCompras().getR2Score()) : "N/A";

        boolean ventasOk = response.getVentas() != null
                && Boolean.TRUE.equals(response.getVentas().getMeets_r2_threshold());
        boolean comprasOk = response.getCompras() != null
                && Boolean.TRUE.equals(response.getCompras().getMeets_r2_threshold());

        lblTrainingTime.setText("Ventas + Compras: 2 modelos");
        lblMetric.setText("Ventas R²=" + ventasR2 + " | Compras R²=" + comprasR2);
        lblResult.setText(ventasOk && comprasOk ? "Aprobado" : ventasOk || comprasOk ? "Parcial" : "Bajo umbral");

        loadMetricIcons();
        trainingMetrics.setVisible(true);
        trainingMetrics.setManaged(true);

        trainingTitleTxt.setText("Modelos entrenados correctamente. Clave: " + response.getPack_key());
        validPhase3 = true;
        updateFooterState();

        loadUserModels();
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
        if (lastPackTrainResponse != null) {
            loadPackResultsKpisFromApi();
            loadPackTrainingSummaryFromApi();
            loadPackModelInfoFromApi();
            Label loadingLabel = new Label("Generando gráficas...");
            loadingLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic; -fx-padding: 20;");
            resultsChartsContainer.getChildren().setAll(loadingLabel);
            requestPackForecastForCharts();
        } else if (lastTrainResponse != null) {
            loadResultsKpisFromApi();
            loadTrainingSummaryFromApi();
            loadModelInfoFromApi();
            // Mostrar placeholder mientras llega el forecast
            Label loadingLabel = new Label("Generando gráficas con datos reales...");
            loadingLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic; -fx-padding: 20;");
            resultsChartsContainer.getChildren().setAll(loadingLabel);
            requestForecastForCharts();
        } else {
            loadResultsKpis();
            loadTrainingSummary();
            loadModelInfo();
            loadResultsCharts();  // fallback con mock solo cuando no hay datos reales
        }
        predictiveFooter.setVisible(false);
        predictiveFooter.setManaged(false);
    }

    /** Carga KPIs del pack (ventas + compras). */
    private void loadPackResultsKpisFromApi() {
        resultsKpiContainer.getChildren().clear();
        PackTrainResponseDTO.SubModelResult ventas = lastPackTrainResponse.getVentas();
        PackTrainResponseDTO.SubModelResult compras = lastPackTrainResponse.getCompras();

        String ventasR2 = ventas != null ? String.format("%.1f%%", ventas.getR2Score() * 100) : "N/A";
        String ventasRmse = ventas != null ? String.format("$%.2f", ventas.getRmse()) : "N/A";
        String comprasR2 = compras != null ? String.format("%.1f%%", compras.getR2Score() * 100) : "N/A";
        String comprasRmse = compras != null ? String.format("$%.2f", compras.getRmse()) : "N/A";

        boolean ventasOk = ventas != null && Boolean.TRUE.equals(ventas.getMeets_r2_threshold());
        boolean comprasOk = compras != null && Boolean.TRUE.equals(compras.getMeets_r2_threshold());

        List<ResultKpiDTO> kpis = List.of(
                new ResultKpiDTO("🛍", ventasR2, "Precisión Ventas (R²)",
                        ventasOk ? "Supera umbral 0.7" : "Bajo umbral 0.7",
                        ventasOk ? "kpi-green" : "kpi-red",
                        ventasOk ? ResultKpiDTO.TrendType.POSITIVE : ResultKpiDTO.TrendType.NEGATIVE),
                new ResultKpiDTO("🛒", comprasR2, "Precisión Compras (R²)",
                        comprasOk ? "Supera umbral 0.7" : "Bajo umbral 0.7",
                        comprasOk ? "kpi-green" : "kpi-red",
                        comprasOk ? ResultKpiDTO.TrendType.POSITIVE : ResultKpiDTO.TrendType.NEGATIVE),
                new ResultKpiDTO("📉", ventasRmse, "RMSE Ventas",
                        "Error cuadrático modelo ventas", "kpi-blue", ResultKpiDTO.TrendType.NEUTRAL),
                new ResultKpiDTO("📊", comprasRmse, "RMSE Compras",
                        "Error cuadrático modelo compras", "kpi-purple", ResultKpiDTO.TrendType.NEUTRAL)
        );

        for (ResultKpiDTO kpi : kpis) {
            resultsKpiContainer.getChildren().add(ResultKpiCard.createKpiCard(kpi));
        }
    }

    /** Carga resumen del entrenamiento del pack. */
    private void loadPackTrainingSummaryFromApi() {
        trainingSummaryContainer.getChildren().clear();
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("Clave", lastPackTrainResponse.getPack_key() != null
                ? lastPackTrainResponse.getPack_key() : "N/A");
        PackTrainResponseDTO.SubModelResult ventas = lastPackTrainResponse.getVentas();
        PackTrainResponseDTO.SubModelResult compras = lastPackTrainResponse.getCompras();
        if (ventas != null) {
            summary.put("Clave Ventas", ventas.getModel_key() != null ? ventas.getModel_key() : "N/A");
            summary.put("R² Ventas", String.format("%.4f", ventas.getR2Score()));
            summary.put("RMSE Ventas", String.format("$%,.2f", ventas.getRmse()));
        }
        if (compras != null) {
            summary.put("Clave Compras", compras.getModel_key() != null ? compras.getModel_key() : "N/A");
            summary.put("R² Compras", String.format("%.4f", compras.getR2Score()));
            summary.put("RMSE Compras", String.format("$%,.2f", compras.getRmse()));
        }
        for (Map.Entry<String, String> entry : summary.entrySet()) {
            trainingSummaryContainer.getChildren().add(createSummaryItem(entry.getKey(), entry.getValue()));
        }
    }

    /** Carga información del pack entrenado. */
    private void loadPackModelInfoFromApi() {
        modelInfoContainer.getChildren().clear();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("Tipo", "Modelo Ventas + Compras");
        info.put("Clave", lastPackTrainResponse.getPack_key() != null
                ? lastPackTrainResponse.getPack_key() : "N/A");
        info.put("Entrenado", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        PackTrainResponseDTO.SubModelResult ventas = lastPackTrainResponse.getVentas();
        PackTrainResponseDTO.SubModelResult compras = lastPackTrainResponse.getCompras();
        boolean ventasOk = ventas != null && Boolean.TRUE.equals(ventas.getMeets_r2_threshold());
        boolean comprasOk = compras != null && Boolean.TRUE.equals(compras.getMeets_r2_threshold());
        info.put("Estado Ventas", ventasOk ? "Activo" : "Bajo rendimiento");
        info.put("Estado Compras", comprasOk ? "Activo" : "Bajo rendimiento");
        for (Map.Entry<String, String> entry : info.entrySet()) {
            modelInfoContainer.getChildren().add(createSummaryItem(entry.getKey(), entry.getValue()));
        }
    }

    /** Solicita forecast del pack (ventas + compras) para los charts. */
    private void requestPackForecastForCharts() {
        String packKey = lastPackTrainResponse.getPack_key();
        if (packKey == null) return;

        int periods = phase2Config != null ? phase2Config.getPredictionHorizon() * 30 : 30;
        periods = Math.min(periods, 180);

        final int finalPeriods = periods;
        predictionService.forecastPack(packKey, finalPeriods)
                .thenAccept(response -> Platform.runLater(() -> {
                    if (response != null && response.isSuccess()) {
                        lastPackForecastResponse = response;
                        resultsChartsContainer.getChildren().clear();
                        loadPackForecastCharts();
                    }
                }))
                .exceptionally(ex -> {
                    System.out.println("Forecast pack no disponible: " + ex.getMessage());
                    return null;
                });
    }

    /** Carga dos gráficas: forecast de ventas y de compras del pack. */
    private void loadPackForecastCharts() {
        if (lastPackForecastResponse == null) return;

        // Chart 1: Predicción de Ventas del pack
        PackForecastResponseDTO.ForecastSeries ventasSeries = lastPackForecastResponse.getVentas();
        if (ventasSeries != null && !ventasSeries.getDates().isEmpty()) {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/fxml/components/charts/LineChartCard.fxml"));
                VBox chartNode = loader.load();
                LineChartCardController controller = loader.getController();
                controller.setTitle("Predicción de Ventas (Modelo)");
                controller.setSubtitle("Forecast ventas — " + lastPackForecastResponse.getPeriods() + " períodos");

                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("Ventas predichas");
                List<String> dates = ventasSeries.getDates();
                List<Double> values = ventasSeries.getValues();
                int step = Math.max(1, dates.size() / 30);
                for (int i = 0; i < dates.size(); i += step) {
                    String lbl = dates.get(i).length() > 10 ? dates.get(i).substring(5, 10) : dates.get(i);
                    series.getData().add(new XYChart.Data<>(lbl, values.get(i)));
                }
                controller.loadCustomData(series);
                resultsChartsContainer.getChildren().add(chartNode);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Chart 2: Predicción de Compras del pack
        PackForecastResponseDTO.ForecastSeries comprasSeries = lastPackForecastResponse.getCompras();
        if (comprasSeries != null && !comprasSeries.getDates().isEmpty()) {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/fxml/components/charts/LineChartCard.fxml"));
                VBox chartNode = loader.load();
                LineChartCardController controller = loader.getController();
                controller.setTitle("Predicción de Compras (Modelo)");
                controller.setSubtitle("Forecast compras — " + lastPackForecastResponse.getPeriods() + " períodos");

                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("Compras predichas");
                List<String> dates = comprasSeries.getDates();
                List<Double> values = comprasSeries.getValues();
                int step = Math.max(1, dates.size() / 30);
                for (int i = 0; i < dates.size(); i += step) {
                    String lbl = dates.get(i).length() > 10 ? dates.get(i).substring(5, 10) : dates.get(i);
                    series.getData().add(new XYChart.Data<>(lbl, values.get(i)));
                }
                controller.loadCustomData(series);
                resultsChartsContainer.getChildren().add(chartNode);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** Construye un PackTrainResponseDTO mínimo desde un PackInfoDTO guardado (para saltar a fase 4). */
    private PackTrainResponseDTO buildPackResponseFromSavedPack(PackInfoDTO pack) {
        PackTrainResponseDTO r = new PackTrainResponseDTO();
        r.setSuccess(true);
        r.setPack_key(pack.getPack_key());
        r.setPack_id(pack.getPack_id());
        return r;
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
                if (!(entry.getValue() instanceof Number)) continue;
                double val = ((Number) entry.getValue()).doubleValue();
                String key = entry.getKey();
                String formatted;
                switch (key) {
                    case "training_samples", "test_samples" ->
                        formatted = String.format("%,d", (long) val);
                    case "training_time" ->
                        formatted = String.format("%.2f s", val);
                    case "mae", "rmse", "mse" ->
                        formatted = String.format("$%,.2f", val);
                    case "mape" ->
                        formatted = String.format("%.2f%%", val * 100);
                    default ->
                        formatted = String.format("%.4f", val);
                }
                summary.put(key.toUpperCase(), formatted);
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
        // Si viene de modelo guardado, mostrar su nombre
        if (selectedSavedModel != null && selectedSavedModel.getNombre() != null
                && !selectedSavedModel.getNombre().isEmpty()) {
            info.put("Nombre", selectedSavedModel.getNombre());
        }
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

                Set<String> metricasRelevantes = Set.of("r2_score", "mae", "rmse", "mape");
                for (Map.Entry<String, Object> entry : lastTrainResponse.getMetrics().entrySet()) {
                    String key = entry.getKey().toLowerCase();
                    if (metricasRelevantes.contains(key) && entry.getValue() instanceof Number) {
                        metricsSeries.getData().add(
                                new XYChart.Data<>(key.toUpperCase(), ((Number) entry.getValue()).doubleValue())
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

        // Chart 4: Importancia de Features (Regresión Múltiple, Ensemble y XGBoost)
        String selectedTitle = selectedModel != null ? selectedModel.title() : "";
        if (("Regresión Múltiple".equals(selectedTitle) || "Modelo Ensemble".equals(selectedTitle)
                || "XGBoost".equals(selectedTitle))
                && lastTrainResponse != null
                && lastTrainResponse.getMetrics() != null) {
            Object featureImpObj = lastTrainResponse.getMetrics().get("feature_importance");
            if (featureImpObj instanceof Map) {
                loadFeatureImportanceChartFromData((Map<?, ?>) featureImpObj);
            }
        }
    }

    /**
     * Carga un BarChart con la importancia de features del modelo de Regresión Múltiple.
     * Solo muestra el top-10 para legibilidad.
     */
    @SuppressWarnings("unchecked")
    private void loadFeatureImportanceChartFromData(Map<?, ?> featureImportance) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/components/charts/BarChartCard.fxml")
            );
            VBox chartNode = loader.load();
            BarChartCardController controller = loader.getController();

            controller.setTitle("Importancia de Variables");
            controller.setSubtitle("Top features por coeficiente absoluto normalizado (%)");

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Importancia (%)");

            // Ordenar por valor descendente y tomar top-10
            featureImportance.entrySet().stream()
                    .filter(e -> e.getValue() instanceof Number)
                    .sorted((a, b) -> Double.compare(
                            ((Number) b.getValue()).doubleValue(),
                            ((Number) a.getValue()).doubleValue()))
                    .limit(10)
                    .forEach(e -> {
                        String featureName = String.valueOf(e.getKey());
                        double importance = ((Number) e.getValue()).doubleValue();
                        series.getData().add(new XYChart.Data<>(featureName, importance));
                    });

            controller.loadCustomData(series);
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
     * Carga chart de predicción vs real; usa datos reales del forecast si están disponibles.
     */
    @SuppressWarnings("unchecked")
    private void loadPredictionChart() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/components/charts/LineChartCard.fxml")
            );
            VBox chartNode = loader.load();
            LineChartCardController controller = loader.getController();

            controller.setTitle("Predicci\u00F3n vs Datos Reales");
            controller.setSubtitle("Comparativa de resultados");

            if (lastForecastResponse != null && lastForecastResponse.getPredictions() != null) {
                List<String> dates  = (List<String>) lastForecastResponse.getPredictions().get("dates");
                List<Number> values = (List<Number>) lastForecastResponse.getPredictions().get("values");
                if (dates != null && values != null) {
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName("Predicci\u00F3n");
                    int step = Math.max(1, dates.size() / 30);
                    for (int i = 0; i < dates.size(); i += step) {
                        String label = dates.get(i).length() > 10
                                ? dates.get(i).substring(5, 10) : dates.get(i);
                        series.getData().add(new XYChart.Data<>(label, values.get(i)));
                    }
                    controller.loadCustomData(series);
                } else {
                    controller.loadData();
                }
            } else {
                controller.loadData();
            }

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
        if (lastTrainResponse == null) {
            WarningService.show("Entrena un modelo primero para generar el reporte.");
            return;
        }
        List<Map<String, Object>> rows = buildPredictionRows();
        if (rows.isEmpty()) {
            WarningService.show("No hay datos de predicci\u00F3n disponibles. Genera un forecast primero.");
            return;
        }
        ChoiceDialog<String> fmt = new ChoiceDialog<>("PDF", "PDF", "Excel");
        fmt.setTitle("Formato de exportaci\u00F3n");
        fmt.setHeaderText("Reporte de predicci\u00F3n \u2014 " + lastTrainResponse.getModelType());
        fmt.setContentText("Selecciona el formato:");
        Optional<String> result = fmt.showAndWait();
        if (result.isEmpty()) return;
        String format = result.get().equals("Excel") ? "Excel" : "PDF";
        String dateRange = rows.get(0).get("fecha") + " \u2192 " + rows.get(rows.size() - 1).get("fecha");
        try {
            ReportGeneratorService.GeneratedFile file =
                    reportGenerator.generate(format,
                            "Prediccion_" + lastTrainResponse.getModelType(),
                            "predicciones", dateRange, rows);
            WarningService.show("Reporte guardado en: " + file.path().getFileName());
        } catch (Exception e) {
            WarningService.show("Error al generar reporte: " + e.getMessage());
        }
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
        List<Map<String, Object>> rows = buildPredictionRows();
        if (rows.isEmpty()) {
            WarningService.show("No hay datos de predicci\u00F3n para exportar.");
            return;
        }
        ChoiceDialog<String> fmt = new ChoiceDialog<>("Excel", "Excel", "PDF");
        fmt.setTitle("Exportar datos");
        fmt.setHeaderText("Exportar datos de predicci\u00F3n");
        fmt.setContentText("Selecciona el formato:");
        Optional<String> result = fmt.showAndWait();
        if (result.isEmpty()) return;
        String format = result.get().equals("PDF") ? "PDF" : "Excel";
        String dateRange = rows.get(0).get("fecha") + " \u2192 " + rows.get(rows.size() - 1).get("fecha");
        try {
            ReportGeneratorService.GeneratedFile file =
                    reportGenerator.generate(format, "Datos_Prediccion",
                            "predicciones", dateRange, rows);
            WarningService.show("Datos exportados: " + file.path().getFileName());
        } catch (Exception e) {
            WarningService.show("Error al exportar: " + e.getMessage());
        }
    }

    /** Convierte lastForecastResponse a filas para el generador de reportes. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildPredictionRows() {
        if (lastForecastResponse == null || lastForecastResponse.getPredictions() == null)
            return List.of();
        List<String> dates   = (List<String>) lastForecastResponse.getPredictions().get("dates");
        List<Number> values  = (List<Number>) lastForecastResponse.getPredictions().get("values");
        List<Number> lowerCi = (List<Number>) lastForecastResponse.getPredictions().get("lower_ci");
        List<Number> upperCi = (List<Number>) lastForecastResponse.getPredictions().get("upper_ci");
        if (dates == null || values == null) return List.of();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fecha",       dates.get(i));
            row.put("prediccion",  values.get(i) != null
                    ? String.format("%.2f", values.get(i).doubleValue()) : "\u2014");
            row.put("ic_inferior", lowerCi != null && i < lowerCi.size() && lowerCi.get(i) != null
                    ? String.format("%.2f", lowerCi.get(i).doubleValue()) : "\u2014");
            row.put("ic_superior", upperCi != null && i < upperCi.size() && upperCi.get(i) != null
                    ? String.format("%.2f", upperCi.get(i).doubleValue()) : "\u2014");
            rows.add(row);
        }
        return rows;
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

