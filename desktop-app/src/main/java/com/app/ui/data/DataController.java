package com.app.ui.data;

import com.app.model.data.UploadedFileDTO;
import com.app.model.data.ValidationResultDTO;
import com.app.model.data.ValidationResultDTO.ValidationRuleResult;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;

public class DataController {

    // ── Tabs ──────────────────────────────────────────────────────────────────
    @FXML private Button tabUpload;
    @FXML private Button tabHistory;
    @FXML private Button tabValidation;

    // ── Secciones ─────────────────────────────────────────────────────────────
    @FXML private ScrollPane sectionUpload;
    @FXML private ScrollPane sectionHistory;
    @FXML private ScrollPane sectionValidation;

    // ── Cargar datos — zona de carga ──────────────────────────────────────────
    @FXML private VBox   dropZoneCard;
    @FXML private VBox   stateIdle;
    @FXML private VBox   stateFileReady;
    @FXML private VBox   stateDragOver;
    @FXML private Label  lblDropTitle;
    @FXML private Label  lblFormats;
    @FXML private Label  lblOr;
    @FXML private Button btnSelectFile;
    @FXML private Label  lblMaxSize;
    @FXML private Label  lblFileName;
    @FXML private Label  lblFileSize;
    @FXML private Label  lblFileType;
    @FXML private Button btnProcess;
    @FXML private Button btnRemoveFile;
    @FXML private Label  lblDropHere;

    // ── Cargar datos — recientes y sidebar ────────────────────────────────────
    @FXML private Label  lblRecentTitle;
    @FXML private VBox   recentUploadsList;
    @FXML private VBox   recentEmptyState;
    @FXML private Label  lblRecentEmpty;
    @FXML private Label  lblHowTitle;
    @FXML private VBox   howToStepsList;
    @FXML private Label  lblFormatTitle;
    @FXML private VBox   formatFieldsList;
    @FXML private Label  lblTemplateTitle;
    @FXML private Label  lblTemplateHint;
    @FXML private Button btnDownloadCompras;
    @FXML private Button btnDownloadVentas;

    // ── Históricos — filtros ──────────────────────────────────────────────────
    @FXML private Label             lblFiltersTitle;
    @FXML private Label             lblFilterType;
    @FXML private ComboBox<String>  cmbDataType;
    @FXML private Label             lblFilterFrom;
    @FXML private DatePicker        dpHistoryFrom;
    @FXML private Label             lblFilterTo;
    @FXML private DatePicker        dpHistoryTo;
    @FXML private Label             lblFilterSearch;
    @FXML private TextField         txtHistorySearch;
    @FXML private Button            btnApplyFilters;
    @FXML private Label             lblTableTitle;
    @FXML private Label             lblRecordCount;
    @FXML private Button            btnExport;
    @FXML private TableView<ObservableList<String>> dataTable;
    @FXML private TableColumn<ObservableList<String>, String> colDate;
    @FXML private TableColumn<ObservableList<String>, String> colProduct;
    @FXML private TableColumn<ObservableList<String>, String> colPrice;
    @FXML private TableColumn<ObservableList<String>, String> colQty;
    @FXML private TableColumn<ObservableList<String>, String> colTotal;
    @FXML private TableColumn<ObservableList<String>, String> colType;
    @FXML private Label             lblStatRange;
    @FXML private Label             lblStatTotal;
    @FXML private Label             lblStatAvg;
    @FXML private VBox              historyEmptyState;
    @FXML private Label             lblHistoryEmpty;

    // ── Validación y limpieza ─────────────────────────────────────────────────
    @FXML private VBox        validationNoData;
    @FXML private Label       lblNoDataTitle;
    @FXML private Label       lblNoDataHint;
    @FXML private Button      btnGoUpload;
    @FXML private VBox        validationReady;
    @FXML private Label       lblValidationFileName;
    @FXML private Label       lblValidationFileMeta;
    @FXML private Button      btnStartValidation;
    @FXML private Label       lblValidationCheckTitle;
    @FXML private VBox        validationRulesList;
    @FXML private ProgressBar validationProgress;
    @FXML private Label       lblValidationStatus;
    @FXML private Label       lblCleaningTitle;
    @FXML private VBox        cleaningSummaryList;
    @FXML private VBox        validationResult;
    @FXML private Label       lblResultTitle;
    @FXML private Label       lblMetricTotalVal;
    @FXML private Label       lblMetricTotalLbl;
    @FXML private Label       lblMetricValidVal;
    @FXML private Label       lblMetricValidLbl;
    @FXML private Label       lblMetricRemovedVal;
    @FXML private Label       lblMetricRemovedLbl;
    @FXML private Label       lblMetricRetentionVal;
    @FXML private Label       lblMetricRetentionLbl;
    @FXML private HBox        lowRetentionWarning;
    @FXML private Label       lblLowRetentionMsg;
    @FXML private Button      btnCancelValidation;
    @FXML private Button      btnConfirmValidation;

    // ── Estado interno ────────────────────────────────────────────────────────
    private List<Button>       allTabs;
    private List<ScrollPane>   allSections;
    private int                activeTabIndex = 0;

    // Flags para saber si cada tab ya fue inicializada
    private boolean historyInitialized    = false;
    private boolean validationInitialized = false;


    /** Archivo actualmente seleccionado/arrastrado (null = ninguno) */
    private File currentFile = null;

    private static final long   MAX_SIZE_BYTES = 100L * 1024 * 1024; // 100 MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of("csv", "xlsx");

    private static final DateTimeFormatter FMT_DISPLAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "MX"));

    // ═════════════════════════════════════════════════════════════════════════
    //  INICIALIZACIÓN
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        allTabs     = List.of(tabUpload, tabHistory, tabValidation);
        allSections = List.of(sectionUpload, sectionHistory, sectionValidation);

        setAllTexts();
        setupDropZone();

        Platform.runLater(() -> {
            buildHowToSteps();
            buildFormatFields();
            loadRecentUploads(getMockRecentUploads());
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TEXTOS — sin hardcodear en FXML
    // ─────────────────────────────────────────────────────────────────────────
    private void setAllTexts() {
        tabUpload.setText("Cargar datos");
        tabHistory.setText("Consultar hist\u00F3ricos");
        tabValidation.setText("Validaci\u00F3n y limpieza");

        // Zona de carga
        lblDropTitle.setText("Arrastra tu archivo aqu\u00ED");
        lblFormats.setText("Formatos permitidos: CSV, Excel (.xlsx)");
        lblOr.setText("— o —");
        btnSelectFile.setText("Seleccionar archivo");
        lblMaxSize.setText("Tama\u00F1o m\u00E1ximo: 100 MB");
        btnProcess.setText("Procesar archivo");
        btnRemoveFile.setText("Quitar");
        lblDropHere.setText("Suelta el archivo para cargarlo");

        // Recientes
        lblRecentTitle.setText("Cargas recientes");
        lblRecentEmpty.setText("No hay cargas recientes.");

        // Sidebar
        lblHowTitle.setText("\u00BFC\u00F3mo cargar mis datos?");
        lblFormatTitle.setText("Formato de archivo");
        lblTemplateTitle.setText("Descargar plantillas");
        lblTemplateHint.setText("Archivos con encabezados listos para completar:");
        btnDownloadCompras.setText("Plantilla Compras (.xlsx)");
        btnDownloadVentas.setText("Plantilla Ventas (.xlsx)");

        // Históricos
        lblFiltersTitle.setText("Filtros de b\u00FAsqueda");
        lblFilterType.setText("Tipo de datos");
        lblFilterFrom.setText("Fecha desde");
        lblFilterTo.setText("Fecha hasta");
        lblFilterSearch.setText("Buscar producto");
        btnApplyFilters.setText("Aplicar filtros");
        lblTableTitle.setText("Resultados");
        btnExport.setText("Exportar");
        txtHistorySearch.setPromptText("Nombre de producto o servicio...");
        lblHistoryEmpty.setText("No se encontraron registros con los criterios indicados.\nAjusta los filtros e intenta de nuevo.");

        // Validación
        lblNoDataTitle.setText("Sin datos cargados");
        lblNoDataHint.setText("Primero carga un archivo en la pesta\u00F1a \"Cargar datos\"\npara poder iniciar la validaci\u00F3n.");
        btnGoUpload.setText("Ir a cargar datos");
        lblValidationCheckTitle.setText("Reglas de validaci\u00F3n");
        lblCleaningTitle.setText("Limpieza autom\u00E1tica");
        btnStartValidation.setText("Iniciar validaci\u00F3n");
        btnCancelValidation.setText("Cancelar");
        btnConfirmValidation.setText("Confirmar y guardar");

        // Etiquetas de métricas
        lblMetricTotalLbl.setText("Registros originales");
        lblMetricValidLbl.setText("Registros v\u00E1lidos");
        lblMetricRemovedLbl.setText("Duplicados eliminados");
        lblMetricRetentionLbl.setText("Retenci\u00F3n");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  DRAG & DROP
    // ═════════════════════════════════════════════════════════════════════════

    private void setupDropZone() {
        // Aceptar drag cuando entra al card
        dropZoneCard.setOnDragOver(event -> {
            if (event.getGestureSource() != dropZoneCard
                    && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                showDragOverState(true);
            }
            event.consume();
        });

        // Restaurar estado cuando el drag sale del card sin soltar
        dropZoneCard.setOnDragExited(event -> {
            showDragOverState(false);
            event.consume();
        });

        // Archivo soltado sobre el card
        dropZoneCard.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File dropped = db.getFiles().getFirst();

                // Marcamos el evento como completado ANTES de mostrar cualquier diálogo
                event.setDropCompleted(true);
                event.consume();
                showDragOverState(false);

                // Ahora sí, fuera del evento de drag, mostramos el diálogo si aplica
                if (currentFile != null) {
                    javafx.application.Platform.runLater(() -> {
                        boolean confirmed = showReplaceConfirmation(
                                currentFile.getName(), dropped.getName());
                        if (confirmed) acceptFile(dropped);
                    });
                } else {
                    // Sin archivo previo: aceptar directamente, sin diálogo
                    javafx.application.Platform.runLater(() -> acceptFile(dropped));
                }
                return; // salir antes del consume del final
            }

            showDragOverState(false);
            event.setDropCompleted(success);
            event.consume();
        });

        dropZoneCard.setOnMouseClicked(event -> {
            // Solo disparar si está en estado IDLE o FILE_READY (no durante drag)
            if (stateDragOver.isVisible()) return;
            onSelectFile();
        });
    }

    /**
     * Muestra u oculta el estado visual de "arrastrando sobre el card".
     */
    private void showDragOverState(boolean isDragging) {
        stateDragOver.setVisible(isDragging);
        stateDragOver.setManaged(isDragging);
        stateIdle.setVisible(!isDragging && currentFile == null);
        stateIdle.setManaged(!isDragging && currentFile == null);
        stateFileReady.setVisible(!isDragging && currentFile != null);
        stateFileReady.setManaged(!isDragging && currentFile != null);

        dropZoneCard.getStyleClass().removeAll("upload-card-drag");
        if (isDragging) dropZoneCard.getStyleClass().add("upload-card-drag");
    }

    /**
     * Pide confirmación al usuario antes de reemplazar el archivo actual.
     * @return true si el usuario acepta reemplazar
     */
    private boolean showReplaceConfirmation(String currentName, String newName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reemplazar archivo");
        confirm.setHeaderText("Ya tienes un archivo seleccionado");
        confirm.setContentText(
                "Archivo actual: \"" + currentName + "\"\n" +
                        "Nuevo archivo: \"" + newName + "\"\n\n" +
                        "\u00BFDeseas reemplazar el archivo actual?"
        );
        ButtonType btnYes = new ButtonType("Reemplazar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnNo  = new ButtonType("Cancelar",   ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(btnYes, btnNo);

        return confirm.showAndWait()
                .filter(b -> b == btnYes)
                .isPresent();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SELECCIÓN Y VALIDACIÓN DE ARCHIVO
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onSelectFile() {
        System.out.println("Accion del boton");
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar archivo de datos");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Archivos de datos (CSV, Excel)", "*.csv", "*.xlsx")
        );

        File selected = chooser.showOpenDialog(btnSelectFile.getScene().getWindow());
        if (selected == null) return;

        if (currentFile != null) {
            boolean confirmed = showReplaceConfirmation(currentFile.getName(), selected.getName());
            if (!confirmed) return;
        }

        acceptFile(selected);
    }

    /**
     * Acepta un archivo tras validar formato y tamaño.
     * Si pasa las validaciones, transiciona al estado FILE_READY.
     */
    private void acceptFile(File file) {
        String ext = getExtension(file.getName()).toLowerCase();

        // Validar formato
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            showError("Formato no soportado",
                    "Por favor selecciona un archivo CSV o Excel (.xlsx).");
            return;
        }

        // Validar tamaño
        if (file.length() > MAX_SIZE_BYTES) {
            showError("Archivo demasiado grande",
                    "El archivo excede el tama\u00F1o m\u00E1ximo permitido (100 MB).\n" +
                            "Tama\u00F1o detectado: " + formatSize(file.length()));
            return;
        }

        currentFile = file;

        // Actualizar UI al estado FILE_READY
        lblFileName.setText(file.getName());
        lblFileSize.setText(formatSize(file.length()));
        lblFileType.setText(ext.toUpperCase() + "  ·  Listo para procesar");

        stateIdle.setVisible(false);
        stateIdle.setManaged(false);
        stateFileReady.setVisible(true);
        stateFileReady.setManaged(true);

        // Notificar a la pestaña de validación que hay datos disponibles
        syncValidationTab();
    }

    @FXML
    private void onRemoveFile() {
        currentFile = null;

        stateFileReady.setVisible(false);
        stateFileReady.setManaged(false);
        stateIdle.setVisible(true);
        stateIdle.setManaged(true);

        syncValidationTab();
    }

    /**
     * Inicia el procesamiento del archivo.
     * TODO: reemplazar por dataService.processFile(currentFile)
     *       que ejecute CU-01 (lectura) → CU-02 (validación) → CU-03 (limpieza)
     */
    @FXML
    private void onProcessFile() {
        if (currentFile == null) return;

        btnProcess.setDisable(true);
        btnProcess.setText("Procesando...");

        System.out.printf("[DATA] Procesando archivo: %s (%s)%n",
                currentFile.getName(), formatSize(currentFile.length()));

        // TODO: Task/Service de JavaFX para no bloquear el hilo UI:
        // Task<ValidationResultDTO> task = new ProcessFileTask(currentFile);
        // task.setOnSucceeded(e -> showProcessingResult(task.getValue()));
        // task.setOnFailed(e -> showError(...));
        // new Thread(task).start();

        // Simulación con delay
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(e -> {
            btnProcess.setDisable(false);
            btnProcess.setText("Procesar archivo");
            loadRecentUploads(getMockRecentUploads());

            // Platform.runLater saca el Alert del ciclo de animación
            javafx.application.Platform.runLater(() ->
                    showInfo("Archivo procesado",
                            "Se han cargado los datos del archivo \""
                                    + currentFile.getName() + "\" exitosamente.\n"
                                    + "Puedes continuar en la pestaña de Validación y limpieza.")
            );
        });
        delay.play();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CARGAS RECIENTES
    // ═════════════════════════════════════════════════════════════════════════

    private void loadRecentUploads(List<UploadedFileDTO> uploads) {
        recentUploadsList.getChildren().clear();

        boolean empty = uploads.isEmpty();
        recentEmptyState.setVisible(empty);
        recentEmptyState.setManaged(empty);

        for (UploadedFileDTO upload : uploads) {
            recentUploadsList.getChildren().add(buildRecentRow(upload));
        }
    }

    private HBox buildRecentRow(UploadedFileDTO upload) {
        HBox row = new HBox(0);
        row.getStyleClass().add("recent-row");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        BiFunction<javafx.scene.Node, Double, HBox> fixedCol = (node, width) -> {
            HBox col = new HBox(node);
            col.setMinWidth(width);
            col.setMaxWidth(width);
            col.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            return col;
        };

        // Icono de tipo
        String iconPath = upload.fileType().equals("XLSX")
                ? "/images/reports/format-excel.png"
                : "/images/data/csv-icon.png";
        ImageView icon = loadIcon(iconPath, 28, 28);
        HBox colIcon = fixedCol.apply(icon, 40.0);

        // Nombre del archivo (flexible con elipsis)
        Label lblName = new Label(upload.fileName());
        lblName.getStyleClass().add("recent-file-name");
        lblName.setTextOverrun(OverrunStyle.ELLIPSIS);
        lblName.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblName, javafx.scene.layout.Priority.ALWAYS);

        // Fecha
        Label lblDate = new Label(upload.uploadedAt().format(FMT_DISPLAY));
        lblDate.getStyleClass().add("recent-meta");
        HBox colDate = fixedCol.apply(lblDate, 130.0);

        // Estado con color
        Label lblStatus = new Label(upload.status());
        lblStatus.getStyleClass().add("upload-status-" + upload.status().toLowerCase());
        HBox colStatus = fixedCol.apply(lblStatus, 90.0);

        // Peso
        Label lblSize = new Label(upload.sizeKb() + " KB");
        lblSize.getStyleClass().add("recent-meta");
        HBox colSize = fixedCol.apply(lblSize, 70.0);

        // Botón ver datos
        Button btnView = new Button("Ver datos");
        btnView.getStyleClass().add("btn-view-log");
        btnView.setTooltip(new Tooltip("Ver datos"));
        btnView.setOnAction(e -> onViewUploadedData(upload));

        row.getChildren().addAll(colIcon, lblName, colDate, colStatus, colSize, btnView);
        return row;
    }

    /**
     * Abre la vista de datos cargados para el archivo indicado.
     * TODO: implementar vista previa de datos (CU-01 paso 6: primeras 10 filas)
     */
    private void onViewUploadedData(UploadedFileDTO upload) {
        System.out.printf("[DATA] Ver datos de carga: id=%d, archivo=%s%n",
                upload.id(), upload.fileName());
        // TODO: dataPreviewService.showPreview(upload.id())
        //       o navegar a históricos con filtro por esta carga
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SIDEBAR: PASOS, FORMATO, PLANTILLAS
    // ═════════════════════════════════════════════════════════════════════════

    private void buildHowToSteps() {
        List<String> steps = List.of(
                "Descarga la plantilla correspondiente a tu tipo de datos (ventas o compras).",
                "Completa el archivo con tus registros respetando el formato de cada columna.",
                "Guarda el archivo en formato CSV o Excel (.xlsx) antes de cargarlo.",
                "Arrastra el archivo al \u00E1rea de carga o usa el bot\u00F3n \"Seleccionar archivo\".",
                "Revisa la vista previa y presiona \"Procesar archivo\" para iniciar el an\u00E1lisis."
        );

        List<HBox> rows = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            rows.add(buildStep(i + 1, steps.get(i)));
        }
        howToStepsList.getChildren().clear();
        howToStepsList.getChildren().addAll(rows); // un solo layout pass
    }

    private HBox buildStep(int number, String text) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        // Círculo azul con número
        StackPane bubble = new StackPane();
        bubble.getStyleClass().add("step-bubble");
        Label num = new Label(String.valueOf(number));
        num.getStyleClass().add("step-number");
        bubble.getChildren().add(num);

        Label lbl = new Label(text);
        lbl.getStyleClass().add("step-text");
        lbl.setWrapText(true);
        HBox.setHgrow(lbl, javafx.scene.layout.Priority.ALWAYS);

        row.getChildren().addAll(bubble, lbl);
        return row;
    }

    private void buildFormatFields() {
        List<String> mandatory = List.of(
                "Fecha  (DD/MM/AAAA)",
                "Producto / Servicio",
                "Precio Unitario",
                "Cantidad",
                "Monto Final"
        );
        List<String> optional = List.of(
                "Descuento",
                "Cliente / Proveedor",
                "Canal de Venta",
                "Notas"
        );

        List<javafx.scene.Node> nodes = new ArrayList<>();
        for (String field : mandatory)
            nodes.add(buildFieldRow("\u2705", field, "format-field-mandatory"));

        Label lblOpt = new Label("Opcionales:");
        lblOpt.getStyleClass().add("format-optional-header");
        nodes.add(lblOpt);

        for (String field : optional)
            nodes.add(buildFieldRow("\uD83D\uDCCE", field, "format-field-optional"));

        formatFieldsList.getChildren().clear();
        formatFieldsList.getChildren().addAll(nodes); // un solo layout pass

    }

    private HBox buildFieldRow(String emoji, String text, String styleClass) {
        HBox row = new HBox(8);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label icon = new Label(emoji);
        icon.setMinWidth(20);
        Label lbl = new Label(text);
        lbl.getStyleClass().add(styleClass);
        row.getChildren().addAll(icon, lbl);
        return row;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  DESCARGA DE PLANTILLAS
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onDownloadCompras() {
        downloadTemplate("/templates/plantilla_compras.xlsx", "plantilla_compras.xlsx");
    }

    @FXML
    private void onDownloadVentas() {
        downloadTemplate("/templates/plantilla_ventas.xlsx", "plantilla_ventas.xlsx");
    }

    /**
     * Copia un archivo de plantilla desde resources al destino elegido por el usuario.
     *
     * @param resourcePath Ruta dentro del classpath (resources/)
     * @param defaultName  Nombre sugerido en el FileChooser
     */
    private void downloadTemplate(String resourcePath, String defaultName) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                showError("Plantilla no encontrada",
                        "No se pudo localizar la plantilla en: " + resourcePath);
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar plantilla");
            chooser.setInitialFileName(defaultName);
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Excel (.xlsx)", "*.xlsx")
            );

            File dest = chooser.showSaveDialog(btnDownloadCompras.getScene().getWindow());
            if (dest == null) return;

            Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            showInfo("Plantilla descargada",
                    "La plantilla se guard\u00F3 correctamente en:\n" + dest.getAbsolutePath());

            System.out.printf("[DATA] Plantilla descargada: %s → %s%n",
                    resourcePath, dest.getAbsolutePath());

        } catch (IOException e) {
            showError("Error al descargar",
                    "Ocurri\u00F3 un error al guardar la plantilla: " + e.getMessage());
            System.err.println("[DATA] Error al descargar plantilla: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HISTÓRICOS — tabla y filtros
    // ═════════════════════════════════════════════════════════════════════════

    private void populateHistoryFilters() {
        cmbDataType.getItems().addAll("Todos", "Ventas", "Compras");
        cmbDataType.getSelectionModel().selectFirst();
        dpHistoryFrom.setValue(LocalDate.now().minusMonths(3));
        dpHistoryTo.setValue(LocalDate.now());
    }

    @SuppressWarnings("unchecked")
    private void setupHistoryTable() {
        colDate.setText("Fecha");
        colProduct.setText("Producto / Servicio");
        colPrice.setText("Precio Unitario");
        colQty.setText("Cantidad");
        colTotal.setText("Monto Final");
        colType.setText("Tipo");

        // Binding de columnas por índice de la lista observable
        colDate.setCellValueFactory(r    -> new SimpleStringProperty(r.getValue().get(0)));
        colProduct.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().get(1)));
        colPrice.setCellValueFactory(r   -> new SimpleStringProperty(r.getValue().get(2)));
        colQty.setCellValueFactory(r     -> new SimpleStringProperty(r.getValue().get(3)));
        colTotal.setCellValueFactory(r   -> new SimpleStringProperty(r.getValue().get(4)));
        colType.setCellValueFactory(r    -> new SimpleStringProperty(r.getValue().get(5)));

        dataTable.setPlaceholder(new Label("Aplica los filtros para ver los datos"));
    }

    @FXML
    private void onApplyFilters() {
        // TODO: reemplazar por dataService.query(tipo, desde, hasta, search)
        //       retorna List<List<String>> o Page<DataRowDTO>
        String tipo   = cmbDataType.getValue();
        LocalDate from = dpHistoryFrom.getValue();
        LocalDate to   = dpHistoryTo.getValue();
        String search  = txtHistorySearch.getText();

        System.out.printf("[DATA] Consulta histórica: tipo=%s, desde=%s, hasta=%s, buscar='%s'%n",
                tipo, from, to, search);

        // Mock: cargamos datos de ejemplo
        List<ObservableList<String>> mockRows = getMockHistoryRows();

        boolean empty = mockRows.isEmpty();
        historyEmptyState.setVisible(empty);
        historyEmptyState.setManaged(empty);
        dataTable.setVisible(!empty);

        dataTable.setItems(FXCollections.observableArrayList(mockRows));

        lblRecordCount.setText(mockRows.size() + " registros");
        lblStatRange.setText("\uD83D\uDCC5  Per\u00EDodo: " + from + " → " + to);
        lblStatTotal.setText("\uD83D\uDCCB  Total: " + mockRows.size() + " filas");
        lblStatAvg.setText("\uD83D\uDCCA  Tipo: " + tipo);
    }

    @FXML
    private void onExportData() {
        // TODO: dataService.export(currentQueryResult, formato)
        System.out.println("[DATA] Exportar datos históricos filtrados");
        showInfo("Exportar datos",
                "La exportaci\u00F3n de datos estar\u00E1 disponible en la pr\u00F3xima versi\u00F3n.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  VALIDACIÓN Y LIMPIEZA (CU-02, CU-03)
    // ═════════════════════════════════════════════════════════════════════════

    /** Sincroniza el estado de la pestaña de validación con el archivo actual */
    private void syncValidationTab() {
        boolean hasFile = currentFile != null;

        validationNoData.setVisible(!hasFile);
        validationNoData.setManaged(!hasFile);
        validationReady.setVisible(hasFile);
        validationReady.setManaged(hasFile);

        if (hasFile) {
            lblValidationFileName.setText(currentFile.getName());
            lblValidationFileMeta.setText(formatSize(currentFile.length())
                    + "  ·  " + getExtension(currentFile.getName()).toUpperCase()
                    + "  ·  Pendiente de validaci\u00F3n");
        }
    }

    private void buildValidationChecklist() {
        List<String> rules = List.of(
                "Columnas obligatorias presentes",
                "Formato de fechas v\u00E1lido (DD/MM/AAAA)",
                "Valores monetarios positivos",
                "Cantidades v\u00E1lidas (> 0)",
                "Campos vac\u00EDos por registro \u2264 50%",
                "Sin fechas futuras"
        );

        List<HBox> rows = new ArrayList<>();
        for (String rule : rules) {
            HBox row = new HBox(10);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label dot = new Label("○");
            dot.getStyleClass().add("rule-dot-pending");
            dot.setMinWidth(20);
            Label lbl = new Label(rule);
            lbl.getStyleClass().add("rule-text");
            row.getChildren().addAll(dot, lbl);
            rows.add(row);
        }

        validationRulesList.getChildren().clear();
        validationRulesList.getChildren().addAll(rows);
    }

    private void buildCleaningSummary() {
        List<String[]> items = List.of(
                new String[]{ "\uD83D\uDDD1\uFE0F", "Eliminar registros duplicados (conserva el m\u00E1s reciente)" },
                new String[]{ "\uD83D\uDEAB", "Descartar filas con \u226550% campos vac\u00EDos" },
                new String[]{ "\uD83D\uDCCA", "Detectar valores at\u00EDpicos (\u00B13 desviaciones est\u00E1ndar)" },
                new String[]{ "\uD83E\uDDE9", "Estimar valores faltantes (mediana por columna)" },
                new String[]{ "\uD83D\uDD04", "Estandarizar formatos (fechas, may\u00FAsculas, espacios)" }
        );

        List<HBox> rows = new ArrayList<>();
        for (String[] item : items) {
            HBox row = new HBox(10);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label icon = new Label(item[0]);
            icon.setMinWidth(24);
            Label lbl = new Label(item[1]);
            lbl.getStyleClass().add("cleaning-item-text");
            lbl.setWrapText(true);
            HBox.setHgrow(lbl, javafx.scene.layout.Priority.ALWAYS);
            row.getChildren().addAll(icon, lbl);
            rows.add(row);
        }

        cleaningSummaryList.getChildren().clear();
        cleaningSummaryList.getChildren().addAll(rows);
    }

    @FXML
    private void onStartValidation() {
        if (currentFile == null) return;

        btnStartValidation.setDisable(true);
        btnStartValidation.setText("Validando...");
        validationProgress.setVisible(true);
        validationProgress.setManaged(true);
        lblValidationStatus.setVisible(true);
        lblValidationStatus.setManaged(true);
        lblValidationStatus.setText("Analizando estructura del archivo...");

        // Animación de progreso simulada
        animateValidation();
    }

    /**
     * Simula el proceso de validación con progreso visual.
     * TODO: reemplazar por Task<ValidationResultDTO> que ejecute el servicio real:
     *       ValidationResultDTO result = validationService.validate(currentFile);
     *       cleaningService.clean(result);
     */
    private void animateValidation() {
        String[] statusMessages = {
                "Verificando columnas obligatorias...",
                "Validando formato de fechas...",
                "Revisando valores monetarios...",
                "Calculando porcentaje de campos vac\u00EDos...",
                "Detectando duplicados...",
                "Estimando valores faltantes...",
                "Generando resumen..."
        };

        final int[] step = { 0 };
        PauseTransition tick = new PauseTransition(Duration.millis(600));
        tick.setOnFinished(e -> {
            if (step[0] < statusMessages.length) {
                double progress = (double)(step[0] + 1) / statusMessages.length;
                validationProgress.setProgress(progress);
                lblValidationStatus.setText(statusMessages[step[0]]);

                // Actualizar el ícono del checklist según el paso
                updateRuleIcon(step[0]);
                step[0]++;
                tick.play(); // siguiente tick
            } else {
                // Completado: mostrar resultado con mock
                javafx.application.Platform.runLater(() ->
                        onValidationComplete(getMockValidationResult())
                );
            }
        });
        tick.play();
    }

    /** Actualiza el ícono de la regla en el checklist durante la animación */
    private void updateRuleIcon(int ruleIndex) {
        if (ruleIndex >= validationRulesList.getChildren().size()) return;
        if (validationRulesList.getChildren().get(ruleIndex) instanceof HBox row) {
            if (row.getChildren().get(0) instanceof Label dot) {
                dot.setText("✔");
                dot.getStyleClass().removeAll("rule-dot-pending");
                dot.getStyleClass().add("rule-dot-ok");
            }
        }
    }

    private void onValidationComplete(ValidationResultDTO result) {
        // Ocultar progreso
        validationProgress.setVisible(false);
        validationProgress.setManaged(false);
        lblValidationStatus.setVisible(false);
        lblValidationStatus.setManaged(false);
        btnStartValidation.setDisable(false);
        btnStartValidation.setText("Iniciar validaci\u00F3n");

        // Llenar métricas
        lblMetricTotalVal.setText(String.valueOf(result.totalRecords()));
        lblMetricValidVal.setText(String.valueOf(result.validRecords()));
        lblMetricRemovedVal.setText(String.valueOf(result.duplicatesRemoved()));
        lblMetricRetentionVal.setText(String.format("%.1f%%", result.retentionPercent()));
        lblResultTitle.setText(
                "\u2714 Validaci\u00F3n completada — "
                        + result.validRecords() + " registros v\u00E1lidos, "
                        + result.invalidRecords() + " con errores"
        );

        // Advertencia de umbral
        if (!result.meetsThreshold()) {
            lblLowRetentionMsg.setText(
                    "Advertencia: la limpieza result\u00F3 en " +
                            String.format("%.1f%%", result.retentionPercent()) +
                            " de retenci\u00F3n (m\u00EDnimo recomendado: 70%).\n" +
                            "Se recomienda revisar los datos de origen antes de confirmar."
            );
            lowRetentionWarning.setVisible(true);
            lowRetentionWarning.setManaged(true);
        } else {
            lowRetentionWarning.setVisible(false);
            lowRetentionWarning.setManaged(false);
        }

        validationResult.setVisible(true);
        validationResult.setManaged(true);
    }

    @FXML
    private void onConfirmValidation() {
        // TODO: dataService.confirmAndSave(validatedData)
        //       Persiste los datos limpios en BD y los marca como disponibles para análisis
        System.out.println("[DATA] Datos validados confirmados y guardados.");
        showInfo("Datos guardados",
                "Los datos han sido validados y est\u00E1n disponibles para an\u00E1lisis.");
        validationResult.setVisible(false);
        validationResult.setManaged(false);
        buildValidationChecklist(); // reset
    }

    @FXML
    private void onCancelValidation() {
        // TODO: dataService.discardTemporaryData()
        validationResult.setVisible(false);
        validationResult.setManaged(false);
        buildValidationChecklist(); // reset checklist
        System.out.println("[DATA] Validaci\u00F3n cancelada, datos temporales descartados.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN DE TABS
    // ═════════════════════════════════════════════════════════════════════════

    @FXML public void onTabUpload()     { switchTab(0); }
    @FXML public void onTabHistory()    { switchTab(1); }
    @FXML public void onTabValidation() { switchTab(2); }

    private void switchTab(int index) {
        if (index == activeTabIndex) return;
        activeTabIndex = index;
        for (int i = 0; i < allTabs.size(); i++) {
            boolean active = (i == index);
            allTabs.get(i).getStyleClass().removeAll("data-tab-active");
            if (active) allTabs.get(i).getStyleClass().add("data-tab-active");
            allSections.get(i).setVisible(active);
            allSections.get(i).setManaged(active);
        }

        // Inicializar la pestaña solo la primera vez que se abre
        switch (index) {
            case 1 -> initHistoryTabIfNeeded();
            case 2 -> initValidationTabIfNeeded();
        }
    }

    private void initHistoryTabIfNeeded() {
        if (historyInitialized) return;
        historyInitialized = true;
        setupHistoryTable();
        populateHistoryFilters();
    }

    private void initValidationTabIfNeeded() {
        if (validationInitialized) return;
        validationInitialized = true;
        buildValidationChecklist();
        buildCleaningSummary();
        syncValidationTab();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ═════════════════════════════════════════════════════════════════════════

    private ImageView loadIcon(String path, int w, int h) {
        ImageView iv = new ImageView();
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setPreserveRatio(true);
        try {
            var stream = getClass().getResourceAsStream(path);
            if (stream != null) iv.setImage(new Image(stream));
        } catch (Exception e) {
            System.err.println("[ICON] No se pudo cargar: " + path);
        }
        return iv;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1) : "";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    private void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MOCKS — sustituir por servicios reales
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * TODO: sustituir por uploadService.findRecent(userId, limit=10)
     */
    private List<UploadedFileDTO> getMockRecentUploads() {
        return List.of(
                new UploadedFileDTO(1L, "ventas_enero_2026.xlsx", "XLSX", "VENTAS",
                        LocalDateTime.now().minusHours(3),
                        "PROCESADO", 2048L, 1250, "Mateo Alexander"),
                new UploadedFileDTO(2L, "compras_q1_2026.csv", "CSV", "COMPRAS",
                        LocalDateTime.now().minusDays(2),
                        "PROCESADO", 512L, 340, "Mateo Alexander"),
                new UploadedFileDTO(3L, "ventas_diciembre_2025.xlsx", "XLSX", "VENTAS",
                        LocalDateTime.now().minusDays(30),
                        "ERROR", 1800L, -1, "Ana Mart\u00EDnez")
        );
    }

    /**
     * TODO: sustituir por dataService.query(filtros) → List<DataRowDTO>
     */
    private List<ObservableList<String>> getMockHistoryRows() {
        return List.of(
                FXCollections.observableArrayList("01/01/2026","Producto A","$150.00","10","$1,500.00","VENTA"),
                FXCollections.observableArrayList("05/01/2026","Producto B","$80.50","5","$402.50","COMPRA"),
                FXCollections.observableArrayList("10/01/2026","Servicio C","$2,000.00","1","$2,000.00","VENTA"),
                FXCollections.observableArrayList("15/01/2026","Producto A","$150.00","20","$3,000.00","VENTA"),
                FXCollections.observableArrayList("20/01/2026","Producto D","$45.00","100","$4,500.00","COMPRA")
        );
    }

    /**
     * TODO: sustituir por validationService.getLastResult(uploadId)
     */
    private ValidationResultDTO getMockValidationResult() {
        return new ValidationResultDTO(
                1250, 1198, 52, 18, 24, 7, 95.8,
                List.of(
                        new ValidationRuleResult("Columnas obligatorias", true, 0, "OK"),
                        new ValidationRuleResult("Formato de fechas", true, 3, "3 fechas corregidas"),
                        new ValidationRuleResult("Valores monetarios", true, 0, "OK"),
                        new ValidationRuleResult("Cantidades v\u00E1lidas", false, 12, "12 filas con cantidad 0"),
                        new ValidationRuleResult("Campos vac\u00EDos \u226550%", true, 0, "OK"),
                        new ValidationRuleResult("Sin fechas futuras", true, 0, "OK")
                )
        );
    }
}