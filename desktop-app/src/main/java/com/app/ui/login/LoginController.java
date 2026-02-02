package com.app.ui.login;

import com.app.Main;
import com.app.core.navigation.SceneManager;
import com.app.core.session.UserSession;
import com.app.core.threading.AppExecutor;
import com.app.service.auth.AuthService;
import com.app.util.ValidationUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class LoginController {

    @FXML private TextField userField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    @FXML private HBox userWrapper;
    @FXML private HBox passwordWrapper;

    private final AuthService authService = new AuthService();

    private boolean userTouched = false;
    private boolean passTouched = false;

    @FXML
    private void initialize() {
        Platform.runLater(() -> {
            userField.getParent().requestFocus();
        });

        userField.textProperty().addListener((obs, oldVal, newVal) -> {
            userTouched = true;
            validateForm();
        });

        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            passTouched = true;
            validateForm();
        });
    }

    private void validateForm() {
        String email = userField.getText();
        String password = passwordField.getText();

        boolean emailRequired = ValidationUtil.isEmailRequired(email);
        boolean emailValid = emailRequired && ValidationUtil.isEmailFormatValid(email);

        boolean passRequired = ValidationUtil.isPasswordRequired(password);

        updateFieldState(userWrapper, emailValid, userTouched);
        updateFieldState(passwordWrapper, passRequired, passTouched);

        // Mensajes con prioridad lógica
        if (userTouched) {
            if (!emailRequired) {
                showError("El correo electrónico es obligatorio");
            } else if (!emailValid) {
                showError("Ingresa un correo electrónico válido");
            }
        }

        if (passTouched) {
            if (!passRequired) {
                showError("La contraseña es obligatoria");
            }
        }else{
            passRequired = true; //si no se ha tocado el field entonces es valida (para evitar errores solucionados)
        }

        // Si todo es válido, limpia error
        System.out.println("Email valid " +  emailValid);
        System.out.println("Password valid " +  passRequired);
        if (emailValid && passRequired) {
            clearError();
        }

        loginButton.setDisable(!(emailValid && passRequired));
    }

    private void updateFieldState(HBox wrapper, boolean valid, boolean touched) {
        wrapper.getStyleClass().removeAll("error", "success");

        if (!touched) return;

        wrapper.getStyleClass().add(valid ? "success" : "error");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    @FXML
    private void onLogin() {

        clearError();
        loginButton.setDisable(true);
        loginButton.setText("Validando...");

        String user = userField.getText();
        String pass = passwordField.getText();

        AppExecutor.runAsync(() -> {

            boolean success = authService.login(user, pass);//NECESITA AJUSTES CUANDO SE IMPLEMENTE LECTURA DE BASE DE DATOS

            Platform.runLater(() -> {
                if (success) {
                    UserSession.setUser(user, "Usuario"); //NECESITA AJUSTES CUANDO SE IMPLEMENTE LECTURA DE BASE DE DATOS
                    SceneManager.showHome();
                } else {
                    showError("Usuario o contraseña incorrectos");
                    loginButton.setDisable(false);
                    loginButton.setText("Iniciar sesión →");
                }
            });
        });
    }

    @FXML
    private void onShowTerms() {
        SceneManager.showModal(
                "/fxml/common/TermsView.fxml",
                "Términos y condiciones",
                "legal-icon.png"
        );
    }

    @FXML
    private void onContactSales() {
        try {
            String mailto =
                    "mailto:correo@gmail.com" +
                            "?subject=" + encodeMailParam("Información Sistema BI TT") +
                            "&body=" + encodeMailParam(
                            "Hola,\n\n" +
                                    "Estoy interesado en utilizar su aplicación y me gustaría recibir información " +
                                    "sobre la contratación del Sistema BI TT.\n\n" +
                                    "Gracias.");

            Main.getHost().showDocument(mailto);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    //Método auxiliar
    private String encodeMailParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
//        if(!ValidationUtil.isMac()){
//
//        }else{
//            return value
//                    .replace(" ", "%20")
//                    .replace("\n", "%0A")
//                    .replace("\r", "");
//        }
    }


}
