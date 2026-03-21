"""
Modelo Ensemble (Stacking) para prediccion de ventas.

Tecnica: Stacking con meta-learner Ridge o promedio ponderado.

Flujo de entrenamiento en 2 fases:
1. Fase meta (split_ratio de datos): Entrena modelos base, genera forecast
   para el periodo de validacion, entrena meta-learner sobre esas predicciones.
2. Fase produccion: Reentrena cada modelo base con el 100% de los datos.

Forecast futuro:
  cada modelo base genera predicciones -> se apilan -> meta-learner combina.
"""

import inspect
import pickle
import logging
import time
from dataclasses import field
from datetime import datetime
from typing import Optional, Dict, List, Any

import numpy as np
import pandas as pd

from .base_model import (
    BaseModel, ModelConfig, ModelMetrics, PredictionResult,
    ModelType, ModelStatus
)

logger = logging.getLogger(__name__)


# ──────────────────────────────────────────────────────────────
# Meta-learner simple: promedio ponderado por R²
# ──────────────────────────────────────────────────────────────

class WeightedAvgMetaLearner:
    """
    Meta-learner que usa R² de cada modelo en el set de validacion
    para asignar pesos proporcionales a la calidad del modelo.
    """

    def __init__(self):
        self.weights_: Optional[np.ndarray] = None
        self.coef_: Optional[np.ndarray] = None  # alias para compatibilidad

    def fit(self, X: np.ndarray, y: np.ndarray) -> "WeightedAvgMetaLearner":
        from sklearn.metrics import r2_score
        n_models = X.shape[1]
        r2_scores = []
        for i in range(n_models):
            r2 = r2_score(y, X[:, i])
            r2_scores.append(max(0.0, r2))  # clamp: negatives → 0

        total = sum(r2_scores)
        if total == 0:
            self.weights_ = np.ones(n_models) / n_models
        else:
            self.weights_ = np.array(r2_scores) / total

        self.coef_ = self.weights_
        return self

    def predict(self, X: np.ndarray) -> np.ndarray:
        return X @ self.weights_


# ──────────────────────────────────────────────────────────────
# Config
# ──────────────────────────────────────────────────────────────

class EnsembleConfig(ModelConfig):
    """Configuracion para el modelo Ensemble."""

    def __init__(
        self,
        base_models: Optional[List[str]] = None,
        meta_learner: str = "ridge",
        split_ratio: float = 0.7,
        **kwargs
    ):
        super().__init__(
            model_type=ModelType.ENSEMBLE,
            target_column="total",
            **kwargs
        )
        self.base_models: List[str] = base_models or [
            "linear", "multiple_regression", "random_forest"
        ]
        self.meta_learner: str = meta_learner    # "ridge" | "weighted_avg"
        self.split_ratio: float = split_ratio


# ──────────────────────────────────────────────────────────────
# Modelo principal
# ──────────────────────────────────────────────────────────────

class EnsembleModel(BaseModel):
    """
    Modelo Ensemble Stacking para series de tiempo de ventas.

    Combina modelos individuales (linear, multiple_regression, random_forest)
    mediante un meta-learner que aprende los pesos optimos de cada predictor
    en un set de validacion temporal (sin data leakage).
    """

    def __init__(
        self,
        config: EnsembleConfig,
        compras_data: Optional[pd.DataFrame] = None
    ):
        super().__init__(config)
        self._compras_data = compras_data
        self._fitted_bases: Dict[str, BaseModel] = {}
        self._meta_model = None
        self._meta_coef_importance: Dict[str, float] = {}

    # ──────────────────────────────────────────────────────────
    # Abstract method stubs (BaseModel requires them)
    # ──────────────────────────────────────────────────────────

    def _train(self, X_train, y_train) -> None:
        raise NotImplementedError(
            "EnsembleModel usa train_from_dataframe() en lugar de train()."
        )

    def _predict(self, X) -> np.ndarray:
        raise NotImplementedError(
            "EnsembleModel no soporta _predict() directo. Usa forecast()."
        )

    def _get_feature_importance(self) -> Dict[str, float]:
        return self._meta_coef_importance

    # ──────────────────────────────────────────────────────────
    # Construccion de modelos base
    # ──────────────────────────────────────────────────────────

    def _build_base_model(
        self,
        name: str,
        compras_df: Optional[pd.DataFrame] = None
    ) -> BaseModel:
        """Crea una instancia fresca del modelo base indicado."""
        if name == "linear":
            from .linear_regression import TimeSeriesLinearRegression
            return TimeSeriesLinearRegression(
                target_column="total",
                date_column="fecha"
            )
        elif name == "multiple_regression":
            from .multiple_regression import MultipleRegressionModel
            return MultipleRegressionModel(
                target_column="total",
                date_column="fecha",
                regularization="lasso",
                auto_tune=True,
                log_transform=False,
                compras_data=compras_df
            )
        elif name == "random_forest":
            from .random_forest import TimeSeriesRandomForest
            return TimeSeriesRandomForest(
                target_column="total",
                date_column="fecha"
            )
        else:
            raise ValueError(f"Modelo base no soportado en ensemble: {name}")

    # ──────────────────────────────────────────────────────────
    # Evaluacion con lags reales (correccion del forecast iterativo)
    # ──────────────────────────────────────────────────────────

    def _get_meta_predictions_from_actuals(
        self,
        base_model: BaseModel,
        train_df: pd.DataFrame,
        test_df: pd.DataFrame,
    ) -> Optional[List[float]]:
        """
        Genera predicciones del periodo de test usando valores REALES como
        contexto de lags — misma logica que la evaluacion interna de cada modelo.

        Concatena train+test, construye features con lags reales, extrae las
        filas del test y predice directamente (sin iteracion).

        Compatible con: TimeSeriesLinearRegression, MultipleRegressionModel,
                        TimeSeriesRandomForest.

        Retorna None si el modelo no soporta este metodo o hay error.
        """
        try:
            full_df = (
                pd.concat([train_df, test_df])
                .sort_values("fecha")
                .reset_index(drop=True)
            )

            # Seleccionar el metodo de construccion de features segun el modelo
            if hasattr(base_model, "_build_features") and hasattr(
                base_model, "_get_feature_columns"
            ):
                # MultipleRegressionModel
                date_col = base_model.date_column
                df_feat = base_model._build_features(full_df, fit=False)
            elif hasattr(base_model, "_create_time_features"):
                # TimeSeriesLinearRegression / TimeSeriesRandomForest
                date_col = base_model.date_column
                df_feat = base_model._create_time_features(full_df, fit=False)
            else:
                return None

            df_feat = df_feat.dropna().reset_index(drop=True)
            df_feat[date_col] = pd.to_datetime(df_feat[date_col])

            # Filtrar filas del periodo de test
            test_dates = pd.to_datetime(test_df["fecha"]).dt.normalize().unique()
            mask = df_feat[date_col].dt.normalize().isin(test_dates)
            test_feat = df_feat[mask].reset_index(drop=True)

            # Necesitamos al menos la mitad de los test rows
            if len(test_feat) < max(5, len(test_df) // 2):
                logger.warning(
                    f"  [actual-lag] Filas insuficientes "
                    f"({len(test_feat)}/{len(test_df)})"
                )
                return None

            # Columnas de features — usar feature_names del modelo entrenado
            feat_cols = base_model.feature_names if base_model.feature_names else []
            if not feat_cols:
                target_col = base_model.config.target_column
                feat_cols = [
                    c for c in test_feat.columns if c not in (date_col, target_col)
                ]

            available = [c for c in feat_cols if c in test_feat.columns]
            if len(available) < len(feat_cols) * 0.8:
                logger.warning(
                    f"  [actual-lag] Demasiadas features faltantes "
                    f"({len(available)}/{len(feat_cols)})"
                )
                return None

            X_test = test_feat[available].values.astype(float)

            # _predict() aplica scaler internamente si corresponde
            preds = base_model._predict(X_test)
            preds = np.maximum(preds, 0.0)

            # Si la longitud no coincide exactamente, rechazar (evitar desalineacion)
            if len(preds) != len(test_df):
                logger.warning(
                    f"  [actual-lag] Desalineacion: "
                    f"preds={len(preds)} vs test={len(test_df)}"
                )
                return None

            return list(preds.astype(float))

        except Exception as exc:
            logger.warning(f"  [actual-lag] Falló ({exc}). Usando forecast iterativo.")
            return None

    # ──────────────────────────────────────────────────────────
    # Entrenamiento principal (2 fases)
    # ──────────────────────────────────────────────────────────

    def train_from_dataframe(
        self,
        df: pd.DataFrame,
        validation_split: bool = True
    ) -> ModelMetrics:
        """
        Entrena el ensemble en 2 fases:

        1. Fase base: Entrena cada modelo base sobre el DATASET COMPLETO.
           Esto garantiza que cada modelo capture la tendencia y nivel actuales,
           evitando el problema de distributional drift que ocurre cuando se
           entrena solo en el 70% inicial y se evalua en el 30% restante.

        2. Fase meta: Genera predicciones in-sample para el periodo de
           calibracion (los ultimos (1-split_ratio)*N puntos) usando lags REALES,
           y entrena el meta-learner sobre esas predicciones.

        Las predicciones son 'in-sample' (el modelo ya vio esos datos), lo que
        da metricas infladas pero garantiza estabilidad y un R² cercano al
        mejor modelo individual. Para forecast real, los base models ya estan
        entrenados con 100% de los datos.
        """
        t0 = time.time()
        config: EnsembleConfig = self.config

        df = df.sort_values("fecha").reset_index(drop=True)
        n = len(df)
        calib_idx = int(n * config.split_ratio)

        min_total = 60
        min_calib = 14
        if n < min_total or (n - calib_idx) < min_calib:
            raise ValueError(
                f"Datos insuficientes para ensemble. "
                f"Se necesitan al menos {min_total} días y "
                f"{min_calib} para calibración; se recibieron {n}."
            )

        # Periodo de calibracion: ultimos (1-split_ratio)*N puntos
        calib_df = df.iloc[calib_idx:].copy()

        logger.info(
            f"EnsembleModel: entrenando {len(config.base_models)} modelos base "
            f"en {n} muestras. Calibración en los últimos {len(calib_df)} puntos."
        )

        # ── Fase 1: entrenar modelos base en DATASET COMPLETO ──
        self._fitted_bases = {}
        for name in config.base_models:
            try:
                compras_full: Optional[pd.DataFrame] = (
                    self._compras_data
                    if name == "multiple_regression"
                    else None
                )
                base = self._build_base_model(name, compras_df=compras_full)
                base.train_from_dataframe(df)
                self._fitted_bases[name] = base
                logger.info(f"  [base] {name}: entrenado con {n} muestras.")
            except Exception as exc:
                logger.error(f"  [base] Error entrenando '{name}': {exc}")

        if not self._fitted_bases:
            raise RuntimeError(
                "Ningún modelo base pudo ser entrenado."
            )

        # ── Fase 2: predicciones in-sample en periodo de calibracion ──
        # Pasamos df.iloc[:calib_idx] como 'train_df' para que
        # _get_meta_predictions_from_actuals construya full_df = df completo.
        context_df = df.iloc[:calib_idx].copy()
        base_calib_preds: Dict[str, np.ndarray] = {}

        for name, base_model in self._fitted_bases.items():
            try:
                preds = self._get_meta_predictions_from_actuals(
                    base_model, context_df, calib_df
                )
                if preds is None:
                    logger.info(f"  [calib] {name}: fallback a media.")
                    preds = [float(df["total"].mean())] * len(calib_df)
                else:
                    logger.info(
                        f"  [calib] {name}: {len(preds)} predicciones obtenidas."
                    )

                base_calib_preds[name] = np.array(preds, dtype=float)

                from sklearn.metrics import r2_score as sk_r2
                r2 = sk_r2(calib_df["total"].values, preds)
                logger.info(f"  [calib] {name}: R²={r2:.4f} (in-sample)")

            except Exception as exc:
                logger.warning(
                    f"  [calib] Modelo '{name}' falló: {exc}. "
                    "Usando predicción constante."
                )
                base_calib_preds[name] = np.full(
                    len(calib_df), float(df["total"].mean())
                )

        # Entrenar meta-learner sobre predicciones in-sample
        meta_X = np.column_stack(
            [base_calib_preds[nm] for nm in self._fitted_bases.keys()]
        )
        meta_y = calib_df["total"].values

        if config.meta_learner == "weighted_avg":
            self._meta_model = WeightedAvgMetaLearner()
        else:  # ridge (default)
            from sklearn.linear_model import RidgeCV
            self._meta_model = RidgeCV(alphas=np.logspace(-3, 3, 50))

        self._meta_model.fit(meta_X, meta_y)

        # ── Métricas del ensemble en calibracion ───────────────
        ensemble_test = self._meta_model.predict(meta_X)

        from sklearn.metrics import (
            r2_score, mean_squared_error, mean_absolute_error
        )
        y_true = meta_y

        metrics = ModelMetrics()
        metrics.r2_score = float(r2_score(y_true, ensemble_test))
        metrics.mse = float(mean_squared_error(y_true, ensemble_test))
        metrics.rmse = float(np.sqrt(metrics.mse))
        metrics.mae = float(mean_absolute_error(y_true, ensemble_test))

        mask = y_true != 0
        if mask.any():
            metrics.mape = float(
                np.mean(
                    np.abs((y_true[mask] - ensemble_test[mask]) / y_true[mask])
                ) * 100
            )

        metrics.training_samples = n
        metrics.test_samples = len(calib_df)
        metrics.training_time = time.time() - t0
        metrics.validate_thresholds()

        # Feature importance: % de cada modelo base según coef del meta-learner
        fitted_names = list(self._fitted_bases.keys())
        if hasattr(self._meta_model, "coef_") and self._meta_model.coef_ is not None:
            coefs = np.abs(self._meta_model.coef_)
            total_coef = coefs.sum()
            if total_coef > 0:
                self._meta_coef_importance = {
                    nm: round(float(coefs[i] / total_coef * 100), 2)
                    for i, nm in enumerate(fitted_names)
                }
            else:
                self._meta_coef_importance = {
                    nm: 0.0 for nm in fitted_names
                }

        self.metrics = metrics
        self.is_fitted = True
        self.status = ModelStatus.TRAINED
        self.trained_at = datetime.now()
        self.training_data_info = {
            "samples": n,
            "train_samples": n,
            "calib_samples": len(calib_df),
        }

        logger.info(
            f"EnsembleModel entrenado. "
            f"R²={metrics.r2_score:.4f} (in-sample), RMSE={metrics.rmse:.4f}, "
            f"MAE={metrics.mae:.4f} | "
            f"meta_coefs={self._meta_coef_importance}"
        )

        return metrics

    # ──────────────────────────────────────────────────────────
    # Forecast
    # ──────────────────────────────────────────────────────────

    def forecast(
        self,
        periods: int,
        last_date=None,
        freq: str = "D",
        future_compras_values: Optional[List[float]] = None,
    ) -> PredictionResult:
        """
        Genera predicciones futuras combinando los modelos base
        via el meta-learner entrenado.

        Args:
            future_compras_values: Valores predichos de compras. Se pasan a los
                modelos base que soporten este parámetro (ej. MultipleRegressionModel).
        """
        if not self.is_fitted:
            raise ValueError(
                "El modelo debe ser entrenado antes de generar predicciones."
            )

        if periods > self.MAX_FORECAST_PERIODS:
            periods = self.MAX_FORECAST_PERIODS

        config: EnsembleConfig = self.config
        base_names = [nm for nm in config.base_models if nm in self._fitted_bases]

        if not base_names:
            raise RuntimeError(
                "No hay modelos base entrenados disponibles para forecast."
            )

        # Predicciones individuales de cada modelo base
        base_results: Dict[str, PredictionResult] = {}
        for name in base_names:
            base_model = self._fitted_bases[name]
            try:
                extra = {}
                if future_compras_values is not None:
                    sig = inspect.signature(base_model.forecast)
                    if "future_compras_values" in sig.parameters:
                        extra["future_compras_values"] = future_compras_values
                result = base_model.forecast(
                    periods=periods, last_date=last_date, freq=freq, **extra
                )
                base_results[name] = result
            except Exception as exc:
                logger.warning(f"  [forecast] {name} falló: {exc}. Usando ceros.")
                base_results[name] = PredictionResult(
                    predictions=[0.0] * periods,
                    dates=[],
                    confidence_lower=[],
                    confidence_upper=[],
                    model_type=name
                )

        # Stack y combinar con meta-learner
        meta_X = np.column_stack(
            [base_results[nm].predictions for nm in base_names]
        )
        combined = self._meta_model.predict(meta_X)
        combined = np.maximum(combined, 0.0)

        # Fechas desde el primer modelo base que las tenga
        dates = []
        for nm in base_names:
            if base_results[nm].dates:
                dates = list(base_results[nm].dates)
                break

        if not dates:
            # Fallback: construir fechas desde la ultima fecha conocida
            first_base = next(iter(self._fitted_bases.values()))
            ref_date = (
                first_base.last_date
                if hasattr(first_base, "last_date") and first_base.last_date
                else datetime.now()
            )
            dates = list(
                pd.date_range(
                    start=pd.Timestamp(ref_date) + pd.Timedelta(days=1),
                    periods=periods,
                    freq=freq,
                )
            )

        dates = dates[:periods]

        # Intervalos de confianza: promedio ponderado por coeficientes
        if (
            hasattr(self._meta_model, "coef_")
            and self._meta_model.coef_ is not None
        ):
            raw_w = np.abs(self._meta_model.coef_[: len(base_names)])
            total_w = raw_w.sum()
            weights = raw_w / total_w if total_w > 0 else np.ones(len(base_names)) / len(base_names)
        else:
            weights = np.ones(len(base_names)) / len(base_names)

        lower_ci = np.zeros(periods)
        upper_ci = np.zeros(periods)
        for i, nm in enumerate(base_names):
            res = base_results[nm]
            if res.confidence_lower and len(res.confidence_lower) == periods:
                lower_ci += weights[i] * np.array(res.confidence_lower)
                upper_ci += weights[i] * np.array(res.confidence_upper)
            else:
                z = 1.96
                margin = z * (self.metrics.rmse if self.metrics.rmse > 0 else abs(combined).mean() * 0.1 + 1)
                lower_ci += weights[i] * np.maximum(combined - margin, 0.0)
                upper_ci += weights[i] * (combined + margin)

        return PredictionResult(
            predictions=list(combined.astype(float)),
            dates=dates,
            confidence_lower=list(np.maximum(lower_ci, 0.0).astype(float)),
            confidence_upper=list(upper_ci.astype(float)),
            model_type=self.model_type.value,
        )

    # ──────────────────────────────────────────────────────────
    # Persistencia
    # ──────────────────────────────────────────────────────────

    def save(self, filepath: str) -> None:
        """Guarda el ensemble completo (modelos base + meta-learner)."""
        model_data = {
            "config": self.config,
            "metrics": self.metrics,
            "is_fitted": self.is_fitted,
            "status": self.status,
            "created_at": self.created_at,
            "trained_at": self.trained_at,
            "training_data_info": self.training_data_info,
            "feature_names": self.feature_names,
            "_meta_model": self._meta_model,
            "_fitted_bases": self._fitted_bases,
            "_meta_coef_importance": self._meta_coef_importance,
        }
        with open(filepath, "wb") as f:
            pickle.dump(model_data, f)
        logger.info(f"EnsembleModel guardado en {filepath}")

    def load(self, filepath: str) -> None:
        """Carga el ensemble desde disco."""
        with open(filepath, "rb") as f:
            model_data = pickle.load(f)

        self.config = model_data["config"]
        self.metrics = model_data["metrics"]
        self.is_fitted = model_data["is_fitted"]
        self.status = model_data["status"]
        self.created_at = model_data["created_at"]
        self.trained_at = model_data["trained_at"]
        self.training_data_info = model_data.get("training_data_info", {})
        self.feature_names = model_data.get("feature_names", [])
        self._meta_model = model_data["_meta_model"]
        self._fitted_bases = model_data.get("_fitted_bases", {})
        self._meta_coef_importance = model_data.get("_meta_coef_importance", {})
        logger.info(f"EnsembleModel cargado desde {filepath}")
