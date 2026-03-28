package com.app.core.theme;

import javafx.scene.Scene;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Singleton estático que gestiona el tema visual de la aplicación.
 * <p>
 * Usa WeakReference para no retener escenas descartadas y
 * java.util.prefs.Preferences para persistencia entre sesiones.
 */
public final class ThemeManager {

    private static final String DARK_CSS   = "/styles/dark-theme.css";
    private static final String PREF_KEY   = "theme";
    private static final String DEFAULT    = "Azul corporativo";

    private static final List<WeakReference<Scene>> scenes = new ArrayList<>();
    private static String current;

    static {
        current = Preferences.userNodeForPackage(ThemeManager.class)
                              .get(PREF_KEY, DEFAULT);
    }

    private ThemeManager() {}

    /** Registra una escena y le aplica el tema actual de inmediato. */
    public static void register(Scene scene) {
        if (scene == null) return;
        scenes.add(new WeakReference<>(scene));
        applyToScene(scene, current);
    }

    /** Cambia el tema, persiste la preferencia y actualiza todas las escenas registradas. */
    public static void apply(String theme) {
        if (theme == null) return;
        current = theme;
        Preferences.userNodeForPackage(ThemeManager.class).put(PREF_KEY, theme);
        scenes.removeIf(ref -> ref.get() == null);
        for (WeakReference<Scene> ref : scenes) {
            Scene s = ref.get();
            if (s != null) applyToScene(s, theme);
        }
    }

    /** Devuelve el tema actualmente activo. */
    public static String getCurrent() {
        return current;
    }

    /** Limpia la lista de escenas registradas (llamar al hacer logout). */
    public static void clearScenes() {
        scenes.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void applyToScene(Scene scene, String theme) {
        String darkUrl = ThemeManager.class.getResource(DARK_CSS).toExternalForm();
        scene.getStylesheets().remove(darkUrl);
        if ("Oscuro".equals(theme)) {
            scene.getStylesheets().add(darkUrl);
        }
    }
}
