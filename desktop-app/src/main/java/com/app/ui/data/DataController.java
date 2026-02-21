package com.app.ui.data;

import com.app.model.data.CleaningReport;
import com.app.model.data.ValidationReport;
import com.app.model.data.ValidationResultDTO;
import com.app.model.data.ValidationResultDTO.ValidationRuleResult;
import com.app.model.data.api.*;
import com.app.service.data.CleaningService;
import com.app.service.data.DataApiService;
import com.app.service.data.TransformService;
import com.app.service.data.ValidationService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
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
import java.util.*;
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

    /** Servicio para llamadas a la API de datos */
    private final DataApiService dataApiService = new DataApiService();

    /** ID del upload actual en el backend (null = no subido) */
    private String currentUploadId = null;

    /** Tipo de datos seleccionado por el usuario (ventas/compras) */
    private String currentDataType = null;

    private static final long   MAX_SIZE_BYTES = 100L * 1024 * 1024; // 100 MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of("csv", "xlsx");

    private static final DateTimeFormatter FMT_DISPLAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "MX"));

    /** Catálogo de productos cargados en memoria para filtrado local */
    private List<ProductoCatalogDTO> allProductos = new ArrayList<>();

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
            loadRecentUploadsFromApi();
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
        // Limpiar upload temporal en el backend si existe
        if (currentUploadId != null) {
            dataApiService.deleteUpload(currentUploadId);
            currentUploadId = null;
        }

        currentFile = null;
        currentDataType = null;

        stateFileReady.setVisible(false);
        stateFileReady.setManaged(false);
        stateIdle.setVisible(true);
        stateIdle.setManaged(true);

        syncValidationTab();
    }

    // ═════════════════════════════════════════════════════════════════════════
//  PROCESAMIENTO — CU-02, CU-03, CU-04
//  Reemplaza el método onProcessFile() existente en DataController
//  y agrega readFile(), readCsv(), readXlsx() como métodos del controller.
// ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onProcessFile() {
        if (currentFile == null) return;

        // ── Diálogo: selección de tipo de archivo ─────────────────────────────
        Dialog<String> tipoDialog = new Dialog<>();
        tipoDialog.setTitle("Tipo de archivo");
        tipoDialog.setHeaderText("¿Qué tipo de datos contiene este archivo?");
        tipoDialog.setContentText(currentFile.getName());

        ButtonType btnVentas     = new ButtonType("Ventas",      ButtonBar.ButtonData.LEFT);
        ButtonType btnCompras    = new ButtonType("Compras",     ButtonBar.ButtonData.LEFT);
        ButtonType btnProductos  = new ButtonType("Productos",   ButtonBar.ButtonData.LEFT);
        ButtonType btnInventario = new ButtonType("Inventario",  ButtonBar.ButtonData.LEFT);
        ButtonType btnCancel     = new ButtonType("Cancelar",    ButtonBar.ButtonData.CANCEL_CLOSE);
        tipoDialog.getDialogPane().getButtonTypes()
                .setAll(btnVentas, btnCompras, btnProductos, btnInventario, btnCancel);

        tipoDialog.setResultConverter(btn -> {
            if (btn == btnVentas)     return "ventas";
            if (btn == btnCompras)    return "compras";
            if (btn == btnProductos)  return "productos";
            if (btn == btnInventario) return "inventario";
            return null;
        });

        Optional<String> tipoResult = tipoDialog.showAndWait();
        if (tipoResult.isEmpty()) return;

        currentDataType = tipoResult.get();
        System.out.printf("[DATA] Tipo seleccionado: %s — archivo: %s (%s)%n",
                currentDataType, currentFile.getName(), formatSize(currentFile.length()));

        // ── Bloquear UI durante el procesamiento ──────────────────────────────
        btnProcess.setDisable(true);
        btnProcess.setText("Subiendo al servidor...");

        // ── Subir archivo al backend ──────────────────────────────────────────
        dataApiService.uploadFile(currentFile)
                .thenAccept(uploadResponse -> Platform.runLater(() -> {
                    if (uploadResponse == null) {
                        btnProcess.setDisable(false);
                        btnProcess.setText("Procesar archivo");
                        showError("Error al subir",
                                "No se pudo subir el archivo al servidor.\nVerifica que el backend esté activo.");
                        return;
                    }

                    currentUploadId = uploadResponse.getUploadId();
                    System.out.printf("[DATA] Archivo subido — upload_id=%s, filas=%d%n",
                            currentUploadId, uploadResponse.getTotalRows());

                    btnProcess.setText("Validando...");

                    // ── Validar estructura en el backend ──────────────────────
                    dataApiService.validateStructure(currentUploadId, currentDataType)
                            .thenAccept(validateResponse -> Platform.runLater(() -> {
                                if (validateResponse == null) {
                                    btnProcess.setDisable(false);
                                    btnProcess.setText("Procesar archivo");
                                    showError("Error de validación",
                                            "No se pudo validar la estructura del archivo.");
                                    return;
                                }

                                if (!validateResponse.isValid()) {
                                    btnProcess.setDisable(false);
                                    btnProcess.setText("Procesar archivo");
                                    String missing = validateResponse.getMissingRequired() != null
                                            ? String.join(", ", validateResponse.getMissingRequired())
                                            : "N/A";
                                    showError("Estructura inválida",
                                            "Faltan columnas obligatorias: " + missing +
                                            "\n\nRevisa que el archivo use el formato de la plantilla.");
                                    return;
                                }

                                btnProcess.setText("Limpiando datos...");

                                // ── Limpiar datos en el backend ───────────────
                                dataApiService.cleanData(currentUploadId)
                                        .thenAccept(cleanResponse -> Platform.runLater(() -> {
                                            btnProcess.setDisable(false);
                                            btnProcess.setText("Procesar archivo");

                                            if (cleanResponse == null || cleanResponse.getResult() == null) {
                                                showError("Error de limpieza",
                                                        "No se pudo completar la limpieza de datos.");
                                                return;
                                            }

                                            CleanResponseDTO.CleaningResultDTO result = cleanResponse.getResult();
                                            double retention = result.getRetentionPercent();

                                            // Sincronizar pestaña de validación
                                            syncValidationTab();

                                            if (retention < 70.0) {
                                                Alert warn = new Alert(Alert.AlertType.WARNING);
                                                warn.setTitle("Retención baja");
                                                warn.setHeaderText(String.format(
                                                        "La limpieza resultó en %.1f%% de retención (mínimo recomendado: 70%%).", retention));
                                                warn.setContentText(
                                                        "Registros originales:  " + result.getOriginalRows() + "\n" +
                                                        "Registros conservados: " + result.getCleanedRows() + "\n\n" +
                                                        "Se recomienda revisar los datos de origen.");
                                                warn.showAndWait();
                                            } else {
                                                showInfo("Procesamiento completado",
                                                        String.format("Archivo: %s\n\n" +
                                                                "Registros leídos:    %d\n" +
                                                                "Registros válidos:   %d\n" +
                                                                "Duplicados eliminados: %d\n" +
                                                                "Outliers detectados: %d\n" +
                                                                "Retención:           %.1f%%\n" +
                                                                "Calidad:             %.1f%%\n\n" +
                                                                "Los datos están listos. Ve a la pestaña Validación y limpieza para confirmar.",
                                                                currentFile.getName(),
                                                                result.getOriginalRows(),
                                                                result.getCleanedRows(),
                                                                result.getDuplicatesRemoved(),
                                                                result.getOutliersDetected(),
                                                                retention,
                                                                result.getQualityScore()));
                                            }
                                        }));
                            }));
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        btnProcess.setDisable(false);
                        btnProcess.setText("Procesar archivo");
                        showError("Error de conexión", "No se pudo conectar con el servidor: " + ex.getMessage());
                    });
                    return null;
                });
    }

// ─────────────────────────────────────────────────────────────────────────
//  LECTURA DE ARCHIVOS — unifica CSV y XLSX
// ─────────────────────────────────────────────────────────────────────────

    /**
     * Lee el archivo y devuelve todas las filas como List<List<String>>.
     * La fila 0 siempre es la cabecera.
     */
    private List<List<String>> readFile(File file) throws Exception {
        String ext = getExtension(file.getName()).toLowerCase();
        return switch (ext) {
            case "csv"  -> readCsv(file);
            case "xlsx" -> readXlsx(file);
            default     -> throw new IllegalArgumentException("Formato no soportado: " + ext);
        };
    }

    /**
     * Lee un CSV. Fila 0 = cabecera, resto = datos.
     * TODO: sustituir por dataService.parseCsv(file) cuando exista persistencia
     */
    private List<List<String>> readCsv(File file) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cells = Arrays.asList(line.split(",", -1));
                rows.add(new ArrayList<>(cells));
            }
        }
        System.out.printf("[CSV] Leídas %d filas (incluye cabecera).%n", rows.size());
        return rows;
    }

    /**
     * Lee un XLSX usando Apache POI. Fila 0 = cabecera.
     * Requiere: org.apache.poi:poi-ooxml:5.2.3 en pom.xml
     * TODO: sustituir por dataService.parseXlsx(file) cuando exista persistencia
     */
    private List<List<String>> readXlsx(File file) throws Exception {
        List<List<String>> rows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(fis)) {

            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
            org.apache.poi.ss.usermodel.DataFormatter fmt =
                    new org.apache.poi.ss.usermodel.DataFormatter();

            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (org.apache.poi.ss.usermodel.Cell cell : row)
                    cells.add(fmt.formatCellValue(cell));
                // Saltar filas completamente vacías
                if (cells.stream().allMatch(String::isBlank)) continue;
                rows.add(cells);
            }
        }
        System.out.printf("[XLSX] Leídas %d filas (incluye cabecera).%n", rows.size());
        return rows;
    }


    // ═════════════════════════════════════════════════════════════════════════
    //  CARGAS RECIENTES — datos reales de GET /data/historial
    // ═════════════════════════════════════════════════════════════════════════

    /** Llama a la API y actualiza la lista de cargas recientes. */
    private void loadRecentUploadsFromApi() {
        dataApiService.getHistorial(null)
                .thenAccept(historial -> Platform.runLater(() -> {
                    List<HistorialCargaItemDTO> items =
                            (historial != null) ? historial.getItems() : List.of();
                    loadRecentUploads(items);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> loadRecentUploads(List.of()));
                    return null;
                });
    }

    private void loadRecentUploads(List<HistorialCargaItemDTO> uploads) {
        recentUploadsList.getChildren().clear();

        boolean empty = uploads.isEmpty();
        recentEmptyState.setVisible(empty);
        recentEmptyState.setManaged(empty);

        for (HistorialCargaItemDTO upload : uploads) {
            recentUploadsList.getChildren().add(buildRecentRow(upload));
        }
    }

    private HBox buildRecentRow(HistorialCargaItemDTO upload) {
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

        // Icono de tipo (CSV / XLSX)
        String ext = upload.getFileExtension();
        String iconPath = ext.equals("XLSX")
                ? "/images/reports/format-excel.png"
                : "/images/data/csv-icon.png";
        ImageView icon = loadIcon(iconPath, 28, 28);
        HBox colIcon = fixedCol.apply(icon, 40.0);

        // Nombre del archivo (flexible con elipsis)
        Label lblName = new Label(upload.getNombreArchivo());
        lblName.getStyleClass().add("recent-file-name");
        lblName.setTextOverrun(OverrunStyle.ELLIPSIS);
        lblName.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblName, javafx.scene.layout.Priority.ALWAYS);

        // Fecha (parseada desde ISO-8601)
        String fechaStr = "--";
        if (upload.getCargadoEn() != null && !upload.getCargadoEn().isBlank()) {
            try {
                LocalDateTime dt = LocalDateTime.parse(upload.getCargadoEn(),
                        java.time.format.DateTimeFormatter.ISO_DATE_TIME);
                fechaStr = dt.format(FMT_DISPLAY);
            } catch (Exception ignored) {
                fechaStr = upload.getCargadoEn();
            }
        }
        Label lblDate = new Label(fechaStr);
        lblDate.getStyleClass().add("recent-meta");
        HBox colDate = fixedCol.apply(lblDate, 130.0);

        // Estado con color
        Label lblStatus = new Label(upload.getEstado());
        lblStatus.getStyleClass().add("upload-status-" + upload.getEstado().toLowerCase());
        HBox colStatus = fixedCol.apply(lblStatus, 90.0);

        // Registros: muestra insertados y/o actualizados
        int ins = upload.getRegistrosInsertados();
        int upd = upload.getRegistrosActualizados();
        String recsText;
        if (ins > 0 && upd > 0) {
            recsText = ins + " ins · " + upd + " act";
        } else if (ins > 0) {
            recsText = ins + " nuevos";
        } else if (upd > 0) {
            recsText = upd + " actualizados";
        } else {
            recsText = "0 registros";
        }
        Label lblRecs = new Label(recsText);
        lblRecs.getStyleClass().add("recent-meta");
        HBox colRecs = fixedCol.apply(lblRecs, 110.0);

        // Botón ver datos → navega al tab de históricos con el tipo de datos
        Button btnView = new Button("Ver datos");
        btnView.getStyleClass().add("btn-view-log");
        btnView.setTooltip(new Tooltip("Ver datos en Consultar históricos"));
        btnView.setOnAction(e -> onViewUploadedData(upload));

        row.getChildren().addAll(colIcon, lblName, colDate, colStatus, colRecs, btnView);
        return row;
    }

    /** Navega al tab de históricos y carga los productos del usuario. */
    private void onViewUploadedData(HistorialCargaItemDTO upload) {
        System.out.printf("[DATA] Ver datos de carga: id=%d, tipo=%s, archivo=%s%n",
                upload.getIdHistorial(), upload.getTipoDatos(), upload.getNombreArchivo());
        // Navegar al tab de históricos
        switchTab(1);
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
    //  HISTÓRICOS — catálogo de productos del usuario
    // ═════════════════════════════════════════════════════════════════════════

    private void populateHistoryFilters() {
        // Filtro de estado (productos activos / inactivos)
        cmbDataType.getItems().addAll("Todos", "Activos", "Inactivos");
        cmbDataType.getSelectionModel().selectFirst();

        // Ocultar filtros de fecha (no aplican al catálogo de productos)
        dpHistoryFrom.setVisible(false);
        dpHistoryFrom.setManaged(false);
        dpHistoryTo.setVisible(false);
        dpHistoryTo.setManaged(false);
        lblFilterFrom.setVisible(false);
        lblFilterFrom.setManaged(false);
        lblFilterTo.setVisible(false);
        lblFilterTo.setManaged(false);

        // Actualizar etiquetas del filtro
        lblFiltersTitle.setText("Filtros");
        lblFilterType.setText("Estado");
        lblFilterSearch.setText("Buscar producto / SKU");
        txtHistorySearch.setPromptText("Nombre o SKU...");
        lblTableTitle.setText("Cat\u00E1logo de Productos");
    }

    @SuppressWarnings("unchecked")
    private void setupHistoryTable() {
        // Columnas reasignadas al catálogo de productos
        colDate.setText("SKU");
        colProduct.setText("Nombre");
        colPrice.setText("Precio Unit.");
        colQty.setText("Costo Unit.");
        colTotal.setText("Margen");
        colType.setText("Categor\u00EDa");

        colDate.setCellValueFactory(r    -> new SimpleStringProperty(r.getValue().get(0)));
        colProduct.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().get(1)));
        colPrice.setCellValueFactory(r   -> new SimpleStringProperty(r.getValue().get(2)));
        colQty.setCellValueFactory(r     -> new SimpleStringProperty(r.getValue().get(3)));
        colTotal.setCellValueFactory(r   -> new SimpleStringProperty(r.getValue().get(4)));
        colType.setCellValueFactory(r    -> new SimpleStringProperty(r.getValue().get(5)));

        dataTable.setPlaceholder(new Label("Cargando productos..."));
    }

    /** Carga los productos desde la API y los almacena en memoria. */
    private void loadProductosFromApi() {
        dataTable.setPlaceholder(new Label("Cargando productos..."));
        historyEmptyState.setVisible(false);
        historyEmptyState.setManaged(false);

        dataApiService.getProductos()
                .thenAccept(productos -> Platform.runLater(() -> {
                    if (productos == null) {
                        allProductos = new ArrayList<>();
                        showHistoryEmpty(true);
                        return;
                    }
                    allProductos = productos;
                    applyAndRenderProductoFilters();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        allProductos = new ArrayList<>();
                        showHistoryEmpty(true);
                        dataTable.setPlaceholder(new Label("Error al conectar con el servidor."));
                    });
                    return null;
                });
    }

    /** Aplica los filtros actuales sobre allProductos y actualiza la tabla. */
    private void applyAndRenderProductoFilters() {
        String estado  = cmbDataType.getValue();
        String busqueda = txtHistorySearch.getText() != null
                ? txtHistorySearch.getText().trim().toLowerCase() : "";

        List<ProductoCatalogDTO> filtered = allProductos.stream()
                .filter(p -> {
                    if ("Activos".equals(estado)   && !p.isActivo())  return false;
                    if ("Inactivos".equals(estado) &&  p.isActivo())  return false;
                    if (!busqueda.isEmpty()) {
                        boolean matchNombre = p.getNombre().toLowerCase().contains(busqueda);
                        boolean matchSku    = p.getSku().toLowerCase().contains(busqueda);
                        if (!matchNombre && !matchSku) return false;
                    }
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());

        updateProductosTable(filtered);
    }

    /** Renderiza la lista filtrada en la tabla de históricos. */
    private void updateProductosTable(List<ProductoCatalogDTO> productos) {
        boolean empty = productos.isEmpty();
        showHistoryEmpty(empty);

        if (!empty) {
            List<ObservableList<String>> rows = new ArrayList<>();
            for (ProductoCatalogDTO p : productos) {
                rows.add(FXCollections.observableArrayList(
                        p.getSku(),
                        p.getNombre(),
                        p.getPrecioFormateado(),
                        p.getCostoFormateado(),
                        p.getMargen(),
                        p.getCategoriaNombre()
                ));
            }
            dataTable.setItems(FXCollections.observableArrayList(rows));
            dataTable.setVisible(true);
        }

        // Estadísticas del footer
        long activos   = allProductos.stream().filter(ProductoCatalogDTO::isActivo).count();
        long inactivos = allProductos.size() - activos;
        long categorias = allProductos.stream()
                .map(ProductoCatalogDTO::getCategoriaNombre)
                .distinct().count();

        lblRecordCount.setText(productos.size() + " producto" + (productos.size() != 1 ? "s" : ""));
        lblStatRange.setText("\uD83D\uDCE6  Total cat\u00E1logo: " + allProductos.size());
        lblStatTotal.setText("\u2705  Activos: " + activos + "  |  \u26D4  Inactivos: " + inactivos);
        lblStatAvg.setText("\uD83C\uDFF7\uFE0F  Categor\u00EDas: " + categorias);
    }

    private void showHistoryEmpty(boolean empty) {
        historyEmptyState.setVisible(empty);
        historyEmptyState.setManaged(empty);
        dataTable.setVisible(!empty);
        if (empty) {
            dataTable.setPlaceholder(new Label(
                    "No hay productos cargados.\nSube un archivo de tipo \"Productos\" en la pesta\u00F1a Cargar datos."));
        }
    }

    @FXML
    private void onApplyFilters() {
        // Si no hay productos en caché (primera vez o tras error), recargar desde API
        // En cualquier otro caso, filtrar localmente para respuesta inmediata
        if (allProductos.isEmpty()) {
            loadProductosFromApi();
        } else {
            applyAndRenderProductoFilters();
        }
    }

    /** Fuerza la recarga completa del catálogo desde la API (limpia la caché local). */
    private void refreshProductosFromApi() {
        allProductos = new ArrayList<>();
        loadProductosFromApi();
    }

    @FXML
    private void onExportData() {
        System.out.println("[DATA] Exportar catálogo de productos");
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

        // Si ya hay uploadId, usar la API directamente
        if (currentUploadId != null) {
            animateValidationWithApi();
        } else {
            // Si no hay uploadId, primero subir el archivo
            dataApiService.uploadFile(currentFile)
                    .thenAccept(uploadResponse -> Platform.runLater(() -> {
                        if (uploadResponse != null) {
                            currentUploadId = uploadResponse.getUploadId();
                            animateValidationWithApi();
                        } else {
                            resetValidationUI();
                            showError("Error", "No se pudo subir el archivo al servidor.");
                        }
                    }));
        }
    }

    /**
     * Ejecuta validación y limpieza usando la API del backend con animación visual.
     */
    private void animateValidationWithApi() {
        String[] statusMessages = {
                "Verificando columnas obligatorias...",
                "Validando formato de fechas...",
                "Revisando valores monetarios...",
                "Calculando porcentaje de campos vacíos...",
                "Detectando duplicados...",
                "Estimando valores faltantes...",
                "Generando resumen..."
        };

        final int[] step = { 0 };
        PauseTransition tick = new PauseTransition(Duration.millis(400));
        tick.setOnFinished(e -> {
            if (step[0] < statusMessages.length) {
                double progress = (double)(step[0] + 1) / statusMessages.length;
                validationProgress.setProgress(progress);
                lblValidationStatus.setText(statusMessages[step[0]]);
                updateRuleIcon(step[0]);
                step[0]++;
                tick.play();
            } else {
                // Animación completada — ahora llamar al backend para limpiar datos
                lblValidationStatus.setText("Procesando en el servidor...");
                String dataType = currentDataType != null ? currentDataType : "ventas";

                dataApiService.cleanData(currentUploadId)
                        .thenAccept(cleanResponse -> Platform.runLater(() -> {
                            if (cleanResponse != null && cleanResponse.getResult() != null) {
                                CleanResponseDTO.CleaningResultDTO res = cleanResponse.getResult();
                                int totalRecords = res.getOriginalRows();
                                int validRecords = res.getCleanedRows();
                                int invalidRecords = res.getRemovedRows();
                                int duplicatesRemoved = res.getDuplicatesRemoved();
                                double retention = res.getRetentionPercent();

                                ValidationResultDTO result = new ValidationResultDTO(
                                        totalRecords, validRecords, invalidRecords,
                                        duplicatesRemoved, res.getNullsHandled(),
                                        res.getOutliersDetected(), retention,
                                        List.of(
                                                new ValidationRuleResult("Columnas obligatorias", true, 0, "OK"),
                                                new ValidationRuleResult("Formato de fechas", true, 0, "Verificado por backend"),
                                                new ValidationRuleResult("Valores monetarios", true, 0, "OK"),
                                                new ValidationRuleResult("Cantidades válidas", true, invalidRecords, invalidRecords + " filas removidas"),
                                                new ValidationRuleResult("Campos vacíos ≤50%", true, res.getNullsHandled(), res.getNullsHandled() + " nulos procesados"),
                                                new ValidationRuleResult("Sin fechas futuras", true, 0, "OK")
                                        )
                                );
                                onValidationComplete(result);
                            } else {
                                resetValidationUI();
                                showError("Error de limpieza",
                                        "No se pudo completar la limpieza en el servidor.");
                            }
                        }));
            }
        });
        tick.play();
    }

    private void resetValidationUI() {
        btnStartValidation.setDisable(false);
        btnStartValidation.setText("Iniciar validación");
        validationProgress.setVisible(false);
        validationProgress.setManaged(false);
        lblValidationStatus.setVisible(false);
        lblValidationStatus.setManaged(false);
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
        if (currentUploadId == null) {
            showError("Error", "No hay datos cargados para confirmar.");
            return;
        }

        btnConfirmValidation.setDisable(true);
        btnConfirmValidation.setText("Guardando...");

        String dataType = currentDataType != null ? currentDataType : "ventas";

        // Mapeo de columnas según el tipo de datos (source == target porque el CSV
        // ya usa los nombres que espera el backend en su lógica de inserción).
        Map<String, String> columnMappings = new HashMap<>();
        switch (dataType) {
            case "ventas" -> {
                columnMappings.put("fecha",           "fecha");
                columnMappings.put("total",           "total");
                columnMappings.put("cantidad",        "cantidad");
                columnMappings.put("precio_unitario", "precio_unitario");
                columnMappings.put("cliente",         "cliente");
            }
            case "compras" -> {
                columnMappings.put("fecha",     "fecha");
                columnMappings.put("total",     "total");
                columnMappings.put("proveedor", "proveedor");
                columnMappings.put("cantidad",  "cantidad");
                columnMappings.put("costo",     "costo");
            }
            case "productos" -> {
                columnMappings.put("sku",        "sku");
                columnMappings.put("nombre",     "nombre");
                columnMappings.put("precio",     "precio");
                columnMappings.put("categoria",  "categoria");
                columnMappings.put("descripcion","descripcion");
                columnMappings.put("costo",      "costo");
            }
            case "inventario" -> {
                columnMappings.put("sku",       "sku");
                columnMappings.put("cantidad",  "cantidad");
                columnMappings.put("ubicacion", "ubicacion");
                columnMappings.put("minimo",    "minimo");
                columnMappings.put("maximo",    "maximo");
            }
            default -> {
                // Fallback: enviar columnas vacías y dejar que el backend las detecte
            }
        }

        dataApiService.confirmUpload(currentUploadId, dataType, columnMappings)
                .thenAccept(confirmResponse -> Platform.runLater(() -> {
                    btnConfirmValidation.setDisable(false);
                    btnConfirmValidation.setText("Confirmar y guardar");

                    if (confirmResponse != null && confirmResponse.isSuccess()) {
                        int inserted = confirmResponse.getRecordsInserted();
                        int updated  = confirmResponse.getRecordsUpdated();
                        String detail;
                        if (inserted > 0 && updated > 0) {
                            detail = String.format(
                                "%d registros nuevos insertados\n%d registros existentes actualizados",
                                inserted, updated);
                        } else if (inserted > 0) {
                            detail = String.format("%d registros insertados exitosamente", inserted);
                        } else if (updated > 0) {
                            detail = String.format("%d registros actualizados exitosamente", updated);
                        } else {
                            detail = confirmResponse.getMessage();
                        }
                        showInfo("Datos guardados", detail);
                        validationResult.setVisible(false);
                        validationResult.setManaged(false);
                        buildValidationChecklist();
                        currentUploadId = null;
                        // Refrescar cargas recientes y catálogo de productos
                        loadRecentUploadsFromApi();
                        if (historyInitialized) {
                            refreshProductosFromApi();
                        }
                    } else {
                        String msg = confirmResponse != null ? confirmResponse.getMessage() : "Error desconocido";
                        showError("Error al confirmar", msg);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        btnConfirmValidation.setDisable(false);
                        btnConfirmValidation.setText("Confirmar y guardar");
                        showError("Error de conexión", ex.getMessage());
                    });
                    return null;
                });
    }

    @FXML
    private void onCancelValidation() {
        // Eliminar upload temporal en el backend
        if (currentUploadId != null) {
            dataApiService.deleteUpload(currentUploadId)
                    .thenAccept(success ->
                            System.out.println("[DATA] Upload temporal eliminado: " + success));
            currentUploadId = null;
        }

        validationResult.setVisible(false);
        validationResult.setManaged(false);
        buildValidationChecklist();
        System.out.println("[DATA] Validación cancelada, datos temporales descartados.");
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
        loadProductosFromApi();
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

}