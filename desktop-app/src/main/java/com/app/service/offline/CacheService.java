package com.app.service.offline;

import com.app.service.storage.AvatarStorageService;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Caché en disco para respuestas de la API.
 * Ruta: %APPDATA%\SANI\cache\{userId}\{key}.json
 *
 * Formato en disco:
 * {"key":"dashboard_30d","cachedAt":"2026-03-25T14:30:00","payload":"{...json...}"}
 *
 * El campo {@code payload} almacena el body HTTP original como string.
 */
public final class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    private static final Gson gson = new Gson();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private CacheService() {}

    // ── API pública ───────────────────────────────────────────────────────────

    /** Entrada de caché leída desde disco. */
    public static class CacheEntry {
        public final String key;
        public final String cachedAt;
        public final String payload;

        public CacheEntry(String key, String cachedAt, String payload) {
            this.key      = key;
            this.cachedAt = cachedAt;
            this.payload  = payload;
        }
    }

    /**
     * Guarda el JSON de respuesta en disco.
     * Crea el directorio si no existe. Fallas son silenciosas (no lanza).
     */
    public static void put(String userId, String key, String rawJson) {
        try {
            Path dir = cacheDir(userId);
            Files.createDirectories(dir);
            CacheWrapper wrapper = new CacheWrapper();
            wrapper.key      = key;
            wrapper.cachedAt = LocalDateTime.now().format(FMT);
            wrapper.payload  = rawJson;
            Files.writeString(dir.resolve(key + ".json"), gson.toJson(wrapper), StandardCharsets.UTF_8);
            logger.debug("[CACHE] Guardado: {}/{}", userId, key);
        } catch (IOException e) {
            logger.warn("[CACHE] No se pudo guardar key={}: {}", key, e.getMessage());
        }
    }

    /**
     * Lee una entrada del caché. Retorna null si no existe o hay error.
     */
    public static CacheEntry get(String userId, String key) {
        try {
            Path file = cacheDir(userId).resolve(key + ".json");
            if (!Files.exists(file)) return null;
            String content = Files.readString(file, StandardCharsets.UTF_8);
            CacheWrapper wrapper = gson.fromJson(content, CacheWrapper.class);
            if (wrapper == null || wrapper.payload == null) return null;
            return new CacheEntry(wrapper.key, wrapper.cachedAt, wrapper.payload);
        } catch (Exception e) {
            logger.warn("[CACHE] No se pudo leer key={}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Retorna true si existe al menos un archivo .json en el directorio del usuario.
     */
    public static boolean hasAnyCache(String userId) {
        try {
            Path dir = cacheDir(userId);
            if (!Files.exists(dir)) return false;
            try (var stream = Files.list(dir)) {
                return stream.anyMatch(p -> p.toString().endsWith(".json"));
            }
        } catch (IOException e) {
            return false;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static Path cacheDir(String userId) {
        return AvatarStorageService.getAppDataPath().resolve("cache").resolve(userId);
    }

    /** Wrapper interno para la serialización Gson. */
    private static class CacheWrapper {
        String key;
        String cachedAt;
        String payload;
    }
}
