package com.app.ui.admin;

import com.app.model.UserDTO;
import com.app.util.ValidationUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;

public class UserFormController {

    @FXML private Label        formTitle;
    @FXML private Label        formSubtitle;

    @FXML private TextField    txtFullName;
    @FXML private TextField    txtUsername;
    @FXML private TextField    txtEmail;
    @FXML private Label        emailError;

    @FXML private VBox         roleRow;
    @FXML private ComboBox<String> cmbRole;

    @FXML private HBox passwordSection;
    @FXML private PasswordField txtPassword;
    @FXML private PasswordField txtConfirmPassword;
    @FXML private Label         passwordMatchError;

    @FXML private VBox         resetSection;
    @FXML private Button       btnSubmit;

    // ── Estado ────────────────────────────────────────────────────────────────
    private UserDTO            userToEdit = null;
    private Consumer<UserDTO>  onSaved;
    private boolean            isEditMode = false;

    // ═════════════════════════════════════════════════════════════════════════
    //  Init público: llamado desde AdminController
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * @param user     null → modo creación, non-null → modo edición
     * @param onSaved  callback con el UserDTO resultante
     */
    public void initForm(UserDTO user, Consumer<UserDTO> onSaved) {
        this.onSaved    = onSaved;
        this.userToEdit = user;
        this.isEditMode = (user != null);

        // Poblar roles
        cmbRole.getItems().addAll("Administrador", "Analista");

        if (isEditMode) {
            // Modo edición
            formTitle.setText("Editar usuario");
            formSubtitle.setText("Modifica los datos del usuario");
            btnSubmit.setText("Guardar cambios");

            txtFullName.setText(user.fullName());
            txtUsername.setText(user.username());
            txtUsername.setEditable(false); // No se puede cambiar el username
            txtEmail.setText(user.email());
            cmbRole.getSelectionModel().select(
                    user.roleDisplay() != null ? user.roleDisplay() : user.role()
            );

            // Ocultar campos de contraseña, mostrar reset
            passwordSection.setVisible(false);
            passwordSection.setManaged(false);
            resetSection.setVisible(true);
            resetSection.setManaged(true);

        } else {
            // Modo creación
            roleRow.setVisible(true);
            roleRow.setManaged(true);
        }

        // Listeners de validación en tiempo real
        txtEmail.textProperty().addListener((obs, o, v) -> validateEmail(v));
        txtConfirmPassword.textProperty().addListener((obs, o, v) -> validatePasswordMatch());
        txtPassword.textProperty().addListener((obs, o, v) -> validatePasswordMatch());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Validaciones
    // ═════════════════════════════════════════════════════════════════════════

    private boolean validateEmail(String email) {
        if (email == null || email.isBlank()) {
            showFieldError(emailError, "El correo es obligatorio");
            markError(txtEmail, true);
            return false;
        }
        if (!ValidationUtil.isEmailFormatValid(email)) {
            showFieldError(emailError, "Ingresa un correo válido (ej. usuario@dominio.com)");
            markError(txtEmail, true);
            return false;
        }
        clearFieldError(emailError);
        markError(txtEmail, false);
        return true;
    }

    private boolean validatePasswordMatch() {
        if (!isEditMode) {
            String pwd     = txtPassword.getText();
            String confirm = txtConfirmPassword.getText();

            if (confirm.isBlank()) {
                clearFieldError(passwordMatchError);
                return false;
            }
            if (!pwd.equals(confirm)) {
                showFieldError(passwordMatchError, "Las contraseñas no coinciden");
                markError(txtConfirmPassword, true);
                return false;
            }
            clearFieldError(passwordMatchError);
            markError(txtConfirmPassword, false);
            return true;
        }
        return true;
    }

    private void showFieldError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
    }

    private void clearFieldError(Label lbl) {
        lbl.setText("");
        lbl.setVisible(false);
    }

    private void markError(Control field, boolean error) {
        if (error) {
            if (!field.getStyleClass().contains("form-input-error"))
                field.getStyleClass().add("form-input-error");
        } else {
            field.getStyleClass().remove("form-input-error");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Acciones
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onSubmit() {
        // ── Validar campos requeridos ──
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String email    = txtEmail.getText().trim();
        String role     = cmbRole.getSelectionModel().getSelectedItem();

        if (fullName.isBlank()) {
            showAlert("Campo requerido", "El nombre completo es obligatorio."); return;
        }
        if (username.isBlank()) {
            showAlert("Campo requerido", "El nombre de usuario es obligatorio."); return;
        }
        if (!validateEmail(email)) return;
        if (role == null) {
            showAlert("Campo requerido", "Selecciona un rol para el usuario."); return;
        }

        if (!isEditMode) {
            String pwd     = txtPassword.getText();
            String confirm = txtConfirmPassword.getText();

            if (pwd.isBlank()) {
                showAlert("Campo requerido", "La contraseña es obligatoria."); return;
            }
            if (pwd.length() < 8) {
                showAlert("Contraseña inválida", "La contraseña debe tener al menos 8 caracteres."); return;
            }
            if (!validatePasswordMatch()) {
                showAlert("Contraseñas no coinciden",
                        "La contraseña y su confirmación no son iguales."); return;
            }

            // TODO: adminApiService.createUser(payload)
            System.out.println("[ADMIN] Crear usuario:");
            System.out.println("  nombreCompleto:  " + fullName);
            System.out.println("  nombreUsuario:   " + username);
            System.out.println("  email:           " + email);
            System.out.println("  rol:             " + role);
            System.out.println("  password:        [REDACTED]");

        } else {
            // TODO: adminApiService.updateUser(userId, payload)
            System.out.println("[ADMIN] Editar usuario:");
            System.out.println("  nombreCompleto:  " + fullName);
            System.out.println("  email:           " + email);
            System.out.println("  rol:             " + role);
        }

        // Construir DTO actualizado/nuevo y disparar callback
        UserDTO result = new UserDTO(
                fullName,
                username,
                role,
                role,
                userToEdit != null ? userToEdit.department() : "",
                email,
                userToEdit != null && userToEdit.isVerified(),
                userToEdit != null ? userToEdit.phone() : "",
                null,
                userToEdit != null ? userToEdit.memberSince() : LocalDate.now(),
                userToEdit != null ? userToEdit.lastAccess()  : LocalDateTime.now(),
                userToEdit != null ? userToEdit.stats()
                        : new UserDTO.UserStats(0, 0, 0)
        );

        if (onSaved != null) onSaved.accept(result);
        closeModal();
    }

    @FXML
    private void onResetPassword() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Resetear contraseña");
        alert.setHeaderText("¿Resetear la contraseña de " + txtFullName.getText() + "?");
        alert.setContentText("Se generará una contraseña temporal y se enviará al correo del usuario.");

        ButtonType btnConfirm = new ButtonType("Resetear", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel  = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnConfirm, btnCancel);
        applyAlertStyle(alert);

        alert.showAndWait().ifPresent(r -> {
            if (r == btnConfirm) {
                // TODO: adminApiService.resetPassword(userId)
                System.out.println("[ADMIN] Resetear contraseña para: "
                        + txtEmail.getText());

                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Contraseña reseteada");
                info.setHeaderText(null);
                info.setContentText("Se ha enviado la nueva contraseña al correo del usuario.");
                applyAlertStyle(info);
                info.showAndWait();
            }
        });
    }

    @FXML
    private void onCancel() {
        closeModal();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private void closeModal() {
        Stage stage = (Stage) btnSubmit.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        applyAlertStyle(a);
        a.showAndWait();
    }

    private void applyAlertStyle(Alert alert) {
        try {
            alert.getDialogPane().getStylesheets().add(
                    Objects.requireNonNull(
                            getClass().getResource("/styles/admin.css")
                    ).toExternalForm()
            );
        } catch (Exception ignored) {}

        alert.getDialogPane().getScene().windowProperty().addListener((obs, o, w) -> {
            if (w instanceof Stage s) {
                var ico = getClass().getResourceAsStream("/images/app-icon.png");
                if (ico != null) s.getIcons().add(new Image(ico));
            }
        });
    }
}