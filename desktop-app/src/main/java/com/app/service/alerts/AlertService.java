package com.app.service.alerts;

import com.app.model.Alert;
import com.app.model.Alert.AlertType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para gestionar alertas del sistema
 */
public class AlertService {

    // Ejemplo de query - ajusta según tu estructura de BD
    private static final String QUERY_RECENT_ALERTS =
            "SELECT TOP 5 id, type, title, message, icon_path, created_at " +
                    "FROM alerts " +
                    "WHERE dismissed = 0 AND active = 1 " +
                    "ORDER BY created_at DESC";

    /**
     * Obtiene las alertas recientes desde la base de datos
     */
    public List<Alert> getRecentAlerts(Connection connection) {
        List<Alert> alerts = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(QUERY_RECENT_ALERTS);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Alert alert = new Alert();
                alert.setId(rs.getInt("id"));

                // Convertir el tipo desde la BD
                String typeStr = rs.getString("type");
                alert.setType(parseAlertType(typeStr));

                alert.setTitle(rs.getString("title"));
                alert.setMessage(rs.getString("message"));
                alert.setIconPath(rs.getString("icon_path"));

                // Formatear timestamp
                String timestamp = formatTimestamp(rs.getTimestamp("created_at").toLocalDateTime());
                alert.setTimestamp(timestamp);

                alerts.add(alert);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            // En caso de error, retornar lista vacía
        }

        return alerts;
    }

    /**
     * Marca una alerta como descartada en la BD
     */
    public boolean dismissAlert(Connection connection, int alertId) {
        String sql = "UPDATE alerts SET dismissed = 1 WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, alertId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Convierte el tipo de alerta desde string a enum
     */
    private AlertType parseAlertType(String typeStr) {
        return switch (typeStr.toUpperCase()) {
            case "SUCCESS", "EXITO" -> AlertType.SUCCESS;
            case "WARNING", "ADVERTENCIA" -> AlertType.WARNING;
            case "ERROR", "DANGER", "PELIGRO" -> AlertType.ERROR;
            default -> AlertType.WARNING;
        };
    }

    /**
     * Formatea el timestamp de manera legible
     */
    private String formatTimestamp(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        long minutesAgo = java.time.Duration.between(dateTime, now).toMinutes();

        if (minutesAgo < 1) {
            return "Ahora";
        } else if (minutesAgo < 60) {
            return "Hace " + minutesAgo + " min";
        } else if (minutesAgo < 1440) { // menos de 24 horas
            long hoursAgo = minutesAgo / 60;
            return "Hace " + hoursAgo + " h";
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            return dateTime.format(formatter);
        }
    }

    /**
     * Obtiene alertas de ejemplo (para testing sin BD)
     */
    public List<Alert> getMockAlerts() {
        List<Alert> alerts = new ArrayList<>();

        alerts.add(new Alert(
                1,
                AlertType.SUCCESS,
                "Carga exitosa",
                "Los datos de ventas se actualizaron correctamente",
                "/images/alerts/success-icon.png",
                "Hace 2 min"
        ));

        alerts.add(new Alert(
                2,
                AlertType.WARNING,
                "Inventario bajo",
                "El producto BBB tiene menos de 10 unidades en stock",
                "/images/alerts/warning-icon.png",
                "Hace 15 min"
        ));

        alerts.add(new Alert(
                3,
                AlertType.ERROR,
                "Error de sincronización",
                "No se pudo conectar con el servidor de reportes",
                "/images/alerts/error-icon.png",
                "Hace 1 h"
        ));

        return alerts;
    }
}