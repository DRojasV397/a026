package com.app.ui.alerts;

import com.app.model.alerts.AlertDTO;
import com.app.ui.components.AnimatedToggleSwitch;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class AlertsController {

    // ── Tabs ──────────────────────────────────────────────────────────────────
    @FXML private Button tabActiveAlert;
    @FXML private Button tabHistory;
    @FXML private Button tabConfiguration;

    // ── Secciones ─────────────────────────────────────────────────────────────
    @FXML private ScrollPane sectionActiveAlerts;
    @FXML private ScrollPane sectionHistory;
    @FXML private ScrollPane sectionConfiguration;

    // ── Alertas Activas — barra superior ──────────────────────────────────────
    @FXML private TextField  txtActiveSearch;
    @FXML private Button     btnMarkAllRead;
    @FXML private Button     btnExportActive;

    // ── Alertas Activas — lista ───────────────────────────────────────────────
    @FXML private VBox  activeAlertsList;
    @FXML private VBox  activeEmptyState;
    @FXML private Label lblActiveEmpty;

    // ── Panel lateral — alertas activas ───────────────────────────────────────
    @FXML private Label  lblKpiActive;
    @FXML private Label  lblKpiAttended;
    @FXML private Label  lblKpiResolved;
    @FXML private Label  lblKpiPending;
    @FXML private Canvas canvasDonut;        // Donut chart de criticidad
    @FXML private VBox   barChartContainer;  // Barras por tipo
    @FXML private VBox   quickActionsBox;

    // ── Historial — barra superior ────────────────────────────────────────────
    @FXML private TextField txtHistorySearch;
    @FXML private Button    btnExportHistory;

    // ── Historial — lista ─────────────────────────────────────────────────────
    @FXML private VBox  historyAlertsList;
    @FXML private VBox  historyEmptyState;
    @FXML private Label lblHistoryEmpty;

    // ── Panel lateral — historial (mismos KPI y charts, distintos fx:id) ─────
    @FXML private Label  lblHistKpiActive;
    @FXML private Label  lblHistKpiAttended;
    @FXML private Label  lblHistKpiResolved;
    @FXML private Label  lblHistKpiPending;
    @FXML private Canvas canvasDonutHist;
    @FXML private VBox   barChartContainerHist;

    // ── Configuración ─────────────────────────────────────────────────────────
    @FXML private TextField  txtThresholdSalesDrop;
    @FXML private TextField  txtThresholdAnomaly;
    @FXML private TextField  txtMaxAlerts;
    @FXML private ComboBox<String> cmbConfidenceLevel;
    @FXML private AnimatedToggleSwitch  tglEmailNotif;
    @FXML private AnimatedToggleSwitch  tglDashNotif;
    @FXML private AnimatedToggleSwitch tglAlertsActive;
    @FXML private Button btnSaveConfig;
    @FXML private Button btnResetConfig;
    @FXML private VBox   thresholdRulesList;

    // ── Estado interno ────────────────────────────────────────────────────────
    private List<Button>     allTabs;
    private List<ScrollPane> allSections;
    private int              activeTabIndex = 0;

    private ObservableList<AlertDTO> allActiveAlerts;
    private FilteredList<AlertDTO>   filteredActive;

    private ObservableList<AlertDTO> allHistoryAlerts;
    private FilteredList<AlertDTO>   filteredHistory;

    private boolean historyInitialized = false;
    private boolean configInitialized  = false;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "MX"));

    // Paleta de colores
    private static final String COLOR_CRITICAL   = "#A03C48";
    private static final String COLOR_WARNING    = "#D9A441";
    private static final String COLOR_OPPORTUNITY = "#58BD8B";
    private static final String COLOR_NEUTRAL    = "#6B7280";

    // ═════════════════════════════════════════════════════════════════════════
    //  INICIALIZACIÓN
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        allTabs     = List.of(tabActiveAlert, tabHistory, tabConfiguration);
        allSections = List.of(sectionActiveAlerts, sectionHistory, sectionConfiguration);

        setAllTexts();
        setupSearchListeners();

        // Carga diferida — primer frame visible antes de procesar datos
        Platform.runLater(this::loadActiveSectionData);
    }

    private void loadActiveSectionData() {
        Task<List<AlertDTO>> task = new Task<>() {
            @Override protected List<AlertDTO> call() {
                return getMockActiveAlerts();
            }
            @Override protected void succeeded() {
                allActiveAlerts = FXCollections.observableArrayList(getValue());
                filteredActive  = new FilteredList<>(allActiveAlerts, p -> true);
                renderActiveAlerts();
                updateSidebarKpis(allActiveAlerts,
                        lblKpiActive, lblKpiAttended, lblKpiResolved, lblKpiPending);
                Platform.runLater(() -> {
                    drawDonutChart(canvasDonut, allActiveAlerts);
                    buildBarChart(barChartContainer, allActiveAlerts);
                    buildQuickActions();
                });
            }
            @Override protected void failed() {
                System.err.println("[ALERTS] Error cargando alertas activas: " + getException().getMessage());
            }
        };
        new Thread(task).start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEXTOS
    // ═════════════════════════════════════════════════════════════════════════

    private void setAllTexts() {
        tabActiveAlert.setText("Alertas activas");
        tabHistory.setText("Consultar hist\u00F3rico");
        tabConfiguration.setText("Configuraci\u00F3n");

        if (txtActiveSearch  != null) txtActiveSearch.setPromptText("\uD83D\uDD0D  Buscar alerta...");
        if (txtHistorySearch != null) txtHistorySearch.setPromptText("\uD83D\uDD0D  Buscar en el hist\u00F3rico...");
        if (btnMarkAllRead   != null) btnMarkAllRead.setText("Marcar todas como le\u00EDdas");
        if (btnExportActive  != null) btnExportActive.setText("Exportar");
        if (btnExportHistory != null) btnExportHistory.setText("Exportar");
        if (lblActiveEmpty   != null) lblActiveEmpty.setText("No hay alertas activas en este momento.");
        if (lblHistoryEmpty  != null) lblHistoryEmpty.setText("No se encontraron alertas en el hist\u00F3rico.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  BÚSQUEDA EN TIEMPO REAL
    // ═════════════════════════════════════════════════════════════════════════

    private void setupSearchListeners() {
        if (txtActiveSearch != null) {
            txtActiveSearch.textProperty().addListener((obs, o, newVal) -> {
                if (filteredActive == null) return;
                String term = newVal == null ? "" : newVal.trim().toLowerCase();
                filteredActive.setPredicate(a -> term.isEmpty()
                        || a.title().toLowerCase().contains(term)
                        || a.description().toLowerCase().contains(term)
                        || a.affectedMetric().toLowerCase().contains(term)
                        || a.type().toLowerCase().contains(term));
                renderActiveAlerts();
            });
        }
        if (txtHistorySearch != null) {
            txtHistorySearch.textProperty().addListener((obs, o, newVal) -> {
                if (filteredHistory == null) return;
                String term = newVal == null ? "" : newVal.trim().toLowerCase();
                filteredHistory.setPredicate(a -> term.isEmpty()
                        || a.title().toLowerCase().contains(term)
                        || a.description().toLowerCase().contains(term)
                        || a.type().toLowerCase().contains(term));
                renderHistoryAlerts();
            });
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  RENDERIZADO — ALERTAS ACTIVAS
    // ═════════════════════════════════════════════════════════════════════════

    private void renderActiveAlerts() {
        List<AlertDTO> visible = filteredActive.stream().toList();
        boolean empty = visible.isEmpty();

        activeEmptyState.setVisible(empty);
        activeEmptyState.setManaged(empty);
        activeAlertsList.setVisible(!empty);
        activeAlertsList.setManaged(!empty);

        List<VBox> rows = new ArrayList<>();
        for (AlertDTO alert : visible)
            rows.add(buildAlertRow(alert, true));

        activeAlertsList.getChildren().clear();
        activeAlertsList.getChildren().addAll(rows);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  RENDERIZADO — HISTORIAL
    // ═════════════════════════════════════════════════════════════════════════

    private void renderHistoryAlerts() {
        List<AlertDTO> visible = filteredHistory.stream().toList();
        boolean empty = visible.isEmpty();

        historyEmptyState.setVisible(empty);
        historyEmptyState.setManaged(empty);
        historyAlertsList.setVisible(!empty);
        historyAlertsList.setManaged(!empty);

        List<VBox> rows = new ArrayList<>();
        for (AlertDTO alert : visible)
            rows.add(buildAlertRow(alert, false));

        historyAlertsList.getChildren().clear();
        historyAlertsList.getChildren().addAll(rows);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  COMPONENTE: FILA DE ALERTA
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Construye una fila de alerta para la lista.
     * @param alert       DTO de la alerta
     * @param showResolve true → muestra botón "Resolver" (solo en Activas)
     */
    private VBox buildAlertRow(AlertDTO alert, boolean showResolve) {
        VBox card = new VBox(0);
        card.getStyleClass().add("alert-row-card");
        if (!alert.read()) card.getStyleClass().add("alert-row-unread");

        HBox content = new HBox(12);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("alert-row-content");

        // ── Franja de color lateral ───────────────────────────────────────────
        VBox stripe = new VBox();
        stripe.getStyleClass().addAll("alert-stripe", alert.colorStyleClass());
        stripe.setMinWidth(5);
        stripe.setMaxWidth(5);

        // ── Icono tipo de alerta ──────────────────────────────────────────────
        ImageView typeIcon = loadIcon(typeIconPath(alert.type()), 26, 26);
        typeIcon.setFitWidth(26);
        typeIcon.setFitHeight(26);

        // ── Cuerpo principal ──────────────────────────────────────────────────
        VBox body = new VBox(4);
        HBox.setHgrow(body, Priority.ALWAYS);

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label(alert.title());
        lblTitle.getStyleClass().add("alert-row-title");

        Label severityBadge = new Label(alert.severityLabel());
        severityBadge.getStyleClass().addAll("alert-severity-badge",
                "badge-" + alert.type().toLowerCase());

        Label typeBadge = new Label(alert.type());
        typeBadge.getStyleClass().addAll("alert-type-badge",
                "badge-" + alert.type().toLowerCase());

        titleRow.getChildren().addAll(lblTitle, severityBadge, typeBadge);

        Label lblDesc = new Label(alert.description());
        lblDesc.getStyleClass().add("alert-row-desc");
        lblDesc.setWrapText(true);

        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label lblMetric = new Label("\uD83D\uDCCA  " + alert.affectedMetric());
        lblMetric.getStyleClass().add("alert-meta");

        Label lblDeviation = new Label(
                String.format("Desviaci\u00F3n: %.1f%%", alert.deviationPercent()));
        lblDeviation.getStyleClass().add("alert-meta");
        lblDeviation.getStyleClass().add(alert.deviationPercent() < 0
                ? "alert-meta-negative" : "alert-meta-positive");

        Label lblConf = new Label(
                String.format("Confianza: %.0f%%", alert.confidenceLevel()));
        lblConf.getStyleClass().add("alert-meta");

        Label lblDate = new Label("\uD83D\uDD52  " + alert.createdAt().format(FMT));
        lblDate.getStyleClass().add("alert-meta");

        metaRow.getChildren().addAll(lblMetric, lblDeviation, lblConf, lblDate);
        body.getChildren().addAll(titleRow, lblDesc, metaRow);

        // Si está resuelta, mostrar quién/cuándo resolvió
        if ("RESUELTA".equals(alert.status()) && alert.resolvedAt() != null) {
            Label lblResolved = new Label("\u2705  Resuelta por " + alert.resolvedBy()
                    + "  ·  " + alert.resolvedAt().format(FMT));
            lblResolved.getStyleClass().add("alert-resolved-meta");
            body.getChildren().add(lblResolved);
        }

        // ── Botones de acción ─────────────────────────────────────────────────
        VBox actions = new VBox(6);
        actions.setAlignment(Pos.CENTER);
        actions.setMinWidth(100);

        Button btnDetail = new Button("Ver detalles");
        btnDetail.getStyleClass().add("btn-alert-detail");
        btnDetail.setOnAction(e -> onViewAlertDetail(alert));

        actions.getChildren().add(btnDetail);

        if (showResolve) {
            Button btnResolve = new Button("Resolver");
            btnResolve.getStyleClass().add("btn-alert-resolve");
            btnResolve.setOnAction(e -> onResolveAlert(alert));
            actions.getChildren().add(btnResolve);
        }

        content.getChildren().addAll(typeIcon, body, actions);

        // Ensamblar stripe + contenido
        HBox outer = new HBox(0, stripe, content);
        HBox.setHgrow(content, Priority.ALWAYS);
        card.getChildren().add(outer);

        return card;
    }

    private String typeIconPath(String type) {
        return switch (type) {
            case "RIESGO"      -> "/images/alerts/icon-risk.png";
            case "OPORTUNIDAD" -> "/images/alerts/icon-opportunity.png";
            default            -> "/images/alerts/icon-warning.png";
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PANEL LATERAL — KPI CARDS
    // ═════════════════════════════════════════════════════════════════════════

    private void updateSidebarKpis(List<AlertDTO> alerts,
                                   Label kpiActive, Label kpiAttended,
                                   Label kpiResolved, Label kpiPending) {
        if (kpiActive   == null) return;
        long active    = alerts.stream().filter(a -> "ACTIVA".equals(a.status())).count();
        long attended  = alerts.stream().filter(a -> "LEIDA".equals(a.status())).count();
        long resolved  = alerts.stream().filter(a -> "RESUELTA".equals(a.status())).count();
        long pending   = alerts.stream().filter(a -> !a.read()).count();

        kpiActive.setText(String.valueOf(active));
        kpiAttended.setText(String.valueOf(attended));
        kpiResolved.setText(String.valueOf(resolved));
        kpiPending.setText(String.valueOf(pending));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PANEL LATERAL — DONUT CHART (Canvas JavaFX, sin librería)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Dibuja un donut chart de distribución por criticidad usando Canvas.
     * Componente reutilizable: acepta cualquier lista de AlertDTO.
     */
    private void drawDonutChart(Canvas canvas, List<AlertDTO> alerts) {
        if (canvas == null || alerts.isEmpty()) return;

        // Agrupa por severidad/tipo
        Map<String, Long> counts = alerts.stream().collect(
                Collectors.groupingBy(a -> {
                    if ("OPORTUNIDAD".equals(a.type())) return "Oportunidad";
                    return switch (a.severity()) {
                        case "CRITICA" -> "Cr\u00EDtica";
                        case "ALTA"    -> "Alta";
                        case "MEDIA"   -> "Media";
                        default        -> "Baja";
                    };
                }, Collectors.counting())
        );

        List<String> labels = new ArrayList<>(counts.keySet());
        List<Long>   values = labels.stream().map(counts::get).toList();
        List<String> colors = labels.stream().map(l -> switch (l) {
            case "Cr\u00EDtica" -> COLOR_CRITICAL;
            case "Alta"         -> "#C0525E";
            case "Media"        -> COLOR_WARNING;
            case "Baja"         -> "#6B9EBF";
            default             -> COLOR_OPPORTUNITY;
        }).toList();

        double total = values.stream().mapToLong(Long::longValue).sum();
        double w     = canvas.getWidth();
        double cx    = w / 2;

        // outer basado en ancho para reservar espacio vertical a la leyenda
        double outer = w * 0.36;
        double inner = outer * 0.58;

        // cy = margen superior + radio → el donut queda en la mitad superior
        double cy = outer + 8;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, canvas.getHeight());

        // Arcos del donut
        double startAngle = -90;
        for (int i = 0; i < values.size(); i++) {
            double sweep = (values.get(i) / total) * 360.0;
            gc.setFill(Color.web(colors.get(i)));
            gc.fillArc(cx - outer, cy - outer, outer * 2, outer * 2,
                    startAngle, sweep, javafx.scene.shape.ArcType.ROUND);
            startAngle += sweep;
        }

        // Agujero interior — mismo color que el fondo del sidebar-card
        gc.setFill(Color.web("#FFFFFF"));
        gc.fillOval(cx - inner, cy - inner, inner * 2, inner * 2);

        // Número y etiqueta en el centro
        gc.setFill(Color.web("#1E293B"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 26));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(String.valueOf((long) total), cx, cy + 6);
        gc.setFont(Font.font("System", FontWeight.NORMAL, 13));
        gc.setFill(Color.web("#667B99"));
        gc.fillText("alertas", cx, cy + 18);

        gc.setFont(Font.font("System", FontWeight.NORMAL, 13));
        gc.setTextAlign(TextAlignment.LEFT);

        // Separador + respiro
        double separatorY = cy + outer + 18;
        gc.setStroke(Color.web("#E2E8F0"));
        gc.setLineWidth(0.8);
        gc.strokeLine(8, separatorY, w - 8, separatorY);

        double legendStartY = separatorY + 10;  // 10px debajo del separador
        double rowH         = 17;
        double dotSize      = 10;

        gc.setFont(Font.font("System", FontWeight.NORMAL, 10));
        gc.setTextAlign(TextAlignment.LEFT);

        for (int i = 0; i < labels.size(); i++) {
            double rowY = legendStartY + (i * rowH);
            gc.setFill(Color.web(colors.get(i)));
            gc.fillRoundRect(8, rowY, dotSize, dotSize, 3, 3);
            gc.setFill(Color.web("#475569"));
            double pct  = (values.get(i) / total) * 100;
            String line = String.format("%s  -  %d  -  %.0f%%",
                    labels.get(i), values.get(i), pct);
            gc.fillText(line, 22, rowY + dotSize);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PANEL LATERAL — BAR CHART por tipo
    // ═════════════════════════════════════════════════════════════════════════

    private void buildBarChart(VBox container, List<AlertDTO> alerts) {
        if (container == null) return;

        Map<String, Long> byType = alerts.stream()
                .collect(Collectors.groupingBy(AlertDTO::type, Collectors.counting()));

        long maxCount = byType.values().stream().mapToLong(Long::longValue).max().orElse(1);

        List<String[]> typeEntries = List.of(
                new String[]{ "RIESGO",      "Riesgo",      COLOR_CRITICAL },
                new String[]{ "ADVERTENCIA", "Advertencia", COLOR_WARNING },
                new String[]{ "OPORTUNIDAD", "Oportunidad", COLOR_OPPORTUNITY }
        );

        // Anchos fijos conocidos: label=78, count=24, spacing HBox=8*2=16 → reservado=118
        final double RESERVED = 118;

        List<javafx.scene.Node> rows = new ArrayList<>();

        for (String[] entry : typeEntries) {
            long count = byType.getOrDefault(entry[0], 0L);
            double ratio = maxCount == 0 ? 0 : (double) count / maxCount;

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("bar-row");

            Label lblType = new Label(entry[1]);
            lblType.getStyleClass().add("bar-label");
            lblType.setMinWidth(78);
            lblType.setMaxWidth(78);

            // Track contenedor de la barra
            StackPane track = new StackPane();
            track.getStyleClass().add("bar-track");
            HBox.setHgrow(track, Priority.ALWAYS);
            track.setMinHeight(10);
            track.setMaxHeight(10);

            // Barra de relleno
            Region fill = new Region();
            fill.setStyle("-fx-background-color: " + entry[2] + "; -fx-background-radius: 5;");
            fill.setMinHeight(10);
            fill.setMaxHeight(10);
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);
            track.getChildren().add(fill);

            Label lblCount = new Label(String.valueOf(count));
            lblCount.getStyleClass().add("bar-count");
            lblCount.setMinWidth(24);
            lblCount.setMaxWidth(24);

            row.getChildren().addAll(lblType, track, lblCount);
            rows.add(row);

            // Bind al ancho del track cuando ya esté disponible
            track.widthProperty().addListener((obs, oldW, newW) -> {
                if (newW.doubleValue() > 0) {
                    fill.setPrefWidth(newW.doubleValue() * ratio);
                }
            });

            // Por si el track ya tiene ancho al momento de agregar (re-renders)
            if (track.getWidth() > 0) {
                fill.setPrefWidth(track.getWidth() * ratio);
            }
        }

        container.getChildren().clear();
        container.getChildren().addAll(rows);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PANEL LATERAL — ACCIONES RÁPIDAS
    // ═════════════════════════════════════════════════════════════════════════

    private void buildQuickActions() {
        if (quickActionsBox == null) return;

        record QuickAction(String label, String icon, Runnable action) {}
        List<QuickAction> actions = List.of(
                new QuickAction("Generar reporte",        "\uD83D\uDCC4", this::onQuickGenerateReport),
                new QuickAction("Exportar alertas",       "\uD83D\uDCE5", this::onQuickExport),
                new QuickAction("Silenciar temporalmente","\uD83D\uDD15", this::onQuickMute),
                new QuickAction("Ir a configuraci\u00F3n","\u2699\uFE0F",  () -> switchTab(2))
        );

        List<javafx.scene.Node> btns = new ArrayList<>();
        for (QuickAction qa : actions) {
            Button btn = new Button(qa.icon() + "  " + qa.label());
            btn.getStyleClass().add("btn-quick-action");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> qa.action().run());
            btns.add(btn);
        }
        quickActionsBox.getChildren().clear();
        quickActionsBox.getChildren().addAll(btns);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ACCIONES — ALERTAS ACTIVAS
    // ═════════════════════════════════════════════════════════════════════════

    private void onViewAlertDetail(AlertDTO alert) {
        System.out.printf("[ALERTS] Ver detalle → id=%d, tipo=%s, titulo='%s'%n",
                alert.id(), alert.type(), alert.title());
        // TODO: abrir modal de detalle con valores actual/esperado, historial, etc.
    }

    private void onResolveAlert(AlertDTO alert) {
        System.out.printf("[ALERTS] Resolver alerta → id=%d, titulo='%s'%n",
                alert.id(), alert.title());
        // TODO: alertService.resolve(alert.id(), currentUser)
        //       luego recargar la lista activa
    }

    @FXML
    private void onMarkAllRead() {
        System.out.println("[ALERTS] Marcar todas como le\u00EDdas");
        // TODO: alertService.markAllRead(currentUser)
        //       luego recargar allActiveAlerts con read=true
    }

    @FXML
    private void onExportActive() {
        System.out.println("[ALERTS] Exportar alertas activas");
        // TODO: exportService.export(filteredActive, formato)
    }

    @FXML
    private void onExportHistory() {
        System.out.println("[ALERTS] Exportar hist\u00F3rico de alertas");
        // TODO: exportService.export(filteredHistory, formato)
    }

    // ── Acciones rápidas ──────────────────────────────────────────────────────
    private void onQuickGenerateReport() {
        System.out.println("[ALERTS] Acci\u00F3n r\u00E1pida: Generar reporte de alertas");
        // TODO: navegar a módulo de reportes con filtro de alertas preseleccionado
    }
    private void onQuickExport() {
        System.out.println("[ALERTS] Acci\u00F3n r\u00E1pida: Exportar todas las alertas activas");
    }
    private void onQuickMute() {
        System.out.println("[ALERTS] Acci\u00F3n r\u00E1pida: Silenciar alertas temporalmente");
        // TODO: alertService.muteAll(durationMinutes = 30)
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SECCIÓN HISTORIAL — carga lazy
    // ═════════════════════════════════════════════════════════════════════════

    private void initHistoryIfNeeded() {
        if (historyInitialized) return;
        historyInitialized = true;

        Task<List<AlertDTO>> task = new Task<>() {
            @Override protected List<AlertDTO> call() { return getMockHistoryAlerts(); }
            @Override protected void succeeded() {
                allHistoryAlerts = FXCollections.observableArrayList(getValue());
                filteredHistory  = new FilteredList<>(allHistoryAlerts, p -> true);
                renderHistoryAlerts();
                updateSidebarKpis(allHistoryAlerts,
                        lblHistKpiActive, lblHistKpiAttended,
                        lblHistKpiResolved, lblHistKpiPending);
                Platform.runLater(() -> {
                    drawDonutChart(canvasDonutHist, allHistoryAlerts);
                    buildBarChart(barChartContainerHist, allHistoryAlerts);
                });
            }
            @Override protected void failed() {
                System.err.println("[ALERTS] Error cargando hist\u00F3rico: " + getException().getMessage());
            }
        };
        new Thread(task).start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SECCIÓN CONFIGURACIÓN — carga lazy
    // ═════════════════════════════════════════════════════════════════════════

    private void initConfigIfNeeded() {
        if (configInitialized) return;
        configInitialized = true;

        Platform.runLater(() -> {
            // ── Valores por defecto ───────────────────────────────────────────
            if (txtThresholdSalesDrop != null) txtThresholdSalesDrop.setText("15");
            if (txtThresholdAnomaly  != null) txtThresholdAnomaly.setText("5");
            if (txtMaxAlerts         != null) txtMaxAlerts.setText("10");

            if (cmbConfidenceLevel != null) {
                cmbConfidenceLevel.getItems().addAll("70%", "80%", "85%", "90%", "95%");
                cmbConfidenceLevel.getSelectionModel().select("80%");
            }

            // ── Estado inicial de toggles ─────────────────────────────────────
            if (tglEmailNotif   != null) tglEmailNotif.setSelected(true);
            if (tglDashNotif    != null) tglDashNotif.setSelected(true);
            if (tglAlertsActive != null) tglAlertsActive.setSelected(true);

            // ── Listeners de toggles ──────────────────────────────────────────
            if (tglEmailNotif != null)
                tglEmailNotif.selectedProperty().addListener((obs, oldVal, active) -> {
                    System.out.printf("[CONFIG] Notificación email → %s%n", active ? "activada" : "desactivada");
                    // TODO: configService.setEmailNotif(active)
                });

            if (tglDashNotif != null)
                tglDashNotif.selectedProperty().addListener((obs, oldVal, active) -> {
                    System.out.printf("[CONFIG] Notificación dashboard → %s%n", active ? "activada" : "desactivada");
                    // TODO: configService.setDashboardNotif(active)
                });

            if (tglAlertsActive != null)
                tglAlertsActive.selectedProperty().addListener((obs, oldVal, active) -> {
                    System.out.printf("[CONFIG] Sistema de alertas → %s%n", active ? "activado" : "pausado");
                    // TODO: configService.setAlertsEnabled(active)
                });

            buildThresholdRules();
        });
    }

    /** Construye la lista de reglas de umbral configuradas */
    private void buildThresholdRules() {
        if (thresholdRulesList == null) return;

        record Rule(String metric, String condition, String threshold, boolean active) {}
        List<Rule> rules = List.of(
                new Rule("Ca\u00EDda de ventas",    "Mayor a",      "15%",   true),
                new Rule("Anomal\u00EDa detectada", "Mayor a",      "5%",    true),
                new Rule("Pico de demanda",         "Mayor a",      "30%",   true),
                new Rule("Margen negativo",         "Menor a",      "0%",    true),
                new Rule("Confianza m\u00EDnima",   "Mayor o igual","70%",   false)
        );

        List<HBox> rows = new ArrayList<>();
        for (Rule rule : rules) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("config-rule-row");

            // Indicador activo/inactivo
            Circle dot = new Circle(5);
            dot.getStyleClass().add(rule.active() ? "rule-active-dot" : "rule-inactive-dot");

            Label lblMetric = new Label(rule.metric());
            lblMetric.getStyleClass().add("config-rule-metric");
            HBox.setHgrow(lblMetric, Priority.ALWAYS);

            Label lblCond = new Label(rule.condition() + " " + rule.threshold());
            lblCond.getStyleClass().add("config-rule-condition");

            Button btnEdit = new Button("\u270F\uFE0F");
            btnEdit.getStyleClass().add("btn-config-edit");
            btnEdit.setOnAction(e -> {
                System.out.printf("[CONFIG] Editar regla: '%s' %s %s%n",
                        rule.metric(), rule.condition(), rule.threshold());
                // TODO: abrir modal de edición de regla
            });

            row.getChildren().addAll(dot, lblMetric, lblCond, btnEdit);
            rows.add(row);
        }

        thresholdRulesList.getChildren().clear();
        thresholdRulesList.getChildren().addAll(rows);
    }

    @FXML
    private void onSaveConfig() {
        System.out.printf("[CONFIG] Guardar configuraci\u00F3n → umbralVentas=%s, umbralAnomalia=%s, maxAlertas=%s, confianza=%s%n",
                txtThresholdSalesDrop != null ? txtThresholdSalesDrop.getText() : "N/A",
                txtThresholdAnomaly   != null ? txtThresholdAnomaly.getText()   : "N/A",
                txtMaxAlerts          != null ? txtMaxAlerts.getText()           : "N/A",
                cmbConfidenceLevel    != null ? cmbConfidenceLevel.getValue()   : "N/A");
        // TODO: configService.save(AlertConfigDTO)
    }

    @FXML
    private void onResetConfig() {
        System.out.println("[CONFIG] Restablecer valores por defecto");
        // TODO: configService.resetToDefaults() → luego recargar initConfigIfNeeded()
        configInitialized = false;
        initConfigIfNeeded();
    }

    @FXML
    private void onAddRule() {
        System.out.println("[CONFIG] Agregar nueva regla de umbral");
        // TODO: abrir modal de creación de regla
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN DE TABS
    // ═════════════════════════════════════════════════════════════════════════

    @FXML public void onTabActiveAlert()    { switchTab(0); }
    @FXML public void onTabHistory()        { switchTab(1); }
    @FXML public void onTabConfiguration() { switchTab(2); }

    private void switchTab(int index) {
        if (index == activeTabIndex) return;
        activeTabIndex = index;

        for (int i = 0; i < allTabs.size(); i++) {
            boolean active = (i == index);
            allTabs.get(i).getStyleClass().removeAll("alrt-tab-active");
            if (active) allTabs.get(i).getStyleClass().add("alrt-tab-active");
            allSections.get(i).setVisible(active);
            allSections.get(i).setManaged(active);
        }

        switch (index) {
            case 1 -> initHistoryIfNeeded();
            case 2 -> initConfigIfNeeded();
        }
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

    // ═════════════════════════════════════════════════════════════════════════
    //  MOCKS — sustituir por servicios reales
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * TODO: sustituir por alertService.findActive()
     */
    private List<AlertDTO> getMockActiveAlerts() {
        return List.of(
                new AlertDTO(1L, "RIESGO", "CRITICA", null,
                        "Ca\u00EDda cr\u00EDtica en ventas",
                        "Las ventas del canal online cayeron un 22% respecto a la semana anterior, superando el umbral del 15%.",
                        "Ventas canal online", 78000, 100000, -22.0, 92.0,
                        "ACTIVA", LocalDateTime.now().minusHours(2), null, null, false),

                new AlertDTO(2L, "RIESGO", "ALTA", null,
                        "Anomal\u00EDa en datos de compras",
                        "Se detectaron 47 registros at\u00EDpicos en el \u00FAltimo lote importado de compras.",
                        "Integridad de datos", 47, 0, 8.5, 87.0,
                        "ACTIVA", LocalDateTime.now().minusHours(5), null, null, false),

                new AlertDTO(3L, "OPORTUNIDAD", null, "ALTO",
                        "Incremento de demanda en Categor\u00EDa A",
                        "La demanda de Categor\u00EDa A muestra una tendencia positiva del 34% sobre la proyecci\u00F3n.",
                        "Demanda Categor\u00EDa A", 134, 100, 34.0, 88.0,
                        "ACTIVA", LocalDateTime.now().minusHours(8), null, null, true),

                new AlertDTO(4L, "ADVERTENCIA", "MEDIA", null,
                        "Margen de utilidad por debajo del objetivo",
                        "El margen de utilidad neto del mes se ubica en 12.3%, por debajo del objetivo del 15%.",
                        "Margen de utilidad", 12.3, 15.0, -2.7, 95.0,
                        "ACTIVA", LocalDateTime.now().minusDays(1), null, null, true),

                new AlertDTO(5L, "OPORTUNIDAD", null, "MEDIO",
                        "Segmento B con mayor retenci\u00F3n de clientes",
                        "El Segmento B muestra una tasa de retenci\u00F3n 18% por encima del promedio hist\u00F3rico.",
                        "Retenci\u00F3n Segmento B", 78, 66, 18.0, 82.0,
                        "ACTIVA", LocalDateTime.now().minusDays(1), null, null, false)
        );
    }

    /**
     * TODO: sustituir por alertService.findHistory(filtros)
     */
    private List<AlertDTO> getMockHistoryAlerts() {
        List<AlertDTO> history = new ArrayList<>(getMockActiveAlerts());
        history.addAll(List.of(
                new AlertDTO(10L, "RIESGO", "ALTA", null,
                        "Interrupci\u00F3n de carga de datos",
                        "El proceso de carga autom\u00E1tica fall\u00F3 durante 3 horas consecutivas.",
                        "Pipeline de datos", 0, 1, -100.0, 99.0,
                        "RESUELTA", LocalDateTime.now().minusDays(5),
                        LocalDateTime.now().minusDays(5).plusHours(3),
                        "Mateo Alexander", true),

                new AlertDTO(11L, "OPORTUNIDAD", null, "ALTO",
                        "M\u00E1ximo hist\u00F3rico de ventas Q4",
                        "El total de ventas del Q4 2025 alcanz\u00F3 un m\u00E1ximo hist\u00F3rico.",
                        "Ventas Q4", 2400000, 2000000, 20.0, 91.0,
                        "RESUELTA", LocalDateTime.now().minusDays(45),
                        LocalDateTime.now().minusDays(44),
                        "Ana Mart\u00EDnez", true),

                new AlertDTO(12L, "ADVERTENCIA", "BAJA", null,
                        "Inconsistencia menor en fechas",
                        "Se detectaron 3 registros con fechas futuras en el archivo importado.",
                        "Validaci\u00F3n de datos", 3, 0, 0.2, 75.0,
                        "RESUELTA", LocalDateTime.now().minusDays(10),
                        LocalDateTime.now().minusDays(10).plusHours(1),
                        "Mateo Alexander", true)
        ));
        return history;
    }
}