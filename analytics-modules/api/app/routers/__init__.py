"""
Modulo de routers (endpoints) de la API.
"""

from .auth import router as auth_router
from .usuarios import router as usuarios_router, rol_router
from .productos import router as productos_router, categoria_router
from .data import router as data_router
from .ventas import router as ventas_router
from .compras import router as compras_router
from .predictions import router as predictions_router
from .profitability import router as profitability_router
from .simulations import router as simulations_router
from .alerts import router as alerts_router
from .dashboard import router as dashboard_router

__all__ = [
    'auth_router',
    'usuarios_router',
    'rol_router',
    'productos_router',
    'categoria_router',
    'data_router',
    'ventas_router',
    'compras_router',
    'predictions_router',
    'profitability_router',
    'simulations_router',
    'alerts_router',
    'dashboard_router'
]
