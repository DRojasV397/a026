"""
Repositorio para modelo de Alerta.
"""

from typing import Optional, List
from sqlalchemy.orm import Session
from sqlalchemy import desc, func, case
from datetime import datetime
import logging

from app.models import Alerta
from .base_repository import BaseRepository

logger = logging.getLogger(__name__)


class AlertaRepository(BaseRepository[Alerta]):
    """Repositorio especifico para Alerta."""

    def __init__(self, db: Session):
        super().__init__(Alerta, db)

    def get_activas(self, limite: int = 10) -> List[Alerta]:
        """
        Obtiene alertas activas (maximo 10 segun RN-04.05).

        Args:
            limite: Numero maximo de alertas

        Returns:
            List[Alerta]: Lista de alertas activas
        """
        try:
            # Usar CASE para ordenar por importancia (Alta=1, Media=2, Baja=3)
            importancia_order = case(
                (Alerta.importancia == 'Alta', 1),
                (Alerta.importancia == 'Media', 2),
                else_=3
            )
            return self.db.query(Alerta).filter(
                Alerta.estado == 'Activa'
            ).order_by(
                importancia_order,
                desc(Alerta.creadaEn)
            ).limit(limite).all()
        except Exception as e:
            logger.error(f"Error al obtener alertas activas: {str(e)}")
            return []

    def get_by_tipo(self, tipo_alerta: str) -> List[Alerta]:
        """
        Obtiene alertas por tipo.

        Args:
            tipo_alerta: Tipo de alerta (Riesgo, Oportunidad, Anomalia)

        Returns:
            List[Alerta]: Lista de alertas
        """
        try:
            return self.db.query(Alerta).filter(
                Alerta.tipo == tipo_alerta
            ).order_by(desc(Alerta.creadaEn)).all()
        except Exception as e:
            logger.error(f"Error al obtener alertas por tipo: {str(e)}")
            return []

    def get_by_importancia(self, importancia: str) -> List[Alerta]:
        """
        Obtiene alertas por nivel de importancia.

        Args:
            importancia: Nivel de importancia (Alta, Media, Baja)

        Returns:
            List[Alerta]: Lista de alertas
        """
        try:
            return self.db.query(Alerta).filter(
                Alerta.importancia == importancia,
                Alerta.estado == 'Activa'
            ).order_by(desc(Alerta.creadaEn)).all()
        except Exception as e:
            logger.error(f"Error al obtener alertas por importancia: {str(e)}")
            return []

    def get_by_prediccion(self, id_pred: int) -> List[Alerta]:
        """
        Obtiene alertas asociadas a una prediccion.

        Args:
            id_pred: ID de la prediccion

        Returns:
            List[Alerta]: Lista de alertas
        """
        try:
            return self.db.query(Alerta).filter(
                Alerta.idPred == id_pred
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener alertas por prediccion: {str(e)}")
            return []

    def get_historial(
        self, fecha_inicio: datetime, fecha_fin: datetime
    ) -> List[Alerta]:
        """
        Obtiene historial de alertas en un rango de fechas.

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final

        Returns:
            List[Alerta]: Lista de alertas
        """
        try:
            return self.db.query(Alerta).filter(
                Alerta.creadaEn >= fecha_inicio,
                Alerta.creadaEn <= fecha_fin
            ).order_by(desc(Alerta.creadaEn)).all()
        except Exception as e:
            logger.error(f"Error al obtener historial de alertas: {str(e)}")
            return []

    def marcar_como_leida(self, id_alerta: int) -> bool:
        """
        Marca una alerta como leida.

        Args:
            id_alerta: ID de la alerta

        Returns:
            bool: True si se actualizo exitosamente
        """
        try:
            alerta = self.get_by_id(id_alerta)
            if alerta:
                alerta.estado = 'Leida'
                self.db.commit()
                return True
            return False
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al marcar alerta como leida: {str(e)}")
            return False

    def resolver_alerta(self, id_alerta: int) -> bool:
        """
        Marca una alerta como resuelta.

        Args:
            id_alerta: ID de la alerta

        Returns:
            bool: True si se actualizo exitosamente
        """
        try:
            alerta = self.get_by_id(id_alerta)
            if alerta:
                alerta.estado = 'Resuelta'
                self.db.commit()
                return True
            return False
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al resolver alerta: {str(e)}")
            return False

    def cambiar_estado(self, id_alerta: int, nuevo_estado: str) -> bool:
        """
        Cambia el estado de una alerta.

        Args:
            id_alerta: ID de la alerta
            nuevo_estado: Nuevo estado

        Returns:
            bool: True si se actualizo exitosamente
        """
        try:
            alerta = self.get_by_id(id_alerta)
            if alerta:
                alerta.estado = nuevo_estado
                self.db.commit()
                return True
            return False
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al cambiar estado de alerta: {str(e)}")
            return False

    def contar_por_estado(self) -> dict:
        """
        Cuenta alertas agrupadas por estado.

        Returns:
            dict: Conteo por estado
        """
        try:
            result = self.db.query(
                Alerta.estado,
                func.count(Alerta.idAlerta)
            ).group_by(Alerta.estado).all()

            return {estado: count for estado, count in result}
        except Exception as e:
            logger.error(f"Error al contar alertas por estado: {str(e)}")
            return {}

    def contar_por_importancia(self) -> dict:
        """
        Cuenta alertas activas agrupadas por importancia.

        Returns:
            dict: Conteo por importancia
        """
        try:
            result = self.db.query(
                Alerta.importancia,
                func.count(Alerta.idAlerta)
            ).filter(
                Alerta.estado == 'Activa'
            ).group_by(Alerta.importancia).all()

            return {importancia: count for importancia, count in result}
        except Exception as e:
            logger.error(f"Error al contar alertas por importancia: {str(e)}")
            return {}

    def contar_por_tipo(self) -> dict:
        """
        Cuenta alertas agrupadas por tipo.

        Returns:
            dict: Conteo por tipo
        """
        try:
            result = self.db.query(
                Alerta.tipo,
                func.count(Alerta.idAlerta)
            ).filter(
                Alerta.estado == 'Activa'
            ).group_by(Alerta.tipo).all()

            return {tipo: count for tipo, count in result}
        except Exception as e:
            logger.error(f"Error al contar alertas por tipo: {str(e)}")
            return {}

    def get_resumen(self) -> dict:
        """
        Obtiene resumen de alertas activas.

        Returns:
            dict: Resumen de alertas
        """
        try:
            total_activas = self.db.query(func.count(Alerta.idAlerta)).filter(
                Alerta.estado == 'Activa'
            ).scalar() or 0

            return {
                'totalActivas': total_activas,
                'porTipo': self.contar_por_tipo(),
                'porImportancia': self.contar_por_importancia()
            }
        except Exception as e:
            logger.error(f"Error al obtener resumen de alertas: {str(e)}")
            return {
                'totalActivas': 0,
                'porTipo': {},
                'porImportancia': {}
            }
