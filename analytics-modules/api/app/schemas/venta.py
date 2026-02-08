"""
Esquemas DTO (Pydantic) para el módulo de Ventas.
"""

from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, List
from decimal import Decimal
from datetime import date, datetime


# Esquemas de DetalleVenta
class DetalleVentaBase(BaseModel):
    """Esquema base de Detalle de Venta."""
    renglon: int
    idProducto: int
    cantidad: Decimal
    precioUnitario: Decimal
    descuento: Optional[Decimal] = 0
    subtotal: Optional[Decimal] = None


class DetalleVentaCreate(BaseModel):
    """Esquema para crear un Detalle de Venta."""
    idProducto: int
    cantidad: Decimal
    precioUnitario: Decimal
    descuento: Optional[Decimal] = 0


class DetalleVentaResponse(DetalleVentaBase):
    """Esquema de respuesta de Detalle de Venta."""
    idVenta: int

    model_config = ConfigDict(from_attributes=True)


# Esquemas de Venta
class VentaBase(BaseModel):
    """Esquema base de Venta."""
    fecha: date
    total: Optional[Decimal] = None
    subtotal: Optional[Decimal] = None
    impuestos: Optional[Decimal] = None
    moneda: Optional[str] = 'MXN'
    idCliente: Optional[int] = None


class VentaCreate(VentaBase):
    """Esquema para crear una Venta."""
    creadoPor: Optional[int] = None
    detalles: Optional[List[DetalleVentaCreate]] = []


class VentaUpdate(BaseModel):
    """Esquema para actualizar una Venta."""
    fecha: Optional[date] = None
    total: Optional[Decimal] = None
    subtotal: Optional[Decimal] = None
    impuestos: Optional[Decimal] = None
    idCliente: Optional[int] = None


class VentaResponse(VentaBase):
    """Esquema de respuesta de Venta."""
    idVenta: int
    creadoPor: Optional[int] = None

    model_config = ConfigDict(from_attributes=True)
