package com.app.ui.admin;

import com.app.model.admin.AdminUserDTO;
import com.app.service.admin.AdminApiService;
import com.app.util.ValidationUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @FXML private VBox         modulesSection;
    @FXML private CheckBox     chkDashboard;
    @FXML private CheckBox     chkDatos;
    @FXML private CheckBox     chkPredicciones;
    @FXML private CheckBox     chkRentabilidad;
    @FXML private CheckBox     chkSimulacion;
    @FXML private CheckBox     chkAlertas;
    @FXML private CheckBox     chkReportes;

    @FXML private HBox passwordSection;
    @FXML private PasswordField txtPassword;
    @FXML private PasswordField txtConfirmPassword;
    @FXML private Label         passwordMatchError;

    @FXML private VBox         resetSection;
    @FXML private Button       btnSubmit;

    // ── Estado ────────────────────────────────────────────────────────────────
    private AdminUserDTO             adminUserToEdit = null;
    private Consumer<AdminUserDTO>   onSaved;
    private boolean                  isEditMode = false;

    private final AdminApiService adminApiService = new AdminApiService();

    // ═════════════════════════════════════════════════════════════════════════
    //  Init publico: llamado desde AdminController
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * @param user     null = modo creacion, non-null = modo edicion
     * @param onSaved  callback con el AdminUserDTO resultante
     */
    public void initForm(AdminUserDTO user, Consumer<AdminUserDTO> onSaved) {
        this.onSaved         = onSaved;
        this.adminUserToEdit = user;
        this.isEditMode      = (user != null);

        // Poblar tipos de usuario
        cmbRole.getItems().addAll("Principal", "Secundario");

        // Listener para mostrar/ocultar seccion de modulos
        cmbRole.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSecundario = "Secundario".equals(newVal);
            modulesSection.setVisible(isSecundario);
            modulesSection.setManaged(isSecundario);
        });

        if (isEditMode) {
            formTitle.setText("Editar usuario");
            formSubtitle.setText("Modifica los datos del usuario");
            btnSubmit.setText("Guardar cambios");

            txtFullName.setText(user.getNombreCompleto());
            txtUsername.setText(user.getNombreUsuario());
            txtUsername.setEditable(false);
            txtEmail.setText(user.getEmail());

            // Seleccionar tipo
            String tipo = user.isPrincipal() ? "Principal" : "Secundario";
            cmbRole.getSelectionModel().select(tipo);

            // Pre-check modulos
            if (!user.isPrincipal()) {
                List<String> userModulos = user.getModulos();
                chkDashboard.setSelected(userModulos.contains("dashboard"));
                chkDatos.setSelected(userModulos.contains("datos"));
                chkPredicciones.setSelected(userModulos.contains("predicciones"));
                chkRentabilidad.setSelected(userModulos.contains("rentabilidad"));
                chkSimulacion.setSelected(userModulos.contains("simulacion"));
                chkAlertas.setSelected(userModulos.contains("alertas"));
                chkReportes.setSelected(userModulos.contains("reportes"));

                modulesSection.setVisible(true);
                modulesSection.setManaged(true);
            }

            // Ocultar campos de contrasena, mostrar reset
            passwordSection.setVisible(false);
            passwordSection.setManaged(false);
            resetSection.setVisible(true);
            resetSection.setManaged(true);

        } else {
            // Modo creacion
            roleRow.setVisible(true);
            roleRow.setManaged(true);
        }

        // Listeners de validacion en tiempo real
        txtEmail.textProperty().addListener((obs, o, v) -> validateEmail(v));
        txtConfirmPassword.textProperty().addListener((obs, o, v) -> validatePasswordMatch());
        txtPassword.textProperty().addListener((obs, o, v) -> validatePasswordMatch());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Modulos helper
    // ═════════════════════════════════════════════════════════════════════════

    private List<String> getSelectedModulos() {
        List<String> modulos = new ArrayList<>();
        if (chkDashboard.isSelected())     modulos.add("dashboard");
        if (chkDatos.isSelected())         modulos.add("datos");
        if (chkPredicciones.isSelected())  modulos.add("predicciones");
        if (chkRentabilidad.isSelected())  modulos.add("rentabilidad");
        if (chkSimulacion.isSelected())    modulos.add("simulacion");
        if (chkAlertas.isSelected())       modulos.add("alertas");
        if (chkReportes.isSelected())      modulos.add("reportes");
        return modulos;
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
            showFieldError(emailError, "Ingresa un correo valido (ej. usuario@dominio.com)");
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
                showFieldError(passwordMatchError, "Las contrasenas no coinciden");
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
        String tipo     = cmbRole.getSelectionModel().getSelectedItem();

        if (fullName.isBlank()) {
            showFormAlert("Campo requerido", "El nombre completo es obligatorio."); return;
        }
        if (username.isBlank()) {
            showFormAlert("Campo requerido", "El nombre de usuario es obligatorio."); return;
        }
        if (!validateEmail(email)) return;
        if (tipo == null) {
            showFormAlert("Campo requerido", "Selecciona un tipo de usuario."); return;
        }

        // Validar que Secundario tenga al menos un modulo
        List<String> modulos = getSelectedModulos();
        if ("Secundario".equals(tipo) && modulos.isEmpty()) {
            showFormAlert("Modulos requeridos",
                    "Selecciona al menos un modulo para el usuario secundario."); return;
        }

        // Deshabilitar boton mientras se procesa
        btnSubmit.setDisable(true);
        btnSubmit.setText("Guardando...");

        if (!isEditMode) {
            String pwd     = txtPassword.getText();
            String confirm = txtConfirmPassword.getText();

            if (pwd.isBlank()) {
                resetSubmitButton();
                showFormAlert("Campo requerido", "La contrasena es obligatoria."); return;
            }
            if (pwd.length() < 8) {
                resetSubmitButton();
                showFormAlert("Contrasena invalida",
                        "La contrasena debe tener al menos 8 caracteres."); return;
            }
            if (!validatePasswordMatch()) {
                resetSubmitButton();
                showFormAlert("Contrasenas no coinciden",
                        "La contrasena y su confirmacion no son iguales."); return;
            }

            // Crear usuario via API
            Map<String, Object> payload = new HashMap<>();
            payload.put("nombreCompleto", fullName);
            payload.put("nombreUsuario", username);
            payload.put("email", email);
            payload.put("password", pwd);
            payload.put("tipo", tipo);
            payload.put("modulos", modulos);

            adminApiService.createUsuario(payload).thenAccept(result -> {
                Platform.runLater(() -> {
                    resetSubmitButton();
                    if (result != null) {
                        if (onSaved != null) onSaved.accept(result);
                        closeModal();
                    } else {
                        showFormAlert("Error al crear",
                                "No se pudo crear el usuario. Verifica que el nombre de usuario y email no esten en uso.");
                    }
                });
            });

        } else {
            // Editar usuario via API
            Map<String, Object> payload = new HashMap<>();
            payload.put("nombreCompleto", fullName);
            payload.put("email", email);
            payload.put("tipo", tipo);
            payload.put("modulos", modulos);

            adminApiService.updateUsuario(adminUserToEdit.getIdUsuario(), payload)
                    .thenAccept(result -> {
                        Platform.runLater(() -> {
                            resetSubmitButton();
                            if (result != null) {
                                if (onSaved != null) onSaved.accept(result);
                                closeModal();
                            } else {
                                showFormAlert("Error al guardar",
                                        "No se pudieron guardar los cambios.");
                            }
                        });
                    });
        }
    }

    private void resetSubmitButton() {
        btnSubmit.setDisable(false);
        btnSubmit.setText(isEditMode ? "Guardar cambios" : "Crear usuario");
    }

    @FXML
    private void onResetPassword() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Resetear contrasena");
        alert.setHeaderText("Resetear la contrasena de " + txtFullName.getText() + "?");
        alert.setContentText("Se generara una contrasena temporal y se enviara al correo del usuario.");

        ButtonType btnConfirm = new ButtonType("Resetear", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel  = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnConfirm, btnCancel);
        applyAlertStyle(alert);

        alert.showAndWait().ifPresent(r -> {
            if (r == btnConfirm) {
                System.out.println("[ADMIN] Resetear contrasena para: "
                        + txtEmail.getText());

                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Contrasena reseteada");
                info.setHeaderText(null);
                info.setContentText("Se ha enviado la nueva contrasena al correo del usuario.");
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

    private void showFormAlert(String title, String msg) {
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
