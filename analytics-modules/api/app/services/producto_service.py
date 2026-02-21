"""
Servicio de lógica de negocio para Productos y Categorías.
"""

from typing import List, Optional
from sqlalchemy.orm import Session
import logging

from app.repositories import ProductoRepository, CategoriaRepository
from app.schemas import ProductoCreate, ProductoUpdate, CategoriaCreate, CategoriaUpdate
from app.models import Producto, Categoria

logger = logging.getLogger(__name__)


class ProductoService:
    """Servicio para gestión de productos."""

    def __init__(self, db: Session):
        self.db = db
        self.producto_repo = ProductoRepository(db)
        self.categoria_repo = CategoriaRepository(db)

    def create_producto(
        self, producto_data: ProductoCreate, user_id: int
    ) -> Optional[Producto]:
        """
        Crea un nuevo producto asociado al usuario.
        Si el SKU ya existe para ese usuario, lanza ValueError.

        Args:
            producto_data: Datos del producto
            user_id: ID del usuario propietario

        Returns:
            Optional[Producto]: Producto creado o None
        """
        # Validar que el SKU no exista ya para este usuario
        if producto_data.sku:
            existing = self.producto_repo.get_by_sku_y_usuario(producto_data.sku, user_id)
            if existing:
                raise ValueError(
                    f"El SKU '{producto_data.sku}' ya existe en tu catálogo. "
                    f"Usa la carga de archivo para actualizar sus valores."
                )

        # Validar que la categoría exista si se proporciona
        if producto_data.idCategoria:
            categoria = self.categoria_repo.get_by_id(producto_data.idCategoria)
            if not categoria:
                raise ValueError(f"La categoría {producto_data.idCategoria} no existe")

        data = producto_data.model_dump()
        data['creadoPor'] = user_id
        return self.producto_repo.create(data)

    def get_producto(self, producto_id: int, user_id: int) -> Optional[Producto]:
        """
        Obtiene un producto por ID, verificando que pertenezca al usuario.

        Args:
            producto_id: ID del producto
            user_id: ID del usuario

        Returns:
            Optional[Producto]: Producto si existe y pertenece al usuario, None si no
        """
        producto = self.producto_repo.get_by_id(producto_id)
        if producto and producto.creadoPor == user_id:
            return producto
        return None

    def get_producto_by_sku(self, sku: str, user_id: int) -> Optional[Producto]:
        """Obtiene un producto por SKU perteneciente al usuario."""
        return self.producto_repo.get_by_sku_y_usuario(sku, user_id)

    def get_productos(
        self,
        user_id: int,
        skip: int = 0,
        limit: int = 100,
        activos_only: bool = False,
        categoria_id: Optional[int] = None
    ) -> List[Producto]:
        """
        Obtiene los productos del usuario con filtros opcionales.

        Args:
            user_id: ID del usuario (solo verá sus propios productos)
            skip: Paginación
            limit: Máximo de resultados
            activos_only: Solo productos activos
            categoria_id: Filtrar por categoría
        """
        if categoria_id:
            return self.producto_repo.get_por_categoria_y_usuario(categoria_id, user_id)
        if activos_only:
            return self.producto_repo.get_activos_por_usuario(user_id)
        return self.producto_repo.get_por_usuario(user_id, skip=skip, limit=limit)

    def update_producto(
        self, producto_id: int, producto_data: ProductoUpdate, user_id: int
    ) -> Optional[Producto]:
        """Actualiza un producto verificando que pertenezca al usuario."""
        producto = self.get_producto(producto_id, user_id)
        if not producto:
            return None
        update_dict = producto_data.model_dump(exclude_unset=True)
        return self.producto_repo.update(producto_id, update_dict)

    def delete_producto(self, producto_id: int, user_id: int) -> bool:
        """Elimina un producto verificando que pertenezca al usuario."""
        producto = self.get_producto(producto_id, user_id)
        if not producto:
            return False
        return self.producto_repo.delete(producto_id)


class CategoriaService:
    """Servicio para gestión de categorías."""

    def __init__(self, db: Session):
        self.db = db
        self.categoria_repo = CategoriaRepository(db)

    def create_categoria(self, categoria_data: CategoriaCreate) -> Optional[Categoria]:
        """Crea una nueva categoría."""
        # Validar que el nombre no exista
        existing_categoria = self.categoria_repo.get_by_nombre(categoria_data.nombre)
        if existing_categoria:
            raise ValueError(f"La categoría '{categoria_data.nombre}' ya existe")

        return self.categoria_repo.create(categoria_data.model_dump())

    def get_categoria(self, categoria_id: int) -> Optional[Categoria]:
        """Obtiene una categoría por ID."""
        return self.categoria_repo.get_by_id(categoria_id)

    def get_categorias(self, skip: int = 0, limit: int = 100, activas_only: bool = False) -> List[Categoria]:
        """Obtiene categorías con filtros opcionales."""
        if activas_only:
            return self.categoria_repo.get_activas()
        return self.categoria_repo.get_all(skip=skip, limit=limit)

    def update_categoria(self, categoria_id: int, categoria_data: CategoriaUpdate) -> Optional[Categoria]:
        """Actualiza una categoría."""
        update_dict = categoria_data.model_dump(exclude_unset=True)
        return self.categoria_repo.update(categoria_id, update_dict)

    def delete_categoria(self, categoria_id: int) -> bool:
        """Elimina una categoría."""
        return self.categoria_repo.delete(categoria_id)
