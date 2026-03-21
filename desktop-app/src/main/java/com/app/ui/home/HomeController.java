package com.app.ui.home;

import com.app.model.Alert;
import com.app.model.ListItem;
import com.app.model.StatsDTO;
import com.app.model.alerts.AlertDTO;
import com.app.model.dashboard.ExecutiveDashboardDTO;
import com.app.service.alerts.AlertApiService;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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

    // Contenedor de alertas y controles del carrusel
    @FXML private HBox   alertsContainer;
    @FXML private Button btnAlertPrev;
    @FXML private Button btnAlertNext;
    @FXML private Label  lblAlertPage;

    // Estado del carrusel de alertas
    private final List<Alert> allAlerts = new ArrayList<>();
    private int alertPage = 0;
    private static final int ALERTS_PER_PAGE = 3;

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
    private final AlertApiService alertApiService = new AlertApiService();
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
                loadChartsFromDashboard(dto);
                if (additionalChartsLoaded) refreshLazyCharts();
                if (moduleChartsLoaded)    populateModuleCharts();
            } else {
                loadStatsCards();
                loadInitialCharts();
            }
            loadActiveAlertsFromApi();
        })).exceptionally(ex -> {
            System.err.println("[HOME] Error al cargar dashboard: " + ex.getMessage());
            Platform.runLater(() -> { loadStatsCards(); loadInitialCharts(); loadActiveAlertsFromApi(); });
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
        if (productDistributionChartController != null && dashboardDto != null) {
            if (!dashboardDto.getVentasPorCategoria().isEmpty()) {
                ObservableList<PieChart.Data> pie = FXCollections.observableArrayList();
                dashboardDto.getVentasPorCategoria().forEach(c ->
                    pie.add(new PieChart.Data(c.getCategoria(), c.getTotal())));
                productDistributionChartController.loadCustomData(pie);
            } else {
                productDistributionChartController.setSubtitle("Sin datos para el período");
                productDistributionChartController.clearData();
            }
        }
        if (revenueExpenseChartController != null && dashboardDto != null) {
            if (!dashboardDto.getTendencias().getVentas().isEmpty()) {
                revenueExpenseChartController.loadCustomData(
                    buildTrendSeries("Ingresos", dashboardDto.getTendencias().getVentas()),
                    buildTrendSeries("Gastos",   dashboardDto.getTendencias().getCompras())
                );
            } else {
                revenueExpenseChartController.setSubtitle("Sin datos para el período");
                revenueExpenseChartController.clearData();
            }
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
                salesTrendChartController.setSubtitle("Sin datos disponibles");
                salesTrendChartController.clearData();
            }

            if (topProductsListController != null) {
                topProductsListController.setTitle("Top Productos");
                topProductsListController.setSubtitle("Sin datos disponibles");
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
     * Carga alertas desde la API usando el mismo endpoint y filtro que el módulo de alertas:
     * excluye RESUELTA e IGNORADA. Muestra máximo 5 alertas recientes.
     * Fallback a datos mock si la API no está disponible.
     */
    private void loadActiveAlertsFromApi() {
        alertApiService.getActiveAlerts()
                .thenAccept(alertas -> Platform.runLater(() -> {
                    List<Alert> alerts = alertas.stream()
                            .filter(a -> !"RESUELTA".equals(a.status()) && !"IGNORADA".equals(a.status()))
                            .limit(5)
                            .map(this::alertDtoToAlert)
                            .toList();
                    displayAlerts(alerts);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> displayAlerts(List.of()));
                    return null;
                });
    }

    /** Convierte un AlertDTO (API) al modelo Alert usado por el dashboard. */
    private Alert alertDtoToAlert(AlertDTO dto) {
        Alert.AlertType tipo = switch (dto.type()) {
            case "RIESGO"      -> Alert.AlertType.ERROR;
            case "OPORTUNIDAD" -> Alert.AlertType.SUCCESS;
            default            -> Alert.AlertType.WARNING;
        };
        return new Alert((int) dto.id(), tipo, dto.title(), dto.description(), null,
                dto.createdAt() != null ? dto.createdAt().toLocalDate().toString() : "");
    }

    /**
     * Recibe la lista completa de alertas, reinicia el carrusel a la página 0 y renderiza.
     */
    private void displayAlerts(List<Alert> alerts) {
        allAlerts.clear();
        if (alerts != null) allAlerts.addAll(alerts);
        alertPage = 0;
        renderAlertPage();
    }

    /**
     * Renderiza la página actual del carrusel (ALERTS_PER_PAGE tarjetas).
     */
    private void renderAlertPage() {
        alertsContainer.getChildren().clear();

        if (allAlerts.isEmpty()) {
            showEmptyAlertsState();
            updateCarouselNav(0, 0);
            return;
        }

        int total      = allAlerts.size();
        int totalPages = (int) Math.ceil((double) total / ALERTS_PER_PAGE);
        int start      = alertPage * ALERTS_PER_PAGE;
        int end        = Math.min(start + ALERTS_PER_PAGE, total);

        for (int i = start; i < end; i++) {
            Alert alert = allAlerts.get(i);
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/fxml/components/alerts/AlertCard.fxml"));
                HBox alertCard = loader.load();
                AlertCardController controller = loader.getController();

                controller.setAlert(alert);
                alertCard.setUserData(alert.getId());
                controller.setOnDismissCallback(this::handleAlertDismiss);
                // Cada tarjeta crece equitativamente para ocupar el ancho disponible
                HBox.setHgrow(alertCard, Priority.ALWAYS);

                alertsContainer.getChildren().add(alertCard);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        updateCarouselNav(alertPage + 1, totalPages);
    }

    /** Actualiza la visibilidad y estado de los controles de navegación del carrusel. */
    private void updateCarouselNav(int currentPage, int totalPages) {
        boolean multiPage = totalPages > 1;

        if (lblAlertPage != null) {
            lblAlertPage.setVisible(multiPage);
            lblAlertPage.setManaged(multiPage);
            if (multiPage) lblAlertPage.setText(currentPage + " / " + totalPages);
        }
        if (btnAlertPrev != null) {
            btnAlertPrev.setVisible(multiPage);
            btnAlertPrev.setManaged(multiPage);
            btnAlertPrev.setDisable(currentPage <= 1);
        }
        if (btnAlertNext != null) {
            btnAlertNext.setVisible(multiPage);
            btnAlertNext.setManaged(multiPage);
            btnAlertNext.setDisable(currentPage >= totalPages);
        }
    }

    /** Muestra el estado vacío cuando no hay alertas. */
    private void showEmptyAlertsState() {
        HBox emptyState = new HBox();
        emptyState.getStyleClass().add("alerts-empty-state");
        emptyState.setAlignment(javafx.geometry.Pos.CENTER);
        HBox.setHgrow(emptyState, Priority.ALWAYS);

        Label emptyLabel = new Label("Sin alertas recientes");
        emptyLabel.getStyleClass().add("alerts-empty-text");

        emptyState.getChildren().add(emptyLabel);
        alertsContainer.getChildren().add(emptyState);
    }

    /** Navega a la página anterior del carrusel. */
    @FXML
    private void onAlertPrev() {
        if (alertPage > 0) {
            alertPage--;
            renderAlertPage();
        }
    }

    /** Navega a la página siguiente del carrusel. */
    @FXML
    private void onAlertNext() {
        int totalPages = (int) Math.ceil((double) allAlerts.size() / ALERTS_PER_PAGE);
        if (alertPage < totalPages - 1) {
            alertPage++;
            renderAlertPage();
        }
    }

    /**
     * Elimina la alerta descartada de la lista y re-renderiza la página actual.
     * Si la página queda vacía por el dismiss, retrocede una página.
     */
    private void handleAlertDismiss(Alert alert) {
        Platform.runLater(() -> {
            allAlerts.removeIf(a -> a.getId() == alert.getId());
            int totalPages = (int) Math.ceil((double) allAlerts.size() / ALERTS_PER_PAGE);
            if (alertPage >= totalPages && alertPage > 0) alertPage--;
            renderAlertPage();
        });
    }

    /**
     * Refresca las alertas (llamar después de acciones que generen alertas)
     */
    public void refreshAlerts() {
        loadActiveAlertsFromApi();
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
                        if (dashboardDto != null && !dashboardDto.getVentasPorCategoria().isEmpty()) {
                            productDistributionChartController.setSubtitle("Participación en ventas");
                            ObservableList<PieChart.Data> pie = FXCollections.observableArrayList();
                            dashboardDto.getVentasPorCategoria().forEach(c ->
                                pie.add(new PieChart.Data(c.getCategoria(), c.getTotal())));
                            productDistributionChartController.loadCustomData(pie);
                        } else {
                            productDistributionChartController.setSubtitle("Sin datos para el período");
                            productDistributionChartController.clearData();
                        }
                    }

                    if (revenueExpenseChartController != null) {
                        revenueExpenseChartController.setTitle("Ingresos vs Gastos");
                        if (dashboardDto != null && !dashboardDto.getTendencias().getVentas().isEmpty()) {
                            revenueExpenseChartController.setSubtitle("Comparativa semanal");
                            revenueExpenseChartController.loadCustomData(
                                buildTrendSeries("Ingresos", dashboardDto.getTendencias().getVentas()),
                                buildTrendSeries("Gastos",   dashboardDto.getTendencias().getCompras())
                            );
                        } else {
                            revenueExpenseChartController.setSubtitle("Sin datos para el período");
                            revenueExpenseChartController.clearData();
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
                    HBox.setHgrow(pieNode,  Priority.ALWAYS);
                    HBox.setHgrow(bar2Node, Priority.ALWAYS);
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

        // 1. BarChart — Rentabilidad por Categoría (ingresos vs utilidad bruta)
        if (rentabilidadChartController != null) {
            rentabilidadChartController.setTitle("Rentabilidad por Categoría");
            List<ExecutiveDashboardDTO.RentabilidadCategoria> cats = dashboardDto.getRentabilidadCategorias();
            if (!cats.isEmpty()) {
                // Calcular margen promedio ponderado para el subtítulo informativo
                double totalIngresos = cats.stream().mapToDouble(ExecutiveDashboardDTO.RentabilidadCategoria::getIngresos).sum();
                double totalUtilidad = cats.stream().mapToDouble(ExecutiveDashboardDTO.RentabilidadCategoria::getUtilidad).sum();
                double margenProm = totalIngresos > 0 ? (totalUtilidad / totalIngresos * 100) : 0;
                rentabilidadChartController.setSubtitle(
                        String.format("Ingresos vs Utilidad Bruta  |  Margen promedio: %.1f%%", margenProm));
                XYChart.Series<String, Number> serieIngresos = new XYChart.Series<>();
                serieIngresos.setName("Ingresos");
                XYChart.Series<String, Number> serieUtilidad = new XYChart.Series<>();
                serieUtilidad.setName("Utilidad Bruta");
                cats.forEach(c -> {
                    serieIngresos.getData().add(new XYChart.Data<>(c.getCategoria(), c.getIngresos()));
                    serieUtilidad.getData().add(new XYChart.Data<>(c.getCategoria(), c.getUtilidad()));
                });
                rentabilidadChartController.loadCustomData(serieIngresos, serieUtilidad);
            } else {
                rentabilidadChartController.setSubtitle("Sin datos para el período");
                rentabilidadChartController.clearData();
            }
        }

        // 2. PieChart — Alertas por tipo
        if (alertasChartController != null) {
            alertasChartController.setTitle("Distribución de Alertas");
            java.util.Map<String, Integer> porTipo = dashboardDto.getAlertasActivas().getPorTipo();
            if (porTipo != null && !porTipo.isEmpty()) {
                ObservableList<PieChart.Data> pie = FXCollections.observableArrayList();
                porTipo.forEach((tipo, count) -> {
                    if (count > 0) pie.add(new PieChart.Data(tipo, count));
                });
                if (!pie.isEmpty()) {
                    alertasChartController.setSubtitle("Por tipo de alerta activa");
                    alertasChartController.loadCustomData(pie);
                } else {
                    alertasChartController.setSubtitle("Sin alertas activas");
                    alertasChartController.clearData();
                }
            } else {
                alertasChartController.setSubtitle("Sin alertas activas");
                alertasChartController.clearData();
            }
        }

        // 3. BarChart — Precisión (R²) de packs de modelos
        if (modelosChartController != null) {
            modelosChartController.setTitle("Precisión de Packs Predictivos");
            List<ExecutiveDashboardDTO.ModeloResumen> modelos = dashboardDto.getPrecisionModelos();
            if (!modelos.isEmpty()) {
                // Encontrar el mejor pack para resaltarlo en el subtítulo
                ExecutiveDashboardDTO.ModeloResumen mejor = modelos.stream()
                        .max(java.util.Comparator.comparingDouble(ExecutiveDashboardDTO.ModeloResumen::getPrecision))
                        .orElse(null);
                String subtitulo = mejor != null
                        ? String.format("R² por pack activo  |  Mejor: %s (R²=%.3f)", mejor.getNombre(), mejor.getPrecision())
                        : "R² promedio por pack activo (0 = peor, 1 = mejor)";
                modelosChartController.setSubtitle(subtitulo);
                XYChart.Series<String, Number> serie = new XYChart.Series<>();
                serie.setName("R²");
                modelos.forEach(m -> serie.getData().add(
                        new XYChart.Data<>(m.getNombre(), m.getPrecision())));
                modelosChartController.loadCustomData(serie);
            } else {
                modelosChartController.setSubtitle("Sin packs entrenados — entrena un pack en el módulo predictivo");
                modelosChartController.clearData();
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
        ExecutiveDashboardDTO.ResumenVentas ventas   = dto.getResumenVentas();
        ExecutiveDashboardDTO.ResumenCompras compras  = dto.getResumenCompras();
        ExecutiveDashboardDTO.KpisFinancieros kpis    = dto.getKpisFinancieros();

        double variacionVentas = ventas.getVariacion();
        String signV   = variacionVentas >= 0 ? "↑" : "↓";
        String colorV  = variacionVentas >= 0 ? "blue" : "orange";

        // Margen bruto (tres razones — razón 1)
        double margenBruto = kpis.getMargenBrutoPct();
        String colorMB = margenBruto >= 20 ? "green" : margenBruto >= 10 ? "blue" : "orange";

        // Margen operativo (tres razones — razón 2)
        double margenOp = kpis.getMargenOperativoPct();
        String colorMO = margenOp >= 15 ? "green" : margenOp >= 8 ? "blue" : "orange";

        double variacionCompras = compras.getVariacion();
        String signC  = variacionCompras >= 0 ? "↑" : "↓";
        String colorC = variacionCompras >= 0 ? "orange" : "green";

        // Estado financiero (de backend)
        String estado = kpis.getEstadoFinanciero();
        String estadoLabel = switch (estado) {
            case "excelente" -> "Estado: Excelente";
            case "bueno"     -> "Estado: Bueno";
            case "aceptable" -> "Estado: Aceptable";
            case "bajo"      -> "Estado: Bajo";
            case "critico"   -> "Estado: Crítico";
            default          -> "Sin datos";
        };

        List<StatsDTO> stats = List.of(
                // Card 1: Ventas Totales con variación vs período anterior
                new StatsDTO("📊",
                        String.format("$%,.0f", ventas.getTotal()),
                        "Ventas Totales",
                        String.format("%s %.1f%% vs período ant.", signV, Math.abs(variacionVentas)),
                        colorV,
                        variacionVentas >= 0),
                // Card 2: Utilidad Bruta — razón 1 del módulo de rentabilidad
                new StatsDTO("💰",
                        String.format("$%,.0f", kpis.getUtilidadBruta()),
                        "Utilidad Bruta",
                        String.format("Margen bruto: %.1f%%", margenBruto),
                        colorMB,
                        kpis.getUtilidadBruta() >= 0),
                // Card 3: Compras Totales
                new StatsDTO("🛒",
                        String.format("$%,.0f", compras.getTotal()),
                        "Compras Totales",
                        String.format("%s %.1f%% vs período ant.", signC, Math.abs(variacionCompras)),
                        colorC,
                        variacionCompras <= 0),
                // Card 4: Margen Operativo — razón 2 del módulo de rentabilidad
                new StatsDTO("📈",
                        String.format("%.1f%%", margenOp),
                        "Margen Operativo",
                        estadoLabel,
                        colorMO,
                        margenOp >= 0),
                // Card 5: Ticket Promedio
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

    /* CARGA STATS CARDS (fallback sin conexión) */
    private void loadStatsCards() {
        statsCardsContainer.getChildren().clear();
        Label msg = new Label("Sin conexión con el servidor — Datos no disponibles");
        msg.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 13px; -fx-padding: 20px 0;");
        statsCardsContainer.getChildren().add(msg);
    }



}