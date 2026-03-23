"""
Middleware de autenticacion y autorizacion.
Proporciona dependencias para proteger endpoints con JWT.
"""

from typing import Optional, List, Tuple
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

# Excepcion reutilizable para credenciales invalidas
_CREDENTIALS_EXCEPTION = HTTPException(
    status_code=status.HTTP_401_UNAUTHORIZED,
    detail="No se pudo validar las credenciales",
    headers={"WWW-Authenticate": "Bearer"},
)

_INACTIVE_EXCEPTION = HTTPException(
    status_code=status.HTTP_403_FORBIDDEN,
    detail="Usuario inactivo",
)


async def _resolve_user(
    token: Optional[str],
    db: Session,
) -> Tuple[Usuario, TokenData]:
    """
    Helper interno: verifica el token JWT y carga el Usuario de la BD.

    Centraliza la logica compartida por get_current_user,
    get_current_active_user y require_roles, evitando duplicacion.

    Returns:
        (Usuario, TokenData)

    Raises:
        HTTPException 401: Si falta token, es invalido, o el usuario no existe.
    """
    if not token:
        raise _CREDENTIALS_EXCEPTION

    auth_service = AuthService(db)
    token_data = auth_service.verify_token(token)
    if not token_data:
        raise _CREDENTIALS_EXCEPTION

    from app.repositories import UsuarioRepository
    user = UsuarioRepository(db).get_by_id(token_data.idUsuario)
    if not user:
        raise _CREDENTIALS_EXCEPTION

    return user, token_data


async def get_current_user(
    token: Optional[str] = Depends(oauth2_scheme),
    db: Session = Depends(get_db),
) -> Usuario:
    """
    Obtiene el usuario autenticado a partir del token JWT.
    Lanza 401 si el token falta o es invalido.

    Returns:
        Usuario autenticado (sin verificar estado activo).
    """
    user, _ = await _resolve_user(token, db)
    return user


async def get_current_active_user(
    token: Optional[str] = Depends(oauth2_scheme),
    db: Session = Depends(get_db),
) -> Usuario:
    """
    Obtiene el usuario autenticado y activo.
    Lanza 401 si el token es invalido, 403 si el usuario esta inactivo.

    Returns:
        Usuario autenticado y activo.
    """
    user, _ = await _resolve_user(token, db)
    if user.estado and user.estado.lower() != "activo":
        raise _INACTIVE_EXCEPTION
    return user


def require_roles(allowed_roles: List[str]):
    """
    Dependencia para requerir roles especificos.

    Verifica (en orden):
    1. Token valido y usuario existente (401 si falla)
    2. Usuario activo (403 si inactivo)          ← fix B3
    3. Al menos un rol permitido (403 si falta)

    Args:
        allowed_roles: Lista de roles aceptados (OR logico).

    Returns:
        Dependencia de FastAPI que retorna el Usuario autenticado.
    """
    async def role_checker(
        token: Optional[str] = Depends(oauth2_scheme),
        db: Session = Depends(get_db),
    ) -> Usuario:
        user, token_data = await _resolve_user(token, db)

        # B3: verificar que el usuario este activo antes de comprobar roles
        if user.estado and user.estado.lower() != "activo":
            raise _INACTIVE_EXCEPTION

        user_roles = token_data.roles or []
        if not any(role in allowed_roles for role in user_roles):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Se requiere uno de los siguientes roles: {', '.join(allowed_roles)}",
            )

        return user

    return role_checker


# Dependencias pre-configuradas para roles comunes
require_admin = require_roles(["Administrador", "Admin"])
require_operativo = require_roles(["Administrador", "Admin", "Operativo"])
