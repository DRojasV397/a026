package com.app.ui.components.navbar;

import com.app.core.session.UserSession;
import com.app.service.storage.AvatarStorageService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.Objects;

public class NavbarController {

    @FXML private Label     screenTitle;
    @FXML private Label     screenSubtitle;
    @FXML private Label     userNameLabel;
    @FXML private Label     userRoleLabel;
    @FXML private ImageView navbarAvatarView;

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
    }


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