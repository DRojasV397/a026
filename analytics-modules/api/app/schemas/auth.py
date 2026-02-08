"""
Esquemas DTO para autenticacion y seguridad (JWT).
"""

from pydantic import BaseModel, EmailStr, Field, ConfigDict
from typing import Optional, List
from datetime import datetime


# Esquemas de Login
class LoginRequest(BaseModel):
    """Request para inicio de sesion."""
    username: str = Field(..., description="Nombre de usuario o email")
    password: str = Field(..., min_length=6, description="Contrasena")


class LoginResponse(BaseModel):
    """Respuesta de inicio de sesion exitoso."""
    access_token: str
    refresh_token: Optional[str] = None
    token_type: str = "bearer"
    expires_in: int = Field(..., description="Tiempo de expiracion en segundos")
    user: "UserInfo"


class UserInfo(BaseModel):
    """Informacion basica del usuario autenticado."""
    idUsuario: int
    nombreCompleto: str
    nombreUsuario: str
    email: str
    roles: List[str] = []

    model_config = ConfigDict(from_attributes=True)


# Esquemas de Registro
class RegisterRequest(BaseModel):
    """Request para registro de usuario."""
    nombreCompleto: str = Field(..., min_length=3, max_length=120)
    nombreUsuario: str = Field(..., min_length=3, max_length=60)
    email: EmailStr
    password: str = Field(..., min_length=8, description="Minimo 8 caracteres")
    confirmPassword: str = Field(..., description="Confirmacion de contrasena")


class RegisterResponse(BaseModel):
    """Respuesta de registro exitoso."""
    message: str = "Usuario registrado exitosamente"
    idUsuario: int
    nombreUsuario: str


# Esquemas de Token
class TokenData(BaseModel):
    """Datos contenidos en el token JWT."""
    sub: str  # Subject (username o user_id)
    idUsuario: int
    nombreUsuario: str
    roles: List[str] = []
    exp: Optional[datetime] = None
    iat: Optional[datetime] = None


class TokenVerifyRequest(BaseModel):
    """Request para verificar token."""
    token: str


class TokenVerifyResponse(BaseModel):
    """Respuesta de verificacion de token."""
    valid: bool
    user: Optional[UserInfo] = None
    message: Optional[str] = None


class RefreshTokenRequest(BaseModel):
    """Request para refrescar token."""
    refresh_token: str


class RefreshTokenResponse(BaseModel):
    """Respuesta de refresh token."""
    access_token: str
    token_type: str = "bearer"
    expires_in: int


# Esquemas de Cambio de Contrasena
class ChangePasswordRequest(BaseModel):
    """Request para cambiar contrasena."""
    current_password: str = Field(..., description="Contrasena actual")
    new_password: str = Field(..., min_length=8, description="Nueva contrasena")
    confirm_password: str = Field(..., description="Confirmacion de nueva contrasena")


class ChangePasswordResponse(BaseModel):
    """Respuesta de cambio de contrasena."""
    message: str = "Contrasena actualizada exitosamente"
    success: bool = True


# Esquemas de Recuperacion de Contrasena
class ForgotPasswordRequest(BaseModel):
    """Request para recuperar contrasena."""
    email: EmailStr


class ForgotPasswordResponse(BaseModel):
    """Respuesta de solicitud de recuperacion."""
    message: str = "Si el email existe, se enviara un enlace de recuperacion"


class ResetPasswordRequest(BaseModel):
    """Request para resetear contrasena."""
    token: str
    new_password: str = Field(..., min_length=8)
    confirm_password: str


class ResetPasswordResponse(BaseModel):
    """Respuesta de reseteo de contrasena."""
    message: str = "Contrasena restablecida exitosamente"
    success: bool = True


# Actualizar referencia forward
LoginResponse.model_rebuild()
