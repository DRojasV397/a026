package com.app.core.navigation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;

public class SceneManager {

    private static Stage stage;

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
        loadScene("/fxml/home/HomeView.fxml");
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
