package com.app.service.alerts;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class WarningService {

    private WarningService() {}

    public static void show(String message) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle("Atención");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
