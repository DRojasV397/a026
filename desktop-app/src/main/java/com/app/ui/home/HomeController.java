package com.app.ui.home;

import com.app.model.Alert;
import com.app.model.ListItem;
import com.app.model.StatsDTO;
import com.app.model.alerts.AlertDTO;
import com.app.model.dashboard.ExecutiveDashboardDTO;
import com.app.model.dashboard.PreferencesResponseDTO;
import com.app.model.dashboard.UserPreferenceItemDTO;
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
import javafx.scene.Scene;
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
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    // Preferencias del usuario
    private final Map<String, UserPreferenceItemDTO> preferenceMap = new HashMap<>();
    private int currentDays = 30;

    private static final List<String> DEFAULT_STAT_ORDER = List.of(
            "stat_ventas_totales", "stat_utilidad_bruta", "stat_compras_totales",
            "stat_margen_operativo", "stat_ticket_promedio");

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

        loadPreferences();
    }

    /**
     * Carga las preferencias del usuario desde la API y luego carga el dashboard.
     */
    private void loadPreferences() {
        dashboardService.getPreferences().thenAccept(resp -> Platform.runLater(() -> {
            preferenceMap.clear();
            if (resp != null && resp.isSuccess()) {
                for (UserPreferenceItemDTO p : resp.getPreferencias()) {
                    preferenceMap.put(p.getKpi(), p);
                }
            }
            loadDashboardForPeriod(currentDays);
        })).exceptionally(ex -> {
            Platform.runLater(() -> loadDashboardForPeriod(currentDays));
            return null;
        });
    }

    /**
     * Retorna true si el elemento con la clave kpi debe mostrarse.
     * Por defecto (sin preferencia guardada) todo es visible.
     */
    private boolean isVisible(String kpi) {
        UserPreferenceItemDTO pref = preferenceMap.get(kpi);
        return pref == null || pref.isVisible();
    }

    /**
     * Retorna las claves de stats cards en el orden guardado por el usuario.
     */
    private List<String> getStatOrder() {
        boolean hasPrefs = DEFAULT_STAT_ORDER.stream().anyMatch(preferenceMap::containsKey);
        if (!hasPrefs) return DEFAULT_STAT_ORDER;
        return DEFAULT_STAT_ORDER.stream()
                .sorted(Comparator.comparingInt((String k) ->
                        preferenceMap.containsKey(k) ? preferenceMap.get(k).getOrden() : 999))
                .collect(Collectors.toList());
    }

    /**
     * Carga (o recarga) el dashboard para el rango de días dado desde hoy.
     */
    private void loadDashboardForPeriod(int days) {
        this.currentDays = days;
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

    /** Abre el diálogo modal de personalización del dashboard. */
    @FXML
    private void onOpenPreferences() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/home/DashboardPreferencesView.fxml"));
            VBox root = loader.load();
            DashboardPreferencesController ctrl = loader.getController();
            ctrl.initPreferences(preferenceMap);
            ctrl.setOnSaveCallback(newPrefs -> {
                // 1. Apply preferences locally and immediately (FX thread — no async needed)
                preferenceMap.clear();
                for (int i = 0; i < newPrefs.size(); i++) {
                    UserPreferenceItemDTO p = newPrefs.get(i);
                    p.setOrden(i + 1);
                    preferenceMap.put(p.getKpi(), p);
                }
                // 2. Reset lazy charts and reload dashboard right away
                additionalChartsLoaded = false;
                moduleChartsLoaded     = false;
                productDistributionChartController = null;
                revenueExpenseChartController      = null;
                rentabilidadChartController        = null;
                alertasChartController             = null;
                modelosChartController             = null;
                chartsContainer.getChildren().clear();
                modulesChartsContainer.getChildren().clear();
                loadDashboardForPeriod(currentDays);
                // 3. Persist to API in background (best-effort — UI doesn't wait for this)
                dashboardService.savePreferences(newPrefs)
                        .exceptionally(ex -> {
                            System.err.println("[HOME] Error al guardar preferencias en API: " + ex.getMessage());
                            return false;
                        });
            });

            Stage stage = new Stage();
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(dashboardRoot.getScene().getWindow());
            stage.setTitle("Personalizar Dashboard");
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
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
     * Carga charts adicionales bajo demanda (cuando el usuario hace clic).
     * Respeta preferencias de visibilidad.
     */
    @FXML
    private void loadMoreCharts() {
        if (additionalChartsLoaded) return;

        final boolean showPie  = isVisible("chart_distribucion_categoria");
        final boolean showArea = isVisible("chart_ingresos_gastos");

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    if (showPie) {
                        FXMLLoader pieLoader = new FXMLLoader(
                                getClass().getResource("/fxml/components/charts/PieChartCard.fxml"));
                        VBox pieChartNode = pieLoader.load();
                        productDistributionChartController = pieLoader.getController();
                        Platform.runLater(() -> chartsContainer.getChildren().add(pieChartNode));
                        Thread.sleep(200);
                    }

                    if (showArea) {
                        FXMLLoader areaLoader = new FXMLLoader(
                                getClass().getResource("/fxml/components/charts/AreaChartCard.fxml"));
                        VBox areaChartNode = areaLoader.load();
                        revenueExpenseChartController = areaLoader.getController();
                        Platform.runLater(() -> chartsContainer.getChildren().add(areaChartNode));
                    }

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
     * Carga los gráficos de análisis por módulo bajo demanda.
     * Respeta preferencias de visibilidad.
     */
    @FXML
    private void loadModuleCharts() {
        if (moduleChartsLoaded) return;

        final boolean showRentabilidad = isVisible("chart_rentabilidad");
        final boolean showAlertas      = isVisible("chart_alertas");
        final boolean showModelos      = isVisible("chart_precision_packs");

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    // BarChart — Rentabilidad por Categoría (ancho completo)
                    if (showRentabilidad) {
                        FXMLLoader barLoader1 = new FXMLLoader(
                                getClass().getResource("/fxml/components/charts/BarChartCard.fxml"));
                        VBox bar1Node = barLoader1.load();
                        rentabilidadChartController = barLoader1.getController();
                        Platform.runLater(() -> modulesChartsContainer.getChildren().add(bar1Node));
                        Thread.sleep(150);
                    }

                    // PieChart Alertas + BarChart Modelos (visibles según preferencia)
                    if (showAlertas || showModelos) {
                        FXMLLoader pieLoader = showAlertas ? new FXMLLoader(
                                getClass().getResource("/fxml/components/charts/PieChartCard.fxml")) : null;
                        VBox pieNode = null;
                        if (pieLoader != null) {
                            pieNode = pieLoader.load();
                            alertasChartController = pieLoader.getController();
                        }

                        FXMLLoader barLoader2 = showModelos ? new FXMLLoader(
                                getClass().getResource("/fxml/components/charts/BarChartCard.fxml")) : null;
                        VBox bar2Node = null;
                        if (barLoader2 != null) {
                            bar2Node = barLoader2.load();
                            modelosChartController = barLoader2.getController();
                        }

                        final VBox finalPieNode  = pieNode;
                        final VBox finalBar2Node = bar2Node;

                        Platform.runLater(() -> {
                            if (finalPieNode != null && finalBar2Node != null) {
                                // Both visible: side by side
                                HBox row2 = new HBox(20);
                                HBox.setHgrow(finalPieNode,  Priority.ALWAYS);
                                HBox.setHgrow(finalBar2Node, Priority.ALWAYS);
                                row2.getChildren().addAll(finalPieNode, finalBar2Node);
                                modulesChartsContainer.getChildren().add(row2);
                            } else if (finalPieNode != null) {
                                HBox.setHgrow(finalPieNode, Priority.ALWAYS);
                                modulesChartsContainer.getChildren().add(finalPieNode);
                            } else if (finalBar2Node != null) {
                                HBox.setHgrow(finalBar2Node, Priority.ALWAYS);
                                modulesChartsContainer.getChildren().add(finalBar2Node);
                            }
                        });
                    }

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

    /** Llena los gráficos de módulos con datos del dashboardDto actual. */
    private void populateModuleCharts() {
        if (dashboardDto == null) return;

        // 1. BarChart — Rentabilidad por Categoría (ingresos vs utilidad bruta)
        if (rentabilidadChartController != null) {
            rentabilidadChartController.setTitle("Rentabilidad por Categoría");
            List<ExecutiveDashboardDTO.RentabilidadCategoria> cats = dashboardDto.getRentabilidadCategorias();
            if (!cats.isEmpty()) {
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
                ExecutiveDashboardDTO.ModeloResumen mejor = modelos.stream()
                        .max(Comparator.comparingDouble(ExecutiveDashboardDTO.ModeloResumen::getPrecision))
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

    /** Popula las stats cards con datos reales, respetando orden y visibilidad del usuario. */
    private void loadStatsFromDashboard(ExecutiveDashboardDTO dto) {
        ExecutiveDashboardDTO.ResumenVentas  ventas  = dto.getResumenVentas();
        ExecutiveDashboardDTO.ResumenCompras compras = dto.getResumenCompras();
        ExecutiveDashboardDTO.KpisFinancieros kpis   = dto.getKpisFinancieros();

        double variacionVentas = ventas.getVariacion();
        String signV  = variacionVentas >= 0 ? "↑" : "↓";
        String colorV = variacionVentas >= 0 ? "blue" : "orange";

        double margenBruto = kpis.getMargenBrutoPct();
        String colorMB = margenBruto >= 20 ? "green" : margenBruto >= 10 ? "blue" : "orange";

        double margenOp = kpis.getMargenOperativoPct();
        String colorMO = margenOp >= 15 ? "green" : margenOp >= 8 ? "blue" : "orange";

        double variacionCompras = compras.getVariacion();
        String signC  = variacionCompras >= 0 ? "↑" : "↓";
        String colorC = variacionCompras >= 0 ? "orange" : "green";

        String estado = kpis.getEstadoFinanciero();
        String estadoLabel = switch (estado) {
            case "excelente" -> "Estado: Excelente";
            case "bueno"     -> "Estado: Bueno";
            case "aceptable" -> "Estado: Aceptable";
            case "bajo"      -> "Estado: Bajo";
            case "critico"   -> "Estado: Crítico";
            default          -> "Sin datos";
        };

        // Build a map from kpi key → StatsDTO
        Map<String, StatsDTO> statsMap = new HashMap<>();
        statsMap.put("stat_ventas_totales",
                new StatsDTO("📊", String.format("$%,.0f", ventas.getTotal()), "Ventas Totales",
                        String.format("%s %.1f%% vs período ant.", signV, Math.abs(variacionVentas)),
                        colorV, variacionVentas >= 0));
        statsMap.put("stat_utilidad_bruta",
                new StatsDTO("💰", String.format("$%,.0f", kpis.getUtilidadBruta()), "Utilidad Bruta",
                        String.format("Margen bruto: %.1f%%", margenBruto),
                        colorMB, kpis.getUtilidadBruta() >= 0));
        statsMap.put("stat_compras_totales",
                new StatsDTO("🛒", String.format("$%,.0f", compras.getTotal()), "Compras Totales",
                        String.format("%s %.1f%% vs período ant.", signC, Math.abs(variacionCompras)),
                        colorC, variacionCompras <= 0));
        statsMap.put("stat_margen_operativo",
                new StatsDTO("📈", String.format("%.1f%%", margenOp), "Margen Operativo",
                        estadoLabel, colorMO, margenOp >= 0));
        statsMap.put("stat_ticket_promedio",
                new StatsDTO("🎯", String.format("$%,.0f", ventas.getTicketPromedio()), "Ticket Promedio",
                        String.format("%d transacciones", ventas.getCantidad()),
                        "blue", ventas.getTicketPromedio() >= 0));

        statsCardsContainer.getChildren().clear();
        for (String kpi : getStatOrder()) {
            if (!isVisible(kpi)) continue;
            StatsDTO stat = statsMap.get(kpi);
            if (stat == null) continue;
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
