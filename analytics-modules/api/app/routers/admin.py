"""
Router de administracion de usuarios.
Endpoints CRUD para gestion de usuarios por parte de administradores (Principal).
"""

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from pydantic import BaseModel, ConfigDict
from typing import Optional, List
from datetime import datetime
import logging

from app.database import get_db
from app.services.auth_service import AuthService
from app.models import Usuario, Rol, UsuarioRol, PermisoModulo

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/admin", tags=["Admin"])
security = HTTPBearer()

ALL_MODULES = [
    "dashboard", "datos", "predicciones",
    "rentabilidad", "simulacion", "alertas", "reportes"
]


# ── Schemas inline ────────────────────────────────────────────────────────────

class AdminCreateRequest(BaseModel):
    nombreCompleto: str
    nombreUsuario: str
    email: str
    password: str
    tipo: str  # "Principal" or "Secundario"
    modulos: List[str] = []


class AdminUpdateRequest(BaseModel):
    nombreCompleto: Optional[str] = None
    email: Optional[str] = None
    tipo: Optional[str] = None
    modulos: Optional[List[str]] = None


class AdminUpdateModulosRequest(BaseModel):
    modulos: List[str]


class AdminUpdateEstadoRequest(BaseModel):
    estado: str  # "Activo" or "Inactivo"


class AdminUsuarioResponse(BaseModel):
    idUsuario: int
    nombreCompleto: str
    nombreUsuario: str
    email: str
    estado: Optional[str] = None
    creadoEn: Optional[str] = None
    tipo: str
    roles: List[str]
    modulos: List[str]

    model_config = ConfigDict(from_attributes=True)


# ── Dependencies ──────────────────────────────────────────────────────────────

def get_current_admin(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: Session = Depends(get_db)
) -> dict:
    """Verifica que el token sea valido y que el usuario sea Administrador."""
    token = credentials.credentials
    payload = AuthService.decode_token(token)

    if not payload:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token invalido o expirado"
        )

    roles = payload.get("roles", [])
    if "Administrador" not in roles:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Acceso denegado: se requiere rol Administrador"
        )

    return payload


# ── Helpers ───────────────────────────────────────────────────────────────────

def _get_user_tipo_and_modules(usuario: Usuario, db: Session):
    """Retorna (tipo, roles, modulos) para un usuario dado."""
    roles_db = db.query(Rol).join(
        UsuarioRol, Rol.idRol == UsuarioRol.idRol
    ).filter(
        UsuarioRol.idUsuario == usuario.idUsuario
    ).all()

    roles = [r.nombre for r in roles_db]
    tipo = "Principal" if "Administrador" in roles else "Secundario"

    if tipo == "Principal":
        modulos = list(ALL_MODULES)
    else:
        permisos = db.query(PermisoModulo).filter(
            PermisoModulo.idUsuario == usuario.idUsuario
        ).all()
        modulos = [p.modulo for p in permisos]

    return tipo, roles, modulos


def _build_response(usuario: Usuario, db: Session) -> AdminUsuarioResponse:
    """Construye AdminUsuarioResponse a partir de un Usuario."""
    tipo, roles, modulos = _get_user_tipo_and_modules(usuario, db)

    return AdminUsuarioResponse(
        idUsuario=usuario.idUsuario,
        nombreCompleto=usuario.nombreCompleto or "",
        nombreUsuario=usuario.nombreUsuario or "",
        email=usuario.email or "",
        estado=usuario.estado,
        creadoEn=usuario.creadoEn.isoformat() if usuario.creadoEn else None,
        tipo=tipo,
        roles=roles,
        modulos=modulos
    )


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get(
    "/usuarios",
    response_model=List[AdminUsuarioResponse],
    summary="Listar todos los usuarios"
)
async def list_usuarios(
    admin: dict = Depends(get_current_admin),
    db: Session = Depends(get_db)
):
    """Lista todos los usuarios con su tipo, roles y modulos."""
    usuarios = db.query(Usuario).order_by(Usuario.idUsuario).all()
    return [_build_response(u, db) for u in usuarios]


@router.post(
    "/usuarios",
    response_model=AdminUsuarioResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Crear usuario"
)
async def create_usuario(
    req: AdminCreateRequest,
    admin: dict = Depends(get_current_admin),
    db: Session = Depends(get_db)
):
    """Crea un nuevo usuario con rol y modulos asignados."""
    # Verificar unicidad
    existing = db.query(Usuario).filter(
        (Usuario.nombreUsuario == req.nombreUsuario) |
        (Usuario.email == req.email)
    ).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="El nombre de usuario o email ya existe"
        )

    # Crear usuario
    hashed = AuthService.hash_password(req.password)
    nuevo = Usuario(
        nombreCompleto=req.nombreCompleto,
        nombreUsuario=req.nombreUsuario,
        email=req.email,
        hashPassword=hashed,
        estado='Activo',
        creadoEn=datetime.now()
    )
    db.add(nuevo)
    db.flush()

    # Asignar rol
    rol_nombre = "Administrador" if req.tipo == "Principal" else "Secundario"
    rol = db.query(Rol).filter(Rol.nombre == rol_nombre).first()
    if rol:
        usuario_rol = UsuarioRol(
            idUsuario=nuevo.idUsuario,
            idRol=rol.idRol
        )
        db.add(usuario_rol)

    # Asignar modulos (solo para Secundario)
    if req.tipo == "Secundario" and req.modulos:
        for modulo in req.modulos:
            if modulo in ALL_MODULES:
                permiso = PermisoModulo(
                    idUsuario=nuevo.idUsuario,
                    modulo=modulo
                )
                db.add(permiso)

    db.commit()
    db.refresh(nuevo)

    return _build_response(nuevo, db)


@router.put(
    "/usuarios/{user_id}",
    response_model=AdminUsuarioResponse,
    summary="Actualizar usuario"
)
async def update_usuario(
    user_id: int,
    req: AdminUpdateRequest,
    admin: dict = Depends(get_current_admin),
    db: Session = Depends(get_db)
):
    """Actualiza campos del usuario. Si cambia el tipo, reasigna rol y modulos."""
    usuario = db.query(Usuario).filter(Usuario.idUsuario == user_id).first()
    if not usuario:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Usuario no encontrado"
        )

    # Actualizar campos basicos
    if req.nombreCompleto is not None:
        usuario.nombreCompleto = req.nombreCompleto
    if req.email is not None:
        # Verificar unicidad del email
        existing = db.query(Usuario).filter(
            Usuario.email == req.email,
            Usuario.idUsuario != user_id
        ).first()
        if existing:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="El email ya esta en uso por otro usuario"
            )
        usuario.email = req.email

    # Si cambia el tipo, reasignar rol
    if req.tipo is not None:
        # Eliminar roles actuales
        db.query(UsuarioRol).filter(UsuarioRol.idUsuario == user_id).delete()

        rol_nombre = "Administrador" if req.tipo == "Principal" else "Secundario"
        rol = db.query(Rol).filter(Rol.nombre == rol_nombre).first()
        if rol:
            usuario_rol = UsuarioRol(
                idUsuario=user_id,
                idRol=rol.idRol
            )
            db.add(usuario_rol)

        # Si cambia a Principal, eliminar permisos de modulo (tiene acceso total)
        if req.tipo == "Principal":
            db.query(PermisoModulo).filter(PermisoModulo.idUsuario == user_id).delete()

    # Actualizar modulos si se proporcionan
    if req.modulos is not None:
        db.query(PermisoModulo).filter(PermisoModulo.idUsuario == user_id).delete()
        for modulo in req.modulos:
            if modulo in ALL_MODULES:
                permiso = PermisoModulo(
                    idUsuario=user_id,
                    modulo=modulo
                )
                db.add(permiso)

    db.commit()
    db.refresh(usuario)

    return _build_response(usuario, db)


@router.put(
    "/usuarios/{user_id}/modulos",
    summary="Actualizar modulos de un usuario"
)
async def update_modulos(
    user_id: int,
    req: AdminUpdateModulosRequest,
    admin: dict = Depends(get_current_admin),
    db: Session = Depends(get_db)
):
    """Reemplaza la lista de modulos permitidos para un usuario."""
    usuario = db.query(Usuario).filter(Usuario.idUsuario == user_id).first()
    if not usuario:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Usuario no encontrado"
        )

    # Eliminar permisos existentes
    db.query(PermisoModulo).filter(PermisoModulo.idUsuario == user_id).delete()

    # Agregar nuevos
    for modulo in req.modulos:
        if modulo in ALL_MODULES:
            permiso = PermisoModulo(
                idUsuario=user_id,
                modulo=modulo
            )
            db.add(permiso)

    db.commit()
    return {"message": "ok"}


@router.put(
    "/usuarios/{user_id}/estado",
    summary="Cambiar estado de usuario (Activo/Inactivo)"
)
async def update_estado(
    user_id: int,
    req: AdminUpdateEstadoRequest,
    admin: dict = Depends(get_current_admin),
    db: Session = Depends(get_db)
):
    """Activa o desactiva un usuario."""
    usuario = db.query(Usuario).filter(Usuario.idUsuario == user_id).first()
    if not usuario:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Usuario no encontrado"
        )

    usuario.estado = req.estado
    db.commit()
    return {"message": "ok"}


@router.delete(
    "/usuarios/{user_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Eliminar usuario"
)
async def delete_usuario(
    user_id: int,
    admin: dict = Depends(get_current_admin),
    db: Session = Depends(get_db)
):
    """Elimina un usuario. No permite eliminar usuarios con rol Administrador."""
    usuario = db.query(Usuario).filter(Usuario.idUsuario == user_id).first()
    if not usuario:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Usuario no encontrado"
        )

    # Verificar que no sea Principal (Administrador)
    tipo, _, _ = _get_user_tipo_and_modules(usuario, db)
    if tipo == "Principal":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No se puede eliminar un usuario Principal (Administrador)"
        )

    db.delete(usuario)
    db.commit()
    return None
