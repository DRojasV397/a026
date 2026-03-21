"""
Esquemas DTO (Pydantic) para el modulo de Predicciones y Modelos ML.
"""

from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, List, Dict, Any
from decimal import Decimal
from datetime import datetime, date
from enum import Enum


class TipoModelo(str, Enum):
    """Tipos de modelos predictivos disponibles."""
    LINEAR_REGRESSION = "linear_regression"
    MULTIPLE_REGRESSION = "multiple_regression"
    ARIMA = "arima"
    SARIMA = "sarima"
    RANDOM_FOREST = "random_forest"
    XGBOOST = "xgboost"
    KMEANS = "kmeans"
    ENSEMBLE = "ensemble"
    PROPHET = "prophet"
    PACK = "pack"


class EstadoModelo(str, Enum):
    """Estados posibles de un modelo."""
    ACTIVO = "Activo"
    INACTIVO = "Inactivo"
    ENTRENANDO = "Entrenando"
    ERROR = "Error"


class TipoEntidad(str, Enum):
    """Tipos de entidades para prediccion."""
    PRODUCTO = "Producto"
    CATEGORIA = "Categoria"
    GENERAL = "General"


# Esquemas de Modelo
class ModeloBase(BaseModel):
    """Esquema base de Modelo Predictivo."""
    tipoModelo: str = Field(..., max_length=40, description="Tipo de modelo (linear_regression, arima, etc.)")
    objetivo: Optional[str] = Field(None, max_length=120, description="Objetivo del modelo")


class ModeloCreate(ModeloBase):
    """Esquema para crear un Modelo."""
    pass


class ModeloResponse(ModeloBase):
    """Esquema de respuesta de Modelo."""
    idModelo: int
    creadoEn: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


# Esquemas de Version de Modelo
class VersionModeloBase(BaseModel):
    """Esquema base de Version de Modelo."""
    idModelo: int
    numeroVersion: str = Field(..., max_length=20)
    algoritmo: Optional[str] = None
    parametros: Optional[str] = None  # JSON string
    metricas: Optional[str] = None  # JSON string
    precision: Optional[Decimal] = Field(None, ge=0, le=1)
    estado: Optional[str] = Field(default="Activo")


class VersionModeloCreate(VersionModeloBase):
    """Esquema para crear una Version de Modelo."""
    pass


class VersionModeloResponse(VersionModeloBase):
    """Esquema de respuesta de Version de Modelo."""
    idVersion: int
    fechaEntrenamiento: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


# Esquemas de Prediccion
class PrediccionBase(BaseModel):
    """Esquema base de Prediccion."""
    idVersion: int
    tipoEntidad: str = Field(..., description="Producto, Categoria o General")
    idEntidad: int
    periodo: str = Field(..., description="Formato YYYY-MM")
    valorPredicho: Optional[Decimal] = None
    confianza: Optional[Decimal] = Field(None, ge=0, le=1)


class PrediccionCreate(PrediccionBase):
    """Esquema para crear una Prediccion."""
    pass


class PrediccionResponse(PrediccionBase):
    """Esquema de respuesta de Prediccion."""
    idPred: int
    fechaPrediccion: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


# Esquemas para Entrenamiento
class TrainModelRequest(BaseModel):
    """Request para entrenar un modelo."""
    tipoModelo: TipoModelo = Field(..., description="Tipo de modelo a entrenar")
    tipoEntidad: TipoEntidad = Field(default=TipoEntidad.GENERAL)
    idEntidad: Optional[int] = Field(None, description="ID de producto/categoria especifico")
    parametros: Optional[Dict[str, Any]] = Field(default={}, description="Hiperparametros del modelo")
    descripcion: Optional[str] = None


class TrainModelResponse(BaseModel):
    """Respuesta de entrenamiento de modelo."""
    idModelo: int
    idVersion: int
    tipoModelo: str
    metricas: Dict[str, float]
    precision: float
    mensaje: str = "Modelo entrenado exitosamente"


# Esquemas para Forecast
class ForecastRequest(BaseModel):
    """Request para ejecutar prediccion."""
    idVersion: int = Field(..., description="ID de la version del modelo a usar")
    tipoEntidad: TipoEntidad
    idEntidad: Optional[int] = None
    periodos: int = Field(default=3, ge=1, le=12, description="Numero de periodos a predecir")


class ForecastItem(BaseModel):
    """Item individual de prediccion."""
    periodo: str
    valorPredicho: Decimal
    confianza: Decimal
    limiteInferior: Optional[Decimal] = None
    limiteSuperior: Optional[Decimal] = None


class ForecastResponse(BaseModel):
    """Respuesta de prediccion."""
    idVersion: int
    tipoEntidad: str
    idEntidad: Optional[int]
    predicciones: List[ForecastItem]
    fechaGeneracion: datetime = Field(default_factory=datetime.now)


# Esquemas de Metricas
class ModelMetrics(BaseModel):
    """Metricas de evaluacion del modelo."""
    r2_score: Optional[float] = Field(None, description="Coeficiente de determinacion R2")
    rmse: Optional[float] = Field(None, description="Root Mean Square Error")
    mae: Optional[float] = Field(None, description="Mean Absolute Error")
    mape: Optional[float] = Field(None, description="Mean Absolute Percentage Error")


class ModelMetricsResponse(BaseModel):
    """Respuesta con metricas del modelo."""
    idVersion: int
    tipoModelo: str
    metricas: ModelMetrics
    fechaEntrenamiento: datetime
    cumpleUmbral: bool = Field(..., description="True si R2 > 0.7")


# Comparacion de Modelos
class CompareModelsRequest(BaseModel):
    """Request para comparar modelos."""
    version_ids: List[int] = Field(..., min_length=2, max_length=5)


class ModelComparison(BaseModel):
    """Comparacion individual de modelo."""
    idVersion: int
    tipoModelo: str
    metricas: ModelMetrics
    ranking: int


class CompareModelsResponse(BaseModel):
    """Respuesta de comparacion de modelos."""
    comparaciones: List[ModelComparison]
    mejorModelo: int = Field(..., description="ID de la version con mejor desempeno")


# ── Pack de Modelos (Ventas + Compras) ───────────────────────────────────────

class PackMetrics(BaseModel):
    """Métricas de un submodelo del pack."""
    model_key: Optional[str] = None
    metrics: Optional[Dict[str, Any]] = None
    meets_r2_threshold: Optional[bool] = None


class PackTrainRequest(BaseModel):
    """Request para entrenar un pack ventas+compras."""
    nombre: Optional[str] = Field(None, description="Nombre descriptivo del pack")
    fecha_inicio: Optional[date] = Field(None, description="Fecha inicio de datos de entrenamiento")
    fecha_fin: Optional[date] = Field(None, description="Fecha fin de datos de entrenamiento")
    hyperparameters: Optional[Dict[str, Any]] = Field(
        None,
        description="HP por submodelo: {ventas: {...}, compras: {...}}"
    )
    ventas_model_type: Optional[str] = Field(
        "multiple_regression",
        description="Tipo de modelo para ventas (linear, arima, ensemble, xgboost, prophet, etc.)"
    )


class PackTrainResponse(BaseModel):
    """Respuesta del entrenamiento de un pack."""
    success: bool
    pack_id: Optional[int] = None
    pack_key: Optional[str] = None
    ventas: Optional[PackMetrics] = None
    compras: Optional[PackMetrics] = None
    error: Optional[str] = None
    issues: Optional[List[str]] = None


class PackForecastRequest(BaseModel):
    """Request para forecast coordinado de un pack."""
    pack_key: str = Field(..., description="Clave del pack")
    periods: int = Field(30, ge=1, le=180, description="Períodos a predecir (max 180)")


class PackForecastSeries(BaseModel):
    """Serie de predicciones de un submodelo del pack."""
    predictions: Optional[Dict[str, Any]] = None
    model_key: Optional[str] = None


class PackForecastResponse(BaseModel):
    """Respuesta de forecast coordinado de un pack."""
    success: bool
    pack_key: Optional[str] = None
    periods: Optional[int] = None
    ventas: Optional[PackForecastSeries] = None
    compras: Optional[PackForecastSeries] = None
    error: Optional[str] = None


class PackVersionInfo(BaseModel):
    """Información de la versión de un submodelo del pack."""
    version_id: Optional[int] = None
    precision: Optional[float] = None
    metricas: Optional[Dict[str, Any]] = None


class PackInfoResponse(BaseModel):
    """Información de un pack entrenado."""
    pack_id: int
    pack_key: str
    nombre: Optional[str] = None
    creado_en: Optional[str] = None
    ventas: Optional[PackVersionInfo] = None
    compras: Optional[PackVersionInfo] = None
