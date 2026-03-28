package com.app.ui.layout;

import com.app.core.session.UserSession;
import com.app.service.auth.TokenRefreshService;
import com.app.service.offline.ConnectivityService;
import com.app.service.offline.OfflineModeManager;
import com.app.service.storage.AvatarStorageService;
import com.app.ui.components.navbar.NavbarController;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BaseLayoutController {

    private static final Logger logger = LoggerFactory.getLogger(BaseLayoutController.class);

    @FXML
    private StackPane contentPane;

    @FXML
    private BorderPane centerContainer;

    @FXML
    private VBox topContainer;

    @FXML
    private NavbarController navbarController;

    /** Scheduler que renueva el access token antes de que expire. */
    private ScheduledExecutorService tokenRefreshScheduler;

    @FXML
    private void initialize() {
        // Cargar el navbar manualmente
        loadNavbar();
        // Configurar usuario si el navbar se cargó correctamente
        if (navbarController != null) {
            navbarController.setUser(UserSession.getUser());
            navbarController.setUserRole(UserSession.getRole());
        } else {
            logger.error("No se pudo cargar el NavbarController");
        }
        startTokenRefreshScheduler();
        setupOfflineMode();
    }

    /**
     * Inicia un hilo daemon que verifica cada 4 minutos si el token de acceso
     * está próximo a expirar (menos de 5 minutos restantes) y lo renueva automáticamente.
     */
    private void startTokenRefreshScheduler() {
        tokenRefreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-refresh");
            t.setDaemon(true); // Muere cuando la JVM se cierra
            return t;
        });
        tokenRefreshScheduler.scheduleAtFixedRate(() -> {
            // Guard offline: no intentar refresh sin token
            if (UserSession.isOfflineMode()) return;

            if (UserSession.isLoggedIn() && UserSession.isTokenExpiringSoon(5)) {
                logger.info("Token próximo a expirar, renovando...");
                new TokenRefreshService().refreshAsync()
                        .thenAccept(success -> {
                            if (!success) {
                                logger.warn("No se pudo renovar el token automáticamente");
                            }
                        });
            }
        }, 4, 4, TimeUnit.MINUTES);
    }

    /**
     * Configura el banner de modo offline y el poleo de conectividad.
     */
    private void setupOfflineMode() {
        HBox banner = buildOfflineBanner();

        if (OfflineModeManager.isOffline()) {
            showBanner(banner);
        }

        // Listener reactivo: muestra/oculta banner al cambiar modo offline
        OfflineModeManager.offlineProperty().addListener((obs, old, isOffline) ->
                Platform.runLater(() -> {
                    if (isOffline) showBanner(banner); else hideBanner(banner);
                })
        );

        // Cuando la conectividad se restaura, salir del modo offline
        ConnectivityService.onlineProperty().addListener((obs, wasOnline, nowOnline) -> {
            if (Boolean.TRUE.equals(nowOnline) && OfflineModeManager.isOffline()) {
                Platform.runLater(OfflineModeManager::exitOfflineMode);
            }
        });

        // Poleo de conectividad cada 60s (reutiliza tokenRefreshScheduler)
        tokenRefreshScheduler.scheduleAtFixedRate(() -> {
            if (OfflineModeManager.isOffline()) {
                ConnectivityService.checkAsync();
            }
        }, 10, 60, TimeUnit.SECONDS);
    }

    private HBox buildOfflineBanner() {
        HBox banner = new HBox();
        banner.getStyleClass().add("offline-banner");
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(6, 20, 6, 20));

        Label lblStatus = new Label("Sin conexión  •  Modo lectura");
        lblStatus.getStyleClass().add("offline-banner-text");

        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        Label lblTimestamp = new Label("Datos del: " + getCacheTimestamp());
        lblTimestamp.getStyleClass().add("offline-banner-timestamp");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        banner.getChildren().addAll(lblStatus, spacer1, lblTimestamp, spacer2);
        return banner;
    }

    private String getCacheTimestamp() {
        String userId   = String.valueOf(UserSession.getUserId());
        Path   cacheDir = AvatarStorageService.getAppDataPath().resolve("cache").resolve(userId);
        if (!Files.exists(cacheDir)) return "sin fecha";
        try (var stream = Files.list(cacheDir)) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        try { return Files.getLastModifiedTime(p).toInstant(); }
                        catch (IOException e) { return java.time.Instant.MIN; }
                    })
                    .max(Comparator.naturalOrder())
                    .map(instant -> instant.atZone(ZoneId.systemDefault()).format(fmt))
                    .orElse("sin fecha");
        } catch (IOException e) {
            return "sin fecha";
        }
    }

    private void showBanner(HBox b) {
        if (!topContainer.getChildren().contains(b)) {
            topContainer.getChildren().add(0, b);
        }
        b.setVisible(true);
        b.setManaged(true);
    }

    private void hideBanner(HBox b) {
        b.setVisible(false);
        b.setManaged(false);
    }

    private void loadNavbar() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/layout/Navbar.fxml")
            );
            Node navbarNode = loader.load();
            navbarController = loader.getController();

            // Agregar el navbar al VBox topContainer
            StackPane navbarContainer = new StackPane(navbarNode);
            BorderPane.setMargin(navbarContainer, new Insets(15, 20, 15, 20));

            topContainer.getChildren().add(navbarContainer);

        } catch (IOException e) {
            System.err.println("ERROR al cargar Navbar.fxml:");
            e.printStackTrace();
        }
    }

    public void setContent(Node view) {
        contentPane.getChildren().setAll(view);
    }

    /**
     * Actualiza el título del navbar
     */
    public void updateNavbarTitle(String title, String subtitle) {
        navbarController.setTitle(title, subtitle);
    }

}
