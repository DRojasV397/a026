package com.app.core.session;

public class UserSession {

    private static String username;
    private static String userRole;

    private UserSession() {}

    public static void setUser(String user, String role) {
        username = user;
        userRole = role;
    }

    public static String getUser() {
        if(username==null || username.isEmpty()){
            return "Invitado";
        }else{
            return username;
        }
    }

    public static void clear() {
        username = null;
    }

    public static boolean isLoggedIn() {
        return username != null;
    }

    public static boolean isAdmin() {
        return false; //NECESITA AJUSTES CUANDO SE IMPLEMENTE LECTURA DE BASE DE DATOS
    }

    public static String getRole(){
        if(userRole==null || userRole.isEmpty()){
            return "Usuario";
        }else{
            return userRole;
        }
    }

}
