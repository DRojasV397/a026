"""
Modelos DAO para el módulo de Usuarios y Seguridad.
"""

from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Table
from sqlalchemy.orm import relationship
from datetime import datetime

from app.database import Base


class Usuario(Base):
    """Modelo de Usuario del sistema."""

    __tablename__ = 'Usuario'

    idUsuario = Column(Integer, primary_key=True, index=True, autoincrement=True)
    nombreCompleto = Column(String(120), nullable=False)
    nombreUsuario = Column(String(60), unique=True, nullable=False, index=True)
    email = Column(String(160), unique=True, nullable=False, index=True)
    hashPassword = Column(String(255), nullable=True)
    estado = Column(String(20), nullable=True)
    creadoEn = Column(DateTime, nullable=True, default=datetime.now)

    # Relaciones
    roles = relationship("UsuarioRol", back_populates="usuario", cascade="all, delete-orphan")
    preferencias = relationship("PreferenciaUsuario", back_populates="usuario", cascade="all, delete-orphan")
    ventas_creadas = relationship("Venta", foreign_keys="Venta.creadoPor", back_populates="creador")
    compras_creadas = relationship("Compra", foreign_keys="Compra.creadoPor", back_populates="creador")
    escenarios_creados = relationship("Escenario", foreign_keys="Escenario.creadoPor", overlaps="creador")
    reportes_generados = relationship("Reporte", foreign_keys="Reporte.generadoPor", back_populates="generador")

    def __repr__(self):
        return f"<Usuario(id={self.idUsuario}, username={self.nombreUsuario})>"


class Rol(Base):
    """Modelo de Rol para control de acceso."""

    __tablename__ = 'Rol'

    idRol = Column(Integer, primary_key=True, index=True, autoincrement=True)
    nombre = Column(String(80), unique=True, nullable=False)

    # Relaciones
    usuarios = relationship("UsuarioRol", back_populates="rol")

    def __repr__(self):
        return f"<Rol(id={self.idRol}, nombre={self.nombreRol})>"


class UsuarioRol(Base):
    """Modelo de relación muchos a muchos entre Usuario y Rol."""

    __tablename__ = 'UsuarioRol'

    idUsuario = Column(Integer, ForeignKey('Usuario.idUsuario'), primary_key=True)
    idRol = Column(Integer, ForeignKey('Rol.idRol'), primary_key=True)
    fechaAsignacion = Column(DateTime, default=datetime.now)

    # Relaciones
    usuario = relationship("Usuario", back_populates="roles")
    rol = relationship("Rol", back_populates="usuarios")

    def __repr__(self):
        return f"<UsuarioRol(usuario={self.idUsuario}, rol={self.idRol})>"


class PreferenciaUsuario(Base):
    """Modelo de Preferencias de Usuario para KPIs."""

    __tablename__ = 'PreferenciaUsuario'

    idPreferencia = Column(Integer, primary_key=True, index=True, autoincrement=True)
    idUsuario = Column(Integer, ForeignKey('Usuario.idUsuario'), nullable=False)
    kpi = Column(String(60), nullable=False)
    visible = Column(Integer, default=1)
    orden = Column(Integer, nullable=True)
    creadoEn = Column(DateTime, default=datetime.now)

    # Relaciones
    usuario = relationship("Usuario", back_populates="preferencias")

    def __repr__(self):
        return f"<PreferenciaUsuario(id={self.idPreferencia}, kpi={self.kpi})>"
