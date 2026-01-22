package com.app.util;

import java.util.regex.Pattern;

public class ValidationUtil {
    // RFC 5322 simplificado (suficiente para UI)
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private ValidationUtil() {}

    /* ==========================
       EMAIL
       ========================== */

    public static boolean isEmailRequired(String email) {
        return email != null && !email.trim().isEmpty();
    }

    public static boolean isEmailFormatValid(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /* ==========================
       PASSWORD
       ========================== */

    public static boolean isPasswordRequired(String password) {
        return password != null && !password.isEmpty();
    }

    public static boolean isPasswordLengthValid(String password) {
        return password.length() >= 6;
    }
}
