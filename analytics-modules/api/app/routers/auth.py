"""
Router de autenticacion.
Endpoints para login, registro, verificacion y refresh de tokens.
"""

from collections import defaultdict
from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
import logging
import time

from app.database import get_db
from app.services.auth_service import AuthService
from app.middleware import get_current_active_user
from app.models import Usuario
from app.schemas.auth import (
    LoginRequest,
    LoginResponse,
    RegisterRequest,
    RegisterResponse,
    TokenVerifyRequest,
    TokenVerifyResponse,
    RefreshTokenRequest,
    RefreshTokenResponse,
    ChangePasswordRequest,
    ChangePasswordResponse,
    UserInfo
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/auth", tags=["Autenticacion"])

# ─── Rate limiting para /auth/login ──────────────────────────────────────────
# Máximo 5 intentos fallidos por IP en una ventana de 5 minutos.
_login_attempts: dict[str, list[float]] = defaultdict(list)
_MAX_LOGIN_ATTEMPTS = 5
_LOGIN_WINDOW_SECONDS = 300  # 5 minutos


def _check_rate_limit(ip: str) -> None:
    """Lanza 429 si la IP superó el límite de intentos fallidos."""
    now = time.time()
    # Descartar intentos fuera de la ventana
    _login_attempts[ip] = [t for t in _login_attempts[ip] if now - t < _LOGIN_WINDOW_SECONDS]
    if len(_login_attempts[ip]) >= _MAX_LOGIN_ATTEMPTS:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Demasiados intentos fallidos. Intenta de nuevo en {_LOGIN_WINDOW_SECONDS // 60} minutos.",
            headers={"Retry-After": str(_LOGIN_WINDOW_SECONDS)},
        )


def _record_failed(ip: str) -> None:
    _login_attempts[ip].append(time.time())


def _clear_attempts(ip: str) -> None:
    _login_attempts.pop(ip, None)


@router.post(
    "/login",
    response_model=LoginResponse,
    summary="Iniciar sesion",
    description="Autentica un usuario y retorna tokens JWT"
)
async def login(
    request: Request,
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: Session = Depends(get_db)
):
    """
    Endpoint de login usando OAuth2 password flow.

    - **username**: Nombre de usuario o email
    - **password**: Contrasena

    Retorna access_token y refresh_token.
    """
    ip = request.client.host if request.client else "unknown"
    _check_rate_limit(ip)

    auth_service = AuthService(db)
    result = auth_service.login(form_data.username, form_data.password)

    if not result:
        _record_failed(ip)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Usuario o contrasena incorrectos",
            headers={"WWW-Authenticate": "Bearer"},
        )

    _clear_attempts(ip)
    return LoginResponse(
        access_token=result["access_token"],
        refresh_token=result["refresh_token"],
        token_type=result["token_type"],
        expires_in=result["expires_in"],
        user=result["user"]
    )


@router.post(
    "/login/json",
    response_model=LoginResponse,
    summary="Iniciar sesion (JSON)",
    description="Autentica un usuario usando JSON body"
)
async def login_json(
    request: Request,
    credentials: LoginRequest,
    db: Session = Depends(get_db)
):
    """
    Endpoint de login usando JSON body.

    Alternativa al OAuth2 form para clientes que prefieren JSON.
    """
    ip = request.client.host if request.client else "unknown"
    _check_rate_limit(ip)

    auth_service = AuthService(db)
    result = auth_service.login(credentials.username, credentials.password)

    if not result:
        _record_failed(ip)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Usuario o contrasena incorrectos",
            headers={"WWW-Authenticate": "Bearer"},
        )

    _clear_attempts(ip)
    return LoginResponse(
        access_token=result["access_token"],
        refresh_token=result["refresh_token"],
        token_type=result["token_type"],
        expires_in=result["expires_in"],
        user=result["user"]
    )


@router.post(
    "/register",
    response_model=RegisterResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Registrar usuario",
    description="Registra un nuevo usuario en el sistema"
)
async def register(
    user_data: RegisterRequest,
    db: Session = Depends(get_db)
):
    """
    Endpoint de registro de usuario.

    - **nombreCompleto**: Nombre completo del usuario
    - **nombreUsuario**: Nombre de usuario para login
    - **email**: Email del usuario
    - **password**: Contrasena (minimo 8 caracteres)
    - **confirmPassword**: Confirmacion de contrasena
    """
    # Validar que las contrasenas coincidan
    if user_data.password != user_data.confirmPassword:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Las contrasenas no coinciden"
        )

    auth_service = AuthService(db)
    user = auth_service.register_user(
        nombre_completo=user_data.nombreCompleto,
        nombre_usuario=user_data.nombreUsuario,
        email=user_data.email,
        password=user_data.password
    )

    if not user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No se pudo registrar el usuario. El nombre de usuario o email ya existe."
        )

    return RegisterResponse(
        message="Usuario registrado exitosamente",
        idUsuario=user.idUsuario,
        nombreUsuario=user.nombreUsuario
    )


@router.get(
    "/verify",
    response_model=TokenVerifyResponse,
    summary="Verificar token",
    description="Verifica si el token actual es valido"
)
async def verify_token(
    current_user: Usuario = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """
    Verifica el token del header Authorization.

    Retorna informacion del usuario si el token es valido.
    """
    auth_service = AuthService(db)
    user_info = auth_service.get_user_info(current_user)

    return TokenVerifyResponse(
        valid=True,
        user=user_info,
        message="Token valido"
    )


@router.post(
    "/refresh",
    response_model=RefreshTokenResponse,
    summary="Refrescar token",
    description="Obtiene un nuevo access token usando el refresh token"
)
async def refresh_token(
    token_data: RefreshTokenRequest,
    db: Session = Depends(get_db)
):
    """
    Refresca el access token usando un refresh token valido.

    - **refresh_token**: Token de refresco obtenido en el login
    """
    auth_service = AuthService(db)
    result = auth_service.refresh_access_token(token_data.refresh_token)

    if not result:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Refresh token invalido o expirado",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return RefreshTokenResponse(
        access_token=result["access_token"],
        token_type=result["token_type"],
        expires_in=result["expires_in"]
    )


@router.post(
    "/logout",
    summary="Cerrar sesion",
    description="Cierra la sesion del usuario (client-side)"
)
async def logout(
    current_user: Usuario = Depends(get_current_active_user)
):
    """
    Endpoint de logout.

    Nota: Con JWT stateless, el logout se maneja del lado del cliente
    eliminando el token. Este endpoint sirve para logging y futuras
    implementaciones de blacklist de tokens.
    """
    logger.info(f"Usuario {current_user.nombreUsuario} cerro sesion")

    return {
        "message": "Sesion cerrada exitosamente",
        "detail": "Elimina el token del almacenamiento del cliente"
    }


@router.put(
    "/password",
    response_model=ChangePasswordResponse,
    summary="Cambiar contrasena",
    description="Cambia la contrasena del usuario autenticado"
)
async def change_password(
    password_data: ChangePasswordRequest,
    current_user: Usuario = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """
    Cambia la contrasena del usuario actual.

    - **current_password**: Contrasena actual
    - **new_password**: Nueva contrasena (minimo 8 caracteres)
    - **confirm_password**: Confirmacion de nueva contrasena
    """
    # Validar que las contrasenas nuevas coincidan
    if password_data.new_password != password_data.confirm_password:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Las contrasenas nuevas no coinciden"
        )

    auth_service = AuthService(db)
    success = auth_service.change_password(
        user_id=current_user.idUsuario,
        current_password=password_data.current_password,
        new_password=password_data.new_password
    )

    if not success:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Contrasena actual incorrecta"
        )

    return ChangePasswordResponse(
        message="Contrasena actualizada exitosamente",
        success=True
    )


@router.get(
    "/me",
    response_model=UserInfo,
    summary="Obtener usuario actual",
    description="Retorna informacion del usuario autenticado"
)
async def get_current_user_info(
    current_user: Usuario = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """
    Obtiene la informacion del usuario actualmente autenticado.
    """
    auth_service = AuthService(db)
    return auth_service.get_user_info(current_user)
