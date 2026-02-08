"""
Modulo de metricas de evaluacion para modelos predictivos.
Implementa RMSE, MAE, R2 y otras metricas segun requisitos.
"""

import pandas as pd
import numpy as np
from typing import Dict, Any, List, Optional, Tuple, Union
from dataclasses import dataclass, field
import logging

logger = logging.getLogger(__name__)


@dataclass
class RegressionMetrics:
    """Metricas de evaluacion para regresion."""
    r2_score: float = 0.0
    adjusted_r2: float = 0.0
    rmse: float = 0.0
    mae: float = 0.0
    mape: float = 0.0
    mse: float = 0.0
    medae: float = 0.0  # Median Absolute Error
    max_error: float = 0.0
    explained_variance: float = 0.0

    def to_dict(self) -> Dict[str, float]:
        return {
            "r2_score": round(self.r2_score, 4),
            "adjusted_r2": round(self.adjusted_r2, 4),
            "rmse": round(self.rmse, 4),
            "mae": round(self.mae, 4),
            "mape": round(self.mape, 2),
            "mse": round(self.mse, 4),
            "medae": round(self.medae, 4),
            "max_error": round(self.max_error, 4),
            "explained_variance": round(self.explained_variance, 4)
        }


@dataclass
class ForecastMetrics:
    """Metricas especificas para pronosticos."""
    rmse: float = 0.0
    mae: float = 0.0
    mape: float = 0.0
    smape: float = 0.0  # Symmetric MAPE
    mase: float = 0.0   # Mean Absolute Scaled Error
    tracking_signal: float = 0.0
    bias: float = 0.0

    def to_dict(self) -> Dict[str, float]:
        return {
            "rmse": round(self.rmse, 4),
            "mae": round(self.mae, 4),
            "mape": round(self.mape, 2),
            "smape": round(self.smape, 2),
            "mase": round(self.mase, 4),
            "tracking_signal": round(self.tracking_signal, 4),
            "bias": round(self.bias, 4)
        }


@dataclass
class ModelComparisonResult:
    """Resultado de comparacion de modelos."""
    model_name: str
    metrics: Dict[str, float]
    rank: int = 0
    is_best: bool = False
    meets_threshold: bool = False  # RN-03.02: R2 > 0.7

    def to_dict(self) -> Dict[str, Any]:
        return {
            "model_name": self.model_name,
            "metrics": self.metrics,
            "rank": self.rank,
            "is_best": self.is_best,
            "meets_threshold": self.meets_threshold
        }


def calculate_regression_metrics(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    n_features: int = 1
) -> RegressionMetrics:
    """
    Calcula metricas de regresion.

    Args:
        y_true: Valores reales
        y_pred: Valores predichos
        n_features: Numero de features (para adjusted R2)

    Returns:
        RegressionMetrics con todas las metricas
    """
    from sklearn.metrics import (
        r2_score, mean_squared_error, mean_absolute_error,
        median_absolute_error, max_error, explained_variance_score
    )

    y_true = np.array(y_true).flatten()
    y_pred = np.array(y_pred).flatten()

    n = len(y_true)

    metrics = RegressionMetrics()

    # R2 Score
    metrics.r2_score = float(r2_score(y_true, y_pred))

    # Adjusted R2
    if n > n_features + 1:
        metrics.adjusted_r2 = 1 - (1 - metrics.r2_score) * (n - 1) / (n - n_features - 1)
    else:
        metrics.adjusted_r2 = metrics.r2_score

    # MSE y RMSE
    metrics.mse = float(mean_squared_error(y_true, y_pred))
    metrics.rmse = float(np.sqrt(metrics.mse))

    # MAE
    metrics.mae = float(mean_absolute_error(y_true, y_pred))

    # Median Absolute Error
    metrics.medae = float(median_absolute_error(y_true, y_pred))

    # Max Error
    metrics.max_error = float(max_error(y_true, y_pred))

    # Explained Variance
    metrics.explained_variance = float(explained_variance_score(y_true, y_pred))

    # MAPE (evitar division por cero)
    mask = y_true != 0
    if mask.any():
        metrics.mape = float(
            np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask])) * 100
        )

    return metrics


def calculate_forecast_metrics(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    y_train: Optional[np.ndarray] = None
) -> ForecastMetrics:
    """
    Calcula metricas especificas para pronosticos de series de tiempo.

    Args:
        y_true: Valores reales
        y_pred: Valores predichos
        y_train: Datos de entrenamiento (para MASE)

    Returns:
        ForecastMetrics con metricas de pronostico
    """
    y_true = np.array(y_true).flatten()
    y_pred = np.array(y_pred).flatten()

    metrics = ForecastMetrics()

    # RMSE
    metrics.rmse = float(np.sqrt(np.mean((y_true - y_pred) ** 2)))

    # MAE
    metrics.mae = float(np.mean(np.abs(y_true - y_pred)))

    # MAPE
    mask = y_true != 0
    if mask.any():
        metrics.mape = float(
            np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask])) * 100
        )

    # SMAPE (Symmetric MAPE)
    denominator = (np.abs(y_true) + np.abs(y_pred)) / 2
    mask = denominator != 0
    if mask.any():
        metrics.smape = float(
            np.mean(np.abs(y_true[mask] - y_pred[mask]) / denominator[mask]) * 100
        )

    # MASE (Mean Absolute Scaled Error)
    if y_train is not None and len(y_train) > 1:
        naive_errors = np.abs(np.diff(y_train))
        scale = np.mean(naive_errors)
        if scale > 0:
            metrics.mase = float(metrics.mae / scale)

    # Bias
    metrics.bias = float(np.mean(y_pred - y_true))

    # Tracking Signal
    cumulative_error = np.cumsum(y_pred - y_true)
    if metrics.mae > 0:
        metrics.tracking_signal = float(cumulative_error[-1] / (len(y_true) * metrics.mae))

    return metrics


def compare_models(
    y_true: np.ndarray,
    predictions: Dict[str, np.ndarray],
    metric: str = "r2_score",
    r2_threshold: float = 0.7
) -> List[ModelComparisonResult]:
    """
    Compara multiples modelos y los rankea.

    Args:
        y_true: Valores reales
        predictions: Dict con nombre de modelo y predicciones
        metric: Metrica para ranking (r2_score, rmse, mae, mape)
        r2_threshold: Umbral minimo de R2 (RN-03.02)

    Returns:
        Lista de ModelComparisonResult ordenada por ranking
    """
    results = []

    for model_name, y_pred in predictions.items():
        metrics = calculate_regression_metrics(y_true, y_pred)

        result = ModelComparisonResult(
            model_name=model_name,
            metrics=metrics.to_dict(),
            meets_threshold=metrics.r2_score >= r2_threshold
        )
        results.append(result)

    # Ordenar por metrica
    reverse = metric in ["r2_score", "adjusted_r2", "explained_variance"]
    results.sort(key=lambda x: x.metrics.get(metric, 0), reverse=reverse)

    # Asignar rankings
    for i, result in enumerate(results):
        result.rank = i + 1
        result.is_best = (i == 0)

    return results


def cross_validate_model(
    model,
    X: np.ndarray,
    y: np.ndarray,
    cv: int = 5,
    scoring: str = "r2"
) -> Dict[str, Any]:
    """
    Realiza validacion cruzada de un modelo.

    Args:
        model: Modelo de scikit-learn o compatible
        X: Features
        y: Target
        cv: Numero de folds
        scoring: Metrica de scoring

    Returns:
        Dict con resultados de validacion cruzada
    """
    try:
        from sklearn.model_selection import cross_val_score, cross_val_predict
    except ImportError:
        raise ImportError("Se requiere scikit-learn")

    scores = cross_val_score(model, X, y, cv=cv, scoring=scoring)

    return {
        "cv_folds": cv,
        "scoring": scoring,
        "scores": list(scores),
        "mean_score": float(np.mean(scores)),
        "std_score": float(np.std(scores)),
        "min_score": float(np.min(scores)),
        "max_score": float(np.max(scores))
    }


class ModelEvaluator:
    """
    Clase para evaluacion completa de modelos.
    Implementa validacion segun reglas de negocio.
    """

    # RN-03.02: R2 minimo para considerar modelo usable
    R2_THRESHOLD = 0.7

    def __init__(self):
        self.evaluation_history: List[Dict[str, Any]] = []

    def evaluate(
        self,
        y_true: np.ndarray,
        y_pred: np.ndarray,
        model_name: str = "model",
        n_features: int = 1,
        y_train: Optional[np.ndarray] = None
    ) -> Dict[str, Any]:
        """
        Evaluacion completa de un modelo.

        Args:
            y_true: Valores reales
            y_pred: Valores predichos
            model_name: Nombre del modelo
            n_features: Numero de features
            y_train: Datos de entrenamiento (opcional)

        Returns:
            Dict con todas las metricas y validaciones
        """
        # Metricas de regresion
        reg_metrics = calculate_regression_metrics(y_true, y_pred, n_features)

        # Metricas de pronostico
        forecast_metrics = calculate_forecast_metrics(y_true, y_pred, y_train)

        # Validar umbral R2 (RN-03.02)
        meets_threshold = reg_metrics.r2_score >= self.R2_THRESHOLD

        result = {
            "model_name": model_name,
            "regression_metrics": reg_metrics.to_dict(),
            "forecast_metrics": forecast_metrics.to_dict(),
            "validation": {
                "r2_threshold": self.R2_THRESHOLD,
                "meets_threshold": meets_threshold,
                "recommendation": (
                    "Modelo apto para uso" if meets_threshold
                    else "Modelo no cumple umbral minimo de R2"
                )
            },
            "sample_size": len(y_true)
        }

        # Guardar en historial
        self.evaluation_history.append(result)

        return result

    def evaluate_time_series(
        self,
        y_true: np.ndarray,
        y_pred: np.ndarray,
        dates: Optional[List] = None,
        model_name: str = "model"
    ) -> Dict[str, Any]:
        """
        Evaluacion especifica para series de tiempo.

        Args:
            y_true: Valores reales
            y_pred: Valores predichos
            dates: Fechas correspondientes
            model_name: Nombre del modelo

        Returns:
            Dict con metricas y analisis temporal
        """
        basic_eval = self.evaluate(y_true, y_pred, model_name)

        # Analisis de errores por periodo
        errors = y_true - y_pred
        abs_errors = np.abs(errors)

        temporal_analysis = {
            "error_mean": float(np.mean(errors)),
            "error_std": float(np.std(errors)),
            "error_skewness": float(self._calculate_skewness(errors)),
            "max_overestimation": float(np.min(errors)),  # Negativo
            "max_underestimation": float(np.max(errors)),  # Positivo
            "error_autocorrelation": self._calculate_error_autocorrelation(errors)
        }

        basic_eval["temporal_analysis"] = temporal_analysis

        return basic_eval

    def _calculate_skewness(self, data: np.ndarray) -> float:
        """Calcula asimetria de los datos."""
        n = len(data)
        if n < 3:
            return 0.0
        mean = np.mean(data)
        std = np.std(data)
        if std == 0:
            return 0.0
        return float(np.mean(((data - mean) / std) ** 3))

    def _calculate_error_autocorrelation(
        self,
        errors: np.ndarray,
        lag: int = 1
    ) -> float:
        """Calcula autocorrelacion de los errores."""
        if len(errors) <= lag:
            return 0.0

        shifted = errors[lag:]
        original = errors[:-lag]

        if np.std(original) == 0 or np.std(shifted) == 0:
            return 0.0

        correlation = np.corrcoef(original, shifted)[0, 1]
        return float(correlation) if not np.isnan(correlation) else 0.0

    def generate_report(self) -> Dict[str, Any]:
        """
        Genera reporte consolidado de todas las evaluaciones.

        Returns:
            Dict con reporte completo
        """
        if not self.evaluation_history:
            return {"message": "No hay evaluaciones registradas"}

        # Encontrar mejor modelo
        best_model = max(
            self.evaluation_history,
            key=lambda x: x["regression_metrics"]["r2_score"]
        )

        # Modelos que cumplen umbral
        valid_models = [
            e for e in self.evaluation_history
            if e["validation"]["meets_threshold"]
        ]

        return {
            "total_evaluations": len(self.evaluation_history),
            "valid_models_count": len(valid_models),
            "best_model": {
                "name": best_model["model_name"],
                "r2_score": best_model["regression_metrics"]["r2_score"],
                "rmse": best_model["regression_metrics"]["rmse"]
            },
            "all_models": [
                {
                    "name": e["model_name"],
                    "r2_score": e["regression_metrics"]["r2_score"],
                    "meets_threshold": e["validation"]["meets_threshold"]
                }
                for e in self.evaluation_history
            ],
            "recommendations": self._generate_recommendations()
        }

    def _generate_recommendations(self) -> List[str]:
        """Genera recomendaciones basadas en evaluaciones."""
        recommendations = []

        if not self.evaluation_history:
            return ["Realizar evaluaciones de modelos"]

        # Verificar si algun modelo cumple umbral
        valid_models = [
            e for e in self.evaluation_history
            if e["validation"]["meets_threshold"]
        ]

        if not valid_models:
            recommendations.append(
                "Ningun modelo cumple el umbral minimo de R2=0.7. "
                "Considerar: mas datos, features adicionales, o modelos diferentes."
            )
        else:
            recommendations.append(
                f"{len(valid_models)} modelo(s) cumplen el umbral de calidad."
            )

        # Verificar overfitting
        for e in self.evaluation_history:
            if e["regression_metrics"]["r2_score"] > 0.99:
                recommendations.append(
                    f"Modelo '{e['model_name']}' podria tener overfitting (R2 muy alto)."
                )

        return recommendations

    def clear_history(self):
        """Limpia el historial de evaluaciones."""
        self.evaluation_history = []
