package com.app.model.reports;

import java.time.LocalDateTime;

/**
 * Representa una programación de reporte automático.
 *
 * @param id            Identificador único
 * @param name          Nombre de la programación
 * @param reportTypeId  Tipo de reporte a generar
 * @param typeName      Nombre legible del tipo
 * @param frequency     Frecuencia: "Diaria", "Semanal", "Mensual", "Trimestral"
 * @param scheduledTime Hora del día en que se ejecuta (HH:mm)
 * @param format        Formato de salida: "PDF", "EXCEL", "AMBOS"
 * @param active        Si la programación está activa
 * @param lastExecution Fecha de la última ejecución (null si nunca se ha ejecutado)
 * @param nextExecution Fecha estimada de la próxima ejecución
 * @param createdBy     Usuario que creó la programación
 */
public record ScheduledReportDTO(
        long          id,
        String        name,
        String        reportTypeId,
        String        typeName,
        String        frequency,
        String        scheduledTime,
        String        format,
        boolean       active,
        LocalDateTime lastExecution,
        LocalDateTime nextExecution,
        String        createdBy
) {}