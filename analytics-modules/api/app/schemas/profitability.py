"""
Esquemas DTO (Pydantic) para el modulo de Rentabilidad.
"""

from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, List
from decimal import Decimal
from datetime import datetime
from enum import Enum


class TipoPeriodo(str, Enum):
    """Tipos de periodo para calculos."""
    MENSUAL = "mensual"
    TRIMESTRAL = "trimestral"
    ANUAL = "anual"


class TipoEntidadRentabilidad(str, Enum):
    """Tipos de entidad para rentabilidad."""
    PRODUCTO = "Producto"
    CATEGORIA = "Categoria"
    GENERAL = "General"


# Esquemas de Rentabilidad
class RentabilidadBase(BaseModel):
    """Esquema base de Rentabilidad."""
    tipoEntidad: str
    idEntidad: int
    periodo: str = Field(..., description="Formato YYYY-MM o YYYY-QN o YYYY")
    ingresos: Optional[Decimal] = None
    costos: Optional[Decimal] = None
    gastos: Optional[Decimal] = None
    margenBruto: Optional[Decimal] = None
    margenNeto: Optional[Decimal] = None
    roi: Optional[Decimal] = None


class RentabilidadCreate(RentabilidadBase):
    """Esquema para crear un registro de Rentabilidad."""
    pass


class RentabilidadResponse(RentabilidadBase):
    """Esquema de respuesta de Rentabilidad."""
    idRentabilidad: int
    fechaCalculo: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


# Esquemas de Resultado Financiero
class ResultadoFinancieroBase(BaseModel):
    """Esquema base de Resultado Financiero."""
    idVersion: Optional[int] = None
    periodo: str
    indicador: str
    valor: Optional[Decimal] = None
    variacion: Optional[Decimal] = None
    tendencia: Optional[str] = None


class ResultadoFinancieroCreate(ResultadoFinancieroBase):
    """Esquema para crear un Resultado Financiero."""
    pass


class ResultadoFinancieroResponse(ResultadoFinancieroBase):
    """Esquema de respuesta de Resultado Financiero."""
    idResultado: int
    fechaCalculo: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


# Request para Calculos
class CalcularRentabilidadRequest(BaseModel):
    """Request para calcular rentabilidad."""
    tipoEntidad: TipoEntidadRentabilidad = TipoEntidadRentabilidad.GENERAL
    idEntidad: Optional[int] = None
    tipoPeriodo: TipoPeriodo = TipoPeriodo.MENSUAL
    periodoInicio: str = Field(..., description="Periodo inicial (YYYY-MM)")
    periodoFin: Optional[str] = Field(None, description="Periodo final (YYYY-MM)")


class IndicadoresFinancieros(BaseModel):
    """Indicadores financieros calculados."""
    ingresos: Decimal
    costos: Decimal
    gastos: Decimal
    utilidadBruta: Decimal
    utilidadOperativa: Decimal
    utilidadNeta: Decimal
    margenBruto: Decimal = Field(..., description="Porcentaje")
    margenOperativo: Decimal = Field(..., description="Porcentaje")
    margenNeto: Decimal = Field(..., description="Porcentaje")
    roi: Optional[Decimal] = None
    roa: Optional[Decimal] = None
    roe: Optional[Decimal] = None


class CalcularRentabilidadResponse(BaseModel):
    """Respuesta de calculo de rentabilidad."""
    tipoEntidad: str
    idEntidad: Optional[int]
    periodo: str
    indicadores: IndicadoresFinancieros
    esRentable: bool = Field(..., description="True si margen > 10%")
    fechaCalculo: datetime = Field(default_factory=datetime.now)


# Rentabilidad por Producto
class RentabilidadProducto(BaseModel):
    """Rentabilidad individual de un producto."""
    idProducto: int
    nombreProducto: str
    sku: Optional[str] = None
    ingresos: Decimal
    costos: Decimal
    utilidad: Decimal
    margen: Decimal
    esRentable: bool


class RentabilidadProductosResponse(BaseModel):
    """Respuesta de rentabilidad por productos."""
    periodo: str
    productos: List[RentabilidadProducto]
    totalProductos: int
    productosRentables: int
    productosNoRentables: int


# Rentabilidad por Categoria
class RentabilidadCategoria(BaseModel):
    """Rentabilidad de una categoria."""
    idCategoria: int
    nombreCategoria: str
    ingresos: Decimal
    costos: Decimal
    utilidad: Decimal
    margen: Decimal
    numeroProductos: int


class RentabilidadCategoriasResponse(BaseModel):
    """Respuesta de rentabilidad por categorias."""
    periodo: str
    categorias: List[RentabilidadCategoria]


# Tendencias de Rentabilidad
class TendenciaItem(BaseModel):
    """Item de tendencia temporal."""
    periodo: str
    valor: Decimal
    variacion: Optional[Decimal] = None
    tendencia: str = Field(..., description="Alza, Baja, Estable")


class TendenciasRentabilidadResponse(BaseModel):
    """Respuesta de tendencias de rentabilidad."""
    tipoEntidad: str
    idEntidad: Optional[int]
    indicador: str
    tendencias: List[TendenciaItem]
    tendenciaGeneral: str


# Ranking de Productos
class ProductoRanking(BaseModel):
    """Producto en el ranking."""
    posicion: int
    idProducto: int
    nombreProducto: str
    margen: Decimal
    utilidad: Decimal


class RankingProductosResponse(BaseModel):
    """Respuesta de ranking de productos."""
    periodo: str
    criterio: str = Field(default="margen", description="Criterio de ordenamiento")
    ranking: List[ProductoRanking]
