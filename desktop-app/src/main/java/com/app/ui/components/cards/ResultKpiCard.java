package com.app.ui.components.cards;

import com.app.model.ResultKpiDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Componente reutilizable para KPI Cards de resultados
 */
public class ResultKpiCard {

    /**
     * Crea una KPI card para resultados predictivos
     */
    public static VBox createKpiCard(ResultKpiDTO kpi) {
        VBox card = new VBox(8);
        card.getStyleClass().add("result-kpi-card");
        card.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(card, Priority.ALWAYS);

        // Ícono
        Label icon = new Label(kpi.icon());
        icon.getStyleClass().add("result-kpi-icon");

        // Valor principal
        Label value = new Label(kpi.value());
        value.getStyleClass().addAll("result-kpi-value", kpi.colorClass());

        // Label
        Label label = new Label(kpi.label());
        label.getStyleClass().add("result-kpi-label");
        label.setWrapText(true);

        // Contenedor de valor + trend
        HBox valueRow = new HBox(8);
        valueRow.setAlignment(Pos.CENTER_LEFT);
        valueRow.getChildren().add(value);

        // Agregar indicador de tendencia
        if (kpi.trend() != ResultKpiDTO.TrendType.NEUTRAL) {
            Label trend = new Label(getTrendIcon(kpi.trend()));
            trend.getStyleClass().addAll("result-kpi-trend", getTrendClass(kpi.trend()));
            valueRow.getChildren().add(trend);
        }

        // Subtitle opcional
        if (kpi.subtitle() != null && !kpi.subtitle().isEmpty()) {
            Label subtitle = new Label(kpi.subtitle());
            subtitle.getStyleClass().add("result-kpi-subtitle");
            subtitle.setWrapText(true);
            card.getChildren().addAll(icon, valueRow, label, subtitle);
        } else {
            card.getChildren().addAll(icon, valueRow, label);
        }

        card.setPadding(new Insets(16));

        return card;
    }

    private static String getTrendIcon(ResultKpiDTO.TrendType trend) {
        return switch (trend) {
            case POSITIVE -> "↑";
            case NEGATIVE -> "↓";
            case NEUTRAL -> "";
        };
    }

    private static String getTrendClass(ResultKpiDTO.TrendType trend) {
        return switch (trend) {
            case POSITIVE -> "trend-positive";
            case NEGATIVE -> "trend-negative";
            case NEUTRAL -> "trend-neutral";
        };
    }
}