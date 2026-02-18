package com.app.flow;

import com.app.config.ApiConfig;
import com.app.model.LoginResponseDTO;
import com.app.service.api.ExternalApiService;
import com.app.service.auth.AuthService;
import com.app.core.session.UserSession;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de flujo de autenticación contra el backend real.
 * Requiere que la API esté corriendo en localhost:8000.
 *
 * Ejecutar con: mvn failsafe:integration-test -Dit.test=LoginFlowTest
 * O con: mvn test -Dgroups=integration
 */
@Tag("integration")
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Flujo de Login - Integración con Backend")
class LoginFlowTest {

    private static final Gson gson = new Gson();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final AuthService authService = new AuthService();

    // Datos sintéticos para pruebas
    private static final String VALID_USER = "admin";
    private static final String VALID_PASSWORD = "admin123";
    private static final String INVALID_USER = "usuario_inexistente_xyz";
    private static final String INVALID_PASSWORD = "contrasena_erronea_xyz";

    @BeforeEach
    void setup() {
        UserSession.clear();
    }

    @AfterEach
    void cleanup() {
        UserSession.clear();
    }

    // ── Verificación de conectividad ────────────────────────────────────

    @Test
    @Order(0)
    @DisplayName("API está accesible en localhost:8000")
    void apiIsReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ApiConfig.getBaseUrl().replace("/api/v1", "/docs")))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertTrue(response.statusCode() < 500,
                    "La API debe estar corriendo. Código HTTP: " + response.statusCode());
        } catch (Exception e) {
            fail("No se puede conectar a la API en " + ApiConfig.getBaseUrl() +
                    ". Asegúrate de que el backend esté corriendo. Error: " + e.getMessage());
        }
    }

    // ── Casos positivos ─────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Login exitoso con credenciales válidas")
    void loginSuccessWithValidCredentials() {
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);

        assertNotNull(response, "El login con credenciales válidas debe retornar respuesta");
        assertNotNull(response.getAccessToken(), "Debe incluir access_token");
        assertFalse(response.getAccessToken().isEmpty(), "access_token no debe estar vacío");
    }

    @Test
    @Order(2)
    @DisplayName("Login exitoso establece sesión de usuario correctamente")
    void loginSetsUserSession() {
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);
        assertNotNull(response, "Login debe ser exitoso");

        UserSession.setFromLoginResponse(response);

        assertTrue(UserSession.isLoggedIn(), "Usuario debe estar logueado");
        assertNotNull(UserSession.getAccessToken(), "Token debe estar en sesión");
        assertNotNull(UserSession.getUser(), "Username debe estar en sesión");
        assertNotEquals("Invitado", UserSession.getUser(), "No debe ser 'Invitado'");
    }

    @Test
    @Order(3)
    @DisplayName("Token JWT tiene formato válido (3 segmentos)")
    void tokenHasValidJwtFormat() {
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);
        assertNotNull(response);

        String token = response.getAccessToken();
        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT debe tener 3 segmentos separados por punto");
    }

    @Test
    @Order(4)
    @DisplayName("Login exitoso incluye datos del usuario")
    void loginResponseIncludesUserData() {
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);
        assertNotNull(response);

        UserSession.setFromLoginResponse(response);
        assertTrue(UserSession.getUserId() > 0, "userId debe ser positivo");
        assertNotNull(UserSession.getEmail(), "email debe estar presente");
    }

    @Test
    @Order(5)
    @DisplayName("Login exitoso incluye refresh_token")
    void loginIncludesRefreshToken() {
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);
        assertNotNull(response);

        UserSession.setFromLoginResponse(response);
        assertNotNull(UserSession.getRefreshToken(), "refresh_token debe estar presente");
    }

    // ── Casos negativos ─────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("Login falla con usuario incorrecto")
    void loginFailsWithInvalidUser() {
        LoginResponseDTO response = authService.login(INVALID_USER, VALID_PASSWORD);
        assertNull(response, "Login con usuario incorrecto debe retornar null");
    }

    @Test
    @Order(11)
    @DisplayName("Login falla con contraseña incorrecta")
    void loginFailsWithInvalidPassword() {
        LoginResponseDTO response = authService.login(VALID_USER, INVALID_PASSWORD);
        assertNull(response, "Login con contraseña incorrecta debe retornar null");
    }

    @Test
    @Order(12)
    @DisplayName("Login falla con ambos campos incorrectos")
    void loginFailsWithBothInvalid() {
        LoginResponseDTO response = authService.login(INVALID_USER, INVALID_PASSWORD);
        assertNull(response, "Login con ambos campos incorrectos debe retornar null");
    }

    @Test
    @Order(13)
    @DisplayName("Login falla con usuario null")
    void loginFailsWithNullUser() {
        LoginResponseDTO response = authService.login(null, VALID_PASSWORD);
        assertNull(response, "Login con usuario null debe retornar null");
    }

    @Test
    @Order(14)
    @DisplayName("Login falla con contraseña null")
    void loginFailsWithNullPassword() {
        LoginResponseDTO response = authService.login(VALID_USER, null);
        assertNull(response, "Login con contraseña null debe retornar null");
    }

    @Test
    @Order(15)
    @DisplayName("Login falla con campos vacíos")
    void loginFailsWithEmptyFields() {
        LoginResponseDTO response = authService.login("", "");
        assertNull(response, "Login con campos vacíos debe retornar null");
    }

    @Test
    @Order(16)
    @DisplayName("Login falla con espacios en blanco como credenciales")
    void loginFailsWithWhitespace() {
        LoginResponseDTO response = authService.login("   ", "   ");
        assertNull(response, "Login con solo espacios debe retornar null");
    }

    // ── Seguridad ───────────────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("Login con inyección SQL no causa error en servidor")
    void loginSqlInjectionDoesNotCrashServer() {
        LoginResponseDTO response = authService.login("admin' OR '1'='1", "password");
        assertNull(response, "Intento de SQL injection debe fallar el login");
    }

    @Test
    @Order(21)
    @DisplayName("Login con XSS no causa error en servidor")
    void loginXssDoesNotCrashServer() {
        LoginResponseDTO response = authService.login("<script>alert(1)</script>", "password");
        assertNull(response, "Intento de XSS debe fallar el login");
    }

    // ── Flujo completo ──────────────────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("Flujo completo: login → verificar sesión → limpiar sesión")
    void fullLoginFlow() {
        // 1. Verificar que no hay sesión
        assertFalse(UserSession.isLoggedIn());

        // 2. Login
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);
        assertNotNull(response, "Login debe ser exitoso");

        // 3. Establecer sesión
        UserSession.setFromLoginResponse(response);
        assertTrue(UserSession.isLoggedIn());
        assertNotNull(UserSession.getAccessToken());

        // 4. Verificar token contra /auth/verify
        String token = UserSession.getAccessToken();
        try {
            HttpRequest verifyRequest = HttpRequest.newBuilder()
                    .uri(URI.create(ApiConfig.getVerifyUrl()))
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> verifyResponse = httpClient.send(
                    verifyRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, verifyResponse.statusCode(),
                    "Token debe ser válido en /auth/verify");
        } catch (Exception e) {
            fail("Error al verificar token: " + e.getMessage());
        }

        // 5. Limpiar sesión
        UserSession.clear();
        assertFalse(UserSession.isLoggedIn());
        assertNull(UserSession.getAccessToken());
    }

    @Test
    @Order(31)
    @DisplayName("Login fallido no establece sesión")
    void failedLoginDoesNotSetSession() {
        assertFalse(UserSession.isLoggedIn());

        LoginResponseDTO response = authService.login(INVALID_USER, INVALID_PASSWORD);
        assertNull(response);

        // La sesión debe permanecer limpia
        assertFalse(UserSession.isLoggedIn());
        assertEquals("Invitado", UserSession.getUser());
    }
}
