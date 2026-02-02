package com.app.ui.home;

import com.app.model.Alert;
import com.app.model.ListItem;
import com.app.model.StatsDTO;
import com.app.service.alerts.AlertService;
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
import java.util.List;

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

    // Servicio de alertas
    private final AlertService alertService = new AlertService();

    @FXML
    private void initialize() {
        // Cargar CSS de alertas programáticamente
        loadAlertsCSS();

        //Ajustar altura mínima del dashboard al viewport (CLAVE)
        dashboardContainer.minHeightProperty().bind(
                dashboardRoot.heightProperty()
        );

        // Cargar charts iniciales en background
        Task<Void> initTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }

            @Override
            protected void succeeded() {
                loadStatsCards();
                loadInitialCharts();
                loadAlerts(); // Cargar alertas después de los charts
            }
        };

        Thread initThread = new Thread(initTask);
        initThread.setDaemon(true);
        initThread.start();
    }

    /**
     * Carga el CSS de alertas
     */
    private void loadAlertsCSS() {
        try {
            String alertsCSS = getClass().getResource("/styles/alerts.css").toExternalForm();
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
     * Carga la lista de productos desde la BD
     */
    private void loadTopProductsList() {
        Task<List<ListItem>> loadListTask = new Task<>() {
            @Override
            protected List<ListItem> call() {
                // TODO: Conectar con tu BD
                // return productService.getTopProducts();

                // Datos de ejemplo
                return getMockListItems();
            }

            @Override
            protected void succeeded() {
                List<ListItem> items = getValue();
                Platform.runLater(() -> topProductsListController.loadItems(items));
            }
        };

        new Thread(loadListTask).start();
    }

    /**
     * Obtiene datos de ejemplo para la lista
     */
    private List<ListItem> getMockListItems() {
        List<ListItem> items = new java.util.ArrayList<>();

        items.add(new ListItem(1, "Laptop HP Elite", "345 unidades vendidas",
                "$45,230", "💻", ListItem.ItemType.SUCCESS));
        items.add(new ListItem(2, "Mouse Logitech MX", "892 unidades vendidas",
                "$12,150", "🖱️", ListItem.ItemType.SUCCESS));
        items.add(new ListItem(3, "Teclado Mecánico", "567 unidades vendidas",
                "$28,450", "⌨️", ListItem.ItemType.INFO));
        items.add(new ListItem(4, "Monitor Samsung", "234 unidades vendidas",
                "$65,800", "🖥️", ListItem.ItemType.SUCCESS));
        items.add(new ListItem(5, "Webcam Logitech", "156 unidades vendidas",
                "$8,450", "📷", ListItem.ItemType.WARNING));
        items.add(new ListItem(6, "Audífonos Sony", "423 unidades vendidas",
                "$15,680", "🎧", ListItem.ItemType.INFO));

        return items;
    }

    /**
     * Carga las alertas desde la base de datos
     */
    private void loadAlerts() {
        Task<List<Alert>> loadAlertsTask = new Task<>() {
            @Override
            protected List<Alert> call() {
                // TODO: Reemplazar con conexión real a tu BD
                // Connection conn = DatabaseConnection.getConnection();
                // return alertService.getRecentAlerts(conn);

                // Por ahora, usar datos de ejemplo
                return alertService.getMockAlerts();
            }

            @Override
            protected void succeeded() {
                List<Alert> alerts = getValue();
                Platform.runLater(() -> displayAlerts(alerts));
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> showEmptyAlertsState());
            }
        };

        new Thread(loadAlertsTask).start();
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

    /* CARGA STATS CARDS */
    private void loadStatsCards() {
        Task<List<StatsDTO>> loadStatsTask = new Task<>() {
            @Override
            protected List<StatsDTO> call() {
                // TODO: Reemplazar con BD real
                return getMockStats();
            }

            @Override
            protected void succeeded() {
                List<StatsDTO> stats = getValue();

                Platform.runLater(() -> {
                    statsCardsContainer.getChildren().clear();

                    for (StatsDTO stat : stats) {
                        HBox card = StatsCard.createStatsCard(
                                stat.emoji(),
                                stat.value(),
                                stat.label(),
                                stat.change(),
                                stat.colorClass(),
                                stat.positive()
                        );
                        statsCardsContainer.getChildren().add(card);
                    }
                });
            }
        };

        new Thread(loadStatsTask).start();
    }

    private List<StatsDTO> getMockStats() {
        return List.of(
                new StatsDTO("📊", "$124,500", "Ventas Totales", "↑ 12.5%", "blue", true),
                new StatsDTO("👥", "1,428", "Clientes", "↑ 8.2%", "green", true),
                new StatsDTO("📦", "342", "Pedidos", "↓ 3.1%", "orange", false)
        );
    }



}