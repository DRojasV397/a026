"""
Servicio de autenticacion y seguridad.
Maneja hash de contrasenas, generacion y verificacion de tokens JWT.
"""

from datetime import datetime, timedelta
from typing import Optional, Dict, Any
from jose import JWTError, jwt
import bcrypt
from sqlalchemy.orm import Session
import logging

from app.config import settings
from app.models import Usuario, Rol, UsuarioRol
from app.repositories import UsuarioRepository, RolRepository
from app.schemas.auth import TokenData, UserInfo

logger = logging.getLogger(__name__)


class AuthService:
    """Servicio de autenticacion."""

    def __init__(self, db: Session):
        self.db = db
        self.usuario_repo = UsuarioRepository(db)
        self.rol_repo = RolRepository(db)

    # =====================
    # Hash de Contrasenas
    # =====================

    @staticmethod
    def hash_password(password: str) -> str:
        """
        Genera hash de una contrasena.

        Args:
            password: Contrasena en texto plano

        Returns:
            str: Hash de la contrasena
        """
        password_bytes = password.encode('utf-8')
        salt = bcrypt.gensalt()
        hashed = bcrypt.hashpw(password_bytes, salt)
        return hashed.decode('utf-8')

    @staticmethod
    def verify_password(plain_password: str, hashed_password: str) -> bool:
        """
        Verifica si una contrasena coincide con su hash.

        Args:
            plain_password: Contrasena en texto plano
            hashed_password: Hash almacenado

        Returns:
            bool: True si coinciden
        """
        try:
            password_bytes = plain_password.encode('utf-8')
            hashed_bytes = hashed_password.encode('utf-8')
            return bcrypt.checkpw(password_bytes, hashed_bytes)
        except Exception as e:
            logger.error(f"Error al verificar contrasena: {str(e)}")
            return False

    # =====================
    # Generacion de Tokens
    # =====================

    @staticmethod
    def create_access_token(
        data: Dict[str, Any],
        expires_delta: Optional[timedelta] = None
    ) -> str:
        """
        Crea un token de acceso JWT.

        Args:
            data: Datos a incluir en el token
            expires_delta: Tiempo de expiracion personalizado

        Returns:
            str: Token JWT codificado
        """
        to_encode = data.copy()

        if expires_delta:
            expire = datetime.utcnow() + expires_delta
        else:
            expire = datetime.utcnow() + timedelta(
                minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES
            )

        to_encode.update({
            "exp": expire,
            "iat": datetime.utcnow(),
            "type": "access"
        })

        encoded_jwt = jwt.encode(
            to_encode,
            settings.SECRET_KEY,
            algorithm=settings.ALGORITHM
        )
        return encoded_jwt

    @staticmethod
    def create_refresh_token(
        data: Dict[str, Any],
        expires_delta: Optional[timedelta] = None
    ) -> str:
        """
        Crea un token de refresco JWT.

        Args:
            data: Datos a incluir en el token
            expires_delta: Tiempo de expiracion personalizado

        Returns:
            str: Token JWT codificado
        """
        to_encode = data.copy()

        if expires_delta:
            expire = datetime.utcnow() + expires_delta
        else:
            expire = datetime.utcnow() + timedelta(
                days=settings.REFRESH_TOKEN_EXPIRE_DAYS
            )

        to_encode.update({
            "exp": expire,
            "iat": datetime.utcnow(),
            "type": "refresh"
        })

        encoded_jwt = jwt.encode(
            to_encode,
            settings.SECRET_KEY,
            algorithm=settings.ALGORITHM
        )
        return encoded_jwt

    @staticmethod
    def decode_token(token: str) -> Optional[Dict[str, Any]]:
        """
        Decodifica y valida un token JWT.

        Args:
            token: Token JWT a decodificar

        Returns:
            Optional[Dict]: Payload del token o None si es invalido
        """
        try:
            payload = jwt.decode(
                token,
                settings.SECRET_KEY,
                algorithms=[settings.ALGORITHM]
            )
            return payload
        except JWTError as e:
            logger.warning(f"Error al decodificar token: {str(e)}")
            return None

    # =====================
    # Autenticacion
    # =====================

    def authenticate_user(
        self, username: str, password: str
    ) -> Optional[Usuario]:
        """
        Autentica un usuario por nombre de usuario/email y contrasena.

        Args:
            username: Nombre de usuario o email
            password: Contrasena en texto plano

        Returns:
            Optional[Usuario]: Usuario autenticado o None
        """
        # Buscar por nombre de usuario o email
        user = self.usuario_repo.get_by_username(username)
        if not user:
            user = self.usuario_repo.get_by_email(username)

        if not user:
            logger.info(f"Usuario no encontrado: {username}")
            return None

        # Verificar que el usuario este activo
        if user.estado and user.estado.lower() != 'activo':
            logger.info(f"Usuario inactivo: {username}")
            return None

        # Verificar contrasena
        if not self.verify_password(password, user.hashPassword):
            logger.info(f"Contrasena incorrecta para: {username}")
            return None

        logger.info(f"Usuario autenticado: {username}")
        return user

    def get_user_roles(self, user_id: int) -> list:
        """
        Obtiene los roles de un usuario.

        Args:
            user_id: ID del usuario

        Returns:
            list: Lista de nombres de roles
        """
        try:
            roles = self.db.query(Rol).join(
                UsuarioRol, Rol.idRol == UsuarioRol.idRol
            ).filter(
                UsuarioRol.idUsuario == user_id
            ).all()

            return [rol.nombre for rol in roles]
        except Exception as e:
            logger.error(f"Error al obtener roles: {str(e)}")
            return []

    def get_user_info(self, user: Usuario) -> UserInfo:
        """
        Obtiene informacion del usuario para respuesta.

        Args:
            user: Objeto Usuario

        Returns:
            UserInfo: Informacion del usuario
        """
        roles = self.get_user_roles(user.idUsuario)

        return UserInfo(
            idUsuario=user.idUsuario,
            nombreCompleto=user.nombreCompleto,
            nombreUsuario=user.nombreUsuario,
            email=user.email,
            roles=roles
        )

    def login(self, username: str, password: str) -> Optional[Dict[str, Any]]:
        """
        Realiza el proceso de login completo.

        Args:
            username: Nombre de usuario o email
            password: Contrasena

        Returns:
            Optional[Dict]: Tokens y datos de usuario o None
        """
        user = self.authenticate_user(username, password)
        if not user:
            return None

        # Obtener roles
        roles = self.get_user_roles(user.idUsuario)

        # Crear payload para tokens
        token_data = {
            "sub": user.nombreUsuario,
            "idUsuario": user.idUsuario,
            "nombreUsuario": user.nombreUsuario,
            "roles": roles
        }

        # Crear tokens
        access_token = self.create_access_token(token_data)
        refresh_token = self.create_refresh_token({"sub": user.nombreUsuario})

        return {
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "user": self.get_user_info(user)
        }

    def refresh_access_token(self, refresh_token: str) -> Optional[Dict[str, Any]]:
        """
        Refresca el token de acceso usando un refresh token.

        Args:
            refresh_token: Token de refresco

        Returns:
            Optional[Dict]: Nuevo access token o None
        """
        payload = self.decode_token(refresh_token)
        if not payload:
            return None

        # Verificar que sea un refresh token
        if payload.get("type") != "refresh":
            logger.warning("Token no es de tipo refresh")
            return None

        # Obtener usuario
        username = payload.get("sub")
        user = self.usuario_repo.get_by_username(username)
        if not user:
            return None

        # Verificar que el usuario siga activo
        if user.estado and user.estado.lower() != 'activo':
            return None

        # Crear nuevo access token
        roles = self.get_user_roles(user.idUsuario)
        token_data = {
            "sub": user.nombreUsuario,
            "idUsuario": user.idUsuario,
            "nombreUsuario": user.nombreUsuario,
            "roles": roles
        }

        access_token = self.create_access_token(token_data)

        return {
            "access_token": access_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60
        }

    def verify_token(self, token: str) -> Optional[TokenData]:
        """
        Verifica un token y retorna sus datos.

        Args:
            token: Token JWT

        Returns:
            Optional[TokenData]: Datos del token o None
        """
        payload = self.decode_token(token)
        if not payload:
            return None

        try:
            return TokenData(
                sub=payload.get("sub"),
                idUsuario=payload.get("idUsuario"),
                nombreUsuario=payload.get("nombreUsuario"),
                roles=payload.get("roles", []),
                exp=datetime.fromtimestamp(payload.get("exp")) if payload.get("exp") else None,
                iat=datetime.fromtimestamp(payload.get("iat")) if payload.get("iat") else None
            )
        except Exception as e:
            logger.error(f"Error al parsear token data: {str(e)}")
            return None

    # =====================
    # Registro de Usuario
    # =====================

    def register_user(
        self,
        nombre_completo: str,
        nombre_usuario: str,
        email: str,
        password: str,
        rol_default: str = "Operativo"
    ) -> Optional[Usuario]:
        """
        Registra un nuevo usuario.

        Args:
            nombre_completo: Nombre completo del usuario
            nombre_usuario: Nombre de usuario (login)
            email: Email del usuario
            password: Contrasena en texto plano
            rol_default: Rol por defecto a asignar

        Returns:
            Optional[Usuario]: Usuario creado o None si hay error
        """
        # Verificar que no exista el usuario
        if self.usuario_repo.get_by_username(nombre_usuario):
            logger.warning(f"Nombre de usuario ya existe: {nombre_usuario}")
            return None

        if self.usuario_repo.get_by_email(email):
            logger.warning(f"Email ya registrado: {email}")
            return None

        try:
            # Crear usuario
            user = Usuario(
                nombreCompleto=nombre_completo,
                nombreUsuario=nombre_usuario,
                email=email,
                hashPassword=self.hash_password(password),
                estado='Activo'
            )

            self.db.add(user)
            self.db.commit()
            self.db.refresh(user)

            # Asignar rol por defecto si existe
            rol = self.rol_repo.get_by_nombre(rol_default)
            if rol:
                usuario_rol = UsuarioRol(
                    idUsuario=user.idUsuario,
                    idRol=rol.idRol
                )
                self.db.add(usuario_rol)
                self.db.commit()

            logger.info(f"Usuario registrado: {nombre_usuario}")
            return user

        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al registrar usuario: {str(e)}")
            return None

    def change_password(
        self,
        user_id: int,
        current_password: str,
        new_password: str
    ) -> bool:
        """
        Cambia la contrasena de un usuario.

        Args:
            user_id: ID del usuario
            current_password: Contrasena actual
            new_password: Nueva contrasena

        Returns:
            bool: True si se cambio exitosamente
        """
        user = self.usuario_repo.get_by_id(user_id)
        if not user:
            return False

        # Verificar contrasena actual
        if not self.verify_password(current_password, user.hashPassword):
            logger.warning(f"Contrasena actual incorrecta para usuario {user_id}")
            return False

        try:
            user.hashPassword = self.hash_password(new_password)
            self.db.commit()
            logger.info(f"Contrasena cambiada para usuario {user_id}")
            return True
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al cambiar contrasena: {str(e)}")
            return False
