package com.app;

import com.app.core.navigation.SceneManager;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public class Main extends Application {

    private static HostServices hostServices;

    @Override
    public void start(Stage stage) {
        hostServices = getHostServices();
        SceneManager.init(stage);

        stage.setTitle("SANI");// Sistema de análiis de negocios inteligentes

        stage.getIcons().add(
                new Image(
                        Objects.requireNonNull(
                                getClass().getResourceAsStream("/images/app-icon.png")
                        )
                )
        );

        SceneManager.showLogin();
        stage.show();
    }

    public static HostServices getHost() {
        return hostServices;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
