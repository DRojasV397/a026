package com.app.service.auth;

import com.app.model.LoginResponseDTO;
import com.app.service.api.ExternalApiService;

public class AuthService {

    private final ExternalApiService apiService = new ExternalApiService();

    /**
     * Autentica al usuario contra la API JWT.
     *
     * @param user     nombre de usuario o email
     * @param password contrasena
     * @return LoginResponseDTO si el login fue exitoso, null si fallo
     */
    public LoginResponseDTO login(String user, String password) {
        if (user == null || password == null) {
            return null;
        }
        return apiService.login(user, password);
    }
}
