"""
Modulo de esquemas DTO (Data Transfer Objects) usando Pydantic.
"""

# Schemas comunes
from .common import (
    PaginationParams, PaginatedResponse,
    MessageResponse, ErrorResponse, SuccessResponse,
    DateRangeFilter, IdListRequest, StatusUpdate
)

# Schemas de autenticacion
from .auth import (
    LoginRequest, LoginResponse, UserInfo,
    RegisterRequest, RegisterResponse,
    TokenData, TokenVerifyRequest, TokenVerifyResponse,
    RefreshTokenRequest, RefreshTokenResponse,
    ChangePasswordRequest, ChangePasswordResponse,
    ForgotPasswordRequest, ForgotPasswordResponse,
    ResetPasswordRequest, ResetPasswordResponse
)

# Schemas de usuario
from .usuario import (
    UsuarioCreate, UsuarioUpdate, UsuarioResponse, UsuarioCompleto,
    RolCreate, RolResponse,
    UsuarioRolCreate, UsuarioRolResponse,
    PreferenciaUsuarioCreate, PreferenciaUsuarioUpdate, PreferenciaUsuarioResponse
)

# Schemas de producto
from .producto import (
    CategoriaCreate, CategoriaUpdate, CategoriaResponse,
    ProductoCreate, ProductoUpdate, ProductoResponse
)

# Schemas de venta
from .venta import (
    VentaCreate, VentaUpdate, VentaResponse,
    DetalleVentaCreate, DetalleVentaResponse
)

# Schemas de compra
from .compra import (
    CompraCreate, CompraUpdate, CompraResponse, CompraConDetalles, CompraFiltros,
    DetalleCompraCreate, DetalleCompraResponse
)

# Schemas de prediccion
from .prediction import (
    TipoModelo, EstadoModelo, TipoEntidad,
    ModeloCreate, ModeloResponse,
    VersionModeloCreate, VersionModeloResponse,
    PrediccionCreate, PrediccionResponse,
    TrainModelRequest, TrainModelResponse,
    ForecastRequest, ForecastItem, ForecastResponse,
    ModelMetrics, ModelMetricsResponse,
    CompareModelsRequest, ModelComparison, CompareModelsResponse
)

# Schemas de rentabilidad
from .profitability import (
    TipoPeriodo, TipoEntidadRentabilidad,
    RentabilidadCreate, RentabilidadResponse,
    ResultadoFinancieroCreate, ResultadoFinancieroResponse,
    CalcularRentabilidadRequest, IndicadoresFinancieros, CalcularRentabilidadResponse,
    RentabilidadProducto, RentabilidadProductosResponse,
    RentabilidadCategoria, RentabilidadCategoriasResponse,
    TendenciaItem, TendenciasRentabilidadResponse,
    ProductoRanking, RankingProductosResponse
)

# Schemas de simulacion
from .simulation import (
    EstadoEscenario, TipoParametro,
    EscenarioCreate, EscenarioUpdate, EscenarioResponse, EscenarioCompleto,
    ParametroEscenarioCreate, ParametroEscenarioResponse,
    ResultadoEscenarioCreate, ResultadoEscenarioResponse,
    CrearEscenarioRequest, CrearEscenarioResponse,
    ModificarParametroItem, ModificarParametrosRequest, ModificarParametrosResponse,
    EjecutarSimulacionRequest, ResultadoSimulacionItem, EjecutarSimulacionResponse,
    CompararEscenariosRequest, ComparacionIndicador, CompararEscenariosResponse
)

# Schemas de alertas
from .alert import (
    TipoAlerta, ImportanciaAlerta, EstadoAlerta,
    AlertaCreate, AlertaUpdate, AlertaResponse,
    UmbralAlerta, ConfigurarAlertasRequest, ConfigurarAlertasResponse,
    AlertaFiltros, AlertasListResponse,
    MarcarLeidaResponse,
    CambiarEstadoRequest, CambiarEstadoResponse,
    ResumenAlertas, AlertaConContexto
)

# Schemas de carga de datos
from .data_upload import (
    DataType, UploadStatus,
    UploadResponse, ValidateRequest, ValidateResponse, ColumnValidation,
    PreviewRequest, PreviewResponse,
    CleaningOptions, CleanRequest, CleaningResult, CleanResponse,
    ConfirmRequest, ConfirmResponse,
    QualityMetric, QualityReportResponse
)

__all__ = [
    # Common
    'PaginationParams', 'PaginatedResponse',
    'MessageResponse', 'ErrorResponse', 'SuccessResponse',
    'DateRangeFilter', 'IdListRequest', 'StatusUpdate',

    # Auth
    'LoginRequest', 'LoginResponse', 'UserInfo',
    'RegisterRequest', 'RegisterResponse',
    'TokenData', 'TokenVerifyRequest', 'TokenVerifyResponse',
    'RefreshTokenRequest', 'RefreshTokenResponse',
    'ChangePasswordRequest', 'ChangePasswordResponse',
    'ForgotPasswordRequest', 'ForgotPasswordResponse',
    'ResetPasswordRequest', 'ResetPasswordResponse',

    # Usuario
    'UsuarioCreate', 'UsuarioUpdate', 'UsuarioResponse', 'UsuarioCompleto',
    'RolCreate', 'RolResponse',
    'UsuarioRolCreate', 'UsuarioRolResponse',
    'PreferenciaUsuarioCreate', 'PreferenciaUsuarioUpdate', 'PreferenciaUsuarioResponse',

    # Categoria y Producto
    'CategoriaCreate', 'CategoriaUpdate', 'CategoriaResponse',
    'ProductoCreate', 'ProductoUpdate', 'ProductoResponse',

    # Venta
    'VentaCreate', 'VentaUpdate', 'VentaResponse',
    'DetalleVentaCreate', 'DetalleVentaResponse',

    # Compra
    'CompraCreate', 'CompraUpdate', 'CompraResponse', 'CompraConDetalles', 'CompraFiltros',
    'DetalleCompraCreate', 'DetalleCompraResponse',

    # Prediccion
    'TipoModelo', 'EstadoModelo', 'TipoEntidad',
    'ModeloCreate', 'ModeloResponse',
    'VersionModeloCreate', 'VersionModeloResponse',
    'PrediccionCreate', 'PrediccionResponse',
    'TrainModelRequest', 'TrainModelResponse',
    'ForecastRequest', 'ForecastItem', 'ForecastResponse',
    'ModelMetrics', 'ModelMetricsResponse',
    'CompareModelsRequest', 'ModelComparison', 'CompareModelsResponse',

    # Rentabilidad
    'TipoPeriodo', 'TipoEntidadRentabilidad',
    'RentabilidadCreate', 'RentabilidadResponse',
    'ResultadoFinancieroCreate', 'ResultadoFinancieroResponse',
    'CalcularRentabilidadRequest', 'IndicadoresFinancieros', 'CalcularRentabilidadResponse',
    'RentabilidadProducto', 'RentabilidadProductosResponse',
    'RentabilidadCategoria', 'RentabilidadCategoriasResponse',
    'TendenciaItem', 'TendenciasRentabilidadResponse',
    'ProductoRanking', 'RankingProductosResponse',

    # Simulacion
    'EstadoEscenario', 'TipoParametro',
    'EscenarioCreate', 'EscenarioUpdate', 'EscenarioResponse', 'EscenarioCompleto',
    'ParametroEscenarioCreate', 'ParametroEscenarioResponse',
    'ResultadoEscenarioCreate', 'ResultadoEscenarioResponse',
    'CrearEscenarioRequest', 'CrearEscenarioResponse',
    'ModificarParametroItem', 'ModificarParametrosRequest', 'ModificarParametrosResponse',
    'EjecutarSimulacionRequest', 'ResultadoSimulacionItem', 'EjecutarSimulacionResponse',
    'CompararEscenariosRequest', 'ComparacionIndicador', 'CompararEscenariosResponse',

    # Alertas
    'TipoAlerta', 'ImportanciaAlerta', 'EstadoAlerta',
    'AlertaCreate', 'AlertaUpdate', 'AlertaResponse',
    'UmbralAlerta', 'ConfigurarAlertasRequest', 'ConfigurarAlertasResponse',
    'AlertaFiltros', 'AlertasListResponse',
    'MarcarLeidaResponse',
    'CambiarEstadoRequest', 'CambiarEstadoResponse',
    'ResumenAlertas', 'AlertaConContexto',

    # Data Upload
    'DataType', 'UploadStatus',
    'UploadResponse', 'ValidateRequest', 'ValidateResponse', 'ColumnValidation',
    'PreviewRequest', 'PreviewResponse',
    'CleaningOptions', 'CleanRequest', 'CleaningResult', 'CleanResponse',
    'ConfirmRequest', 'ConfirmResponse',
    'QualityMetric', 'QualityReportResponse'
]
