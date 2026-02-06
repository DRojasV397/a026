"""
Middleware de autenticacion y autorizacion.
Proporciona dependencias para proteger endpoints con JWT.
"""

from typing import Optional, List
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.orm import Session
import logging

from app.database import get_db
from app.services.auth_service import AuthService
from app.schemas.auth import TokenData
from app.models import Usuario

logger = logging.getLogger(__name__)

# Esquema OAuth2 para obtener token del header Authorization
oauth2_scheme = OAuth2PasswordBearer(
    tokenUrl="/api/v1/auth/login",
    auto_error=False
)


async def get_current_user(
    token: Optional[str] = Depends(oauth2_scheme),
    db: Session = Depends(get_db)
) -> Optional[Usuario]:
    """
    Obtiene el usuario actual a partir del token JWT.
    No lanza excepcion si no hay token (para endpoints opcionales).

    Args:
        token: Token JWT del header Authorization
        db: Sesion de base de datos

    Returns:
        Optional[Usuario]: Usuario autenticado o None
    """
    if not token:
        return None

    auth_service = AuthService(db)
    token_data = auth_service.verify_token(token)

    if not token_data:
        return None

    # Obtener usuario de la BD
    from app.repositories import UsuarioRepository
    user_repo = UsuarioRepository(db)
    user = user_repo.get_by_id(token_data.idUsuario)

    return user


async def get_current_active_user(
    token: Optional[str] = Depends(oauth2_scheme),
    db: Session = Depends(get_db)
) -> Usuario:
    """
    Obtiene el usuario actual autenticado y activo.
    Lanza excepcion si no hay token o usuario invalido.

    Args:
        token: Token JWT del header Authorization
        db: Sesion de base de datos

    Returns:
        Usuario: Usuario autenticado

    Raises:
        HTTPException: Si no hay token, es invalido o usuario inactivo
    """
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="No se pudo validar las credenciales",
        headers={"WWW-Authenticate": "Bearer"},
    )

    if not token:
        raise credentials_exception

    auth_service = AuthService(db)
    token_data = auth_service.verify_token(token)

    if not token_data:
        raise credentials_exception

    # Obtener usuario de la BD
    from app.repositories import UsuarioRepository
    user_repo = UsuarioRepository(db)
    user = user_repo.get_by_id(token_data.idUsuario)

    if not user:
        raise credentials_exception

    # Verificar que el usuario este activo
    if user.estado and user.estado.lower() != 'activo':
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Usuario inactivo"
        )

    return user


def require_roles(allowed_roles: List[str]):
    """
    Decorador/dependencia para requerir roles especificos.

    Args:
        allowed_roles: Lista de roles permitidos

    Returns:
        Dependencia que verifica los roles
    """
    async def role_checker(
        token: Optional[str] = Depends(oauth2_scheme),
        db: Session = Depends(get_db)
    ) -> Usuario:
        """
        Verifica que el usuario tenga uno de los roles permitidos.

        Returns:
            Usuario: Usuario autenticado con rol valido

        Raises:
            HTTPException: Si no tiene permisos
        """
        credentials_exception = HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="No se pudo validar las credenciales",
            headers={"WWW-Authenticate": "Bearer"},
        )

        if not token:
            raise credentials_exception

        auth_service = AuthService(db)
        token_data = auth_service.verify_token(token)

        if not token_data:
            raise credentials_exception

        # Verificar roles
        user_roles = token_data.roles or []
        has_permission = any(role in allowed_roles for role in user_roles)

        if not has_permission:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Se requiere uno de los siguientes roles: {', '.join(allowed_roles)}"
            )

        # Obtener usuario de la BD
        from app.repositories import UsuarioRepository
        user_repo = UsuarioRepository(db)
        user = user_repo.get_by_id(token_data.idUsuario)

        if not user:
            raise credentials_exception

        return user

    return role_checker


# Dependencias pre-configuradas para roles comunes
require_admin = require_roles(["Administrador", "Admin"])
require_operativo = require_roles(["Administrador", "Admin", "Operativo"])
