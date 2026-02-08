"""
Modulo de repositorios para acceso a datos.
"""

from .base_repository import BaseRepository

# Repositorios de usuario
from .usuario_repository import UsuarioRepository, RolRepository, PreferenciaUsuarioRepository

# Repositorios de producto
from .producto_repository import ProductoRepository, CategoriaRepository

# Repositorios de venta
from .venta_repository import VentaRepository, DetalleVentaRepository

# Repositorios de compra
from .compra_repository import CompraRepository, DetalleCompraRepository

# Repositorios de modelos predictivos
from .modelo_repository import ModeloRepository, VersionModeloRepository

# Repositorio de predicciones
from .prediccion_repository import PrediccionRepository

# Repositorios de escenarios
from .escenario_repository import (
    EscenarioRepository,
    ParametroEscenarioRepository,
    ResultadoEscenarioRepository
)

# Repositorios de rentabilidad
from .rentabilidad_repository import RentabilidadRepository, ResultadoFinancieroRepository

# Repositorio de alertas
from .alerta_repository import AlertaRepository

__all__ = [
    # Base
    'BaseRepository',

    # Usuario
    'UsuarioRepository',
    'RolRepository',
    'PreferenciaUsuarioRepository',

    # Producto
    'ProductoRepository',
    'CategoriaRepository',

    # Venta
    'VentaRepository',
    'DetalleVentaRepository',

    # Compra
    'CompraRepository',
    'DetalleCompraRepository',

    # Modelo Predictivo
    'ModeloRepository',
    'VersionModeloRepository',

    # Prediccion
    'PrediccionRepository',

    # Escenario
    'EscenarioRepository',
    'ParametroEscenarioRepository',
    'ResultadoEscenarioRepository',

    # Rentabilidad
    'RentabilidadRepository',
    'ResultadoFinancieroRepository',

    # Alerta
    'AlertaRepository'
]
