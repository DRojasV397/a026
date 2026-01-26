-- =============================================
-- Script de Creación de Base de Datos
-- Sistema de Inteligencia de Negocios Predictiva
-- SQL Server
-- =============================================

-- Crear la base de datos
CREATE DATABASE SistemaBI;
GO

-- Usar la base de datos
USE SistemaBI;
GO

-- =============================================
-- MÓDULO DE USUARIOS Y SEGURIDAD
-- =============================================

-- Tabla Usuario
CREATE TABLE Usuario (
    idUsuario INT NOT NULL IDENTITY(1,1),
    nombreCompleto VARCHAR(120) NOT NULL,
    nombreUsuario VARCHAR(60) NOT NULL,
    email VARCHAR(160) NOT NULL,
    hashPassword VARCHAR(50), -- se guarda en base64
    estado VARCHAR(20),
    creadoEn DATETIME DEFAULT GETDATE(),
    CONSTRAINT PK_Usuario PRIMARY KEY (idUsuario),
    CONSTRAINT UQ_Usuario_nombreUsuario UNIQUE (nombreUsuario),
    CONSTRAINT UQ_Usuario_email UNIQUE (email)
);
GO

-- Tabla Rol
CREATE TABLE Rol (
    idRol INT NOT NULL IDENTITY(1,1),
    nombre VARCHAR(80) NOT NULL,
    CONSTRAINT PK_Rol PRIMARY KEY (idRol),
    CONSTRAINT UQ_Rol_nombre UNIQUE (nombre)
);
GO

-- Tabla UsuarioRol (Relación muchos a muchos)
CREATE TABLE UsuarioRol (
    idUsuario INT NOT NULL,
    idRol INT NOT NULL,
    CONSTRAINT PK_UsuarioRol PRIMARY KEY (idUsuario, idRol),
    CONSTRAINT FK_UsuarioRol_Usuario FOREIGN KEY (idUsuario) 
        REFERENCES Usuario(idUsuario),
    CONSTRAINT FK_UsuarioRol_Rol FOREIGN KEY (idRol) 
        REFERENCES Rol(idRol)
);
GO

-- Tabla PreferenciaUsuario
CREATE TABLE PreferenciaUsuario (
    idPreferencia INT NOT NULL IDENTITY(1,1),
    idUsuario INT NOT NULL,
    kpi VARCHAR(60) NOT NULL, -- nombre del KPI (ej. "ROI", "Margen")
    visible BIT NOT NULL DEFAULT 1,
    orden INT, -- posición de visualización
    creadoEn DATETIME DEFAULT GETDATE(),
    CONSTRAINT PK_PreferenciaUsuario PRIMARY KEY (idPreferencia),
    CONSTRAINT FK_PreferenciaUsuario_Usuario FOREIGN KEY (idUsuario) 
        REFERENCES Usuario(idUsuario)
);
GO

-- =============================================
-- MÓDULO DE PRODUCTOS Y CATEGORÍAS
-- =============================================

-- Tabla Categoria
CREATE TABLE Categoria (
    idCategoria INT NOT NULL IDENTITY(1,1),
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(255),
    CONSTRAINT PK_Categoria PRIMARY KEY (idCategoria),
    CONSTRAINT UQ_Categoria_nombre UNIQUE (nombre)
);
GO

-- Tabla Producto
CREATE TABLE Producto (
    idProducto INT NOT NULL IDENTITY(1,1),
    sku VARCHAR(60),
    nombre VARCHAR(160) NOT NULL,
    idCategoria INT NOT NULL,
    costoUnitario DECIMAL(18,2),
    precioUnitario DECIMAL(18,2),
    activo BIT DEFAULT 1,
    CONSTRAINT PK_Producto PRIMARY KEY (idProducto),
    CONSTRAINT UQ_Producto_sku UNIQUE (sku),
    CONSTRAINT FK_Producto_Categoria FOREIGN KEY (idCategoria) 
        REFERENCES Categoria(idCategoria)
);
GO

-- =============================================
-- MÓDULO DE VENTAS
-- =============================================

-- Tabla Venta
CREATE TABLE Venta (
    idVenta INT NOT NULL IDENTITY(1,1),
    fecha DATE NOT NULL,
    total DECIMAL(18,2),
    moneda CHAR(3) DEFAULT 'MXN',
    creadoPor INT,
    CONSTRAINT PK_Venta PRIMARY KEY (idVenta),
    CONSTRAINT FK_Venta_Usuario FOREIGN KEY (creadoPor) 
        REFERENCES Usuario(idUsuario)
);
GO

-- Tabla DetalleVenta
CREATE TABLE DetalleVenta (
    idVenta INT NOT NULL,
    renglon INT NOT NULL,
    idProducto INT NOT NULL,
    cantidad DECIMAL(18,4) NOT NULL,
    precioUnitario DECIMAL(18,4) NOT NULL,
    CONSTRAINT PK_DetalleVenta PRIMARY KEY (idVenta, renglon),
    CONSTRAINT FK_DetalleVenta_Venta FOREIGN KEY (idVenta) 
        REFERENCES Venta(idVenta),
    CONSTRAINT FK_DetalleVenta_Producto FOREIGN KEY (idProducto) 
        REFERENCES Producto(idProducto)
);
GO

-- =============================================
-- MÓDULO DE COMPRAS
-- =============================================

-- Tabla Compra
CREATE TABLE Compra (
    idCompra INT NOT NULL IDENTITY(1,1),
    fecha DATE NOT NULL,
    proveedor VARCHAR(120),
    total DECIMAL(18,2),
    moneda CHAR(3) DEFAULT 'MXN',
    creadoPor INT,
    CONSTRAINT PK_Compra PRIMARY KEY (idCompra),
    CONSTRAINT FK_Compra_Usuario FOREIGN KEY (creadoPor) 
        REFERENCES Usuario(idUsuario)
);
GO

-- Tabla DetalleCompra
CREATE TABLE DetalleCompra (
    idCompra INT NOT NULL,
    renglon INT NOT NULL,
    idProducto INT NOT NULL,
    cantidad DECIMAL(18,4) NOT NULL,
    costoUnitario DECIMAL(18,4) NOT NULL,
    CONSTRAINT PK_DetalleCompra PRIMARY KEY (idCompra, renglon),
    CONSTRAINT FK_DetalleCompra_Compra FOREIGN KEY (idCompra) 
        REFERENCES Compra(idCompra),
    CONSTRAINT FK_DetalleCompra_Producto FOREIGN KEY (idProducto) 
        REFERENCES Producto(idProducto)
);
GO

-- =============================================
-- MÓDULO DE MODELOS PREDICTIVOS
-- =============================================

-- Tabla Modelo
CREATE TABLE Modelo (
    idModelo INT NOT NULL IDENTITY(1,1),
    tipoModelo VARCHAR(40) NOT NULL,
    objetivo VARCHAR(120),
    creadoEn DATETIME DEFAULT GETDATE(),
    CONSTRAINT PK_Modelo PRIMARY KEY (idModelo)
);
GO

-- Tabla VersionModelo
CREATE TABLE VersionModelo (
    idVersion INT NOT NULL IDENTITY(1,1),
    idModelo INT NOT NULL,
    hyperparams TEXT,
    entrenadoDesde DATE NOT NULL,
    entrenadoHasta DATE NOT NULL,
    estado VARCHAR(10) DEFAULT 'Activo',
    CONSTRAINT PK_VersionModelo PRIMARY KEY (idVersion),
    CONSTRAINT FK_VersionModelo_Modelo FOREIGN KEY (idModelo) 
        REFERENCES Modelo(idModelo)
);
GO

-- Tabla Prediccion
CREATE TABLE Prediccion (
    idPred INT NOT NULL IDENTITY(1,1),
    idVersion INT NOT NULL,
    entidad VARCHAR(20) NOT NULL,
    claveEntidad VARCHAR(60) NOT NULL,
    periodo DATE NOT NULL,
    valorPredicho DECIMAL(18,4) NOT NULL,
    nivelConfianza DECIMAL(5,2),
    CONSTRAINT PK_Prediccion PRIMARY KEY (idPred),
    CONSTRAINT FK_Prediccion_VersionModelo FOREIGN KEY (idVersion) 
        REFERENCES VersionModelo(idVersion)
);
GO

-- =============================================
-- MÓDULO DE ESCENARIOS Y SIMULACIONES
-- =============================================

-- Tabla Escenario
CREATE TABLE Escenario (
    idEscenario INT NOT NULL IDENTITY(1,1),
    nombre VARCHAR(120) NOT NULL,
    descripcion TEXT,
    horizonteMeses INT,
    baseVersion INT,
    creadoPor INT NOT NULL,
    creadoEn DATETIME DEFAULT GETDATE(),
    CONSTRAINT PK_Escenario PRIMARY KEY (idEscenario),
    CONSTRAINT FK_Escenario_VersionModelo FOREIGN KEY (baseVersion) 
        REFERENCES VersionModelo(idVersion),
    CONSTRAINT FK_Escenario_Usuario FOREIGN KEY (creadoPor) 
        REFERENCES Usuario(idUsuario)
);
GO

-- Tabla ParametroEscenario
CREATE TABLE ParametroEscenario (
    idEscenario INT NOT NULL,
    parametro VARCHAR(80) NOT NULL,
    valorBase DECIMAL(18,4),
    valorActual DECIMAL(18,4),
    CONSTRAINT PK_ParametroEscenario PRIMARY KEY (idEscenario, parametro),
    CONSTRAINT FK_ParametroEscenario_Escenario FOREIGN KEY (idEscenario) 
        REFERENCES Escenario(idEscenario)
);
GO

-- Tabla ResultadoEscenario
CREATE TABLE ResultadoEscenario (
    idEscenario INT NOT NULL,
    periodo DATE NOT NULL,
    kpi VARCHAR(40) NOT NULL,
    valor DECIMAL(18,4),
    confianza DECIMAL(5,2),
    CONSTRAINT PK_ResultadoEscenario PRIMARY KEY (idEscenario, periodo, kpi),
    CONSTRAINT FK_ResultadoEscenario_Escenario FOREIGN KEY (idEscenario) 
        REFERENCES Escenario(idEscenario)
);
GO

-- =============================================
-- MÓDULO DE REPORTES Y ALERTAS
-- =============================================

-- Tabla Reporte
CREATE TABLE Reporte (
    idReporte INT NOT NULL IDENTITY(1,1),
    tipo VARCHAR(40),
    formato VARCHAR(10),
    periodo VARCHAR(40),
    generadoPor INT NOT NULL,
    generadoEn DATETIME DEFAULT GETDATE(),
    CONSTRAINT PK_Reporte PRIMARY KEY (idReporte),
    CONSTRAINT FK_Reporte_Usuario FOREIGN KEY (generadoPor) 
        REFERENCES Usuario(idUsuario)
);
GO

-- Tabla Alerta
CREATE TABLE Alerta (
    idAlerta INT NOT NULL IDENTITY(1,1),
    idPred INT NOT NULL,
    tipo VARCHAR(20) NOT NULL, -- Ej. "RIESGO"
    importancia VARCHAR(10) NOT NULL, -- Baja, Media, Alta, Crítica
    metrica VARCHAR(40) NOT NULL,
    valorActual DECIMAL(18,4) NOT NULL,
    valorEsperado DECIMAL(18,4),
    nivelConfianza DECIMAL(5,2),
    estado VARCHAR(12) DEFAULT 'Activa',
    creadaEn DATETIME DEFAULT GETDATE(),
    CONSTRAINT PK_Alerta PRIMARY KEY (idAlerta),
    CONSTRAINT FK_Alerta_Prediccion FOREIGN KEY (idPred) 
        REFERENCES Prediccion(idPred)
);
GO

-- =============================================
-- MÓDULO DE RENTABILIDAD INTEGRADO
-- =============================================

-- Tabla Rentabilidad
CREATE TABLE Rentabilidad (
    idRentabilidad INT NOT NULL IDENTITY(1,1),
    claveEntidad VARCHAR(60), -- idProducto o idCategoria
    periodo DATE NOT NULL,
    ingresos DECIMAL(18,2), -- Ventas totales
    costos DECIMAL(18,2), -- Costos variables o compras
    utilidadOperativa DECIMAL(18,2),
    margen DECIMAL(8,2),
    utilidadNeta DECIMAL(18,2), -- COMPUTED: ingresos - costos - gastosOperativos
    margenUtilidad DECIMAL(8,2), -- COMPUTED: (utilidadNeta / ingresos) * 100
    ROA DECIMAL(8,2), -- COMPUTED: (utilidadNeta / activosTotales) * 100
    ROE DECIMAL(8,2), -- COMPUTED: (utilidadNeta / patrimonio) * 100
    CONSTRAINT PK_Rentabilidad PRIMARY KEY (idRentabilidad)
);
GO

-- Tabla ResultadoFinanciero
CREATE TABLE ResultadoFinanciero (
    idResultado INT NOT NULL IDENTITY(1,1),
    idVersion INT, -- modelo que generó el resultado
    nombreIndicador VARCHAR(60) NOT NULL, -- Ej: ROI, Margen, Punto de equilibrio
    tipoEntidad VARCHAR(20) NOT NULL, -- Producto, Categoria, Empresa
    claveEntidad VARCHAR(60) NOT NULL,
    periodo DATE NOT NULL,
    valor DECIMAL(18,4) NOT NULL,
    fuente VARCHAR(20), -- 'Histórico', 'Proyección', 'Escenario'
    CONSTRAINT PK_ResultadoFinanciero PRIMARY KEY (idResultado),
    CONSTRAINT FK_ResultadoFinanciero_VersionModelo FOREIGN KEY (idVersion) 
        REFERENCES VersionModelo(idVersion)
);
GO

-- =============================================
-- ÍNDICES ADICIONALES PARA OPTIMIZACIÓN
-- =============================================

-- Índices para búsquedas por fecha (series de tiempo)
CREATE INDEX IX_Venta_Fecha ON Venta(fecha);
CREATE INDEX IX_Compra_Fecha ON Compra(fecha);
CREATE INDEX IX_Prediccion_Periodo ON Prediccion(periodo);
CREATE INDEX IX_ResultadoEscenario_Periodo ON ResultadoEscenario(periodo);
CREATE INDEX IX_Rentabilidad_Periodo ON Rentabilidad(periodo);
CREATE INDEX IX_ResultadoFinanciero_Periodo ON ResultadoFinanciero(periodo);
GO

-- Índices para búsquedas por entidad
CREATE INDEX IX_Prediccion_Entidad ON Prediccion(entidad, claveEntidad);
CREATE INDEX IX_ResultadoFinanciero_Entidad ON ResultadoFinanciero(tipoEntidad, claveEntidad);
GO

-- Índices para relaciones frecuentes
CREATE INDEX IX_Producto_Categoria ON Producto(idCategoria);
CREATE INDEX IX_DetalleVenta_Producto ON DetalleVenta(idProducto);
CREATE INDEX IX_DetalleCompra_Producto ON DetalleCompra(idProducto);
CREATE INDEX IX_PreferenciaUsuario_Usuario ON PreferenciaUsuario(idUsuario);
GO

-- =============================================
-- SCRIPT COMPLETADO
-- =============================================

PRINT 'Base de datos SistemaBI creada exitosamente';
PRINT 'Total de tablas creadas: 23';
GO
