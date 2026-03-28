package com.app.service.offline;

import com.app.core.session.UserSession;
import com.app.service.storage.AvatarStorageService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gestiona el modo sin conexión: identidad persistida en disco y flag global.
 *
 * Identidad en disco: %APPDATA%\SANI\offline_identity.json
 * {"userId":"42","nombreCompleto":"...","nombreUsuario":"...","email":"...",
 *  "roles":["Administrador"],"tipo":"Principal","modulos":["predicciones",...]}
 */
public final class OfflineModeManager {

    private static final Logger logger = LoggerFactory.getLogger(OfflineModeManager.class);

    private static final SimpleBooleanProperty offlineProp = new SimpleBooleanProperty(false);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private OfflineModeManager() {}

    // ── Estado online/offline ─────────────────────────────────────────────────

    public static boolean isOffline() {
        return offlineProp.get();
    }

    /** Propiedad observable del estado offline (para binding en la UI). */
    public static ReadOnlyBooleanProperty offlineProperty() {
        return offlineProp;
    }

    public static void enterOfflineMode() {
        offlineProp.set(true);
    }

    public static void exitOfflineMode() {
        offlineProp.set(false);
    }

    // ── Identidad en disco ────────────────────────────────────────────────────

    /** Retorna true si existe un archivo de identidad offline en disco. */
    public static boolean hasOfflineIdentity() {
        return Files.exists(identityPath());
    }

    /**
     * Guarda la identidad del usuario autenticado en disco para uso offline futuro.
     * Debe llamarse tras un login online exitoso.
     */
    public static void saveIdentity() {
        OfflineIdentity identity = new OfflineIdentity();
        identity.userId        = String.valueOf(UserSession.getUserId());
        identity.nombreCompleto = UserSession.getNombreCompleto();
        identity.nombreUsuario  = UserSession.getUser();
        identity.email          = UserSession.getEmail();
        identity.roles          = UserSession.getRoles();
        identity.tipo           = UserSession.isPrincipal() ? "Principal" : UserSession.getRole();
        identity.modulos        = UserSession.getModulos();

        try {
            Path path = identityPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(identity), StandardCharsets.UTF_8);
            logger.info("[OFFLINE] Identidad guardada para usuario {}", identity.userId);
        } catch (IOException e) {
            logger.warn("[OFFLINE] No se pudo guardar identidad: {}", e.getMessage());
        }
    }

    /**
     * Carga la identidad offline desde disco y la aplica a {@link UserSession}.
     *
     * @return true si la identidad fue cargada correctamente
     */
    public static boolean loadAndApplyIdentity() {
        Path path = identityPath();
        if (!Files.exists(path)) return false;
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            OfflineIdentity identity = gson.fromJson(json, OfflineIdentity.class);
            if (identity == null || identity.nombreUsuario == null) return false;
            UserSession.setOfflineIdentity(
                    identity.userId,
                    identity.nombreCompleto,
                    identity.nombreUsuario,
                    identity.email,
                    identity.roles,
                    identity.tipo,
                    identity.modulos
            );
            logger.info("[OFFLINE] Identidad cargada para usuario {}", identity.userId);
            return true;
        } catch (Exception e) {
            logger.warn("[OFFLINE] No se pudo cargar identidad: {}", e.getMessage());
            return false;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static Path identityPath() {
        return AvatarStorageService.getAppDataPath().resolve("offline_identity.json");
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    private static class OfflineIdentity {
        String userId;
        String nombreCompleto;
        String nombreUsuario;
        String email;
        List<String> roles;
        String tipo;
        List<String> modulos;
    }

    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) { out.nullValue(); return; }
            out.value(value.format(DT_FMT));
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            return LocalDateTime.parse(in.nextString(), DT_FMT);
        }
    }
}
