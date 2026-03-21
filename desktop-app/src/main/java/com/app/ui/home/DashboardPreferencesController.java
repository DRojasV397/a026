package com.app.ui.home;

import com.app.model.dashboard.UserPreferenceItemDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Controller for the Dashboard Personalization dialog.
 * Allows users to toggle visibility and reorder KPI stat cards.
 */
public class DashboardPreferencesController {

    @FXML private VBox statsItemsContainer;
    @FXML private VBox chartsItemsContainer;

    private static final List<String[]> STAT_DEFS = List.of(
            new String[]{"stat_ventas_totales",   "Ventas Totales"},
            new String[]{"stat_utilidad_bruta",   "Utilidad Bruta"},
            new String[]{"stat_compras_totales",  "Compras Totales"},
            new String[]{"stat_margen_operativo", "Margen Operativo"},
            new String[]{"stat_ticket_promedio",  "Ticket Promedio"}
    );

    private static final List<String[]> CHART_DEFS = List.of(
            new String[]{"chart_distribucion_categoria", "Distribución por Categoría"},
            new String[]{"chart_ingresos_gastos",        "Ingresos vs Gastos"},
            new String[]{"chart_rentabilidad",           "Rentabilidad por Categoría"},
            new String[]{"chart_alertas",                "Distribución de Alertas"},
            new String[]{"chart_precision_packs",        "Precisión de Packs"}
    );

    private final List<PrefRow> statRows  = new ArrayList<>();
    private final List<PrefRow> chartRows = new ArrayList<>();
    private Consumer<List<UserPreferenceItemDTO>> onSaveCallback;

    public void setOnSaveCallback(Consumer<List<UserPreferenceItemDTO>> cb) {
        this.onSaveCallback = cb;
    }

    /**
     * Initializes the dialog with the current preference state.
     *
     * @param prefMap current preference map keyed by kpi string
     */
    public void initPreferences(Map<String, UserPreferenceItemDTO> prefMap) {
        statRows.clear();
        chartRows.clear();

        boolean hasStatPrefs = STAT_DEFS.stream().anyMatch(def -> prefMap.containsKey(def[0]));

        if (hasStatPrefs) {
            // Sort stats by their saved orden
            List<String[]> sorted = new ArrayList<>(STAT_DEFS);
            sorted.sort((a, b) -> {
                UserPreferenceItemDTO pa = prefMap.get(a[0]);
                UserPreferenceItemDTO pb = prefMap.get(b[0]);
                int oa = pa != null ? pa.getOrden() : 999;
                int ob = pb != null ? pb.getOrden() : 999;
                return Integer.compare(oa, ob);
            });
            for (String[] def : sorted) {
                UserPreferenceItemDTO pref = prefMap.get(def[0]);
                boolean visible = pref == null || pref.isVisible();
                statRows.add(new PrefRow(def[0], def[1], visible));
            }
        } else {
            // No saved preferences — all visible in default order
            for (String[] def : STAT_DEFS) {
                statRows.add(new PrefRow(def[0], def[1], true));
            }
        }

        // Charts: no ordering, just visibility
        for (String[] def : CHART_DEFS) {
            UserPreferenceItemDTO pref = prefMap.get(def[0]);
            boolean visible = pref == null || pref.isVisible();
            chartRows.add(new PrefRow(def[0], def[1], visible));
        }

        renderStatRows();
        renderChartRows();
    }

    private void renderStatRows() {
        statsItemsContainer.getChildren().clear();
        for (int i = 0; i < statRows.size(); i++) {
            final int idx = i;
            PrefRow row = statRows.get(i);

            row.checkBox = new CheckBox();
            row.checkBox.setSelected(row.visible);

            Label lbl = new Label(row.label);
            lbl.setStyle("-fx-font-size: 13px;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button btnUp = new Button("↑");
            btnUp.setStyle("-fx-font-size: 11px; -fx-padding: 2 7;");
            btnUp.setDisable(i == 0);
            btnUp.setOnAction(e -> { moveUp(idx); renderStatRows(); });

            Button btnDown = new Button("↓");
            btnDown.setStyle("-fx-font-size: 11px; -fx-padding: 2 7;");
            btnDown.setDisable(i == statRows.size() - 1);
            btnDown.setOnAction(e -> { moveDown(idx); renderStatRows(); });

            HBox hbox = new HBox(8);
            hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            hbox.setStyle("-fx-padding: 5 0;");
            hbox.getChildren().addAll(row.checkBox, lbl, spacer, btnUp, btnDown);
            statsItemsContainer.getChildren().add(hbox);
        }
    }

    private void renderChartRows() {
        chartsItemsContainer.getChildren().clear();
        for (PrefRow row : chartRows) {
            row.checkBox = new CheckBox();
            row.checkBox.setSelected(row.visible);

            Label lbl = new Label(row.label);
            lbl.setStyle("-fx-font-size: 13px;");

            HBox hbox = new HBox(8);
            hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            hbox.setStyle("-fx-padding: 5 0;");
            hbox.getChildren().addAll(row.checkBox, lbl);
            chartsItemsContainer.getChildren().add(hbox);
        }
    }

    /** Saves current CheckBox UI states back to row.visible before re-rendering. */
    private void syncCheckboxesToModel() {
        for (PrefRow row : statRows) {
            if (row.checkBox != null) row.visible = row.checkBox.isSelected();
        }
        for (PrefRow row : chartRows) {
            if (row.checkBox != null) row.visible = row.checkBox.isSelected();
        }
    }

    private void moveUp(int idx) {
        if (idx > 0) {
            syncCheckboxesToModel();
            PrefRow tmp = statRows.get(idx - 1);
            statRows.set(idx - 1, statRows.get(idx));
            statRows.set(idx, tmp);
        }
    }

    private void moveDown(int idx) {
        if (idx < statRows.size() - 1) {
            syncCheckboxesToModel();
            PrefRow tmp = statRows.get(idx + 1);
            statRows.set(idx + 1, statRows.get(idx));
            statRows.set(idx, tmp);
        }
    }

    @FXML
    private void onCancel() {
        ((Stage) statsItemsContainer.getScene().getWindow()).close();
    }

    @FXML
    private void onSave() {
        List<UserPreferenceItemDTO> result = new ArrayList<>();

        // Stats in current display order (visible first serves as ordering cue for backend)
        for (PrefRow row : statRows) {
            UserPreferenceItemDTO dto = new UserPreferenceItemDTO();
            dto.setKpi(row.kpi);
            dto.setVisible(row.checkBox.isSelected() ? 1 : 0);
            result.add(dto);
        }
        // Charts (order fixed, only visibility matters)
        for (PrefRow row : chartRows) {
            UserPreferenceItemDTO dto = new UserPreferenceItemDTO();
            dto.setKpi(row.kpi);
            dto.setVisible(row.checkBox.isSelected() ? 1 : 0);
            result.add(dto);
        }

        if (onSaveCallback != null) onSaveCallback.accept(result);
        ((Stage) statsItemsContainer.getScene().getWindow()).close();
    }

    // ── Internal model ────────────────────────────────────────────────────────

    private static class PrefRow {
        String kpi;
        String label;
        boolean visible;
        CheckBox checkBox;

        PrefRow(String kpi, String label, boolean visible) {
            this.kpi     = kpi;
            this.label   = label;
            this.visible = visible;
        }
    }
}
