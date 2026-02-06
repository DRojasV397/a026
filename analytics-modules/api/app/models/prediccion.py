"""
Modelos DAO para el módulo de Modelos Predictivos y Escenarios.
"""

from sqlalchemy import Column, Integer, String, DECIMAL, Date, DateTime, Text, ForeignKey
from sqlalchemy.orm import relationship
from datetime import datetime

from app.database import Base


class Modelo(Base):
    """Modelo de Modelo Predictivo."""

    __tablename__ = 'Modelo'

    idModelo = Column(Integer, primary_key=True, index=True, autoincrement=True)
    tipoModelo = Column(String(40), nullable=False)
    objetivo = Column(String(120), nullable=True)
    creadoEn = Column(DateTime, default=datetime.now)

    # Relaciones
    versiones = relationship("VersionModelo", back_populates="modelo", cascade="all, delete-orphan")

    def __repr__(self):
        return f"<Modelo(id={self.idModelo}, tipo={self.tipoModelo})>"


class VersionModelo(Base):
    """Modelo de Versión de Modelo."""

    __tablename__ = 'VersionModelo'

    idVersion = Column(Integer, primary_key=True, index=True, autoincrement=True)
    idModelo = Column(Integer, ForeignKey('Modelo.idModelo'), nullable=False)
    numeroVersion = Column(String(20), nullable=False)
    algoritmo = Column(String(80), nullable=True)
    parametros = Column(Text, nullable=True)
    metricas = Column(Text, nullable=True)
    precision = Column(DECIMAL(5, 4), nullable=True)
    estado = Column(String(20), default='Activo')
    fechaEntrenamiento = Column(DateTime, default=datetime.now)

    # Relaciones
    modelo = relationship("Modelo", back_populates="versiones")
    predicciones = relationship("Prediccion", back_populates="version")
    escenarios = relationship("Escenario", back_populates="version_base")
    resultados_financieros = relationship("ResultadoFinanciero", back_populates="version")

    def __repr__(self):
        return f"<VersionModelo(id={self.idVersion}, modelo={self.idModelo}, estado={self.estado})>"


class Prediccion(Base):
    """Modelo de Predicción."""

    __tablename__ = 'Prediccion'

    idPred = Column(Integer, primary_key=True, index=True, autoincrement=True)
    idVersion = Column(Integer, ForeignKey('VersionModelo.idVersion'), nullable=False)
    entidad = Column(String(40), nullable=False)
    claveEntidad = Column(Integer, nullable=False)
    periodo = Column(String(20), nullable=False, index=True)
    valorPredicho = Column(DECIMAL(18, 2), nullable=True)
    nivelConfianza = Column(DECIMAL(5, 4), nullable=True)

    # Relaciones
    version = relationship("VersionModelo", back_populates="predicciones")
    alertas = relationship("Alerta", back_populates="prediccion")

    def __repr__(self):
        return f"<Prediccion(id={self.idPred}, entidad={self.entidad}, periodo={self.periodo})>"


class Escenario(Base):
    """Modelo de Escenario de simulación."""

    __tablename__ = 'Escenario'

    idEscenario = Column(Integer, primary_key=True, index=True, autoincrement=True)
    nombre = Column(String(120), nullable=False)
    descripcion = Column(Text, nullable=True)
    horizonteMeses = Column(Integer, nullable=True)
    baseVersion = Column(Integer, ForeignKey('VersionModelo.idVersion'), nullable=True)
    creadoPor = Column(Integer, ForeignKey('Usuario.idUsuario'), nullable=False)
    creadoEn = Column(DateTime, default=datetime.now)

    # Relaciones
    version_base = relationship("VersionModelo", back_populates="escenarios", foreign_keys=[baseVersion])
    creador = relationship("Usuario", foreign_keys=[creadoPor], overlaps="escenarios_creados")
    parametros = relationship("ParametroEscenario", back_populates="escenario", cascade="all, delete-orphan")
    resultados = relationship("ResultadoEscenario", back_populates="escenario", cascade="all, delete-orphan")

    def __repr__(self):
        return f"<Escenario(id={self.idEscenario}, nombre={self.nombre})>"


class ParametroEscenario(Base):
    """Modelo de Parámetro de Escenario."""

    __tablename__ = 'ParametroEscenario'

    idEscenario = Column(Integer, ForeignKey('Escenario.idEscenario'), primary_key=True)
    parametro = Column(String(60), primary_key=True)
    valorBase = Column(DECIMAL(18, 2), nullable=True)
    valorActual = Column(DECIMAL(18, 2), nullable=True)

    # Relaciones
    escenario = relationship("Escenario", back_populates="parametros")

    def __repr__(self):
        return f"<ParametroEscenario(escenario={self.idEscenario}, parametro={self.parametro})>"


class ResultadoEscenario(Base):
    """Modelo de Resultado de Escenario."""

    __tablename__ = 'ResultadoEscenario'

    idEscenario = Column(Integer, ForeignKey('Escenario.idEscenario'), primary_key=True)
    periodo = Column(Date, primary_key=True)
    kpi = Column(String(60), primary_key=True)
    valor = Column(DECIMAL(18, 2), nullable=True)
    confianza = Column(DECIMAL(5, 4), nullable=True)

    # Relaciones
    escenario = relationship("Escenario", back_populates="resultados")

    def __repr__(self):
        return f"<ResultadoEscenario(escenario={self.idEscenario}, kpi={self.kpi})>"
