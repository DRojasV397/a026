"""
Modelo SARIMA para prediccion de series de tiempo con estacionalidad.
RN-03.05: Usar SARIMA para datos con patron de estacionalidad.
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


class SARIMAConfig(ModelConfig):
    """Configuracion para modelo SARIMA."""

    def __init__(
        self,
        target_column: str,
        date_column: str,
        order: Tuple[int, int, int] = (1, 1, 1),  # (p, d, q)
        seasonal_order: Tuple[int, int, int, int] = (1, 1, 1, 12),  # (P, D, Q, s)
        auto_order: bool = True,
        seasonal_period: int = 12,  # 12 para mensual, 7 para semanal, 4 para trimestral
        max_p: int = 3,
        max_d: int = 2,
        max_q: int = 3,
        max_P: int = 2,
        max_D: int = 1,
        max_Q: int = 2,
        information_criterion: str = 'aic',
        **kwargs
    ):
        super().__init__(
            model_type=ModelType.SARIMA,
            target_column=target_column,
            date_column=date_column,
            **kwargs
        )
        self.hyperparameters = {
            "order": order,
            "seasonal_order": seasonal_order,
            "auto_order": auto_order,
            "seasonal_period": seasonal_period,
            "max_p": max_p,
            "max_d": max_d,
            "max_q": max_q,
            "max_P": max_P,
            "max_D": max_D,
            "max_Q": max_Q,
            "information_criterion": information_criterion
        }


class SARIMAModel(BaseModel):
    """
    Modelo SARIMA (Seasonal ARIMA) para series de tiempo con estacionalidad.

    SARIMA(p, d, q)(P, D, Q, s):
    - p, d, q: Componentes no estacionales (AR, diferenciacion, MA)
    - P, D, Q: Componentes estacionales
    - s: Periodo estacional (12 para datos mensuales, 7 para semanales, etc.)

    RN-03.05: Este modelo debe usarse cuando se detecta estacionalidad.
    """

    def __init__(self, config: SARIMAConfig):
        super().__init__(config)
        self.order: Tuple[int, int, int] = config.hyperparameters.get("order", (1, 1, 1))
        self.seasonal_order: Tuple[int, int, int, int] = config.hyperparameters.get(
            "seasonal_order", (1, 1, 1, 12)
        )
        self.fitted_order: Optional[Tuple[int, int, int]] = None
        self.fitted_seasonal_order: Optional[Tuple[int, int, int, int]] = None
        self.series: Optional[pd.Series] = None
        self.last_date: Optional[datetime] = None
        self.freq: Optional[str] = None
        self.aic: float = 0.0
        self.bic: float = 0.0
        self.seasonality_detected: bool = False

    @staticmethod
    def detect_seasonality(
        series: pd.Series,
        max_lag: int = 365
    ) -> Tuple[bool, int]:
        """
        Detecta estacionalidad en una serie de tiempo.

        Returns:
            Tuple[bool, int]: (hay_estacionalidad, periodo)
        """
        try:
            from statsmodels.tsa.stattools import acf
        except ImportError:
            return False, 0

        if len(series) < max_lag:
            max_lag = len(series) // 2

        # Calcular autocorrelacion
        acf_values = acf(series.dropna(), nlags=min(max_lag, len(series) - 1))

        # Buscar picos significativos
        threshold = 2 / np.sqrt(len(series))

        # Buscar periodos comunes
        common_periods = [7, 12, 30, 52, 365]  # semanal, mensual, etc.

        for period in common_periods:
            if period < len(acf_values) and acf_values[period] > threshold:
                return True, period

        # Buscar cualquier pico significativo
        peaks = []
        for i in range(2, len(acf_values) - 1):
            if (acf_values[i] > acf_values[i-1] and
                acf_values[i] > acf_values[i+1] and
                acf_values[i] > threshold):
                peaks.append((i, acf_values[i]))

        if peaks:
            best_period = max(peaks, key=lambda x: x[1])[0]
            return True, best_period

        return False, 0

    def _find_best_order(
        self,
        series: pd.Series,
        seasonal_period: int
    ) -> Tuple[Tuple[int, int, int], Tuple[int, int, int, int]]:
        """
        Encuentra los mejores ordenes para SARIMA.
        """
        try:
            from statsmodels.tsa.statespace.sarimax import SARIMAX
        except ImportError:
            return self.order, self.seasonal_order

        max_p = self.config.hyperparameters.get("max_p", 3)
        max_d = self.config.hyperparameters.get("max_d", 2)
        max_q = self.config.hyperparameters.get("max_q", 3)
        max_P = self.config.hyperparameters.get("max_P", 2)
        max_D = self.config.hyperparameters.get("max_D", 1)
        max_Q = self.config.hyperparameters.get("max_Q", 2)
        criterion = self.config.hyperparameters.get("information_criterion", "aic")

        best_order = (1, 1, 1)
        best_seasonal = (1, 1, 1, seasonal_period)
        best_score = float('inf')

        logger.info(f"Buscando mejor orden SARIMA con periodo estacional {seasonal_period}...")

        # Busqueda simplificada para evitar tiempos muy largos
        for p in range(min(max_p + 1, 3)):
            for d in range(min(max_d + 1, 2)):
                for q in range(min(max_q + 1, 3)):
                    for P in range(min(max_P + 1, 2)):
                        for D in range(min(max_D + 1, 2)):
                            for Q in range(min(max_Q + 1, 2)):
                                if p == 0 and q == 0 and P == 0 and Q == 0:
                                    continue

                                try:
                                    with warnings.catch_warnings():
                                        warnings.simplefilter("ignore")

                                        model = SARIMAX(
                                            series,
                                            order=(p, d, q),
                                            seasonal_order=(P, D, Q, seasonal_period),
                                            enforce_stationarity=False,
                                            enforce_invertibility=False
                                        )
                                        fitted = model.fit(disp=False, maxiter=100)

                                        score = fitted.aic if criterion == 'aic' else fitted.bic

                                        if score < best_score:
                                            best_score = score
                                            best_order = (p, d, q)
                                            best_seasonal = (P, D, Q, seasonal_period)

                                except Exception:
                                    continue

        logger.info(
            f"Mejor orden: SARIMA{best_order}{best_seasonal}, "
            f"{criterion.upper()}: {best_score:.2f}"
        )

        return best_order, best_seasonal

    def _train(
        self,
        X_train: Union[pd.DataFrame, np.ndarray],
        y_train: Union[pd.Series, np.ndarray]
    ) -> None:
        """Entrena el modelo SARIMA."""
        try:
            from statsmodels.tsa.statespace.sarimax import SARIMAX
        except ImportError:
            raise ImportError(
                "Se requiere statsmodels para SARIMA. "
                "Instalar con: pip install statsmodels"
            )

        # Convertir a serie
        if isinstance(y_train, np.ndarray):
            self.series = pd.Series(y_train)
        else:
            self.series = y_train.copy()

        # Detectar estacionalidad
        seasonal_period = self.config.hyperparameters.get("seasonal_period", 12)
        has_seasonality, detected_period = self.detect_seasonality(self.series)

        if has_seasonality:
            self.seasonality_detected = True
            seasonal_period = detected_period
            logger.info(f"Estacionalidad detectada con periodo {seasonal_period}")
        else:
            logger.info("No se detecto estacionalidad clara")

        # Buscar mejor orden si esta configurado
        if self.config.hyperparameters.get("auto_order", True):
            self.order, self.seasonal_order = self._find_best_order(
                self.series, seasonal_period
            )
        else:
            self.seasonal_order = (
                self.seasonal_order[0],
                self.seasonal_order[1],
                self.seasonal_order[2],
                seasonal_period
            )

        # Entrenar modelo
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")

            sarima_model = SARIMAX(
                self.series,
                order=self.order,
                seasonal_order=self.seasonal_order,
                enforce_stationarity=False,
                enforce_invertibility=False
            )
            self.model = sarima_model.fit(disp=False)

        self.fitted_order = self.order
        self.fitted_seasonal_order = self.seasonal_order
        self.aic = self.model.aic
        self.bic = self.model.bic

        logger.info(
            f"SARIMA{self.order}{self.seasonal_order} entrenado. "
            f"AIC: {self.aic:.2f}, BIC: {self.bic:.2f}"
        )

    def _predict(
        self,
        X: Union[pd.DataFrame, np.ndarray]
    ) -> np.ndarray:
        """Retorna valores ajustados."""
        if self.model is None:
            raise ValueError("Modelo no entrenado")

        return self.model.fittedvalues.values

    def _get_feature_importance(self) -> Dict[str, float]:
        """Retorna parametros del modelo."""
        if self.model is None:
            return {}

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
            periods: Numero de periodos a predecir (RN-03.03: max 6 meses)
            last_date: Ultima fecha conocida
            freq: Frecuencia de prediccion

        Returns:
            PredictionResult con predicciones e intervalos de confianza
        """
        if self.model is None:
            raise ValueError("Modelo no entrenado")

        # Validar limite de periodos (RN-03.03: max 6 meses)
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
            model_type=f"SARIMA{self.fitted_order}{self.fitted_seasonal_order}"
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
        self.freq = pd.infer_freq(df[date_col])

        # Crear serie con indice de fecha
        series = df.set_index(date_col)[target_col]

        if validation_split:
            # Split temporal
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
            forecast = self.model.get_forecast(steps=len(test_series))
            y_pred = forecast.predicted_mean.values
            y_true = test_series.values

            from sklearn.metrics import r2_score, mean_squared_error, mean_absolute_error

            self.metrics = ModelMetrics()
            self.metrics.r2_score = float(r2_score(y_true, y_pred))
            self.metrics.mse = float(mean_squared_error(y_true, y_pred))
            self.metrics.rmse = float(np.sqrt(self.metrics.mse))
            self.metrics.mae = float(mean_absolute_error(y_true, y_pred))

            mask = y_true != 0
            if mask.any():
                self.metrics.mape = float(
                    np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask])) * 100
                )

            self.metrics.training_samples = len(train_series)
            self.metrics.test_samples = len(test_series)
            self.metrics.validate_thresholds(self.R2_THRESHOLD)
        else:
            residuals = self.model.resid
            self.metrics = ModelMetrics()
            self.metrics.rmse = float(np.std(residuals))
            self.metrics.mae = float(np.mean(np.abs(residuals)))
            self.metrics.training_samples = len(train_series)

        logger.info(
            f"SARIMA entrenado. R2: {self.metrics.r2_score:.4f}, "
            f"RMSE: {self.metrics.rmse:.4f}"
        )

        return self.metrics

    def get_seasonal_decomposition(self) -> Dict[str, Any]:
        """
        Descompone la serie en tendencia, estacionalidad y residuos.
        """
        if self.series is None:
            return {}

        try:
            from statsmodels.tsa.seasonal import seasonal_decompose

            period = self.seasonal_order[3] if self.fitted_seasonal_order else 12

            decomposition = seasonal_decompose(
                self.series.dropna(),
                period=period,
                extrapolate_trend='freq'
            )

            return {
                "trend": list(decomposition.trend.dropna().values),
                "seasonal": list(decomposition.seasonal.dropna().values),
                "residual": list(decomposition.resid.dropna().values),
                "period": period
            }
        except Exception as e:
            logger.warning(f"Error en descomposicion estacional: {str(e)}")
            return {}

    def get_model_summary(self) -> Dict[str, Any]:
        """Retorna resumen completo del modelo."""
        summary = self.get_model_info()
        summary.update({
            "order": self.fitted_order,
            "seasonal_order": self.fitted_seasonal_order,
            "seasonality_detected": self.seasonality_detected,
            "aic": self.aic,
            "bic": self.bic,
            "seasonal_decomposition": self.get_seasonal_decomposition()
        })
        return summary
