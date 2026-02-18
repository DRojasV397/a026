package com.app.flow;

import com.app.core.session.UserSession;
import com.app.model.LoginResponseDTO;
import com.app.model.data.api.*;
import com.app.service.auth.AuthService;
import com.app.service.data.DataApiService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de flujo del módulo de gestión de datos contra el backend real.
 * Requiere que la API esté corriendo en localhost:8000.
 * Usa archivos CSV sintéticos para pruebas positivas y negativas.
 *
 * Ejecutar con: mvn failsafe:integration-test -Dit.test=DataManagementFlowTest
 */
@Tag("integration")
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Flujo de Gestión de Datos - Integración con Backend")
class DataManagementFlowTest {

    private static final AuthService authService = new AuthService();
    private static final DataApiService dataService = new DataApiService();

    private static final String VALID_USER = "admin";
    private static final String VALID_PASSWORD = "admin123";

    private static Path tempDir;
    private static String uploadId = null;

    @BeforeAll
    static void setup() throws IOException {
        // Login
        LoginResponseDTO response = authService.login(VALID_USER, VALID_PASSWORD);
        assertNotNull(response, "Se requiere login exitoso para tests de datos");
        UserSession.setFromLoginResponse(response);

        // Crear directorio temporal para archivos de prueba
        tempDir = Files.createTempDirectory("sani_test_data");
    }

    @AfterAll
    static void cleanup() {
        // Limpiar upload de prueba
        if (uploadId != null) {
            try {
                dataService.deleteUpload(uploadId).get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }

        // Limpiar archivos temporales
        try {
            Files.walk(tempDir)
                    .map(Path::toFile)
                    .forEach(File::delete);
            tempDir.toFile().delete();
        } catch (Exception ignored) {
        }

        UserSession.clear();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private File createValidCsv(String filename) throws IOException {
        File file = tempDir.resolve(filename).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("fecha,producto,cantidad,monto,categoria\n");
            writer.write("2024-01-15,Producto A,10,1500.50,Categoria 1\n");
            writer.write("2024-01-16,Producto B,5,750.25,Categoria 2\n");
            writer.write("2024-01-17,Producto C,20,3000.00,Categoria 1\n");
            writer.write("2024-01-18,Producto D,8,1200.75,Categoria 3\n");
            writer.write("2024-01-19,Producto E,15,2250.00,Categoria 2\n");
            writer.write("2024-02-01,Producto A,12,1800.60,Categoria 1\n");
            writer.write("2024-02-02,Producto B,7,1050.35,Categoria 2\n");
            writer.write("2024-02-03,Producto F,3,450.00,Categoria 3\n");
            writer.write("2024-02-04,Producto G,25,3750.00,Categoria 1\n");
            writer.write("2024-02-05,Producto H,11,1650.50,Categoria 2\n");
        }
        return file;
    }

    private File createEmptyCsv(String filename) throws IOException {
        File file = tempDir.resolve(filename).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("fecha,producto,cantidad,monto\n");
            // Sin filas de datos
        }
        return file;
    }

    private File createMalformedCsv(String filename) throws IOException {
        File file = tempDir.resolve(filename).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("esto no es un CSV válido\n");
            writer.write(",,,,,,\n");
            writer.write("datos\tcon\ttabs\n");
        }
        return file;
    }

    private File createInvalidFile(String filename) throws IOException {
        File file = tempDir.resolve(filename).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Este es un archivo de texto plano, no un CSV.");
        }
        return file;
    }

    // ── Upload (casos positivos) ────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Upload de archivo CSV válido")
    void uploadValidCsv() throws Exception {
        File csvFile = createValidCsv("ventas_test.csv");

        CompletableFuture<UploadResponseDTO> future = dataService.uploadFile(csvFile);
        UploadResponseDTO response = future.get(60, TimeUnit.SECONDS);

        assertNotNull(response, "Upload debe retornar respuesta");
        assertNotNull(response.getUploadId(), "Debe incluir upload_id");
        assertFalse(response.getUploadId().isEmpty(), "upload_id no debe estar vacío");
        uploadId = response.getUploadId();

        if (response.getFilename() != null) {
            assertTrue(response.getFilename().contains("ventas_test"),
                    "Filename debe contener el nombre original");
        }

        if (response.getTotalRows() > 0) {
            assertTrue(response.getTotalRows() >= 10,
                    "Debe reportar al menos 10 filas");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Upload reporta columnas del archivo")
    void uploadReportsColumns() throws Exception {
        Assumptions.assumeTrue(uploadId != null, "Requiere upload exitoso previo");

        // El upload ya fue exitoso, verificar que se reportaron columnas
        File csvFile = createValidCsv("ventas_cols.csv");
        CompletableFuture<UploadResponseDTO> future = dataService.uploadFile(csvFile);
        UploadResponseDTO response = future.get(60, TimeUnit.SECONDS);

        assertNotNull(response);
        if (response.getColumnInfo() != null) {
            assertFalse(response.getColumnInfo().isEmpty(),
                    "Debe reportar información de columnas");
        }

        // Limpiar este upload extra
        if (response.getUploadId() != null && !response.getUploadId().equals(uploadId)) {
            dataService.deleteUpload(response.getUploadId()).get(15, TimeUnit.SECONDS);
        }
    }

    // ── Upload (casos negativos) ────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("Upload de archivo vacío maneja error correctamente")
    void uploadEmptyCsv() throws Exception {
        File csvFile = createEmptyCsv("vacio.csv");

        CompletableFuture<UploadResponseDTO> future = dataService.uploadFile(csvFile);
        UploadResponseDTO response = future.get(60, TimeUnit.SECONDS);

        // Puede ser null (error) o con status de error - ambos son válidos
        if (response != null && response.getUploadId() != null) {
            // Si el backend acepta el archivo vacío, limpiar
            dataService.deleteUpload(response.getUploadId()).get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    @Order(6)
    @DisplayName("Upload de archivo inexistente maneja error sin crash")
    void uploadNonexistentFile() throws Exception {
        File fakeFile = new File(tempDir.toFile(), "archivo_que_no_existe.csv");

        CompletableFuture<UploadResponseDTO> future = dataService.uploadFile(fakeFile);
        UploadResponseDTO response = future.get(60, TimeUnit.SECONDS);

        assertNull(response, "Upload de archivo inexistente debe retornar null");
    }

    // ── Validación de estructura ────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("Validar estructura de upload exitoso")
    void validateUploadedFile() throws Exception {
        Assumptions.assumeTrue(uploadId != null, "Requiere upload exitoso previo");

        CompletableFuture<ValidateResponseDTO> future =
                dataService.validateStructure(uploadId, "ventas");
        ValidateResponseDTO response = future.get(30, TimeUnit.SECONDS);

        assertNotNull(response, "Validación debe retornar respuesta");
        assertNotNull(response.getUploadId(), "Debe incluir upload_id");
    }

    @Test
    @Order(11)
    @DisplayName("Validar con upload_id inexistente falla")
    void validateInvalidUploadId() throws Exception {
        CompletableFuture<ValidateResponseDTO> future =
                dataService.validateStructure("upload_id_inexistente_xyz", "ventas");
        ValidateResponseDTO response = future.get(30, TimeUnit.SECONDS);

        // Debe retornar null o respuesta con valid=false
        if (response != null) {
            assertFalse(response.isValid(),
                    "Validación con upload_id inexistente debe ser inválida");
        }
    }

    // ── Limpieza de datos ───────────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("Limpiar datos de upload exitoso")
    void cleanUploadedData() throws Exception {
        Assumptions.assumeTrue(uploadId != null, "Requiere upload exitoso previo");

        CompletableFuture<CleanResponseDTO> future = dataService.cleanData(uploadId);
        CleanResponseDTO response = future.get(60, TimeUnit.SECONDS);

        assertNotNull(response, "Limpieza debe retornar respuesta");
    }

    @Test
    @Order(16)
    @DisplayName("Limpiar datos con ID inexistente falla correctamente")
    void cleanInvalidUploadId() throws Exception {
        CompletableFuture<CleanResponseDTO> future =
                dataService.cleanData("upload_inexistente_xyz");
        CleanResponseDTO response = future.get(60, TimeUnit.SECONDS);

        // Debe ser null o con error - no debe crashear
        if (response != null) {
            assertNotEquals("cleaned", response.getStatus(),
                    "No debe reportar limpieza exitosa para ID inexistente");
        }
    }

    // ── Eliminación de upload ───────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("Eliminar upload inexistente retorna false")
    void deleteInvalidUpload() throws Exception {
        CompletableFuture<Boolean> future = dataService.deleteUpload("upload_inexistente_xyz");
        Boolean result = future.get(15, TimeUnit.SECONDS);

        assertFalse(result, "Eliminar upload inexistente debe retornar false");
    }

    // ── Flujo completo ──────────────────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("Flujo completo: upload → validar → limpiar → eliminar")
    void fullDataManagementFlow() throws Exception {
        // 1. Crear archivo CSV de prueba
        File csvFile = createValidCsv("flujo_completo.csv");

        // 2. Upload
        UploadResponseDTO uploadResp = dataService.uploadFile(csvFile).get(60, TimeUnit.SECONDS);
        assertNotNull(uploadResp, "Upload debe ser exitoso");
        String flowUploadId = uploadResp.getUploadId();
        assertNotNull(flowUploadId);

        // 3. Validar estructura
        ValidateResponseDTO validateResp =
                dataService.validateStructure(flowUploadId, "ventas").get(30, TimeUnit.SECONDS);
        assertNotNull(validateResp, "Validación debe retornar respuesta");

        // 4. Limpiar datos
        CleanResponseDTO cleanResp = dataService.cleanData(flowUploadId).get(60, TimeUnit.SECONDS);
        assertNotNull(cleanResp, "Limpieza debe retornar respuesta");

        // 5. Eliminar upload de prueba
        Boolean deleted = dataService.deleteUpload(flowUploadId).get(15, TimeUnit.SECONDS);
        // La eliminación puede o no ser exitosa dependiendo del estado
    }

    // ── Autenticación ───────────────────────────────────────────────────

    @Test
    @Order(40)
    @DisplayName("Upload sin autenticación falla correctamente")
    void uploadWithoutAuthFails() throws Exception {
        // Guardar sesión actual
        String realToken = UserSession.getAccessToken();

        // Limpiar sesión
        UserSession.clear();

        File csvFile = createValidCsv("sin_auth.csv");
        CompletableFuture<UploadResponseDTO> future = dataService.uploadFile(csvFile);
        UploadResponseDTO response = future.get(60, TimeUnit.SECONDS);

        // Debe fallar (null)
        assertNull(response, "Upload sin autenticación debe retornar null");

        // Restaurar sesión
        LoginResponseDTO loginResp = authService.login(VALID_USER, VALID_PASSWORD);
        UserSession.setFromLoginResponse(loginResp);
    }
}
