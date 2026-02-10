package com.app.model;

import java.util.List;

/**
 * DTO que mapea la respuesta JSON del endpoint /auth/login/json de la API.
 */
public class LoginResponseDTO {

    private String access_token;
    private String refresh_token;
    private String token_type;
    private int expires_in;
    private UserInfo user;

    public String getAccessToken() {
        return access_token;
    }

    public String getRefreshToken() {
        return refresh_token;
    }

    public String getTokenType() {
        return token_type;
    }

    public int getExpiresIn() {
        return expires_in;
    }

    public UserInfo getUser() {
        return user;
    }

    public static class UserInfo {
        private int idUsuario;
        private String nombreCompleto;
        private String nombreUsuario;
        private String email;
        private List<String> roles;

        public int getIdUsuario() {
            return idUsuario;
        }

        public String getNombreCompleto() {
            return nombreCompleto;
        }

        public String getNombreUsuario() {
            return nombreUsuario;
        }

        public String getEmail() {
            return email;
        }

        public List<String> getRoles() {
            return roles != null ? roles : List.of();
        }
    }
}
