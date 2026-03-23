package com.app.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Utilidad para reintentar llamadas asíncronas ante errores transitorios.
 *
 * <p>Errores que califican como transitorios (se reintenta):</p>
 * <ul>
 *   <li>{@link IOException}: conexión rechazada, reset de conexión</li>
 *   <li>{@link HttpTimeoutException}: timeout de conexión o lectura</li>
 *   <li>HTTP 503 Service Unavailable (detectado vía mensaje en RuntimeException)</li>
 * </ul>
 *
 * <p>Uso típico:</p>
 * <pre>
 *   return RetryUtil.withRetry(
 *       () -> httpClient.sendAsync(request, BodyHandlers.ofString())
 *                       .thenApply(this::parseResponse),
 *       3,    // máximo 3 intentos
 *       1500  // 1.5 s entre reintentos
 *   );
 * </pre>
 */
public final class RetryUtil {

    private static final Logger logger = LoggerFactory.getLogger(RetryUtil.class);

    /** Scheduler daemon usado únicamente para el delay entre reintentos. */
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "retry-scheduler");
                t.setDaemon(true);
                return t;
            });

    private RetryUtil() {}

    /**
     * Ejecuta {@code action} y, si falla con un error transitorio, lo reintenta
     * hasta {@code maxAttempts} veces con {@code delayMs} milisegundos de espera
     * entre intentos.
     *
     * @param action      Función que produce el CompletableFuture a ejecutar
     * @param maxAttempts Número máximo de intentos (1 = sin reintentos)
     * @param delayMs     Milisegundos de espera antes de cada reintento
     * @param <T>         Tipo del resultado
     * @return CompletableFuture con el resultado o el último error si se agotaron los intentos
     */
    public static <T> CompletableFuture<T> withRetry(
            Supplier<CompletableFuture<T>> action,
            int maxAttempts,
            long delayMs
    ) {
        return action.get().exceptionallyCompose(ex -> {
            if (maxAttempts <= 1 || !isTransient(ex)) {
                // No quedan intentos o el error no es transitorio: propagar
                return CompletableFuture.failedFuture(ex);
            }

            logger.warn("Error transitorio (intentos restantes: {}): {}",
                    maxAttempts - 1, rootCause(ex).getMessage());

            // Delay antes del siguiente intento
            CompletableFuture<T> delayed = new CompletableFuture<>();
            SCHEDULER.schedule(
                    () -> withRetry(action, maxAttempts - 1, delayMs)
                            .whenComplete((result, error) -> {
                                if (error != null) delayed.completeExceptionally(error);
                                else delayed.complete(result);
                            }),
                    delayMs,
                    TimeUnit.MILLISECONDS
            );
            return delayed;
        });
    }

    /**
     * Versión con valores por defecto: 3 intentos, 1500 ms de delay.
     */
    public static <T> CompletableFuture<T> withRetry(Supplier<CompletableFuture<T>> action) {
        return withRetry(action, 3, 1500);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static boolean isTransient(Throwable ex) {
        Throwable cause = rootCause(ex);
        if (cause instanceof IOException)        return true;
        if (cause instanceof HttpTimeoutException) return true;
        // Detectar 503 comunicado como RuntimeException (ej. desde thenApply)
        String msg = cause.getMessage();
        return msg != null && (msg.contains("503") || msg.contains("Connection refused"));
    }

    private static Throwable rootCause(Throwable ex) {
        return (ex instanceof CompletionException && ex.getCause() != null)
                ? ex.getCause()
                : ex;
    }
}
