"""
Repositorio para modelos de Producto y Categoría.
"""

from typing import Optional, List
from sqlalchemy.orm import Session
import logging

from app.models import Producto, Categoria
from .base_repository import BaseRepository

logger = logging.getLogger(__name__)


class ProductoRepository(BaseRepository[Producto]):
    """Repositorio específico para Producto."""

    def __init__(self, db: Session):
        super().__init__(Producto, db)

    def get_by_sku(self, sku: str) -> Optional[Producto]:
        """
        Obtiene un producto por su SKU.

        Args:
            sku: SKU del producto

        Returns:
            Optional[Producto]: Producto encontrado o None
        """
        try:
            return self.db.query(Producto).filter(Producto.sku == sku).first()
        except Exception as e:
            logger.error(f"Error al buscar producto por SKU: {str(e)}")
            return None

    def get_by_categoria(self, id_categoria: int) -> List[Producto]:
        """
        Obtiene todos los productos de una categoría.

        Args:
            id_categoria: ID de la categoría

        Returns:
            List[Producto]: Lista de productos
        """
        try:
            return self.db.query(Producto).filter(
                Producto.idCategoria == id_categoria
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener productos por categoría: {str(e)}")
            return []

    def get_activos(self) -> List[Producto]:
        """
        Obtiene todos los productos activos.

        Returns:
            List[Producto]: Lista de productos activos
        """
        try:
            return self.db.query(Producto).filter(Producto.activo == 1).all()
        except Exception as e:
            logger.error(f"Error al obtener productos activos: {str(e)}")
            return []


class CategoriaRepository(BaseRepository[Categoria]):
    """Repositorio específico para Categoría."""

    def __init__(self, db: Session):
        super().__init__(Categoria, db)

    def get_by_nombre(self, nombre: str) -> Optional[Categoria]:
        """
        Obtiene una categoría por su nombre.

        Args:
            nombre: Nombre de la categoría

        Returns:
            Optional[Categoria]: Categoría encontrada o None
        """
        try:
            return self.db.query(Categoria).filter(Categoria.nombre == nombre).first()
        except Exception as e:
            logger.error(f"Error al buscar categoría por nombre: {str(e)}")
            return None

    def get_activas(self) -> List[Categoria]:
        """
        Obtiene todas las categorías activas.

        Returns:
            List[Categoria]: Lista de categorías activas
        """
        try:
            return self.db.query(Categoria).filter(Categoria.activo == 1).all()
        except Exception as e:
            logger.error(f"Error al obtener categorías activas: {str(e)}")
            return []
