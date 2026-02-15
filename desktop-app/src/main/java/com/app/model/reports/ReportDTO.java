package com.app.model.reports;

import java.time.LocalDateTime;

/**
 * Representa un reporte ya generado y guardado en el sistema.
 *
 * @param id           Identificador único
 * @param name         Nombre dado al reporte por el usuario
 * @param reportTypeId Tipo de reporte (referencia a ReportTypeDTO.id)
 * @param typeName     Nombre legible del tipo (para mostrar en UI sin join)
 * @param format       Formato de salida: "PDF" o "EXCEL"
 * @param generatedBy  Nombre de usuario que lo generó
 * @param createdAt    Fecha y hora de generación
 * @param filePath     Ruta al archivo generado (para descarga)
 * @param sizeKb       Tamaño del archivo en KB
 */
public record ReportDTO(
        long          id,
        String        name,
        String        reportTypeId,
        String        typeName,
        String        format,
        String        generatedBy,
        LocalDateTime createdAt,
        String        filePath,
        long          sizeKb
) {
    /** @return true si el reporte fue generado hoy */
    public boolean isNew() {
        return createdAt.toLocalDate().equals(java.time.LocalDate.now());
    }
}