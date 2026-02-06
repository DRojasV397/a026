"""
Modelo de Regresion Lineal para prediccion de ventas.
Implementa regresion lineal simple y multiple usando scikit-learn.
"""

import pandas as pd
import numpy as np
from typing import Optional, Dict, Any, List, Union
from sklearn.linear_model import LinearRegression, Ridge, Lasso, ElasticNet
from sklearn.preprocessing import StandardScaler, PolynomialFeatures
import logging

from .base_model import (
    BaseModel, ModelConfig, ModelMetrics, PredictionResult, ModelType
)

logger = logging.getLogger(__name__)


class LinearRegressionConfig(ModelConfig):
    """Configuracion especifica para regresion lineal."""

    def __init__(
        self,
        target_column: str,
        feature_columns: List[str] = None,
        regularization: str = "none",  # none, ridge, lasso, elasticnet
        alpha: float = 1.0,
        l1_ratio: float = 0.5,  # Solo para elasticnet
        polynomial_degree: int = 1,
        normalize: bool = True,
        **kwargs
    ):
        super().__init__(
            model_type=ModelType.LINEAR_REGRESSION,
            target_column=target_column,
            feature_columns=feature_columns or [],
            **kwargs
        )
        self.hyperparameters = {
            "regularization": regularization,
            "alpha": alpha,
            "l1_ratio": l1_ratio,
            "polynomial_degree": polynomial_degree,
            "normalize": normalize
        }


class LinearRegressionModel(BaseModel):
    """
    Modelo de Regresion Lineal.

    Soporta:
    - Regresion lineal simple
    - Regresion lineal multiple
    - Regularizacion (Ridge, Lasso, ElasticNet)
    - Caracteristicas polinomiales
    """

    def __init__(self, config: LinearRegressionConfig):
        super().__init__(config)
        self.scaler: Optional[StandardScaler] = None
        self.poly_features: Optional[PolynomialFeatures] = None
        self.coefficients: Dict[str, float] = {}
        self.intercept: float = 0.0

    def _create_model(self) -> Union[LinearRegression, Ridge, Lasso, ElasticNet]:
        """Crea el modelo segun la configuracion."""
        reg_type = self.config.hyperparameters.get("regularization", "none")
        alpha = self.config.hyperparameters.get("alpha", 1.0)

        if reg_type == "ridge":
            return Ridge(alpha=alpha)
        elif reg_type == "lasso":
            return Lasso(alpha=alpha)
        elif reg_type == "elasticnet":
            l1_ratio = self.config.hyperparameters.get("l1_ratio", 0.5)
            return ElasticNet(alpha=alpha, l1_ratio=l1_ratio)
        else:
            return LinearRegression()

    def _preprocess(
        self,
        X: Union[pd.DataFrame, np.ndarray],
        fit: bool = False
    ) -> np.ndarray:
        """Preprocesa los datos de entrada."""
        X_processed = np.array(X)

        # Aplicar transformacion polinomial si es necesario
        poly_degree = self.config.hyperparameters.get("polynomial_degree", 1)
        if poly_degree > 1:
            if fit:
                self.poly_features = PolynomialFeatures(
                    degree=poly_degree,
                    include_bias=False
                )
                X_processed = self.poly_features.fit_transform(X_processed)
            elif self.poly_features:
                X_processed = self.poly_features.transform(X_processed)

        # Normalizar si es necesario
        if self.config.hyperparameters.get("normalize", True):
            if fit:
                self.scaler = StandardScaler()
                X_processed = self.scaler.fit_transform(X_processed)
            elif self.scaler:
                X_processed = self.scaler.transform(X_processed)

        return X_processed

    def _train(
        self,
        X_train: Union[pd.DataFrame, np.ndarray],
        y_train: Union[pd.Series, np.ndarray]
    ) -> None:
        """Entrena el modelo de regresion lineal."""
        # Preprocesar datos
        X_processed = self._preprocess(X_train, fit=True)

        # Crear y entrenar modelo
        self.model = self._create_model()
        self.model.fit(X_processed, y_train)

        # Guardar coeficientes
        self.intercept = float(self.model.intercept_)

        # Mapear coeficientes a nombres de features
        if len(self.feature_names) > 0:
            # Si hay transformacion polinomial, los nombres cambian
            if self.poly_features:
                feature_names = self.poly_features.get_feature_names_out(
                    self.feature_names
                )
            else:
                feature_names = self.feature_names

            self.coefficients = {
                name: float(coef)
                for name, coef in zip(feature_names, self.model.coef_)
            }
        else:
            self.coefficients = {
                f"feature_{i}": float(coef)
                for i, coef in enumerate(self.model.coef_)
            }

        logger.info(
            f"Regresion lineal entrenada. "
            f"Intercepto: {self.intercept:.4f}, "
            f"Coeficientes: {len(self.coefficients)}"
        )

    def _predict(
        self,
        X: Union[pd.DataFrame, np.ndarray]
    ) -> np.ndarray:
        """Realiza predicciones."""
        X_processed = self._preprocess(X, fit=False)
        return self.model.predict(X_processed)

    def _get_feature_importance(self) -> Dict[str, float]:
        """Retorna la importancia de las features (coeficientes absolutos)."""
        if not self.coefficients:
            return {}

        # Normalizar coeficientes para interpretacion
        total = sum(abs(v) for v in self.coefficients.values())
        if total == 0:
            return self.coefficients

        return {
            k: abs(v) / total * 100
            for k, v in sorted(
                self.coefficients.items(),
                key=lambda x: abs(x[1]),
                reverse=True
            )
        }

    def get_equation(self) -> str:
        """Retorna la ecuacion del modelo."""
        if not self.is_fitted:
            return "Modelo no entrenado"

        terms = [f"{self.intercept:.4f}"]
        for name, coef in self.coefficients.items():
            sign = "+" if coef >= 0 else "-"
            terms.append(f"{sign} {abs(coef):.4f} * {name}")

        return f"y = {' '.join(terms)}"

    def get_model_summary(self) -> Dict[str, Any]:
        """Retorna resumen del modelo."""
        summary = self.get_model_info()
        summary.update({
            "intercept": self.intercept,
            "coefficients": self.coefficients,
            "equation": self.get_equation(),
            "regularization": self.config.hyperparameters.get("regularization"),
            "polynomial_degree": self.config.hyperparameters.get("polynomial_degree")
        })
        return summary


class TimeSeriesLinearRegression(LinearRegressionModel):
    """
    Regresion lineal adaptada para series de tiempo.
    Genera features temporales automaticamente.
    """

    def __init__(
        self,
        target_column: str,
        date_column: str,
        **kwargs
    ):
        config = LinearRegressionConfig(
            target_column=target_column,
            date_column=date_column,
            **kwargs
        )
        super().__init__(config)
        self.date_column = date_column
        self.last_date = None
        self.trend_coef = 0.0

    def _create_time_features(
        self,
        df: pd.DataFrame,
        fit: bool = False
    ) -> pd.DataFrame:
        """Crea features temporales."""
        df = df.copy()

        # Asegurar que la fecha es datetime
        if not pd.api.types.is_datetime64_any_dtype(df[self.date_column]):
            df[self.date_column] = pd.to_datetime(df[self.date_column])

        # Guardar ultima fecha
        if fit:
            self.last_date = df[self.date_column].max()

        # Crear features temporales
        df['time_index'] = (
            df[self.date_column] - df[self.date_column].min()
        ).dt.days

        df['month'] = df[self.date_column].dt.month
        df['day_of_week'] = df[self.date_column].dt.dayofweek
        df['day_of_month'] = df[self.date_column].dt.day
        df['quarter'] = df[self.date_column].dt.quarter
        df['year'] = df[self.date_column].dt.year
        df['is_weekend'] = df['day_of_week'].isin([5, 6]).astype(int)

        # Componentes ciclicos (para capturar estacionalidad)
        df['month_sin'] = np.sin(2 * np.pi * df['month'] / 12)
        df['month_cos'] = np.cos(2 * np.pi * df['month'] / 12)
        df['dow_sin'] = np.sin(2 * np.pi * df['day_of_week'] / 7)
        df['dow_cos'] = np.cos(2 * np.pi * df['day_of_week'] / 7)

        return df

    def train_from_dataframe(
        self,
        df: pd.DataFrame,
        validation_split: bool = True
    ) -> ModelMetrics:
        """
        Entrena el modelo desde un DataFrame con fechas.

        Args:
            df: DataFrame con columnas de fecha y objetivo
            validation_split: Si dividir en train/test

        Returns:
            ModelMetrics: Metricas del modelo
        """
        # Crear features temporales
        df_features = self._create_time_features(df, fit=True)

        # Seleccionar features
        feature_cols = [
            'time_index', 'month', 'day_of_week', 'quarter',
            'is_weekend', 'month_sin', 'month_cos', 'dow_sin', 'dow_cos'
        ]

        # Agregar features adicionales si existen
        if self.config.feature_columns:
            feature_cols.extend([
                c for c in self.config.feature_columns
                if c in df.columns and c not in feature_cols
            ])

        self.feature_names = feature_cols

        X = df_features[feature_cols]
        y = df_features[self.config.target_column]

        return self.train(X, y, validation_split)

    def forecast(
        self,
        periods: int,
        last_date: Optional[pd.Timestamp] = None,
        freq: str = 'D'
    ) -> PredictionResult:
        """
        Genera predicciones futuras.

        Args:
            periods: Numero de periodos a predecir
            last_date: Ultima fecha conocida
            freq: Frecuencia de prediccion

        Returns:
            PredictionResult: Resultado con predicciones
        """
        if not self.is_fitted:
            raise ValueError("El modelo debe ser entrenado primero")

        # Validar limite de periodos (RN-03.03)
        if periods > self.MAX_FORECAST_PERIODS:
            periods = self.MAX_FORECAST_PERIODS

        # Determinar fecha de inicio
        start_date = last_date or self.last_date
        if start_date is None:
            raise ValueError("Se requiere fecha de inicio para forecast")

        # Generar fechas futuras
        future_dates = pd.date_range(
            start=start_date + pd.Timedelta(days=1),
            periods=periods,
            freq=freq
        )

        # Crear DataFrame con fechas futuras
        future_df = pd.DataFrame({self.date_column: future_dates})
        future_df = self._create_time_features(future_df, fit=False)

        # Preparar features
        feature_cols = self.feature_names
        X_future = future_df[feature_cols]

        # Predecir
        predictions, lower, upper = self.predict(X_future, return_confidence=True)

        return PredictionResult(
            predictions=list(predictions),
            dates=list(future_dates),
            confidence_lower=list(lower),
            confidence_upper=list(upper),
            model_type=self.model_type.value
        )

    def save(self, filepath: str) -> None:
        """Guarda el modelo en disco incluyendo last_date."""
        import pickle

        model_data = {
            "model": self.model,
            "config": self.config,
            "metrics": self.metrics,
            "feature_names": self.feature_names,
            "is_fitted": self.is_fitted,
            "status": self.status,
            "created_at": self.created_at,
            "trained_at": self.trained_at,
            "training_data_info": self.training_data_info,
            # Atributos especificos de TimeSeriesLinearRegression
            "last_date": self.last_date,
            "date_column": self.date_column,
            "trend_coef": self.trend_coef,
            "intercept": getattr(self, 'intercept', 0.0),
            "coefficients": getattr(self, 'coefficients', {}),
            "scaler": getattr(self, 'scaler', None),
            "poly_features": getattr(self, 'poly_features', None)
        }

        with open(filepath, 'wb') as f:
            pickle.dump(model_data, f)

        logger.info(f"Modelo guardado en {filepath}")

    def load(self, filepath: str) -> None:
        """Carga el modelo desde disco incluyendo last_date."""
        import pickle

        with open(filepath, 'rb') as f:
            model_data = pickle.load(f)

        self.model = model_data["model"]
        self.config = model_data["config"]
        self.metrics = model_data["metrics"]
        self.feature_names = model_data["feature_names"]
        self.is_fitted = model_data["is_fitted"]
        self.status = model_data["status"]
        self.created_at = model_data["created_at"]
        self.trained_at = model_data["trained_at"]
        self.training_data_info = model_data.get("training_data_info", {})

        # Atributos especificos de TimeSeriesLinearRegression
        self.last_date = model_data.get("last_date")
        self.date_column = model_data.get("date_column", self.date_column)
        self.trend_coef = model_data.get("trend_coef", 0.0)
        self.intercept = model_data.get("intercept", 0.0)
        self.coefficients = model_data.get("coefficients", {})
        self.scaler = model_data.get("scaler")
        self.poly_features = model_data.get("poly_features")

        logger.info(f"Modelo cargado desde {filepath}")
