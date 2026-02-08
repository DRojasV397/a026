"""
Modulo de limpieza de datos.
Implementa las reglas de negocio RN-02.01 a RN-02.05.
"""

import pandas as pd
import numpy as np
from typing import Optional, List, Dict, Any, Tuple
from dataclasses import dataclass, field
from enum import Enum
import logging

logger = logging.getLogger(__name__)


class NullStrategy(str, Enum):
    """Estrategias para manejo de valores nulos."""
    DROP = "drop"
    FILL_ZERO = "fill_zero"
    FILL_MEAN = "fill_mean"
    FILL_MEDIAN = "fill_median"
    FILL_MODE = "fill_mode"
    FILL_FORWARD = "fill_forward"
    FILL_BACKWARD = "fill_backward"
    FILL_INTERPOLATE = "fill_interpolate"


@dataclass
class CleaningConfig:
    """Configuracion de limpieza de datos."""
    # Duplicados (RN-02.01)
    remove_duplicates: bool = True
    duplicate_subset: Optional[List[str]] = None
    keep_duplicate: str = "first"  # first, last, False

    # Valores nulos (RN-02.02, RN-02.04)
    handle_nulls: bool = True
    null_strategy: NullStrategy = NullStrategy.DROP
    null_threshold: float = 0.5  # Eliminar columnas con > 50% nulos
    required_columns: List[str] = field(default_factory=list)

    # Valores atipicos (RN-02.03)
    detect_outliers: bool = True
    outlier_method: str = "zscore"  # zscore, iqr
    outlier_threshold: float = 3.0  # Para Z-Score
    iqr_multiplier: float = 1.5  # Para IQR
    remove_outliers: bool = False  # Solo detectar, no eliminar por defecto

    # Normalizacion de texto
    normalize_text: bool = True
    strip_whitespace: bool = True
    lowercase_columns: bool = False

    # Validacion de retencion (RN-02.05)
    min_retention_rate: float = 0.70  # 70% minimo


@dataclass
class CleaningReport:
    """Reporte de limpieza de datos."""
    original_rows: int = 0
    original_columns: int = 0
    cleaned_rows: int = 0
    cleaned_columns: int = 0

    duplicates_found: int = 0
    duplicates_removed: int = 0

    nulls_found: int = 0
    nulls_handled: int = 0
    columns_dropped_nulls: List[str] = field(default_factory=list)

    outliers_detected: int = 0
    outliers_removed: int = 0
    outlier_details: Dict[str, int] = field(default_factory=dict)

    retention_rate: float = 0.0
    meets_retention_requirement: bool = True

    warnings: List[str] = field(default_factory=list)
    errors: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        """Convierte el reporte a diccionario."""
        return {
            "original_rows": self.original_rows,
            "original_columns": self.original_columns,
            "cleaned_rows": self.cleaned_rows,
            "cleaned_columns": self.cleaned_columns,
            "duplicates": {
                "found": self.duplicates_found,
                "removed": self.duplicates_removed
            },
            "nulls": {
                "found": self.nulls_found,
                "handled": self.nulls_handled,
                "columns_dropped": self.columns_dropped_nulls
            },
            "outliers": {
                "detected": self.outliers_detected,
                "removed": self.outliers_removed,
                "by_column": self.outlier_details
            },
            "retention": {
                "rate": round(self.retention_rate * 100, 2),
                "meets_requirement": self.meets_retention_requirement
            },
            "warnings": self.warnings,
            "errors": self.errors
        }


class DataCleaner:
    """
    Limpiador de datos con implementacion de reglas de negocio.

    Reglas implementadas:
    - RN-02.01: Eliminacion de duplicados
    - RN-02.02: Manejo de valores nulos
    - RN-02.03: Deteccion de valores atipicos (Z-Score)
    - RN-02.04: Imputacion de nulos en numericos
    - RN-02.05: Mantener al menos 70% de registros
    """

    def __init__(self, config: Optional[CleaningConfig] = None):
        self.config = config or CleaningConfig()
        self.report = CleaningReport()

    def clean(self, df: pd.DataFrame) -> Tuple[pd.DataFrame, CleaningReport]:
        """
        Ejecuta el proceso completo de limpieza.

        Args:
            df: DataFrame a limpiar

        Returns:
            Tuple[DataFrame, CleaningReport]: DataFrame limpio y reporte
        """
        self.report = CleaningReport()
        self.report.original_rows = len(df)
        self.report.original_columns = len(df.columns)

        df_clean = df.copy()

        # 1. Normalizar texto
        if self.config.normalize_text:
            df_clean = self._normalize_text(df_clean)

        # 2. Eliminar duplicados (RN-02.01)
        if self.config.remove_duplicates:
            df_clean = self._remove_duplicates(df_clean)

        # 3. Manejar columnas con muchos nulos
        df_clean = self._drop_high_null_columns(df_clean)

        # 4. Manejar valores nulos (RN-02.02, RN-02.04)
        if self.config.handle_nulls:
            df_clean = self._handle_nulls(df_clean)

        # 5. Detectar/eliminar outliers (RN-02.03)
        if self.config.detect_outliers:
            df_clean = self._handle_outliers(df_clean)

        # Calcular retencion (RN-02.05)
        self.report.cleaned_rows = len(df_clean)
        self.report.cleaned_columns = len(df_clean.columns)

        if self.report.original_rows > 0:
            self.report.retention_rate = self.report.cleaned_rows / self.report.original_rows
        else:
            self.report.retention_rate = 1.0

        self.report.meets_retention_requirement = (
            self.report.retention_rate >= self.config.min_retention_rate
        )

        if not self.report.meets_retention_requirement:
            self.report.warnings.append(
                f"Tasa de retencion ({self.report.retention_rate:.1%}) menor al "
                f"minimo requerido ({self.config.min_retention_rate:.0%})"
            )

        logger.info(
            f"Limpieza completada: {self.report.cleaned_rows}/{self.report.original_rows} "
            f"filas ({self.report.retention_rate:.1%})"
        )

        return df_clean, self.report

    def _normalize_text(self, df: pd.DataFrame) -> pd.DataFrame:
        """Normaliza columnas de texto."""
        text_cols = df.select_dtypes(include=['object']).columns

        for col in text_cols:
            if self.config.strip_whitespace:
                df[col] = df[col].astype(str).str.strip()
                df[col] = df[col].replace('nan', np.nan)

            if self.config.lowercase_columns:
                df[col] = df[col].str.lower()

        return df

    def _remove_duplicates(self, df: pd.DataFrame) -> pd.DataFrame:
        """Elimina filas duplicadas (RN-02.01)."""
        before = len(df)

        df = df.drop_duplicates(
            subset=self.config.duplicate_subset,
            keep=self.config.keep_duplicate if self.config.keep_duplicate != "False" else False
        )

        self.report.duplicates_found = before - len(df)
        self.report.duplicates_removed = self.report.duplicates_found

        if self.report.duplicates_removed > 0:
            logger.info(f"Duplicados eliminados: {self.report.duplicates_removed}")

        return df

    def _drop_high_null_columns(self, df: pd.DataFrame) -> pd.DataFrame:
        """Elimina columnas con demasiados valores nulos."""
        null_ratios = df.isna().sum() / len(df)
        cols_to_drop = null_ratios[null_ratios > self.config.null_threshold].index.tolist()

        # No eliminar columnas requeridas
        cols_to_drop = [c for c in cols_to_drop if c not in self.config.required_columns]

        if cols_to_drop:
            df = df.drop(columns=cols_to_drop)
            self.report.columns_dropped_nulls = cols_to_drop
            self.report.warnings.append(
                f"Columnas eliminadas por exceso de nulos: {', '.join(cols_to_drop)}"
            )

        return df

    def _handle_nulls(self, df: pd.DataFrame) -> pd.DataFrame:
        """Maneja valores nulos segun la estrategia configurada (RN-02.02, RN-02.04)."""
        self.report.nulls_found = int(df.isna().sum().sum())

        strategy = self.config.null_strategy

        if strategy == NullStrategy.DROP:
            before = len(df)
            df = df.dropna()
            self.report.nulls_handled = before - len(df)

        elif strategy == NullStrategy.FILL_ZERO:
            df = df.fillna(0)
            self.report.nulls_handled = self.report.nulls_found

        elif strategy == NullStrategy.FILL_MEAN:
            numeric_cols = df.select_dtypes(include=[np.number]).columns
            df[numeric_cols] = df[numeric_cols].fillna(df[numeric_cols].mean())
            # Para no numericos, usar valor vacio
            df = df.fillna('')
            self.report.nulls_handled = self.report.nulls_found

        elif strategy == NullStrategy.FILL_MEDIAN:
            numeric_cols = df.select_dtypes(include=[np.number]).columns
            df[numeric_cols] = df[numeric_cols].fillna(df[numeric_cols].median())
            df = df.fillna('')
            self.report.nulls_handled = self.report.nulls_found

        elif strategy == NullStrategy.FILL_MODE:
            for col in df.columns:
                mode_val = df[col].mode()
                if len(mode_val) > 0:
                    df[col] = df[col].fillna(mode_val[0])
            self.report.nulls_handled = self.report.nulls_found

        elif strategy == NullStrategy.FILL_FORWARD:
            df = df.fillna(method='ffill')
            self.report.nulls_handled = self.report.nulls_found - int(df.isna().sum().sum())

        elif strategy == NullStrategy.FILL_BACKWARD:
            df = df.fillna(method='bfill')
            self.report.nulls_handled = self.report.nulls_found - int(df.isna().sum().sum())

        elif strategy == NullStrategy.FILL_INTERPOLATE:
            numeric_cols = df.select_dtypes(include=[np.number]).columns
            df[numeric_cols] = df[numeric_cols].interpolate(method='linear')
            df = df.fillna(method='ffill').fillna(method='bfill')
            self.report.nulls_handled = self.report.nulls_found

        if self.report.nulls_handled > 0:
            logger.info(f"Valores nulos manejados: {self.report.nulls_handled}")

        return df

    def _handle_outliers(self, df: pd.DataFrame) -> pd.DataFrame:
        """Detecta y opcionalmente elimina outliers (RN-02.03)."""
        numeric_cols = df.select_dtypes(include=[np.number]).columns
        total_outliers = 0
        outlier_mask = pd.Series([False] * len(df), index=df.index)

        for col in numeric_cols:
            col_data = df[col].dropna()
            if len(col_data) == 0:
                continue

            if self.config.outlier_method == "zscore":
                outliers = self._detect_zscore_outliers(df[col])
            else:  # iqr
                outliers = self._detect_iqr_outliers(df[col])

            col_outliers = outliers.sum()
            if col_outliers > 0:
                self.report.outlier_details[col] = int(col_outliers)
                total_outliers += col_outliers
                outlier_mask = outlier_mask | outliers

        self.report.outliers_detected = int(total_outliers)

        if self.config.remove_outliers and total_outliers > 0:
            df = df[~outlier_mask]
            self.report.outliers_removed = int(outlier_mask.sum())
            logger.info(f"Outliers eliminados: {self.report.outliers_removed}")

        return df

    def _detect_zscore_outliers(self, series: pd.Series) -> pd.Series:
        """Detecta outliers usando Z-Score."""
        if series.std() == 0:
            return pd.Series([False] * len(series), index=series.index)

        z_scores = np.abs((series - series.mean()) / series.std())
        return z_scores > self.config.outlier_threshold

    def _detect_iqr_outliers(self, series: pd.Series) -> pd.Series:
        """Detecta outliers usando IQR."""
        Q1 = series.quantile(0.25)
        Q3 = series.quantile(0.75)
        IQR = Q3 - Q1

        lower_bound = Q1 - (self.config.iqr_multiplier * IQR)
        upper_bound = Q3 + (self.config.iqr_multiplier * IQR)

        return (series < lower_bound) | (series > upper_bound)

    def get_outlier_summary(self, df: pd.DataFrame) -> Dict[str, Dict[str, Any]]:
        """
        Obtiene un resumen detallado de outliers por columna.

        Returns:
            Dict con estadisticas de outliers por columna
        """
        summary = {}
        numeric_cols = df.select_dtypes(include=[np.number]).columns

        for col in numeric_cols:
            col_data = df[col].dropna()
            if len(col_data) == 0:
                continue

            zscore_outliers = self._detect_zscore_outliers(col_data)
            iqr_outliers = self._detect_iqr_outliers(col_data)

            Q1 = col_data.quantile(0.25)
            Q3 = col_data.quantile(0.75)
            IQR = Q3 - Q1

            summary[col] = {
                "count": len(col_data),
                "mean": float(col_data.mean()),
                "std": float(col_data.std()),
                "min": float(col_data.min()),
                "max": float(col_data.max()),
                "Q1": float(Q1),
                "Q3": float(Q3),
                "IQR": float(IQR),
                "zscore_outliers": int(zscore_outliers.sum()),
                "iqr_outliers": int(iqr_outliers.sum()),
                "outlier_values_zscore": col_data[zscore_outliers].tolist()[:10]  # Primeros 10
            }

        return summary
