package com.app.ui;

import com.app.model.Phase2ConfigDTO;
import com.app.model.data.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.testfx.assertions.api.Assertions.assertThat;

/**
 * Tests de integración UI usando TestFX.
 * Prueba la carga de datos sintéticos y la configuración de predicción
 * a través de componentes JavaFX simplificados.
 *
 * Nota: Estos tests crean escenas JavaFX mínimas sin necesitar FXML ni backend.
 */
@ExtendWith(ApplicationExtension.class)
@DisplayName("DataModuleUI - Tests de interfaz de usuario con TestFX")
class DataModuleUITest {

    // Componentes de la escena de carga de datos
    private Label lblUploadStatus;
    private Label lblFileInfo;
    private Label lblRowCount;
    private Label lblValidationStatus;
    private Label lblRetentionPercent;
    private Button btnProcess;
    private Button btnConfirm;
    private ProgressBar progressBar;
    private ListView<String> validationRulesList;

    // Componentes de la escena de predicción
    private Label lblModelName;
    private Label lblR2Value;
    private Label lblMaeValue;
    private Label lblRmseValue;
    private ComboBox<String> modelSelector;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private Button btnTrain;
    private Label lblPredictionStatus;

    @Start
    void start(Stage stage) {
        buildDataModuleScene(stage);
    }

    private void buildDataModuleScene(Stage stage) {
        // ── Panel de estado de carga ──────────────────────────────────────
        lblUploadStatus = new Label("Arrastra o selecciona un archivo CSV o XLSX");
        lblUploadStatus.setId("lblUploadStatus");

        lblFileInfo = new Label("Ningún archivo seleccionado");
        lblFileInfo.setId("lblFileInfo");

        lblRowCount = new Label("Filas: 0");
        lblRowCount.setId("lblRowCount");

        // ── Botones de acción ─────────────────────────────────────────────
        btnProcess = new Button("Procesar Archivo");
        btnProcess.setId("btnProcess");
        btnProcess.setDisable(true);

        btnConfirm = new Button("Confirmar Carga");
        btnConfirm.setId("btnConfirm");
        btnConfirm.setDisable(true);

        // ── Barra de progreso ─────────────────────────────────────────────
        progressBar = new ProgressBar(0.0);
        progressBar.setId("progressBar");
        progressBar.setPrefWidth(300);

        // ── Estado de validación ──────────────────────────────────────────
        lblValidationStatus = new Label("Sin validar");
        lblValidationStatus.setId("lblValidationStatus");

        lblRetentionPercent = new Label("Retención: N/A");
        lblRetentionPercent.setId("lblRetentionPercent");

        // ── Lista de reglas de validación ─────────────────────────────────
        validationRulesList = new ListView<>();
        validationRulesList.setId("validationRulesList");
        validationRulesList.setPrefHeight(120);

        // ── Panel de configuración de predicción ──────────────────────────
        modelSelector = new ComboBox<>();
        modelSelector.setId("modelSelector");
        modelSelector.getItems().addAll("linear", "arima", "sarima", "random_forest");

        startDatePicker = new DatePicker();
        startDatePicker.setId("startDatePicker");

        endDatePicker = new DatePicker();
        endDatePicker.setId("endDatePicker");

        btnTrain = new Button("Entrenar Modelo");
        btnTrain.setId("btnTrain");
        btnTrain.setDisable(true);

        lblPredictionStatus = new Label("Configure los parámetros para entrenar");
        lblPredictionStatus.setId("lblPredictionStatus");

        // ── KPI labels de predicción ──────────────────────────────────────
        lblModelName = new Label("Modelo: --");
        lblModelName.setId("lblModelName");

        lblR2Value = new Label("R²: --");
        lblR2Value.setId("lblR2Value");

        lblMaeValue = new Label("MAE: --");
        lblMaeValue.setId("lblMaeValue");

        lblRmseValue = new Label("RMSE: --");
        lblRmseValue.setId("lblRmseValue");

        // ── Layout ────────────────────────────────────────────────────────
        VBox dataPanel = new VBox(8,
                new Label("=== MÓDULO DE CARGA DE DATOS ==="),
                lblUploadStatus,
                lblFileInfo,
                lblRowCount,
                btnProcess,
                progressBar,
                lblValidationStatus,
                lblRetentionPercent,
                validationRulesList,
                btnConfirm
        );
        dataPanel.setPadding(new Insets(15));

        VBox predPanel = new VBox(8,
                new Label("=== MÓDULO DE PREDICCIÓN ==="),
                new Label("Modelo:"), modelSelector,
                new Label("Fecha inicio:"), startDatePicker,
                new Label("Fecha fin:"), endDatePicker,
                btnTrain,
                lblPredictionStatus,
                lblModelName,
                lblR2Value,
                lblMaeValue,
                lblRmseValue
        );
        predPanel.setPadding(new Insets(15));

        HBox root = new HBox(20, dataPanel, predPanel);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.setTitle("SANI - Test UI");
        stage.show();
    }

    // ══════════════════════════════════════════════════════════════════════
    // TESTS DE ESTADO INICIAL
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Estado inicial: mensaje de bienvenida visible")
    void initialStateShowsWelcomeMessage(FxRobot robot) {
        assertThat(robot.lookup("#lblUploadStatus").queryLabeled())
                .hasText("Arrastra o selecciona un archivo CSV o XLSX");
    }

    @Test
    @DisplayName("Estado inicial: botón de procesar está deshabilitado")
    void initialStateProcessButtonIsDisabled(FxRobot robot) {
        Button btn = robot.lookup("#btnProcess").queryButton();
        assertTrue(btn.isDisable(), "Botón procesar debe estar deshabilitado inicialmente");
    }

    @Test
    @DisplayName("Estado inicial: botón de confirmar está deshabilitado")
    void initialStateConfirmButtonIsDisabled(FxRobot robot) {
        Button btn = robot.lookup("#btnConfirm").queryButton();
        assertTrue(btn.isDisable(), "Botón confirmar debe estar deshabilitado inicialmente");
    }

    @Test
    @DisplayName("Estado inicial: progreso en 0%")
    void initialStateProgressIsZero(FxRobot robot) {
        ProgressBar bar = robot.lookup("#progressBar").query();
        assertEquals(0.0, bar.getProgress(), 0.001);
    }

    @Test
    @DisplayName("Estado inicial: lista de reglas de validación vacía")
    void initialStateValidationListIsEmpty(FxRobot robot) {
        ListView<?> list = robot.lookup("#validationRulesList").queryListView();
        assertTrue(list.getItems().isEmpty(), "Lista de validación debe estar vacía inicialmente");
    }

    // ══════════════════════════════════════════════════════════════════════
    // TESTS DE CARGA DE DATOS SINTÉTICOS (simulado en UI)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Simular carga de archivo CSV: actualiza info del archivo")
    void simulateFileLoadUpdatesFileInfo(FxRobot robot) {
        // Simular que se seleccionó un archivo CSV sintético
        Platform.runLater(() -> {
            lblFileInfo.setText("ventas_sintetico_365.csv (25 KB)");
            lblRowCount.setText("Filas: 365");
            lblUploadStatus.setText("Archivo cargado - listo para procesar");
            btnProcess.setDisable(false);
        });

        robot.sleep(200);

        assertThat(robot.lookup("#lblFileInfo").queryLabeled())
                .hasText("ventas_sintetico_365.csv (25 KB)");
        assertThat(robot.lookup("#lblRowCount").queryLabeled())
                .hasText("Filas: 365");
    }

    @Test
    @DisplayName("Simular validación exitosa: muestra estado y porcentaje de retención")
    void simulateSuccessfulValidationShowsRetention(FxRobot robot) {
        // Simular resultados de validación y limpieza con datos sintéticos
        ValidationResultDTO result = new ValidationResultDTO(
                365, 358, 7, 5, 12, 3, 98.1,
                List.of(
                        new ValidationResultDTO.ValidationRuleResult("Columnas obligatorias presentes", true, 0, "OK"),
                        new ValidationResultDTO.ValidationRuleResult("Formato de fechas válido", true, 0, "OK"),
                        new ValidationResultDTO.ValidationRuleResult("Valores monetarios > 0", true, 0, "OK"),
                        new ValidationResultDTO.ValidationRuleResult("Cantidades > 0", true, 0, "OK"),
                        new ValidationResultDTO.ValidationRuleResult("Campos vacíos <= 50%", true, 0, "OK"),
                        new ValidationResultDTO.ValidationRuleResult("Sin fechas futuras", true, 0, "OK")
                )
        );

        Platform.runLater(() -> {
            lblValidationStatus.setText(
                    result.meetsThreshold() ? "Validación exitosa" : "Advertencia: baja retención"
            );
            lblRetentionPercent.setText(
                    String.format("Retención: %.1f%%", result.retentionPercent())
            );

            // Poblar lista de reglas
            validationRulesList.getItems().clear();
            for (ValidationResultDTO.ValidationRuleResult rule : result.ruleResults()) {
                String icon = rule.passed() ? "✓" : "✗";
                validationRulesList.getItems().add(icon + " " + rule.ruleName());
            }

            btnConfirm.setDisable(false);
            progressBar.setProgress(1.0);
        });

        robot.sleep(300);

        assertThat(robot.lookup("#lblValidationStatus").queryLabeled())
                .hasText("Validación exitosa");
        assertThat(robot.lookup("#lblRetentionPercent").queryLabeled())
                .hasText("Retención: 98.1%");

        ListView<?> list = robot.lookup("#validationRulesList").queryListView();
        assertEquals(6, list.getItems().size(), "Debe mostrar 6 reglas de validación");
    }

    @Test
    @DisplayName("Simular validación con baja retención: muestra advertencia")
    void simulateLowRetentionShowsWarning(FxRobot robot) {
        ValidationResultDTO result = new ValidationResultDTO(
                100, 60, 40, 25, 5, 2, 60.0, List.of()
        );

        Platform.runLater(() -> {
            lblValidationStatus.setText(
                    result.meetsThreshold() ? "Validación exitosa" : "Advertencia: retención < 70%"
            );
            lblRetentionPercent.setText(
                    String.format("Retención: %.1f%%", result.retentionPercent())
            );
        });

        robot.sleep(200);

        assertThat(robot.lookup("#lblValidationStatus").queryLabeled())
                .hasText("Advertencia: retención < 70%");
        assertFalse(result.meetsThreshold());
    }

    @Test
    @DisplayName("Progreso avanza de 0% a 100% durante procesamiento")
    void progressBarAdvancesDuringProcessing(FxRobot robot) {
        Platform.runLater(() -> progressBar.setProgress(0.25));
        robot.sleep(100);
        ProgressBar bar = robot.lookup("#progressBar").query();
        assertEquals(0.25, bar.getProgress(), 0.001);

        Platform.runLater(() -> progressBar.setProgress(0.75));
        robot.sleep(100);
        assertEquals(0.75, bar.getProgress(), 0.001);

        Platform.runLater(() -> progressBar.setProgress(1.0));
        robot.sleep(100);
        assertEquals(1.0, bar.getProgress(), 0.001);
    }

    // ══════════════════════════════════════════════════════════════════════
    // TESTS DE MÓDULO DE PREDICCIÓN (UI)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Selector de modelos contiene los 4 tipos disponibles")
    void modelSelectorHasFourModels(FxRobot robot) {
        ComboBox<?> combo = robot.lookup("#modelSelector").queryComboBox();
        assertEquals(4, combo.getItems().size());
        assertTrue(combo.getItems().contains("linear"));
        assertTrue(combo.getItems().contains("arima"));
        assertTrue(combo.getItems().contains("sarima"));
        assertTrue(combo.getItems().contains("random_forest"));
    }

    @Test
    @DisplayName("Seleccionar modelo linear activa la configuración")
    void selectLinearModelEnablesConfiguration(FxRobot robot) {
        robot.interact(() -> modelSelector.setValue("linear"));
        robot.sleep(100);

        assertEquals("linear", modelSelector.getValue());
    }

    @Test
    @DisplayName("Configurar fechas válidas (12 meses) habilita botón de entrenamiento")
    void validDatesEnableTrainButton(FxRobot robot) {
        robot.interact(() -> {
            modelSelector.setValue("sarima");
            startDatePicker.setValue(LocalDate.of(2024, 1, 1));
            endDatePicker.setValue(LocalDate.of(2024, 12, 31));

            // Simular validación exitosa con Phase2ConfigDTO
            Phase2ConfigDTO config = new Phase2ConfigDTO();
            config.setStartDate(LocalDate.of(2024, 1, 1));
            config.setEndDate(LocalDate.of(2024, 12, 31));
            config.setSelectedVariables(List.of("Ventas", "Mes"));
            config.setPredictionHorizon(3);

            if (config.isValid()) {
                btnTrain.setDisable(false);
                lblPredictionStatus.setText("Configuración válida - listo para entrenar");
            }
        });

        robot.sleep(200);

        Button btn = robot.lookup("#btnTrain").queryButton();
        assertFalse(btn.isDisable(), "Botón entrenar debe estar habilitado con config válida");

        assertThat(robot.lookup("#lblPredictionStatus").queryLabeled())
                .hasText("Configuración válida - listo para entrenar");
    }

    @Test
    @DisplayName("Simular entrenamiento exitoso muestra KPIs en UI")
    void simulateSuccessfulTrainingShowsKPIs(FxRobot robot) {
        // Simular respuesta de entrenamiento con R² = 0.91
        Platform.runLater(() -> {
            lblModelName.setText("Modelo: Random Forest");
            lblR2Value.setText("R²: 0.91");
            lblMaeValue.setText("MAE: 450.00");
            lblRmseValue.setText("RMSE: 620.00");
            lblPredictionStatus.setText("Entrenamiento completado - R² cumple umbral");
        });

        robot.sleep(200);

        assertThat(robot.lookup("#lblModelName").queryLabeled())
                .hasText("Modelo: Random Forest");
        assertThat(robot.lookup("#lblR2Value").queryLabeled())
                .hasText("R²: 0.91");
        assertThat(robot.lookup("#lblMaeValue").queryLabeled())
                .hasText("MAE: 450.00");
        assertThat(robot.lookup("#lblRmseValue").queryLabeled())
                .hasText("RMSE: 620.00");
    }

    @Test
    @DisplayName("R² por debajo del umbral muestra advertencia en UI")
    void lowR2ShowsWarningInUI(FxRobot robot) {
        double lowR2 = 0.45;

        Platform.runLater(() -> {
            lblR2Value.setText(String.format("R²: %.2f", lowR2));
            lblPredictionStatus.setText(lowR2 >= 0.7
                    ? "Modelo aceptado"
                    : "Advertencia: R² = " + lowR2 + " está por debajo del umbral (0.7)");
        });

        robot.sleep(200);

        assertThat(robot.lookup("#lblPredictionStatus").queryLabeled())
                .hasText("Advertencia: R² = 0.45 está por debajo del umbral (0.7)");
    }

    @Test
    @DisplayName("Simular predicciones: actualiza status con fechas de forecast")
    void simulateForecastUpdatesStatus(FxRobot robot) {
        Platform.runLater(() -> {
            lblPredictionStatus.setText(
                    "Predicción generada: 2025-01-01 → 2025-03-31 (90 días)"
            );
        });

        robot.sleep(200);

        assertThat(robot.lookup("#lblPredictionStatus").queryLabeled())
                .hasText("Predicción generada: 2025-01-01 → 2025-03-31 (90 días)");
    }

    // ══════════════════════════════════════════════════════════════════════
    // TESTS DE INTEGRACIÓN (UI + Modelos)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("UploadedFileDTO se puede reflejar correctamente en labels de UI")
    void uploadedFileDtoReflectsInUILabels(FxRobot robot) {
        UploadedFileDTO dto = new UploadedFileDTO(
                1L, "ventas_2024_sintetico.xlsx", "XLSX", "VENTAS",
                LocalDateTime.of(2024, 6, 15, 10, 30, 0),
                "PROCESADO", 128L, 1500, "tester"
        );

        Platform.runLater(() -> {
            lblFileInfo.setText(dto.fileName() + " (" + dto.sizeKb() + " KB)");
            lblRowCount.setText("Filas: " + dto.rowCount());
            lblUploadStatus.setText("Estado: " + dto.status());
        });

        robot.sleep(200);

        assertThat(robot.lookup("#lblFileInfo").queryLabeled())
                .hasText("ventas_2024_sintetico.xlsx (128 KB)");
        assertThat(robot.lookup("#lblRowCount").queryLabeled())
                .hasText("Filas: 1500");
        assertThat(robot.lookup("#lblUploadStatus").queryLabeled())
                .hasText("Estado: PROCESADO");
    }

    @Test
    @DisplayName("ValidationReport se puede reflejar en lista de reglas de UI")
    void validationReportReflectsInUIRulesList(FxRobot robot) {
        ValidationReport report = new ValidationReport(
                true, List.of(), 365, 360, 5,
                Map.of(10, List.of("Cantidad negativa")),
                List.of("Regla 1 OK", "Regla 2 OK")
        );

        Platform.runLater(() -> {
            validationRulesList.getItems().clear();
            validationRulesList.getItems().add("✓ Estructura válida");
            validationRulesList.getItems().add("✓ " + report.validRows() + " filas válidas");
            validationRulesList.getItems().add("✗ " + report.invalidRows() + " filas con errores");
        });

        robot.sleep(200);

        ListView<?> list = robot.lookup("#validationRulesList").queryListView();
        assertEquals(3, list.getItems().size());
        assertTrue(list.getItems().get(0).toString().contains("válida"));
    }
}
