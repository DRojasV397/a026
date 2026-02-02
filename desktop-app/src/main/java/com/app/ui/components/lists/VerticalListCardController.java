package com.app.ui.components.lists;

import com.app.model.ListItem;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Controlador para una card de lista vertical
 */
public class VerticalListCardController {

    @FXML
    private VBox cardContainer;

    @FXML
    private Label listTitle;

    @FXML
    private Label listSubtitle;

    @FXML
    private VBox listContainer;

    /**
     * Establece el título de la lista
     */
    public void setTitle(String title) {
        if (listTitle != null && title != null) {
            listTitle.setText(title);
        }
    }

    /**
     * Establece el subtítulo de la lista
     */
    public void setSubtitle(String subtitle) {
        if (listSubtitle != null && subtitle != null) {
            listSubtitle.setText(subtitle);
            listSubtitle.setVisible(true);
            listSubtitle.setManaged(true);
        } else if (listSubtitle != null) {
            listSubtitle.setVisible(false);
            listSubtitle.setManaged(false);
        }
    }

    /**
     * Carga los items en la lista
     */
    public void loadItems(List<ListItem> items) {
        if (listContainer == null) return;

        listContainer.getChildren().clear();

        if (items == null || items.isEmpty()) {
            showEmptyState();
            return;
        }

        for (ListItem item : items) {
            VBox itemNode = createListItemNode(item);
            listContainer.getChildren().add(itemNode);
        }
    }

    /**
     * Crea un nodo para un item de la lista
     */
    private VBox createListItemNode(ListItem item) {
        VBox itemBox = new VBox(4);
        itemBox.getStyleClass().add("list-item");

        // Título y valor en la misma línea
        javafx.scene.layout.HBox headerBox = new javafx.scene.layout.HBox(8);
        headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Ícono opcional (emoji o clase de estilo)
        if (item.getIconPath() != null && !item.getIconPath().isEmpty()) {
            Label icon = new Label(item.getIconPath());
            icon.getStyleClass().add("list-item-icon");
            headerBox.getChildren().add(icon);
        }

        // Título
        Label titleLabel = new Label(item.getTitle());
        titleLabel.getStyleClass().add("list-item-title");
        javafx.scene.layout.HBox.setHgrow(titleLabel, javafx.scene.layout.Priority.ALWAYS);
        headerBox.getChildren().add(titleLabel);

        // Valor (alineado a la derecha)
        if (item.getValue() != null && !item.getValue().isEmpty()) {
            Label valueLabel = new Label(item.getValue());
            valueLabel.getStyleClass().add("list-item-value");

            // Aplicar estilo según el tipo
            switch (item.getType()) {
                case SUCCESS -> valueLabel.getStyleClass().add("list-item-value-success");
                case WARNING -> valueLabel.getStyleClass().add("list-item-value-warning");
                case INFO -> valueLabel.getStyleClass().add("list-item-value-info");
            }

            headerBox.getChildren().add(valueLabel);
        }

        itemBox.getChildren().add(headerBox);

        // Subtítulo (opcional)
        if (item.getSubtitle() != null && !item.getSubtitle().isEmpty()) {
            Label subtitleLabel = new Label(item.getSubtitle());
            subtitleLabel.getStyleClass().add("list-item-subtitle");
            itemBox.getChildren().add(subtitleLabel);
        }

        return itemBox;
    }

    /**
     * Muestra estado vacío
     */
    private void showEmptyState() {
        VBox emptyBox = new VBox();
        emptyBox.setAlignment(javafx.geometry.Pos.CENTER);
        emptyBox.getStyleClass().add("list-empty-state");

        Label emptyLabel = new Label("No hay elementos");
        emptyLabel.getStyleClass().add("list-empty-text");

        emptyBox.getChildren().add(emptyLabel);
        listContainer.getChildren().add(emptyBox);
    }

    /**
     * Limpia la lista
     */
    public void clear() {
        if (listContainer != null) {
            listContainer.getChildren().clear();
        }
    }
}