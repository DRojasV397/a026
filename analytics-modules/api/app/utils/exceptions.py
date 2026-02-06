"""
Excepciones personalizadas de la aplicacion.
"""

from typing import Optional, Dict, Any


class AppException(Exception):
    """Excepcion base de la aplicacion."""

    def __init__(
        self,
        message: str,
        code: str = "APP_ERROR",
        details: Optional[Dict[str, Any]] = None
    ):
        self.message = message
        self.code = code
        self.details = details or {}
        super().__init__(self.message)


class ValidationError(AppException):
    """Error de validacion de datos."""

    def __init__(self, message: str, field: Optional[str] = None, details: Optional[Dict] = None):
        super().__init__(
            message=message,
            code="VALIDATION_ERROR",
            details={"field": field, **(details or {})}
        )
        self.field = field


class FileParseError(AppException):
    """Error al parsear archivo."""

    def __init__(self, message: str, filename: Optional[str] = None, details: Optional[Dict] = None):
        super().__init__(
            message=message,
            code="FILE_PARSE_ERROR",
            details={"filename": filename, **(details or {})}
        )
        self.filename = filename


class DataCleaningError(AppException):
    """Error durante la limpieza de datos."""

    def __init__(self, message: str, column: Optional[str] = None, details: Optional[Dict] = None):
        super().__init__(
            message=message,
            code="DATA_CLEANING_ERROR",
            details={"column": column, **(details or {})}
        )
        self.column = column


class DataQualityError(AppException):
    """Error de calidad de datos."""

    def __init__(self, message: str, quality_score: Optional[float] = None, details: Optional[Dict] = None):
        super().__init__(
            message=message,
            code="DATA_QUALITY_ERROR",
            details={"quality_score": quality_score, **(details or {})}
        )
        self.quality_score = quality_score
