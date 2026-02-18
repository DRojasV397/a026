package com.app.flow;

import com.app.config.ApiConfig;
import com.app.core.session.UserSession;
import com.app.model.LoginResponseDTO;
import com.app.service.auth.AuthService;
import com.app.service.profile.ProfileApiService;
import com.app.service.profile.ProfileApiService.ChangePasswordResponse;
import com.app.service.profile.ProfileApiService.UserProfileResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de flujo del módulo de perfil de usuario contra el backend real.
 * Requiere que la API esté corriendo en localhost:8000.
 *
 * Ejecutar con: mvn failsafe:integration-test -Dit.test=ProfileFlowTest
 */
@Tag("integration")
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Flujo de Perfil de Usuario - Integración con Backend")
class ProfileFlowTest {

    private static final AuthService authService = new AuthService();
    private static final ProfileApiService profileService = new ProfileApiService();

    private static final String VALID_USER = "admin";
    private static final String VALID_PASSWORD = "admin123";

    @BeforeAll
    static void loginFirst() {
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);
        assertNotNull(response, "Se requiere login exitoso para tests de perfil");
        UserSession.setFromLoginResponse(response);
        assertTrue(UserSession.isLoggedIn());
    }

    @AfterAll
    static void cleanup() {
        UserSession.clear();
    }

    // ── Obtener perfil (casos positivos) ────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Obtener perfil del usuario actual")
    void getUserProfileById() throws Exception {
        int userId = UserSession.getUserId();
        assertTrue(userId > 0, "userId debe ser positivo después del login");

        CompletableFuture<UserProfileResponse> future = profileService.getUserById(userId);
        UserProfileResponse profile = future.get(15, TimeUnit.SECONDS);

        assertNotNull(profile, "El perfil no debe ser null");
        assertEquals(userId, profile.idUsuario, "El ID debe coincidir");
        assertNotNull(profile.nombreUsuario, "nombreUsuario no debe ser null");
        assertNotNull(profile.email, "email no debe ser null");
    }

    @Test
    @Order(2)
    @DisplayName("Perfil contiene nombre completo")
    void profileHasFullName() throws Exception {
        int userId = UserSession.getUserId();
        CompletableFuture<UserProfileResponse> future = profileService.getUserById(userId);
        UserProfileResponse profile = future.get(15, TimeUnit.SECONDS);

        assertNotNull(profile);
        assertNotNull(profile.nombreCompleto, "nombreCompleto debe estar presente");
        assertFalse(profile.nombreCompleto.isEmpty(), "nombreCompleto no debe estar vacío");
    }

    @Test
    @Order(3)
    @DisplayName("Perfil contiene email válido")
    void profileHasValidEmail() throws Exception {
        int userId = UserSession.getUserId();
        CompletableFuture<UserProfileResponse> future = profileService.getUserById(userId);
        UserProfileResponse profile = future.get(15, TimeUnit.SECONDS);

        assertNotNull(profile);
        assertNotNull(profile.email);
        assertTrue(profile.email.contains("@"), "email debe contener @");
    }

    // ── Obtener perfil (casos negativos) ─────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("Obtener perfil con ID inexistente retorna null")
    void getUserProfileInvalidId() throws Exception {
        CompletableFuture<UserProfileResponse> future = profileService.getUserById(999999);
        UserProfileResponse profile = future.get(15, TimeUnit.SECONDS);

        assertNull(profile, "Perfil con ID inexistente debe retornar null");
    }

    @Test
    @Order(11)
    @DisplayName("Obtener perfil con ID negativo retorna null")
    void getUserProfileNegativeId() throws Exception {
        CompletableFuture<UserProfileResponse> future = profileService.getUserById(-1);
        UserProfileResponse profile = future.get(15, TimeUnit.SECONDS);

        assertNull(profile, "Perfil con ID negativo debe retornar null");
    }

    // ── Actualizar perfil (casos positivos) ──────────────────────────────

    @Test
    @Order(20)
    @DisplayName("Actualizar nombre completo del perfil")
    void updateProfileName() throws Exception {
        int userId = UserSession.getUserId();

        // Primero obtener el perfil original
        UserProfileResponse original = profileService.getUserById(userId).get(15, TimeUnit.SECONDS);
        assertNotNull(original);

        String originalName = original.nombreCompleto;
        String testName = "Test Nombre Actualizado";

        // Actualizar
        UserProfileResponse updated = profileService.updateProfile(userId, testName, null)
                .get(15, TimeUnit.SECONDS);

        assertNotNull(updated, "La actualización debe retornar perfil actualizado");
        assertEquals(testName, updated.nombreCompleto, "El nombre debe haberse actualizado");

        // Restaurar el nombre original
        profileService.updateProfile(userId, originalName, null).get(15, TimeUnit.SECONDS);
    }

    @Test
    @Order(21)
    @DisplayName("Actualizar email del perfil")
    void updateProfileEmail() throws Exception {
        int userId = UserSession.getUserId();

        // Obtener email original
        UserProfileResponse original = profileService.getUserById(userId).get(15, TimeUnit.SECONDS);
        assertNotNull(original);
        String originalEmail = original.email;

        String testEmail = "test_flow_" + System.currentTimeMillis() + "@test.com";

        // Actualizar
        UserProfileResponse updated = profileService.updateProfile(userId, null, testEmail)
                .get(15, TimeUnit.SECONDS);

        assertNotNull(updated, "La actualización debe retornar perfil");

        // Restaurar email original
        profileService.updateProfile(userId, null, originalEmail).get(15, TimeUnit.SECONDS);
    }

    // ── Cambio de contraseña (casos negativos) ───────────────────────────

    @Test
    @Order(30)
    @DisplayName("Cambio de contraseña falla con contraseña actual incorrecta")
    void changePasswordFailsWithWrongCurrent() throws Exception {
        CompletableFuture<ChangePasswordResponse> future =
                profileService.changePassword("contrasena_incorrecta", "NuevaPass123!", "NuevaPass123!");
        ChangePasswordResponse response = future.get(15, TimeUnit.SECONDS);

        assertNotNull(response);
        assertFalse(response.success, "Debe fallar con contraseña actual incorrecta");
    }

    @Test
    @Order(31)
    @DisplayName("Cambio de contraseña falla cuando nueva y confirmación no coinciden")
    void changePasswordFailsMismatch() throws Exception {
        CompletableFuture<ChangePasswordResponse> future =
                profileService.changePassword(VALID_PASSWORD, "NuevaPass1!", "NuevaPass2!");
        ChangePasswordResponse response = future.get(15, TimeUnit.SECONDS);

        assertNotNull(response);
        assertFalse(response.success, "Debe fallar cuando las contraseñas no coinciden");
    }

    // ── Flujo completo ──────────────────────────────────────────────────

    @Test
    @Order(40)
    @DisplayName("Flujo completo: login → obtener perfil → actualizar → verificar")
    void fullProfileFlow() throws Exception {
        int userId = UserSession.getUserId();

        // 1. Obtener perfil actual
        UserProfileResponse profile = profileService.getUserById(userId).get(15, TimeUnit.SECONDS);
        assertNotNull(profile, "Debe obtener perfil");
        String originalName = profile.nombreCompleto;

        // 2. Actualizar nombre
        String newName = "Flow Test " + System.currentTimeMillis();
        UserProfileResponse updated = profileService.updateProfile(userId, newName, null)
                .get(15, TimeUnit.SECONDS);
        assertNotNull(updated);

        // 3. Verificar que el cambio persiste
        UserProfileResponse reloaded = profileService.getUserById(userId).get(15, TimeUnit.SECONDS);
        assertNotNull(reloaded);
        assertEquals(newName, reloaded.nombreCompleto, "El nombre debe haberse actualizado");

        // 4. Restaurar nombre original
        profileService.updateProfile(userId, originalName, null).get(15, TimeUnit.SECONDS);
    }

    // ── Autenticación expirada ───────────────────────────────────────────

    @Test
    @Order(50)
    @DisplayName("Operación con token inválido falla correctamente")
    void operationWithInvalidTokenFails() throws Exception {
        // Guardar token actual
        String realToken = UserSession.getAccessToken();

        // Poner token inválido temporalmente - necesitamos simular esto
        // Como UserSession es estático, guardamos y restauramos
        UserSession.clear();
        UserSession.setUser("fake", "Operativo");

        // Intentar obtener perfil sin token válido
        CompletableFuture<UserProfileResponse> future = profileService.getUserById(1);
        UserProfileResponse profile = future.get(15, TimeUnit.SECONDS);

        assertNull(profile, "Sin token válido, la operación debe fallar");

        // Restaurar sesión
        UserSession.clear();
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);
        UserSession.setFromLoginResponse(response);
    }
}
