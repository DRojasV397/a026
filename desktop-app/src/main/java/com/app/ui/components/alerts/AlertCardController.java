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
 * Controlador para una card individual de alerta
 */
public class AlertCardController {

    @FXML
    private HBox alertCard;

    @FXML
    private StackPane iconContainer;

    @FXML
    private ImageView iconImage;

    @FXML
    private Label alertTitle;

    @FXML
    private Label alertMessage;

    @FXML
    private Label alertTimestamp;

    private Alert alert;
    private Consumer<Alert> onDismissCallback;

    /**
     * Configura la alerta en la card
     */
    public void setAlert(Alert alert) {
        this.alert = alert;

        if (alert != null) {
            // Configurar el tipo de alerta (color)
            alertCard.getStyleClass().add("alert-card-" + alert.getType().getCssClass());
            iconContainer.getStyleClass().add("alert-icon-" + alert.getType().getCssClass());

            // Configurar textos
            alertTitle.setText(alert.getTitle());
            alertMessage.setText(alert.getMessage());
            alertTimestamp.setText(alert.getTimestamp());

            // Configurar ícono
            if (alert.getIconPath() != null && !alert.getIconPath().isEmpty()) {
                try {
                    Image icon = new Image(getClass().getResourceAsStream(alert.getIconPath()));
                    iconImage.setImage(icon);
                } catch (Exception e) {
                    // Si falla la carga, usar ícono por defecto según tipo
                    setDefaultIcon(alert.getType());
                }
            } else {
                setDefaultIcon(alert.getType());
            }
        }
    }

    /**
     * Establece un ícono por defecto basado en el tipo de alerta
     */
    private void setDefaultIcon(Alert.AlertType type) {
        // Usar emojis como fallback si no hay imagen
        String emoji = switch (type) {
            case SUCCESS -> "✓";
            case WARNING -> "⚠";
            case ERROR -> "✕";
        };

        // Si prefieres no mostrar imagen cuando falla, ocultar ImageView
        iconImage.setVisible(false);
        iconImage.setManaged(false);

        // Crear un label con el emoji
        Label emojiLabel = new Label(emoji);
        emojiLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: white;");
        iconContainer.getChildren().clear();
        iconContainer.getChildren().add(emojiLabel);
    }

    /**
     * Maneja el evento de dismiss de la alerta
     */
    @FXML
    private void onDismiss(ActionEvent event) {
        // Prevenir que el evento se propague a la card
        event.consume();

        if (alert != null) {
            alert.setDismissed(true);

            // Llamar callback si existe
            if (onDismissCallback != null) {
                onDismissCallback.accept(alert);
            }
        }
    }

    /**
     * Establece el callback para cuando se hace dismiss
     */
    public void setOnDismissCallback(Consumer<Alert> callback) {
        this.onDismissCallback = callback;
    }

    /**
     * Obtiene la alerta asociada
     */
    public Alert getAlert() {
        return alert;
    }

    /* Redirige al módulo de alertas */
    @FXML
    private void onCardClick(MouseEvent event) {
        // Prevenir que el clic en el botón dismiss active la navegación
        if (event.getTarget().toString().contains("alert-dismiss-btn")) {
            return;
        }

        // Navegar a la vista de alertas
        SceneManager.setContent(
                "/fxml/alerts/AlertsView.fxml",
                "Alertas",
                "Gestión de notificaciones",
                AppRoute.ALERTS
        );
    }
}