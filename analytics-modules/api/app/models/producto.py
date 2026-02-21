"""
Modelos DAO para el módulo de Productos y Categorías.
"""

from sqlalchemy import Column, Integer, String, DECIMAL, ForeignKey
from sqlalchemy.orm import relationship

from app.database import Base


class Categoria(Base):
    """Modelo de Categoría de productos."""

    __tablename__ = 'Categoria'

    idCategoria = Column(Integer, primary_key=True, index=True, autoincrement=True)
    nombre = Column(String(120), unique=True, nullable=False)
    descripcion = Column(String(255), nullable=True)

    # Relaciones
    productos = relationship("Producto", back_populates="categoria")

    def __repr__(self):
        return f"<Categoria(id={self.idCategoria}, nombre={self.nombre})>"


class Producto(Base):
    """Modelo de Producto."""

    __tablename__ = 'Producto'

    idProducto = Column(Integer, primary_key=True, index=True, autoincrement=True)
    sku = Column(String(60), nullable=True)
    nombre = Column(String(160), nullable=False)
    idCategoria = Column(Integer, ForeignKey('Categoria.idCategoria'), nullable=False)
    costoUnitario = Column(DECIMAL(18, 2), nullable=True)
    precioUnitario = Column(DECIMAL(18, 2), nullable=True)
    activo = Column(Integer, nullable=True, default=1)
    creadoPor = Column(Integer, ForeignKey('Usuario.idUsuario'), nullable=True)

    # Relaciones
    categoria = relationship("Categoria", back_populates="productos")
    detalles_venta = relationship("DetalleVenta", back_populates="producto")
    detalles_compra = relationship("DetalleCompra", back_populates="producto")

    def __repr__(self):
        return f"<Producto(id={self.idProducto}, sku={self.sku}, nombre={self.nombre})>"
