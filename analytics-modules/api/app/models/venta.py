"""
Modelos DAO para el módulo de Ventas.
"""

from sqlalchemy import Column, Integer, String, DECIMAL, Date, ForeignKey
from sqlalchemy.orm import relationship

from app.database import Base


class Venta(Base):
    """Modelo de Venta."""

    __tablename__ = 'Venta'

    idVenta = Column(Integer, primary_key=True, index=True, autoincrement=True)
    fecha = Column(Date, nullable=False)
    total = Column(DECIMAL(18, 2), nullable=True)
    moneda = Column(String(3), default='MXN')
    creadoPor = Column(Integer, ForeignKey('Usuario.idUsuario'), nullable=True)

    # Relaciones
    creador = relationship("Usuario", foreign_keys=[creadoPor], back_populates="ventas_creadas")
    detalles = relationship("DetalleVenta", back_populates="venta", cascade="all, delete-orphan")

    def __repr__(self):
        return f"<Venta(id={self.idVenta}, fecha={self.fecha}, total={self.total})>"


class DetalleVenta(Base):
    """Modelo de Detalle de Venta."""

    __tablename__ = 'DetalleVenta'

    idVenta = Column(Integer, ForeignKey('Venta.idVenta'), primary_key=True)
    renglon = Column(Integer, primary_key=True)
    idProducto = Column(Integer, ForeignKey('Producto.idProducto'), nullable=False)
    cantidad = Column(DECIMAL(18, 4), nullable=False)
    precioUnitario = Column(DECIMAL(18, 2), nullable=False)

    # Relaciones
    venta = relationship("Venta", back_populates="detalles")
    producto = relationship("Producto", back_populates="detalles_venta")

    @property
    def subtotal(self):
        """Calcula subtotal como cantidad * precioUnitario."""
        return float(self.cantidad or 0) * float(self.precioUnitario or 0)

    def __repr__(self):
        return f"<DetalleVenta(venta={self.idVenta}, renglon={self.renglon})>"
