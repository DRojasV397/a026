"""
Esquemas DTO para carga y procesamiento de datos.
"""

from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, List, Dict, Any
from datetime import datetime
from enum import Enum


class DataType(str, Enum):
    """Tipos de datos para carga."""
    VENTAS = "ventas"
    COMPRAS = "compras"
    PRODUCTOS = "productos"
    INVENTARIO = "inventario"


class UploadStatus(str, Enum):
    """Estados de carga de datos."""
    PENDING = "pending"
    VALIDATING = "validating"
    CLEANING = "cleaning"
    READY = "ready"
    CONFIRMED = "confirmed"
    ERROR = "error"


# Respuesta de upload
class UploadResponse(BaseModel):
    """Respuesta de carga de archivo."""
    upload_id: str
    filename: str
    file_type: str
    total_rows: int
    status: UploadStatus
    message: str
    column_info: Dict[str, Dict] = Field(default_factory=dict)


# Validacion de estructura
class ColumnValidation(BaseModel):
    """Validacion de una columna."""
    name: str
    found: bool
    suggested_mapping: Optional[str] = None
    data_type: Optional[str] = None
    null_count: int = 0
    null_percentage: float = 0


class ValidateRequest(BaseModel):
    """Request para validar estructura."""
    upload_id: str
    data_type: DataType
    column_mappings: Optional[Dict[str, str]] = None


class ValidateResponse(BaseModel):
    """Respuesta de validacion."""
    upload_id: str
    valid: bool
    data_type: DataType
    columns: List[ColumnValidation]
    missing_required: List[str] = []
    warnings: List[str] = []
    errors: List[str] = []


# Preview de datos
class PreviewRequest(BaseModel):
    """Request para preview de datos."""
    upload_id: str
    rows: int = Field(default=10, ge=1, le=100)


class PreviewResponse(BaseModel):
    """Respuesta de preview."""
    upload_id: str
    total_rows: int
    preview_rows: int
    columns: List[str]
    data: List[Dict[str, Any]]


# Limpieza de datos
class CleaningOptions(BaseModel):
    """Opciones de limpieza de datos."""
    remove_duplicates: bool = True
    handle_nulls: bool = True
    null_strategy: str = Field(default="drop", description="drop, fill_mean, fill_median, fill_zero")
    detect_outliers: bool = True
    outlier_threshold: float = Field(default=3.0, description="Z-score threshold")
    normalize_text: bool = True


class CleanRequest(BaseModel):
    """Request para limpieza de datos."""
    upload_id: str
    options: CleaningOptions = Field(default_factory=CleaningOptions)


class CleaningResult(BaseModel):
    """Resultado de limpieza."""
    original_rows: int
    cleaned_rows: int
    removed_rows: int
    duplicates_removed: int
    nulls_handled: int
    outliers_detected: int
    quality_score: float = Field(..., ge=0, le=100)
    warnings: List[str] = []


class CleanResponse(BaseModel):
    """Respuesta de limpieza."""
    upload_id: str
    status: UploadStatus
    result: CleaningResult
    message: str


# Confirmacion de carga
class ConfirmRequest(BaseModel):
    """Request para confirmar carga."""
    upload_id: str
    data_type: DataType
    column_mappings: Dict[str, str]


class ConfirmResponse(BaseModel):
    """Respuesta de confirmacion."""
    upload_id: str
    success: bool
    records_inserted: int
    records_updated: int
    message: str


# Reporte de calidad
class QualityMetric(BaseModel):
    """Metrica de calidad de una columna."""
    column: str
    completeness: float = Field(..., ge=0, le=100)
    uniqueness: float = Field(..., ge=0, le=100)
    validity: float = Field(..., ge=0, le=100)
    outliers_count: int = 0


class QualityReportResponse(BaseModel):
    """Reporte de calidad de datos."""
    upload_id: str
    overall_score: float = Field(..., ge=0, le=100)
    total_rows: int
    valid_rows: int
    metrics: List[QualityMetric]
    issues: List[str] = []
    recommendations: List[str] = []


# Columnas requeridas por tipo de datos
REQUIRED_COLUMNS = {
    DataType.VENTAS: ["fecha", "total"],
    DataType.COMPRAS: ["fecha", "total"],
    DataType.PRODUCTOS: ["sku", "nombre", "precio"],
    DataType.INVENTARIO: ["sku", "cantidad"]
}

OPTIONAL_COLUMNS = {
    DataType.VENTAS: ["producto", "cantidad", "precio_unitario", "cliente"],
    DataType.COMPRAS: ["producto", "cantidad", "costo", "proveedor"],
    DataType.PRODUCTOS: ["categoria", "descripcion", "costo"],
    DataType.INVENTARIO: ["ubicacion", "minimo", "maximo"]
}


# Historial de cargas
class HistorialCargaItem(BaseModel):
    """Item del historial de cargas de datos."""
    idHistorial: int
    uploadId: str
    tipoDatos: str
    nombreArchivo: Optional[str] = None
    registrosInsertados: int
    registrosActualizados: int = 0
    cargadoPor: int
    cargadoEn: datetime
    estado: str

    model_config = ConfigDict(from_attributes=True)


class HistorialCargaResponse(BaseModel):
    """Respuesta del historial de cargas."""
    items: List[HistorialCargaItem]
    total: int
