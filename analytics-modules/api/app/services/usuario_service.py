"""
Servicio de lógica de negocio para Usuarios.
Capa intermedia entre repositorios y endpoints.
"""

from typing import List, Optional
from sqlalchemy.orm import Session
import logging

from app.repositories import UsuarioRepository, RolRepository, PreferenciaUsuarioRepository
from app.schemas import (
    UsuarioCreate, UsuarioUpdate, UsuarioResponse,
    RolCreate, RolResponse,
    PreferenciaUsuarioCreate, PreferenciaUsuarioUpdate
)
from app.models import Usuario

logger = logging.getLogger(__name__)


class UsuarioService:
    """Servicio para gestión de usuarios."""

    def __init__(self, db: Session):
        self.db = db
        self.usuario_repo = UsuarioRepository(db)

    def create_usuario(self, usuario_data: UsuarioCreate) -> Optional[Usuario]:
        """
        Crea un nuevo usuario.

        Args:
            usuario_data: Datos del usuario

        Returns:
            Optional[Usuario]: Usuario creado o None
        """
        # Validar que el username no exista
        existing_user = self.usuario_repo.get_by_username(usuario_data.nombreUsuario)
        if existing_user:
            raise ValueError(f"El usuario '{usuario_data.nombreUsuario}' ya existe")

        # Validar que el email no exista
        existing_email = self.usuario_repo.get_by_email(usuario_data.email)
        if existing_email:
            raise ValueError(f"El email '{usuario_data.email}' ya está registrado")

        return self.usuario_repo.create(usuario_data.model_dump())

    def get_usuario(self, usuario_id: int) -> Optional[Usuario]:
        """Obtiene un usuario por ID."""
        return self.usuario_repo.get_by_id(usuario_id)

    def get_usuario_by_username(self, username: str) -> Optional[Usuario]:
        """Obtiene un usuario por nombre de usuario."""
        return self.usuario_repo.get_by_username(username)

    def get_usuarios(self, skip: int = 0, limit: int = 100) -> List[Usuario]:
        """Obtiene todos los usuarios con paginación."""
        return self.usuario_repo.get_all(skip=skip, limit=limit)

    def update_usuario(self, usuario_id: int, usuario_data: UsuarioUpdate) -> Optional[Usuario]:
        """Actualiza un usuario."""
        update_dict = usuario_data.model_dump(exclude_unset=True)
        return self.usuario_repo.update(usuario_id, update_dict)

    def delete_usuario(self, usuario_id: int) -> bool:
        """Elimina un usuario."""
        return self.usuario_repo.delete(usuario_id)


class RolService:
    """Servicio para gestión de roles."""

    def __init__(self, db: Session):
        self.db = db
        self.rol_repo = RolRepository(db)

    def create_rol(self, rol_data: RolCreate) -> Optional[Usuario]:
        """Crea un nuevo rol."""
        return self.rol_repo.create(rol_data.model_dump())

    def get_rol(self, rol_id: int) -> Optional[Usuario]:
        """Obtiene un rol por ID."""
        return self.rol_repo.get_by_id(rol_id)

    def get_roles(self, skip: int = 0, limit: int = 100) -> List[Usuario]:
        """Obtiene todos los roles con paginación."""
        return self.rol_repo.get_all(skip=skip, limit=limit)

    def delete_rol(self, rol_id: int) -> bool:
        """Elimina un rol."""
        return self.rol_repo.delete(rol_id)
