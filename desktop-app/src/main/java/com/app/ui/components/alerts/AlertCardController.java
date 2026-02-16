package com.app.ui.components.alerts;

import com.app.core.navigation.SceneManager;
import com.app.model.Alert;
import com.app.model.AppRoute;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * Controlador para una card individual de alerta.
 * Usa imágenes PNG para los íconos de tipo en lugar de emojis.
 * Rutas esperadas en resources:
 *   /images/alerts/icon-risk.png
 *   /images/alerts/icon-warning.png
 *   /images/alerts/icon-opportunity.png   (mapeado desde SUCCESS por ahora)
 */
public class AlertCardController {

    @FXML private HBox       alertCard;
    @FXML private StackPane  iconContainer;
    @FXML private ImageView  iconImage;
    @FXML private Label      alertTitle;
    @FXML private Label      alertMessage;
    @FXML private Label      alertTimestamp;

    private Alert             alert;
    private Consumer<Alert>   onDismissCallback;

    // ─────────────────────────────────────────────────────────────────────────

    public void setAlert(Alert alert) {
        this.alert = alert;
        if (alert == null) return;

        // Estilo de color según tipo
        alertCard.getStyleClass().add("alert-card-" + alert.getType().getCssClass());
        iconContainer.getStyleClass().add("alert-icon-" + alert.getType().getCssClass());

        // Textos
        alertTitle.setText(alert.getTitle());
        alertMessage.setText(alert.getMessage());
        alertTimestamp.setText(alert.getTimestamp());

        // Ícono: primero intenta la ruta del DTO, luego el default por tipo
        boolean loaded = false;
        if (alert.getIconPath() != null && !alert.getIconPath().isEmpty()) {
            loaded = tryLoadImage(alert.getIconPath());
        }
        if (!loaded) {
            tryLoadImage(defaultIconPath(alert.getType()));
        }
    }

    /**
     * Intenta cargar una imagen en iconImage.
     * @return true si la carga fue exitosa
     */
    private boolean tryLoadImage(String path) {
        try {
            var stream = getClass().getResourceAsStream(path);
            if (stream == null) return false;
            iconImage.setImage(new Image(stream));
            iconImage.setVisible(true);
            iconImage.setManaged(true);
            return true;
        } catch (Exception e) {
            System.err.println("[ALERT CARD] No se pudo cargar ícono: " + path);
            return false;
        }
    }

    /**
     * Ruta del ícono por defecto según el tipo de alerta.
     * Misma convención que AlertsController.typeIconPath().
     */
    private String defaultIconPath(Alert.AlertType type) {
        return switch (type) {
            case ERROR   -> "/images/alerts/icon-risk.png";
            case WARNING -> "/images/alerts/icon-warning.png";
            case SUCCESS -> "/images/alerts/icon-opportunity.png";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onDismiss(ActionEvent event) {
        event.consume();
        if (alert != null) {
            alert.setDismissed(true);
            if (onDismissCallback != null) onDismissCallback.accept(alert);
        }
    }

    @FXML
    private void onCardClick(MouseEvent event) {
        if (event.getTarget().toString().contains("alert-dismiss-btn")) return;
        SceneManager.setContent(
                "/fxml/alerts/AlertsView.fxml",
                "Alertas",
                "Gesti\u00F3n de notificaciones",
                AppRoute.ALERTS
        );
    }

    public void setOnDismissCallback(Consumer<Alert> callback) { this.onDismissCallback = callback; }
    public Alert getAlert() { return alert; }
}