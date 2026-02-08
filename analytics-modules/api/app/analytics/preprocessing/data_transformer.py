"""
Modulo de transformacion de datos.
Normalizacion, escalado y transformaciones para preparar datos para ML.
"""

import pandas as pd
import numpy as np
from typing import Optional, List, Dict, Any, Tuple, Union
from dataclasses import dataclass, field
from enum import Enum
import logging

logger = logging.getLogger(__name__)


class ScalingMethod(str, Enum):
    """Metodos de escalado de datos."""
    NONE = "none"
    MINMAX = "minmax"           # Escala a [0, 1]
    STANDARD = "standard"       # Z-score (media=0, std=1)
    ROBUST = "robust"           # Usa mediana e IQR
    MAXABS = "maxabs"           # Escala por valor absoluto maximo
    LOG = "log"                 # Transformacion logaritmica
    SQRT = "sqrt"               # Transformacion raiz cuadrada


class EncodingMethod(str, Enum):
    """Metodos de codificacion de variables categoricas."""
    LABEL = "label"             # Codificacion numerica simple
    ONEHOT = "onehot"           # One-hot encoding
    ORDINAL = "ordinal"         # Codificacion ordinal
    FREQUENCY = "frequency"     # Codificacion por frecuencia
    TARGET = "target"           # Target encoding (requiere variable objetivo)


@dataclass
class TransformConfig:
    """Configuracion de transformaciones."""
    # Escalado
    scaling_method: ScalingMethod = ScalingMethod.NONE
    scaling_columns: Optional[List[str]] = None  # None = todas las numericas

    # Codificacion categorica
    encoding_method: EncodingMethod = EncodingMethod.LABEL
    encoding_columns: Optional[List[str]] = None  # None = todas las categoricas
    max_categories: int = 50  # Maximo de categorias para one-hot

    # Transformaciones de fecha
    extract_date_features: bool = True
    date_columns: Optional[List[str]] = None
    date_features: List[str] = field(default_factory=lambda: [
        "year", "month", "day", "dayofweek", "quarter"
    ])

    # Manejo de valores especiales
    handle_infinity: bool = True
    infinity_replacement: float = np.nan


@dataclass
class TransformResult:
    """Resultado de las transformaciones."""
    original_columns: List[str] = field(default_factory=list)
    transformed_columns: List[str] = field(default_factory=list)
    new_columns: List[str] = field(default_factory=list)
    removed_columns: List[str] = field(default_factory=list)
    scaling_params: Dict[str, Dict[str, float]] = field(default_factory=dict)
    encoding_maps: Dict[str, Dict[Any, int]] = field(default_factory=dict)
    transformations_applied: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        """Convierte el resultado a diccionario."""
        return {
            "original_columns": self.original_columns,
            "transformed_columns": self.transformed_columns,
            "new_columns": self.new_columns,
            "removed_columns": self.removed_columns,
            "transformations_applied": self.transformations_applied,
            "scaling_params": self.scaling_params,
            "encoding_maps": {
                k: {str(kk): vv for kk, vv in v.items()}
                for k, v in self.encoding_maps.items()
            }
        }


class DataTransformer:
    """
    Transformador de datos para preparacion de ML.

    Funcionalidades:
    - Escalado numerico (MinMax, Standard, Robust, etc.)
    - Codificacion de variables categoricas
    - Extraccion de caracteristicas de fechas
    - Transformaciones personalizadas
    """

    def __init__(self, config: Optional[TransformConfig] = None):
        self.config = config or TransformConfig()
        self.result = TransformResult()
        self._fitted = False

    def fit_transform(
        self,
        df: pd.DataFrame,
        target_column: Optional[str] = None
    ) -> Tuple[pd.DataFrame, TransformResult]:
        """
        Ajusta y transforma los datos.

        Args:
            df: DataFrame a transformar
            target_column: Columna objetivo (para target encoding)

        Returns:
            Tuple[DataFrame, TransformResult]: DataFrame transformado y resultado
        """
        self.result = TransformResult()
        self.result.original_columns = list(df.columns)

        df_transformed = df.copy()

        # 1. Manejar valores infinitos
        if self.config.handle_infinity:
            df_transformed = self._handle_infinity(df_transformed)

        # 2. Extraer caracteristicas de fechas
        if self.config.extract_date_features:
            df_transformed = self._extract_date_features(df_transformed)

        # 3. Codificar variables categoricas
        df_transformed = self._encode_categorical(df_transformed, target_column)

        # 4. Escalar variables numericas
        df_transformed = self._scale_numeric(df_transformed)

        self.result.transformed_columns = list(df_transformed.columns)
        self.result.new_columns = [
            c for c in df_transformed.columns
            if c not in self.result.original_columns
        ]
        self.result.removed_columns = [
            c for c in self.result.original_columns
            if c not in df_transformed.columns
        ]

        self._fitted = True

        logger.info(
            f"Transformacion completada: {len(self.result.original_columns)} -> "
            f"{len(self.result.transformed_columns)} columnas"
        )

        return df_transformed, self.result

    def transform(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Transforma datos usando parametros previamente ajustados.

        Args:
            df: DataFrame a transformar

        Returns:
            DataFrame transformado
        """
        if not self._fitted:
            raise ValueError("Transformer no ha sido ajustado. Use fit_transform primero.")

        df_transformed = df.copy()

        # Aplicar transformaciones con parametros guardados
        if self.config.handle_infinity:
            df_transformed = self._handle_infinity(df_transformed)

        if self.config.extract_date_features:
            df_transformed = self._extract_date_features(df_transformed)

        # Aplicar encoding con mapas guardados
        for col, mapping in self.result.encoding_maps.items():
            if col in df_transformed.columns:
                df_transformed[col] = df_transformed[col].map(mapping)

        # Aplicar scaling con parametros guardados
        for col, params in self.result.scaling_params.items():
            if col in df_transformed.columns:
                df_transformed[col] = self._apply_scaling(
                    df_transformed[col],
                    params
                )

        return df_transformed

    def _handle_infinity(self, df: pd.DataFrame) -> pd.DataFrame:
        """Reemplaza valores infinitos."""
        numeric_cols = df.select_dtypes(include=[np.number]).columns
        df[numeric_cols] = df[numeric_cols].replace(
            [np.inf, -np.inf],
            self.config.infinity_replacement
        )
        return df

    def _extract_date_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """Extrae caracteristicas de columnas de fecha."""
        date_cols = self.config.date_columns

        if date_cols is None:
            # Detectar columnas de fecha
            date_cols = []
            for col in df.columns:
                if pd.api.types.is_datetime64_any_dtype(df[col]):
                    date_cols.append(col)
                elif df[col].dtype == 'object':
                    try:
                        pd.to_datetime(df[col].dropna().head(10))
                        date_cols.append(col)
                    except (ValueError, TypeError):
                        pass

        for col in date_cols:
            if col not in df.columns:
                continue

            try:
                # Convertir a datetime si no lo es
                if not pd.api.types.is_datetime64_any_dtype(df[col]):
                    df[col] = pd.to_datetime(df[col], errors='coerce')

                # Extraer caracteristicas
                for feature in self.config.date_features:
                    new_col = f"{col}_{feature}"

                    if feature == "year":
                        df[new_col] = df[col].dt.year
                    elif feature == "month":
                        df[new_col] = df[col].dt.month
                    elif feature == "day":
                        df[new_col] = df[col].dt.day
                    elif feature == "dayofweek":
                        df[new_col] = df[col].dt.dayofweek
                    elif feature == "quarter":
                        df[new_col] = df[col].dt.quarter
                    elif feature == "weekofyear":
                        df[new_col] = df[col].dt.isocalendar().week
                    elif feature == "hour":
                        df[new_col] = df[col].dt.hour
                    elif feature == "is_weekend":
                        df[new_col] = df[col].dt.dayofweek.isin([5, 6]).astype(int)
                    elif feature == "is_month_start":
                        df[new_col] = df[col].dt.is_month_start.astype(int)
                    elif feature == "is_month_end":
                        df[new_col] = df[col].dt.is_month_end.astype(int)

                self.result.transformations_applied.append(f"date_features_{col}")

            except Exception as e:
                logger.warning(f"Error extrayendo features de {col}: {str(e)}")

        return df

    def _encode_categorical(
        self,
        df: pd.DataFrame,
        target_column: Optional[str] = None
    ) -> pd.DataFrame:
        """Codifica variables categoricas."""
        cat_cols = self.config.encoding_columns

        if cat_cols is None:
            cat_cols = df.select_dtypes(include=['object', 'category']).columns.tolist()
            # Excluir columna objetivo
            if target_column and target_column in cat_cols:
                cat_cols.remove(target_column)

        method = self.config.encoding_method

        for col in cat_cols:
            if col not in df.columns:
                continue

            try:
                if method == EncodingMethod.LABEL:
                    df, mapping = self._label_encode(df, col)
                    self.result.encoding_maps[col] = mapping

                elif method == EncodingMethod.ONEHOT:
                    n_unique = df[col].nunique()
                    if n_unique <= self.config.max_categories:
                        df = self._onehot_encode(df, col)
                    else:
                        logger.warning(
                            f"Columna {col} tiene {n_unique} categorias, "
                            f"usando label encoding en su lugar"
                        )
                        df, mapping = self._label_encode(df, col)
                        self.result.encoding_maps[col] = mapping

                elif method == EncodingMethod.FREQUENCY:
                    df, mapping = self._frequency_encode(df, col)
                    self.result.encoding_maps[col] = mapping

                elif method == EncodingMethod.TARGET and target_column:
                    df, mapping = self._target_encode(df, col, target_column)
                    self.result.encoding_maps[col] = mapping

                self.result.transformations_applied.append(f"encode_{col}")

            except Exception as e:
                logger.warning(f"Error codificando {col}: {str(e)}")

        return df

    def _label_encode(
        self,
        df: pd.DataFrame,
        col: str
    ) -> Tuple[pd.DataFrame, Dict[Any, int]]:
        """Codificacion de etiquetas numericas."""
        unique_values = df[col].dropna().unique()
        mapping = {val: idx for idx, val in enumerate(unique_values)}
        df[col] = df[col].map(mapping)
        return df, mapping

    def _onehot_encode(self, df: pd.DataFrame, col: str) -> pd.DataFrame:
        """One-hot encoding."""
        dummies = pd.get_dummies(df[col], prefix=col, dummy_na=False)
        df = pd.concat([df, dummies], axis=1)
        df = df.drop(columns=[col])
        return df

    def _frequency_encode(
        self,
        df: pd.DataFrame,
        col: str
    ) -> Tuple[pd.DataFrame, Dict[Any, float]]:
        """Codificacion por frecuencia."""
        freq = df[col].value_counts(normalize=True).to_dict()
        df[col] = df[col].map(freq)
        return df, freq

    def _target_encode(
        self,
        df: pd.DataFrame,
        col: str,
        target: str
    ) -> Tuple[pd.DataFrame, Dict[Any, float]]:
        """Target encoding (media del target por categoria)."""
        means = df.groupby(col)[target].mean().to_dict()
        df[col] = df[col].map(means)
        return df, means

    def _scale_numeric(self, df: pd.DataFrame) -> pd.DataFrame:
        """Escala variables numericas."""
        if self.config.scaling_method == ScalingMethod.NONE:
            return df

        numeric_cols = self.config.scaling_columns

        if numeric_cols is None:
            numeric_cols = df.select_dtypes(include=[np.number]).columns.tolist()

        for col in numeric_cols:
            if col not in df.columns:
                continue

            try:
                col_data = df[col]
                params = self._calculate_scaling_params(col_data)
                self.result.scaling_params[col] = params

                df[col] = self._apply_scaling(col_data, params)
                self.result.transformations_applied.append(f"scale_{col}")

            except Exception as e:
                logger.warning(f"Error escalando {col}: {str(e)}")

        return df

    def _calculate_scaling_params(self, series: pd.Series) -> Dict[str, float]:
        """Calcula parametros de escalado."""
        method = self.config.scaling_method
        params = {"method": method.value}

        if method == ScalingMethod.MINMAX:
            params["min"] = float(series.min())
            params["max"] = float(series.max())

        elif method == ScalingMethod.STANDARD:
            params["mean"] = float(series.mean())
            params["std"] = float(series.std())

        elif method == ScalingMethod.ROBUST:
            params["median"] = float(series.median())
            params["q1"] = float(series.quantile(0.25))
            params["q3"] = float(series.quantile(0.75))

        elif method == ScalingMethod.MAXABS:
            params["max_abs"] = float(series.abs().max())

        return params

    def _apply_scaling(
        self,
        series: pd.Series,
        params: Dict[str, float]
    ) -> pd.Series:
        """Aplica escalado usando parametros."""
        method = params.get("method", "none")

        if method == "minmax":
            min_val = params["min"]
            max_val = params["max"]
            range_val = max_val - min_val
            if range_val == 0:
                return series * 0
            return (series - min_val) / range_val

        elif method == "standard":
            mean = params["mean"]
            std = params["std"]
            if std == 0:
                return series * 0
            return (series - mean) / std

        elif method == "robust":
            median = params["median"]
            q1 = params["q1"]
            q3 = params["q3"]
            iqr = q3 - q1
            if iqr == 0:
                return series * 0
            return (series - median) / iqr

        elif method == "maxabs":
            max_abs = params["max_abs"]
            if max_abs == 0:
                return series * 0
            return series / max_abs

        elif method == "log":
            return np.log1p(series.clip(lower=0))

        elif method == "sqrt":
            return np.sqrt(series.clip(lower=0))

        return series

    def inverse_transform_column(
        self,
        series: pd.Series,
        column_name: str
    ) -> pd.Series:
        """
        Revierte la transformacion de una columna.

        Args:
            series: Serie transformada
            column_name: Nombre de la columna original

        Returns:
            Serie con valores originales
        """
        if column_name not in self.result.scaling_params:
            return series

        params = self.result.scaling_params[column_name]
        method = params.get("method", "none")

        if method == "minmax":
            min_val = params["min"]
            max_val = params["max"]
            return series * (max_val - min_val) + min_val

        elif method == "standard":
            mean = params["mean"]
            std = params["std"]
            return series * std + mean

        elif method == "robust":
            median = params["median"]
            q1 = params["q1"]
            q3 = params["q3"]
            iqr = q3 - q1
            return series * iqr + median

        elif method == "maxabs":
            max_abs = params["max_abs"]
            return series * max_abs

        elif method == "log":
            return np.expm1(series)

        elif method == "sqrt":
            return series ** 2

        return series


# Funciones de utilidad
def create_time_series_features(
    df: pd.DataFrame,
    date_column: str,
    value_column: str,
    lags: List[int] = [1, 7, 30],
    rolling_windows: List[int] = [7, 30]
) -> pd.DataFrame:
    """
    Crea features para series de tiempo.

    Args:
        df: DataFrame con datos
        date_column: Columna de fecha
        value_column: Columna de valor
        lags: Periodos de lag a crear
        rolling_windows: Ventanas para medias moviles

    Returns:
        DataFrame con features adicionales
    """
    df = df.sort_values(date_column).copy()

    # Features de lag
    for lag in lags:
        df[f"{value_column}_lag_{lag}"] = df[value_column].shift(lag)

    # Medias moviles
    for window in rolling_windows:
        df[f"{value_column}_rolling_mean_{window}"] = (
            df[value_column].rolling(window=window, min_periods=1).mean()
        )
        df[f"{value_column}_rolling_std_{window}"] = (
            df[value_column].rolling(window=window, min_periods=1).std()
        )

    # Diferencias
    df[f"{value_column}_diff_1"] = df[value_column].diff(1)
    df[f"{value_column}_diff_7"] = df[value_column].diff(7)

    # Porcentaje de cambio
    df[f"{value_column}_pct_change_1"] = df[value_column].pct_change(1)
    df[f"{value_column}_pct_change_7"] = df[value_column].pct_change(7)

    return df
