package com.app.ui.home;

import com.app.model.Alert;
import com.app.model.ListItem;
import com.app.model.StatsDTO;
import com.app.model.dashboard.ExecutiveDashboardDTO;
import com.app.service.alerts.AlertService;
import com.app.service.dashboard.DashboardService;
import com.app.ui.components.alerts.AlertCardController;
import com.app.ui.components.charts.*;
import com.app.ui.components.lists.VerticalListCardController;
import com.app.ui.components.cards.StatsCard;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controlador principal del Dashboard/Home
 * Gestiona la carga de datos en todos los charts y alertas
 * OPTIMIZADO: Usa lazy loading para mejorar performance
 */
public class HomeController {

    // Referencias a los controladores de charts iniciales
    @FXML
    private LineChartCardController salesTrendChartController;

    @FXML
    private BarChartCardController regionSalesChartController;

    // Referencia al controller de la lista
    @FXML
    private VerticalListCardController topProductsListController;

    // Contenedor de alertas
    @FXML
    private HBox alertsContainer;

    @FXML
    private ScrollPane dashboardRoot;

    @FXML
    private VBox dashboardContainer;

    @FXML private HBox statsCardsContainer;

    // Botones de selección de período
    @FXML private ToggleButton btn1S;
    @FXML private ToggleButton btn2S;
    @FXML private ToggleButton btn1M;
    @FXML private ToggleButton btn3M;
    @FXML private ToggleButton btn6M;
    @FXML private ToggleButton btn1A;

    // Charts adicionales — sección "Tendencias Detalladas"
    @FXML private VBox chartsContainer;
    private PieChartCardController productDistributionChartController;
    private AreaChartCardController revenueExpenseChartController;
    private boolean additionalChartsLoaded = false;

    // Charts de módulos — sección "Análisis por Módulo"
    @FXML private VBox modulesChartsContainer;
    private BarChartCardController rentabilidadChartController;
    private PieChartCardController alertasChartController;
    private BarChartCardController modelosChartController;
    private boolean moduleChartsLoaded = false;

    // Datos del dashboard ejecutivo cargado (para uso diferido en lazy charts)
    private ExecutiveDashboardDTO dashboardDto;

    // Servicios
    private final AlertService alertService = new AlertService();
    private final DashboardService dashboardService = new DashboardService();

    @FXML
    private void initialize() {
        loadAlertsCSS();

        dashboardContainer.minHeightProperty().bind(dashboardRoot.heightProperty());

        // Configurar ToggleGroup para los botones de período
        ToggleGroup periodGroup = new ToggleGroup();
        btn1S.setToggleGroup(periodGroup);
        btn2S.setToggleGroup(periodGroup);
        btn1M.setToggleGroup(periodGroup);
        btn3M.setToggleGroup(periodGroup);
        btn6M.setToggleGroup(periodGroup);
        btn1A.setToggleGroup(periodGroup);
        btn1M.setSelected(true); // Último mes como default

        // Evitar deselección (siempre debe haber uno seleccionado)
        periodGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) periodGroup.selectToggle(oldVal);
        });

        loadDashboardForPeriod(30);
    }

    /**
     * Carga (o recarga) el dashboard para el rango de días dado desde hoy.
     */
    private void loadDashboardForPeriod(int days) {
        LocalDate fin = LocalDate.now();
        LocalDate inicio = fin.minusDays(days);

        dashboardService.getExecutiveDashboard(inicio, fin).thenAccept(dto -> Platform.runLater(() -> {
            if (dto != null) {
                this.dashboardDto = dto;
                loadStatsFromDashboard(dto);
                loadTopProductsFromDashboard(dto);
                loadAlertsFromDashboard(dto);
                loadChartsFromDashboard(dto);
                if (additionalChartsLoaded) refreshLazyCharts();
                if (moduleChartsLoaded)    populateModuleCharts();
            } else {
                loadStatsCards();
                loadAlerts();
                loadInitialCharts();
            }
        })).exceptionally(ex -> {
            System.err.println("[HOME] Error al cargar dashboard: " + ex.getMessage());
            Platform.runLater(() -> { loadStatsCards(); loadAlerts(); loadInitialCharts(); });
            return null;
        });
    }

    /** Maneja el clic en cualquier botón de período. */
    @FXML
    private void onPeriodChanged(ActionEvent event) {
        ToggleButton btn = (ToggleButton) event.getSource();
        String userData = (String) btn.getUserData();
        if (userData != null) {
            loadDashboardForPeriod(Integer.parseInt(userData));
        }
    }

    /** Refresca los charts lazy (PieChart y AreaChart) cuando ya están visibles. */
    private void refreshLazyCharts() {
        if (productDistributionChartController != null && dashboardDto != null
                && !dashboardDto.getVentasPorCategoria().isEmpty()) {
            ObservableList<PieChart.Data> pie = FXCollections.observableArrayList();
            dashboardDto.getVentasPorCategoria().forEach(c ->
                pie.add(new PieChart.Data(c.getCategoria(), c.getTotal())));
            productDistributionChartController.loadCustomData(pie);
        }
        if (revenueExpenseChartController != null && dashboardDto != null) {
            revenueExpenseChartController.loadCustomData(
                buildTrendSeries("Ingresos", dashboardDto.getTendencias().getVentas()),
                buildTrendSeries("Gastos",   dashboardDto.getTendencias().getCompras())
            );
        }
    }

    /**
     * Carga el CSS de alertas
     */
    private void loadAlertsCSS() {
        try {
            String alertsCSS = Objects.requireNonNull(getClass().getResource("/styles/alerts.css")).toExternalForm();
            alertsContainer.getStylesheets().add(alertsCSS);
        } catch (Exception e) {
            System.err.println("No se pudo cargar alerts.css: " + e.getMessage());
        }
    }

    /**
     * Carga solo los 2 charts iniciales y la lista
     */
    private void loadInitialCharts() {
        Platform.runLater(() -> {
            if (salesTrendChartController != null) {
                salesTrendChartController.setTitle("Tendencia de Ventas");
                salesTrendChartController.setSubtitle("Últimos 4 meses");
                salesTrendChartController.loadData();
            }

            if (regionSalesChartController != null) {
                regionSalesChartController.setTitle("Ventas por Región");
                regionSalesChartController.setSubtitle("Distribución actual");
                regionSalesChartController.loadData();
            }

            // Cargar la lista de productos
            if (topProductsListController != null) {
                topProductsListController.setTitle("Top Productos");
                topProductsListController.setSubtitle("Más vendidos");
                loadTopProductsList();
            }
        });
    }

    /**
     * Carga los charts iniciales con datos reales del dashboard ejecutivo.
     */
    private void loadChartsFromDashboard(ExecutiveDashboardDTO dto) {
        Platform.runLater(() -> {
            if (salesTrendChartController != null) {
                salesTrendChartController.setTitle("Tendencia de Ventas");
                salesTrendChartController.setSubtitle("Últimas semanas del período");
                salesTrendChartController.loadCustomData(
                    buildTrendSeries("Ventas",  dto.getTendencias().getVentas()),
                    buildTrendSeries("Compras", dto.getTendencias().getCompras())
                );
            }
            if (regionSalesChartController != null) {
                regionSalesChartController.setTitle("Ventas por Categoría");
                regionSalesChartController.setSubtitle("Distribución del período");
                XYChart.Series<String, Number> s = new XYChart.Series<>();
                s.setName("Ingresos");
                dto.getVentasPorCategoria().forEach(c ->
                    s.getData().add(new XYChart.Data<>(c.getCategoria(), c.getTotal())));
                regionSalesChartController.loadCustomData(s);
            }
        });
    }

    private XYChart.Series<String, Number> buildTrendSeries(
            String name, List<ExecutiveDashboardDTO.PuntoTendencia> puntos) {
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName(name);
        puntos.forEach(pt -> s.getData().add(
            new XYChart.Data<>(formatWeek(pt.getPeriodo()), pt.getValor())));
        return s;
    }

    private static String formatWeek(String period) {
        if (period == null) return "";
        int idx = period.indexOf("-W");
        return idx >= 0 ? "Sem " + period.substring(idx + 2) : period;
    }

    /**
     * Carga la lista de productos (usado solo si el dashboard ejecutivo no devuelve datos).
     */
    private void loadTopProductsList() {
        if (topProductsListController != null) {
            topProductsListController.setTitle("Top Productos");
            topProductsListController.setSubtitle("M\u00E1s vendidos");
        }
    }

    /**
     * Carga alertas usando datos mock como fallback cuando la API no está disponible.
     */
    private void loadAlerts() {
        List<Alert> alerts = alertService.getMockAlerts();
        displayAlerts(alerts);
    }

    /**
     * Muestra las alertas en el contenedor
     */
    private void displayAlerts(List<Alert> alerts) {
        alertsContainer.getChildren().clear();

        if (alerts == null || alerts.isEmpty()) {
            showEmptyAlertsState();
            return;
        }

        for (Alert alert : alerts) {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/fxml/components/alerts/AlertCard.fxml")
                );
                HBox alertCard = loader.load();
                AlertCardController controller = loader.getController();

                // Configurar la alerta
                controller.setAlert(alert);

                // IMPORTANTE: Guardar el ID de la alerta en el UserData del nodo
                alertCard.setUserData(alert.getId());

                // Configurar callback para dismiss
                controller.setOnDismissCallback(this::handleAlertDismiss);

                // Agregar al contenedor
                alertsContainer.getChildren().add(alertCard);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Muestra el estado vacío cuando no hay alertas
     */
    private void showEmptyAlertsState() {
        alertsContainer.getChildren().clear();

        HBox emptyState = new HBox();
        emptyState.getStyleClass().add("alerts-empty-state");
        emptyState.setAlignment(javafx.geometry.Pos.CENTER);

        Label emptyLabel = new Label("Sin alertas recientes");
        emptyLabel.getStyleClass().add("alerts-empty-text");

        emptyState.getChildren().add(emptyLabel);
        alertsContainer.getChildren().add(emptyState);
    }

    /**
     * Maneja el evento de dismiss de una alerta
     */
    private void handleAlertDismiss(Alert alert) {
        // Buscar y remover la card con animación
        Platform.runLater(() -> {
            alertsContainer.getChildren().removeIf(node -> {
                Object userData = node.getUserData();
                if (userData != null && userData.equals(alert.getId())) {
                    // Animación fade out
                    javafx.animation.FadeTransition fadeOut =
                            new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), node);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.0);
                    fadeOut.setOnFinished(e -> {
                        alertsContainer.getChildren().remove(node);

                        // Si no quedan alertas, mostrar estado vacío
                        if (alertsContainer.getChildren().isEmpty()) {
                            showEmptyAlertsState();
                        }
                    });
                    fadeOut.play();
                    return false; // No remover todavía, lo hace la animación
                }
                return false;
            });
        });

        // TODO: Actualizar la BD para marcar la alerta como descartada
        // Connection conn = DatabaseConnection.getConnection();
        // alertService.dismissAlert(conn, alert.getId());
    }

    /**
     * Refresca las alertas (llamar después de acciones que generen alertas)
     */
    public void refreshAlerts() {
        loadAlerts();
    }

    /**
     * Carga charts adicionales bajo demanda (cuando el usuario hace clic)
     */
    @FXML
    private void loadMoreCharts() {
        if (additionalChartsLoaded) return;

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    // Cargar PieChart
                    FXMLLoader pieLoader = new FXMLLoader(
                            getClass().getResource("/fxml/components/charts/PieChartCard.fxml")
                    );
                    VBox pieChartNode = pieLoader.load();
                    productDistributionChartController = pieLoader.getController();

                    Platform.runLater(() -> chartsContainer.getChildren().add(pieChartNode));

                    Thread.sleep(200);

                    // Cargar AreaChart
                    FXMLLoader areaLoader = new FXMLLoader(
                            getClass().getResource("/fxml/components/charts/AreaChartCard.fxml")
                    );
                    VBox areaChartNode = areaLoader.load();
                    revenueExpenseChartController = areaLoader.getController();

                    Platform.runLater(() -> chartsContainer.getChildren().add(areaChartNode));

                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    if (productDistributionChartController != null) {
                        productDistributionChartController.setTitle("Distribución por Categoría");
                        productDistributionChartController.setSubtitle("Participación en ventas");
                        if (dashboardDto != null && !dashboardDto.getVentasPorCategoria().isEmpty()) {
                            ObservableList<PieChart.Data> pie = FXCollections.observableArrayList();
                            dashboardDto.getVentasPorCategoria().forEach(c ->
                                pie.add(new PieChart.Data(c.getCategoria(), c.getTotal())));
                            productDistributionChartController.loadCustomData(pie);
                        } else {
                            productDistributionChartController.loadData();
                        }
                    }

                    if (revenueExpenseChartController != null) {
                        revenueExpenseChartController.setTitle("Ingresos vs Gastos");
                        revenueExpenseChartController.setSubtitle("Comparativa semanal");
                        if (dashboardDto != null) {
                            revenueExpenseChartController.loadCustomData(
                                buildTrendSeries("Ingresos", dashboardDto.getTendencias().getVentas()),
                                buildTrendSeries("Gastos",   dashboardDto.getTendencias().getCompras())
                            );
                        } else {
                            revenueExpenseChartController.loadData();
                        }
                    }

                    additionalChartsLoaded = true;
                });
            }
        };

        new Thread(loadTask).start();
    }

    /**
     * Carga los 3 gráficos de análisis por módulo bajo demanda.
     */
    @FXML
    private void loadModuleCharts() {
        if (moduleChartsLoaded) return;

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    // BarChart — Rentabilidad por Categoría (ancho completo)
                    FXMLLoader barLoader1 = new FXMLLoader(
                            getClass().getResource("/fxml/components/charts/BarChartCard.fxml"));
                    VBox bar1Node = barLoader1.load();
                    rentabilidadChartController = barLoader1.getController();
                    Platform.runLater(() -> modulesChartsContainer.getChildren().add(bar1Node));

                    Thread.sleep(150);

                    // PieChart Alertas + BarChart Modelos lado a lado
                    FXMLLoader pieLoader = new FXMLLoader(
                            getClass().getResource("/fxml/components/charts/PieChartCard.fxml"));
                    VBox pieNode = pieLoader.load();
                    alertasChartController = pieLoader.getController();

                    FXMLLoader barLoader2 = new FXMLLoader(
                            getClass().getResource("/fxml/components/charts/BarChartCard.fxml"));
                    VBox bar2Node = barLoader2.load();
                    modelosChartController = barLoader2.getController();

                    HBox row2 = new HBox(20);
                    HBox.setHgrow(pieNode,  javafx.scene.layout.Priority.ALWAYS);
                    HBox.setHgrow(bar2Node, javafx.scene.layout.Priority.ALWAYS);
                    row2.getChildren().addAll(pieNode, bar2Node);

                    Platform.runLater(() -> modulesChartsContainer.getChildren().add(row2));

                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    populateModuleCharts();
                    moduleChartsLoaded = true;
                });
            }
        };

        new Thread(loadTask).start();
    }

    /** Llena los 3 gráficos de módulos con datos del dashboardDto actual. */
    private void populateModuleCharts() {
        if (dashboardDto == null) return;

        // 1. BarChart — Rentabilidad por Categoría (ingresos vs utilidad)
        if (rentabilidadChartController != null) {
            rentabilidadChartController.setTitle("Rentabilidad por Categoría");
            rentabilidadChartController.setSubtitle("Ingresos vs Utilidad del período");
            List<ExecutiveDashboardDTO.RentabilidadCategoria> cats = dashboardDto.getRentabilidadCategorias();
            if (!cats.isEmpty()) {
                XYChart.Series<String, Number> serieIngresos = new XYChart.Series<>();
                serieIngresos.setName("Ingresos");
                XYChart.Series<String, Number> serieUtilidad = new XYChart.Series<>();
                serieUtilidad.setName("Utilidad");
                cats.forEach(c -> {
                    serieIngresos.getData().add(new XYChart.Data<>(c.getCategoria(), c.getIngresos()));
                    serieUtilidad.getData().add(new XYChart.Data<>(c.getCategoria(), c.getUtilidad()));
                });
                rentabilidadChartController.loadCustomData(serieIngresos, serieUtilidad);
            } else {
                rentabilidadChartController.loadData();
            }
        }

        // 2. PieChart — Alertas por tipo
        if (alertasChartController != null) {
            alertasChartController.setTitle("Distribución de Alertas");
            alertasChartController.setSubtitle("Por tipo de alerta activa");
            java.util.Map<String, Integer> porTipo = dashboardDto.getAlertasActivas().getPorTipo();
            if (porTipo != null && !porTipo.isEmpty()) {
                ObservableList<PieChart.Data> pie = FXCollections.observableArrayList();
                porTipo.forEach((tipo, count) -> {
                    if (count > 0) pie.add(new PieChart.Data(tipo, count));
                });
                if (!pie.isEmpty()) {
                    alertasChartController.loadCustomData(pie);
                } else {
                    alertasChartController.loadData();
                }
            } else {
                alertasChartController.loadData();
            }
        }

        // 3. BarChart — Precisión (R²) de modelos predictivos
        if (modelosChartController != null) {
            modelosChartController.setTitle("Precisión de Modelos Predictivos");
            modelosChartController.setSubtitle("R² de modelos entrenados (0 = peor, 1 = mejor)");
            List<ExecutiveDashboardDTO.ModeloResumen> modelos = dashboardDto.getPrecisionModelos();
            if (!modelos.isEmpty()) {
                XYChart.Series<String, Number> serie = new XYChart.Series<>();
                serie.setName("R²");
                modelos.forEach(m -> serie.getData().add(
                        new XYChart.Data<>(m.getNombre(), m.getPrecision())));
                modelosChartController.loadCustomData(serie);
            } else {
                modelosChartController.loadData();
            }
        }
    }

    /**
     * Actualiza todos los gráficos con datos frescos
     */
    public void refreshAllCharts() {
        if (salesTrendChartController != null) {
            salesTrendChartController.refreshData();
        }
        if (regionSalesChartController != null) {
            regionSalesChartController.refreshData();
        }
        if (productDistributionChartController != null) {
            productDistributionChartController.refreshData();
        }
        if (revenueExpenseChartController != null) {
            revenueExpenseChartController.refreshData();
        }
    }

    // ── Carga desde API ───────────────────────────────────────────────────────

    /** Popula las stats cards con datos reales del dashboard ejecutivo. */
    private void loadStatsFromDashboard(ExecutiveDashboardDTO dto) {
        ExecutiveDashboardDTO.ResumenVentas ventas  = dto.getResumenVentas();
        ExecutiveDashboardDTO.KpisFinancieros kpis  = dto.getKpisFinancieros();

        double variacionVentas = ventas.getVariacion();
        String signV   = variacionVentas >= 0 ? "↑" : "↓";
        String colorV  = variacionVentas >= 0 ? "blue" : "orange";

        double margen = kpis.getMargenBrutoPct();
        String signM  = margen >= 0 ? "↑" : "↓";
        String colorM = margen >= 20 ? "green" : margen >= 10 ? "blue" : "orange";

        List<StatsDTO> stats = List.of(
                new StatsDTO("📊",
                        String.format("$%,.0f", ventas.getTotal()),
                        "Ventas Totales",
                        String.format("%s %.1f%%", signV, Math.abs(variacionVentas)),
                        colorV,
                        variacionVentas >= 0),
                new StatsDTO("💰",
                        String.format("$%,.0f", kpis.getUtilidadBruta()),
                        "Utilidad Bruta",
                        String.format("%s %.1f%%", signM, Math.abs(margen)),
                        colorM,
                        kpis.getUtilidadBruta() >= 0),
                new StatsDTO("📦",
                        String.valueOf(ventas.getCantidad()),
                        "Ventas del Periodo",
                        String.format("ROI: %.1f%%", kpis.getRoiPorcentaje()),
                        "blue",
                        kpis.getRoiPorcentaje() >= 0),
                new StatsDTO("🎯",
                        String.format("$%,.0f", ventas.getTicketPromedio()),
                        "Ticket Promedio",
                        String.format("%d transacciones", ventas.getCantidad()),
                        "blue",
                        ventas.getTicketPromedio() >= 0)
        );

        statsCardsContainer.getChildren().clear();
        for (StatsDTO stat : stats) {
            HBox card = StatsCard.createStatsCard(
                    stat.emoji(), stat.value(), stat.label(),
                    stat.change(), stat.colorClass(), stat.positive());
            statsCardsContainer.getChildren().add(card);
        }
    }

    /** Carga el top de productos desde el dashboard ejecutivo. */
    private void loadTopProductsFromDashboard(ExecutiveDashboardDTO dto) {
        if (topProductsListController == null) return;
        topProductsListController.setTitle("Top Productos");
        topProductsListController.setSubtitle("Más vendidos");

        List<ExecutiveDashboardDTO.ProductoTop> tops = dto.getTopProductos().getPorCantidad();
        List<ListItem> items = new ArrayList<>();
        for (int i = 0; i < tops.size(); i++) {
            ExecutiveDashboardDTO.ProductoTop p = tops.get(i);
            items.add(new ListItem(
                    p.getIdProducto(),
                    p.getNombre(),
                    p.getCantidadVendida() + " unidades vendidas",
                    String.format("$%,.0f", p.getIngresosGenerados()),
                    "📦",
                    ListItem.ItemType.SUCCESS
            ));
        }
        topProductsListController.loadItems(items);
    }

    /** Convierte alertas del dashboard ejecutivo al modelo Alert del HomeController. */
    private void loadAlertsFromDashboard(ExecutiveDashboardDTO dto) {
        List<ExecutiveDashboardDTO.AlertaResumen> apiAlertas = dto.getAlertasActivas().getAlertas();
        List<Alert> alerts = new ArrayList<>();

        for (ExecutiveDashboardDTO.AlertaResumen a : apiAlertas) {
            Alert.AlertType tipo = switch (a.getTipo()) {
                case "Riesgo"  -> Alert.AlertType.ERROR;
                case "Oportunidad" -> Alert.AlertType.SUCCESS;
                default        -> Alert.AlertType.WARNING;
            };

            String titulo = switch (a.getTipo()) {
                case "Riesgo"      -> "Riesgo en " + a.getMetrica();
                case "Oportunidad" -> "Oportunidad en " + a.getMetrica();
                case "Anomalia"    -> "Anomal\u00EDa en " + a.getMetrica();
                case "Tendencia"   -> "Tendencia en " + a.getMetrica();
                default            -> "Alerta en " + a.getMetrica();
            };

            String mensaje = String.format("Valor actual: %.2f / Esperado: %.2f",
                    a.getValorActual(), a.getValorEsperado());

            alerts.add(new Alert(a.getId(), tipo, titulo, mensaje, null,
                    a.getCreadaEn() != null ? a.getCreadaEn().substring(0, 10) : ""));
        }

        displayAlerts(alerts);
    }

    /* CARGA STATS CARDS (fallback mock) */
    private void loadStatsCards() {
        List<StatsDTO> stats = List.of(
                new StatsDTO("\uD83D\uDCCA", "$124,500", "Ventas Totales", "\u2191 12.5%", "blue", true),
                new StatsDTO("\uD83D\uDCB0", "$38,200", "Utilidad Bruta", "\u2191 8.2%", "green", true),
                new StatsDTO("\uD83D\uDCE6", "342", "Ventas del Periodo", "ROI: 30.7%", "blue", true)
        );

        statsCardsContainer.getChildren().clear();
        for (StatsDTO stat : stats) {
            HBox card = StatsCard.createStatsCard(
                    stat.emoji(), stat.value(), stat.label(),
                    stat.change(), stat.colorClass(), stat.positive());
            statsCardsContainer.getChildren().add(card);
        }
    }



}