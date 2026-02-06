"""
Repositorio para modelos de Compra y DetalleCompra.
"""

from typing import Optional, List
from sqlalchemy.orm import Session
from sqlalchemy import func
from datetime import date
from decimal import Decimal
import logging

from app.models import Compra, DetalleCompra
from .base_repository import BaseRepository

logger = logging.getLogger(__name__)


class CompraRepository(BaseRepository[Compra]):
    """Repositorio especifico para Compra."""

    def __init__(self, db: Session):
        super().__init__(Compra, db)

    def get_by_fecha(self, fecha: date) -> List[Compra]:
        """
        Obtiene compras por fecha.

        Args:
            fecha: Fecha de las compras

        Returns:
            List[Compra]: Lista de compras
        """
        try:
            return self.db.query(Compra).filter(Compra.fecha == fecha).all()
        except Exception as e:
            logger.error(f"Error al buscar compras por fecha: {str(e)}")
            return []

    def get_by_rango_fechas(self, fecha_inicio: date, fecha_fin: date) -> List[Compra]:
        """
        Obtiene compras en un rango de fechas.

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final

        Returns:
            List[Compra]: Lista de compras
        """
        try:
            return self.db.query(Compra).filter(
                Compra.fecha >= fecha_inicio,
                Compra.fecha <= fecha_fin
            ).order_by(Compra.fecha.desc()).all()
        except Exception as e:
            logger.error(f"Error al buscar compras por rango: {str(e)}")
            return []

    def get_by_proveedor(self, proveedor: str) -> List[Compra]:
        """
        Obtiene compras por proveedor.

        Args:
            proveedor: Nombre del proveedor

        Returns:
            List[Compra]: Lista de compras
        """
        try:
            return self.db.query(Compra).filter(
                Compra.proveedor.ilike(f"%{proveedor}%")
            ).all()
        except Exception as e:
            logger.error(f"Error al buscar compras por proveedor: {str(e)}")
            return []

    def get_by_usuario(self, id_usuario: int) -> List[Compra]:
        """
        Obtiene compras creadas por un usuario.

        Args:
            id_usuario: ID del usuario

        Returns:
            List[Compra]: Lista de compras
        """
        try:
            return self.db.query(Compra).filter(Compra.creadoPor == id_usuario).all()
        except Exception as e:
            logger.error(f"Error al buscar compras por usuario: {str(e)}")
            return []

    def get_total_por_periodo(self, fecha_inicio: date, fecha_fin: date) -> Decimal:
        """
        Obtiene el total de compras en un periodo.

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final

        Returns:
            Decimal: Total de compras
        """
        try:
            result = self.db.query(func.sum(Compra.total)).filter(
                Compra.fecha >= fecha_inicio,
                Compra.fecha <= fecha_fin
            ).scalar()
            return result or Decimal('0')
        except Exception as e:
            logger.error(f"Error al calcular total de compras: {str(e)}")
            return Decimal('0')

    def get_resumen_mensual(self, anio: int, mes: int) -> dict:
        """
        Obtiene resumen de compras de un mes.

        Args:
            anio: Ano
            mes: Mes

        Returns:
            dict: Resumen con total, cantidad y promedio
        """
        try:
            result = self.db.query(
                func.count(Compra.idCompra).label('cantidad'),
                func.sum(Compra.total).label('total'),
                func.avg(Compra.total).label('promedio')
            ).filter(
                func.year(Compra.fecha) == anio,
                func.month(Compra.fecha) == mes
            ).first()

            return {
                'cantidad': result.cantidad or 0,
                'total': result.total or Decimal('0'),
                'promedio': result.promedio or Decimal('0')
            }
        except Exception as e:
            logger.error(f"Error al obtener resumen mensual: {str(e)}")
            return {'cantidad': 0, 'total': Decimal('0'), 'promedio': Decimal('0')}

    def get_with_detalles(self, id_compra: int) -> Optional[Compra]:
        """
        Obtiene una compra con sus detalles.

        Args:
            id_compra: ID de la compra

        Returns:
            Optional[Compra]: Compra con detalles cargados
        """
        try:
            return self.db.query(Compra).filter(
                Compra.idCompra == id_compra
            ).first()
        except Exception as e:
            logger.error(f"Error al obtener compra con detalles: {str(e)}")
            return None


class DetalleCompraRepository(BaseRepository[DetalleCompra]):
    """Repositorio especifico para DetalleCompra."""

    def __init__(self, db: Session):
        super().__init__(DetalleCompra, db)

    def get_by_compra(self, id_compra: int) -> List[DetalleCompra]:
        """
        Obtiene detalles de una compra.

        Args:
            id_compra: ID de la compra

        Returns:
            List[DetalleCompra]: Lista de detalles
        """
        try:
            return self.db.query(DetalleCompra).filter(
                DetalleCompra.idCompra == id_compra
            ).order_by(DetalleCompra.renglon).all()
        except Exception as e:
            logger.error(f"Error al obtener detalles de compra: {str(e)}")
            return []

    def get_by_producto(self, id_producto: int) -> List[DetalleCompra]:
        """
        Obtiene todos los detalles de compra de un producto.

        Args:
            id_producto: ID del producto

        Returns:
            List[DetalleCompra]: Lista de detalles
        """
        try:
            return self.db.query(DetalleCompra).filter(
                DetalleCompra.idProducto == id_producto
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener detalles por producto: {str(e)}")
            return []

    def get_costo_promedio_producto(
        self, id_producto: int, fecha_inicio: date, fecha_fin: date
    ) -> Decimal:
        """
        Obtiene el costo promedio de un producto en un periodo.

        Args:
            id_producto: ID del producto
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final

        Returns:
            Decimal: Costo promedio
        """
        try:
            result = self.db.query(
                func.avg(DetalleCompra.costo)
            ).join(Compra).filter(
                DetalleCompra.idProducto == id_producto,
                Compra.fecha >= fecha_inicio,
                Compra.fecha <= fecha_fin
            ).scalar()
            return result or Decimal('0')
        except Exception as e:
            logger.error(f"Error al calcular costo promedio: {str(e)}")
            return Decimal('0')

    def get_total_comprado_producto(
        self, id_producto: int, fecha_inicio: date, fecha_fin: date
    ) -> dict:
        """
        Obtiene el total comprado de un producto en un periodo.

        Args:
            id_producto: ID del producto
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final

        Returns:
            dict: Cantidad total y monto total
        """
        try:
            result = self.db.query(
                func.sum(DetalleCompra.cantidad).label('cantidad'),
                func.sum(DetalleCompra.subtotal).label('total')
            ).join(Compra).filter(
                DetalleCompra.idProducto == id_producto,
                Compra.fecha >= fecha_inicio,
                Compra.fecha <= fecha_fin
            ).first()

            return {
                'cantidad': result.cantidad or Decimal('0'),
                'total': result.total or Decimal('0')
            }
        except Exception as e:
            logger.error(f"Error al calcular total comprado: {str(e)}")
            return {'cantidad': Decimal('0'), 'total': Decimal('0')}
