"""
Repositorio base con operaciones CRUD genéricas.
Implementa patrón Repository para abstracción de acceso a datos.
"""

from typing import Generic, TypeVar, Type, List, Optional, Any, Dict
from sqlalchemy.orm import Session
from sqlalchemy import inspect
import logging

from app.database import Base

# TypeVar para genéricos
ModelType = TypeVar('ModelType', bound=Base)

logger = logging.getLogger(__name__)


class BaseRepository(Generic[ModelType]):
    """
    Repositorio base con operaciones CRUD genéricas.
    Usa programación orientada a objetos y genéricos para reutilización.
    """

    def __init__(self, model: Type[ModelType], db: Session):
        """
        Inicializa el repositorio.

        Args:
            model: Clase del modelo SQLAlchemy
            db: Sesión de base de datos
        """
        self.model = model
        self.db = db

    def get_by_id(self, id: int) -> Optional[ModelType]:
        """
        Obtiene un registro por su ID.

        Args:
            id: ID del registro

        Returns:
            Optional[ModelType]: Registro encontrado o None
        """
        try:
            # Obtener nombre de la clave primaria
            pk = inspect(self.model).primary_key[0].name
            return self.db.query(self.model).filter(getattr(self.model, pk) == id).first()
        except Exception as e:
            logger.error(f"Error al obtener registro por ID {id}: {str(e)}")
            return None

    def get_all(self, skip: int = 0, limit: int = 100) -> List[ModelType]:
        """
        Obtiene todos los registros con paginación.

        Args:
            skip: Número de registros a saltar
            limit: Número máximo de registros a retornar

        Returns:
            List[ModelType]: Lista de registros
        """
        try:
            # Obtener la clave primaria para ordenamiento
            pk = inspect(self.model).primary_key[0].name
            pk_col = getattr(self.model, pk)
            return self.db.query(self.model).order_by(pk_col).offset(skip).limit(limit).all()
        except Exception as e:
            logger.error(f"Error al obtener todos los registros: {str(e)}")
            return []

    def create(self, obj_in: Dict[str, Any]) -> Optional[ModelType]:
        """
        Crea un nuevo registro.

        Args:
            obj_in: Diccionario con los datos del registro

        Returns:
            Optional[ModelType]: Registro creado o None si hay error
        """
        try:
            db_obj = self.model(**obj_in)
            self.db.add(db_obj)
            self.db.commit()
            self.db.refresh(db_obj)
            logger.info(f"Registro creado exitosamente en {self.model.__name__}")
            return db_obj
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al crear registro: {str(e)}")
            return None

    def update(self, id: int, obj_in: Dict[str, Any]) -> Optional[ModelType]:
        """
        Actualiza un registro existente.

        Args:
            id: ID del registro a actualizar
            obj_in: Diccionario con los datos a actualizar

        Returns:
            Optional[ModelType]: Registro actualizado o None
        """
        try:
            db_obj = self.get_by_id(id)
            if db_obj:
                for key, value in obj_in.items():
                    if hasattr(db_obj, key):
                        setattr(db_obj, key, value)
                self.db.commit()
                self.db.refresh(db_obj)
                logger.info(f"Registro {id} actualizado exitosamente")
                return db_obj
            return None
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al actualizar registro {id}: {str(e)}")
            return None

    def delete(self, id: int) -> bool:
        """
        Elimina un registro.

        Args:
            id: ID del registro a eliminar

        Returns:
            bool: True si se eliminó exitosamente, False en caso contrario
        """
        try:
            db_obj = self.get_by_id(id)
            if db_obj:
                self.db.delete(db_obj)
                self.db.commit()
                logger.info(f"Registro {id} eliminado exitosamente")
                return True
            return False
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al eliminar registro {id}: {str(e)}")
            return False

    def count(self) -> int:
        """
        Cuenta el total de registros.

        Returns:
            int: Número total de registros
        """
        try:
            return self.db.query(self.model).count()
        except Exception as e:
            logger.error(f"Error al contar registros: {str(e)}")
            return 0

    def exists(self, id: int) -> bool:
        """
        Verifica si existe un registro con el ID dado.

        Args:
            id: ID del registro

        Returns:
            bool: True si existe, False en caso contrario
        """
        return self.get_by_id(id) is not None
