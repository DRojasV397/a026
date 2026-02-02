package com.app.ui.components.sidebar;

import com.app.core.navigation.SceneManager;
import com.app.core.session.UserSession;
import com.app.model.AppRoute;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class SidebarController {
    @FXML
    private Label userNameLabel;

    @FXML
    private Button adminItem;
    @FXML private Button dashboardBtn;
    @FXML private Button predictiveBtn;
    @FXML private Button profitBtn;
    @FXML private Button simulationBtn;
    @FXML private Button alertsBtn;
    @FXML private Button reportsBtn;
    @FXML private Button dataBtn;
    @FXML private Hyperlink profileBtn;


    @FXML private VBox sidebarRoot;
    @FXML private VBox menuBox;

    @FXML
    private Label collapseIcon;

    private static final double EXPANDED_WIDTH = 190;
    private static final double COLLAPSED_WIDTH = 45;

    private boolean collapsed = false;

    private void handleRouteChange(AppRoute route) {
        clearActive();

        switch (route) {
            case DASHBOARD -> setActive(dashboardBtn);
            case PREDICTIVE -> setActive(predictiveBtn);
            case PROFIT -> setActive(profitBtn);
            case SIMULATION -> setActive(simulationBtn);
            case ALERTS -> setActive(alertsBtn);
            case REPORTS -> setActive(reportsBtn);
            case ADMIN -> setActive(adminItem);
            case DATA -> setActive(dataBtn);
            case PROFILE -> setActiveHypLink(profileBtn);
            default -> {}
        }
    }

    private void setActive(Button btn) {
        if (btn != null) {
            btn.getStyleClass().add("active");
        }
    }

    private void setActiveHypLink(Hyperlink btn) {
        if (btn != null) {
            btn.getStyleClass().add("active");
        }
    }

    private void clearActive() {
        menuBox.getChildren().forEach(node -> {
            if (node instanceof Button btn) {
                btn.getStyleClass().remove("active");
            }
        });

        if (profileBtn != null) {
            profileBtn.getStyleClass().remove("active");
        }
    }


    @FXML
    private void initialize() {
        userNameLabel.setText(UserSession.getUser());

        //Control por rol
        if (!UserSession.isAdmin()) {
            adminItem.setManaged(false);
            adminItem.setVisible(false);
        }


        sidebarRoot.setMinWidth(EXPANDED_WIDTH);
        sidebarRoot.setPrefWidth(EXPANDED_WIDTH);
        sidebarRoot.setMaxWidth(EXPANDED_WIDTH);
        SceneManager.setOnRouteChanged(this::handleRouteChange);

    }

    public void goDashboard() {
        SceneManager.setContent("/fxml/home/HomeView.fxml", "Dashboard", "Resumen general de tu negocio", AppRoute.DASHBOARD);
    }

    public void goPredictive() {
        SceneManager.setContent("/fxml/predictive/PredictiveView.fxml", "Análisis predictivo", "Sin subtitulo", AppRoute.PREDICTIVE);
    }

    public void goProfit() {
        SceneManager.setContent("/fxml/profit/ProfitView.fxml", "Rentabilidad", "Sin subtitulo", AppRoute.PROFIT);
    }

    public void goSimulation() {
        SceneManager.setContent("/fxml/simulation/SimulationView.fxml", "Simulación", "Sin subtitulo", AppRoute.SIMULATION);
    }

    public void goAlerts() {
        SceneManager.setContent("/fxml/alerts/AlertsView.fxml", "Alertas", "Sin subtitulo", AppRoute.ALERTS);
    }

    public void goReports() {
        SceneManager.setContent("/fxml/reports/ReportsView.fxml", "Reportes", "Sin subtitulo", AppRoute.REPORTS);
    }

    public void goAdmin() {
        SceneManager.setContent("/fxml/admin/AdminView.fxml", "Administración",  "Sin subtitulo", AppRoute.ADMIN);
    }

    public void goData() {
        SceneManager.setContent("/fxml/data/DataView.fxml", "Gestión de datos", "Sin subtitulo", AppRoute.DATA);
    }

    public void goProfile() {
        SceneManager.setContent("/fxml/profile/ProfileView.fxml", "Mi perfil", "Sin subtitulo", AppRoute.PROFILE);
    }

    @FXML
    private void toggleSidebar() {
        collapsed = !collapsed;

        if (collapsed) {
            // FORZAR ancho
            collapseIcon.setText("⟩⟩⟩"); // expandir
            sidebarRoot.setMinWidth(COLLAPSED_WIDTH);
            sidebarRoot.setPrefWidth(COLLAPSED_WIDTH);
            sidebarRoot.setMaxWidth(COLLAPSED_WIDTH);
        } else {
            // LIBERAR ancho (volver a AUTO)
            collapseIcon.setText("⟨⟨⟨"); // colapsar
            sidebarRoot.setMinWidth(EXPANDED_WIDTH);
            sidebarRoot.setPrefWidth(EXPANDED_WIDTH);
            sidebarRoot.setMaxWidth(EXPANDED_WIDTH);
        }

        sidebarRoot.lookupAll(".sidebar-text")
                .forEach(n -> {
                    n.setVisible(!collapsed);
                    n.setManaged(!collapsed);
                });


        menuBox.getChildren().forEach(node -> {
            if (node instanceof Button btn) {
                if (collapsed) {
                    btn.setTooltip(new Tooltip(extractText(btn)));
                } else {
                    btn.setTooltip(null); // limpiar al expandir
                }
            }
        });
    }

    // Método auxiliar
    private String extractText(Button btn) {
        if (btn.getGraphic() instanceof HBox hbox) {
            return hbox.getChildren().stream()
                    .filter(n -> n instanceof Label)
                    .map(n -> ((Label) n).getText())
                    .findFirst()
                    .orElse("");
        }
        return "";
    }


}
