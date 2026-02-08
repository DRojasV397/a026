"""
Parser de archivos CSV y Excel.
Utilidad para cargar y validar datos desde archivos.
"""

import pandas as pd
import numpy as np
from typing import Optional, List, Dict, Any, Tuple
from pathlib import Path
from dataclasses import dataclass, field
from enum import Enum
import logging
import io

from .exceptions import FileParseError, ValidationError

logger = logging.getLogger(__name__)


class FileType(str, Enum):
    """Tipos de archivo soportados."""
    CSV = "csv"
    EXCEL = "xlsx"
    XLS = "xls"


@dataclass
class ColumnMapping:
    """Mapeo de columnas del archivo a campos del sistema."""
    source_column: str
    target_field: str
    data_type: str = "string"
    required: bool = False
    default_value: Any = None


@dataclass
class ParseResult:
    """Resultado del parsing de un archivo."""
    success: bool
    data: Optional[pd.DataFrame] = None
    total_rows: int = 0
    valid_rows: int = 0
    error_rows: int = 0
    warnings: List[str] = field(default_factory=list)
    errors: List[str] = field(default_factory=list)
    column_info: Dict[str, Dict] = field(default_factory=dict)
    file_type: Optional[FileType] = None

    def to_dict(self) -> Dict[str, Any]:
        """Convierte el resultado a diccionario."""
        return {
            "success": self.success,
            "total_rows": self.total_rows,
            "valid_rows": self.valid_rows,
            "error_rows": self.error_rows,
            "warnings": self.warnings,
            "errors": self.errors,
            "column_info": self.column_info,
            "file_type": self.file_type.value if self.file_type else None
        }


class FileParser:
    """Parser de archivos CSV y Excel."""

    # Extensiones soportadas
    SUPPORTED_EXTENSIONS = {'.csv', '.xlsx', '.xls'}

    # Encodings comunes para CSV
    CSV_ENCODINGS = ['utf-8', 'latin-1', 'iso-8859-1', 'cp1252']

    def __init__(self):
        self.last_result: Optional[ParseResult] = None

    def detect_file_type(self, filename: str) -> FileType:
        """
        Detecta el tipo de archivo por su extension.

        Args:
            filename: Nombre del archivo

        Returns:
            FileType: Tipo de archivo detectado

        Raises:
            FileParseError: Si la extension no es soportada
        """
        ext = Path(filename).suffix.lower()

        if ext == '.csv':
            return FileType.CSV
        elif ext == '.xlsx':
            return FileType.EXCEL
        elif ext == '.xls':
            return FileType.XLS
        else:
            raise FileParseError(
                f"Extension no soportada: {ext}",
                filename=filename,
                details={"supported": list(self.SUPPORTED_EXTENSIONS)}
            )

    def parse_file(
        self,
        file_content: bytes,
        filename: str,
        sheet_name: Optional[str] = None,
        skip_rows: int = 0,
        encoding: Optional[str] = None
    ) -> ParseResult:
        """
        Parsea un archivo CSV o Excel.

        Args:
            file_content: Contenido del archivo en bytes
            filename: Nombre del archivo
            sheet_name: Nombre de la hoja (solo Excel)
            skip_rows: Filas a saltar al inicio
            encoding: Encoding del archivo (solo CSV)

        Returns:
            ParseResult: Resultado del parsing
        """
        result = ParseResult(success=False)

        try:
            file_type = self.detect_file_type(filename)
            result.file_type = file_type

            if file_type == FileType.CSV:
                df = self._parse_csv(file_content, encoding, skip_rows)
            else:
                df = self._parse_excel(file_content, sheet_name, skip_rows)

            if df is None or df.empty:
                result.errors.append("El archivo esta vacio o no contiene datos validos")
                return result

            # Limpiar nombres de columnas
            df.columns = df.columns.str.strip()

            # Obtener informacion de columnas
            result.column_info = self._get_column_info(df)

            result.success = True
            result.data = df
            result.total_rows = len(df)
            result.valid_rows = len(df)

            logger.info(f"Archivo parseado exitosamente: {filename} ({result.total_rows} filas)")

        except FileParseError:
            raise
        except Exception as e:
            logger.error(f"Error al parsear archivo {filename}: {str(e)}")
            result.errors.append(f"Error al leer archivo: {str(e)}")

        self.last_result = result
        return result

    def _parse_csv(
        self,
        content: bytes,
        encoding: Optional[str] = None,
        skip_rows: int = 0
    ) -> Optional[pd.DataFrame]:
        """Parsea un archivo CSV."""
        encodings_to_try = [encoding] if encoding else self.CSV_ENCODINGS

        for enc in encodings_to_try:
            try:
                df = pd.read_csv(
                    io.BytesIO(content),
                    encoding=enc,
                    skiprows=skip_rows,
                    na_values=['', 'NA', 'N/A', 'null', 'NULL', 'None'],
                    keep_default_na=True
                )
                logger.debug(f"CSV parseado con encoding: {enc}")
                return df
            except UnicodeDecodeError:
                continue
            except Exception as e:
                logger.warning(f"Error con encoding {enc}: {str(e)}")
                continue

        raise FileParseError("No se pudo decodificar el archivo CSV con ninguna codificacion")

    def _parse_excel(
        self,
        content: bytes,
        sheet_name: Optional[str] = None,
        skip_rows: int = 0
    ) -> Optional[pd.DataFrame]:
        """Parsea un archivo Excel."""
        try:
            df = pd.read_excel(
                io.BytesIO(content),
                sheet_name=sheet_name or 0,
                skiprows=skip_rows,
                na_values=['', 'NA', 'N/A', 'null', 'NULL', 'None'],
                keep_default_na=True
            )
            return df
        except Exception as e:
            raise FileParseError(f"Error al leer archivo Excel: {str(e)}")

    def _get_column_info(self, df: pd.DataFrame) -> Dict[str, Dict]:
        """Obtiene informacion sobre las columnas del DataFrame."""
        info = {}

        for col in df.columns:
            col_data = df[col]
            info[col] = {
                "dtype": str(col_data.dtype),
                "null_count": int(col_data.isna().sum()),
                "null_percentage": round(col_data.isna().sum() / len(df) * 100, 2),
                "unique_count": int(col_data.nunique()),
                "sample_values": col_data.dropna().head(3).tolist()
            }

            # Detectar tipo de dato sugerido
            if pd.api.types.is_numeric_dtype(col_data):
                info[col]["suggested_type"] = "numeric"
            elif pd.api.types.is_datetime64_any_dtype(col_data):
                info[col]["suggested_type"] = "datetime"
            else:
                # Intentar detectar fechas en strings
                if self._looks_like_date(col_data):
                    info[col]["suggested_type"] = "datetime"
                else:
                    info[col]["suggested_type"] = "string"

        return info

    def _looks_like_date(self, series: pd.Series, sample_size: int = 10) -> bool:
        """Detecta si una serie parece contener fechas."""
        sample = series.dropna().head(sample_size)
        if len(sample) == 0:
            return False

        date_count = 0
        for val in sample:
            try:
                pd.to_datetime(val)
                date_count += 1
            except:
                pass

        return date_count / len(sample) > 0.7

    def validate_columns(
        self,
        df: pd.DataFrame,
        required_columns: List[str],
        optional_columns: Optional[List[str]] = None
    ) -> Tuple[bool, List[str], List[str]]:
        """
        Valida que el DataFrame tenga las columnas requeridas.

        Args:
            df: DataFrame a validar
            required_columns: Columnas requeridas
            optional_columns: Columnas opcionales

        Returns:
            Tuple[bool, List[str], List[str]]: (valido, columnas_faltantes, warnings)
        """
        df_columns = set(df.columns.str.lower())
        required_lower = {c.lower(): c for c in required_columns}
        optional_lower = {c.lower(): c for c in (optional_columns or [])}

        missing = []
        warnings = []

        for col_lower, col_original in required_lower.items():
            if col_lower not in df_columns:
                missing.append(col_original)

        for col_lower, col_original in optional_lower.items():
            if col_lower not in df_columns:
                warnings.append(f"Columna opcional no encontrada: {col_original}")

        return len(missing) == 0, missing, warnings

    def map_columns(
        self,
        df: pd.DataFrame,
        mappings: List[ColumnMapping]
    ) -> pd.DataFrame:
        """
        Mapea columnas del archivo a nombres del sistema.

        Args:
            df: DataFrame original
            mappings: Lista de mapeos de columnas

        Returns:
            pd.DataFrame: DataFrame con columnas renombradas
        """
        rename_map = {}
        for mapping in mappings:
            if mapping.source_column in df.columns:
                rename_map[mapping.source_column] = mapping.target_field

        return df.rename(columns=rename_map)

    def get_preview(
        self,
        df: pd.DataFrame,
        rows: int = 10
    ) -> List[Dict[str, Any]]:
        """
        Obtiene una vista previa de los datos.

        Args:
            df: DataFrame
            rows: Numero de filas a mostrar

        Returns:
            List[Dict]: Lista de diccionarios con los datos
        """
        preview_df = df.head(rows).copy()

        # Convertir valores a tipos serializables
        for col in preview_df.columns:
            if pd.api.types.is_datetime64_any_dtype(preview_df[col]):
                preview_df[col] = preview_df[col].astype(str)
            elif pd.api.types.is_numeric_dtype(preview_df[col]):
                preview_df[col] = preview_df[col].apply(
                    lambda x: None if pd.isna(x) else x
                )

        return preview_df.to_dict(orient='records')

    def get_sheets(self, file_content: bytes, filename: str) -> List[str]:
        """
        Obtiene la lista de hojas de un archivo Excel.

        Args:
            file_content: Contenido del archivo
            filename: Nombre del archivo

        Returns:
            List[str]: Lista de nombres de hojas
        """
        file_type = self.detect_file_type(filename)

        if file_type == FileType.CSV:
            return []

        try:
            excel_file = pd.ExcelFile(io.BytesIO(file_content))
            return excel_file.sheet_names
        except Exception as e:
            raise FileParseError(f"Error al leer hojas del archivo: {str(e)}")
