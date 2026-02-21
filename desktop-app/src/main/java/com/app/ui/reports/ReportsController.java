package com.app.ui.reports;

import com.app.model.reports.ExecutionLogDTO;
import com.app.model.reports.ReportDTO;
import com.app.model.reports.ReportTypeDTO;
import com.app.model.reports.ScheduledReportDTO;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
    private String          selectedFormat    = "PDF";     // PDF | EXCEL
    private ReportTypeDTO   selectedReportType = null;
    private List<ReportTypeDTO> cachedReportTypes;

    // ── Servicio ──────────────────────────────────────────────────────────────
    private final ReportsService reportsService = new ReportsService();

    // Lista observable para filtrado en tiempo real
    private ObservableList<ReportDTO>   allSavedReports;
    private FilteredList<ReportDTO>     filteredReports;

    private static final DateTimeFormatter FMT_DISPLAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "MX"));
    private static final DateTimeFormatter FMT_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", new Locale("es", "MX"));

    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        allTabs     = List.of(tabGenerate, tabSaved, tabSchedule);
        allSections = List.of(sectionGenerate, sectionSaved, sectionSchedule);

        setAllTexts(); // renombrar el bloque de textos a método propio

        // Cargar tipos de reporte desde la API
        cachedReportTypes = getMockReportTypes(); // fallback mientras carga
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

        // El resto se difiere al siguiente pulso del hilo UI
        Platform.runLater(() -> {
            loadSavedReports(getMockSavedReports());
            loadScheduledReports(getMockScheduledReports());
            loadExecutionHistory(getMockExecutionHistory());
        });
    }

    private void setAllTexts() {
        // ── Tabs ──────────────────────────────────────────────────────────────────
        tabGenerate.setText("Generar Reporte");
        tabSaved.setText("Reportes Guardados");
        tabSchedule.setText("Programaci\u00F3n");

        // ── Sección: Generar Reporte ──────────────────────────────────────────────
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

        // ── Sección: Reportes Guardados ───────────────────────────────────────────
        txtSearch.setPromptText("\uD83D\uDD0D  Buscar reporte por nombre, tipo o usuario...");
        lblSavedEmpty.setText("No se encontraron reportes.\nGenera tu primer reporte en la pesta\u00F1a anterior.");

        // ── Sección: Programación ─────────────────────────────────────────────────
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

        // Icono
        ImageView icon = loadIcon(type.iconPath(), 32, 32);
        icon.getStyleClass().add("report-type-icon");

        // Textos
        VBox texts = new VBox(3);
        Label name = new Label(type.name());
        name.getStyleClass().add("report-type-name");
        Label desc = new Label(type.description());
        desc.getStyleClass().add("report-type-desc");
        desc.setWrapText(true);
        texts.getChildren().addAll(name, desc);
        HBox.setHgrow(texts, Priority.ALWAYS);

        card.getChildren().addAll(icon, texts);

        // Click: selecciona este tipo
        card.setOnMouseClicked(e -> onReportTypeSelected(type, card));

        return card;
    }

    private void onReportTypeSelected(ReportTypeDTO type, HBox selectedCard) {
        // Limpiar selección anterior
        reportTypeList.getChildren().forEach(n ->
                n.getStyleClass().remove("report-type-card-selected"));

        // Marcar la nueva selección
        selectedCard.getStyleClass().add("report-type-card-selected");
        selectedReportType = type;

        // Mostrar el formulario de configuración
        configEmptyState.setVisible(false);
        configEmptyState.setManaged(false);
        configForm.setVisible(true);
        configForm.setManaged(true);

        // Actualizar textos de configuración con el tipo seleccionado
        lblConfigTitle.setText("Configuraci\u00F3n: " + type.name());
        txtReportName.setText(type.name() + " - " +
                DateTimeFormatter.ofPattern("dd/MM/yyyy").format(LocalDate.now()));

        // Cargar sub-tipos según el tipo seleccionado
        // TODO: reemplazar por subTypeService.findByReportType(type.id())
        loadSubTypes(type.id());
    }

    /**
     * Carga los sub-tipos disponibles para el tipo de reporte seleccionado.
     * TODO: reemplazar por consulta a BD: subTypeService.findByReportType(typeId)
     */
    private void loadSubTypes(String typeId) {
        cmbSubType.getItems().clear();

        List<String> subTypes = switch (typeId) {
            case "ventas" -> List.of("Agrupado por d\u00EDa", "Agrupado por semana", "Agrupado por mes");
            case "compras" -> List.of("Agrupado por d\u00EDa", "Agrupado por semana", "Agrupado por mes");
            case "rentabilidad" -> List.of("Rentabilidad mensual");
            case "productos" -> List.of("Top 10 productos", "Top 20 productos", "Top 50 productos");
            // Compatibilidad con IDs legacy del mock
            case "PREDICTIVE" -> List.of("Proyecci\u00F3n de ventas", "Predicci\u00F3n de demanda");
            case "PROFIT" -> List.of("Rentabilidad general", "Rentabilidad por producto");
            default -> List.of("Reporte completo");
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
        // Validaciones
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

        // Estado de carga
        btnGenerate.setDisable(true);
        showStatus("Generando reporte...", true);

        String apiFormato = selectedFormat.equals("EXCEL") ? "excel" : "json";
        String apiTipo    = selectedReportType.id(); // ya viene en formato API ("ventas", etc.)

        reportsService.generateReport(
                apiTipo,
                dpFrom.getValue(),
                dpTo.getValue(),
                apiFormato,
                "dia",
                20
        ).thenAccept(ok -> Platform.runLater(() -> {
            btnGenerate.setDisable(false);
            if (ok) {
                showStatus("\u2714 Reporte generado exitosamente.", true);
            } else {
                showStatus("\u2716 Error al generar el reporte. Intenta de nuevo.", false);
            }
        }));
    }

    private void showStatus(String message, boolean isSuccess) {
        lblGenerateStatus.setText(message);
        lblGenerateStatus.getStyleClass().removeAll("status-success", "status-error");
        lblGenerateStatus.getStyleClass().add(isSuccess ? "status-success" : "status-error");
        lblGenerateStatus.setVisible(true);
        lblGenerateStatus.setManaged(true);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  REPORTES GUARDADOS — con buscador
    // ═════════════════════════════════════════════════════════════════════════

    private void loadSavedReports(List<ReportDTO> reports) {
        allSavedReports = FXCollections.observableArrayList(reports);
        filteredReports = new FilteredList<>(allSavedReports, p -> true);

        // Listener del buscador: filtra en tiempo real
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

        // Renderizado inicial
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

        // ── Icono de formato ─────────────────────────────────────────────────
        String iconPath = report.format().equals("PDF")
                ? "/images/reports/format-pdf.png"
                : "/images/reports/format-excel.png";
        ImageView formatIcon = loadIcon(iconPath, 32, 32);

        // ── Nombre + badge "Nuevo" ────────────────────────────────────────────
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

        // ── Meta: tipo y fecha ────────────────────────────────────────────────
        Label lblMeta = new Label(report.typeName() + "  ·  " +
                report.createdAt().format(FMT_DISPLAY));
        lblMeta.getStyleClass().add("saved-report-meta");

        // ── Usuario generó ────────────────────────────────────────────────────
        Label lblUser = new Label("\uD83D\uDC64  " + report.generatedBy());
        lblUser.getStyleClass().add("saved-report-user");

        VBox info = new VBox(3, nameRow, lblMeta, lblUser);
        HBox.setHgrow(info, Priority.ALWAYS);

        // ── Tamaño ────────────────────────────────────────────────────────────
        Label lblSize = new Label(report.sizeKb() + " KB");
        lblSize.getStyleClass().add("saved-report-size");

        // ── Botón descargar ───────────────────────────────────────────────────
        Button btnDownload = new Button();
        btnDownload.getStyleClass().add("icon-btn");
        btnDownload.setGraphic(loadIcon("/images/reports/icon-download.png", 18, 18));
        btnDownload.setTooltip(new Tooltip("Descargar"));
        // TODO: reemplazar por reportService.download(report.filePath())
        btnDownload.setOnAction(e -> onDownloadReport(report));

        row.getChildren().addAll(formatIcon, info, lblSize, btnDownload);
        return row;
    }

    /**
     * Descarga un reporte guardado.
     * TODO: reemplazar por reportService.download(report.filePath())
     *       o abrir FileChooser para elegir destino y copiar el archivo
     */
    private void onDownloadReport(ReportDTO report) {
        System.out.printf("[REPORT] Descargando: id=%d, ruta=%s%n",
                report.id(), report.filePath());
        // Aquí iría la lógica de descarga / abrir con el SO
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

        // ── Toggle de activación ──────────────────────────────────────────────
        AnimatedToggleSwitch toggle = new AnimatedToggleSwitch();
        toggle.setSelected(schedule.active());
        toggle.selectedProperty().addListener((obs, oldVal, active) -> {
            // TODO: scheduledReportService.setActive(schedule.id(), active)
            System.out.printf("[SCHEDULE] id=%d → activo=%s%n", schedule.id(), active);
        });

        // ── Icono del tipo ────────────────────────────────────────────────────
        ReportTypeDTO type = cachedReportTypes.stream()
                .filter(t -> t.id().equals(schedule.reportTypeId()))
                .findFirst().orElse(null);
        ImageView typeIcon = loadIcon(
                type != null ? type.iconPath() : "/images/reports/format-pdf.png", 28, 28);

        // ── Información principal ─────────────────────────────────────────────
        Label lblName = new Label(schedule.name());
        lblName.getStyleClass().add("schedule-name");

        Label lblFreq = new Label("\uD83D\uDD01  " + schedule.frequency()
                + "  ·  " + schedule.scheduledTime()
                + "  ·  " + schedule.format());
        lblFreq.getStyleClass().add("schedule-meta");

        String lastExec = schedule.lastExecution() != null
                ? schedule.lastExecution().format(FMT_DISPLAY)
                : "Sin ejecuciones";
        Label lblLast = new Label("\uD83D\uDD52  \u00DAltima: " + lastExec);
        lblLast.getStyleClass().add("schedule-meta");

        String nextExec = schedule.nextExecution() != null
                ? schedule.nextExecution().format(FMT_DISPLAY)
                : "—";
        Label lblNext = new Label("\u23F0  Pr\u00F3xima: " + nextExec);
        lblNext.getStyleClass().add("schedule-meta");

        VBox info = new VBox(4, lblName, lblFreq, lblLast, lblNext);
        HBox.setHgrow(info, Priority.ALWAYS);

        // ── Botones de acción ─────────────────────────────────────────────────
        Button btnEdit = new Button();
        btnEdit.getStyleClass().add("icon-btn");
        btnEdit.setGraphic(loadIcon("/images/reports/icon-edit.png", 16, 16));
        btnEdit.setTooltip(new Tooltip("Editar programaci\u00F3n"));
        // TODO: abrir modal de edición: scheduleDialog.open(schedule)
        btnEdit.setOnAction(e -> onEditSchedule(schedule));

        Button btnDelete = new Button();
        btnDelete.getStyleClass().addAll("icon-btn", "icon-btn-danger");
        btnDelete.setGraphic(loadIcon("/images/reports/icon-delete.png", 16, 16));
        btnDelete.setTooltip(new Tooltip("Eliminar programaci\u00F3n"));
        // TODO: confirmar y llamar scheduleService.delete(schedule.id())
        btnDelete.setOnAction(e -> onDeleteSchedule(schedule));

        VBox actions = new VBox(6, btnEdit, btnDelete);
        actions.setAlignment(javafx.geometry.Pos.CENTER);

        card.getChildren().addAll(toggle, typeIcon, info, actions);
        return card;
    }

    /**
     * Abre el formulario de edición de una programación existente.
     * TODO: abrir modal/dialog con los datos de la programación precargados
     */
    private void onEditSchedule(ScheduledReportDTO schedule) {
        System.out.printf("[SCHEDULE] Editar id=%d: %s%n", schedule.id(), schedule.name());
    }

    /**
     * Elimina una programación tras confirmación del usuario.
     * TODO: llamar a scheduledReportService.delete(schedule.id()) y recargar la lista
     */
    private void onDeleteSchedule(ScheduledReportDTO schedule) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar programaci\u00F3n");
        confirm.setHeaderText("¿Eliminar \"" + schedule.name() + "\"?");
        confirm.setContentText("Esta acci\u00F3n no se puede deshacer.");

        ButtonType btnYes = new ButtonType("Eliminar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnNo  = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(btnYes, btnNo);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == btnYes) {
                // TODO: scheduledReportService.delete(schedule.id())
                System.out.printf("[SCHEDULE] Eliminado id=%d%n", schedule.id());
                // Recargar la lista (sustituir por carga real)
                loadScheduledReports(getMockScheduledReports().stream()
                        .filter(s -> s.id() != schedule.id()).toList());
            }
        });
    }

    @FXML
    private void onNewSchedule() {
        // TODO: abrir modal/dialog de nueva programación
        // scheduleDialog.openNew()
        System.out.println("[SCHEDULE] Abrir formulario nueva programaci\u00F3n");
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

        // ── Helper interno: crea una columna de ancho fijo garantizado ────────────
        // minWidth = maxWidth fuerza al HBox padre a respetar exactamente ese ancho
        java.util.function.BiFunction<javafx.scene.Node, Double, HBox> fixedCol =
                (node, width) -> {
                    HBox col = new HBox(node);
                    col.setMinWidth(width);
                    col.setMaxWidth(width);
                    col.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    return col;
                };

        // Indicador de estado
        Label statusDot = new Label("●");
        statusDot.getStyleClass().add("status-dot");
        statusDot.getStyleClass().add(switch (log.status()) {
            case "EXITOSO" -> "status-dot-success";
            case "FALLIDO" -> "status-dot-error";
            default        -> "status-dot-warning";
        });
        HBox colDot = fixedCol.apply(statusDot, 20.0);

        // ── Columna 2: nombre (flexible, consume el espacio restante) ─────────────
        Label lblName = new Label(log.scheduledName());
        lblName.getStyleClass().add("history-name");
        lblName.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        lblName.setMaxWidth(Double.MAX_VALUE);   // permite que crezca hasta el límite
        HBox.setHgrow(lblName, Priority.ALWAYS); // toma el espacio disponible

        // ── Columna 3: fecha (fijo 140px) ─────────────────────────────────────────
        Label lblDate = new Label(log.executedAt().format(FMT_DISPLAY));
        lblDate.getStyleClass().add("history-meta");
        HBox colDate = fixedCol.apply(lblDate, 140.0);

        // ── Columna 4: duración (fijo 70px) ───────────────────────────────────────
        Label lblDuration = new Label(log.durationMs() + " ms");
        lblDuration.getStyleClass().add("history-meta");
        HBox colDuration = fixedCol.apply(lblDuration, 70.0);

        // ── Columna 5: estatus con color (fijo 80px) ──────────────────────────────
        Label lblStatus = new Label(log.status());
        lblStatus.getStyleClass().add("history-status-" + log.status().toLowerCase());
        HBox colStatus = fixedCol.apply(lblStatus, 80.0);

        // ── Columna 6: botón "Ver log" (ancho natural, al extremo derecho) ─────────
        Button btnLog = new Button();
        btnLog.getStyleClass().add("icon-btn-sm");
        btnLog.setGraphic(loadIcon("/images/reports/icon-log.png", 14, 14));
        btnLog.setTooltip(new Tooltip("Ver detalle del log"));
        // TODO: abrir modal con log.logDetail()
        btnLog.setOnAction(e -> onViewLog(log));

        row.getChildren().addAll(colDot, lblName, colDate, colDuration, colStatus, btnLog);
        return row;
    }

    /**
     * Muestra el detalle del log de una ejecución.
     * TODO: abrir modal estilizado con log.logDetail() en lugar de un Alert
     */
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

        // Mismo patrón que applyAlertStyle del ProfileController
        applyAlertStyle(logDialog);

        logDialog.showAndWait();
    }

    /**
     * Aplica CSS e ícono de la app a cualquier Alert del módulo de reportes.
     * Mismo patrón que ProfileController.applyAlertStyle().
     */
    private void applyAlertStyle(Alert alert) {
        // 1. CSS
        try {
            String cssPath = Objects.requireNonNull(
                    getClass().getResource("/styles/reports.css")
            ).toExternalForm();
            alert.getDialogPane().getStylesheets().add(cssPath);
            alert.getDialogPane().getStyleClass().add("custom-alert");
        } catch (Exception e) {
            System.err.println("[Error] CSS del alert no encontrado: " + e.getMessage());
        }

        // 2. Ícono — mismo manejo robusto que ya tienes en Profile
        var scene = alert.getDialogPane().getScene();
        if (scene != null) {
            scene.windowProperty().addListener((obs, oldWin, newWin) -> {
                if (newWin instanceof javafx.stage.Stage dialogStage) {
                    injectIcon(dialogStage);
                }
            });
            // Caso: la ventana ya existe cuando se llama al método
            if (scene.getWindow() instanceof javafx.stage.Stage stage) {
                injectIcon(stage);
            }
        }
    }

    private void injectIcon(javafx.stage.Stage stage) {
        try {
            var iconStream = getClass().getResourceAsStream("/images/app-icon.png");
            if (iconStream == null) {
                System.err.println("[Error] app-icon.png no encontrado.");
                return;
            }
            stage.getIcons().add(new Image(iconStream));
        } catch (Exception e) {
            System.err.println("[Error] No se pudo inyectar el ícono: " + e.getMessage());
        }
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

    /**
     * Carga una imagen desde el classpath y devuelve un ImageView configurado.
     * Si la imagen no existe, devuelve un ImageView vacío sin lanzar excepción.
     */
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

    // ═════════════════════════════════════════════════════════════════════════
    //  MOCKS — sustituir por servicios reales
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Tipos de reporte disponibles en el sistema.
     * TODO: sustituir por reportTypeService.findAll()
     */
    private List<ReportTypeDTO> getMockReportTypes() {
        return List.of(
                new ReportTypeDTO(
                        "PREDICTIVE",
                        "Reporte de Predicciones",
                        "Exporta los resultados de los modelos predictivos generados.",
                        "/images/reports/report-predictive.png"
                ),
                new ReportTypeDTO(
                        "PROFIT",
                        "Reporte de Rentabilidad",
                        "An\u00E1lisis completo de rentabilidad por producto o categor\u00EDa.",
                        "/images/reports/report-profit.png"
                )
        );
    }

    /**
     * Reportes ya generados y guardados en el sistema.
     * TODO: sustituir por reportService.findAll() o reportService.findByUser(userId)
     */
    private List<ReportDTO> getMockSavedReports() {
        return List.of(
                new ReportDTO(1L, "Predicciones Q1 2026", "PREDICTIVE",
                        "Reporte de Predicciones", "PDF", "Mateo Alexander",
                        LocalDateTime.now().minusHours(2),
                        "/reports/pred_q1_2026.pdf", 1248L),
                new ReportDTO(2L, "Rentabilidad Enero 2026", "PROFIT",
                        "Reporte de Rentabilidad", "EXCEL", "Mateo Alexander",
                        LocalDateTime.now().minusDays(3),
                        "/reports/rent_ene_2026.xlsx", 2034L),
                new ReportDTO(3L, "Predicciones Dic 2025", "PREDICTIVE",
                        "Reporte de Predicciones", "PDF", "Ana Mart\u00EDnez",
                        LocalDateTime.now().minusDays(45),
                        "/reports/pred_dic_2025.pdf", 987L),
                new ReportDTO(4L, "Rentabilidad General 2025", "PROFIT",
                        "Reporte de Rentabilidad", "PDF", "Mateo Alexander",
                        LocalDateTime.now().minusDays(60),
                        "/reports/rent_gen_2025.pdf", 3512L)
        );
    }

    /**
     * Reportes con programación automática configurada.
     * TODO: sustituir por scheduledReportService.findAll()
     */
    private List<ScheduledReportDTO> getMockScheduledReports() {
        return List.of(
                new ScheduledReportDTO(1L, "Predicciones Semanal",
                        "PREDICTIVE", "Reporte de Predicciones",
                        "Semanal", "08:00", "PDF", true,
                        LocalDateTime.now().minusDays(7),
                        LocalDateTime.now().plusDays(7),
                        "Mateo Alexander"),
                new ScheduledReportDTO(2L, "Rentabilidad Mensual",
                        "PROFIT", "Reporte de Rentabilidad",
                        "Mensual", "06:00", "AMBOS", true,
                        LocalDateTime.now().minusDays(30),
                        LocalDateTime.now().plusDays(1),
                        "Mateo Alexander"),
                new ScheduledReportDTO(3L, "Resumen Trimestral",
                        "PROFIT", "Reporte de Rentabilidad",
                        "Trimestral", "07:00", "PDF", false,
                        LocalDateTime.now().minusDays(90),
                        LocalDateTime.now().plusDays(3),
                        "Ana Mart\u00EDnez")
        );
    }

    /**
     * Historial de las últimas ejecuciones de reportes programados.
     * TODO: sustituir por executionLogService.findRecent(limit = 10)
     */
    private List<ExecutionLogDTO> getMockExecutionHistory() {
        return List.of(
                new ExecutionLogDTO(1L, 1L, "Predicciones Semanal alias alejandror riba sjs",
                        LocalDateTime.now().minusDays(7),
                        "EXITOSO", 3420L,
                        "[2026-02-07 08:00:03] Inicio de generaci\u00F3n\n"
                                + "[2026-02-07 08:00:05] Consultando predicciones...\n"
                                + "[2026-02-07 08:00:06] Aplicando formato PDF\n"
                                + "[2026-02-07 08:00:06] Archivo generado: pred_semanal_0207.pdf\n"
                                + "[2026-02-07 08:00:06] Proceso completado exitosamente."),
                new ExecutionLogDTO(2L, 2L, "Rentabilidad Mensual",
                        LocalDateTime.now().minusDays(30),
                        "EXITOSO", 8210L,
                        "[2026-01-14 06:00:01] Inicio de generaci\u00F3n\n"
                                + "[2026-01-14 06:00:04] Consultando datos de rentabilidad...\n"
                                + "[2026-01-14 06:00:07] Generando Excel y PDF\n"
                                + "[2026-01-14 06:00:09] Proceso completado."),
                new ExecutionLogDTO(3L, 3L, "Resumen Trimestral",
                        LocalDateTime.now().minusDays(90),
                        "FALLIDO", 1200L,
                        "[2025-11-14 07:00:01] Inicio de generaci\u00F3n\n"
                                + "[2025-11-14 07:00:02] ERROR: No se encontraron datos para el per\u00EDodo.\n"
                                + "[2025-11-14 07:00:02] Proceso terminado con error.")
        );
    }
}