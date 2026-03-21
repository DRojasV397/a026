package com.app.ui.layout;

import com.app.core.session.UserSession;
import com.app.service.auth.TokenRefreshService;
import com.app.ui.components.navbar.NavbarController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BaseLayoutController {

    private static final Logger logger = LoggerFactory.getLogger(BaseLayoutController.class);

    @FXML
    private StackPane contentPane;

    @FXML
    private BorderPane centerContainer;

    @FXML
    private NavbarController navbarController;

    /** Scheduler que renueva el access token antes de que expire. */
    private ScheduledExecutorService tokenRefreshScheduler;

    @FXML
    private void initialize() {
        // Cargar el navbar manualmente
        loadNavbar();
        // Configurar usuario si el navbar se cargó correctamente
        if (navbarController != null) {
            navbarController.setUser(UserSession.getUser());
            navbarController.setUserRole(UserSession.getRole());
        } else {
            logger.error("No se pudo cargar el NavbarController");
        }
        startTokenRefreshScheduler();
    }

    /**
     * Inicia un hilo daemon que verifica cada 4 minutos si el token de acceso
     * está próximo a expirar (menos de 5 minutos restantes) y lo renueva automáticamente.
     */
    private void startTokenRefreshScheduler() {
        tokenRefreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-refresh");
            t.setDaemon(true); // Muere cuando la JVM se cierra
            return t;
        });
        tokenRefreshScheduler.scheduleAtFixedRate(() -> {
            if (UserSession.isLoggedIn() && UserSession.isTokenExpiringSoon(5)) {
                logger.info("Token próximo a expirar, renovando...");
                new TokenRefreshService().refreshAsync()
                        .thenAccept(success -> {
                            if (!success) {
                                logger.warn("No se pudo renovar el token automáticamente");
                            }
                        });
            }
        }, 4, 4, TimeUnit.MINUTES);
    }

    private void loadNavbar() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/layout/Navbar.fxml")
            );
            Node navbarNode = loader.load();
            navbarController = loader.getController();

            // Agregar el navbar al top del BorderPane
            StackPane navbarContainer = new StackPane(navbarNode);
            BorderPane.setMargin(navbarContainer, new Insets(15, 20, 15, 20));

            centerContainer.setTop(navbarContainer);

        } catch (IOException e) {
            System.err.println("ERROR al cargar Navbar.fxml:");
            e.printStackTrace();
        }
    }

    public void setContent(Node view) {
        contentPane.getChildren().setAll(view);
    }

    /**
     * Actualiza el título del navbar
     */
    public void updateNavbarTitle(String title, String subtitle) {
        navbarController.setTitle(title, subtitle);
    }

}
