package com.app.core.session;

import com.app.model.LoginResponseDTO;

import java.util.List;

public class UserSession {

    private static String accessToken;
    private static String refreshToken;
    private static int userId;
    private static String nombreCompleto;
    private static String nombreUsuario;
    private static String email;
    private static List<String> roles;

    private UserSession() {}

    public static void setFromLoginResponse(LoginResponseDTO response) {
        accessToken = response.getAccessToken();
        refreshToken = response.getRefreshToken();

        LoginResponseDTO.UserInfo user = response.getUser();
        userId = user.getIdUsuario();
        nombreCompleto = user.getNombreCompleto();
        nombreUsuario = user.getNombreUsuario();
        email = user.getEmail();
        roles = user.getRoles();
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

    public static void clear() {
        accessToken = null;
        refreshToken = null;
        userId = 0;
        nombreCompleto = null;
        nombreUsuario = null;
        email = null;
        roles = null;
    }

    public static boolean isLoggedIn() {
        return accessToken != null && nombreUsuario != null;
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
}
