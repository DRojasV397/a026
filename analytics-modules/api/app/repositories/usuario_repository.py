"""
Repositorio para el modelo Usuario con operaciones específicas.
"""

from typing import Optional, List
from sqlalchemy.orm import Session
import logging

from app.models import Usuario, Rol, UsuarioRol, PreferenciaUsuario
from .base_repository import BaseRepository

logger = logging.getLogger(__name__)


class UsuarioRepository(BaseRepository[Usuario]):
    """Repositorio específico para Usuario."""

    def __init__(self, db: Session):
        super().__init__(Usuario, db)

    def get_by_username(self, username: str) -> Optional[Usuario]:
        """
        Obtiene un usuario por su nombre de usuario.

        Args:
            username: Nombre de usuario

        Returns:
            Optional[Usuario]: Usuario encontrado o None
        """
        try:
            return self.db.query(Usuario).filter(Usuario.nombreUsuario == username).first()
        except Exception as e:
            logger.error(f"Error al buscar usuario por username: {str(e)}")
            return None

    def get_by_email(self, email: str) -> Optional[Usuario]:
        """
        Obtiene un usuario por su email.

        Args:
            email: Email del usuario

        Returns:
            Optional[Usuario]: Usuario encontrado o None
        """
        try:
            return self.db.query(Usuario).filter(Usuario.email == email).first()
        except Exception as e:
            logger.error(f"Error al buscar usuario por email: {str(e)}")
            return None

    def get_activos(self) -> List[Usuario]:
        """
        Obtiene todos los usuarios activos.

        Returns:
            List[Usuario]: Lista de usuarios activos
        """
        try:
            return self.db.query(Usuario).filter(Usuario.activo == 1).all()
        except Exception as e:
            logger.error(f"Error al obtener usuarios activos: {str(e)}")
            return []


class RolRepository(BaseRepository[Rol]):
    """Repositorio específico para Rol."""

    def __init__(self, db: Session):
        super().__init__(Rol, db)

    def get_by_nombre(self, nombre: str) -> Optional[Rol]:
        """
        Obtiene un rol por su nombre.

        Args:
            nombre: Nombre del rol

        Returns:
            Optional[Rol]: Rol encontrado o None
        """
        try:
            return self.db.query(Rol).filter(Rol.nombre == nombre).first()
        except Exception as e:
            logger.error(f"Error al buscar rol por nombre: {str(e)}")
            return None


class PreferenciaUsuarioRepository(BaseRepository[PreferenciaUsuario]):
    """Repositorio específico para PreferenciaUsuario."""

    def __init__(self, db: Session):
        super().__init__(PreferenciaUsuario, db)

    def get_by_usuario(self, id_usuario: int) -> List[PreferenciaUsuario]:
        """
        Obtiene todas las preferencias de un usuario.

        Args:
            id_usuario: ID del usuario

        Returns:
            List[PreferenciaUsuario]: Lista de preferencias
        """
        try:
            return self.db.query(PreferenciaUsuario).filter(
                PreferenciaUsuario.idUsuario == id_usuario
            ).all()
        except Exception as e:
            logger.error(f"Error al obtener preferencias del usuario: {str(e)}")
            return []
