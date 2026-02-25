"""
Repositorio para modelo de Prediccion.
"""

from typing import Optional, List
from sqlalchemy.orm import Session
from sqlalchemy import desc, func
from datetime import datetime
from decimal import Decimal
import logging

from app.models import Prediccion
from .base_repository import BaseRepository

logger = logging.getLogger(__name__)


class PrediccionRepository(BaseRepository[Prediccion]):
    """Repositorio especifico para Prediccion."""

    def __init__(self, db: Session):
        super().__init__(Prediccion, db)

    def get_by_version(self, id_version: int) -> List[Prediccion]:
        """
        Obtiene predicciones de una version de modelo.

        Args:
            id_version: ID de la version del modelo

        Returns:
            List[Prediccion]: Lista de predicciones
        """
        try:
            return self.db.query(Prediccion).filter(
                Prediccion.idVersion == id_version
            ).order_by(Prediccion.periodo).all()
        except Exception as e:
            logger.error(f"Error al obtener predicciones por version: {str(e)}")
            return []

    def get_by_entidad(self, tipo_entidad: str, id_entidad: int) -> List[Prediccion]:
        """
        Obtiene predicciones de una entidad especifica.

        Args:
            tipo_entidad: Tipo de entidad (Producto, Categoria, General)
            id_entidad: ID de la entidad
        """
        try:
            return self.db.query(Prediccion).filter(
                Prediccion.entidad == tipo_entidad,
                Prediccion.claveEntidad == id_entidad
            ).order_by(desc(Prediccion.periodo)).all()
        except Exception as e:
            logger.error(f"Error al obtener predicciones por entidad: {str(e)}")
            return []

    def get_by_periodo(self, periodo: str) -> List[Prediccion]:
        """
        Obtiene predicciones para un periodo especifico.

        Args:
            periodo: Periodo en formato YYYY-MM

        Returns:
            List[Prediccion]: Lista de predicciones
        """
        try:
            return self.db.query(Prediccion).filter(
                Prediccion.periodo == periodo
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener predicciones por periodo: {str(e)}")
            return []

    def get_ultimas_predicciones(
        self, tipo_entidad: str, id_entidad: int, limite: int = 6
    ) -> List[Prediccion]:
        """
        Obtiene las ultimas predicciones de una entidad.

        Args:
            tipo_entidad: Tipo de entidad
            id_entidad: ID de la entidad
            limite: Numero maximo de predicciones
        """
        try:
            return self.db.query(Prediccion).filter(
                Prediccion.entidad == tipo_entidad,
                Prediccion.claveEntidad == id_entidad
            ).order_by(desc(Prediccion.periodo)).limit(limite).all()
        except Exception as e:
            logger.error(f"Error al obtener ultimas predicciones: {str(e)}")
            return []

    def get_predicciones_alta_confianza(self, confianza_minima: float = 0.7) -> List[Prediccion]:
        """
        Obtiene predicciones con alta confianza.

        Args:
            confianza_minima: Nivel minimo de confianza
        """
        try:
            return self.db.query(Prediccion).filter(
                Prediccion.nivelConfianza >= confianza_minima
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener predicciones alta confianza: {str(e)}")
            return []

    def get_historial_predicciones(
        self, tipo_entidad: str, id_entidad: int,
        fecha_inicio: datetime, fecha_fin: datetime
    ) -> List[Prediccion]:
        """
        Obtiene historial de predicciones en un rango de fechas.

        Args:
            tipo_entidad: Tipo de entidad
            id_entidad: ID de la entidad
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final
        """
        try:
            return self.db.query(Prediccion).filter(
                Prediccion.entidad == tipo_entidad,
                Prediccion.claveEntidad == id_entidad,
                Prediccion.periodo >= fecha_inicio.strftime('%Y-%m-%d'),
                Prediccion.periodo <= fecha_fin.strftime('%Y-%m-%d')
            ).order_by(Prediccion.periodo).all()
        except Exception as e:
            logger.error(f"Error al obtener historial de predicciones: {str(e)}")
            return []

    def get_estadisticas_precision(self, id_version: int) -> dict:
        """
        Obtiene estadisticas de precision de predicciones de una version.

        Args:
            id_version: ID de la version del modelo

        Returns:
            dict: Estadisticas de precision
        """
        try:
            result = self.db.query(
                func.count(Prediccion.idPred).label('total'),
                func.avg(Prediccion.nivelConfianza).label('confianza_promedio'),
                func.min(Prediccion.nivelConfianza).label('confianza_minima'),
                func.max(Prediccion.nivelConfianza).label('confianza_maxima')
            ).filter(
                Prediccion.idVersion == id_version
            ).first()

            return {
                'total': result.total or 0,
                'confianza_promedio': float(result.confianza_promedio or 0),
                'confianza_minima': float(result.confianza_minima or 0),
                'confianza_maxima': float(result.confianza_maxima or 0)
            }
        except Exception as e:
            logger.error(f"Error al obtener estadisticas de precision: {str(e)}")
            return {
                'total': 0,
                'confianza_promedio': 0,
                'confianza_minima': 0,
                'confianza_maxima': 0
            }

    def eliminar_predicciones_version(self, id_version: int) -> int:
        """
        Elimina todas las predicciones de una version.

        Args:
            id_version: ID de la version

        Returns:
            int: Numero de predicciones eliminadas
        """
        try:
            result = self.db.query(Prediccion).filter(
                Prediccion.idVersion == id_version
            ).delete()
            self.db.commit()
            return result
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al eliminar predicciones: {str(e)}")
            return 0
