package com.app.model.reports;

import java.time.LocalDateTime;

/**
 * Entrada del historial de ejecuciones de un reporte programado.
 *
 * @param id                Identificador único del registro
 * @param scheduledReportId ID de la programación que lo originó
 * @param scheduledName     Nombre de la programación (para mostrar sin join)
 * @param executedAt        Cuándo se ejecutó
 * @param status            "EXITOSO", "FALLIDO", "PARCIAL"
 * @param durationMs        Duración en milisegundos
 * @param logDetail         Detalle del log (puede ser largo, se muestra en modal)
 */
public record ExecutionLogDTO(
        long          id,
        long          scheduledReportId,
        String        scheduledName,
        LocalDateTime executedAt,
        String        status,
        long          durationMs,
        String        logDetail
) {}