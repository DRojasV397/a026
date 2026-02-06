"""
Modelos DAO para el módulo de Compras.
"""

from sqlalchemy import Column, Integer, String, DECIMAL, Date, ForeignKey
from sqlalchemy.orm import relationship

from app.database import Base


class Compra(Base):
    """Modelo de Compra."""

    __tablename__ = 'Compra'

    idCompra = Column(Integer, primary_key=True, index=True, autoincrement=True)
    fecha = Column(Date, nullable=False, index=True)
    proveedor = Column(String(120), nullable=True)
    total = Column(DECIMAL(18, 2), nullable=True)
    moneda = Column(String(3), default='MXN')
    creadoPor = Column(Integer, ForeignKey('Usuario.idUsuario'), nullable=True)

    # Relaciones
    creador = relationship("Usuario", foreign_keys=[creadoPor], back_populates="compras_creadas")
    detalles = relationship("DetalleCompra", back_populates="compra", cascade="all, delete-orphan")

    def __repr__(self):
        return f"<Compra(id={self.idCompra}, fecha={self.fecha}, proveedor={self.proveedor})>"


class DetalleCompra(Base):
    """Modelo de Detalle de Compra."""

    __tablename__ = 'DetalleCompra'

    idCompra = Column(Integer, ForeignKey('Compra.idCompra'), primary_key=True)
    renglon = Column(Integer, primary_key=True)
    idProducto = Column(Integer, ForeignKey('Producto.idProducto'), nullable=False)
    cantidad = Column(DECIMAL(18, 4), nullable=False)
    costo = Column(DECIMAL(18, 2), nullable=False)
    descuento = Column(DECIMAL(18, 2), default=0)
    subtotal = Column(DECIMAL(18, 2), nullable=True)

    # Relaciones
    compra = relationship("Compra", back_populates="detalles")
    producto = relationship("Producto", back_populates="detalles_compra")

    def __repr__(self):
        return f"<DetalleCompra(compra={self.idCompra}, renglon={self.renglon})>"
