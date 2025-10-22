package com.example.javafxapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import atlantafx.base.theme.PrimerLight;

/**
 * Main.java
 * Entry point for the JavaFX application. This class starts the JavaFX runtime
 * and loads the login-view.fxml file to show the login UI.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load the FXML from the resources folder
        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("/com/example/javafxapp/login-view.fxml")
        );
        Parent root = loader.load();

        // Set Atlantafx theme globally
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        Scene scene = new Scene(root, 1200, 800);
        // App-specific styles
        scene.getStylesheets().add(Main.class.getResource("/com/example/javafxapp/styles.css").toExternalForm());

        primaryStage.setTitle("Sistema BI - Login");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Standard main method to support running the app from IDEs and shells
     * that launch the class directly instead of using the JavaFX Maven plugin.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
