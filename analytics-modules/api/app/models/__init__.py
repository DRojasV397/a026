"""
Módulo de modelos DAO (Database Access Objects).
Contiene las definiciones de las tablas de la base de datos.
"""

from .usuario import Usuario, Rol, UsuarioRol, PreferenciaUsuario
from .producto import Categoria, Producto
from .venta import Venta, DetalleVenta
from .compra import Compra, DetalleCompra
from .prediccion import Modelo, VersionModelo, Prediccion, Escenario, ParametroEscenario, ResultadoEscenario
from .rentabilidad import Rentabilidad, ResultadoFinanciero, Reporte, Alerta
from .historial_carga import HistorialCarga

__all__ = [
    'Usuario', 'Rol', 'UsuarioRol', 'PreferenciaUsuario',
    'Categoria', 'Producto',
    'Venta', 'DetalleVenta',
    'Compra', 'DetalleCompra',
    'Modelo', 'VersionModelo', 'Prediccion', 'Escenario', 'ParametroEscenario', 'ResultadoEscenario',
    'Rentabilidad', 'ResultadoFinanciero', 'Reporte', 'Alerta',
    'HistorialCarga'
]
