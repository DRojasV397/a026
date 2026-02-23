"""
Configuración centralizada de la aplicación.
Maneja variables de entorno y configuraciones globales.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import field_validator
from typing import Optional, Union
from functools import lru_cache
from pathlib import Path

# Ruta absoluta al .env, siempre relativa a este archivo (config/settings.py → api/.env)
_ENV_FILE = Path(__file__).parent.parent.parent / ".env"


class Settings(BaseSettings):
    """
    Clase de configuración usando Pydantic Settings.
    Lee variables de entorno automáticamente.
    """

    # Información de la aplicación
    APP_NAME: str = "Sistema BI - API"
    APP_VERSION: str = "1.0.0"
    API_PREFIX: str = "/api/v1"

    # Configuración de base de datos SQL Server
    DB_SERVER: str = "localhost"
    DB_PORT: int = 1433
    DB_NAME: str = "TTA026"
    DB_USER: str = "sa"
    DB_PASSWORD: str = ""
    DB_DRIVER: str = "ODBC Driver 17 for SQL Server"

    # Configuración de conexión
    DB_POOL_SIZE: int = 5
    DB_MAX_OVERFLOW: int = 10
    DB_POOL_TIMEOUT: int = 30
    DB_POOL_RECYCLE: int = 3600  # 1 hora

    # Configuración de seguridad JWT
    SECRET_KEY: str = "your-secret-key-here-change-in-production"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7

    # Configuración CORS
    ALLOWED_ORIGINS: Union[list, str] = [
        "http://localhost:3000",
        "http://localhost:8080",
        "http://localhost:8000",
    ]

    @field_validator('ALLOWED_ORIGINS', mode='before')
    @classmethod
    def parse_allowed_origins(cls, v):
        """Parsea ALLOWED_ORIGINS desde string separado por comas o lista."""
        if isinstance(v, str):
            return [origin.strip() for origin in v.split(',') if origin.strip()]
        return v

    # Configuración de logging
    LOG_LEVEL: str = "INFO"

    # Configuración de entorno
    ENVIRONMENT: str = "development"  # development, staging, production
    DEBUG: bool = True

    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE),
        env_file_encoding="utf-8",
        case_sensitive=True
    )


@lru_cache()
def get_settings() -> Settings:
    """
    Obtiene la configuración de la aplicación.
    Usa cache para evitar múltiples instancias.

    Returns:
        Settings: Instancia única de configuración
    """
    return Settings()


# Instancia global de configuración
settings = get_settings()
