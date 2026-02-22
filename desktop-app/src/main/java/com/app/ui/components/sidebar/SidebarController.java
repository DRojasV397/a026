package com.app.ui.components.sidebar;

import com.app.core.navigation.SceneManager;
import com.app.core.session.UserSession;
import com.app.model.AppRoute;
import com.app.service.storage.AvatarStorageService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
    @FXML private Button collapseIconBtn;
    @FXML private HBox profileBtn;

    @FXML private ImageView sidebarAvatarView;

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

    private void setActiveHypLink(HBox btn) {
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


        // Avatar por defecto
        loadDefaultAvatar();

        // Binding reactivo al UserSession
        UserSession.avatarProperty().addListener((obs, oldImg, newImg) -> {
            if (newImg != null) sidebarAvatarView.setImage(newImg);
        });

        UserSession.displayNameProperty().addListener((obs, oldName, newName) -> {
            if (newName != null && !newName.isBlank()) userNameLabel.setText(newName);
        });

    }

    public void goDashboard() {
        SceneManager.setContent("/fxml/home/HomeView.fxml", "Dashboard", "Resumen general de tu negocio", AppRoute.DASHBOARD);
    }

    public void goPredictive() {
        SceneManager.setContent("/fxml/predictive/PredictiveView.fxml", "Análisis predictivo", "Selecciona un modelo, define los parámetros para tu proyección y analiza los resultados", AppRoute.PREDICTIVE);
    }

    public void goProfit() {
        SceneManager.setContent("/fxml/profit/ProfitView.fxml", "Rentabilidad", "Utiliza la calculadora de rentabilidad, analiza tus productos o categorías y toma decisiones con cada resultado", AppRoute.PROFIT);
    }

    public void goSimulation() {
        SceneManager.setContent("/fxml/simulation/SimulationView.fxml", "Simulación", "Proyecta el resultado de tus decisiones... ¿Qué pasaría sí?", AppRoute.SIMULATION);
    }

    public void goAlerts() {
        SceneManager.setContent("/fxml/alerts/AlertsView.fxml", "Alertas", "Gestiona y programa tus propias alertas", AppRoute.ALERTS);
    }

    public void goReports() {
        SceneManager.setContent("/fxml/reports/ReportsView.fxml", "Reportes", "Crea y descarga reportes personalizados del sistema", AppRoute.REPORTS);
    }

    public void goAdmin() {
        SceneManager.setContent("/fxml/admin/AdminView.fxml", "Administración",  "Gestiona tu negocio y el perfil de tus colaboradores", AppRoute.ADMIN);
    }

    public void goData() {
        SceneManager.setContent("/fxml/data/DataView.fxml", "Gestión de datos", "Carga, valida y prepara tus datos para el análisis", AppRoute.DATA);
    }

    public void goProfile() {
        SceneManager.setContent("/fxml/profile/ProfileView.fxml", "Mi perfil", "Actualiza tus datos personales, configura tus preferencias y observa tus estadísticas de uso", AppRoute.PROFILE);
    }

    @FXML
    private void toggleSidebar() {
        collapsed = !collapsed;

        if (collapsed) {
            // FORZAR ancho
            collapseIcon.setText("⏩"); // expandir
            sidebarRoot.setMinWidth(COLLAPSED_WIDTH);
            sidebarRoot.setPrefWidth(COLLAPSED_WIDTH);
            sidebarRoot.setMaxWidth(COLLAPSED_WIDTH);
        } else {
            // LIBERAR ancho (volver a AUTO)
            collapseIcon.setText("⏪"); // colapsar
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

        if (collapsed) {
            collapseIconBtn.setTooltip(new Tooltip("Ampliar menú"));
        } else {
            collapseIconBtn.setTooltip(null); // limpiar al expandir
        }

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


    private void loadDefaultAvatar() {
        // 1. Intentar cargar el avatar guardado del usuario
        String userId = Integer.toString(UserSession.getUserId());
        Image saved = AvatarStorageService.loadAvatar(userId);

        if (saved != null) {
            sidebarAvatarView.setImage(saved);
            UserSession.setAvatar(saved); // sincroniza navbar y sidebar al abrir perfil
            return;
        }

        // 2. Fallback: default-avatar del classpath
        try {
            var stream = getClass().getResourceAsStream("/images/default-avatar.png");
            if (stream != null) {
                Image defaultImg = new Image(stream);
                sidebarAvatarView.setImage(defaultImg);
                // No propagamos el default a UserSession si ya tiene uno desde login
                if (UserSession.getAvatar() == null) {
                    UserSession.setAvatar(defaultImg);
                }
            }
        } catch (Exception e) {
            System.err.println("No se pudo cargar el avatar por defecto: " + e.getMessage());
        }
    }

}
