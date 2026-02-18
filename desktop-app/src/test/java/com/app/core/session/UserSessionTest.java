package com.app.core.session;

import com.app.model.LoginResponseDTO;
import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para UserSession - verifica manejo de sesión del usuario
 * que es fundamental para la autenticación contra el backend.
 */
class UserSessionTest {

    private static final Gson gson = new Gson();

    @AfterEach
    void cleanup() {
        UserSession.clear();
    }

    @Test
    @DisplayName("Sesión nueva no está logueada")
    void newSessionNotLoggedIn() {
        assertFalse(UserSession.isLoggedIn());
    }

    @Test
    @DisplayName("clear() limpia todos los datos de sesión")
    void clearResetsSession() {
        UserSession.setUser("test", "Admin");
        UserSession.clear();

        assertFalse(UserSession.isLoggedIn());
        assertEquals("Invitado", UserSession.getUser());
        assertNull(UserSession.getAccessToken());
        assertNull(UserSession.getEmail());
    }

    @Test
    @DisplayName("getUser() retorna 'Invitado' cuando no hay sesión")
    void getUserReturnsGuestWhenEmpty() {
        assertEquals("Invitado", UserSession.getUser());
    }

    @Test
    @DisplayName("setUser() establece usuario y rol")
    void setUserSetsData() {
        UserSession.setUser("diego", "Administrador");

        assertEquals("diego", UserSession.getUser());
        assertEquals("Administrador", UserSession.getRole());
    }

    @Test
    @DisplayName("isAdmin() detecta rol Administrador")
    void isAdminDetectsAdminRole() {
        UserSession.setUser("admin", "Administrador");
        assertTrue(UserSession.isAdmin());
    }

    @Test
    @DisplayName("isAdmin() detecta rol Admin")
    void isAdminDetectsAdminShort() {
        UserSession.setUser("admin", "Admin");
        assertTrue(UserSession.isAdmin());
    }

    @Test
    @DisplayName("isAdmin() retorna false para rol Operativo")
    void isAdminFalseForOperativo() {
        UserSession.setUser("user", "Operativo");
        assertFalse(UserSession.isAdmin());
    }

    @Test
    @DisplayName("getRole() retorna 'Usuario' cuando no hay roles")
    void getRoleDefaultsToUsuario() {
        assertEquals("Usuario", UserSession.getRole());
    }

    @Test
    @DisplayName("getRoles() retorna lista vacía cuando no hay roles")
    void getRolesEmptyByDefault() {
        assertNotNull(UserSession.getRoles());
        assertTrue(UserSession.getRoles().isEmpty());
    }

    @Test
    @DisplayName("setFromLoginResponse() carga todos los datos de login")
    void setFromLoginResponseLoadsAllData() {
        String json = """
                {
                    "access_token": "jwt-access-token",
                    "refresh_token": "jwt-refresh-token",
                    "token_type": "bearer",
                    "expires_in": 3600,
                    "user": {
                        "idUsuario": 5,
                        "nombreCompleto": "Diego Ramirez",
                        "nombreUsuario": "dramirez",
                        "email": "diego@test.com",
                        "roles": ["Administrador", "Operativo"]
                    }
                }
                """;

        LoginResponseDTO loginResponse = gson.fromJson(json, LoginResponseDTO.class);
        UserSession.setFromLoginResponse(loginResponse);

        assertTrue(UserSession.isLoggedIn());
        assertEquals("jwt-access-token", UserSession.getAccessToken());
        assertEquals("jwt-refresh-token", UserSession.getRefreshToken());
        assertEquals(5, UserSession.getUserId());
        assertEquals("Diego Ramirez", UserSession.getNombreCompleto());
        assertEquals("dramirez", UserSession.getUser());
        assertEquals("diego@test.com", UserSession.getEmail());
        assertTrue(UserSession.isAdmin());
        assertEquals(2, UserSession.getRoles().size());
    }

    @Test
    @DisplayName("getNombreCompleto() retorna username cuando nombre es null")
    void getNombreCompletoFallsBackToUsername() {
        UserSession.setUser("test_user", "Operativo");
        assertEquals("test_user", UserSession.getNombreCompleto());
    }

    @Test
    @DisplayName("displayNameProperty es observable y se puede actualizar")
    void displayNamePropertyIsObservable() {
        UserSession.setDisplayName("Nuevo Nombre");
        assertEquals("Nuevo Nombre", UserSession.getDisplayName());
        assertNotNull(UserSession.displayNameProperty());
    }
}
