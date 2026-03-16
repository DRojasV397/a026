package com.app.service.reports;

import com.app.model.reports.ReportDTO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Persiste metadatos de reportes generados en registry.json.
 * Archivo: %USERPROFILE%/Documents/Herradura/Reportes/registry.json
 *
 * El registro se filtra automáticamente al cargar: se eliminan entradas
 * cuyos archivos ya no existen en disco.
 */
public class ReportRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(ReportRegistryService.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Gson gson;
    private final Path registryPath;

    public ReportRegistryService() {
        gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();

        registryPath = reportsDir().resolve("registry.json");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Carga todos los reportes registrados y filtra los que ya no existen en disco.
     */
    public List<ReportDTO> loadAll() {
        if (!Files.exists(registryPath)) return new ArrayList<>();
        try {
            String json = Files.readString(registryPath, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<ReportDTO>>() {}.getType();
            List<ReportDTO> all = gson.fromJson(json, listType);
            if (all == null) return new ArrayList<>();

            return all.stream()
                    .filter(r -> r.filePath() != null && Files.exists(Path.of(r.filePath())))
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (Exception e) {
            logger.error("Error al cargar registry.json: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Agrega un reporte al registro (al inicio de la lista) y persiste.
     */
    public void add(ReportDTO report) {
        List<ReportDTO> all = loadAll();
        all.add(0, report);
        persist(all);
    }

    /**
     * Elimina un reporte del registro por su ID y persiste.
     */
    public void remove(long id) {
        List<ReportDTO> all = loadAll();
        all.removeIf(r -> r.id() == id);
        persist(all);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void persist(List<ReportDTO> reports) {
        try {
            Files.createDirectories(registryPath.getParent());
            Files.writeString(registryPath, gson.toJson(reports), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Error al persistir registry.json: {}", e.getMessage());
        }
    }

    private static Path reportsDir() {
        return Paths.get(System.getProperty("user.home"), "Documents", "Herradura", "Reportes");
    }

    // ── LocalDateTime TypeAdapter ─────────────────────────────────────────────

    private class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(DT_FMT));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.parse(in.nextString(), DT_FMT);
        }
    }
}
