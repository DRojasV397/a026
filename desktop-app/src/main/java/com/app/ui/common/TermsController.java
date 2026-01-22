package com.app.ui.common;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class TermsController
{

    @FXML
    private Text termsText;

    @FXML
    public void initialize() {
        loadTermsFromFile();
    }

    private void loadTermsFromFile() {
        try (InputStream is =
                     getClass().getResourceAsStream("/texts/terms.txt")) {

            if (is == null) {
                termsText.setText("No se pudieron cargar los términos.");
                return;
            }

            String content = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8)
            ).lines().collect(Collectors.joining("\n"));

            termsText.setText(content);

        } catch (Exception e) {
            termsText.setText("Error al cargar los términos.");
            e.printStackTrace();
        }
    }


    @FXML
    private void onClose(javafx.event.ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource())
                .getScene()
                .getWindow();
        stage.close();
    }

}
