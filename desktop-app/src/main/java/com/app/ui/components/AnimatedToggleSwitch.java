package com.app.ui.components;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Cursor;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class AnimatedToggleSwitch extends StackPane {

    private static final double TRACK_WIDTH  = 44;
    private static final double TRACK_HEIGHT = 24;
    private static final double THUMB_RADIUS = 9;
    private static final double TRAVEL       = 18;

    private static final String COLOR_OFF   = "#CBD5E1";
    private static final String COLOR_ON    = "#22C55E";
    private static final String SHADOW_ON   = "dropshadow(gaussian, rgba(34,197,94,0.4), 6, 0, 0, 0)";
    private static final String SHADOW_OFF  = "dropshadow(gaussian, rgba(0,0,0,0.15), 3, 0, 0, 1)";

    private final Rectangle track;
    private final Circle    thumb;

    // ── Propiedad observable real (un solo campo, no se recrea) ───────────────
    private final BooleanProperty selectedProperty = new SimpleBooleanProperty(false);

    public AnimatedToggleSwitch() {
        track = new Rectangle(TRACK_WIDTH, TRACK_HEIGHT);
        track.setArcWidth(TRACK_HEIGHT);
        track.setArcHeight(TRACK_HEIGHT);
        track.setStyle("-fx-fill: " + COLOR_OFF + ";");

        thumb = new Circle(THUMB_RADIUS);
        thumb.setStyle("-fx-fill: white; -fx-effect: " + SHADOW_OFF + ";");
        thumb.setTranslateX(-TRAVEL / 2);

        getChildren().addAll(track, thumb);
        setCursor(Cursor.HAND);
        setPrefSize(TRACK_WIDTH, TRACK_HEIGHT);
        setMaxSize(TRACK_WIDTH, TRACK_HEIGHT);

        // Click: invierte el valor de la propiedad
        setOnMouseClicked(e -> selectedProperty.set(!selectedProperty.get()));

        // La animación reacciona a cambios en la propiedad (incluidos los externos)
        selectedProperty.addListener((obs, oldVal, on) -> animateTo(on));
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public BooleanProperty selectedProperty() {
        return selectedProperty;
    }

    public boolean isSelected() {
        return selectedProperty.get();
    }

    public void setSelected(boolean value) {
        selectedProperty.set(value);
    }

    // ── Animación ─────────────────────────────────────────────────────────────

    private void animateTo(boolean on) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(180), thumb);
        tt.setToX(on ? TRAVEL / 2 : -TRAVEL / 2);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.play();

        track.setStyle("-fx-fill: " + (on ? COLOR_ON : COLOR_OFF) + ";");
        thumb.setStyle("-fx-fill: white; -fx-effect: " + (on ? SHADOW_ON : SHADOW_OFF) + ";");
    }
}