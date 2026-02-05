package com.app.ui.predictive;

import com.app.model.PredictiveModelDTO;
import com.app.model.TrainingResult;
import com.app.service.alerts.WarningService;
import javafx.animation.Animation;
import javafx.animation.RotateTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.*;

public class PredictiveController {

    @FXML private FlowPane modelsContainer;
    @FXML private VBox modelDetailPane;
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

    @FXML private VBox parametersContainer;
    @FXML private VBox parametersSummary;


    @FXML private StackPane phase1;
    @FXML private StackPane phase2;
    @FXML private StackPane phase3;
    @FXML private StackPane phase4;

    private List<StackPane> phases;

    private int currentPhaseIndex = 0;
    private PredictiveModelDTO selectedModel;
    private boolean validPhase2 = false;
    private final List<VBox> modelCards = new ArrayList<>();

    private RotateTransition loadingRotation;

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
                        "Modelo ARIMA",
                        "Predicción basada en series temporales.",
                        List.of("Series", "Estadístico", "Temporal"),
                        "Ideal para datos históricos estables.",
                        "📈",
                        "98%",
                        "1-2min"
                ),
                new PredictiveModelDTO(
                        "Modelo LSTM",
                        "Red neuronal recurrente.",
                        List.of("IA", "Deep Learning", "Secuencial"),
                        "Requiere alto volumen de datos.",
                        "📊",
                        "98%",
                        "1-2min"
                ),
                new PredictiveModelDTO(
                        "Modelo XGBoost",
                        "Boosting de alto rendimiento.",
                        List.of("Árboles", "Precisión", "Optimizado"),
                        "Excelente para datos estructurados.",
                        "⚡",
                        "98%",
                        "1-2min"
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

        selectButton.setOnAction(e -> selectModel(model, card, selectButton));

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
        selectedModel = model;

        modelCards.forEach(card -> card.getStyleClass().remove("selected"));
        modelCards.forEach(card ->
                ((Button) card.lookup(".select-button")).setText("Seleccionar")
        );

        selectedCard.getStyleClass().add("selected");
        button.setText("Seleccionado");

        updateDetailPane(model);
        updateFooterState();
    }

    private void updateDetailPane(PredictiveModelDTO model) {
        detailTitle.setText(model.title());
        detailDescription.setText(model.description());
        detailExtra.setText(model.detailMessage());
    }

    private void clearDetailPanel() {
        detailTitle.setText("Selecciona un modelo");
        detailDescription.setText("Elige un modelo predictivo para ver sus detalles.");
        detailExtra.setText("");
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

            if (currentPhaseIndex == 1 && !validPhase2) { // se soliicta la fase 2 y aún no es valida la información seleccionada
                buildPhase2UI();
            }

            if(currentPhaseIndex == 2){
                showPhase3();
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
            case 2 -> true;                  // Fase 3
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

    private void buildPhase2UI() {
        parametersContainer.getChildren().clear();
        parametersSummary.getChildren().clear();

        if (selectedModel == null) return;

        // Ejemplo: campo de fecha
        DatePicker startDate = new DatePicker();
        startDate.valueProperty().addListener((obs, o, n) ->
                addSummary("Fecha inicio", n.toString())
        );

        parametersContainer.getChildren().add(
                createField("Fecha de inicio", startDate)
        );

        // Ejemplo: rango
        Slider range = new Slider(1, 100, 50);
        range.valueProperty().addListener((obs, o, n) ->
                addSummary("Ventana", n.intValue() + " días")
        );

        parametersContainer.getChildren().add(
                createField("Ventana de análisis", range)
        );
    }

    private VBox createField(String label, Control control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");

        VBox box = new VBox(6, l, control);
        box.getStyleClass().add("field-box");

        return box;
    }

    private void addSummary(String key, String value) {
        validPhase2 = true;
        updateFooterState();
        Label summary = new Label(key + ": " + value);
        summary.getStyleClass().add("summary-item");

        parametersSummary.getChildren().removeIf(
                n -> ((Label) n).getText().startsWith(key)
        );

        parametersSummary.getChildren().add(summary);
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

        btnTrain.setDisable(true);
        trainingInfo.setText("Entrenando el modelo, este proceso puede tardar unos minutos...");
        startLoadingAnimation();

        Task<TrainingResult> task = new Task<>() {
            @Override
            protected TrainingResult call() throws Exception {
                // 🔌 llamada real a backend
                Thread.sleep(3000);
                return new TrainingResult(
                        "2m 15s",
                        "Accuracy",
                        true
                );
            }
        };

        task.setOnSucceeded(e -> {
            stopLoadingAnimation();
            showTrainingResult(task.getValue());
        });

        task.setOnFailed(e -> {
            stopLoadingAnimation();
            showTrainingError();
        });

        new Thread(task).start();
    }

    private void showTrainingResult(TrainingResult result) {

        trainingIcon.setRotate(0);
        trainingIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/check.png"))
        ));

        lblTrainingTime.setText(result.time());
        lblMetric.setText(result.metric());
        lblResult.setText("Exitoso");

        trainingMetrics.setVisible(true);
        trainingMetrics.setManaged(true);

        trainingInfo.setText("El modelo fue entrenado correctamente y está listo para su uso.");
    }

    private void showTrainingError() {

        trainingIcon.setRotate(0);
        trainingIcon.setImage(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/fail.png"))
        ));

        lblTrainingTime.setText("-");
        lblMetric.setText("-");
        lblResult.setText("Erróneo");

        trainingMetrics.setVisible(true);
        trainingMetrics.setManaged(true);

        trainingInfo.setText("El modelo fue entrenado incorrectamente y reintentalo más tarde.");
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


}

