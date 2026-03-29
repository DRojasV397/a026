package com.app.ui.reports;

import com.app.core.session.UserSession;
import com.app.model.reports.ExecutionLogDTO;
import com.app.model.reports.ReportDTO;
import com.app.model.reports.ReportTypeDTO;
import com.app.model.reports.ScheduledReportDTO;
import com.app.service.reports.ReportGeneratorService;
import com.app.service.reports.ReportRegistryService;
import com.app.service.reports.ReportScheduler;
import com.app.service.reports.ReportsService;
import com.app.ui.components.AnimatedToggleSwitch;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class ReportsController {

    // ── Tabs ──────────────────────────────────────────────────────────────────
    @FXML private Button tabGenerate;
    @FXML private Button tabSaved;
    @FXML private Button tabSchedule;

    // ── Secciones ─────────────────────────────────────────────────────────────
    @FXML private VBox sectionGenerate;
    @FXML private VBox sectionSaved;
    @FXML private VBox sectionSchedule;

    // ── Sección: Generar Reporte ──────────────────────────────────────────────
    @FXML private Label     lblTypesTitle;
    @FXML private VBox      reportTypeList;
    @FXML private VBox      configPanel;
    @FXML private VBox      configEmptyState;
    @FXML private Label     lblSelectTypeHint;
    @FXML private VBox      configForm;
    @FXML private Label     lblConfigTitle;
    @FXML private Label     lblReportName;
    @FXML private TextField txtReportName;
    @FXML private VBox      subTypeBox;
    @FXML private Label     lblSubType;
    @FXML private ComboBox<String> cmbSubType;
    @FXML private Label     lblDateFrom;
    @FXML private DatePicker dpFrom;
    @FXML private Label     lblDateTo;
    @FXML private DatePicker dpTo;
    @FXML private Label     lblFormatTitle;
    @FXML private VBox      btnFormatPdf;
    @FXML private VBox      btnFormatExcel;
    @FXML private Label     lblPdfFormat;
    @FXML private Label     lblExcelFormat;
    @FXML private Button    btnGenerate;
    @FXML private Label     lblGenerateStatus;

    // ── Sección: Reportes Guardados ───────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Label     lblResultCount;
    @FXML private VBox      savedReportsList;
    @FXML private VBox      savedEmptyState;
    @FXML private Label     lblSavedEmpty;

    // ── Sección: Programación ─────────────────────────────────────────────────
    @FXML private Label  lblScheduleTitle;
    @FXML private Button btnNewSchedule;
    @FXML private VBox   scheduleCardsList;
    @FXML private VBox   scheduleEmptyState;
    @FXML private Label  lblScheduleEmpty;
    @FXML private Label  lblHistoryTitle;
    @FXML private VBox   executionHistoryList;
    @FXML private VBox   historyEmptyState;
    @FXML private Label  lblHistoryEmpty;

    // ── Estado interno ────────────────────────────────────────────────────────
    private List<Button>    allTabs;
    private List<VBox>      allSections;
    private int             activeTabIndex    = 0;
    private String          selectedFormat    = "PDF";
    private ReportTypeDTO   selectedReportType = null;
    private List<ReportTypeDTO> cachedReportTypes;

    // ── Servicios ─────────────────────────────────────────────────────────────
    private final ReportsService          reportsService = new ReportsService();
    private final ReportGeneratorService  generator      = new ReportGeneratorService();
    private final ReportRegistryService   registry       = new ReportRegistryService();
    private final ReportScheduler         scheduler      = ReportScheduler.getInstance();

    // ── Listas observables ────────────────────────────────────────────────────
    private ObservableList<ReportDTO>   allSavedReports;
    private FilteredList<ReportDTO>     filteredReports;
    private final List<ExecutionLogDTO> executionLogs = new ArrayList<>();
    private final AtomicLong            nextLogId     = new AtomicLong(1);

    private static final DateTimeFormatter FMT_DISPLAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "MX"));
    private static final DateTimeFormatter FMT_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", new Locale("es", "MX"));
    private static final DateTimeFormatter FMT_DATERANGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        allTabs     = List.of(tabGenerate, tabSaved, tabSchedule);
        allSections = List.of(sectionGenerate, sectionSaved, sectionSchedule);

        setAllTexts();

        cachedReportTypes = getMockReportTypes();
        loadReportTypes(cachedReportTypes);

        dpTo.setValue(LocalDate.now());
        dpFrom.setValue(LocalDate.now().minusMonths(1));

        // Cargar tipos reales desde la API
        reportsService.getReportTypes().thenAccept(types -> Platform.runLater(() -> {
            if (!types.isEmpty()) {
                cachedReportTypes = types;
                loadReportTypes(types);
            }
        }));

        // Inicializar datos persistentes y scheduler
        Platform.runLater(() -> {
            loadSavedReports(registry.loadAll());
            loadScheduledReports(scheduler.loadSchedules());
            loadExecutionHistory(executionLogs);
            scheduler.start(generator, reportsService, registry, this::onScheduledReportGenerated);
        });
    }

    private void setAllTexts() {
        tabGenerate.setText("Generar Reporte");
        tabSaved.setText("Reportes Guardados");
        tabSchedule.setText("Programaci\u00F3n");

        lblTypesTitle.setText("Tipo de Reporte");
        lblSelectTypeHint.setText("Selecciona un tipo de reporte\npara configurar y generar");
        lblReportName.setText("Nombre del Reporte");
        lblSubType.setText("Subtipo");
        lblDateFrom.setText("Fecha de inicio");
        lblDateTo.setText("Fecha de fin");
        lblFormatTitle.setText("Formato de salida");
        lblPdfFormat.setText("PDF");
        lblExcelFormat.setText("Excel");
        btnGenerate.setText("Generar Reporte");

        txtSearch.setPromptText("\uD83D\uDD0D  Buscar reporte por nombre, tipo o usuario...");
        lblSavedEmpty.setText("No se encontraron reportes.\nGenera tu primer reporte en la pesta\u00F1a anterior.");

        lblScheduleTitle.setText("Reportes Programados");
        btnNewSchedule.setText("+ Nueva Programaci\u00F3n");
        lblScheduleEmpty.setText("No hay reportes programados.\nCrea una programaci\u00F3n para automatizar la generaci\u00F3n.");
        lblHistoryTitle.setText("\u00DAltimas Ejecuciones");
        lblHistoryEmpty.setText("No hay ejecuciones recientes registradas.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TIPOS DE REPORTE — Panel izquierdo
    // ═════════════════════════════════════════════════════════════════════════

    private void loadReportTypes(List<ReportTypeDTO> types) {
        List<HBox> cards = new ArrayList<>();
        for (ReportTypeDTO type : types)
            cards.add(buildReportTypeCard(type));

        reportTypeList.getChildren().clear();
        reportTypeList.getChildren().addAll(cards);
    }

    private HBox buildReportTypeCard(ReportTypeDTO type) {
        HBox card = new HBox(12);
        card.getStyleClass().add("report-type-card");
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        ImageView icon = loadIcon(type.iconPath(), 32, 32);
        icon.getStyleClass().add("report-type-icon");

        VBox texts = new VBox(3);
        Label name = new Label(type.name());
        name.getStyleClass().add("report-type-name");
        Label desc = new Label(type.description());
        desc.getStyleClass().add("report-type-desc");
        desc.setWrapText(true);
        texts.getChildren().addAll(name, desc);
        HBox.setHgrow(texts, Priority.ALWAYS);

        card.getChildren().addAll(icon, texts);
        card.setOnMouseClicked(e -> onReportTypeSelected(type, card));
        return card;
    }

    private void onReportTypeSelected(ReportTypeDTO type, HBox selectedCard) {
        reportTypeList.getChildren().forEach(n ->
                n.getStyleClass().remove("report-type-card-selected"));
        selectedCard.getStyleClass().add("report-type-card-selected");
        selectedReportType = type;

        configEmptyState.setVisible(false);
        configEmptyState.setManaged(false);
        configForm.setVisible(true);
        configForm.setManaged(true);

        lblConfigTitle.setText("Configuraci\u00F3n: " + type.name());
        txtReportName.setText(type.name() + " - " +
                DateTimeFormatter.ofPattern("dd/MM/yyyy").format(LocalDate.now()));

        loadSubTypes(type.id());
    }

    private void loadSubTypes(String typeId) {
        cmbSubType.getItems().clear();

        List<String> subTypes = switch (typeId) {
            case "ventas"       -> List.of("Agrupado por d\u00EDa", "Agrupado por semana", "Agrupado por mes");
            case "compras"      -> List.of("Agrupado por d\u00EDa", "Agrupado por semana", "Agrupado por mes");
            case "rentabilidad" -> List.of("Informe completo (mensual + categoría + producto)",
                                             "Solo mensual", "Solo por categoría", "Solo por producto (top 50)");
            case "productos"    -> List.of("Top 10 productos", "Top 20 productos", "Top 50 productos");
            default             -> List.of("Reporte completo");
        };

        cmbSubType.getItems().addAll(subTypes);
        cmbSubType.getSelectionModel().selectFirst();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  GENERACIÓN DE REPORTE
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onSelectPdf() {
        selectedFormat = "PDF";
        btnFormatPdf.getStyleClass().add("format-btn-selected");
        btnFormatExcel.getStyleClass().remove("format-btn-selected");
    }

    @FXML
    private void onSelectExcel() {
        selectedFormat = "EXCEL";
        btnFormatExcel.getStyleClass().add("format-btn-selected");
        btnFormatPdf.getStyleClass().remove("format-btn-selected");
    }

    @FXML
    private void onGenerateReport() {
        if (selectedReportType == null) {
            showStatus("Selecciona un tipo de reporte.", false);
            return;
        }
        if (txtReportName.getText().isBlank()) {
            showStatus("Ingresa un nombre para el reporte.", false);
            return;
        }
        if (dpFrom.getValue() == null || dpTo.getValue() == null) {
            showStatus("Define el per\u00EDodo del reporte.", false);
            return;
        }
        if (dpFrom.getValue().isAfter(dpTo.getValue())) {
            showStatus("La fecha de inicio no puede ser posterior a la fecha fin.", false);
            return;
        }

        String name   = txtReportName.getText().trim();
        String format = selectedFormat;
        String ext    = format.equalsIgnoreCase("PDF") ? ".pdf" : ".xlsx";
        String safeName   = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String timestamp  = LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Diálogo para que el usuario elija dónde guardar el archivo
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar reporte");
        fileChooser.setInitialFileName(safeName + "_" + timestamp + ext);
        if (format.equalsIgnoreCase("PDF")) {
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF (*.pdf)", "*.pdf"));
        } else {
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        }
        File defaultDir = generator.getReportsDir().toFile();
        fileChooser.setInitialDirectory(defaultDir.exists() ? defaultDir
                : new File(System.getProperty("user.home")));

        File selectedFile = fileChooser.showSaveDialog(btnGenerate.getScene().getWindow());
        if (selectedFile == null) return; // usuario canceló
        final Path targetPath = selectedFile.toPath();

        btnGenerate.setDisable(true);
        showStatus("Generando reporte...", true);

        String tipo          = selectedReportType.id();
        String subtype       = cmbSubType.getValue() != null ? cmbSubType.getValue() : "";
        String agruparPor    = subtypeToAgruparPor(subtype);
        int    topN          = subtypeToTopN(subtype);
        String effectiveTipo = subtypeToEffectiveTipo(tipo, subtype);
        String dateRange     = dpFrom.getValue().format(FMT_DATERANGE)
                + " - " + dpTo.getValue().format(FMT_DATERANGE);
        LocalDate from       = dpFrom.getValue();
        LocalDate to         = dpTo.getValue();

        reportsService.fetchReportData(tipo, from, to, agruparPor, topN)
                .thenAccept(data -> Platform.runLater(() -> {
                    btnGenerate.setDisable(false);

                    if (data.isEmpty()) {
                        showStatus("\u2716 Error al obtener datos del servidor. Verifica la conexi\u00F3n.", false);
                        return;
                    }

                    try {
                        ReportGeneratorService.GeneratedFile file;
                        if ("rentabilidad_completo".equals(effectiveTipo)) {
                            file = generator.generateRentabilidadCompleto(
                                    format, name, dateRange,
                                    extractRows(data, "rentabilidad"),
                                    extractRows(data, "rentabilidad_categoria"),
                                    extractRows(data, "rentabilidad_producto"),
                                    targetPath);
                        } else {
                            List<Map<String, Object>> rows = extractRows(data, effectiveTipo);
                            file = generator.generate(format, name, effectiveTipo, dateRange, rows, targetPath);
                        }

                        String displayTypeName = selectedReportType.name()
                                + (subtype.isEmpty() ? "" : " \u2014 " + subtype);
                        ReportDTO report = new ReportDTO(
                                System.currentTimeMillis(),
                                name,
                                effectiveTipo,
                                displayTypeName,
                                format,
                                UserSession.getNombreCompleto(),
                                LocalDateTime.now(),
                                file.path().toString(),
                                file.sizeKb()
                        );

                        registry.add(report);
                        allSavedReports.add(0, report);
                        renderSavedReports();

                        showStatus("\u2714 Guardado en: " + file.path().toAbsolutePath(), true);

                    } catch (Exception e) {
                        showStatus("\u2716 Error al generar el archivo: " + e.getMessage(), false);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        btnGenerate.setDisable(false);
                        showStatus("\u2716 Error inesperado: " + ex.getMessage(), false);
                    });
                    return null;
                });
    }

    private void showStatus(String message, boolean isSuccess) {
        lblGenerateStatus.setText(message);
        lblGenerateStatus.getStyleClass().removeAll("status-success", "status-error");
        lblGenerateStatus.getStyleClass().add(isSuccess ? "status-success" : "status-error");
        lblGenerateStatus.setVisible(true);
        lblGenerateStatus.setManaged(true);
    }

    // ── Subtype helpers ───────────────────────────────────────────────────────

    private String subtypeToAgruparPor(String subtype) {
        if (subtype == null) return "mes";
        String lower = subtype.toLowerCase();
        if (lower.contains("d\u00eda") || lower.contains("dia")) return "dia";
        if (lower.contains("semana")) return "semana";
        return "mes";
    }

    private int subtypeToTopN(String subtype) {
        if (subtype == null) return 20;
        if (subtype.contains("10")) return 10;
        if (subtype.contains("50")) return 50;
        return 20;
    }

    /**
     * Convierte tipo + subtype en un "tipo efectivo" para el generador de archivos.
     */
    private String subtypeToEffectiveTipo(String tipo, String subtype) {
        if (!"rentabilidad".equals(tipo) || subtype == null) return tipo;
        String lower = subtype.toLowerCase();
        if (lower.contains("completo"))  return "rentabilidad_completo";
        if (lower.contains("categor"))   return "rentabilidad_categoria";
        if (lower.contains("producto"))  return "rentabilidad_producto";
        return tipo; // "Solo mensual" → usa tipo base
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Map<String, Object> data, String effectiveTipo) {
        Object reporteObj = data.get("reporte");
        if (!(reporteObj instanceof Map<?, ?> reporte)) return List.of();
        Object listObj = switch (effectiveTipo.toLowerCase()) {
            case "productos"               -> reporte.get("productos");
            case "rentabilidad"            -> reporte.get("datos_mensuales");
            case "rentabilidad_categoria"  -> reporte.get("por_categoria");
            case "rentabilidad_producto"   -> reporte.get("por_producto");
            default                        -> reporte.get("datos");
        };
        if (listObj instanceof List<?> list) return (List<Map<String, Object>>) list;
        return List.of();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  REPORTES GUARDADOS — con buscador
    // ═════════════════════════════════════════════════════════════════════════

    private void loadSavedReports(List<ReportDTO> reports) {
        allSavedReports = FXCollections.observableArrayList(reports);
        filteredReports = new FilteredList<>(allSavedReports, p -> true);

        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            String term = newVal == null ? "" : newVal.trim().toLowerCase();
            filteredReports.setPredicate(report -> {
                if (term.isEmpty()) return true;
                return report.name().toLowerCase().contains(term)
                        || report.typeName().toLowerCase().contains(term)
                        || report.generatedBy().toLowerCase().contains(term)
                        || report.format().toLowerCase().contains(term);
            });
            renderSavedReports();
        });

        renderSavedReports();
    }

    private void renderSavedReports() {
        List<ReportDTO> visible = filteredReports.stream().toList();

        boolean isEmpty = visible.isEmpty();
        savedEmptyState.setVisible(isEmpty);
        savedEmptyState.setManaged(isEmpty);
        lblResultCount.setText(isEmpty ? "Sin resultados"
                : visible.size() + " reporte" + (visible.size() == 1 ? "" : "s"));

        List<HBox> rows = new ArrayList<>();
        for (ReportDTO report : visible)
            rows.add(buildSavedReportRow(report));

        savedReportsList.getChildren().clear();
        savedReportsList.getChildren().addAll(rows);
    }

    private HBox buildSavedReportRow(ReportDTO report) {
        HBox row = new HBox(14);
        row.getStyleClass().add("saved-report-row");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        String iconPath = report.format().equals("PDF")
                ? "/images/reports/format-pdf.png"
                : "/images/reports/format-excel.png";
        ImageView formatIcon = loadIcon(iconPath, 32, 32);

        HBox nameRow = new HBox(8);
        nameRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label lblName = new Label(report.name());
        lblName.getStyleClass().add("saved-report-name");
        nameRow.getChildren().add(lblName);

        if (report.isNew()) {
            Label badge = new Label("NUEVO");
            badge.getStyleClass().add("badge-new");
            nameRow.getChildren().add(badge);
        }

        Label lblMeta = new Label(report.typeName() + "  \u00B7  " +
                report.createdAt().format(FMT_DISPLAY));
        lblMeta.getStyleClass().add("saved-report-meta");

        Label lblUser = new Label("\uD83D\uDC64  " + report.generatedBy());
        lblUser.getStyleClass().add("saved-report-user");

        VBox info = new VBox(3, nameRow, lblMeta, lblUser);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lblSize = new Label(report.sizeKb() + " KB");
        lblSize.getStyleClass().add("saved-report-size");

        Button btnDownload = new Button();
        btnDownload.getStyleClass().add("icon-btn");
        btnDownload.setGraphic(loadIcon("/images/reports/icon-download.png", 18, 18));
        btnDownload.setTooltip(new Tooltip("Abrir archivo"));
        btnDownload.setOnAction(e -> onDownloadReport(report));

        row.getChildren().addAll(formatIcon, info, lblSize, btnDownload);
        return row;
    }

    private void onDownloadReport(ReportDTO report) {
        if (report.filePath() == null || report.filePath().isBlank()) {
            showAlertError("No hay ruta de archivo disponible para este reporte.");
            return;
        }
        File file = new File(report.filePath());
        if (!file.exists()) {
            showAlertError("El archivo ya no existe en disco:\n" + report.filePath());
            return;
        }
        try {
            Desktop.getDesktop().open(file);
        } catch (Exception e) {
            showAlertError("No se pudo abrir el archivo:\n" + e.getMessage());
        }
    }

    private void showAlertError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        applyAlertStyle(alert);
        alert.showAndWait();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PROGRAMACIÓN DE REPORTES
    // ═════════════════════════════════════════════════════════════════════════

    private void loadScheduledReports(List<ScheduledReportDTO> schedules) {
        boolean isEmpty = schedules.isEmpty();
        scheduleEmptyState.setVisible(isEmpty);
        scheduleEmptyState.setManaged(isEmpty);

        List<HBox> cards = new ArrayList<>();
        for (ScheduledReportDTO schedule : schedules)
            cards.add(buildScheduleCard(schedule));

        scheduleCardsList.getChildren().clear();
        scheduleCardsList.getChildren().addAll(cards);
    }

    private HBox buildScheduleCard(ScheduledReportDTO schedule) {
        HBox card = new HBox(16);
        card.getStyleClass().add("schedule-card");
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        AnimatedToggleSwitch toggle = new AnimatedToggleSwitch();
        toggle.setSelected(schedule.active());
        toggle.selectedProperty().addListener((obs, oldVal, active) -> {
            ScheduledReportDTO updated = new ScheduledReportDTO(
                    schedule.id(), schedule.name(), schedule.reportTypeId(),
                    schedule.typeName(), schedule.frequency(), schedule.scheduledTime(),
                    schedule.format(), active,
                    schedule.lastExecution(), schedule.nextExecution(), schedule.createdBy());
            scheduler.updateSchedule(updated);
        });

        ReportTypeDTO type = cachedReportTypes.stream()
                .filter(t -> t.id().equals(schedule.reportTypeId()))
                .findFirst().orElse(null);
        ImageView typeIcon = loadIcon(
                type != null ? type.iconPath() : "/images/reports/format-pdf.png", 28, 28);

        Label lblName = new Label(schedule.name());
        lblName.getStyleClass().add("schedule-name");

        Label lblFreq = new Label("\uD83D\uDD01  " + schedule.frequency()
                + "  \u00B7  " + schedule.scheduledTime()
                + "  \u00B7  " + schedule.format());
        lblFreq.getStyleClass().add("schedule-meta");

        String lastExec = schedule.lastExecution() != null
                ? schedule.lastExecution().format(FMT_DISPLAY)
                : "Sin ejecuciones";
        Label lblLast = new Label("\uD83D\uDD52  \u00DAltima: " + lastExec);
        lblLast.getStyleClass().add("schedule-meta");

        String nextExec = schedule.nextExecution() != null
                ? schedule.nextExecution().format(FMT_DISPLAY)
                : "\u2014";
        Label lblNext = new Label("\u23F0  Pr\u00F3xima: " + nextExec);
        lblNext.getStyleClass().add("schedule-meta");

        VBox info = new VBox(4, lblName, lblFreq, lblLast, lblNext);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button btnEdit = new Button();
        btnEdit.getStyleClass().add("icon-btn");
        btnEdit.setGraphic(loadIcon("/images/reports/icon-edit.png", 16, 16));
        btnEdit.setTooltip(new Tooltip("Editar programaci\u00F3n"));
        btnEdit.setOnAction(e -> onEditSchedule(schedule));

        Button btnDelete = new Button();
        btnDelete.getStyleClass().addAll("icon-btn", "icon-btn-danger");
        btnDelete.setGraphic(loadIcon("/images/reports/icon-delete.png", 16, 16));
        btnDelete.setTooltip(new Tooltip("Eliminar programaci\u00F3n"));
        btnDelete.setOnAction(e -> onDeleteSchedule(schedule));

        VBox actions = new VBox(6, btnEdit, btnDelete);
        actions.setAlignment(javafx.geometry.Pos.CENTER);

        card.getChildren().addAll(toggle, typeIcon, info, actions);
        return card;
    }

    private void onEditSchedule(ScheduledReportDTO schedule) {
        openScheduleDialog(schedule);
    }

    private void onDeleteSchedule(ScheduledReportDTO schedule) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar programaci\u00F3n");
        confirm.setHeaderText("\u00BFEliminar \"" + schedule.name() + "\"?");
        confirm.setContentText("Esta acci\u00F3n no se puede deshacer.");

        ButtonType btnYes = new ButtonType("Eliminar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnNo  = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(btnYes, btnNo);
        applyAlertStyle(confirm);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == btnYes) {
                scheduler.deleteSchedule(schedule.id());
                loadScheduledReports(scheduler.loadSchedules());
            }
        });
    }

    @FXML
    private void onNewSchedule() {
        openScheduleDialog(null);
    }

    /**
     * Abre un diálogo para crear o editar una programación.
     * @param existing null = nueva programación; non-null = editar existente
     */
    private void openScheduleDialog(ScheduledReportDTO existing) {
        boolean isNew = (existing == null);
        Dialog<ScheduledReportDTO> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "Nueva Programaci\u00F3n" : "Editar Programaci\u00F3n");
        dialog.setHeaderText(isNew ? "Configura la nueva programaci\u00F3n automática" : "Modifica la programaci\u00F3n");

        ButtonType okBtn     = new ButtonType(isNew ? "Crear" : "Guardar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, cancelBtn);

        // ── Form fields ───────────────────────────────────────────────────────
        TextField nameField = new TextField(isNew ? "" : existing.name());
        nameField.setPromptText("Ej: Ventas Diarias");

        ComboBox<String> tipoCombo = new ComboBox<>();
        tipoCombo.getItems().addAll("ventas", "compras", "rentabilidad", "productos");
        tipoCombo.setValue(isNew ? "ventas" : existing.reportTypeId());

        ComboBox<String> frecCombo = new ComboBox<>();
        frecCombo.getItems().addAll("Diaria", "Semanal", "Mensual", "Trimestral");
        frecCombo.setValue(isNew ? "Diaria" : existing.frequency());

        TextField horaField = new TextField(isNew ? "08:00" : existing.scheduledTime());
        horaField.setPromptText("HH:mm");

        ComboBox<String> formatoCombo = new ComboBox<>();
        formatoCombo.getItems().addAll("PDF", "EXCEL");
        formatoCombo.setValue(isNew ? "PDF" : (existing.format().equals("AMBOS") ? "PDF" : existing.format()));

        // ── Layout ────────────────────────────────────────────────────────────
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(16));

        grid.add(new Label("Nombre:"),    0, 0); grid.add(nameField,   1, 0);
        grid.add(new Label("Tipo:"),      0, 1); grid.add(tipoCombo,   1, 1);
        grid.add(new Label("Frecuencia:"),0, 2); grid.add(frecCombo,   1, 2);
        grid.add(new Label("Hora (HH:mm):"), 0, 3); grid.add(horaField, 1, 3);
        grid.add(new Label("Formato:"),   0, 4); grid.add(formatoCombo,1, 4);

        HBox.setHgrow(nameField, Priority.ALWAYS);
        tipoCombo.setMaxWidth(Double.MAX_VALUE);
        frecCombo.setMaxWidth(Double.MAX_VALUE);
        horaField.setMaxWidth(Double.MAX_VALUE);
        formatoCombo.setMaxWidth(Double.MAX_VALUE);

        dialog.getDialogPane().setContent(grid);
        applyAlertStyle(dialog);

        // ── Result converter ──────────────────────────────────────────────────
        dialog.setResultConverter(buttonType -> {
            if (buttonType != okBtn) return null;

            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                showAlertError("El nombre de la programación no puede estar vacío.");
                return null;
            }
            String hora = horaField.getText().trim();
            if (!hora.matches("\\d{1,2}:\\d{2}")) {
                showAlertError("La hora debe tener formato HH:mm (ej: 08:00).");
                return null;
            }

            String tipo = tipoCombo.getValue();
            String typeName = capitalize(tipo);
            String frec   = frecCombo.getValue();
            String formato = formatoCombo.getValue();
            boolean active = isNew || existing.active();
            LocalDateTime lastExec = isNew ? null : existing.lastExecution();

            return new ScheduledReportDTO(
                    isNew ? 0L : existing.id(),
                    name, tipo, typeName, frec, hora, formato, active,
                    lastExec, null,
                    UserSession.getNombreCompleto()
            );
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != null) {
                scheduler.saveSchedule(result);
                loadScheduledReports(scheduler.loadSchedules());
            }
        });
    }

    /** Callback invocado por el scheduler cuando un reporte automático se genera. */
    private void onScheduledReportGenerated(ReportDTO report) {
        Platform.runLater(() -> {
            allSavedReports.add(0, report);
            renderSavedReports();

            ExecutionLogDTO log = new ExecutionLogDTO(
                    nextLogId.getAndIncrement(),
                    0L,
                    report.name(),
                    report.createdAt(),
                    "EXITOSO",
                    0L,
                    "[" + report.createdAt().format(FMT_DISPLAY) + "] Reporte generado automáticamente.\n"
                            + "Archivo: " + report.filePath()
            );
            executionLogs.add(0, log);
            loadExecutionHistory(executionLogs);
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HISTORIAL DE EJECUCIONES
    // ═════════════════════════════════════════════════════════════════════════

    private void loadExecutionHistory(List<ExecutionLogDTO> logs) {
        boolean isEmpty = logs.isEmpty();
        historyEmptyState.setVisible(isEmpty);
        historyEmptyState.setManaged(isEmpty);

        List<HBox> rows = new ArrayList<>();
        for (ExecutionLogDTO log : logs)
            rows.add(buildHistoryRow(log));

        executionHistoryList.getChildren().clear();
        executionHistoryList.getChildren().addAll(rows);
    }

    private HBox buildHistoryRow(ExecutionLogDTO log) {
        HBox row = new HBox(12);
        row.getStyleClass().add("history-row");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        java.util.function.BiFunction<javafx.scene.Node, Double, HBox> fixedCol =
                (node, width) -> {
                    HBox col = new HBox(node);
                    col.setMinWidth(width);
                    col.setMaxWidth(width);
                    col.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    return col;
                };

        Label statusDot = new Label("\u25CF");
        statusDot.getStyleClass().add("status-dot");
        statusDot.getStyleClass().add(switch (log.status()) {
            case "EXITOSO" -> "status-dot-success";
            case "FALLIDO" -> "status-dot-error";
            default        -> "status-dot-warning";
        });
        HBox colDot = fixedCol.apply(statusDot, 20.0);

        Label lblName = new Label(log.scheduledName());
        lblName.getStyleClass().add("history-name");
        lblName.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        lblName.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblName, Priority.ALWAYS);

        Label lblDate = new Label(log.executedAt().format(FMT_DISPLAY));
        lblDate.getStyleClass().add("history-meta");
        HBox colDate = fixedCol.apply(lblDate, 140.0);

        Label lblDuration = new Label(log.durationMs() + " ms");
        lblDuration.getStyleClass().add("history-meta");
        HBox colDuration = fixedCol.apply(lblDuration, 70.0);

        Label lblStatus = new Label(log.status());
        lblStatus.getStyleClass().add("history-status-" + log.status().toLowerCase());
        HBox colStatus = fixedCol.apply(lblStatus, 80.0);

        Button btnLog = new Button();
        btnLog.getStyleClass().add("icon-btn-sm");
        btnLog.setGraphic(loadIcon("/images/reports/icon-log.png", 14, 14));
        btnLog.setTooltip(new Tooltip("Ver detalle del log"));
        btnLog.setOnAction(e -> onViewLog(log));

        row.getChildren().addAll(colDot, lblName, colDate, colDuration, colStatus, btnLog);
        return row;
    }

    private void onViewLog(ExecutionLogDTO log) {
        Alert logDialog = new Alert(Alert.AlertType.INFORMATION);
        logDialog.setTitle("Log de ejecuci\u00F3n");
        logDialog.setHeaderText(log.scheduledName()
                + "  \u2014  " + log.executedAt().format(FMT_DISPLAY));

        TextArea logArea = new TextArea(log.logDetail());
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefSize(520, 280);
        logArea.getStyleClass().add("log-text-area");

        logDialog.getDialogPane().setExpandableContent(logArea);
        logDialog.getDialogPane().setExpanded(true);

        applyAlertStyle(logDialog);
        logDialog.showAndWait();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN DE TABS
    // ═════════════════════════════════════════════════════════════════════════

    @FXML private void onTabGenerate() { switchTab(0); }
    @FXML private void onTabSaved()    { switchTab(1); }
    @FXML private void onTabSchedule() { switchTab(2); }

    private void switchTab(int index) {
        if (index == activeTabIndex) return;
        activeTabIndex = index;

        for (int i = 0; i < allTabs.size(); i++) {
            boolean active = (i == index);
            allTabs.get(i).getStyleClass().removeAll("tab-active");
            if (active) allTabs.get(i).getStyleClass().add("tab-active");
            allSections.get(i).setVisible(active);
            allSections.get(i).setManaged(active);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ═════════════════════════════════════════════════════════════════════════

    private ImageView loadIcon(String path, int width, int height) {
        ImageView iv = new ImageView();
        iv.setFitWidth(width);
        iv.setFitHeight(height);
        iv.setPreserveRatio(true);
        try {
            var stream = getClass().getResourceAsStream(path);
            if (stream != null) iv.setImage(new Image(stream));
        } catch (Exception e) {
            System.err.println("[ICON] No se pudo cargar: " + path);
        }
        return iv;
    }

    private void applyAlertStyle(Dialog<?> dialog) {
        try {
            String cssPath = Objects.requireNonNull(
                    getClass().getResource("/styles/reports.css")
            ).toExternalForm();
            dialog.getDialogPane().getStylesheets().add(cssPath);
            dialog.getDialogPane().getStyleClass().add("custom-alert");
        } catch (Exception e) {
            System.err.println("[Error] CSS del alert no encontrado: " + e.getMessage());
        }

        var scene = dialog.getDialogPane().getScene();
        if (scene != null) {
            scene.windowProperty().addListener((obs, oldWin, newWin) -> {
                if (newWin instanceof javafx.stage.Stage dialogStage) injectIcon(dialogStage);
            });
            if (scene.getWindow() instanceof javafx.stage.Stage stage) injectIcon(stage);
        }
    }

    private void injectIcon(javafx.stage.Stage stage) {
        try {
            var iconStream = getClass().getResourceAsStream("/images/app-icon.png");
            if (iconStream == null) return;
            stage.getIcons().add(new Image(iconStream));
        } catch (Exception e) {
            System.err.println("[Error] No se pudo inyectar el \u00EDcono: " + e.getMessage());
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s == null ? "" : s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MOCK fallback — sólo para tipos de reporte mientras carga la API
    // ═════════════════════════════════════════════════════════════════════════

    private List<ReportTypeDTO> getMockReportTypes() {
        return List.of(
                new ReportTypeDTO("ventas", "Reporte de Ventas",
                        "Ventas agrupadas por período.", "/images/reports/report-predictive.png"),
                new ReportTypeDTO("compras", "Reporte de Compras",
                        "Compras agrupadas por período.", "/images/reports/report-profit.png"),
                new ReportTypeDTO("rentabilidad", "Rentabilidad",
                        "Análisis de rentabilidad mensual.", "/images/reports/report-profit.png"),
                new ReportTypeDTO("productos", "Top Productos",
                        "Productos más vendidos del período.", "/images/reports/report-predictive.png")
        );
    }
}
