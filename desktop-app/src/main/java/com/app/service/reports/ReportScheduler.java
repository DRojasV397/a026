package com.app.service.reports;

import com.app.model.reports.ReportDTO;
import com.app.model.reports.ScheduledReportDTO;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Singleton que ejecuta reportes automáticos con ScheduledExecutorService.
 * Persiste la lista de programaciones en schedules.json.
 * Verifica cada 60 s si alguna programación activa debe ejecutarse.
 */
public class ReportScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ReportScheduler.class);
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String SCHEDULES_FILE = "schedules.json";

    // Singleton
    private static final ReportScheduler INSTANCE = new ReportScheduler();
    public  static ReportScheduler getInstance() { return INSTANCE; }

    private final Gson gson;
    private final Path schedulesPath;

    private ScheduledExecutorService executor;
    private ReportGeneratorService  generatorRef;
    private com.app.service.reports.ReportsService   serviceRef;
    private ReportRegistryService   registryRef;
    private Consumer<ReportDTO>     onGeneratedCallback;
    private boolean started = false;

    private final AtomicLong idCounter = new AtomicLong(1);

    private ReportScheduler() {
        gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
        schedulesPath = reportsDir().resolve(SCHEDULES_FILE);

        // Seed idCounter with max existing id
        List<ScheduledReportDTO> existing = loadSchedules();
        long maxId = existing.stream().mapToLong(ScheduledReportDTO::id).max().orElse(0);
        idCounter.set(maxId + 1);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public synchronized void start(ReportGeneratorService gen,
                                    com.app.service.reports.ReportsService svc,
                                    ReportRegistryService registry,
                                    Consumer<ReportDTO> onGenerated) {
        if (started) return;
        this.generatorRef       = gen;
        this.serviceRef         = svc;
        this.registryRef        = registry;
        this.onGeneratedCallback = onGenerated;

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "report-scheduler");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::tick, 60, 60, TimeUnit.SECONDS);
        started = true;
        logger.info("ReportScheduler iniciado.");
    }

    public synchronized void stop() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        started = false;
        logger.info("ReportScheduler detenido.");
    }

    // ── CRUD de programaciones ────────────────────────────────────────────────

    public synchronized List<ScheduledReportDTO> loadSchedules() {
        if (!Files.exists(schedulesPath)) return new ArrayList<>();
        try {
            String json = Files.readString(schedulesPath, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<ScheduledReportDTO>>() {}.getType();
            List<ScheduledReportDTO> list = gson.fromJson(json, listType);
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error al cargar schedules.json: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Guarda una programación nueva (id=0) o existente (id>0). */
    public synchronized void saveSchedule(ScheduledReportDTO s) {
        List<ScheduledReportDTO> all = loadSchedules();
        if (s.id() <= 0) {
            long newId = idCounter.getAndIncrement();
            s = withId(s, newId);
        }
        // Remove old entry if updating
        final long targetId = s.id();
        all.removeIf(x -> x.id() == targetId);
        // Compute nextExecution
        s = withNextExecution(s, computeNextExecution(s));
        all.add(s);
        persistSchedules(all);
    }

    public synchronized void updateSchedule(ScheduledReportDTO s) {
        saveSchedule(s);
    }

    public synchronized void deleteSchedule(long id) {
        List<ScheduledReportDTO> all = loadSchedules();
        all.removeIf(x -> x.id() == id);
        persistSchedules(all);
    }

    // ── Timer tick ────────────────────────────────────────────────────────────

    private void tick() {
        try {
            List<ScheduledReportDTO> schedules = loadSchedules();
            LocalDateTime now = LocalDateTime.now();
            boolean anyUpdated = false;

            for (ScheduledReportDTO s : schedules) {
                if (!s.active()) continue;
                if (!isScheduledNow(s, now)) continue;
                if (!shouldRunNow(s, now)) continue;

                logger.info("Ejecutando reporte programado: {} (id={})", s.name(), s.id());
                executeScheduledReport(s, now);
                anyUpdated = true;
            }

            if (anyUpdated) {
                // schedules were updated inside executeScheduledReport via updateLastExecution
            }
        } catch (Exception e) {
            logger.error("Error en tick del scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * True si la hora programada coincide con la hora actual (±1 minuto).
     */
    private boolean isScheduledNow(ScheduledReportDTO s, LocalDateTime now) {
        try {
            String[] parts = s.scheduledTime().split(":");
            int hour = Integer.parseInt(parts[0]);
            int min  = Integer.parseInt(parts[1]);
            int nowMin = now.getHour() * 60 + now.getMinute();
            int schMin = hour * 60 + min;
            return Math.abs(nowMin - schMin) <= 1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * True si no se ha ejecutado ya en el período actual.
     */
    private boolean shouldRunNow(ScheduledReportDTO s, LocalDateTime now) {
        LocalDateTime last = s.lastExecution();
        if (last == null) return true;

        return switch (s.frequency().toLowerCase()) {
            case "diaria"     -> !last.toLocalDate().equals(now.toLocalDate());
            case "semanal"    -> ChronoUnit.DAYS.between(last, now) >= 7;
            case "mensual"    -> last.getMonth() != now.getMonth() || last.getYear() != now.getYear();
            case "trimestral" -> ChronoUnit.DAYS.between(last, now) >= 90;
            default           -> false;
        };
    }

    private void executeScheduledReport(ScheduledReportDTO schedule, LocalDateTime executionTime) {
        long start = System.currentTimeMillis();

        // Compute date range for this frequency
        LocalDate dateTo   = LocalDate.now();
        LocalDate dateFrom = switch (schedule.frequency().toLowerCase()) {
            case "diaria"     -> dateTo.minusDays(1);
            case "semanal"    -> dateTo.minusDays(7);
            case "mensual"    -> dateTo.minusDays(30);
            case "trimestral" -> dateTo.minusDays(90);
            default           -> dateTo.minusDays(1);
        };

        String agruparPor = schedule.frequency().equalsIgnoreCase("diaria") ? "dia" : "mes";
        String dateRange  = DateTimeFormatter.ofPattern("dd/MM/yyyy").format(dateFrom)
                + " - " + DateTimeFormatter.ofPattern("dd/MM/yyyy").format(dateTo);
        String format = schedule.format().equalsIgnoreCase("EXCEL") ? "EXCEL" : "PDF";

        serviceRef.fetchReportData(schedule.reportTypeId(), dateFrom, dateTo, agruparPor, 20)
                .thenAccept(data -> {
                    try {
                        List<java.util.Map<String, Object>> rows =
                                extractRows(data, schedule.reportTypeId());

                        ReportGeneratorService.GeneratedFile file =
                                generatorRef.generate(format, schedule.name(),
                                        schedule.reportTypeId(), dateRange, rows);

                        long sizeKb = file.sizeKb();
                        long durationMs = System.currentTimeMillis() - start;

                        ReportDTO report = new ReportDTO(
                                System.currentTimeMillis(),
                                schedule.name() + " (" + executionTime.format(
                                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ")",
                                schedule.reportTypeId(),
                                schedule.typeName(),
                                format,
                                schedule.createdBy(),
                                executionTime,
                                file.path().toString(),
                                sizeKb
                        );

                        registryRef.add(report);
                        updateLastExecution(schedule, executionTime);

                        logger.info("Reporte programado generado en {} ms: {}", durationMs, file.path().getFileName());

                        if (onGeneratedCallback != null) {
                            onGeneratedCallback.accept(report);
                        }
                    } catch (Exception e) {
                        logger.error("Error al generar reporte programado '{}': {}", schedule.name(), e.getMessage(), e);
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error en fetchReportData para schedule '{}': {}", schedule.name(), ex.getMessage());
                    return null;
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private synchronized void updateLastExecution(ScheduledReportDTO s, LocalDateTime when) {
        List<ScheduledReportDTO> all = loadSchedules();
        List<ScheduledReportDTO> updated = new ArrayList<>();
        for (ScheduledReportDTO x : all) {
            if (x.id() == s.id()) {
                LocalDateTime next = computeNextExecution(
                        new ScheduledReportDTO(x.id(), x.name(), x.reportTypeId(), x.typeName(),
                                x.frequency(), x.scheduledTime(), x.format(), x.active(),
                                when, null, x.createdBy()));
                updated.add(new ScheduledReportDTO(
                        x.id(), x.name(), x.reportTypeId(), x.typeName(),
                        x.frequency(), x.scheduledTime(), x.format(), x.active(),
                        when, next, x.createdBy()));
            } else {
                updated.add(x);
            }
        }
        persistSchedules(updated);
    }

    private LocalDateTime computeNextExecution(ScheduledReportDTO s) {
        try {
            String[] parts = s.scheduledTime().split(":");
            int hour = Integer.parseInt(parts[0]);
            int min  = Integer.parseInt(parts[1]);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime todayAt = now.toLocalDate().atTime(hour, min);

            int daysAhead = daysForFrequency(s.frequency());

            if (s.lastExecution() == null) {
                return todayAt.isAfter(now) ? todayAt : todayAt.plusDays(daysAhead);
            }
            return s.lastExecution().plusDays(daysAhead)
                    .withHour(hour).withMinute(min).withSecond(0).withNano(0);
        } catch (Exception e) {
            return LocalDateTime.now().plusDays(1);
        }
    }

    private int daysForFrequency(String freq) {
        return switch (freq.toLowerCase()) {
            case "diaria"     -> 1;
            case "semanal"    -> 7;
            case "mensual"    -> 30;
            case "trimestral" -> 90;
            default           -> 1;
        };
    }

    @SuppressWarnings("unchecked")
    private List<java.util.Map<String, Object>> extractRows(
            java.util.Map<String, Object> data, String tipo) {
        if (data == null) return List.of();
        Object reporteObj = data.get("reporte");
        if (!(reporteObj instanceof java.util.Map<?, ?> reporte)) return List.of();
        Object listObj = switch (tipo.toLowerCase()) {
            case "productos"    -> reporte.get("productos");
            case "rentabilidad" -> reporte.get("datos_mensuales");
            default             -> reporte.get("datos");
        };
        if (listObj instanceof List<?> list) return (List<java.util.Map<String, Object>>) list;
        return List.of();
    }

    // ── Immutable record helpers ──────────────────────────────────────────────

    private static ScheduledReportDTO withId(ScheduledReportDTO s, long id) {
        return new ScheduledReportDTO(id, s.name(), s.reportTypeId(), s.typeName(),
                s.frequency(), s.scheduledTime(), s.format(), s.active(),
                s.lastExecution(), s.nextExecution(), s.createdBy());
    }

    private static ScheduledReportDTO withNextExecution(ScheduledReportDTO s, LocalDateTime next) {
        return new ScheduledReportDTO(s.id(), s.name(), s.reportTypeId(), s.typeName(),
                s.frequency(), s.scheduledTime(), s.format(), s.active(),
                s.lastExecution(), next, s.createdBy());
    }

    private void persistSchedules(List<ScheduledReportDTO> schedules) {
        try {
            Files.createDirectories(schedulesPath.getParent());
            Files.writeString(schedulesPath, gson.toJson(schedules), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Error al persistir schedules.json: {}", e.getMessage());
        }
    }

    private static Path reportsDir() {
        return Paths.get(System.getProperty("user.home"), "Documents", "Herradura", "Reportes");
    }

    // ── LocalDateTime TypeAdapter ─────────────────────────────────────────────

    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) out.nullValue();
            else out.value(value.format(DT_FMT));
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            return LocalDateTime.parse(in.nextString(), DT_FMT);
        }
    }
}
