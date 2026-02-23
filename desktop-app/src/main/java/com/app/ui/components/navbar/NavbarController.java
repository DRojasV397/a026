package com.app.ui.components.navbar;

import com.app.core.navigation.SceneManager;
import com.app.core.session.UserSession;
import com.app.service.storage.AvatarStorageService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Objects;

public class NavbarController {

    @FXML private Label     screenTitle;
    @FXML private Label     screenSubtitle;
    @FXML private Label     userNameLabel;
    @FXML private Label     userRoleLabel;
    @FXML private ImageView navbarAvatarView;
    @FXML private StackPane avatarPane;

    private ContextMenu profileMenu;

    @FXML
    public void initialize() {
        // Cargar avatar por defecto
        loadDefaultAvatar();

        // Nombre desde sesión
        userNameLabel.setText(UserSession.getUser());

        // Binding reactivo: cualquier cambio en UserSession.avatarProperty
        // se refleja automáticamente en el ImageView
        UserSession.avatarProperty().addListener((obs, oldImg, newImg) -> {
            if (newImg != null) {
                navbarAvatarView.setImage(newImg);
            } else {
                loadDefaultAvatar();
            }
        });

        // Binding reactivo para el nombre (por si cambia al guardar perfil)
        UserSession.displayNameProperty().addListener((obs, oldName, newName) -> {
            if (newName != null && !newName.isBlank()) {
                userNameLabel.setText(newName);
            }
        });

        // Construir y registrar el menú contextual
        buildProfileContextMenu();
        registerAvatarClickHandler();
    }

    // ── Menú contextual ───────────────────────────────────────────────────────

    private void buildProfileContextMenu() {
        profileMenu = new ContextMenu();
        profileMenu.getStyleClass().add("profile-context-menu");

        // Opción: Ver perfil
        MenuItem viewProfileItem = new MenuItem("👤  Ver perfil");
        viewProfileItem.setOnAction(e -> onViewProfile());

        // Separador
        SeparatorMenuItem separator = new SeparatorMenuItem();

        // Opción: Cerrar sesión
        MenuItem logoutItem = new MenuItem("🚪  Cerrar sesión");
        logoutItem.getStyleClass().add("logout-menu-item");
        logoutItem.setOnAction(e -> onLogout());

        profileMenu.getItems().addAll(viewProfileItem, separator, logoutItem);
    }

    private void registerAvatarClickHandler() {
        // Solo clic DERECHO sobre el avatar o su contenedor abre el menú
        avatarPane.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                profileMenu.show(avatarPane, event.getScreenX(), event.getScreenY());
                event.consume();
            } else {
                // Clic izquierdo cierra el menú si estaba abierto
                if (profileMenu.isShowing()) profileMenu.hide();
            }
        });
    }

    // ── Acciones del menú ─────────────────────────────────────────────────────

    private void onViewProfile() {
        SceneManager.setContent(
                "/fxml/profile/ProfileView.fxml",
                "Mi perfil",
                "Actualiza tus datos personales, configura tus preferencias y observa tus estadísticas de uso",
                com.app.model.AppRoute.PROFILE   // ajusta el enum a tu proyecto
        );
    }

    private void onLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cerrar sesión");
        alert.setHeaderText("¿Estás seguro que deseas cerrar sesión?");
        alert.setContentText("Se perderán los cambios que no hayan sido guardados.");

        // Personalizar los botones
        ButtonType btnConfirmar = new ButtonType("Cerrar sesión", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar  = new ButtonType("Cancelar",      ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnConfirmar, btnCancelar);

        // Heredar estilos y owner de la ventana principal
        Stage owner = (Stage) avatarPane.getScene().getWindow();
        alert.initOwner(owner);
        alert.initModality(Modality.WINDOW_MODAL);
        Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
        alertStage.getIcons().add(
                new Image(
                        Objects.requireNonNull(
                                getClass().getResourceAsStream("/images/app-icon.png")
                        )
                )
        );

        // Aplicar tu CSS si quieres consistencia visual
        alert.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(
                        getClass().getResource("/styles/main.css")
                ).toExternalForm()
        );

        // Esperar respuesta
        alert.showAndWait().ifPresent(response -> {
            if (response == btnConfirmar) {
                SceneManager.clearNavigationGuard();
                UserSession.clear();
                UserSession.setAvatar(null);
                UserSession.setDisplayName("");
                SceneManager.resetToLogin();
            }
            // Si cancela, no hace nada — el menú simplemente se cierra
        });
    }


    // ── Carga de avatar ───────────────────────────────────────────────────────

    private void loadDefaultAvatar() {
        // 1. Intentar cargar el avatar guardado del usuario
        String userId = Integer.toString(UserSession.getUserId());
        Image saved = AvatarStorageService.loadAvatar(userId);

        if (saved != null) {
            navbarAvatarView.setImage(saved);
            UserSession.setAvatar(saved); // sincroniza navbar y sidebar al abrir perfil
            return;
        }

        // 2. Fallback: default-avatar del classpath
        try {
            var stream = getClass().getResourceAsStream("/images/default-avatar.png");
            if (stream != null) {
                Image defaultImg = new Image(stream);
                navbarAvatarView.setImage(defaultImg);
                // No propagamos el default a UserSession si ya tiene uno desde login
                if (UserSession.getAvatar() == null) {
                    UserSession.setAvatar(defaultImg);
                }
            }
        } catch (Exception e) {
            System.err.println("No se pudo cargar el avatar por defecto: " + e.getMessage());
        }
    }

    // ── API pública (llamada desde BaseLayoutController) ──────────────────────

    public void setTitle(String title, String subtitle) {
        screenTitle.setText(title);
        screenSubtitle.setText(subtitle);
    }

    public void setUser(String user) {
        userNameLabel.setText(user);
    }

    public void setUserRole(String role) {
        userRoleLabel.setText(role);
    }
}