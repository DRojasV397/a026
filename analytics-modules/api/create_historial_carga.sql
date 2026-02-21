-- Script para crear la tabla HistorialCarga en SQL Server
-- Ejecutar una sola vez sobre la base de datos existente

IF NOT EXISTS (
    SELECT * FROM sys.tables WHERE name = 'HistorialCarga'
)
BEGIN
    CREATE TABLE [dbo].[HistorialCarga] (
        [idHistorial]          INT           IDENTITY(1,1) NOT NULL,
        [uploadId]             VARCHAR(36)   NOT NULL,
        [tipoDatos]            VARCHAR(20)   NOT NULL,
        [nombreArchivo]        VARCHAR(255)  NULL,
        [registrosInsertados]  INT           NOT NULL DEFAULT 0,
        [cargadoPor]           INT           NOT NULL,
        [cargadoEn]            DATETIME      NOT NULL DEFAULT GETDATE(),
        [estado]               VARCHAR(20)   NOT NULL DEFAULT 'exitoso',

        CONSTRAINT [PK_HistorialCarga] PRIMARY KEY CLUSTERED ([idHistorial] ASC),
        CONSTRAINT [FK_HistorialCarga_Usuario] FOREIGN KEY ([cargadoPor])
            REFERENCES [dbo].[Usuario] ([idUsuario])
    );

    PRINT 'Tabla HistorialCarga creada exitosamente.';
END
ELSE
BEGIN
    PRINT 'La tabla HistorialCarga ya existe.';
END
