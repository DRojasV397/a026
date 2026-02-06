"""
Archivo principal de la aplicación FastAPI.
Punto de entrada de la API.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging

from app.config import settings
from app.database import db_manager
from app.routers import (
    auth_router, usuarios_router, rol_router, productos_router,
    categoria_router, data_router, ventas_router, compras_router,
    predictions_router, profitability_router, simulations_router,
    alerts_router, dashboard_router
)

# Configuración de logging
logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Gestor del ciclo de vida de la aplicación.
    Se ejecuta al iniciar y al cerrar la aplicación.
    """
    # Startup
    logger.info("Iniciando aplicación...")
    try:
        # Inicializar conexión a base de datos
        if db_manager.test_connection():
            logger.info("Conexión a base de datos exitosa")
        else:
            logger.warning("No se pudo conectar a la base de datos")
    except Exception as e:
        logger.error(f"Error al inicializar la aplicación: {str(e)}")

    yield

    # Shutdown
    logger.info("Cerrando aplicación...")


# Crear aplicación FastAPI
app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="API para Sistema de Business Intelligence Predictiva",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc"
)

# Configurar CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
def read_root():
    """Endpoint raíz de la API."""
    return {
        "name": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "status": "running",
        "docs": "/docs",
        "redoc": "/redoc"
    }


@app.get("/health")
def health_check():
    """Endpoint de health check."""
    db_status = "connected" if db_manager.test_connection() else "disconnected"
    return {
        "status": "healthy",
        "database": db_status
    }


# Registrar routers
api_prefix = settings.API_PREFIX

# Router de autenticacion
app.include_router(auth_router, prefix=api_prefix)

# Routers de entidades
app.include_router(usuarios_router, prefix=api_prefix)
app.include_router(rol_router, prefix=api_prefix)
app.include_router(productos_router, prefix=api_prefix)
app.include_router(categoria_router, prefix=api_prefix)

# Router de gestion de datos
app.include_router(data_router, prefix=api_prefix)

# Routers de ventas y compras
app.include_router(ventas_router, prefix=api_prefix)
app.include_router(compras_router, prefix=api_prefix)

# Router de predicciones (RF-02)
app.include_router(predictions_router, prefix=api_prefix)

# Router de rentabilidad (RF-06)
app.include_router(profitability_router, prefix=api_prefix)

# Router de simulacion (RF-05)
app.include_router(simulations_router, prefix=api_prefix)

# Router de alertas (RF-04)
app.include_router(alerts_router, prefix=api_prefix)

# Router de dashboard y reportes (RF-07)
app.include_router(dashboard_router, prefix=api_prefix)


# Manejador global de excepciones
@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """Manejador global de excepciones."""
    logger.error(f"Error no manejado: {str(exc)}")
    return {
        "detail": "Error interno del servidor",
        "message": "Ha ocurrido un error"
    }


# Punto de entrada
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=settings.DEBUG
    )
