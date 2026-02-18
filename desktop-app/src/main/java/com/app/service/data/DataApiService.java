package com.app.service.data;

import com.app.config.ApiConfig;
import com.app.core.session.UserSession;
import com.app.model.data.api.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para consumir los endpoints de /data de la API FastAPI.
 * Maneja upload de archivos (multipart), validación, limpieza y confirmación.
 */
public class DataApiService {

    private static final Logger logger = LoggerFactory.getLogger(DataApiService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public DataApiService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Sube un archivo al backend (multipart/form-data).
     * POST /data/upload
     */
    public CompletableFuture<UploadResponseDTO> uploadFile(File file) {
        try {
            String boundary = UUID.randomUUID().toString();
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String filename = file.getName();

            // Construir cuerpo multipart manualmente
            String mimeType = filename.endsWith(".csv") ? "text/csv"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

            byte[] multipartBody = buildMultipartBody(boundary, filename, mimeType, fileBytes);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(ApiConfig.getDataUploadUrl()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Bearer " + UserSession.getAccessToken())
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        logger.info("Upload response HTTP {}", response.statusCode());
                        if (response.statusCode() == 200) {
                            return gson.fromJson(response.body(), UploadResponseDTO.class);
                        }
                        logger.warn("Upload failed - HTTP {}: {}", response.statusCode(), response.body());
                        return null;
                    })
                    .exceptionally(ex -> {
                        logger.error("Error al subir archivo: {}", ex.getMessage());
                        return null;
                    });

        } catch (IOException e) {
            logger.error("Error al leer archivo para upload: {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Valida la estructura del archivo contra el tipo de datos esperado.
     * POST /data/validate
     */
    public CompletableFuture<ValidateResponseDTO> validateStructure(String uploadId, String dataType) {
        ValidateRequestDTO request = new ValidateRequestDTO(uploadId, dataType);
        String jsonBody = gson.toJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getDataValidateUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("Validate response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), ValidateResponseDTO.class);
                    }
                    logger.warn("Validate failed - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error al validar estructura: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Ejecuta limpieza de datos en el backend.
     * POST /data/clean
     */
    public CompletableFuture<CleanResponseDTO> cleanData(String uploadId) {
        CleanRequestDTO request = new CleanRequestDTO(uploadId);
        String jsonBody = gson.toJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getDataCleanUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("Clean response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), CleanResponseDTO.class);
                    }
                    logger.warn("Clean failed - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error al limpiar datos: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Confirma la carga e inserta datos en la base de datos.
     * POST /data/confirm
     */
    public CompletableFuture<ConfirmResponseDTO> confirmUpload(
            String uploadId, String dataType, Map<String, String> columnMappings) {
        ConfirmRequestDTO request = new ConfirmRequestDTO(uploadId, dataType, columnMappings);
        String jsonBody = gson.toJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getDataConfirmUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("Confirm response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), ConfirmResponseDTO.class);
                    }
                    logger.warn("Confirm failed - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error al confirmar carga: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Elimina un upload temporal.
     * DELETE /data/{upload_id}
     */
    public CompletableFuture<Boolean> deleteUpload(String uploadId) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getDataDeleteUrl(uploadId)))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .DELETE()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(ex -> {
                    logger.error("Error al eliminar upload: {}", ex.getMessage());
                    return false;
                });
    }

    /**
     * Obtiene vista previa de datos cargados.
     * GET /data/preview/{upload_id}?rows=N
     */
    public CompletableFuture<PreviewResponseDTO> getPreview(String uploadId, int rows) {
        String url = ApiConfig.getDataPreviewUrl(uploadId) + "?rows=" + rows;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("Preview response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), PreviewResponseDTO.class);
                    }
                    logger.warn("Preview failed - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error al obtener preview: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Obtiene reporte de calidad de datos.
     * GET /data/quality-report/{upload_id}
     */
    public CompletableFuture<QualityReportResponseDTO> getQualityReport(String uploadId) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getDataQualityReportUrl(uploadId)))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("QualityReport response HTTP {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), QualityReportResponseDTO.class);
                    }
                    logger.warn("QualityReport failed - HTTP {}: {}", response.statusCode(), response.body());
                    return null;
                })
                .exceptionally(ex -> {
                    logger.error("Error al obtener reporte de calidad: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Obtiene hojas de un archivo Excel cargado.
     * GET /data/sheets/{upload_id}
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<String>> getExcelSheets(String uploadId) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.getDataSheetsUrl(uploadId)))
                .header("Authorization", "Bearer " + UserSession.getAccessToken())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        Map<String, Object> result = gson.fromJson(response.body(),
                                new TypeToken<Map<String, Object>>(){}.getType());
                        Object sheets = result.get("sheets");
                        if (sheets instanceof List) {
                            return (List<String>) sheets;
                        }
                    }
                    return List.<String>of();
                })
                .exceptionally(ex -> {
                    logger.error("Error al obtener hojas Excel: {}", ex.getMessage());
                    return List.of();
                });
    }

    /**
     * Construye el cuerpo multipart/form-data para subir el archivo.
     */
    private byte[] buildMultipartBody(String boundary, String filename, String mimeType, byte[] fileBytes) {
        String CRLF = "\r\n";
        StringBuilder header = new StringBuilder();
        header.append("--").append(boundary).append(CRLF);
        header.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(filename).append("\"").append(CRLF);
        header.append("Content-Type: ").append(mimeType).append(CRLF);
        header.append(CRLF);

        String footer = CRLF + "--" + boundary + "--" + CRLF;

        byte[] headerBytes = header.toString().getBytes();
        byte[] footerBytes = footer.getBytes();

        byte[] result = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, result, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, result, headerBytes.length + fileBytes.length, footerBytes.length);

        return result;
    }
}
