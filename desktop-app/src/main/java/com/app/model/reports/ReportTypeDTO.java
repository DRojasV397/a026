package com.app.model.reports;

/**
 * Define un tipo de reporte disponible en el sistema.
 *
 * @param id          Identificador único (ej. "PREDICTIVE", "PROFIT")
 * @param name        Nombre visible en la UI
 * @param description Descripción breve del contenido del reporte
 * @param iconPath    Ruta al recurso de imagen (/images/reports/...)
 */
public record ReportTypeDTO(
        String id,
        String name,
        String description,
        String iconPath
) {}