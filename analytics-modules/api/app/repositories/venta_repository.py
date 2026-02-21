"""
Repositorio para modelos de Venta y DetalleVenta.
"""

from typing import Optional, List
from sqlalchemy.orm import Session
from sqlalchemy import func, or_
from datetime import date
from decimal import Decimal
import logging

from app.models import Venta, DetalleVenta
from .base_repository import BaseRepository

logger = logging.getLogger(__name__)


class VentaRepository(BaseRepository[Venta]):
    """Repositorio especifico para Venta."""

    def __init__(self, db: Session):
        super().__init__(Venta, db)

    def get_by_fecha(self, fecha: date) -> List[Venta]:
        """
        Obtiene ventas por fecha.

        Args:
            fecha: Fecha de las ventas

        Returns:
            List[Venta]: Lista de ventas
        """
        try:
            return self.db.query(Venta).filter(Venta.fecha == fecha).all()
        except Exception as e:
            logger.error(f"Error al buscar ventas por fecha: {str(e)}")
            return []

    def get_by_rango_fechas(
        self,
        fecha_inicio: date,
        fecha_fin: date,
        user_id: Optional[int] = None
    ) -> List[Venta]:
        """
        Obtiene ventas en un rango de fechas, opcionalmente filtradas por usuario.

        Incluye ventas con creadoPor=NULL (datos legacy/seed compartidos).

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final
            user_id: ID del usuario; si se provee, filtra por creadoPor

        Returns:
            List[Venta]: Lista de ventas
        """
        try:
            query = self.db.query(Venta).filter(
                Venta.fecha >= fecha_inicio,
                Venta.fecha <= fecha_fin
            )
            if user_id is not None:
                query = query.filter(
                    or_(Venta.creadoPor == user_id, Venta.creadoPor.is_(None))
                )
            return query.order_by(Venta.fecha.desc()).all()
        except Exception as e:
            logger.error(f"Error al buscar ventas por rango: {str(e)}")
            return []

    def get_by_usuario(self, id_usuario: int) -> List[Venta]:
        """
        Obtiene ventas creadas por un usuario.

        Args:
            id_usuario: ID del usuario

        Returns:
            List[Venta]: Lista de ventas
        """
        try:
            return self.db.query(Venta).filter(Venta.creadoPor == id_usuario).all()
        except Exception as e:
            logger.error(f"Error al buscar ventas por usuario: {str(e)}")
            return []

    def get_total_por_periodo(self, fecha_inicio: date, fecha_fin: date) -> Decimal:
        """
        Obtiene el total de ventas en un periodo.

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final

        Returns:
            Decimal: Total de ventas
        """
        try:
            result = self.db.query(func.sum(Venta.total)).filter(
                Venta.fecha >= fecha_inicio,
                Venta.fecha <= fecha_fin
            ).scalar()
            return result or Decimal('0')
        except Exception as e:
            logger.error(f"Error al calcular total de ventas: {str(e)}")
            return Decimal('0')

    def get_resumen_mensual(self, anio: int, mes: int) -> dict:
        """
        Obtiene resumen de ventas de un mes.

        Args:
            anio: Ano
            mes: Mes

        Returns:
            dict: Resumen con total, cantidad y promedio
        """
        try:
            result = self.db.query(
                func.count(Venta.idVenta).label('cantidad'),
                func.sum(Venta.total).label('total'),
                func.avg(Venta.total).label('promedio')
            ).filter(
                func.year(Venta.fecha) == anio,
                func.month(Venta.fecha) == mes
            ).first()

            return {
                'cantidad': result.cantidad or 0,
                'total': result.total or Decimal('0'),
                'promedio': result.promedio or Decimal('0')
            }
        except Exception as e:
            logger.error(f"Error al obtener resumen mensual: {str(e)}")
            return {'cantidad': 0, 'total': Decimal('0'), 'promedio': Decimal('0')}

    def get_with_detalles(self, id_venta: int) -> Optional[Venta]:
        """
        Obtiene una venta con sus detalles.

        Args:
            id_venta: ID de la venta

        Returns:
            Optional[Venta]: Venta con detalles cargados
        """
        try:
            return self.db.query(Venta).filter(
                Venta.idVenta == id_venta
            ).first()
        except Exception as e:
            logger.error(f"Error al obtener venta con detalles: {str(e)}")
            return None


class DetalleVentaRepository(BaseRepository[DetalleVenta]):
    """Repositorio especifico para DetalleVenta."""

    def __init__(self, db: Session):
        super().__init__(DetalleVenta, db)

    def get_by_venta(self, id_venta: int) -> List[DetalleVenta]:
        """
        Obtiene detalles de una venta.

        Args:
            id_venta: ID de la venta

        Returns:
            List[DetalleVenta]: Lista de detalles
        """
        try:
            return self.db.query(DetalleVenta).filter(
                DetalleVenta.idVenta == id_venta
            ).order_by(DetalleVenta.renglon).all()
        except Exception as e:
            logger.error(f"Error al obtener detalles de venta: {str(e)}")
            return []

    def get_by_producto(self, id_producto: int) -> List[DetalleVenta]:
        """
        Obtiene todos los detalles de venta de un producto.

        Args:
            id_producto: ID del producto

        Returns:
            List[DetalleVenta]: Lista de detalles
        """
        try:
            return self.db.query(DetalleVenta).filter(
                DetalleVenta.idProducto == id_producto
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener detalles por producto: {str(e)}")
            return []

    def get_ventas_producto_periodo(
        self, id_producto: int, fecha_inicio: date, fecha_fin: date
    ) -> List[DetalleVenta]:
        """
        Obtiene detalles de venta de un producto en un periodo.

        Args:
            id_producto: ID del producto
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final

        Returns:
            List[DetalleVenta]: Lista de detalles
        """
        try:
            return self.db.query(DetalleVenta).join(Venta).filter(
                DetalleVenta.idProducto == id_producto,
                Venta.fecha >= fecha_inicio,
                Venta.fecha <= fecha_fin
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener ventas de producto por periodo: {str(e)}")
            return []

    def get_total_vendido_producto(
        self, id_producto: int, fecha_inicio: date, fecha_fin: date
    ) -> dict:
        """
        Obtiene el total vendido de un producto en un periodo.

        Args:
            id_producto: ID del producto
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final

        Returns:
            dict: Cantidad total y monto total
        """
        try:
            result = self.db.query(
                func.sum(DetalleVenta.cantidad).label('cantidad'),
                func.sum(DetalleVenta.subtotal).label('total')
            ).join(Venta).filter(
                DetalleVenta.idProducto == id_producto,
                Venta.fecha >= fecha_inicio,
                Venta.fecha <= fecha_fin
            ).first()

            return {
                'cantidad': result.cantidad or Decimal('0'),
                'total': result.total or Decimal('0')
            }
        except Exception as e:
            logger.error(f"Error al calcular total vendido: {str(e)}")
            return {'cantidad': Decimal('0'), 'total': Decimal('0')}
