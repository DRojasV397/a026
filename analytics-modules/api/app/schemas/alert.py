"""
Esquemas DTO (Pydantic) para el modulo de Alertas.
"""

from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, List
from datetime import datetime
from decimal import Decimal
from enum import Enum


class TipoAlerta(str, Enum):
    """Tipos de alerta del sistema."""
    RIESGO = "Riesgo"
    OPORTUNIDAD = "Oportunidad"
    ANOMALIA = "Anomalia"
    TENDENCIA = "Tendencia"
    UMBRAL = "Umbral"


class ImportanciaAlerta(str, Enum):
    """Niveles de importancia de alertas."""
    ALTA = "Alta"
    MEDIA = "Media"
    BAJA = "Baja"


class EstadoAlerta(str, Enum):
    """Estados posibles de una alerta."""
    ACTIVA = "Activa"
    LEIDA = "Leida"
    RESUELTA = "Resuelta"
    IGNORADA = "Ignorada"


# Esquemas de Alerta
class AlertaBase(BaseModel):
    """Esquema base de Alerta."""
    idPred: int = Field(..., description="ID de la prediccion asociada")
    tipo: str = Field(..., max_length=20, description="Tipo de alerta")
    importancia: str = Field(..., max_length=10, description="Nivel de importancia")
    metrica: str = Field(..., max_length=40, description="Metrica evaluada")
    valorActual: Decimal = Field(..., description="Valor actual de la metrica")
    valorEsperado: Optional[Decimal] = Field(None, description="Valor esperado")
    nivelConfianza: Optional[Decimal] = Field(None, ge=0, le=1, description="Nivel de confianza")


class AlertaCreate(AlertaBase):
    """Esquema para crear una Alerta."""
    pass


class AlertaUpdate(BaseModel):
    """Esquema para actualizar una Alerta."""
    estado: Optional[str] = Field(None, max_length=12)


class AlertaResponse(AlertaBase):
    """Esquema de respuesta de Alerta."""
    idAlerta: int
    estado: str
    creadaEn: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


# Request y Response para Configuracion
class UmbralAlerta(BaseModel):
    """Configuracion de umbral para alertas."""
    tipo: TipoAlerta
    umbralRiesgo: Decimal = Field(default=Decimal('15'), description="Porcentaje de caida para alerta de riesgo")
    umbralOportunidad: Decimal = Field(default=Decimal('20'), description="Porcentaje de subida para oportunidad")
    umbralAnomalia: Decimal = Field(default=Decimal('5'), description="Porcentaje de transacciones anomalas")


class ConfigurarAlertasRequest(BaseModel):
    """Request para configurar umbrales de alertas."""
    umbrales: List[UmbralAlerta]


class ConfigurarAlertasResponse(BaseModel):
    """Respuesta de configuracion de alertas."""
    mensaje: str = "Configuracion actualizada"
    umbralesActualizados: int


# Listado de Alertas
class AlertaFiltros(BaseModel):
    """Filtros para busqueda de alertas."""
    tipo: Optional[TipoAlerta] = None
    importancia: Optional[ImportanciaAlerta] = None
    estado: Optional[EstadoAlerta] = None
    fechaInicio: Optional[datetime] = None
    fechaFin: Optional[datetime] = None


class AlertasListResponse(BaseModel):
    """Respuesta de listado de alertas."""
    alertas: List[AlertaResponse]
    total: int
    activas: int
    porImportancia: dict = Field(default_factory=dict)


# Marcar como leida
class MarcarLeidaResponse(BaseModel):
    """Respuesta de marcar alerta como leida."""
    idAlerta: int
    estado: str
    mensaje: str = "Alerta marcada como leida"


# Cambiar estado
class CambiarEstadoRequest(BaseModel):
    """Request para cambiar estado de alerta."""
    estado: EstadoAlerta
    comentario: Optional[str] = None


class CambiarEstadoResponse(BaseModel):
    """Respuesta de cambio de estado."""
    idAlerta: int
    estadoAnterior: str
    estadoNuevo: str
    mensaje: str = "Estado actualizado"


# Resumen de Alertas
class ResumenAlertas(BaseModel):
    """Resumen de alertas activas."""
    totalActivas: int = Field(..., le=10, description="Maximo 10 alertas simultaneas")
    porTipo: dict
    porImportancia: dict
    alertasRecientes: List[AlertaResponse]


# Alerta con contexto de prediccion
class AlertaConContexto(AlertaResponse):
    """Alerta con informacion de la prediccion asociada."""
    prediccion: Optional[dict] = None
    entidadAfectada: Optional[str] = None
    porcentajeCambio: Optional[Decimal] = None
