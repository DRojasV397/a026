"""
Modelo ARIMA para prediccion de series de tiempo.
Implementa ARIMA (AutoRegressive Integrated Moving Average).
RF-02.02: Usar modelo ARIMA para proyeccion de ventas.
"""

import pandas as pd
import numpy as np
from typing import Optional, Dict, Any, List, Tuple, Union
from datetime import datetime
import warnings
import logging

from .base_model import (
    BaseModel, ModelConfig, ModelMetrics, PredictionResult, ModelType
)

logger = logging.getLogger(__name__)


class ARIMAConfig(ModelConfig):
    """Configuracion para modelo ARIMA."""

    def __init__(
        self,
        target_column: str,
        date_column: str,
        order: Tuple[int, int, int] = (1, 1, 1),  # (p, d, q)
        auto_order: bool = True,  # Busqueda automatica de orden
        max_p: int = 5,
        max_d: int = 2,
        max_q: int = 5,
        information_criterion: str = 'aic',  # aic, bic
        **kwargs
    ):
        super().__init__(
            model_type=ModelType.ARIMA,
            target_column=target_column,
            date_column=date_column,
            **kwargs
        )
        self.hyperparameters = {
            "order": order,
            "auto_order": auto_order,
            "max_p": max_p,
            "max_d": max_d,
            "max_q": max_q,
            "information_criterion": information_criterion
        }


class ARIMAModel(BaseModel):
    """
    Modelo ARIMA para series de tiempo.

    ARIMA(p, d, q):
    - p: Orden del componente autoregresivo (AR)
    - d: Grado de diferenciacion
    - q: Orden del componente de media movil (MA)
    """

    def __init__(self, config: ARIMAConfig):
        super().__init__(config)
        self.order: Tuple[int, int, int] = config.hyperparameters.get("order", (1, 1, 1))
        self.fitted_order: Optional[Tuple[int, int, int]] = None
        self.series: Optional[pd.Series] = None
        self.last_date: Optional[datetime] = None
        self.freq: Optional[str] = None
        self.aic: float = 0.0
        self.bic: float = 0.0

    def _find_best_order(
        self,
        series: pd.Series
    ) -> Tuple[int, int, int]:
        """
        Encuentra el mejor orden ARIMA usando criterio de informacion.
        """
        try:
            from statsmodels.tsa.arima.model import ARIMA
        except ImportError:
            logger.warning("statsmodels no instalado. Usando orden por defecto.")
            return self.order

        max_p = self.config.hyperparameters.get("max_p", 5)
        max_d = self.config.hyperparameters.get("max_d", 2)
        max_q = self.config.hyperparameters.get("max_q", 5)
        criterion = self.config.hyperparameters.get("information_criterion", "aic")

        best_order = (1, 1, 1)
        best_score = float('inf')

        logger.info("Buscando mejor orden ARIMA...")

        for p in range(max_p + 1):
            for d in range(max_d + 1):
                for q in range(max_q + 1):
                    if p == 0 and q == 0:
                        continue
                    try:
                        with warnings.catch_warnings():
                            warnings.simplefilter("ignore")
                            model = ARIMA(series, order=(p, d, q))
                            fitted = model.fit()

                            score = fitted.aic if criterion == 'aic' else fitted.bic

                            if score < best_score:
                                best_score = score
                                best_order = (p, d, q)

                    except Exception:
                        continue

        logger.info(f"Mejor orden encontrado: ARIMA{best_order}, {criterion.upper()}: {best_score:.2f}")
        return best_order

    def _train(
        self,
        X_train: Union[pd.DataFrame, np.ndarray],
        y_train: Union[pd.Series, np.ndarray]
    ) -> None:
        """
        Entrena el modelo ARIMA.
        Nota: ARIMA usa solo la serie temporal, X_train se ignora.
        """
        try:
            from statsmodels.tsa.arima.model import ARIMA
        except ImportError:
            raise ImportError(
                "Se requiere statsmodels para ARIMA. "
                "Instalar con: pip install statsmodels"
            )

        # Convertir a serie si es necesario
        if isinstance(y_train, np.ndarray):
            self.series = pd.Series(y_train)
        else:
            self.series = y_train.copy()

        # Buscar mejor orden si esta configurado
        if self.config.hyperparameters.get("auto_order", True):
            self.order = self._find_best_order(self.series)

        # Entrenar modelo
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            arima_model = ARIMA(self.series, order=self.order)
            self.model = arima_model.fit()

        self.fitted_order = self.order
        self.aic = self.model.aic
        self.bic = self.model.bic

        logger.info(
            f"ARIMA{self.order} entrenado. "
            f"AIC: {self.aic:.2f}, BIC: {self.bic:.2f}"
        )

    def _predict(
        self,
        X: Union[pd.DataFrame, np.ndarray]
    ) -> np.ndarray:
        """
        Predice valores para los indices dados.
        Para ARIMA, esto retorna valores ajustados (in-sample).
        """
        if self.model is None:
            raise ValueError("Modelo no entrenado")

        # Retornar valores ajustados
        return self.model.fittedvalues.values

    def _get_feature_importance(self) -> Dict[str, float]:
        """ARIMA no tiene feature importance tradicional."""
        if self.model is None:
            return {}

        # Retornar coeficientes del modelo
        params = {}
        try:
            for name, value in self.model.params.items():
                params[str(name)] = float(value)
        except Exception:
            pass
        return params

    def forecast(
        self,
        periods: int,
        last_date: Optional[datetime] = None,
        freq: str = 'D'
    ) -> PredictionResult:
        """
        Genera predicciones futuras.

        Args:
            periods: Numero de periodos a predecir
            last_date: Ultima fecha conocida
            freq: Frecuencia ('D', 'W', 'M')

        Returns:
            PredictionResult con predicciones e intervalos de confianza
        """
        if self.model is None:
            raise ValueError("Modelo no entrenado")

        # Validar limite de periodos (RN-03.03)
        if periods > self.MAX_FORECAST_PERIODS:
            logger.warning(f"Limitando prediccion a {self.MAX_FORECAST_PERIODS} periodos")
            periods = self.MAX_FORECAST_PERIODS

        # Generar forecast
        forecast_result = self.model.get_forecast(steps=periods)
        predictions = forecast_result.predicted_mean.values

        # Intervalos de confianza
        conf_int = forecast_result.conf_int(alpha=0.05)
        lower = conf_int.iloc[:, 0].values
        upper = conf_int.iloc[:, 1].values

        # Generar fechas
        if last_date is None:
            last_date = self.last_date or datetime.now()

        dates = pd.date_range(
            start=last_date + pd.Timedelta(days=1),
            periods=periods,
            freq=freq
        )

        return PredictionResult(
            predictions=list(predictions),
            dates=list(dates),
            confidence_lower=list(lower),
            confidence_upper=list(upper),
            confidence_level=0.95,
            model_type=f"ARIMA{self.fitted_order}"
        )

    def train_from_dataframe(
        self,
        df: pd.DataFrame,
        validation_split: bool = True
    ) -> ModelMetrics:
        """
        Entrena desde un DataFrame con columna de fecha.

        Args:
            df: DataFrame con fechas y valores
            validation_split: Si hacer split para validacion

        Returns:
            ModelMetrics del modelo entrenado
        """
        date_col = self.config.date_column
        target_col = self.config.target_column

        # Preparar serie temporal
        df = df.sort_values(date_col)

        if not pd.api.types.is_datetime64_any_dtype(df[date_col]):
            df[date_col] = pd.to_datetime(df[date_col])

        self.last_date = df[date_col].max()

        # Detectar frecuencia
        self.freq = pd.infer_freq(df[date_col])

        # Crear serie con indice de fecha
        series = df.set_index(date_col)[target_col]

        if validation_split:
            # Split temporal (no aleatorio para series de tiempo)
            split_idx = int(len(series) * (1 - self.config.test_size))
            train_series = series.iloc[:split_idx]
            test_series = series.iloc[split_idx:]
        else:
            train_series = series
            test_series = series

        # Entrenar
        self._train(None, train_series)
        self.is_fitted = True

        # Evaluar
        if validation_split and len(test_series) > 0:
            # Predecir periodos de test
            forecast = self.model.get_forecast(steps=len(test_series))
            y_pred = forecast.predicted_mean.values
            y_true = test_series.values

            # Calcular metricas
            from sklearn.metrics import r2_score, mean_squared_error, mean_absolute_error

            self.metrics = ModelMetrics()
            self.metrics.r2_score = float(r2_score(y_true, y_pred))
            self.metrics.mse = float(mean_squared_error(y_true, y_pred))
            self.metrics.rmse = float(np.sqrt(self.metrics.mse))
            self.metrics.mae = float(mean_absolute_error(y_true, y_pred))

            # MAPE
            mask = y_true != 0
            if mask.any():
                self.metrics.mape = float(
                    np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask])) * 100
                )

            self.metrics.training_samples = len(train_series)
            self.metrics.test_samples = len(test_series)
            self.metrics.validate_thresholds(self.R2_THRESHOLD)
        else:
            # Usar residuos para evaluar
            residuals = self.model.resid
            self.metrics = ModelMetrics()
            self.metrics.rmse = float(np.std(residuals))
            self.metrics.mae = float(np.mean(np.abs(residuals)))
            self.metrics.training_samples = len(train_series)

        logger.info(
            f"ARIMA entrenado. R2: {self.metrics.r2_score:.4f}, "
            f"RMSE: {self.metrics.rmse:.4f}"
        )

        return self.metrics

    def get_diagnostics(self) -> Dict[str, Any]:
        """Retorna diagnosticos del modelo."""
        if self.model is None:
            return {}

        return {
            "order": self.fitted_order,
            "aic": self.aic,
            "bic": self.bic,
            "params": dict(self.model.params),
            "residual_mean": float(self.model.resid.mean()),
            "residual_std": float(self.model.resid.std())
        }

    def get_model_summary(self) -> Dict[str, Any]:
        """Retorna resumen completo del modelo."""
        summary = self.get_model_info()
        summary.update({
            "order": self.fitted_order,
            "aic": self.aic,
            "bic": self.bic,
            "diagnostics": self.get_diagnostics()
        })
        return summary
