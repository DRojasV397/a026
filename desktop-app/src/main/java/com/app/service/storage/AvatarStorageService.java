package com.app.service.storage;

import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Gestiona el almacenamiento de avatares de usuario en el sistema de archivos local.
 *
 * Estructura:
 *   Windows: %APPDATA%/SaniBI/avatars/{userId}/profile.png
 *   Linux:   ~/.local/share/SaniBI/avatars/{userId}/profile.png
 *   macOS:   ~/Library/Application Support/SaniBI/avatars/{userId}/profile.png
 */
public class AvatarStorageService {

    private static final String APP_NAME    = "SANI";
    private static final String AVATAR_DIR  = "avatars";
    private static final String AVATAR_FILE = "profile.png";
    private static final String BACKUP_FILE = "profile.backup.png";

    // ── Resolución de la ruta base por SO ────────────────────────────────────

    /**
     * Devuelve la ruta base de datos de la app según el sistema operativo.
     * Nunca lanza excepción: si no puede determinar el SO, usa el home del usuario.
     */
    public static Path getAppDataPath() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows: %APPDATA%/SaniBI
            String appData = System.getenv("APPDATA");
            if (appData != null) return Path.of(appData, APP_NAME);
        } else if (os.contains("mac")) {
            // macOS: ~/Library/Application Support/SaniBI
            return Path.of(System.getProperty("user.home"),
                    "Library", "Application Support", APP_NAME);
        }
        // Linux y fallback: ~/.local/share/SaniBI
        return Path.of(System.getProperty("user.home"),
                ".local", "share", APP_NAME);
    }

    /**
     * Devuelve la ruta completa al archivo de avatar de un usuario.
     * Ejemplo: C:/Users/alex/AppData/Roaming/SaniBI/avatars/42/profile.png
     */
    public static Path getAvatarPath(String userId) {
        return getAppDataPath().resolve(AVATAR_DIR).resolve(userId).resolve(AVATAR_FILE);
    }

    // ── Operaciones de lectura ────────────────────────────────────────────────

    /**
     * Carga el avatar del usuario como Image de JavaFX.
     *
     * @param userId  Identificador único del usuario (username o ID de BD)
     * @return        Image si existe el archivo, null si no hay avatar guardado
     */
    public static Image loadAvatar(String userId) {
        Path avatarPath = getAvatarPath(userId);

        if (Files.exists(avatarPath)) {
            try (InputStream is = Files.newInputStream(avatarPath)) {
                return new Image(is);
            } catch (IOException e) {
                System.err.printf("[AVATAR] Error al leer avatar de %s: %s%n",
                        userId, e.getMessage());
            }
        }
        return null; // null = usar default-avatar del classpath
    }

    // ── Operaciones de escritura ──────────────────────────────────────────────

    /**
     * Guarda el avatar seleccionado por el usuario.
     * Implementa escritura atómica con backup para evitar corrupción.
     *
     * @param userId   Identificador del usuario
     * @param source   Archivo de imagen seleccionado por el usuario
     * @return         true si el guardado fue exitoso
     */
    public static boolean saveAvatar(String userId, File source) {
        Path targetDir  = getAppDataPath().resolve(AVATAR_DIR).resolve(userId);
        Path targetFile = targetDir.resolve(AVATAR_FILE);
        Path backupFile = targetDir.resolve(BACKUP_FILE);

        try {
            // 1. Crear directorio si no existe
            Files.createDirectories(targetDir);

            // 2. Si ya existe un avatar, moverlo a backup (atómico)
            if (Files.exists(targetFile)) {
                Files.move(targetFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // 3. Copiar el nuevo archivo
            Path sourcePath = source.toPath();
            String extension = getExtension(source.getName()).toLowerCase();

            if (extension.equals("png")) {
                // PNG: copia directa sin recodificar
                Files.copy(sourcePath, targetFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // JPG/WEBP/etc: convertir a PNG para uniformidad
                convertToPng(source, targetFile);
            }

            // 4. Guardado exitoso: eliminar backup
            Files.deleteIfExists(backupFile);

            System.out.printf("[AVATAR] Guardado exitoso para usuario %s en: %s%n",
                    userId, targetFile);
            return true;

        } catch (IOException e) {
            System.err.printf("[AVATAR] Error al guardar avatar de %s: %s%n",
                    userId, e.getMessage());

            // Intentar restaurar backup si algo falló
            tryRestoreBackup(targetFile, backupFile);
            return false;
        }
    }

    // ── Utilidades privadas ───────────────────────────────────────────────────

    private static void convertToPng(File source, Path target) throws IOException {
        BufferedImage buffered = ImageIO.read(source);
        if (buffered == null) {
            throw new IOException("No se pudo leer la imagen: formato no soportado.");
        }
        ImageIO.write(buffered, "PNG", target.toFile());
    }

    private static void tryRestoreBackup(Path target, Path backup) {
        try {
            if (Files.exists(backup)) {
                Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[AVATAR] Backup restaurado tras error.");
            }
        } catch (IOException ex) {
            System.err.println("[AVATAR] No se pudo restaurar el backup: " + ex.getMessage());
        }
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1) : "png";
    }
}