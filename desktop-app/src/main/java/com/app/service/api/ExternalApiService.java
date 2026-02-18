package com.app.service.api;

import com.app.config.ApiConfig;
import com.app.model.LoginResponseDTO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ExternalApiService {
    private static final Logger logger = LoggerFactory.getLogger(ExternalApiService.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;

    public ExternalApiService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Realiza login contra la API y retorna la respuesta parseada.
     *
     * @param username nombre de usuario o email
     * @param password contrasena
     * @return LoginResponseDTO si el login fue exitoso, null si fallo
     */
    public LoginResponseDTO login(String username, String password) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("username", username);
            body.addProperty("password", password);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ApiConfig.getLoginUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return gson.fromJson(response.body(), LoginResponseDTO.class);
            }

            logger.warn("Login fallido - HTTP {}: {}", response.statusCode(), response.body());
            return null;

        } catch (Exception e) {
            logger.error("Error al conectar con la API de login: {}", e.getMessage());
            return null;
        }
    }
}
