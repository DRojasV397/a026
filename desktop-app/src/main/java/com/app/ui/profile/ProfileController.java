package com.app.ui.profile;

import com.app.core.navigation.SceneManager;
import com.app.core.session.UserSession;
import com.app.model.UserDTO;
import com.app.service.storage.AvatarStorageService;
import com.app.ui.components.AnimatedToggleSwitch;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Objects;

public class ProfileController {

    // ── Cabecera ──────────────────────────────────────────────────────────────
    @FXML private ImageView profileImageView;
    @FXML private Label     lblHeaderName;
    @FXML private Label     lblHeaderRole;
    @FXML private Label     lblHeaderEmail;
    @FXML private Button    btnEditProfile;

    // ── Tabs ──────────────────────────────────────────────────────────────────
    @FXML private Button tabPersonal;
    @FXML private Button tabDashboard;
    @FXML private Button tabSecurity;
    @FXML private Button tabNotifications;

    // ── Secciones ─────────────────────────────────────────────────────────────
    @FXML private VBox sectionPersonal;
    @FXML private VBox sectionDashboard;
    @FXML private VBox sectionSecurity;
    @FXML private VBox sectionNotifications;

    // ── Banner edición ────────────────────────────────────────────────────────
    @FXML private HBox editBanner;

    // ── Formulario: Datos Personales ──────────────────────────────────────────
    @FXML private TextField txtFullName;
    @FXML private TextField txtUsername;
    @FXML private TextField txtRole;       // NUNCA editable
    @FXML private TextField txtDepartment;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;
    @FXML private Label     lblVerifiedStatus;

    // ── Estadísticas ──────────────────────────────────────────────────────────
    @FXML private Label lblStatsDays;
    @FXML private Label lblStatsPredictions;
    @FXML private Label lblStatsReports;

    // ── Dashboard settings ────────────────────────────────────────────────────
    @FXML private ComboBox<String> cmbTheme;
    @FXML private ComboBox<String> cmbDateRange;

    // ── Cabecera — meta ───────────────────────────────────────────────────────────
    @FXML private Label lblMemberSince;
    @FXML private Label lblLastAccess;

    // ── Seguridad — password section ─────────────────────────────────────────────
    @FXML private VBox          passwordSection;
    @FXML private Button        btnChangePassword;
    @FXML private PasswordField txtCurrentPassword;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmPassword;
    @FXML private Label         lblPasswordMatch;

    // ── Estado interno — password ─────────────────────────────────────────────────
    private boolean passwordEditMode = false;

    // ── Toggle buttons  ─────────────────────────────────────────────────
    private AnimatedToggleSwitch tglPredictionsPanel;
    private AnimatedToggleSwitch tgl2FA;
    private AnimatedToggleSwitch tglEmailNotif;
    private AnimatedToggleSwitch tglSystemNotif;
    private AnimatedToggleSwitch tglNotifPredictions;
    private AnimatedToggleSwitch tglNotifReports;
    private AnimatedToggleSwitch tglNotifSecurity;

    @FXML private HBox rowPredictionsPanel;
    @FXML private HBox row2FA;
    @FXML private HBox rowEmailNotif;
    @FXML private HBox rowSystemNotif;
    @FXML private HBox rowNotifPredictions;
    @FXML private HBox rowNotifReports;
    @FXML private HBox rowNotifSecurity;

    // ── Estado interno ────────────────────────────────────────────────────────
    private List<Button> allTabs;
    private List<VBox>   allSections;
    private int          activeTabIndex = 0;
    private boolean      editMode       = false;
    private boolean configLoaded = false;

    /** Snapshot de valores al entrar en modo edición, para detectar cambios */
    private String snapFullName, snapUsername, snapDepartment, snapEmail, snapPhone;

    // Clave → nombre legible para el log / futura llamada a BD
    private enum ConfigKey {
        THEME, DATE_RANGE, PREDICTIONS_PANEL,
        TWO_FACTOR, EMAIL_NOTIF, SYSTEM_NOTIF,
        NOTIF_PREDICTIONS, NOTIF_REPORTS, NOTIF_SECURITY
    }

    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        allTabs     = List.of(tabPersonal, tabDashboard, tabSecurity, tabNotifications);
        allSections = List.of(sectionPersonal, sectionDashboard,
                sectionSecurity, sectionNotifications);

        loadDefaultAvatar();
        populateCombos();
        createToggles();
        displayUser(getMockUserInfo());

        configLoaded = false;
        applyMockConfig(getMockConfig());
        configLoaded = true;

        applyMockConfig(getMockConfig());
    }

    private void createToggles() {
        tglPredictionsPanel  = injectToggle(rowPredictionsPanel);
        tgl2FA               = injectToggle(row2FA);
        tglEmailNotif        = injectToggle(rowEmailNotif);
        tglSystemNotif       = injectToggle(rowSystemNotif);
        tglNotifPredictions  = injectToggle(rowNotifPredictions);
        tglNotifReports      = injectToggle(rowNotifReports);
        tglNotifSecurity     = injectToggle(rowNotifSecurity);

        tglPredictionsPanel.selectedProperty().addListener((obs, o, v) -> onConfigChanged(ConfigKey.PREDICTIONS_PANEL, v));
        tgl2FA.selectedProperty()             .addListener((obs, o, v) -> onConfigChanged(ConfigKey.TWO_FACTOR, v));
        tglEmailNotif.selectedProperty()      .addListener((obs, o, v) -> onConfigChanged(ConfigKey.EMAIL_NOTIF, v));
        tglSystemNotif.selectedProperty()     .addListener((obs, o, v) -> onConfigChanged(ConfigKey.SYSTEM_NOTIF, v));
        tglNotifPredictions.selectedProperty().addListener((obs, o, v) -> onConfigChanged(ConfigKey.NOTIF_PREDICTIONS, v));
        tglNotifReports.selectedProperty()    .addListener((obs, o, v) -> onConfigChanged(ConfigKey.NOTIF_REPORTS, v));
        tglNotifSecurity.selectedProperty()   .addListener((obs, o, v) -> onConfigChanged(ConfigKey.NOTIF_SECURITY, v));
    }

    /**
     * Punto central de persistencia de configuración.
     * Hoy imprime en consola; mañana: configService.update(userId, key, value)
     *
     * @param key   Identificador del ajuste modificado
     * @param value Nuevo valor booleano
     */
    private void onConfigChanged(ConfigKey key, boolean value) {
        if (!configLoaded) return;
        System.out.printf("[CONFIG] %-22s → %s%n", key.name(), value ? "ON" : "OFF");
        // TODO: configService.updateSetting(UserSession.getUserId(), key.name(), value);
    }

    private AnimatedToggleSwitch injectToggle(HBox row) {
        AnimatedToggleSwitch toggle = new AnimatedToggleSwitch();
        row.getChildren().add(toggle);
        return toggle;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CARGA DE DATOS
    // ═════════════════════════════════════════════════════════════════════════

    private void loadDefaultAvatar() {
        // 1. Intentar cargar el avatar guardado del usuario
        String userId = Integer.toString(UserSession.getUserId());
        Image saved = AvatarStorageService.loadAvatar(userId);

        if (saved != null) {
            profileImageView.setImage(saved);
            UserSession.setAvatar(saved); // sincroniza navbar y sidebar al abrir perfil
            return;
        }

        // 2. Fallback: default-avatar del classpath
        try {
            var stream = getClass().getResourceAsStream("/images/default-avatar.png");
            if (stream != null) {
                Image defaultImg = new Image(stream);
                profileImageView.setImage(defaultImg);
                // No propagamos el default a UserSession si ya tiene uno desde login
                if (UserSession.getAvatar() == null) {
                    UserSession.setAvatar(defaultImg);
                }
            }
        } catch (Exception e) {
            System.err.println("No se pudo cargar el avatar por defecto: " + e.getMessage());
        }
    }

    private void displayUser(UserDTO user) {
        // Cabecera
        lblHeaderName.setText(user.fullName());
        lblHeaderRole.setText(user.roleDisplay().toUpperCase());
        lblHeaderEmail.setText(user.email());

        lblMemberSince.setText("📅 Miembro desde " +
                user.memberSince().format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy",
                        new java.util.Locale("es", "MX"))));
        lblLastAccess.setText("⏰ Último acceso " +
                user.lastAccess().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        // Avatar personalizado
        if (user.avatarPath() != null && !user.avatarPath().isBlank()) {
            try {
                var stream = getClass().getResourceAsStream(user.avatarPath());
                if (stream != null) profileImageView.setImage(new Image(stream));
            } catch (Exception e) {
                System.err.println("Avatar de usuario no encontrado, usando default.");
            }
        }

        // Formulario
        txtFullName.setText(user.fullName());
        txtUsername.setText(user.username());
        txtRole.setText(user.role());
        txtDepartment.setText(user.department());
        txtEmail.setText(user.email());
        txtPhone.setText(user.phone());

        // Badge verificado
        updateVerifiedBadge(user.isVerified());

        // Estadísticas
        lblStatsDays.setText(String.valueOf(user.stats().daysActive()));
        lblStatsPredictions.setText(String.format("%,d", user.stats().predictionsGenerated()));
        lblStatsReports.setText(String.valueOf(user.stats().reportsGenerated()));
    }

    /**
     * Centraliza la actualización del badge de verificación.
     * Se llama al cargar y al detectar cambios en el email.
     */
    private void updateVerifiedBadge(boolean verified) {
        lblVerifiedStatus.getStyleClass().removeAll("verified-tag", "unverified-tag");
        if (verified) {
            lblVerifiedStatus.setText("✔ Verificado");
            lblVerifiedStatus.getStyleClass().add("verified-tag");
        } else {
            lblVerifiedStatus.setText("✘ No verificado");
            lblVerifiedStatus.getStyleClass().add("unverified-tag");
        }
    }

    private void populateCombos() {
        cmbTheme.getItems().addAll("Azul corporativo", "Oscuro", "Claro");
        cmbDateRange.getItems().addAll(
                "Últimos 7 días", "Últimos 30 días",
                "Último trimestre", "Este año"
        );

        // ── Listeners de cambio (misma lógica que los toggles) ────────────────
        cmbTheme.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) onConfigChangedString(ConfigKey.THEME, newVal);
                }
        );
        cmbDateRange.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) onConfigChangedString(ConfigKey.DATE_RANGE, newVal);
                }
        );
    }

    /**
     * Variante de onConfigChanged para valores String (ComboBox).
     */
    private void onConfigChangedString(ConfigKey key, String value) {
        if (!configLoaded) return;
        System.out.printf("[CONFIG] %-22s → \"%s\"%n", key.name(), value);
        // TODO: configService.updateSetting(UserSession.getUserId(), key.name(), value);
    }

    /**
     * Aplica la configuración guardada del usuario a los controles de settings.
     * Aquí se reemplazará getMockConfig() por service.getUserConfig(userId).
     */
    private void applyMockConfig(UserConfig config) {
        cmbTheme.getSelectionModel().select(config.theme());
        cmbDateRange.getSelectionModel().select(config.dateRange());
        tglPredictionsPanel.setSelected(config.showPredictionsOnStart());

        tgl2FA.setSelected(config.twoFactorEnabled());

        tglEmailNotif.setSelected(config.emailNotifications());
        tglSystemNotif.setSelected(config.systemNotifications());
        tglNotifPredictions.setSelected(config.notifyPredictions());
        tglNotifReports.setSelected(config.notifyReports());
        tglNotifSecurity.setSelected(config.notifySecurityAlerts());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MODO EDICIÓN
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onEditProfile() {
        if (!editMode) enterEditMode();
        // Si ya está en editMode, el usuario usa los botones Guardar / Cancelar del banner
    }


    private void enterEditMode() {
        // Si no está en la pestaña de editar lo mueve
        requestTabSwitch(0);

        editMode = true;

        // Snapshot de valores actuales para comparación posterior
        snapFullName   = txtFullName.getText();
        snapUsername   = txtUsername.getText();
        snapDepartment = txtDepartment.getText();
        snapEmail      = txtEmail.getText();
        snapPhone      = txtPhone.getText();

        // Habilitar campos editables (txtRole NUNCA se habilita)
        setFieldsEditable(true);

        // Mostrar banner y actualizar botón
        editBanner.setVisible(true);
        editBanner.setManaged(true);
        btnEditProfile.setText("Editando...");
        btnEditProfile.setDisable(true);

        // Estilo "modo edición" a los campos
        applyEditStyle(true);

        // Listener: si el email cambia, marcar como no verificado
        txtEmail.textProperty().addListener((obs, oldVal, newVal) -> {
            if (editMode) {
                boolean sameAsOriginal = newVal.equals(snapEmail);
                updateVerifiedBadge(sameAsOriginal && getMockUserInfo().isVerified());
            }
        });


        // Registrar guard para interceptar navegación externa
        SceneManager.setNavigationGuard(() -> {
            if (hasUnsavedChanges()) {
                boolean confirmed = showUnsavedChangesAlert();
                if (confirmed) {
                    // Restaurar y limpiar antes de salir
                    txtFullName.setText(snapFullName);
                    txtUsername.setText(snapUsername);
                    txtDepartment.setText(snapDepartment);
                    txtEmail.setText(snapEmail);
                    txtPhone.setText(snapPhone);
                    exitEditMode(false);
                }
                return confirmed;
            }
            return true;
        });
    }

    @FXML
    private void onSaveChanges() {
        // TODO: persistir cambios en BD
        // userService.update(buildUpdatedUser());

        // Actualizar cabecera con los nuevos datos
        lblHeaderName.setText(txtFullName.getText());
        lblHeaderEmail.setText(txtEmail.getText());

        // ── Propagar nombre a navbar y sidebar ────────────────────────────────
        UserSession.setDisplayName(txtUsername.getText());

        exitEditMode(false);
        showInfo("Cambios guardados", "Tu perfil ha sido actualizado correctamente.");
    }

    @FXML
    private void onCancelEdit() {
        if (hasUnsavedChanges()) {
            boolean confirmed = showUnsavedChangesAlert();
            if (!confirmed) return;
        }
        // Restaurar snapshot
        txtFullName.setText(snapFullName);
        txtUsername.setText(snapUsername);
        txtDepartment.setText(snapDepartment);
        txtEmail.setText(snapEmail);
        txtPhone.setText(snapPhone);

        exitEditMode(false);
    }

    private void exitEditMode(boolean keepDirty) {
        editMode = false;
        setFieldsEditable(false);
        applyEditStyle(false);

        editBanner.setVisible(false);
        editBanner.setManaged(false);
        btnEditProfile.setText("Editar Perfil");
        btnEditProfile.setDisable(false);

        SceneManager.clearNavigationGuard();
    }

    private void setFieldsEditable(boolean editable) {
        txtFullName.setEditable(editable);
        txtUsername.setEditable(editable);
        // txtRole: NUNCA editable
        txtDepartment.setEditable(editable);
        txtEmail.setEditable(editable);
        txtPhone.setEditable(editable);
    }

    private void applyEditStyle(boolean editing) {
        List<TextField> editableFields = List.of(
                txtFullName, txtUsername, txtDepartment, txtEmail, txtPhone
        );
        for (TextField field : editableFields) {
            if (editing) {
                field.getStyleClass().removeAll("input-view");
                field.getStyleClass().add("input-editing");
            } else {
                field.getStyleClass().removeAll("input-editing");
                field.getStyleClass().add("input-view");
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CAMBIO DE AVATAR
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onChangeAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar foto de perfil");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );

        File selectedFile = chooser.showOpenDialog(profileImageView.getScene().getWindow());
        if (selectedFile == null) return;

        // Obtener userId desde sesión (username como identificador de carpeta)
        String userId = Integer.toString(UserSession.getUserId());;

        // Guardar en sistema de archivos con escritura atómica
        boolean saved = AvatarStorageService.saveAvatar(userId, selectedFile);

        if (saved) {
            // Cargar la imagen ya guardada (desde el path definitivo, no el temporal)
            Image savedImage = AvatarStorageService.loadAvatar(userId);
            if (savedImage != null) {
                profileImageView.setImage(savedImage);
                UserSession.setAvatar(savedImage); // propaga a navbar y sidebar
            }
        } else {
            showError("No se pudo guardar la imagen. Verifica que el archivo sea válido.");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN DE TABS
    // ═════════════════════════════════════════════════════════════════════════

    @FXML private void onTabPersonal()      { requestTabSwitch(0); }
    @FXML private void onTabDashboard()     { requestTabSwitch(1); }
    @FXML private void onTabSecurity()      { requestTabSwitch(2); }
    @FXML private void onTabNotifications() { requestTabSwitch(3); }



    /** Desde el botón "Seguridad" de la cabecera */
    @FXML private void onGoToSecurity()     { requestTabSwitch(2); }

    /**
     * Solicita cambio de tab. Si hay cambios sin guardar en modo edición,
     * pide confirmación antes de continuar.
     */
    private void requestTabSwitch(int targetIndex) {
        if (targetIndex == activeTabIndex) return;

        if (editMode && hasUnsavedChanges()) {
            boolean confirmed = showUnsavedChangesAlert();
            if (!confirmed) return;
            // El usuario aceptó salir: restaurar y salir del modo edición
            txtFullName.setText(snapFullName);
            txtUsername.setText(snapUsername);
            txtDepartment.setText(snapDepartment);
            txtEmail.setText(snapEmail);
            txtPhone.setText(snapPhone);
            exitEditMode(false);
        }

        switchTab(targetIndex);
    }

    private void switchTab(int index) {
        activeTabIndex = index;
        for (int i = 0; i < allTabs.size(); i++) {
            boolean active = (i == index);
            allTabs.get(i).getStyleClass().removeAll("tab-active");
            if (active) allTabs.get(i).getStyleClass().add("tab-active");
            allSections.get(i).setVisible(active);
            allSections.get(i).setManaged(active);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  DETECCIÓN DE CAMBIOS
    // ═════════════════════════════════════════════════════════════════════════

    private boolean hasUnsavedChanges() {
        return !txtFullName.getText().equals(snapFullName)
                || !txtUsername.getText().equals(snapUsername)
                || !txtDepartment.getText().equals(snapDepartment)
                || !txtEmail.getText().equals(snapEmail)
                || !txtPhone.getText().equals(snapPhone);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ALERTAS / DIÁLOGOS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * @return true si el usuario confirma que quiere salir sin guardar.
     */
    private boolean showUnsavedChangesAlert() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cambios sin guardar");
        alert.setHeaderText("Estás a punto de salir de la edición");
        alert.setContentText("Los cambios no guardados se perderán.\n¿Estás completamente seguro?");

        ButtonType btnStay  = new ButtonType("Seguir editando",    ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType btnLeave = new ButtonType("Salir sin guardar",  ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(btnStay, btnLeave);

        applyAlertStyle(alert);

        return alert.showAndWait()
                .filter(btn -> btn == btnLeave)
                .isPresent();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        applyAlertStyle(alert);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        applyAlertStyle(alert);
        alert.showAndWait();
    }

    /**
     * Aplica CSS de la app e icono de la ventana principal a cualquier Alert.
     * Funciona escuchando el evento de mostrado de la ventana del diálogo.
     */
    private void applyAlertStyle(Alert alert) {
        System.out.println("[Debug] Iniciando estilo para alerta: " + alert.getTitle());

        // 1. Aplicar CSS
        try {
            String cssPath = getClass().getResource("/styles/profile.css").toExternalForm();
            alert.getDialogPane().getStylesheets().add(cssPath);
            alert.getDialogPane().getStyleClass().add("custom-alert");
            System.out.println("[Debug] CSS cargado correctamente.");
        } catch (Exception e) {
            System.err.println("[Error] Falló la carga del CSS: " + e.getMessage());
        }

        // 2. Lógica del Icono
        var scene = alert.getDialogPane().getScene();
        if (scene != null) {
            scene.windowProperty().addListener((obs, oldWin, newWin) -> {
                System.out.println("[Debug] Cambio de ventana detectado: " + newWin);
                if (newWin instanceof Stage dialogStage) {
                    injectIcon(dialogStage);
                }
            });

            // Caso crítico: Si la ventana ya existe, el listener no se dispara.
            // Forzamos la ejecución si ya hay una ventana vinculada.
            if (scene.getWindow() instanceof Stage stage) {
                System.out.println("[Debug] Ventana ya presente, inyectando icono directamente.");
                injectIcon(stage);
            }
        }
    }

    private void injectIcon(Stage stage) {
        try {
            var iconStream = getClass().getResourceAsStream("/images/app-icon.png");
            if (iconStream == null) {
                System.err.println("[Error] No se encontró el archivo en: /images/app-icon.png");
                return;
            }
            stage.getIcons().add(new Image(iconStream));
            System.out.println("[Debug] Icono inyectado con éxito en el Stage.");
        } catch (Exception e) {
            System.err.println("[Error] Error al procesar la imagen del icono: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CAMBIO DE CONTRASEÑA
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onRequestPasswordChange() {
        passwordEditMode = true;
        passwordSection.setVisible(true);
        passwordSection.setManaged(true);
        btnChangePassword.setDisable(true);

        // Listener en tiempo real para validar coincidencia
        txtConfirmPassword.textProperty().addListener((obs, o, v) -> validatePasswordMatch());
        txtNewPassword.textProperty().addListener((obs, o, v) -> validatePasswordMatch());

        // Guard: intercepta navegación externa si hay algo escrito
        // (reutilizamos el mismo mecanismo que el formulario)
        SceneManager.setNavigationGuard(() -> {
            if (hasPasswordChanges()) {
                boolean confirmed = showUnsavedChangesAlert();
                if (confirmed) cancelPasswordEdit();
                return confirmed;
            }
            return true;
        });
    }

    @FXML
    private void onSavePassword() {
        String current = txtCurrentPassword.getText();
        String newPwd  = txtNewPassword.getText();
        String confirm = txtConfirmPassword.getText();

        // Validaciones
        if (current.isBlank() || newPwd.isBlank() || confirm.isBlank()) {
            showError("Completa todos los campos de contraseña.");
            return;
        }
        if (!newPwd.equals(confirm)) {
            showError("La nueva contraseña y su confirmación no coinciden.");
            txtConfirmPassword.getStyleClass().add("input-error");
            return;
        }
        if (newPwd.length() < 8) {
            showError("La contraseña debe tener al menos 8 caracteres.");
            return;
        }

        // TODO: authService.changePassword(UserSession.getUserId(), current, newPwd);
        System.out.println("[AUTH] Solicitud de cambio de contraseña enviada.");

        cancelPasswordEdit();
        showInfo("Contraseña actualizada", "Tu contraseña ha sido cambiada correctamente.");
    }

    @FXML
    private void onCancelPassword() {
        if (hasPasswordChanges()) {
            boolean confirmed = showUnsavedChangesAlert();
            if (!confirmed) return;
        }
        cancelPasswordEdit();
    }

    private void cancelPasswordEdit() {
        txtCurrentPassword.clear();
        txtNewPassword.clear();
        txtConfirmPassword.clear();
        lblPasswordMatch.setText("");
        lblPasswordMatch.getStyleClass().removeAll("match-ok", "match-error");
        txtConfirmPassword.getStyleClass().remove("input-error");

        passwordSection.setVisible(false);
        passwordSection.setManaged(false);
        btnChangePassword.setDisable(false);
        passwordEditMode = false;
        SceneManager.clearNavigationGuard();
    }

    private boolean hasPasswordChanges() {
        return !txtCurrentPassword.getText().isBlank()
                || !txtNewPassword.getText().isBlank()
                || !txtConfirmPassword.getText().isBlank();
    }

    private void validatePasswordMatch() {
        String newPwd  = txtNewPassword.getText();
        String confirm = txtConfirmPassword.getText();

        lblPasswordMatch.getStyleClass().removeAll("match-ok", "match-error");
        txtConfirmPassword.getStyleClass().remove("input-error");

        if (confirm.isBlank()) {
            lblPasswordMatch.setText("");
            return;
        }

        if (newPwd.equals(confirm)) {
            lblPasswordMatch.setText("✔ Las contraseñas coinciden");
            lblPasswordMatch.getStyleClass().add("match-ok");
        } else {
            lblPasswordMatch.setText("✘ No coinciden");
            lblPasswordMatch.getStyleClass().add("match-error");
            txtConfirmPassword.getStyleClass().add("input-error");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MOCKS  —  reemplazar por servicios reales
    // ═════════════════════════════════════════════════════════════════════════

    private UserDTO getMockUserInfo() {
        return new UserDTO(
                "Mateo Alexander",
                "malex_sani",
                "Senior Data Analyst",
                "Administrador",
                "Business Intelligence",
                "mateo.alex@sani-bi.com",
                true,
                "+52 55 1234 5678",
                null,
                java.time.LocalDate.of(2023, 3, 15),          // memberSince
                java.time.LocalDateTime.of(2026, 2, 14, 9, 32), // lastAccess
                new UserDTO.UserStats(128, 1042, 45)
        );
    }

    /**
     * Devuelve la configuración guardada del usuario.
     * Sustituir por: configService.findByUserId(currentUser.id())
     */
    private UserConfig getMockConfig() {
        return new UserConfig(
                "Azul corporativo",   // theme
                "Últimos 30 días",    // dateRange
                true,                 // showPredictionsOnStart
                false,                // twoFactorEnabled
                true,                 // emailNotifications
                true,                 // systemNotifications
                true,                 // notifyPredictions
                false,                // notifyReports
                true                  // notifySecurityAlerts
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Record interno para la configuración de usuario
    // ─────────────────────────────────────────────────────────────────────────
    public record UserConfig(
            String  theme,
            String  dateRange,
            boolean showPredictionsOnStart,
            boolean twoFactorEnabled,
            boolean emailNotifications,
            boolean systemNotifications,
            boolean notifyPredictions,
            boolean notifyReports,
            boolean notifySecurityAlerts
    ) {}
}