package com.app.core.session;

public class UserSession {

    private static String username;

    private UserSession() {}

    public static void setUser(String user) {
        username = user;
    }

    public static String getUser() {
        return username;
    }

    public static void clear() {
        username = null;
    }

    public static boolean isLoggedIn() {
        return username != null;
    }

}
