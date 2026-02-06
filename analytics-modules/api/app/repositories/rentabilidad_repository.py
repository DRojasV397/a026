"""
Repositorio para modelos de Rentabilidad y ResultadoFinanciero.
"""

from typing import Optional, List
from sqlalchemy.orm import Session
from sqlalchemy import desc, func
from datetime import datetime
from decimal import Decimal
import logging

from app.models import Rentabilidad, ResultadoFinanciero
from .base_repository import BaseRepository

logger = logging.getLogger(__name__)


class RentabilidadRepository(BaseRepository[Rentabilidad]):
    """Repositorio especifico para Rentabilidad."""

    def __init__(self, db: Session):
        super().__init__(Rentabilidad, db)

    def get_by_entidad(self, tipo_entidad: str, id_entidad: int) -> List[Rentabilidad]:
        """
        Obtiene registros de rentabilidad de una entidad.

        Args:
            tipo_entidad: Tipo de entidad (Producto, Categoria, General)
            id_entidad: ID de la entidad

        Returns:
            List[Rentabilidad]: Lista de registros
        """
        try:
            return self.db.query(Rentabilidad).filter(
                Rentabilidad.tipoEntidad == tipo_entidad,
                Rentabilidad.idEntidad == id_entidad
            ).order_by(desc(Rentabilidad.periodo)).all()
        except Exception as e:
            logger.error(f"Error al obtener rentabilidad por entidad: {str(e)}")
            return []

    def get_by_periodo(self, periodo: str) -> List[Rentabilidad]:
        """
        Obtiene registros de rentabilidad de un periodo.

        Args:
            periodo: Periodo (YYYY-MM, YYYY-QN, YYYY)

        Returns:
            List[Rentabilidad]: Lista de registros
        """
        try:
            return self.db.query(Rentabilidad).filter(
                Rentabilidad.periodo == periodo
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener rentabilidad por periodo: {str(e)}")
            return []

    def get_ultimo_registro(self, tipo_entidad: str, id_entidad: int) -> Optional[Rentabilidad]:
        """
        Obtiene el ultimo registro de rentabilidad de una entidad.

        Args:
            tipo_entidad: Tipo de entidad
            id_entidad: ID de la entidad

        Returns:
            Optional[Rentabilidad]: Ultimo registro o None
        """
        try:
            return self.db.query(Rentabilidad).filter(
                Rentabilidad.tipoEntidad == tipo_entidad,
                Rentabilidad.idEntidad == id_entidad
            ).order_by(desc(Rentabilidad.periodo)).first()
        except Exception as e:
            logger.error(f"Error al obtener ultimo registro: {str(e)}")
            return None

    def get_productos_no_rentables(self, periodo: str, margen_minimo: Decimal = Decimal('10')) -> List[Rentabilidad]:
        """
        Obtiene productos con margen menor al minimo (no rentables).

        Args:
            periodo: Periodo a consultar
            margen_minimo: Margen minimo en porcentaje (default 10%)

        Returns:
            List[Rentabilidad]: Lista de productos no rentables
        """
        try:
            return self.db.query(Rentabilidad).filter(
                Rentabilidad.tipoEntidad == 'Producto',
                Rentabilidad.periodo == periodo,
                Rentabilidad.margenNeto < margen_minimo
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener productos no rentables: {str(e)}")
            return []

    def get_tendencia(
        self, tipo_entidad: str, id_entidad: int, periodos: int = 6
    ) -> List[Rentabilidad]:
        """
        Obtiene tendencia de rentabilidad (ultimos N periodos).

        Args:
            tipo_entidad: Tipo de entidad
            id_entidad: ID de la entidad
            periodos: Numero de periodos a obtener

        Returns:
            List[Rentabilidad]: Lista ordenada por periodo
        """
        try:
            return self.db.query(Rentabilidad).filter(
                Rentabilidad.tipoEntidad == tipo_entidad,
                Rentabilidad.idEntidad == id_entidad
            ).order_by(desc(Rentabilidad.periodo)).limit(periodos).all()
        except Exception as e:
            logger.error(f"Error al obtener tendencia: {str(e)}")
            return []

    def get_ranking_productos(self, periodo: str, limite: int = 10) -> List[Rentabilidad]:
        """
        Obtiene ranking de productos por rentabilidad.

        Args:
            periodo: Periodo a consultar
            limite: Numero maximo de productos

        Returns:
            List[Rentabilidad]: Lista ordenada por margen descendente
        """
        try:
            return self.db.query(Rentabilidad).filter(
                Rentabilidad.tipoEntidad == 'Producto',
                Rentabilidad.periodo == periodo
            ).order_by(desc(Rentabilidad.margenNeto)).limit(limite).all()
        except Exception as e:
            logger.error(f"Error al obtener ranking de productos: {str(e)}")
            return []

    def calcular_totales_periodo(self, periodo: str) -> dict:
        """
        Calcula totales de rentabilidad de un periodo.

        Args:
            periodo: Periodo a consultar

        Returns:
            dict: Totales agregados
        """
        try:
            result = self.db.query(
                func.sum(Rentabilidad.ingresos).label('total_ingresos'),
                func.sum(Rentabilidad.costos).label('total_costos'),
                func.sum(Rentabilidad.gastos).label('total_gastos'),
                func.avg(Rentabilidad.margenNeto).label('margen_promedio')
            ).filter(
                Rentabilidad.periodo == periodo
            ).first()

            return {
                'total_ingresos': result.total_ingresos or Decimal('0'),
                'total_costos': result.total_costos or Decimal('0'),
                'total_gastos': result.total_gastos or Decimal('0'),
                'margen_promedio': float(result.margen_promedio or 0)
            }
        except Exception as e:
            logger.error(f"Error al calcular totales: {str(e)}")
            return {
                'total_ingresos': Decimal('0'),
                'total_costos': Decimal('0'),
                'total_gastos': Decimal('0'),
                'margen_promedio': 0
            }


class ResultadoFinancieroRepository(BaseRepository[ResultadoFinanciero]):
    """Repositorio especifico para ResultadoFinanciero."""

    def __init__(self, db: Session):
        super().__init__(ResultadoFinanciero, db)

    def get_by_periodo(self, periodo: str) -> List[ResultadoFinanciero]:
        """
        Obtiene resultados financieros de un periodo.

        Args:
            periodo: Periodo (YYYY-MM)

        Returns:
            List[ResultadoFinanciero]: Lista de resultados
        """
        try:
            return self.db.query(ResultadoFinanciero).filter(
                ResultadoFinanciero.periodo == periodo
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener resultados por periodo: {str(e)}")
            return []

    def get_by_indicador(self, indicador: str) -> List[ResultadoFinanciero]:
        """
        Obtiene historial de un indicador financiero.

        Args:
            indicador: Nombre del indicador

        Returns:
            List[ResultadoFinanciero]: Lista de resultados
        """
        try:
            return self.db.query(ResultadoFinanciero).filter(
                ResultadoFinanciero.indicador == indicador
            ).order_by(ResultadoFinanciero.periodo).all()
        except Exception as e:
            logger.error(f"Error al obtener historial de indicador: {str(e)}")
            return []

    def get_by_version(self, id_version: int) -> List[ResultadoFinanciero]:
        """
        Obtiene resultados financieros asociados a una version de modelo.

        Args:
            id_version: ID de la version del modelo

        Returns:
            List[ResultadoFinanciero]: Lista de resultados
        """
        try:
            return self.db.query(ResultadoFinanciero).filter(
                ResultadoFinanciero.idVersion == id_version
            ).order_by(ResultadoFinanciero.periodo).all()
        except Exception as e:
            logger.error(f"Error al obtener resultados por version: {str(e)}")
            return []

    def get_tendencias(self, indicador: str, periodos: int = 12) -> List[ResultadoFinanciero]:
        """
        Obtiene tendencias de un indicador.

        Args:
            indicador: Nombre del indicador
            periodos: Numero de periodos

        Returns:
            List[ResultadoFinanciero]: Lista ordenada por periodo
        """
        try:
            return self.db.query(ResultadoFinanciero).filter(
                ResultadoFinanciero.indicador == indicador
            ).order_by(desc(ResultadoFinanciero.periodo)).limit(periodos).all()
        except Exception as e:
            logger.error(f"Error al obtener tendencias: {str(e)}")
            return []

    def get_ultimo_valor(self, indicador: str) -> Optional[ResultadoFinanciero]:
        """
        Obtiene el ultimo valor de un indicador.

        Args:
            indicador: Nombre del indicador

        Returns:
            Optional[ResultadoFinanciero]: Ultimo resultado o None
        """
        try:
            return self.db.query(ResultadoFinanciero).filter(
                ResultadoFinanciero.indicador == indicador
            ).order_by(desc(ResultadoFinanciero.periodo)).first()
        except Exception as e:
            logger.error(f"Error al obtener ultimo valor: {str(e)}")
            return None
