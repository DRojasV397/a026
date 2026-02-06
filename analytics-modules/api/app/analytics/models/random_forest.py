"""
Modelo Random Forest para prediccion de ventas.
RF-02.03: Usar modelos de Machine Learning como Random Forest.
"""

import pandas as pd
import numpy as np
from typing import Optional, Dict, Any, List, Union, Tuple
from datetime import datetime
import logging

from .base_model import (
    BaseModel, ModelConfig, ModelMetrics, PredictionResult, ModelType
)

logger = logging.getLogger(__name__)


class RandomForestConfig(ModelConfig):
    """Configuracion para modelo Random Forest."""

    def __init__(
        self,
        target_column: str,
        feature_columns: List[str] = None,
        n_estimators: int = 100,
        max_depth: Optional[int] = None,
        min_samples_split: int = 2,
        min_samples_leaf: int = 1,
        max_features: str = "sqrt",  # sqrt, log2, None, float
        bootstrap: bool = True,
        oob_score: bool = True,
        n_jobs: int = -1,  # Usar todos los cores
        **kwargs
    ):
        super().__init__(
            model_type=ModelType.RANDOM_FOREST,
            target_column=target_column,
            feature_columns=feature_columns or [],
            **kwargs
        )
        self.hyperparameters = {
            "n_estimators": n_estimators,
            "max_depth": max_depth,
            "min_samples_split": min_samples_split,
            "min_samples_leaf": min_samples_leaf,
            "max_features": max_features,
            "bootstrap": bootstrap,
            "oob_score": oob_score,
            "n_jobs": n_jobs
        }


class RandomForestModel(BaseModel):
    """
    Modelo Random Forest para regresion.

    Ventajas:
    - Robusto a outliers
    - Captura relaciones no lineales
    - Proporciona importancia de features
    - Menos propenso a overfitting
    """

    def __init__(self, config: RandomForestConfig):
        super().__init__(config)
        self.oob_score: float = 0.0
        self.feature_importances: Dict[str, float] = {}

    def _train(
        self,
        X_train: Union[pd.DataFrame, np.ndarray],
        y_train: Union[pd.Series, np.ndarray]
    ) -> None:
        """Entrena el modelo Random Forest."""
        try:
            from sklearn.ensemble import RandomForestRegressor
        except ImportError:
            raise ImportError(
                "Se requiere scikit-learn para Random Forest. "
                "Instalar con: pip install scikit-learn"
            )

        # Obtener hiperparametros
        params = self.config.hyperparameters

        # Crear modelo
        self.model = RandomForestRegressor(
            n_estimators=params.get("n_estimators", 100),
            max_depth=params.get("max_depth"),
            min_samples_split=params.get("min_samples_split", 2),
            min_samples_leaf=params.get("min_samples_leaf", 1),
            max_features=params.get("max_features", "sqrt"),
            bootstrap=params.get("bootstrap", True),
            oob_score=params.get("oob_score", True),
            n_jobs=params.get("n_jobs", -1),
            random_state=self.config.random_state
        )

        # Entrenar
        self.model.fit(X_train, y_train)

        # Guardar OOB score si esta disponible
        if hasattr(self.model, 'oob_score_') and self.model.oob_score_:
            self.oob_score = self.model.oob_score_

        # Calcular importancia de features
        if len(self.feature_names) > 0:
            self.feature_importances = {
                name: float(imp)
                for name, imp in zip(self.feature_names, self.model.feature_importances_)
            }
        else:
            self.feature_importances = {
                f"feature_{i}": float(imp)
                for i, imp in enumerate(self.model.feature_importances_)
            }

        logger.info(
            f"Random Forest entrenado. "
            f"Arboles: {params.get('n_estimators')}, "
            f"OOB Score: {self.oob_score:.4f}"
        )

    def _predict(
        self,
        X: Union[pd.DataFrame, np.ndarray]
    ) -> np.ndarray:
        """Realiza predicciones."""
        return self.model.predict(X)

    def _get_feature_importance(self) -> Dict[str, float]:
        """Retorna la importancia de las features."""
        if not self.feature_importances:
            return {}

        # Ordenar por importancia
        sorted_features = sorted(
            self.feature_importances.items(),
            key=lambda x: x[1],
            reverse=True
        )

        # Convertir a porcentaje
        total = sum(v for _, v in sorted_features)
        return {
            k: round(v / total * 100, 2) if total > 0 else 0
            for k, v in sorted_features
        }

    def _get_confidence_intervals(
        self,
        predictions: np.ndarray,
        confidence: float = 0.95
    ) -> Tuple[np.ndarray, np.ndarray]:
        """
        Calcula intervalos de confianza usando predicciones de arboles individuales.
        """
        # Obtener predicciones de cada arbol
        all_predictions = np.array([
            tree.predict(predictions.reshape(-1, 1) if predictions.ndim == 1 else predictions)
            for tree in self.model.estimators_
        ])

        # Calcular percentiles
        alpha = 1 - confidence
        lower = np.percentile(all_predictions, alpha / 2 * 100, axis=0)
        upper = np.percentile(all_predictions, (1 - alpha / 2) * 100, axis=0)

        return lower, upper

    def predict_with_std(
        self,
        X: Union[pd.DataFrame, np.ndarray]
    ) -> Tuple[np.ndarray, np.ndarray]:
        """
        Retorna predicciones con desviacion estandar.
        """
        if self.model is None:
            raise ValueError("Modelo no entrenado")

        # Predicciones de cada arbol
        predictions = np.array([
            tree.predict(X) for tree in self.model.estimators_
        ])

        mean_pred = predictions.mean(axis=0)
        std_pred = predictions.std(axis=0)

        return mean_pred, std_pred

    def tune_hyperparameters(
        self,
        X: Union[pd.DataFrame, np.ndarray],
        y: Union[pd.Series, np.ndarray],
        param_grid: Optional[Dict[str, List[Any]]] = None,
        cv: int = 5
    ) -> Dict[str, Any]:
        """
        Optimiza hiperparametros usando GridSearchCV.

        Args:
            X: Features
            y: Variable objetivo
            param_grid: Grid de parametros a probar
            cv: Numero de folds para cross-validation

        Returns:
            Mejores parametros encontrados
        """
        try:
            from sklearn.ensemble import RandomForestRegressor
            from sklearn.model_selection import GridSearchCV
        except ImportError:
            raise ImportError("Se requiere scikit-learn")

        if param_grid is None:
            param_grid = {
                'n_estimators': [50, 100, 200],
                'max_depth': [None, 10, 20, 30],
                'min_samples_split': [2, 5, 10],
                'min_samples_leaf': [1, 2, 4]
            }

        rf = RandomForestRegressor(random_state=self.config.random_state)

        grid_search = GridSearchCV(
            rf, param_grid,
            cv=cv,
            scoring='r2',
            n_jobs=-1,
            verbose=1
        )

        grid_search.fit(X, y)

        logger.info(f"Mejores parametros: {grid_search.best_params_}")
        logger.info(f"Mejor R2: {grid_search.best_score_:.4f}")

        # Actualizar configuracion con mejores parametros
        self.config.hyperparameters.update(grid_search.best_params_)

        return {
            "best_params": grid_search.best_params_,
            "best_score": grid_search.best_score_,
            "cv_results": {
                "mean_test_score": list(grid_search.cv_results_['mean_test_score']),
                "std_test_score": list(grid_search.cv_results_['std_test_score'])
            }
        }

    def get_top_features(self, n: int = 10) -> List[Tuple[str, float]]:
        """Retorna las N features mas importantes."""
        importance = self._get_feature_importance()
        return list(importance.items())[:n]

    def get_model_summary(self) -> Dict[str, Any]:
        """Retorna resumen completo del modelo."""
        summary = self.get_model_info()
        summary.update({
            "n_estimators": self.config.hyperparameters.get("n_estimators"),
            "max_depth": self.config.hyperparameters.get("max_depth"),
            "oob_score": self.oob_score,
            "feature_importance": self._get_feature_importance(),
            "top_features": self.get_top_features(10)
        })
        return summary


class TimeSeriesRandomForest(RandomForestModel):
    """
    Random Forest adaptado para series de tiempo.
    Genera features temporales automaticamente.
    """

    def __init__(
        self,
        target_column: str,
        date_column: str,
        lags: List[int] = None,
        rolling_windows: List[int] = None,
        **kwargs
    ):
        config = RandomForestConfig(
            target_column=target_column,
            **kwargs
        )
        super().__init__(config)
        self.date_column = date_column
        self.lags = lags or [1, 7, 14, 30]
        self.rolling_windows = rolling_windows or [7, 14, 30]
        self.last_date = None
        self.last_values: Dict[str, float] = {}

    def _create_time_features(
        self,
        df: pd.DataFrame,
        fit: bool = False
    ) -> pd.DataFrame:
        """Crea features temporales para el modelo."""
        df = df.copy()
        target = self.config.target_column

        # Asegurar tipo datetime
        if not pd.api.types.is_datetime64_any_dtype(df[self.date_column]):
            df[self.date_column] = pd.to_datetime(df[self.date_column])

        # Guardar ultima fecha
        if fit:
            self.last_date = df[self.date_column].max()

        # Features de calendario
        df['year'] = df[self.date_column].dt.year
        df['month'] = df[self.date_column].dt.month
        df['day'] = df[self.date_column].dt.day
        df['day_of_week'] = df[self.date_column].dt.dayofweek
        df['day_of_year'] = df[self.date_column].dt.dayofyear
        df['week_of_year'] = df[self.date_column].dt.isocalendar().week.astype(int)
        df['quarter'] = df[self.date_column].dt.quarter
        df['is_weekend'] = df['day_of_week'].isin([5, 6]).astype(int)
        df['is_month_start'] = df[self.date_column].dt.is_month_start.astype(int)
        df['is_month_end'] = df[self.date_column].dt.is_month_end.astype(int)

        # Features ciclicos
        df['month_sin'] = np.sin(2 * np.pi * df['month'] / 12)
        df['month_cos'] = np.cos(2 * np.pi * df['month'] / 12)
        df['dow_sin'] = np.sin(2 * np.pi * df['day_of_week'] / 7)
        df['dow_cos'] = np.cos(2 * np.pi * df['day_of_week'] / 7)

        # Lags
        if target in df.columns:
            for lag in self.lags:
                df[f'{target}_lag_{lag}'] = df[target].shift(lag)

            # Medias moviles
            for window in self.rolling_windows:
                df[f'{target}_rolling_mean_{window}'] = (
                    df[target].rolling(window=window, min_periods=1).mean()
                )
                df[f'{target}_rolling_std_{window}'] = (
                    df[target].rolling(window=window, min_periods=1).std()
                )

            # Diferencias
            df[f'{target}_diff_1'] = df[target].diff(1)
            df[f'{target}_diff_7'] = df[target].diff(7)

            # Guardar ultimos valores para prediccion
            if fit:
                self.last_values = {
                    f'lag_{lag}': df[target].iloc[-lag] if len(df) >= lag else None
                    for lag in self.lags
                }

        return df

    def train_from_dataframe(
        self,
        df: pd.DataFrame,
        validation_split: bool = True
    ) -> ModelMetrics:
        """
        Entrena desde un DataFrame con fechas.

        Args:
            df: DataFrame con fechas y valores
            validation_split: Si hacer split para validacion

        Returns:
            ModelMetrics del modelo entrenado
        """
        # Ordenar por fecha
        df = df.sort_values(self.date_column)

        # Crear features
        df_features = self._create_time_features(df, fit=True)

        # Eliminar filas con NaN de lags
        df_features = df_features.dropna()

        # Seleccionar features
        exclude_cols = [self.date_column, self.config.target_column]
        feature_cols = [c for c in df_features.columns if c not in exclude_cols]

        self.feature_names = feature_cols

        X = df_features[feature_cols]
        y = df_features[self.config.target_column]

        return self.train(X, y, validation_split)

    def forecast(
        self,
        periods: int,
        last_date: Optional[datetime] = None,
        freq: str = 'D',
        historical_data: Optional[pd.DataFrame] = None
    ) -> PredictionResult:
        """
        Genera predicciones futuras.

        Nota: Random Forest requiere features, por lo que se necesitan
        datos historicos para generar las features de lag.

        Args:
            periods: Numero de periodos a predecir
            last_date: Ultima fecha conocida
            freq: Frecuencia
            historical_data: Datos historicos para generar features

        Returns:
            PredictionResult con predicciones
        """
        if not self.is_fitted:
            raise ValueError("El modelo debe ser entrenado primero")

        if periods > self.MAX_FORECAST_PERIODS:
            periods = self.MAX_FORECAST_PERIODS

        start_date = last_date or self.last_date
        if start_date is None:
            raise ValueError("Se requiere fecha de inicio")

        # Generar fechas futuras
        future_dates = pd.date_range(
            start=start_date + pd.Timedelta(days=1),
            periods=periods,
            freq=freq
        )

        predictions = []
        lower_bounds = []
        upper_bounds = []

        # Para cada fecha futura, predecir y usar el valor para la siguiente
        current_data = historical_data.copy() if historical_data is not None else None

        for date in future_dates:
            # Crear features para esta fecha
            future_df = pd.DataFrame({self.date_column: [date]})

            if current_data is not None:
                # Agregar el target temporal para calcular lags
                temp_df = pd.concat([current_data, future_df], ignore_index=True)
                temp_df = self._create_time_features(temp_df, fit=False)
                row_features = temp_df.iloc[-1:][self.feature_names]
            else:
                future_df = self._create_time_features(future_df, fit=False)
                row_features = future_df[self.feature_names]

            # Predecir
            pred, std = self.predict_with_std(row_features)
            predictions.append(float(pred[0]))
            lower_bounds.append(float(pred[0] - 1.96 * std[0]))
            upper_bounds.append(float(pred[0] + 1.96 * std[0]))

            # Agregar prediccion a datos para siguiente iteracion
            if current_data is not None:
                new_row = {
                    self.date_column: date,
                    self.config.target_column: pred[0]
                }
                current_data = pd.concat([
                    current_data,
                    pd.DataFrame([new_row])
                ], ignore_index=True)

        return PredictionResult(
            predictions=predictions,
            dates=list(future_dates),
            confidence_lower=lower_bounds,
            confidence_upper=upper_bounds,
            confidence_level=0.95,
            model_type="RandomForest"
        )

    def save(self, filepath: str) -> None:
        """Guarda el modelo en disco incluyendo last_date y last_values."""
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
            # Atributos especificos de TimeSeriesRandomForest
            "last_date": self.last_date,
            "last_values": self.last_values,
            "date_column": self.date_column,
            "lags": self.lags,
            "rolling_windows": self.rolling_windows,
            "scaler": getattr(self, 'scaler', None)
        }

        with open(filepath, 'wb') as f:
            pickle.dump(model_data, f)

        logger.info(f"Modelo guardado en {filepath}")

    def load(self, filepath: str) -> None:
        """Carga el modelo desde disco incluyendo last_date y last_values."""
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

        # Atributos especificos de TimeSeriesRandomForest
        self.last_date = model_data.get("last_date")
        self.last_values = model_data.get("last_values", {})
        self.date_column = model_data.get("date_column", self.date_column)
        self.lags = model_data.get("lags", self.lags)
        self.rolling_windows = model_data.get("rolling_windows", self.rolling_windows)
        self.scaler = model_data.get("scaler")

        logger.info(f"Modelo cargado desde {filepath}")
