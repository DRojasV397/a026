"""
Endpoints de la API para Usuarios y Roles.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List

from app.database import get_db
from app.schemas import (
    UsuarioCreate, UsuarioUpdate, UsuarioResponse,
    RolCreate, RolResponse,
    UsuarioRolCreate
)
from app.services import UsuarioService, RolService


# Routers
router = APIRouter(prefix="/usuarios", tags=["Usuarios"])
rol_router = APIRouter(prefix="/roles", tags=["Roles"])


# Endpoints de Usuarios
@router.post("/", response_model=UsuarioResponse, status_code=status.HTTP_201_CREATED)
def create_usuario(usuario_data: UsuarioCreate, db: Session = Depends(get_db)):
    """Crea un nuevo usuario."""
    try:
        service = UsuarioService(db)
        usuario = service.create_usuario(usuario_data)
        if not usuario:
            raise HTTPException(status_code=400, detail="Error al crear usuario")
        return usuario
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/", response_model=List[UsuarioResponse])
def get_usuarios(skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    """Obtiene todos los usuarios con paginación."""
    service = UsuarioService(db)
    return service.get_usuarios(skip=skip, limit=limit)


@router.get("/{usuario_id}", response_model=UsuarioResponse)
def get_usuario(usuario_id: int, db: Session = Depends(get_db)):
    """Obtiene un usuario por ID."""
    service = UsuarioService(db)
    usuario = service.get_usuario(usuario_id)
    if not usuario:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")
    return usuario


@router.get("/username/{username}", response_model=UsuarioResponse)
def get_usuario_by_username(username: str, db: Session = Depends(get_db)):
    """Obtiene un usuario por nombre de usuario."""
    service = UsuarioService(db)
    usuario = service.get_usuario_by_username(username)
    if not usuario:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")
    return usuario


@router.put("/{usuario_id}", response_model=UsuarioResponse)
def update_usuario(usuario_id: int, usuario_data: UsuarioUpdate, db: Session = Depends(get_db)):
    """Actualiza un usuario existente."""
    service = UsuarioService(db)
    usuario = service.update_usuario(usuario_id, usuario_data)
    if not usuario:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")
    return usuario


@router.delete("/{usuario_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_usuario(usuario_id: int, db: Session = Depends(get_db)):
    """Elimina un usuario."""
    service = UsuarioService(db)
    if not service.delete_usuario(usuario_id):
        raise HTTPException(status_code=404, detail="Usuario no encontrado")


@router.post("/{usuario_id}/roles", status_code=status.HTTP_200_OK)
def assign_rol_to_usuario(usuario_id: int, rol_data: UsuarioRolCreate, db: Session = Depends(get_db)) -> dict:
    """Asigna un rol a un usuario."""
    # Implementar lógica de asignación de rol
    return {"message": "Rol asignado exitosamente"}


@router.delete("/{usuario_id}/roles/{rol_id}", status_code=status.HTTP_204_NO_CONTENT)
def remove_rol_from_usuario(usuario_id: int, rol_id: int, db: Session = Depends(get_db)):
    """Remueve un rol de un usuario."""
    # Implementar lógica de remoción de rol
    pass


# Endpoints de Roles
@rol_router.post("/", response_model=RolResponse, status_code=status.HTTP_201_CREATED)
def create_rol(rol_data: RolCreate, db: Session = Depends(get_db)):
    """Crea un nuevo rol."""
    service = RolService(db)
    rol = service.create_rol(rol_data)
    if not rol:
        raise HTTPException(status_code=400, detail="Error al crear rol")
    return rol


@rol_router.get("/", response_model=List[RolResponse])
def get_roles(skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    """Obtiene todos los roles con paginación."""
    service = RolService(db)
    return service.get_roles(skip=skip, limit=limit)


@rol_router.get("/{rol_id}", response_model=RolResponse)
def get_rol(rol_id: int, db: Session = Depends(get_db)):
    """Obtiene un rol por ID."""
    service = RolService(db)
    rol = service.get_rol(rol_id)
    if not rol:
        raise HTTPException(status_code=404, detail="Rol no encontrado")
    return rol


@rol_router.delete("/{rol_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_rol(rol_id: int, db: Session = Depends(get_db)):
    """Elimina un rol."""
    service = RolService(db)
    if not service.delete_rol(rol_id):
        raise HTTPException(status_code=404, detail="Rol no encontrado")
