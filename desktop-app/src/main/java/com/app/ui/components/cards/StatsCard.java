package com.app.ui.components.cards;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class StatsCard {

    public static HBox createStatsCard(
            String emoji,
            String value,
            String label,
            String changeText,
            String colorClass,
            boolean positive
    ) {
        // Card raíz
        HBox card = new HBox(16);
        card.getStyleClass().add("stats-card");
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);

        // Icono
        StackPane icon = new StackPane();
        icon.getStyleClass().addAll("stats-card-icon", colorClass);

        Label iconLabel = new Label(emoji);
        iconLabel.setStyle("-fx-font-size: 24px;");
        icon.getChildren().add(iconLabel);

        // Texto
        VBox textBox = new VBox(4);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stats-card-value");

        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("stats-card-label");

        Label changeLabel = new Label(changeText);
        changeLabel.getStyleClass().add("stats-card-change");
        changeLabel.getStyleClass().add(positive ? "positive" : "negative");

        textBox.getChildren().addAll(valueLabel, labelLabel, changeLabel);

        card.getChildren().addAll(icon, textBox);

        return card;
    }


}
