"""
Modelo DAO para el historial de cargas de datos.
Registra cada sesión de carga para trazabilidad por usuario.
"""

from sqlalchemy import Column, Integer, String, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from datetime import datetime

from app.database import Base


class HistorialCarga(Base):
    """Registra cada sesión de carga de datos realizada por un usuario."""

    __tablename__ = 'HistorialCarga'

    idHistorial = Column(Integer, primary_key=True, autoincrement=True)
    uploadId = Column(String(36), nullable=False)
    tipoDatos = Column(String(20), nullable=False)
    nombreArchivo = Column(String(255), nullable=True)
    registrosInsertados = Column(Integer, default=0)
    registrosActualizados = Column(Integer, default=0)
    cargadoPor = Column(Integer, ForeignKey('Usuario.idUsuario'), nullable=False)
    cargadoEn = Column(DateTime, default=datetime.now)
    estado = Column(String(20), default='exitoso')

    # Relación
    usuario = relationship("Usuario", foreign_keys=[cargadoPor])

    def __repr__(self):
        return f"<HistorialCarga(id={self.idHistorial}, tipo={self.tipoDatos}, usuario={self.cargadoPor})>"
