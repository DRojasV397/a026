"""
Repositorio para modelos de Producto y Categoría.
"""

from typing import Optional, List
from sqlalchemy import or_
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

    def get_by_sku_y_usuario(self, sku: str, user_id: int) -> Optional[Producto]:
        """
        Obtiene un producto por SKU perteneciente a un usuario específico.

        Args:
            sku: SKU del producto
            user_id: ID del usuario propietario

        Returns:
            Optional[Producto]: Producto encontrado o None
        """
        try:
            return self.db.query(Producto).filter(
                Producto.sku == sku,
                Producto.creadoPor == user_id
            ).first()
        except Exception as e:
            logger.error(f"Error al buscar producto por SKU y usuario: {str(e)}")
            return None

    def get_por_usuario(self, user_id: int, skip: int = 0, limit: int = 100) -> List[Producto]:
        """
        Obtiene todos los productos de un usuario con paginación.
        Incluye productos sin propietario asignado (creadoPor IS NULL), que son
        productos del catálogo compartido o cargados antes de implementar el campo.

        Args:
            user_id: ID del usuario
            skip: Registros a saltar
            limit: Máximo de registros

        Returns:
            List[Producto]: Lista de productos del usuario
        """
        try:
            return self.db.query(Producto).filter(
                or_(Producto.creadoPor == user_id, Producto.creadoPor.is_(None))
            ).order_by(Producto.idProducto).offset(skip).limit(limit).all()
        except Exception as e:
            logger.error(f"Error al obtener productos por usuario: {str(e)}")
            return []

    def get_by_nombre_y_usuario(self, nombre: str, user_id: int) -> Optional[Producto]:
        """
        Busca un producto por nombre (case-insensitive) para un usuario.
        Incluye productos sin propietario asignado (legacy/seed).

        Args:
            nombre: Nombre del producto
            user_id: ID del usuario

        Returns:
            Optional[Producto]: Producto encontrado o None
        """
        try:
            return self.db.query(Producto).filter(
                Producto.nombre.ilike(nombre),
                or_(Producto.creadoPor == user_id, Producto.creadoPor.is_(None))
            ).first()
        except Exception as e:
            logger.error(f"Error al buscar producto por nombre y usuario: {str(e)}")
            return None

    def get_activos_por_usuario(self, user_id: int) -> List[Producto]:
        """
        Obtiene los productos activos de un usuario.
        Incluye productos sin propietario asignado (creadoPor IS NULL).

        Args:
            user_id: ID del usuario

        Returns:
            List[Producto]: Lista de productos activos del usuario
        """
        try:
            return self.db.query(Producto).filter(
                Producto.activo == 1,
                or_(Producto.creadoPor == user_id, Producto.creadoPor.is_(None))
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener productos activos por usuario: {str(e)}")
            return []

    def get_por_categoria_y_usuario(self, id_categoria: int, user_id: int) -> List[Producto]:
        """
        Obtiene productos de una categoría para un usuario.
        Incluye productos sin propietario asignado (creadoPor IS NULL).

        Args:
            id_categoria: ID de la categoría
            user_id: ID del usuario

        Returns:
            List[Producto]: Lista de productos
        """
        try:
            return self.db.query(Producto).filter(
                Producto.idCategoria == id_categoria,
                or_(Producto.creadoPor == user_id, Producto.creadoPor.is_(None))
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener productos por categoría y usuario: {str(e)}")
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
