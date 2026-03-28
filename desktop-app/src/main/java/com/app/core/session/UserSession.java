package com.app.core.session;

import com.app.model.LoginResponseDTO;

import java.util.List;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;

public class UserSession {

    private static String accessToken;
    private static String refreshToken;
    private static int userId;
    /** Timestamp Unix (segundos) en que expira el access token. 0 = desconocido. */
    private static long tokenExpiryEpoch = 0;
    private static String nombreCompleto;
    private static String nombreUsuario;
    private static String email;
    private static List<String> roles;
    private static String tipo;
    private static List<String> modulos;

    private static boolean offlineMode = false;

    private UserSession() {}

    public static void setFromLoginResponse(LoginResponseDTO response) {
        accessToken = response.getAccessToken();
        refreshToken = response.getRefreshToken();
        tokenExpiryEpoch = System.currentTimeMillis() / 1000 + response.getExpiresIn();

        LoginResponseDTO.UserInfo user = response.getUser();
        userId = user.getIdUsuario();
        nombreCompleto = user.getNombreCompleto();
        nombreUsuario = user.getNombreUsuario();
        email = user.getEmail();
        roles = user.getRoles();
        tipo = user.getTipo();
        modulos = user.getModulos();
    }

    /**
     * Actualiza solo el access token tras un refresh exitoso.
     *
     * @param newToken  nuevo access token JWT
     * @param expiresIn segundos hasta la expiración
     */
    public static void updateAccessToken(String newToken, int expiresIn) {
        accessToken = newToken;
        tokenExpiryEpoch = System.currentTimeMillis() / 1000 + expiresIn;
    }

    /**
     * Retorna true si el access token expira en menos de {@code minutes} minutos.
     * Si no se conoce la expiración, devuelve false (no forzar refresh).
     */
    public static boolean isTokenExpiringSoon(int minutes) {
        if (tokenExpiryEpoch == 0) return false;
        long nowSeconds = System.currentTimeMillis() / 1000;
        return (tokenExpiryEpoch - nowSeconds) < (minutes * 60L);
    }

    public static void setUser(String user, String role) {
        nombreUsuario = user;
        roles = List.of(role);
    }

    public static String getUser() {
        if (nombreUsuario == null || nombreUsuario.isEmpty()) {
            return "Invitado";
        }
        return nombreUsuario;
    }

    public static String getNombreCompleto() {
        if (nombreCompleto == null || nombreCompleto.isEmpty()) {
            return getUser();
        }
        return nombreCompleto;
    }

    public static String getEmail() {
        return email;
    }

    public static int getUserId() {
        return userId;
    }

    public static String getAccessToken() {
        return accessToken;
    }

    public static String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Popula la sesión con identidad offline (sin tokens).
     * Usado al iniciar sesión desde el caché local.
     */
    public static void setOfflineIdentity(String userId, String nombreCompleto,
            String nombreUsuario, String email, List<String> roles, String tipo, List<String> modulos) {
        UserSession.userId         = userId != null ? Integer.parseInt(userId) : 0;
        UserSession.nombreCompleto = nombreCompleto;
        UserSession.nombreUsuario  = nombreUsuario;
        UserSession.email          = email;
        UserSession.roles          = roles;
        UserSession.tipo           = tipo;
        UserSession.modulos        = modulos;
        accessToken    = null;
        refreshToken   = null;
        offlineMode    = true;
    }

    /** Retorna true si la sesión está en modo sin conexión. */
    public static boolean isOfflineMode() {
        return offlineMode;
    }

    public static void clear() {
        accessToken = null;
        refreshToken = null;
        userId = 0;
        tokenExpiryEpoch = 0;
        nombreCompleto = null;
        nombreUsuario = null;
        email = null;
        roles = null;
        tipo = null;
        modulos = null;
        offlineMode = false;
    }

    public static boolean isLoggedIn() {
        return (accessToken != null && nombreUsuario != null)
                || (offlineMode && nombreUsuario != null);
    }

    public static boolean isAdmin() {
        if (roles == null) return false;
        return roles.contains("Administrador") || roles.contains("Admin");
    }

    public static String getRole() {
        if (roles == null || roles.isEmpty()) {
            return "Usuario";
        }
        return roles.get(0);
    }

    public static List<String> getRoles() {
        return roles != null ? roles : List.of();
    }

    public static boolean isPrincipal() {
        return "Principal".equals(tipo);
    }

    public static List<String> getModulos() {
        return modulos != null ? modulos : List.of();
    }

    public static boolean hasModuleAccess(String moduloId) {
        if (isPrincipal()) return true;
        return modulos != null && modulos.contains(moduloId);
    }

    // ── Propiedades observables globales ─────────────────────────────────────
    private static final ObjectProperty<Image> avatarProperty =
            new SimpleObjectProperty<>(null);

    private static final StringProperty displayNameProperty =
            new SimpleStringProperty("");

    // ── API pública ───────────────────────────────────────────────────────────

    public static ObjectProperty<Image> avatarProperty() {
        return avatarProperty;
    }

    public static Image getAvatar() {
        return avatarProperty.get();
    }

    public static void setAvatar(Image image) {
        avatarProperty.set(image);
    }

    public static StringProperty displayNameProperty() {
        return displayNameProperty;
    }

    public static void setDisplayName(String name) {
        displayNameProperty.set(name);
    }

    public static String getDisplayName() {
        return displayNameProperty.get();
    }
}
