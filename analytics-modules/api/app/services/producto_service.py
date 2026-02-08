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

    def create_producto(self, producto_data: ProductoCreate) -> Optional[Producto]:
        """
        Crea un nuevo producto.

        Args:
            producto_data: Datos del producto

        Returns:
            Optional[Producto]: Producto creado o None
        """
        # Validar que el SKU no exista
        existing_producto = self.producto_repo.get_by_sku(producto_data.sku)
        if existing_producto:
            raise ValueError(f"El SKU '{producto_data.sku}' ya existe")

        # Validar que la categoría exista si se proporciona
        if producto_data.idCategoria:
            categoria = self.categoria_repo.get_by_id(producto_data.idCategoria)
            if not categoria:
                raise ValueError(f"La categoría {producto_data.idCategoria} no existe")

        return self.producto_repo.create(producto_data.model_dump())

    def get_producto(self, producto_id: int) -> Optional[Producto]:
        """Obtiene un producto por ID."""
        return self.producto_repo.get_by_id(producto_id)

    def get_producto_by_sku(self, sku: str) -> Optional[Producto]:
        """Obtiene un producto por SKU."""
        return self.producto_repo.get_by_sku(sku)

    def get_productos(
        self,
        skip: int = 0,
        limit: int = 100,
        activos_only: bool = False,
        categoria_id: Optional[int] = None
    ) -> List[Producto]:
        """Obtiene productos con filtros opcionales."""
        if categoria_id:
            return self.producto_repo.get_by_categoria(categoria_id)
        if activos_only:
            return self.producto_repo.get_activos()
        return self.producto_repo.get_all(skip=skip, limit=limit)

    def update_producto(self, producto_id: int, producto_data: ProductoUpdate) -> Optional[Producto]:
        """Actualiza un producto."""
        update_dict = producto_data.model_dump(exclude_unset=True)
        return self.producto_repo.update(producto_id, update_dict)

    def delete_producto(self, producto_id: int) -> bool:
        """Elimina un producto."""
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
