package com.app.model;

/**
 * Modelo de datos para alertas del sistema
 */
public class Alert {

    public enum AlertType {
        SUCCESS("success", "#10B981"),  // Verde
        WARNING("warning", "#F59E0B"),  // Amarillo
        ERROR("error", "#EF4444");      // Rojo

        private final String cssClass;
        private final String color;

        AlertType(String cssClass, String color) {
            this.cssClass = cssClass;
            this.color = color;
        }

        public String getCssClass() {
            return cssClass;
        }

        public String getColor() {
            return color;
        }
    }

    private int id;
    private AlertType type;
    private String title;
    private String message;
    private String iconPath;  // Ruta a la imagen del ícono
    private String timestamp;
    private boolean dismissed;

    public Alert() {}

    public Alert(int id, AlertType type, String title, String message, String iconPath, String timestamp) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.message = message;
        this.iconPath = iconPath;
        this.timestamp = timestamp;
        this.dismissed = false;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public AlertType getType() {
        return type;
    }

    public void setType(AlertType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getIconPath() {
        return iconPath;
    }

    public void setIconPath(String iconPath) {
        this.iconPath = iconPath;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void setDismissed(boolean dismissed) {
        this.dismissed = dismissed;
    }
}