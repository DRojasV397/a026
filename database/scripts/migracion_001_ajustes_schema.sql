-- =============================================
-- MIGRACIÓN 001 — Ajustes de esquema
-- Aplica las columnas y tablas faltantes
-- detectadas entre crear_base_datos.sql y
-- los modelos SQLAlchemy actuales.
--
-- Ejecutar UNA sola vez sobre la BD existente.
-- Es idempotente: cada bloque verifica antes
-- de agregar para no fallar si ya fue aplicado.
-- =============================================

USE Herradura;  -- Cambia al nombre real de tu base de datos si difiere
GO

-- =============================================
-- 1. TABLA Producto — columnas faltantes
-- =============================================

-- creadoPor (FK a Usuario): identifica al usuario propietario del producto.
-- Es crítico: todas las queries filtran por este campo.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('Producto') AND name = 'creadoPor'
)
BEGIN
    ALTER TABLE Producto
        ADD creadoPor INT NULL
            CONSTRAINT FK_Producto_Usuario FOREIGN KEY REFERENCES Usuario(idUsuario);
    PRINT 'Producto.creadoPor agregado.';
END
GO

-- stock: cantidad disponible en inventario.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('Producto') AND name = 'stock'
)
BEGIN
    ALTER TABLE Producto ADD stock INT NULL;
    PRINT 'Producto.stock agregado.';
END
GO

-- stockMinimo: nivel mínimo de reorden.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('Producto') AND name = 'stockMinimo'
)
BEGIN
    ALTER TABLE Producto ADD stockMinimo INT NULL;
    PRINT 'Producto.stockMinimo agregado.';
END
GO

-- stockMaximo: nivel máximo de almacenamiento.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('Producto') AND name = 'stockMaximo'
)
BEGIN
    ALTER TABLE Producto ADD stockMaximo INT NULL;
    PRINT 'Producto.stockMaximo agregado.';
END
GO

-- ubicacion: localización física en almacén.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('Producto') AND name = 'ubicacion'
)
BEGIN
    ALTER TABLE Producto ADD ubicacion VARCHAR(120) NULL;
    PRINT 'Producto.ubicacion agregado.';
END
GO

-- =============================================
-- 2. TABLA DetalleCompra — corrección de nombre
--    y columnas faltantes
-- =============================================

-- El script original creó la columna como "costoUnitario"
-- pero el modelo SQLAlchemy la llama "costo".
-- Renombramos para que coincidan.
IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('DetalleCompra') AND name = 'costoUnitario'
)
BEGIN
    EXEC sp_rename 'DetalleCompra.costoUnitario', 'costo', 'COLUMN';
    PRINT 'DetalleCompra.costoUnitario renombrado a costo.';
END
GO

-- descuento: descuento aplicado al renglón.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('DetalleCompra') AND name = 'descuento'
)
BEGIN
    ALTER TABLE DetalleCompra ADD descuento DECIMAL(18,2) NOT NULL DEFAULT 0;
    PRINT 'DetalleCompra.descuento agregado.';
END
GO

-- subtotal: cantidad * costo - descuento, calculado en la app y persistido.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('DetalleCompra') AND name = 'subtotal'
)
BEGIN
    ALTER TABLE DetalleCompra ADD subtotal DECIMAL(18,2) NULL;
    PRINT 'DetalleCompra.subtotal agregado.';
END
GO

-- =============================================
-- 3. TABLA HistorialCarga — creación completa
-- =============================================

IF NOT EXISTS (
    SELECT 1 FROM sys.objects
    WHERE type = 'U' AND name = 'HistorialCarga'
)
BEGIN
    CREATE TABLE HistorialCarga (
        idHistorial         INT NOT NULL IDENTITY(1,1),
        uploadId            VARCHAR(36)  NOT NULL,
        tipoDatos           VARCHAR(20)  NOT NULL,
        nombreArchivo       VARCHAR(255) NULL,
        registrosInsertados INT          NOT NULL DEFAULT 0,
        registrosActualizados INT        NOT NULL DEFAULT 0,
        cargadoPor          INT          NOT NULL,
        cargadoEn           DATETIME     NOT NULL DEFAULT GETDATE(),
        estado              VARCHAR(20)  NOT NULL DEFAULT 'exitoso',
        CONSTRAINT PK_HistorialCarga PRIMARY KEY (idHistorial),
        CONSTRAINT FK_HistorialCarga_Usuario FOREIGN KEY (cargadoPor)
            REFERENCES Usuario(idUsuario)
    );
    PRINT 'Tabla HistorialCarga creada.';
END
GO

PRINT '=== Migración 001 completada ===';
GO
