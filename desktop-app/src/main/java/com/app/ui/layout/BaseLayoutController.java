package com.app.ui.layout;

import com.app.core.session.UserSession;
import com.app.ui.components.navbar.NavbarController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class BaseLayoutController {

    @FXML
    private StackPane contentPane;

    @FXML
    private BorderPane centerContainer;

    @FXML
    private NavbarController navbarController;

    @FXML
    private void initialize() {
        // Cargar el navbar manualmente
        loadNavbar();
        // Configurar usuario si el navbar se cargó correctamente
        if (navbarController != null) {
            navbarController.setUser(UserSession.getUser());
            navbarController.setUserRole(UserSession.getRole());
        } else {
            System.err.println("ERROR: No se pudo cargar el NavbarController");
        }
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
