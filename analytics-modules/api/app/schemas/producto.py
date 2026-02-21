"""
Esquemas DTO (Pydantic) para el módulo de Productos y Categorías.
"""

from pydantic import BaseModel, Field, ConfigDict
from typing import Optional
from decimal import Decimal


# Esquemas de Categoría
class CategoriaBase(BaseModel):
    """Esquema base de Categoría."""
    nombre: str = Field(..., description="Nombre de la categoría")
    descripcion: Optional[str] = None
    activo: Optional[int] = 1


class CategoriaCreate(CategoriaBase):
    """Esquema para crear una Categoría."""
    pass


class CategoriaUpdate(BaseModel):
    """Esquema para actualizar una Categoría."""
    nombre: Optional[str] = None
    descripcion: Optional[str] = None
    activo: Optional[int] = None


class CategoriaResponse(CategoriaBase):
    """Esquema de respuesta de Categoría."""
    idCategoria: int

    model_config = ConfigDict(from_attributes=True)


# Esquemas de Producto
class ProductoBase(BaseModel):
    """Esquema base de Producto."""
    sku: str = Field(..., description="SKU del producto")
    nombre: str = Field(..., description="Nombre del producto")
    descripcion: Optional[str] = None
    idCategoria: Optional[int] = None
    precioUnitario: Optional[Decimal] = None
    costo: Optional[Decimal] = None
    existencia: Optional[int] = 0
    activo: Optional[int] = 1


class ProductoCreate(ProductoBase):
    """Esquema para crear un Producto."""
    pass


class ProductoUpdate(BaseModel):
    """Esquema para actualizar un Producto."""
    sku: Optional[str] = None
    nombre: Optional[str] = None
    descripcion: Optional[str] = None
    idCategoria: Optional[int] = None
    precioUnitario: Optional[Decimal] = None
    costo: Optional[Decimal] = None
    existencia: Optional[int] = None
    activo: Optional[int] = None


class ProductoResponse(ProductoBase):
    """Esquema de respuesta de Producto."""
    idProducto: int
    costoUnitario: Optional[Decimal] = None
    categoriaNombre: Optional[str] = None

    model_config = ConfigDict(from_attributes=True)
