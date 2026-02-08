"""
Repositorio para modelos de Escenario, ParametroEscenario y ResultadoEscenario.
"""

from typing import Optional, List
from sqlalchemy.orm import Session
from sqlalchemy import desc
from datetime import datetime
import logging

from app.models import Escenario, ParametroEscenario, ResultadoEscenario
from .base_repository import BaseRepository

logger = logging.getLogger(__name__)


class EscenarioRepository(BaseRepository[Escenario]):
    """Repositorio especifico para Escenario."""

    def __init__(self, db: Session):
        super().__init__(Escenario, db)

    def get_by_usuario(self, id_usuario: int) -> List[Escenario]:
        """
        Obtiene escenarios creados por un usuario.

        Args:
            id_usuario: ID del usuario

        Returns:
            List[Escenario]: Lista de escenarios
        """
        try:
            return self.db.query(Escenario).filter(
                Escenario.creadoPor == id_usuario
            ).order_by(desc(Escenario.creadoEn)).all()
        except Exception as e:
            logger.error(f"Error al obtener escenarios por usuario: {str(e)}")
            return []

    def get_by_nombre(self, nombre: str) -> Optional[Escenario]:
        """
        Obtiene un escenario por nombre.

        Args:
            nombre: Nombre del escenario

        Returns:
            Optional[Escenario]: Escenario encontrado o None
        """
        try:
            return self.db.query(Escenario).filter(
                Escenario.nombre == nombre
            ).first()
        except Exception as e:
            logger.error(f"Error al buscar escenario por nombre: {str(e)}")
            return None

    def get_activos(self) -> List[Escenario]:
        """
        Obtiene todos los escenarios (no hay columna estado en BD).

        Returns:
            List[Escenario]: Lista de escenarios
        """
        try:
            return self.db.query(Escenario).order_by(desc(Escenario.creadoEn)).all()
        except Exception as e:
            logger.error(f"Error al obtener escenarios: {str(e)}")
            return []

    def get_by_version(self, id_version: int) -> List[Escenario]:
        """
        Obtiene escenarios basados en una version de modelo.

        Args:
            id_version: ID de la version del modelo

        Returns:
            List[Escenario]: Lista de escenarios
        """
        try:
            return self.db.query(Escenario).filter(
                Escenario.baseVersion == id_version
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener escenarios por version: {str(e)}")
            return []

    def get_with_parametros(self, id_escenario: int) -> Optional[Escenario]:
        """
        Obtiene un escenario con sus parametros.

        Args:
            id_escenario: ID del escenario

        Returns:
            Optional[Escenario]: Escenario con parametros cargados
        """
        try:
            return self.db.query(Escenario).filter(
                Escenario.idEscenario == id_escenario
            ).first()
        except Exception as e:
            logger.error(f"Error al obtener escenario con parametros: {str(e)}")
            return None

    def get_escenarios_comparables(self, limite: int = 5) -> List[Escenario]:
        """
        Obtiene escenarios para comparacion (maximo 5).

        Args:
            limite: Numero maximo de escenarios

        Returns:
            List[Escenario]: Lista de escenarios
        """
        try:
            return self.db.query(Escenario).order_by(
                desc(Escenario.creadoEn)
            ).limit(limite).all()
        except Exception as e:
            logger.error(f"Error al obtener escenarios comparables: {str(e)}")
            return []

    def archivar_escenario(self, id_escenario: int) -> bool:
        """
        Archiva (elimina) un escenario.
        Nota: La BD no tiene columna estado, se elimina el registro.

        Args:
            id_escenario: ID del escenario

        Returns:
            bool: True si se archivo/elimino exitosamente
        """
        try:
            escenario = self.get_by_id(id_escenario)
            if escenario:
                self.db.delete(escenario)
                self.db.commit()
                return True
            return False
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al archivar escenario: {str(e)}")
            return False


class ParametroEscenarioRepository(BaseRepository[ParametroEscenario]):
    """Repositorio especifico para ParametroEscenario."""

    def __init__(self, db: Session):
        super().__init__(ParametroEscenario, db)

    def get_by_escenario(self, id_escenario: int) -> List[ParametroEscenario]:
        """
        Obtiene parametros de un escenario.

        Args:
            id_escenario: ID del escenario

        Returns:
            List[ParametroEscenario]: Lista de parametros
        """
        try:
            return self.db.query(ParametroEscenario).filter(
                ParametroEscenario.idEscenario == id_escenario
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener parametros del escenario: {str(e)}")
            return []

    def get_parametro(self, id_escenario: int, parametro: str) -> Optional[ParametroEscenario]:
        """
        Obtiene un parametro especifico de un escenario.

        Args:
            id_escenario: ID del escenario
            parametro: Nombre del parametro

        Returns:
            Optional[ParametroEscenario]: Parametro encontrado o None
        """
        try:
            return self.db.query(ParametroEscenario).filter(
                ParametroEscenario.idEscenario == id_escenario,
                ParametroEscenario.parametro == parametro
            ).first()
        except Exception as e:
            logger.error(f"Error al obtener parametro: {str(e)}")
            return None

    def actualizar_parametro(
        self, id_escenario: int, parametro: str, valor_actual: float, valor_base: float = None
    ) -> bool:
        """
        Actualiza o crea un parametro de escenario.

        Args:
            id_escenario: ID del escenario
            parametro: Nombre del parametro
            valor_actual: Valor actual/modificado
            valor_base: Valor base (opcional, si no se da usa valor_actual)

        Returns:
            bool: True si se actualizo exitosamente
        """
        try:
            from decimal import Decimal
            param = self.get_parametro(id_escenario, parametro)
            if param:
                param.valorActual = Decimal(str(valor_actual)) if valor_actual is not None else None
                if valor_base is not None:
                    param.valorBase = Decimal(str(valor_base))
            else:
                param = ParametroEscenario(
                    idEscenario=id_escenario,
                    parametro=parametro,
                    valorBase=Decimal(str(valor_base)) if valor_base is not None else Decimal(str(valor_actual)),
                    valorActual=Decimal(str(valor_actual)) if valor_actual is not None else None
                )
                self.db.add(param)
            self.db.commit()
            return True
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al actualizar parametro: {str(e)}")
            return False

    def eliminar_parametros_escenario(self, id_escenario: int) -> int:
        """
        Elimina todos los parametros de un escenario.

        Args:
            id_escenario: ID del escenario

        Returns:
            int: Numero de parametros eliminados
        """
        try:
            result = self.db.query(ParametroEscenario).filter(
                ParametroEscenario.idEscenario == id_escenario
            ).delete()
            self.db.commit()
            return result
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al eliminar parametros: {str(e)}")
            return 0


class ResultadoEscenarioRepository(BaseRepository[ResultadoEscenario]):
    """Repositorio especifico para ResultadoEscenario."""

    def __init__(self, db: Session):
        super().__init__(ResultadoEscenario, db)

    def get_by_escenario(self, id_escenario: int) -> List[ResultadoEscenario]:
        """
        Obtiene resultados de un escenario.

        Args:
            id_escenario: ID del escenario

        Returns:
            List[ResultadoEscenario]: Lista de resultados
        """
        try:
            return self.db.query(ResultadoEscenario).filter(
                ResultadoEscenario.idEscenario == id_escenario
            ).order_by(ResultadoEscenario.periodo).all()
        except Exception as e:
            logger.error(f"Error al obtener resultados del escenario: {str(e)}")
            return []

    def get_by_kpi(self, id_escenario: int, kpi: str) -> List[ResultadoEscenario]:
        """
        Obtiene resultados de un KPI especifico.

        Args:
            id_escenario: ID del escenario
            kpi: Nombre del KPI

        Returns:
            List[ResultadoEscenario]: Lista de resultados
        """
        try:
            return self.db.query(ResultadoEscenario).filter(
                ResultadoEscenario.idEscenario == id_escenario,
                ResultadoEscenario.kpi == kpi
            ).order_by(ResultadoEscenario.periodo).all()
        except Exception as e:
            logger.error(f"Error al obtener resultados por kpi: {str(e)}")
            return []

    def get_by_periodo(self, id_escenario: int, periodo: str) -> List[ResultadoEscenario]:
        """
        Obtiene resultados de un periodo especifico.

        Args:
            id_escenario: ID del escenario
            periodo: Periodo (YYYY-MM)

        Returns:
            List[ResultadoEscenario]: Lista de resultados
        """
        try:
            return self.db.query(ResultadoEscenario).filter(
                ResultadoEscenario.idEscenario == id_escenario,
                ResultadoEscenario.periodo == periodo
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener resultados por periodo: {str(e)}")
            return []

    def eliminar_resultados_escenario(self, id_escenario: int) -> int:
        """
        Elimina todos los resultados de un escenario.

        Args:
            id_escenario: ID del escenario

        Returns:
            int: Numero de resultados eliminados
        """
        try:
            result = self.db.query(ResultadoEscenario).filter(
                ResultadoEscenario.idEscenario == id_escenario
            ).delete()
            self.db.commit()
            return result
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al eliminar resultados: {str(e)}")
            return 0
