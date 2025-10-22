package com.example.javafxapp;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import org.reactfx.EventStreams;

import java.util.regex.Pattern;

/**
 * LoginController
 * Implements the reactive login form behavior, validations, states, and responsiveness.
 */
public class LoginController {

    // Layout references
    @FXML private HBox rootHBox;
    @FXML private StackPane leftPanel;
    @FXML private VBox rightPanel;
    @FXML private StackPane mobileLogo;

    // Form card and inputs
    @FXML private StackPane formCard;
    @FXML private StackPane emailWrapper;
    @FXML private TextField emailField;
    @FXML private HBox emailErrorRow;
    @FXML private Label emailError;
    @FXML private SVGPath iconEmail;

    @FXML private StackPane passwordWrapper;
    @FXML private PasswordField passwordField;
    @FXML private HBox passwordErrorRow;
    @FXML private Label passwordError;
    @FXML private SVGPath iconLock;
    @FXML private Button togglePassBtn;
    @FXML private SVGPath iconEye;

    // Options and actions
    @FXML private CheckBox rememberCheck;
    @FXML private Hyperlink forgotLink;
    @FXML private Button loginButton;
    @FXML private Label btnText;
    @FXML private SVGPath arrowIcon;
    @FXML private ProgressIndicator spinner;

    // State
    private boolean loading = false;
    private boolean showingPassword = false;
    private TextField visiblePassField;
    private static final Pattern EMAIL_RE = Pattern.compile("\\S+@\\S+\\.\\S+");

    @FXML
    private void initialize() {
        // Responsive behavior: show left panel only when width >= 1024px
        leftPanel.managedProperty().bind(leftPanel.visibleProperty());
        mobileLogo.managedProperty().bind(mobileLogo.visibleProperty());
        rootHBox.widthProperty().addListener((obs, oldW, newW) -> {
            boolean showLeft = newW.doubleValue() >= 1024;
            leftPanel.setVisible(showLeft);
            mobileLogo.setVisible(!showLeft);
        });

        // Input focus/icon color change
        emailField.focusedProperty().addListener((obs, was, is) -> updateFocusStyles(emailWrapper, iconEmail, is));
        passwordField.focusedProperty().addListener((obs, was, is) -> updateFocusStyles(passwordWrapper, iconLock, is));

        // Clear errors reactively when typing
        EventStreams.valuesOf(emailField.textProperty()).subscribe(t -> clearEmailError());
        EventStreams.valuesOf(passwordField.textProperty()).subscribe(t -> clearPasswordError());

        // Hover arrow translation
        loginButton.hoverProperty().addListener((obs, was, is) -> {
            arrowIcon.setTranslateX(is ? 4 : 0);
        });

        // Click handlers
        loginButton.setOnAction(e -> tryLogin());
        togglePassBtn.setOnAction(e -> togglePasswordVisibility());

        // Initial disable state
        updateLoginButtonDisable();

        // Also update disable state reactively when content changes
        EventStreams.merge(
                EventStreams.valuesOf(emailField.textProperty()),
                EventStreams.valuesOf(passwordField.textProperty())
        ).subscribe(t -> updateLoginButtonDisable());
    }

    private void updateFocusStyles(StackPane wrapper, SVGPath icon, boolean focused) {
        if (focused) {
            addStyleClass(wrapper, "input-focused");
            replaceIconFillClass(icon, true);
        } else {
            removeStyleClass(wrapper, "input-focused");
            replaceIconFillClass(icon, false);
        }
    }

    private void replaceIconFillClass(SVGPath icon, boolean focused) {
        // Switch between input-icon and input-icon-focused
        if (focused) {
            if (!icon.getStyleClass().contains("input-icon-focused")) {
                icon.getStyleClass().remove("input-icon");
                icon.getStyleClass().add("input-icon-focused");
            }
        } else {
            icon.getStyleClass().remove("input-icon-focused");
            if (!icon.getStyleClass().contains("input-icon")) {
                icon.getStyleClass().add("input-icon");
            }
        }
    }

    private void addStyleClass(StackPane node, String cls) {
        if (!node.getStyleClass().contains(cls)) node.getStyleClass().add(cls);
    }
    private void removeStyleClass(StackPane node, String cls) {
        node.getStyleClass().remove(cls);
    }

    private void clearEmailError() {
        emailError.setText("");
        emailErrorRow.setVisible(false);
        emailErrorRow.setManaged(false);
        emailWrapper.getStyleClass().remove("input-error");
    }

    private void clearPasswordError() {
        passwordError.setText("");
        passwordErrorRow.setVisible(false);
        passwordErrorRow.setManaged(false);
        passwordWrapper.getStyleClass().remove("input-error");
    }

    private boolean validateEmail() {
        String value = emailField.getText() == null ? "" : emailField.getText().trim();
        if (value.isEmpty()) {
            emailError.setText("El correo electrónico es requerido");
        } else if (!EMAIL_RE.matcher(value).matches()) {
            emailError.setText("Ingresa un correo electrónico válido");
        }
        boolean ok = emailError.getText().isEmpty();
        emailErrorRow.setVisible(!ok);
        emailErrorRow.setManaged(!ok);
        if (!ok && !emailWrapper.getStyleClass().contains("input-error")) {
            emailWrapper.getStyleClass().add("input-error");
        }
        return ok;
    }

    private boolean validatePassword() {
        String value = passwordField.getText() == null ? "" : passwordField.getText();
        if (value.isEmpty()) {
            passwordError.setText("La contraseña es requerida");
        } else if (value.length() < 6) {
            passwordError.setText("La contraseña debe tener al menos 6 caracteres");
        }
        boolean ok = passwordError.getText().isEmpty();
        passwordErrorRow.setVisible(!ok);
        passwordErrorRow.setManaged(!ok);
        if (!ok && !passwordWrapper.getStyleClass().contains("input-error")) {
            passwordWrapper.getStyleClass().add("input-error");
        }
        return ok;
    }

    private void updateLoginButtonDisable() {
        boolean disabled = loading || emailField.getText() == null || emailField.getText().isBlank()
                || passwordField.getText() == null || passwordField.getText().isBlank();
        loginButton.setDisable(disabled);
    }

    private void setLoading(boolean value) {
        loading = value;
        emailField.setDisable(value);
        passwordField.setDisable(value);
        rememberCheck.setDisable(value);
        loginButton.setDisable(value);
        spinner.setVisible(value);
        spinner.setManaged(value);
        btnText.setText(value ? "Iniciando sesión..." : "Iniciar Sesión");
    }

    private void tryLogin() {
        // Show errors immediately if invalid
        boolean emailOk = validateEmail();
        boolean passOk = validatePassword();
        if (!emailOk || !passOk) {
            return; // Do not proceed
        }
        // Simulate loading delay 800ms
        setLoading(true);
        PauseTransition pause = new PauseTransition(Duration.millis(800));
        pause.setOnFinished(e -> {
            setLoading(false);
            onLogin(emailField.getText().trim(), passwordField.getText());
        });
        pause.play();
    }

    private void togglePasswordVisibility() {
        if (!(passwordField.getParent() instanceof StackPane pane)) return;
        int index = pane.getChildren().indexOf(passwordField);
        if (!showingPassword) {
            // Switch to visible text field
            if (visiblePassField == null) {
                visiblePassField = new TextField();
                visiblePassField.setPromptText("••••••••");
                visiblePassField.setPrefHeight(passwordField.getPrefHeight());
                // Keep texts in sync
                visiblePassField.textProperty().bindBidirectional(passwordField.textProperty());
            }
            if (index >= 0) {
                pane.getChildren().set(index, visiblePassField);
                showingPassword = true;
                // eye-off icon
                iconEye.setFill(javafx.scene.paint.Color.web("#6B7280"));
                iconEye.setContent("M1 12 C3 7 8 4 12 4 C16 4 21 7 23 12 C21 17 16 20 12 20 C8 20 3 17 1 12 Z M5 5 L19 19 M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8 Z");
            }
        } else {
            // Switch back to password field
            int idx = pane.getChildren().indexOf(visiblePassField);
            if (idx >= 0) {
                pane.getChildren().set(idx, passwordField);
                showingPassword = false;
                // eye icon
                iconEye.setFill(javafx.scene.paint.Color.web("#6B7280"));
                iconEye.setContent("M1 12 C3 7 8 4 12 4 C16 4 21 7 23 12 C21 17 16 20 12 20 C8 20 3 17 1 12 Z M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8 Z");
            }
        }
    }

    // Callback when login is successful
    public void onLogin(String email, String password) {
        // Print requested confirmation
        System.out.println("Login successful - User: " + email + ", Password: " + password);
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/example/javafxapp/DashboardView.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            // Apply app-specific styles and rely on global Atlantafx theme set in Main
            scene.getStylesheets().add(Main.class.getResource("/com/example/javafxapp/styles.css").toExternalForm());
            scene.getStylesheets().add(Main.class.getResource("/com/example/javafxapp/styles/dashboard.css").toExternalForm());
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Business Intelligence Dashboard");
            stage.setScene(scene);
            stage.setMinWidth(1280);
            stage.setMinHeight(720);
            stage.setMaximized(true);
            stage.show();
            // Close login window
            javafx.stage.Stage current = (javafx.stage.Stage) loginButton.getScene().getWindow();
            current.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
