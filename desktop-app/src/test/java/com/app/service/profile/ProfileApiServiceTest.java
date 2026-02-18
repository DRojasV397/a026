package com.app.service.profile;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para ProfileApiService - verifica deserialización de respuestas
 * del backend de perfil de usuario.
 */
class ProfileApiServiceTest {

    private static final Gson gson = new Gson();

    // ── UserProfileResponse ───────────────────────────────────────────────

    @Test
    @DisplayName("UserProfileResponse deserializa datos completos de usuario")
    void userProfileResponseDeserializes() {
        String json = """
                {
                    "idUsuario": 42,
                    "nombreCompleto": "Diego Ramirez",
                    "nombreUsuario": "dramirez",
                    "email": "diego@empresa.com",
                    "estado": "Activo",
                    "creadoEn": "2023-03-15T10:30:00"
                }
                """;

        ProfileApiService.UserProfileResponse resp =
                gson.fromJson(json, ProfileApiService.UserProfileResponse.class);

        assertEquals(42, resp.idUsuario);
        assertEquals("Diego Ramirez", resp.nombreCompleto);
        assertEquals("dramirez", resp.nombreUsuario);
        assertEquals("diego@empresa.com", resp.email);
        assertEquals("Activo", resp.estado);
        assertEquals("2023-03-15T10:30:00", resp.creadoEn);
    }

    @Test
    @DisplayName("UserProfileResponse maneja campos nulos")
    void userProfileResponseNullFields() {
        String json = """
                {
                    "idUsuario": 1,
                    "nombreCompleto": "Test User",
                    "nombreUsuario": "test",
                    "email": "test@test.com"
                }
                """;

        ProfileApiService.UserProfileResponse resp =
                gson.fromJson(json, ProfileApiService.UserProfileResponse.class);

        assertEquals(1, resp.idUsuario);
        assertNull(resp.estado);
        assertNull(resp.creadoEn);
    }

    // ── ChangePasswordResponse ────────────────────────────────────────────

    @Test
    @DisplayName("ChangePasswordResponse deserializa éxito")
    void changePasswordSuccess() {
        String json = """
                {
                    "message": "Contrasena actualizada exitosamente",
                    "success": true
                }
                """;

        ProfileApiService.ChangePasswordResponse resp =
                gson.fromJson(json, ProfileApiService.ChangePasswordResponse.class);

        assertTrue(resp.success);
        assertEquals("Contrasena actualizada exitosamente", resp.message);
    }

    @Test
    @DisplayName("ChangePasswordResponse deserializa error")
    void changePasswordError() {
        String json = """
                {
                    "message": "Contrasena actual incorrecta",
                    "success": false
                }
                """;

        ProfileApiService.ChangePasswordResponse resp =
                gson.fromJson(json, ProfileApiService.ChangePasswordResponse.class);

        assertFalse(resp.success);
        assertEquals("Contrasena actual incorrecta", resp.message);
    }
}
