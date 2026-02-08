"""
Clase base abstracta para todos los modelos predictivos.
Define la interfaz comun que deben implementar todos los modelos.
"""

import pandas as pd
import numpy as np
from abc import ABC, abstractmethod
from typing import Optional, Dict, Any, List, Tuple, Union
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
import pickle
import json
import logging

logger = logging.getLogger(__name__)


class ModelType(str, Enum):
    """Tipos de modelos soportados."""
    LINEAR_REGRESSION = "linear_regression"
    ARIMA = "arima"
    SARIMA = "sarima"
    RANDOM_FOREST = "random_forest"
    XGBOOST = "xgboost"
    KMEANS = "kmeans"


class ModelStatus(str, Enum):
    """Estados del modelo."""
    CREATED = "created"
    TRAINING = "training"
    TRAINED = "trained"
    FAILED = "failed"
    DEPRECATED = "deprecated"


@dataclass
class ModelConfig:
    """Configuracion base para modelos."""
    model_type: ModelType
    target_column: str
    feature_columns: List[str] = field(default_factory=list)
    test_size: float = 0.3  # RN-03.01: 70/30 split
    random_state: int = 42
    hyperparameters: Dict[str, Any] = field(default_factory=dict)

    # Configuracion de serie de tiempo
    date_column: Optional[str] = None
    frequency: Optional[str] = None  # 'D', 'W', 'M', etc.
    forecast_periods: int = 30  # RN-03.03: hasta 6 meses


@dataclass
class ModelMetrics:
    """Metricas de evaluacion del modelo."""
    r2_score: float = 0.0
    rmse: float = 0.0
    mae: float = 0.0
    mape: float = 0.0
    mse: float = 0.0

    # Metricas adicionales
    training_samples: int = 0
    test_samples: int = 0
    training_time: float = 0.0

    # Validacion de reglas de negocio
    meets_r2_threshold: bool = False  # RN-03.02: R2 > 0.7

    def to_dict(self) -> Dict[str, Any]:
        """Convierte a diccionario."""
        return {
            "r2_score": round(self.r2_score, 4),
            "rmse": round(self.rmse, 4),
            "mae": round(self.mae, 4),
            "mape": round(self.mape, 4),
            "mse": round(self.mse, 4),
            "training_samples": self.training_samples,
            "test_samples": self.test_samples,
            "training_time": round(self.training_time, 2),
            "meets_r2_threshold": self.meets_r2_threshold
        }

    def validate_thresholds(self, r2_min: float = 0.7) -> bool:
        """
        Valida que las metricas cumplan los umbrales minimos.
        RN-03.02: R2 > 0.7 para considerar el modelo usable.
        """
        self.meets_r2_threshold = self.r2_score >= r2_min
        return self.meets_r2_threshold


@dataclass
class PredictionResult:
    """Resultado de una prediccion."""
    predictions: List[float] = field(default_factory=list)
    dates: List[datetime] = field(default_factory=list)
    confidence_lower: List[float] = field(default_factory=list)
    confidence_upper: List[float] = field(default_factory=list)
    confidence_level: float = 0.95
    model_type: str = ""
    model_id: Optional[int] = None
    created_at: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> Dict[str, Any]:
        """Convierte a diccionario."""
        return {
            "predictions": [
                {
                    "date": d.isoformat() if isinstance(d, datetime) else str(d),
                    "value": round(p, 2),
                    "confidence_lower": round(cl, 2) if cl else None,
                    "confidence_upper": round(cu, 2) if cu else None
                }
                for d, p, cl, cu in zip(
                    self.dates,
                    self.predictions,
                    self.confidence_lower or [None] * len(self.predictions),
                    self.confidence_upper or [None] * len(self.predictions)
                )
            ],
            "confidence_level": self.confidence_level,
            "model_type": self.model_type,
            "model_id": self.model_id,
            "count": len(self.predictions),
            "created_at": self.created_at.isoformat()
        }

    def to_dataframe(self) -> pd.DataFrame:
        """Convierte a DataFrame."""
        data = {
            "date": self.dates,
            "prediction": self.predictions
        }
        if self.confidence_lower:
            data["confidence_lower"] = self.confidence_lower
        if self.confidence_upper:
            data["confidence_upper"] = self.confidence_upper
        return pd.DataFrame(data)


class BaseModel(ABC):
    """
    Clase base abstracta para modelos predictivos.

    Todos los modelos deben heredar de esta clase e implementar
    los metodos abstractos: _train, _predict, _evaluate.
    """

    # Umbral minimo de R2 segun RN-03.02
    R2_THRESHOLD = 0.7

    # Maximo de periodos de prediccion (6 meses) segun RN-03.03
    MAX_FORECAST_PERIODS = 180

    def __init__(self, config: ModelConfig):
        self.config = config
        self.model = None
        self.is_fitted = False
        self.status = ModelStatus.CREATED
        self.metrics = ModelMetrics()
        self.feature_names: List[str] = []
        self.training_data_info: Dict[str, Any] = {}
        self.created_at = datetime.now()
        self.trained_at: Optional[datetime] = None

    @property
    def model_type(self) -> ModelType:
        """Retorna el tipo de modelo."""
        return self.config.model_type

    @abstractmethod
    def _train(
        self,
        X_train: Union[pd.DataFrame, np.ndarray],
        y_train: Union[pd.Series, np.ndarray]
    ) -> None:
        """
        Implementacion especifica del entrenamiento.
        Debe ser implementado por cada modelo.
        """
        pass

    @abstractmethod
    def _predict(
        self,
        X: Union[pd.DataFrame, np.ndarray]
    ) -> np.ndarray:
        """
        Implementacion especifica de la prediccion.
        Debe ser implementado por cada modelo.
        """
        pass

    @abstractmethod
    def _get_feature_importance(self) -> Dict[str, float]:
        """
        Retorna la importancia de las features.
        Debe ser implementado por cada modelo.
        """
        pass

    def train(
        self,
        X: Union[pd.DataFrame, np.ndarray],
        y: Union[pd.Series, np.ndarray],
        validation_split: bool = True
    ) -> ModelMetrics:
        """
        Entrena el modelo con los datos proporcionados.

        Args:
            X: Features de entrenamiento
            y: Variable objetivo
            validation_split: Si dividir datos en train/test (RN-03.01)

        Returns:
            ModelMetrics: Metricas del modelo entrenado
        """
        import time
        start_time = time.time()

        self.status = ModelStatus.TRAINING
        logger.info(f"Iniciando entrenamiento de {self.model_type.value}")

        try:
            # Guardar nombres de features
            if isinstance(X, pd.DataFrame):
                self.feature_names = list(X.columns)

            # Dividir datos segun RN-03.01 (70/30)
            if validation_split:
                X_train, X_test, y_train, y_test = self._split_data(X, y)
            else:
                X_train, X_test = X, X
                y_train, y_test = y, y

            # Entrenar modelo
            self._train(X_train, y_train)
            self.is_fitted = True

            # Evaluar modelo
            if validation_split:
                self.metrics = self._evaluate(X_test, y_test)
            else:
                self.metrics = self._evaluate(X_train, y_train)

            self.metrics.training_samples = len(X_train)
            self.metrics.test_samples = len(X_test)
            self.metrics.training_time = time.time() - start_time

            # Validar umbral R2 (RN-03.02)
            self.metrics.validate_thresholds(self.R2_THRESHOLD)

            self.status = ModelStatus.TRAINED
            self.trained_at = datetime.now()

            # Guardar info de datos de entrenamiento
            self.training_data_info = {
                "samples": len(X),
                "features": len(self.feature_names),
                "train_samples": self.metrics.training_samples,
                "test_samples": self.metrics.test_samples
            }

            logger.info(
                f"Modelo {self.model_type.value} entrenado. "
                f"R2: {self.metrics.r2_score:.4f}, RMSE: {self.metrics.rmse:.4f}"
            )

            return self.metrics

        except Exception as e:
            self.status = ModelStatus.FAILED
            logger.error(f"Error entrenando modelo: {str(e)}")
            raise

    def predict(
        self,
        X: Union[pd.DataFrame, np.ndarray],
        return_confidence: bool = False
    ) -> Union[np.ndarray, Tuple[np.ndarray, np.ndarray, np.ndarray]]:
        """
        Realiza predicciones con el modelo entrenado.

        Args:
            X: Features para prediccion
            return_confidence: Si retornar intervalos de confianza

        Returns:
            Predicciones (y opcionalmente intervalos de confianza)
        """
        if not self.is_fitted:
            raise ValueError("El modelo no ha sido entrenado")

        predictions = self._predict(X)

        if return_confidence:
            lower, upper = self._get_confidence_intervals(predictions)
            return predictions, lower, upper

        return predictions

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
            freq: Frecuencia ('D'=diario, 'W'=semanal, 'M'=mensual)

        Returns:
            PredictionResult: Resultado con predicciones
        """
        # Validar maximo de periodos (RN-03.03)
        if periods > self.MAX_FORECAST_PERIODS:
            logger.warning(
                f"Periodos solicitados ({periods}) excede maximo ({self.MAX_FORECAST_PERIODS}). "
                "Limitando a 6 meses."
            )
            periods = self.MAX_FORECAST_PERIODS

        # Implementacion por defecto, puede ser sobrescrita
        raise NotImplementedError(
            "Forecast debe ser implementado por modelos de series de tiempo"
        )

    def _split_data(
        self,
        X: Union[pd.DataFrame, np.ndarray],
        y: Union[pd.Series, np.ndarray]
    ) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        """
        Divide datos en entrenamiento y prueba.
        RN-03.01: Division 70/30
        """
        from sklearn.model_selection import train_test_split

        return train_test_split(
            X, y,
            test_size=self.config.test_size,
            random_state=self.config.random_state
        )

    def _evaluate(
        self,
        X_test: Union[pd.DataFrame, np.ndarray],
        y_test: Union[pd.Series, np.ndarray]
    ) -> ModelMetrics:
        """Evalua el modelo con datos de prueba."""
        from sklearn.metrics import r2_score, mean_squared_error, mean_absolute_error

        y_pred = self._predict(X_test)
        y_true = np.array(y_test)

        # Calcular metricas
        metrics = ModelMetrics()
        metrics.r2_score = float(r2_score(y_true, y_pred))
        metrics.mse = float(mean_squared_error(y_true, y_pred))
        metrics.rmse = float(np.sqrt(metrics.mse))
        metrics.mae = float(mean_absolute_error(y_true, y_pred))

        # MAPE (evitar division por cero)
        mask = y_true != 0
        if mask.any():
            metrics.mape = float(
                np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask])) * 100
            )

        return metrics

    def _get_confidence_intervals(
        self,
        predictions: np.ndarray,
        confidence: float = 0.95
    ) -> Tuple[np.ndarray, np.ndarray]:
        """
        Calcula intervalos de confianza para las predicciones.
        Implementacion por defecto usando error estandar.
        """
        # Estimacion simple usando RMSE como proxy del error estandar
        std_error = self.metrics.rmse if self.metrics.rmse > 0 else np.std(predictions) * 0.1

        from scipy import stats
        z_score = stats.norm.ppf((1 + confidence) / 2)

        margin = z_score * std_error
        lower = predictions - margin
        upper = predictions + margin

        return lower, upper

    def get_feature_importance(self) -> Dict[str, float]:
        """Retorna la importancia de las features."""
        if not self.is_fitted:
            return {}
        return self._get_feature_importance()

    def save(self, filepath: str) -> None:
        """Guarda el modelo en disco."""
        model_data = {
            "model": self.model,
            "config": self.config,
            "metrics": self.metrics,
            "feature_names": self.feature_names,
            "is_fitted": self.is_fitted,
            "status": self.status,
            "created_at": self.created_at,
            "trained_at": self.trained_at,
            "training_data_info": self.training_data_info
        }

        with open(filepath, 'wb') as f:
            pickle.dump(model_data, f)

        logger.info(f"Modelo guardado en {filepath}")

    def load(self, filepath: str) -> None:
        """Carga el modelo desde disco."""
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

        logger.info(f"Modelo cargado desde {filepath}")

    def get_model_info(self) -> Dict[str, Any]:
        """Retorna informacion del modelo."""
        return {
            "model_type": self.model_type.value,
            "status": self.status.value,
            "is_fitted": self.is_fitted,
            "created_at": self.created_at.isoformat(),
            "trained_at": self.trained_at.isoformat() if self.trained_at else None,
            "metrics": self.metrics.to_dict() if self.is_fitted else None,
            "feature_names": self.feature_names,
            "config": {
                "target_column": self.config.target_column,
                "test_size": self.config.test_size,
                "hyperparameters": self.config.hyperparameters
            },
            "training_data_info": self.training_data_info
        }

    def should_retrain(self, new_data_count: int) -> bool:
        """
        Determina si el modelo debe ser reentrenado.
        RN-03.04: Reentrenar con 20% mas de datos.
        """
        if not self.is_fitted:
            return True

        original_count = self.training_data_info.get("samples", 0)
        if original_count == 0:
            return True

        increase_ratio = (new_data_count - original_count) / original_count
        return increase_ratio >= 0.20  # 20% mas de datos
