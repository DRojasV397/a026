-- Migración: Excepciones por producto en ParametroEscenario
-- Agrega productoId nullable (NULL = global, valor = override por producto)
-- Cambia PK a un surrogate id para soportar múltiples filas por (idEscenario, parametro).
--
-- NOTA: los GO separan batches; SQL Server compila cada batch de forma independiente,
-- lo que permite referenciar columnas recién agregadas en DDL subsecuente.

-- ══════════════════════════════════════════════════════
--  Batch 1 — Eliminar PK compuesta existente
-- ══════════════════════════════════════════════════════
DECLARE @pkName NVARCHAR(256);
SELECT @pkName = CONSTRAINT_NAME
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_NAME = 'ParametroEscenario' AND CONSTRAINT_TYPE = 'PRIMARY KEY';

IF @pkName IS NOT NULL
    EXEC('ALTER TABLE ParametroEscenario DROP CONSTRAINT [' + @pkName + ']');
GO

-- ══════════════════════════════════════════════════════
--  Batch 2 — Agregar columna productoId + FK
-- ══════════════════════════════════════════════════════
ALTER TABLE ParametroEscenario ADD productoId INT NULL;
GO

ALTER TABLE ParametroEscenario ADD CONSTRAINT FK_ParamEsc_Producto
    FOREIGN KEY (productoId) REFERENCES Producto(idProducto);
GO

-- ══════════════════════════════════════════════════════
--  Batch 3 — Agregar id surrogate + nueva PK
-- ══════════════════════════════════════════════════════
ALTER TABLE ParametroEscenario ADD id INT IDENTITY(1,1) NOT NULL;
GO

ALTER TABLE ParametroEscenario ADD CONSTRAINT PK_ParametroEscenario_New
    PRIMARY KEY (id);
GO

-- ══════════════════════════════════════════════════════
--  Batch 4 — Índices únicos filtrados
--  (productoId ya existe en el schema → sin error de compilación)
-- ══════════════════════════════════════════════════════

-- Unicidad para parámetros globales (productoId IS NULL)
CREATE UNIQUE INDEX UQ_ParamEsc_Global
    ON ParametroEscenario(idEscenario, parametro)
    WHERE productoId IS NULL;
GO

-- Unicidad para overrides por producto (productoId NOT NULL)
CREATE UNIQUE INDEX UQ_ParamEsc_Producto
    ON ParametroEscenario(idEscenario, parametro, productoId)
    WHERE productoId IS NOT NULL;
GO
