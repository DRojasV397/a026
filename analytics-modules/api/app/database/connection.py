"""
Gestor de conexiones a la base de datos SQL Server.
Implementa patrón Singleton y manejo de pool de conexiones.
"""

import pyodbc
import urllib
from sqlalchemy import create_engine, event, text
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session
from typing import Generator
import logging
from contextlib import contextmanager

from app.config import settings

# Configurar logging
logger = logging.getLogger(__name__)

# Base para modelos SQLAlchemy
Base = declarative_base()


class DatabaseManager:
    """
    Gestor centralizado de conexiones a la base de datos.
    Implementa patrón Singleton para gestión eficiente de recursos.
    """

    _instance = None
    _engine = None
    _session_factory = None

    def __new__(cls):
        """Implementación del patrón Singleton."""
        if cls._instance is None:
            cls._instance = super(DatabaseManager, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        """Inicializa el gestor de base de datos."""
        if self._engine is None:
            self._initialize_engine()

    def _get_connection_string(self) -> str:
        """
        Construye la cadena de conexión para SQL Server.

        Returns:
            str: Cadena de conexión URL-encoded para SQLAlchemy
        """
        # Construcción de la cadena de conexión ODBC con autenticación de Windows
        # No especificamos puerto para usar Named Pipes (más confiable en Windows)
        connection_params = (
            f"DRIVER={{{settings.DB_DRIVER}}};"
            f"SERVER={settings.DB_SERVER};"
            f"DATABASE={settings.DB_NAME};"
            "Trusted_Connection=yes;"
            "TrustServerCertificate=yes;"
        )

        # URL encode para SQLAlchemy
        connection_string = f"mssql+pyodbc:///?odbc_connect={urllib.parse.quote_plus(connection_params)}"

        return connection_string

    def _initialize_engine(self):
        """Inicializa el engine de SQLAlchemy con configuración de pool."""
        try:
            connection_string = self._get_connection_string()

            self._engine = create_engine(
                connection_string,
                pool_size=settings.DB_POOL_SIZE,
                max_overflow=settings.DB_MAX_OVERFLOW,
                pool_timeout=settings.DB_POOL_TIMEOUT,
                pool_recycle=settings.DB_POOL_RECYCLE,
                pool_pre_ping=True,  # Verifica conexión antes de usar
                echo=settings.DEBUG,  # Muestra SQL en desarrollo
            )

            # Crear SessionMaker
            self._session_factory = sessionmaker(
                autocommit=False,
                autoflush=False,
                bind=self._engine
            )

            # Event listener para configuración de sesión
            @event.listens_for(self._engine, "connect")
            def receive_connect(dbapi_conn, connection_record):
                """Configura la conexión al establecerse."""
                logger.debug("Nueva conexión establecida con la base de datos")

            logger.info("Database engine inicializado correctamente")

        except Exception as e:
            logger.error(f"Error al inicializar el engine de base de datos: {e}")
            raise

    @property
    def engine(self):
        """Obtiene el engine de SQLAlchemy."""
        return self._engine

    @property
    def session_factory(self):
        """Obtiene el session factory."""
        return self._session_factory

    def get_session(self) -> Session:
        """
        Crea una nueva sesión de base de datos.

        Returns:
            Session: Nueva sesión de SQLAlchemy
        """
        if self._session_factory is None:
            raise RuntimeError("Database no inicializado")
        return self._session_factory()

    @contextmanager
    def get_session_context(self) -> Generator[Session, None, None]:
        """
        Context manager para manejo automático de sesiones.

        Yields:
            Session: Sesión de base de datos

        Example:
            with db_manager.get_session_context() as session:
                result = session.query(Usuario).all()
        """
        session = self.get_session()
        try:
            yield session
            session.commit()
        except Exception as e:
            session.rollback()
            logger.error(f"Error en transacción de base de datos: {e}")
            raise
        finally:
            session.close()

    def test_connection(self) -> bool:
        """
        Prueba la conexión a la base de datos.

        Returns:
            bool: True si la conexión es exitosa, False en caso contrario
        """
        try:
            with self.get_session_context() as session:
                session.execute(text("SELECT 1"))
            logger.info("Conexión a base de datos exitosa")
            return True
        except Exception as e:
            logger.error(f"Error al probar conexión: {e}")
            return False

    def create_tables(self):
        """Crea todas las tablas definidas en los modelos."""
        try:
            Base.metadata.create_all(bind=self._engine)
            logger.info("Tablas creadas/verificadas correctamente")
        except Exception as e:
            logger.error(f"Error al crear tablas: {e}")
            raise

    def drop_tables(self):
        """Elimina todas las tablas (usar con precaución)."""
        try:
            Base.metadata.drop_all(bind=self._engine)
            logger.warning("Todas las tablas han sido eliminadas")
        except Exception as e:
            logger.error(f"Error al eliminar tablas: {e}")
            raise

    def close(self):
        """Cierra todas las conexiones del pool."""
        if self._engine:
            self._engine.dispose()
            logger.info("Conexiones de base de datos cerradas")


# Instancia global del gestor de base de datos
db_manager = DatabaseManager()


def get_db() -> Generator[Session, None, None]:
    """
    Dependency para FastAPI que proporciona una sesión de base de datos.

    Yields:
        Session: Sesión de base de datos

    Example:
        @app.get("/users")
        def get_users(db: Session = Depends(get_db)):
            return db.query(Usuario).all()
    """
    session = db_manager.get_session()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
