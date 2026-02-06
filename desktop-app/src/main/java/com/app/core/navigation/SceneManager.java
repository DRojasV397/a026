package com.app.core.navigation;

import com.app.model.AppRoute;
import com.app.ui.layout.BaseLayoutController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;
import java.util.function.Consumer;

public class SceneManager {

    private static Stage stage;
    private static BaseLayoutController baseController;

    private static Consumer<AppRoute> onRouteChanged;

    public static void setOnRouteChanged(Consumer<AppRoute> listener) {
        onRouteChanged = listener;
    }


    public static void init(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("TT A026");

        stage.setWidth(1000);
        stage.setHeight(620);

        stage.setMinWidth(1000);
        stage.setMinHeight(620);

        stage.setResizable(true);
    }

    public static void showLogin() {
        System.out.println(
                SceneManager.class.getResource("/fxml/login/LoginView.fxml")
        );
        loadScene("/fxml/login/LoginView.fxml");
    }

    public static void showHome() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource("/fxml/layout/BaseLayout.fxml")
            );

            Parent root = loader.load();
            baseController = loader.getController();

            Scene scene = new Scene(root);
            scene.getStylesheets().add("/styles/main.css");

            stage.setScene(scene);

            // Cargamos el contenido inicial
            setContent("/fxml/home/HomeView.fxml", "Dashboard", "Resumen general de tu negocio", AppRoute.DASHBOARD);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setContent(String fxmlPath, String title, String subtitle, AppRoute route) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource(fxmlPath)
            );
            Parent content = loader.load();

            baseController.setContent(content);

            // Actualizar navbar si se proporcionan títulos
            if (title != null && baseController != null) {
                baseController.updateNavbarTitle(title, subtitle);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (onRouteChanged != null && route != null) {
            onRouteChanged.accept(route);
        }

    }

    private static void loadScene(String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(
                    Objects.requireNonNull(SceneManager.class.getResource(fxmlPath))
            );

            Scene scene = new Scene(root);
            scene.getStylesheets().add("/styles/main.css");

            stage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void showModal(String fxmlPath, String title, String icono) {
        try {
            Parent root = FXMLLoader.load(
                    Objects.requireNonNull(SceneManager.class.getResource(fxmlPath))
            );

            Scene scene = new Scene(root);
            scene.getStylesheets().add("/styles/main.css");
            scene.getStylesheets().add("/styles/modal.css");

            Stage modalStage = new Stage();
            modalStage.setTitle(title);
            modalStage.initOwner(stage);
            modalStage.initModality(Modality.WINDOW_MODAL);
            modalStage.setScene(scene);
            modalStage.setResizable(false);

            /* Icono */
            modalStage.getIcons().add(
                    new Image(
                            Objects.requireNonNull(
                                    SceneManager.class.getResourceAsStream("/images/" + icono)
                            )
                    )
            );

            modalStage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
