"""
Modelo XGBoost para prediccion de ventas.
RF-02.03: Usar modelos de Machine Learning como XGBoost.
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


class XGBoostConfig(ModelConfig):
    """Configuracion para modelo XGBoost."""

    def __init__(
        self,
        target_column: str,
        feature_columns: List[str] = None,
        n_estimators: int = 100,
        max_depth: int = 6,
        learning_rate: float = 0.1,
        subsample: float = 0.8,
        colsample_bytree: float = 0.8,
        min_child_weight: int = 1,
        gamma: float = 0,
        reg_alpha: float = 0,
        reg_lambda: float = 1,
        objective: str = "reg:squarederror",
        n_jobs: int = -1,
        **kwargs
    ):
        super().__init__(
            model_type=ModelType.XGBOOST,
            target_column=target_column,
            feature_columns=feature_columns or [],
            **kwargs
        )
        self.hyperparameters = {
            "n_estimators": n_estimators,
            "max_depth": max_depth,
            "learning_rate": learning_rate,
            "subsample": subsample,
            "colsample_bytree": colsample_bytree,
            "min_child_weight": min_child_weight,
            "gamma": gamma,
            "reg_alpha": reg_alpha,
            "reg_lambda": reg_lambda,
            "objective": objective,
            "n_jobs": n_jobs
        }


class XGBoostModel(BaseModel):
    """
    Modelo XGBoost para regresion.

    Ventajas:
    - Alto rendimiento predictivo
    - Manejo eficiente de datos faltantes
    - Regularizacion incorporada (L1 y L2)
    - Paralelizacion automatica
    - Importancia de features
    """

    def __init__(self, config: XGBoostConfig):
        super().__init__(config)
        self.feature_importances: Dict[str, float] = {}
        self.evals_result: Dict[str, Any] = {}

    def _train(
        self,
        X_train: Union[pd.DataFrame, np.ndarray],
        y_train: Union[pd.Series, np.ndarray]
    ) -> None:
        """Entrena el modelo XGBoost."""
        try:
            import xgboost as xgb
        except ImportError:
            raise ImportError(
                "Se requiere xgboost para este modelo. "
                "Instalar con: pip install xgboost"
            )

        # Obtener hiperparametros
        params = self.config.hyperparameters

        # Crear modelo
        self.model = xgb.XGBRegressor(
            n_estimators=params.get("n_estimators", 100),
            max_depth=params.get("max_depth", 6),
            learning_rate=params.get("learning_rate", 0.1),
            subsample=params.get("subsample", 0.8),
            colsample_bytree=params.get("colsample_bytree", 0.8),
            min_child_weight=params.get("min_child_weight", 1),
            gamma=params.get("gamma", 0),
            reg_alpha=params.get("reg_alpha", 0),
            reg_lambda=params.get("reg_lambda", 1),
            objective=params.get("objective", "reg:squarederror"),
            n_jobs=params.get("n_jobs", -1),
            random_state=self.config.random_state,
            verbosity=0
        )

        # Entrenar
        self.model.fit(X_train, y_train)

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
            f"XGBoost entrenado. "
            f"Estimadores: {params.get('n_estimators')}, "
            f"Max Depth: {params.get('max_depth')}"
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

    def train_with_early_stopping(
        self,
        X_train: Union[pd.DataFrame, np.ndarray],
        y_train: Union[pd.Series, np.ndarray],
        X_val: Union[pd.DataFrame, np.ndarray],
        y_val: Union[pd.Series, np.ndarray],
        early_stopping_rounds: int = 10
    ) -> ModelMetrics:
        """
        Entrena con early stopping para evitar overfitting.

        Args:
            X_train: Features de entrenamiento
            y_train: Target de entrenamiento
            X_val: Features de validacion
            y_val: Target de validacion
            early_stopping_rounds: Rondas sin mejora antes de parar

        Returns:
            ModelMetrics del modelo
        """
        try:
            import xgboost as xgb
        except ImportError:
            raise ImportError("Se requiere xgboost")

        params = self.config.hyperparameters

        self.model = xgb.XGBRegressor(
            n_estimators=params.get("n_estimators", 100),
            max_depth=params.get("max_depth", 6),
            learning_rate=params.get("learning_rate", 0.1),
            subsample=params.get("subsample", 0.8),
            colsample_bytree=params.get("colsample_bytree", 0.8),
            min_child_weight=params.get("min_child_weight", 1),
            gamma=params.get("gamma", 0),
            reg_alpha=params.get("reg_alpha", 0),
            reg_lambda=params.get("reg_lambda", 1),
            objective=params.get("objective", "reg:squarederror"),
            n_jobs=params.get("n_jobs", -1),
            random_state=self.config.random_state,
            early_stopping_rounds=early_stopping_rounds,
            verbosity=0
        )

        # Entrenar con early stopping
        self.model.fit(
            X_train, y_train,
            eval_set=[(X_val, y_val)],
            verbose=False
        )

        # Guardar nombres de features
        if isinstance(X_train, pd.DataFrame):
            self.feature_names = list(X_train.columns)
        else:
            self.feature_names = [f"feature_{i}" for i in range(X_train.shape[1])]

        # Calcular importancia
        self.feature_importances = {
            name: float(imp)
            for name, imp in zip(self.feature_names, self.model.feature_importances_)
        }

        # Calcular metricas
        y_pred = self.model.predict(X_val)
        self._is_fitted = True
        self._trained_at = datetime.now()

        # Calcular metricas
        from sklearn.metrics import r2_score, mean_squared_error, mean_absolute_error

        self._metrics = ModelMetrics(
            r2_score=float(r2_score(y_val, y_pred)),
            rmse=float(np.sqrt(mean_squared_error(y_val, y_pred))),
            mae=float(mean_absolute_error(y_val, y_pred)),
            training_samples=len(y_train),
            test_samples=len(y_val)
        )

        logger.info(
            f"XGBoost con early stopping. "
            f"Mejor iteracion: {self.model.best_iteration}"
        )

        return self._metrics

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
            import xgboost as xgb
            from sklearn.model_selection import GridSearchCV
        except ImportError:
            raise ImportError("Se requiere xgboost y scikit-learn")

        if param_grid is None:
            param_grid = {
                'n_estimators': [50, 100, 200],
                'max_depth': [3, 6, 9],
                'learning_rate': [0.01, 0.1, 0.2],
                'subsample': [0.7, 0.8, 0.9]
            }

        model = xgb.XGBRegressor(
            random_state=self.config.random_state,
            verbosity=0
        )

        grid_search = GridSearchCV(
            model, param_grid,
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
            "learning_rate": self.config.hyperparameters.get("learning_rate"),
            "feature_importance": self._get_feature_importance(),
            "top_features": self.get_top_features(10)
        })
        return summary


class TimeSeriesXGBoost(XGBoostModel):
    """
    XGBoost adaptado para series de tiempo.
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
        config = XGBoostConfig(
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
                df[f'{target}_rolling_min_{window}'] = (
                    df[target].rolling(window=window, min_periods=1).min()
                )
                df[f'{target}_rolling_max_{window}'] = (
                    df[target].rolling(window=window, min_periods=1).max()
                )

            # Diferencias
            df[f'{target}_diff_1'] = df[target].diff(1)
            df[f'{target}_diff_7'] = df[target].diff(7)

            # Expansion exponencial
            df[f'{target}_ewm_7'] = df[target].ewm(span=7).mean()
            df[f'{target}_ewm_30'] = df[target].ewm(span=30).mean()

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
            pred = self.model.predict(row_features)[0]
            predictions.append(float(pred))

            # Estimar intervalo de confianza basado en el error del modelo
            if self.metrics:
                std_error = self.metrics.rmse
                lower_bounds.append(float(pred - 1.96 * std_error))
                upper_bounds.append(float(pred + 1.96 * std_error))
            else:
                lower_bounds.append(float(pred * 0.9))
                upper_bounds.append(float(pred * 1.1))

            # Agregar prediccion a datos para siguiente iteracion
            if current_data is not None:
                new_row = {
                    self.date_column: date,
                    self.config.target_column: pred
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
            model_type="XGBoost"
        )
