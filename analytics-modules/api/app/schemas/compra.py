"""
Esquemas DTO (Pydantic) para el modulo de Compras.
"""

from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, List
from decimal import Decimal
from datetime import date, datetime


# Esquemas de DetalleCompra
class DetalleCompraBase(BaseModel):
    """Esquema base de Detalle de Compra."""
    renglon: int
    idProducto: int
    cantidad: Decimal
    costo: Decimal
    descuento: Optional[Decimal] = Decimal('0')
    subtotal: Optional[Decimal] = None


class DetalleCompraCreate(BaseModel):
    """Esquema para crear un Detalle de Compra."""
    idProducto: int = Field(..., description="ID del producto")
    cantidad: Decimal = Field(..., gt=0, description="Cantidad comprada")
    costo: Decimal = Field(..., ge=0, description="Costo unitario")
    descuento: Optional[Decimal] = Field(default=Decimal('0'), ge=0)


class DetalleCompraResponse(DetalleCompraBase):
    """Esquema de respuesta de Detalle de Compra."""
    idCompra: int

    model_config = ConfigDict(from_attributes=True)


# Esquemas de Compra
class CompraBase(BaseModel):
    """Esquema base de Compra."""
    fecha: date
    proveedor: Optional[str] = Field(None, max_length=120)
    total: Optional[Decimal] = None
    moneda: Optional[str] = Field(default='MXN', max_length=3)


class CompraCreate(CompraBase):
    """Esquema para crear una Compra."""
    creadoPor: Optional[int] = None
    detalles: Optional[List[DetalleCompraCreate]] = []


class CompraUpdate(BaseModel):
    """Esquema para actualizar una Compra."""
    fecha: Optional[date] = None
    proveedor: Optional[str] = None
    total: Optional[Decimal] = None


class CompraResponse(CompraBase):
    """Esquema de respuesta de Compra."""
    idCompra: int
    creadoPor: Optional[int] = None

    model_config = ConfigDict(from_attributes=True)


class CompraConDetalles(CompraResponse):
    """Esquema de Compra con sus detalles."""
    detalles: List[DetalleCompraResponse] = []


# Filtros de Compras
class CompraFiltros(BaseModel):
    """Filtros para busqueda de compras."""
    fecha_inicio: Optional[date] = None
    fecha_fin: Optional[date] = None
    proveedor: Optional[str] = None
    total_min: Optional[Decimal] = None
    total_max: Optional[Decimal] = None
