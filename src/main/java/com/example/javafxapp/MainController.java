package com.example.javafxapp;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controller for main-view.fxml. It wires up UI controls and handles user actions.
 */
public class MainController {

    @FXML
    private Label messageLabel;

    @FXML
    private Button changeTextButton;

    /**
     * Called by JavaFX when the button is clicked. Changes the label's text.
     */
    @FXML
    private void onChangeTextButtonClick() {
        messageLabel.setText("Hello, JavaFX! Button clicked.");
    }
}
