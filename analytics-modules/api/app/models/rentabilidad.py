"""
Modelos DAO para el módulo de Rentabilidad y Reportes.
"""

from sqlalchemy import Column, Integer, String, DECIMAL, Date, DateTime, Text, ForeignKey
from sqlalchemy.orm import relationship
from datetime import datetime

from app.database import Base


class Rentabilidad(Base):
    """Modelo de Rentabilidad."""

    __tablename__ = 'Rentabilidad'

    idRentabilidad = Column(Integer, primary_key=True, index=True, autoincrement=True)
    tipoEntidad = Column(String(40), nullable=False)
    idEntidad = Column(Integer, nullable=False)
    periodo = Column(String(20), nullable=False, index=True)
    ingresos = Column(DECIMAL(18, 2), nullable=True)
    costos = Column(DECIMAL(18, 2), nullable=True)
    gastos = Column(DECIMAL(18, 2), nullable=True)
    margenBruto = Column(DECIMAL(18, 2), nullable=True)
    margenNeto = Column(DECIMAL(18, 2), nullable=True)
    roi = Column(DECIMAL(8, 4), nullable=True)
    fechaCalculo = Column(DateTime, default=datetime.now)

    def __repr__(self):
        return f"<Rentabilidad(id={self.idRentabilidad}, periodo={self.periodo})>"


class ResultadoFinanciero(Base):
    """Modelo de Resultado Financiero."""

    __tablename__ = 'ResultadoFinanciero'

    idResultado = Column(Integer, primary_key=True, index=True, autoincrement=True)
    idVersion = Column(Integer, ForeignKey('VersionModelo.idVersion'), nullable=True)
    periodo = Column(String(20), nullable=False, index=True)
    indicador = Column(String(60), nullable=False)
    valor = Column(DECIMAL(18, 2), nullable=True)
    variacion = Column(DECIMAL(8, 4), nullable=True)
    tendencia = Column(String(20), nullable=True)
    fechaCalculo = Column(DateTime, default=datetime.now)

    # Relaciones
    version = relationship("VersionModelo", back_populates="resultados_financieros")

    def __repr__(self):
        return f"<ResultadoFinanciero(id={self.idResultado}, indicador={self.indicador})>"


class Reporte(Base):
    """Modelo de Reporte."""

    __tablename__ = 'Reporte'

    idReporte = Column(Integer, primary_key=True, index=True, autoincrement=True)
    tipo = Column(String(60), nullable=False)
    formato = Column(String(20), nullable=True)
    periodo = Column(String(40), nullable=True)
    generadoPor = Column(Integer, ForeignKey('Usuario.idUsuario'), nullable=True)
    generadoEn = Column(DateTime, default=datetime.now)

    # Relaciones
    generador = relationship("Usuario", foreign_keys=[generadoPor], back_populates="reportes_generados")

    def __repr__(self):
        return f"<Reporte(id={self.idReporte}, tipo={self.tipo})>"


class Alerta(Base):
    """Modelo de Alerta."""

    __tablename__ = 'Alerta'

    idAlerta = Column(Integer, primary_key=True, index=True, autoincrement=True)
    idPred = Column(Integer, ForeignKey('Prediccion.idPred'), nullable=False)
    tipo = Column(String(20), nullable=False)
    importancia = Column(String(10), nullable=False)
    metrica = Column(String(40), nullable=False)
    valorActual = Column(DECIMAL(18, 2), nullable=False)
    valorEsperado = Column(DECIMAL(18, 2), nullable=True)
    nivelConfianza = Column(DECIMAL(5, 4), nullable=True)
    estado = Column(String(12), default='Activa')
    creadaEn = Column(DateTime, default=datetime.now)

    # Relaciones
    prediccion = relationship("Prediccion", back_populates="alertas")

    def __repr__(self):
        return f"<Alerta(id={self.idAlerta}, tipo={self.tipo}, importancia={self.importancia})>"
