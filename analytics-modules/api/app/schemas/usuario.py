"""
Esquemas DTO (Pydantic) para el módulo de Usuarios.
"""

from pydantic import BaseModel, EmailStr, Field, ConfigDict
from typing import Optional, List
from datetime import datetime


# Esquemas de Usuario
class UsuarioBase(BaseModel):
    """Esquema base de Usuario."""
    nombreCompleto: str = Field(..., description="Nombre completo del usuario")
    nombreUsuario: str = Field(..., description="Nombre de usuario único")
    email: EmailStr = Field(..., description="Correo electrónico del usuario")
    estado: Optional[str] = None


class UsuarioCreate(UsuarioBase):
    """Esquema para crear un Usuario."""
    hashPassword: str = Field(..., description="Contraseña hasheada")


class UsuarioUpdate(BaseModel):
    """Esquema para actualizar un Usuario."""
    nombreCompleto: Optional[str] = None
    email: Optional[EmailStr] = None
    estado: Optional[str] = None


class UsuarioResponse(UsuarioBase):
    """Esquema de respuesta de Usuario."""
    idUsuario: int
    creadoEn: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


class UsuarioCompleto(UsuarioResponse):
    """Esquema completo de Usuario con relaciones."""
    # Se pueden agregar relaciones aquí si es necesario
    pass


# Esquemas de Rol
class RolBase(BaseModel):
    """Esquema base de Rol."""
    nombreRol: str
    descripcion: Optional[str] = None
    activo: Optional[int] = 1


class RolCreate(RolBase):
    """Esquema para crear un Rol."""
    pass


class RolResponse(RolBase):
    """Esquema de respuesta de Rol."""
    idRol: int

    model_config = ConfigDict(from_attributes=True)


# Esquemas de UsuarioRol
class UsuarioRolCreate(BaseModel):
    """Esquema para asignar un Rol a un Usuario."""
    idUsuario: int
    idRol: int


class UsuarioRolResponse(UsuarioRolCreate):
    """Esquema de respuesta de UsuarioRol."""
    fechaAsignacion: datetime

    model_config = ConfigDict(from_attributes=True)


# Esquemas de PreferenciaUsuario
class PreferenciaUsuarioBase(BaseModel):
    """Esquema base de Preferencia de Usuario."""
    kpi: str
    valorPreferencia: Optional[str] = None
    activo: Optional[int] = 1


class PreferenciaUsuarioCreate(PreferenciaUsuarioBase):
    """Esquema para crear una Preferencia de Usuario."""
    idUsuario: int


class PreferenciaUsuarioUpdate(BaseModel):
    """Esquema para actualizar una Preferencia de Usuario."""
    valorPreferencia: Optional[str] = None
    activo: Optional[int] = None


class PreferenciaUsuarioResponse(PreferenciaUsuarioBase):
    """Esquema de respuesta de Preferencia de Usuario."""
    idPreferencia: int
    idUsuario: int

    model_config = ConfigDict(from_attributes=True)
