package com.app.ui.components.sidebar;

import com.app.core.navigation.SceneManager;
import com.app.core.session.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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

    @FXML private VBox sidebarRoot;
    @FXML private VBox menuBox;

    @FXML
    private Label collapseIcon;

    private static final double EXPANDED_WIDTH = 190;
    private static final double COLLAPSED_WIDTH = 45;

    private boolean collapsed = false;

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

    }

    public void goDashboard() {
        SceneManager.setContent("/fxml/home/HomeView.fxml", "Dashboard", "Resumen general de tu negocio");
    }

    public void goPredictive() {
        SceneManager.setContent("/fxml/predictive/PredictiveView.fxml", "Análisis predictivo", "Sin subtitulo");
    }

    public void goProfit() {
        SceneManager.setContent("/fxml/profit/ProfitView.fxml", "Rentabilidad", "Sin subtitulo");
    }

    public void goSimulation() {
        SceneManager.setContent("/fxml/simulation/SimulationView.fxml", "Simulación", "Sin subtitulo");
    }

    public void goAlerts() {
        SceneManager.setContent("/fxml/alerts/AlertsView.fxml", "Alertas", "Sin subtitulo");
    }

    public void goReports() {
        SceneManager.setContent("/fxml/reports/ReportsView.fxml", "Reportes", "Sin subtitulo");
    }

    public void goAdmin() {
        SceneManager.setContent("/fxml/admin/AdminView.fxml", "Administración",  "Sin subtitulo");
    }

    public void goData() {
        SceneManager.setContent("/fxml/data/DataView.fxml", "Gestión de datos", "Sin subtitulo");
    }

    public void goProfile() {
        SceneManager.setContent("/fxml/profile/ProfileView.fxml", "Mi perfil", "Sin subtitulo");
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
