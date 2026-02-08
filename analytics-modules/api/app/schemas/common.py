"""
Esquemas DTO comunes para paginacion y respuestas estandar.
"""

from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, Generic, TypeVar, List
from datetime import datetime

# TypeVar para respuestas genericas
T = TypeVar('T')


class PaginationParams(BaseModel):
    """Parametros de paginacion."""
    page: int = Field(default=1, ge=1, description="Numero de pagina")
    page_size: int = Field(default=20, ge=1, le=100, description="Elementos por pagina")

    @property
    def skip(self) -> int:
        """Calcula el offset para la consulta."""
        return (self.page - 1) * self.page_size

    @property
    def limit(self) -> int:
        """Retorna el limite de elementos."""
        return self.page_size


class PaginatedResponse(BaseModel, Generic[T]):
    """Respuesta paginada generica."""
    items: List[T]
    total: int = Field(..., description="Total de elementos")
    page: int = Field(..., description="Pagina actual")
    page_size: int = Field(..., description="Elementos por pagina")
    pages: int = Field(..., description="Total de paginas")

    model_config = ConfigDict(from_attributes=True)


class MessageResponse(BaseModel):
    """Respuesta simple con mensaje."""
    message: str
    success: bool = True


class ErrorResponse(BaseModel):
    """Respuesta de error estandar."""
    detail: str
    code: Optional[str] = None
    timestamp: datetime = Field(default_factory=datetime.now)


class SuccessResponse(BaseModel):
    """Respuesta de exito generica."""
    success: bool = True
    message: str = "Operacion exitosa"
    data: Optional[dict] = None


class DateRangeFilter(BaseModel):
    """Filtro por rango de fechas."""
    fecha_inicio: Optional[datetime] = None
    fecha_fin: Optional[datetime] = None


class IdListRequest(BaseModel):
    """Request con lista de IDs."""
    ids: List[int] = Field(..., min_length=1, description="Lista de IDs")


class StatusUpdate(BaseModel):
    """Actualizacion de estado."""
    estado: str = Field(..., description="Nuevo estado")
