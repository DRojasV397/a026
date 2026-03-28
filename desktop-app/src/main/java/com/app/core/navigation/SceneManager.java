package com.app.core.navigation;

import com.app.core.theme.ThemeManager;
import com.app.model.AppRoute;
import com.app.ui.layout.BaseLayoutController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class SceneManager {

    private static Stage stage;
    private static BaseLayoutController baseController;

    private static Consumer<AppRoute> onRouteChanged;

    private static AppRoute currentRoute = null;

    public static void setOnRouteChanged(Consumer<AppRoute> listener) {
        onRouteChanged = listener;
    }

    // Interfaz funcional para el guard de navegación
    public interface NavigationGuard {
        /** @return true si la navegación puede proceder, false para cancelarla */
        boolean canLeave();
    }

    private static NavigationGuard navigationGuard;

    /**
     * Caché de vistas por ruta.
     * Evita que el controller se re-instancie en cada cambio de módulo,
     * preservando el estado interno (alertas resueltas, tabs seleccionados, etc.).
     * Se limpia al hacer logout (resetToLogin).
     */
    private static final Map<AppRoute, Parent> viewCache = new HashMap<>();

    public static void setNavigationGuard(NavigationGuard guard) {
        navigationGuard = guard;
    }

    public static void clearNavigationGuard() {
        navigationGuard = null;
    }


    public static void init(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("TT A026");

        stage.setWidth(1000);
        stage.setHeight(620);

        stage.setMinWidth(1000);
        stage.setMinHeight(620);

        stage.setResizable(true);
    }

    public static void showLogin() {
        System.out.println(
                SceneManager.class.getResource("/fxml/login/LoginView.fxml")
        );
        loadScene("/fxml/login/LoginView.fxml");
    }

    public static void showHome() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource("/fxml/layout/BaseLayout.fxml")
            );

            Parent root = loader.load();
            baseController = loader.getController();

            Scene scene = new Scene(root);
            scene.getStylesheets().add("/styles/main.css");
            ThemeManager.register(scene);

            stage.setScene(scene);

            // Cargamos el contenido inicial
            setContent("/fxml/home/HomeView.fxml", "Dashboard", "Resumen general de tu negocio", AppRoute.DASHBOARD);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setContent(String fxmlPath, String title, String subtitle, AppRoute route) {
        // 1. Validación de repetición: Si ya estamos en esta ruta, ignoramos la petición
        if (route != null && route == currentRoute) {
            System.out.println("[Navigation] Bloqueado: Ya te encuentras en " + route);
            return;
        }

        // ── Guard de navegación ──────────────────────────────────
        if (navigationGuard != null && !navigationGuard.canLeave()) {
            return; // El guard mostró su propio diálogo y el usuario canceló
        }
        clearNavigationGuard(); // Limpia el guard tras navegar exitosamente
        // ────────────────────────────────────────────────────────

        try {
            Parent content;

            if (route != null && viewCache.containsKey(route)) {
                // Vista ya cargada: reutilizar el nodo (el controller conserva su estado)
                content = viewCache.get(route);
            } else {
                // Primera carga: crear nuevo controller
                FXMLLoader loader = new FXMLLoader(
                        SceneManager.class.getResource(fxmlPath)
                );
                content = loader.load();
                if (route != null) {
                    viewCache.put(route, content);
                }
            }

            baseController.setContent(content);

            // Actualizar navbar si se proporcionan títulos
            if (title != null && baseController != null) {
                baseController.updateNavbarTitle(title, subtitle);
            }

            // Éxito: Actualizamos la ruta actual solo después de una carga exitosa
            currentRoute = route;

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (onRouteChanged != null && route != null) {
            onRouteChanged.accept(route);
        }

    }

    private static void loadScene(String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(
                    Objects.requireNonNull(SceneManager.class.getResource(fxmlPath))
            );

            Scene scene = new Scene(root);
            scene.getStylesheets().add("/styles/main.css");
            ThemeManager.register(scene);

            stage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void showModal(String fxmlPath, String title, String icono) {
        try {
            Parent root = FXMLLoader.load(
                    Objects.requireNonNull(SceneManager.class.getResource(fxmlPath))
            );

            Scene scene = new Scene(root);
            scene.getStylesheets().add("/styles/main.css");
            scene.getStylesheets().add("/styles/modal.css");
            ThemeManager.register(scene);

            Stage modalStage = new Stage();
            modalStage.setTitle(title);
            modalStage.initOwner(stage);
            modalStage.initModality(Modality.WINDOW_MODAL);
            modalStage.setScene(scene);
            modalStage.setResizable(false);

            /* Icono */
            modalStage.getIcons().add(
                    new Image(
                            Objects.requireNonNull(
                                    SceneManager.class.getResourceAsStream("/images/" + icono)
                            )
                    )
            );

            modalStage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Limpia el estado interno del SceneManager y regresa al login.
     * Llamar siempre después de limpiar UserSession.
     */
    public static void resetToLogin() {
        // Resetear estado de navegación
        currentRoute = null;
        baseController = null;
        navigationGuard = null;
        onRouteChanged = null;
        viewCache.clear();  // Liberar todos los controllers cacheados
        ThemeManager.clearScenes();

        // Cargar la escena de login desde cero
        showLogin();
    }

}
