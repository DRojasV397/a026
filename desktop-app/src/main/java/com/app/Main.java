package com.app;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Apply AtlantaFX theme
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // Create UI components
        Label welcomeLabel = new Label("Bienvenido a la Aplicación");
        welcomeLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label descriptionLabel = new Label("Proyecto de Trabajo de Titulación");
        descriptionLabel.setStyle("-fx-font-size: 14px;");

        Button testButton = new Button("Probar Conexión");
        testButton.setOnAction(e -> {
            System.out.println("Botón presionado - Sistema funcionando correctamente");
            descriptionLabel.setText("¡Todo funciona correctamente!");
        });

        // Layout
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getChildren().addAll(welcomeLabel, descriptionLabel, testButton);

        // Scene and Stage
        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("Desktop App - A026");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
