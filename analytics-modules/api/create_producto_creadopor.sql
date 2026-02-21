-- Script para agregar la columna creadoPor a la tabla Producto en SQL Server
-- Ejecutar una sola vez sobre la base de datos existente

-- Agregar columna creadoPor si no existe
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE object_id = OBJECT_ID(N'[dbo].[Producto]')
      AND name = 'creadoPor'
)
BEGIN
    ALTER TABLE [dbo].[Producto]
    ADD [creadoPor] INT NULL;

    PRINT 'Columna creadoPor agregada a Producto.';
END
ELSE
BEGIN
    PRINT 'La columna creadoPor ya existe en Producto.';
END
GO

-- Agregar FK a Usuario si no existe
IF NOT EXISTS (
    SELECT * FROM sys.foreign_keys
    WHERE name = 'FK_Producto_Usuario'
)
BEGIN
    ALTER TABLE [dbo].[Producto]
    ADD CONSTRAINT [FK_Producto_Usuario]
        FOREIGN KEY ([creadoPor]) REFERENCES [dbo].[Usuario]([idUsuario]);

    PRINT 'FK FK_Producto_Usuario creada.';
END
ELSE
BEGIN
    PRINT 'La FK FK_Producto_Usuario ya existe.';
END
GO

-- NOTA OPCIONAL: Si deseas asignar los productos existentes (sin creadoPor) a
-- un usuario administrador específico, ejecuta:
--
--   UPDATE [dbo].[Producto]
--   SET [creadoPor] = <id_del_admin>
--   WHERE [creadoPor] IS NULL;
--
-- Sustituye <id_del_admin> por el idUsuario correspondiente.
-- Los productos con creadoPor = NULL no serán visibles para ningún usuario
-- hasta que se les asigne un propietario.
