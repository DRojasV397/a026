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
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
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

    @FXML
    private VBox chartsContainer;

    // Contenedor de alertas
    @FXML
    private HBox alertsContainer;

    @FXML
    private ScrollPane dashboardRoot;

    @FXML
    private VBox dashboardContainer;

    @FXML private HBox statsCardsContainer;

    // Controladores de charts adicionales (cargados bajo demanda)
    private PieChartCardController productDistributionChartController;
    private AreaChartCardController revenueExpenseChartController;

    private boolean additionalChartsLoaded = false;

    // Servicios
    private final AlertService alertService = new AlertService();
    private final DashboardService dashboardService = new DashboardService();

    @FXML
    private void initialize() {
        // Cargar CSS de alertas programáticamente
        loadAlertsCSS();

        //Ajustar altura mínima del dashboard al viewport (CLAVE)
        dashboardContainer.minHeightProperty().bind(
                dashboardRoot.heightProperty()
        );

        // Cargar datos del dashboard ejecutivo desde la API
        dashboardService.getExecutiveDashboard().thenAccept(dto -> Platform.runLater(() -> {
            if (dto != null) {
                loadStatsFromDashboard(dto);
                loadTopProductsFromDashboard(dto);
                loadAlertsFromDashboard(dto);
            } else {
                // Fallback a charts estáticos si la API no responde
                loadStatsCards();
                loadAlerts();
            }
            loadInitialCharts();
        })).exceptionally(ex -> {
            System.err.println("[HOME] Error al cargar dashboard ejecutivo: " + ex.getMessage());
            Platform.runLater(() -> { loadStatsCards(); loadAlerts(); loadInitialCharts(); });
            return null;
        });
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
                        productDistributionChartController.setTitle("Distribución de Productos");
                        productDistributionChartController.setSubtitle("Por categoría");
                        productDistributionChartController.loadData();
                    }

                    if (revenueExpenseChartController != null) {
                        revenueExpenseChartController.setTitle("Ingresos vs Gastos");
                        revenueExpenseChartController.setSubtitle("Análisis trimestral");
                        revenueExpenseChartController.loadData();
                    }

                    additionalChartsLoaded = true;
                });
            }
        };

        new Thread(loadTask).start();
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
                        kpis.getRoiPorcentaje() >= 0)
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