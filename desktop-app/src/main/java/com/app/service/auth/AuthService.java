package com.app.service.auth;

public class AuthService {

    public boolean login(String user, String password) {

        // Simulación de validación compleja
        // Luego aquí iría BD, LDAP, API, etc.
        if (user == null || password == null) {
            return false;
        }

        return user.equals("admin@x") && password.equals("1234");
    }

}
