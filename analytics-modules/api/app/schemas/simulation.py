"""
Esquemas DTO (Pydantic) para el modulo de Simulacion de Escenarios.
"""

from pydantic import BaseModel, Field, ConfigDict, field_validator
from typing import Optional, List, Dict
from decimal import Decimal
from datetime import datetime
from enum import Enum


class EstadoEscenario(str, Enum):
    """Estados posibles de un escenario."""
    ACTIVO = "Activo"
    EJECUTADO = "Ejecutado"
    ARCHIVADO = "Archivado"


class TipoParametro(str, Enum):
    """Tipos de parametros de escenario."""
    PRECIO = "precio"
    COSTO = "costo"
    DEMANDA = "demanda"
    PORCENTAJE = "porcentaje"


# Esquemas de Escenario
class EscenarioBase(BaseModel):
    """Esquema base de Escenario."""
    nombre: str = Field(..., min_length=3, max_length=120)
    descripcion: Optional[str] = None
    idVersion: Optional[int] = None


class EscenarioCreate(EscenarioBase):
    """Esquema para crear un Escenario."""
    creadoPor: Optional[int] = None
    parametros: Optional[List["ParametroEscenarioCreate"]] = []


class EscenarioUpdate(BaseModel):
    """Esquema para actualizar un Escenario."""
    nombre: Optional[str] = None
    descripcion: Optional[str] = None
    estado: Optional[str] = None


class EscenarioResponse(EscenarioBase):
    """Esquema de respuesta de Escenario."""
    idEscenario: int
    creadoPor: Optional[int] = None
    fechaCreacion: Optional[datetime] = None
    estado: str

    model_config = ConfigDict(from_attributes=True)


# Esquemas de Parametro de Escenario
class ParametroEscenarioBase(BaseModel):
    """Esquema base de Parametro de Escenario."""
    parametro: str = Field(..., max_length=60)
    valor: Optional[str] = None
    tipoValor: Optional[str] = None


class ParametroEscenarioCreate(ParametroEscenarioBase):
    """Esquema para crear un Parametro de Escenario."""
    pass


class ParametroEscenarioResponse(ParametroEscenarioBase):
    """Esquema de respuesta de Parametro de Escenario."""
    idEscenario: int

    model_config = ConfigDict(from_attributes=True)


# Esquemas de Resultado de Escenario
class ResultadoEscenarioBase(BaseModel):
    """Esquema base de Resultado de Escenario."""
    idEscenario: int
    periodo: str
    indicador: str
    valor: Optional[Decimal] = None


class ResultadoEscenarioCreate(ResultadoEscenarioBase):
    """Esquema para crear un Resultado de Escenario."""
    pass


class ResultadoEscenarioResponse(ResultadoEscenarioBase):
    """Esquema de respuesta de Resultado de Escenario."""
    idResultado: int
    fechaCalculo: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


# Request para Crear Escenario
class CrearEscenarioRequest(BaseModel):
    """Request para crear un nuevo escenario."""
    nombre: str = Field(..., min_length=3, max_length=120)
    descripcion: Optional[str] = None
    basadoEnHistorico: bool = Field(default=True, description="Usar datos historicos como base")
    periodos: int = Field(default=6, ge=1, le=12, description="Periodos a simular")


class CrearEscenarioResponse(BaseModel):
    """Respuesta de creacion de escenario."""
    idEscenario: int
    nombre: str
    mensaje: str = "Escenario creado exitosamente"


# Modificar Parametros
class ModificarParametroItem(BaseModel):
    """Item para modificar un parametro."""
    parametro: str
    valor: str
    tipoValor: TipoParametro

    @field_validator('valor')
    @classmethod
    def validar_variacion(cls, v, info):
        """Valida que la variacion no exceda +/- 50%."""
        try:
            valor_num = float(v.replace('%', ''))
            if abs(valor_num) > 50:
                raise ValueError("La variacion no puede exceder +/- 50%")
        except ValueError:
            pass  # No es numerico, validacion no aplica
        return v


class ModificarParametrosRequest(BaseModel):
    """Request para modificar parametros de escenario."""
    parametros: List[ModificarParametroItem] = Field(..., min_length=1)


class ModificarParametrosResponse(BaseModel):
    """Respuesta de modificacion de parametros."""
    idEscenario: int
    parametrosModificados: int
    mensaje: str = "Parametros actualizados"


# Ejecutar Simulacion
class EjecutarSimulacionRequest(BaseModel):
    """Request para ejecutar simulacion."""
    guardarResultados: bool = Field(default=True)


class ResultadoSimulacionItem(BaseModel):
    """Item de resultado de simulacion."""
    periodo: str
    indicador: str
    valorBase: Decimal
    valorSimulado: Decimal
    diferencia: Decimal
    porcentajeCambio: Decimal


class EjecutarSimulacionResponse(BaseModel):
    """Respuesta de ejecucion de simulacion."""
    idEscenario: int
    nombre: str
    resultados: List[ResultadoSimulacionItem]
    resumen: Dict[str, Decimal]
    fechaEjecucion: datetime = Field(default_factory=datetime.now)
    advertencia: str = Field(
        default="Los resultados son de caracter informativo y no constituyen predicciones garantizadas."
    )


# Comparar Escenarios
class CompararEscenariosRequest(BaseModel):
    """Request para comparar escenarios."""
    escenario_ids: List[int] = Field(..., min_length=2, max_length=5, description="IDs de escenarios a comparar")


class ComparacionIndicador(BaseModel):
    """Comparacion de un indicador entre escenarios."""
    indicador: str
    periodo: str
    valores: Dict[int, Decimal] = Field(..., description="idEscenario -> valor")
    mejorEscenario: int
    peorEscenario: int


class CompararEscenariosResponse(BaseModel):
    """Respuesta de comparacion de escenarios."""
    escenarios: List[EscenarioResponse]
    comparaciones: List[ComparacionIndicador]
    resumen: Dict[str, Dict[int, Decimal]]


# Escenario Completo
class EscenarioCompleto(EscenarioResponse):
    """Escenario con parametros y resultados."""
    parametros: List[ParametroEscenarioResponse] = []
    resultados: List[ResultadoEscenarioResponse] = []


# Actualizar referencia forward
EscenarioCreate.model_rebuild()
