package com.app.model.data;

import java.time.LocalDateTime;

/**
 * Registro de un archivo cargado al sistema (historial de cargas).
 *
 * @param id         Identificador único
 * @param fileName   Nombre original del archivo
 * @param fileType   Tipo: "CSV" | "XLSX"
 * @param dataType   Tipo de datos: "VENTAS" | "COMPRAS"
 * @param uploadedAt Fecha y hora de carga
 * @param status     Estado: "PROCESADO" | "ERROR" | "PENDIENTE"
 * @param sizeKb     Tamaño en KB
 * @param rowCount   Número de registros cargados (-1 si hubo error)
 * @param uploadedBy Usuario que realizó la carga
 */
public record UploadedFileDTO(
        long          id,
        String        fileName,
        String        fileType,
        String        dataType,
        LocalDateTime uploadedAt,
        String        status,
        long          sizeKb,
        int           rowCount,
        String        uploadedBy
) {}