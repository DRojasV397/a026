"""
Repositorio para modelos de Modelo Predictivo y VersionModelo.
"""

from typing import Optional, List
from sqlalchemy.orm import Session
from sqlalchemy import desc
from datetime import datetime
import logging

from app.models import Modelo, VersionModelo
from .base_repository import BaseRepository

logger = logging.getLogger(__name__)


class ModeloRepository(BaseRepository[Modelo]):
    """Repositorio especifico para Modelo Predictivo."""

    def __init__(self, db: Session):
        super().__init__(Modelo, db)

    def get_by_tipo(self, tipo_modelo: str) -> List[Modelo]:
        """
        Obtiene modelos por tipo.

        Args:
            tipo_modelo: Tipo de modelo (linear_regression, arima, etc.)

        Returns:
            List[Modelo]: Lista de modelos
        """
        try:
            return self.db.query(Modelo).filter(
                Modelo.tipoModelo == tipo_modelo
            ).all()
        except Exception as e:
            logger.error(f"Error al buscar modelos por tipo: {str(e)}")
            return []

    def get_recientes(self, limite: int = 10) -> List[Modelo]:
        """
        Obtiene los modelos mas recientes.

        Args:
            limite: Numero maximo de modelos

        Returns:
            List[Modelo]: Lista de modelos recientes
        """
        try:
            return self.db.query(Modelo).order_by(
                desc(Modelo.creadoEn)
            ).limit(limite).all()
        except Exception as e:
            logger.error(f"Error al obtener modelos recientes: {str(e)}")
            return []

    def get_with_versiones(self, id_modelo: int) -> Optional[Modelo]:
        """
        Obtiene un modelo con sus versiones.

        Args:
            id_modelo: ID del modelo

        Returns:
            Optional[Modelo]: Modelo con versiones cargadas
        """
        try:
            return self.db.query(Modelo).filter(
                Modelo.idModelo == id_modelo
            ).first()
        except Exception as e:
            logger.error(f"Error al obtener modelo con versiones: {str(e)}")
            return None


class VersionModeloRepository(BaseRepository[VersionModelo]):
    """Repositorio especifico para VersionModelo."""

    def __init__(self, db: Session):
        super().__init__(VersionModelo, db)

    def get_by_modelo(self, id_modelo: int) -> List[VersionModelo]:
        """
        Obtiene versiones de un modelo.

        Args:
            id_modelo: ID del modelo

        Returns:
            List[VersionModelo]: Lista de versiones
        """
        try:
            return self.db.query(VersionModelo).filter(
                VersionModelo.idModelo == id_modelo
            ).order_by(desc(VersionModelo.fechaEntrenamiento)).all()
        except Exception as e:
            logger.error(f"Error al obtener versiones del modelo: {str(e)}")
            return []

    def get_ultima_version(self, id_modelo: int) -> Optional[VersionModelo]:
        """
        Obtiene la ultima version activa de un modelo.

        Args:
            id_modelo: ID del modelo

        Returns:
            Optional[VersionModelo]: Ultima version o None
        """
        try:
            return self.db.query(VersionModelo).filter(
                VersionModelo.idModelo == id_modelo,
                VersionModelo.estado == 'Activo'
            ).order_by(desc(VersionModelo.fechaEntrenamiento)).first()
        except Exception as e:
            logger.error(f"Error al obtener ultima version: {str(e)}")
            return None

    def get_activas(self) -> List[VersionModelo]:
        """
        Obtiene todas las versiones activas.

        Returns:
            List[VersionModelo]: Lista de versiones activas
        """
        try:
            return self.db.query(VersionModelo).filter(
                VersionModelo.estado == 'Activo'
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener versiones activas: {str(e)}")
            return []

    def get_by_precision_minima(self, precision_minima: float) -> List[VersionModelo]:
        """
        Obtiene versiones con precision mayor o igual a la especificada.

        Args:
            precision_minima: Precision minima (e.g., 0.7 para R2 > 0.7)

        Returns:
            List[VersionModelo]: Lista de versiones que cumplen el umbral
        """
        try:
            return self.db.query(VersionModelo).filter(
                VersionModelo.precision >= precision_minima,
                VersionModelo.estado == 'Activo'
            ).all()
        except Exception as e:
            logger.error(f"Error al filtrar por precision: {str(e)}")
            return []

    def desactivar_versiones_anteriores(self, id_modelo: int, id_version_actual: int) -> int:
        """
        Desactiva versiones anteriores de un modelo.

        Args:
            id_modelo: ID del modelo
            id_version_actual: ID de la version a mantener activa

        Returns:
            int: Numero de versiones desactivadas
        """
        try:
            result = self.db.query(VersionModelo).filter(
                VersionModelo.idModelo == id_modelo,
                VersionModelo.idVersion != id_version_actual,
                VersionModelo.estado == 'Activo'
            ).update({'estado': 'Inactivo'})
            self.db.commit()
            return result
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al desactivar versiones anteriores: {str(e)}")
            return 0
