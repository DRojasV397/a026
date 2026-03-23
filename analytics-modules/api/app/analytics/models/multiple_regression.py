"""
Modelo de Regresion Multiple para prediccion de ventas.

v3 — Diseño log-consistente:
- Cuando log_transform=True, TODAS las features de ventas (lags, rolling, EMA)
  tambien se transforman a log-space: el modelo aprende log(y_t) ~ log(y_{t-1}),
  que es lineal y correcto para datos retail con varianza multiplicativa.
- Eliminado ventas_pct_7d (causaba outliers extremos cuando lag8 ~ 0).
- EMA guardado sin shift para inicializacion correcta del forecast.
- RidgeCV / LassoCV / ElasticNetCV con TimeSeriesSplit (auto alpha).
- Week-of-year ciclos anuales y features de momentum (log-retornos).
"""

import pandas as pd
import numpy as np
from typing import Optional, Dict, List, Union
from sklearn.linear_model import (
    LinearRegression, Ridge, Lasso, ElasticNet,
    RidgeCV, LassoCV, ElasticNetCV
)
from sklearn.model_selection import TimeSeriesSplit
from sklearn.preprocessing import StandardScaler
import logging
import pickle

from .base_model import (
    BaseModel, ModelConfig, ModelMetrics, PredictionResult, ModelType, ModelStatus
)

logger = logging.getLogger(__name__)


class MultipleRegressionConfig(ModelConfig):
    """Configuracion especifica para Regresion Multiple."""

    def __init__(
        self,
        target_column: str,
        date_column: str = 'fecha',
        regularization: str = "ridge",
        alpha: float = 1.0,
        l1_ratio: float = 0.5,
        use_compras: bool = True,
        use_ventas: bool = False,
        lag_periods: Optional[List[int]] = None,
        rolling_windows: Optional[List[int]] = None,
        include_calendar: bool = True,
        polynomial_degree: int = 1,
        log_transform: bool = False,
        auto_tune: bool = True,
        **kwargs
    ):
        super().__init__(
            model_type=ModelType.MULTIPLE_REGRESSION,
            target_column=target_column,
            date_column=date_column,
            **kwargs
        )
        self.hyperparameters = {
            "regularization": regularization,
            "alpha": alpha,
            "l1_ratio": l1_ratio,
            "use_compras": use_compras,
            "use_ventas": use_ventas,
            "lag_periods": lag_periods if lag_periods is not None else [1, 7, 14, 30],
            "rolling_windows": rolling_windows if rolling_windows is not None else [7, 14, 30],
            "include_calendar": include_calendar,
            "polynomial_degree": polynomial_degree,
            "log_transform": log_transform,
            "auto_tune": auto_tune,
        }


class MultipleRegressionModel(BaseModel):
    """
    Modelo de Regresion Multiple log-consistente para series de tiempo de ventas.

    Diseno clave (log_transform=True, el default):
    - Target: log1p(ventas)
    - Features de ventas: log1p(lags), log1p(rolling_mean), log1p(ema)
    - Features de momentum: log1p(lag1) - log1p(lag2) = log-retorno (acotado)
    - Calendar: sin/cos mensuales, semanales y anuales (no cambia)

    Resultado: modelo log-log lineal, donde coef(log_lag1) ≈ 1.
    Esto generaliza bien a periodos con distinto nivel de ventas (ej. Q4 vs Q1).

    Cuando log_transform=False: todas las features permanecen en escala raw
    (compatible con el comportamiento original).
    """

    def __init__(
        self,
        target_column: str,
        date_column: str = 'fecha',
        compras_data: Optional[pd.DataFrame] = None,
        ventas_data: Optional[pd.DataFrame] = None,
        **kwargs
    ):
        config = MultipleRegressionConfig(
            target_column=target_column,
            date_column=date_column,
            **kwargs
        )
        super().__init__(config)

        self.date_column = date_column
        self._compras_data = compras_data
        self._ventas_data = ventas_data

        self.scaler: Optional[StandardScaler] = None
        self.coefficients: Dict[str, float] = {}
        self.intercept: float = 0.0

        self.start_date = None
        self.last_date = None
        self.last_known_values: List[float] = []   # siempre raw
        self.last_known_compras: List[float] = []  # siempre raw
        self.last_known_ventas: List[float] = []   # siempre raw (para modelo de compras)
        self._base_feature_names: List[str] = []

        # EMA state para forecast — en el espacio apropiado (log o raw)
        self._last_ema7: float = 0.0
        self._last_ema14: float = 0.0

    @property
    def _log_transform(self) -> bool:
        return bool(self.config.hyperparameters.get("log_transform", True))

    @property
    def _auto_tune(self) -> bool:
        return bool(self.config.hyperparameters.get("auto_tune", True))

    # ──────────────────────────────────────────────────────────────
    # Sklearn model factory
    # ──────────────────────────────────────────────────────────────

    def _get_sklearn_model(self):
        """Crea el estimador sklearn segun configuracion."""
        reg_type = str(self.config.hyperparameters.get("regularization", "ridge")).lower()
        alpha = float(self.config.hyperparameters.get("alpha", 1.0))
        alphas_cv = np.logspace(-3, 3, 60)

        if self._auto_tune:
            if reg_type == "ridge":
                return RidgeCV(alphas=alphas_cv, cv=TimeSeriesSplit(n_splits=3))
            elif reg_type == "lasso":
                return LassoCV(alphas=alphas_cv, cv=TimeSeriesSplit(n_splits=3), max_iter=5000)
            elif reg_type == "elasticnet":
                l1_ratio = float(self.config.hyperparameters.get("l1_ratio", 0.5))
                return ElasticNetCV(
                    alphas=alphas_cv,
                    l1_ratio=[l1_ratio],
                    cv=TimeSeriesSplit(n_splits=3),
                    max_iter=5000
                )
            else:
                return LinearRegression()
        else:
            if reg_type == "ridge":
                return Ridge(alpha=alpha)
            elif reg_type == "lasso":
                return Lasso(alpha=alpha, max_iter=5000)
            elif reg_type == "elasticnet":
                l1_ratio = float(self.config.hyperparameters.get("l1_ratio", 0.5))
                return ElasticNet(alpha=alpha, l1_ratio=l1_ratio, max_iter=5000)
            else:
                return LinearRegression()

    # ──────────────────────────────────────────────────────────────
    # Helpers de transformacion
    # ──────────────────────────────────────────────────────────────

    def _log_series(self, s: pd.Series) -> pd.Series:
        """Aplica log1p a una Serie (clip a 0 para evitar log de negativos)."""
        return np.log1p(s.clip(lower=0))

    def _log_val(self, v: float) -> float:
        """Aplica log1p a un valor escalar."""
        return float(np.log1p(max(0.0, v)))

    # ──────────────────────────────────────────────────────────────
    # Feature engineering
    # ──────────────────────────────────────────────────────────────

    def _build_features(self, df: pd.DataFrame, fit: bool = False) -> pd.DataFrame:
        """
        Construye la matriz de features.

        Con log_transform=True (default):
          - Lags / rolling mean / EMA: en log-space  → log1p(ventas_{t-k})
          - Momentum: log-retornos  → log1p(lag1) - log1p(lag2)  (acotados)
          - Target: log1p(ventas_t)  [aplicado en _train, no aqui]

        Con log_transform=False:
          - Todas las features en escala raw (comportamiento v1).
        """
        hp = self.config.hyperparameters
        target = self.config.target_column
        lag_periods: List[int] = hp.get("lag_periods", [1, 7, 14, 30])
        rolling_windows: List[int] = hp.get("rolling_windows", [7, 14, 30])
        include_calendar: bool = hp.get("include_calendar", True)
        poly_degree: int = int(hp.get("polynomial_degree", 1))
        use_compras: bool = bool(hp.get("use_compras", True))
        use_ventas: bool = bool(hp.get("use_ventas", False))
        use_log: bool = self._log_transform

        df = df.copy()
        if not pd.api.types.is_datetime64_any_dtype(df[self.date_column]):
            df[self.date_column] = pd.to_datetime(df[self.date_column])
        df = df.sort_values(self.date_column).reset_index(drop=True)

        if fit:
            self.start_date = df[self.date_column].min()
            self.last_date = df[self.date_column].max()

        ref_date = self.start_date if (not fit and self.start_date is not None) \
            else df[self.date_column].min()

        # ── 1. Tendencia temporal ──────────────────────────────────
        df['time_index'] = (df[self.date_column] - ref_date).dt.days
        if poly_degree >= 2:
            df['time_index_sq'] = df['time_index'] ** 2

        # ── 2. Calendario ──────────────────────────────────────────
        if include_calendar:
            df['mes'] = df[self.date_column].dt.month
            df['dia_semana'] = df[self.date_column].dt.dayofweek
            df['dia_mes'] = df[self.date_column].dt.day
            df['trimestre'] = df[self.date_column].dt.quarter
            df['es_fin_semana'] = df['dia_semana'].isin([5, 6]).astype(int)
            df['month_sin'] = np.sin(2 * np.pi * df['mes'] / 12)
            df['month_cos'] = np.cos(2 * np.pi * df['mes'] / 12)
            df['dow_sin'] = np.sin(2 * np.pi * df['dia_semana'] / 7)
            df['dow_cos'] = np.cos(2 * np.pi * df['dia_semana'] / 7)
            week_of_year = df[self.date_column].dt.isocalendar().week.astype(int)
            df['week_sin'] = np.sin(2 * np.pi * week_of_year / 52)
            df['week_cos'] = np.cos(2 * np.pi * week_of_year / 52)

        # ── 3-6. Features basadas en target ───────────────────────
        if target in df.columns:
            # Serie base: log1p o raw segun configuracion
            base_series = self._log_series(df[target]) if use_log else df[target]

            # Lags configurados + lag2 y lag8 para momentum
            all_needed_lags = set(lag_periods) | {2, 8}
            for lag in sorted(all_needed_lags):
                df[f'ventas_lag{lag}'] = base_series.shift(lag)

            # Rolling mean sobre base_series
            for w in rolling_windows:
                df[f'rolling_mean_{w}d'] = base_series.rolling(w, min_periods=1).mean()

            # Rolling std sobre base_series
            for w in [7, 14]:
                df[f'rolling_std_{w}d'] = (
                    base_series.rolling(w, min_periods=2).std().fillna(0)
                )

            # EMA sin shift (guardamos el ultimo valor para forecast) y con shift para features
            ema7_full = base_series.ewm(span=7, adjust=False).mean()
            ema14_full = base_series.ewm(span=14, adjust=False).mean()
            df['ema_7d'] = ema7_full.shift(1)   # shifted → sin lookahead
            df['ema_14d'] = ema14_full.shift(1)

            # Momentum: log-retornos (acotados) o diferencias raw
            # lag1 y lag2/lag8 ya estan en base_series (log o raw)
            df['ventas_delta1'] = df['ventas_lag1'] - df['ventas_lag2']
            df['ventas_delta7'] = df['ventas_lag1'] - df['ventas_lag8']
            # ventas_pct_7d eliminado (era redundante con delta7 en log-space
            # y causaba outliers extremos en raw-space cuando lag8 ~ 0)

            if fit:
                max_lag = max(lag_periods) if lag_periods else 30
                # Buffer siempre en raw para display correcto en forecast
                self.last_known_values = df[target].iloc[-max_lag:].tolist()
                # EMA state en el espacio apropiado (log o raw) — SIN shift
                self._last_ema7 = float(ema7_full.iloc[-1])
                self._last_ema14 = float(ema14_full.iloc[-1])

            # Eliminar lags auxiliares que no estan en lag_periods
            for lag in {2, 8} - set(lag_periods):
                col = f'ventas_lag{lag}'
                if col in df.columns:
                    df.drop(columns=[col], inplace=True)

        # ── 7. Features de compras (variable exogena) ─────────────
        if use_compras and self._compras_data is not None and not self._compras_data.empty:
            c = self._compras_data[['fecha', 'total']].copy()
            c['fecha'] = pd.to_datetime(c['fecha'])
            c = c.rename(columns={'total': '_ct'})
            df = df.merge(c, on=self.date_column, how='left')
            df['_ct'] = df['_ct'].fillna(0)

            base_ct = self._log_series(df['_ct']) if use_log else df['_ct']
            df['compras_lag1'] = base_ct.shift(1).fillna(0)
            df['compras_lag7'] = base_ct.shift(7).fillna(0)
            df['compras_rolling7'] = base_ct.rolling(7, min_periods=1).mean().fillna(0)

            if fit:
                max_lag = max(lag_periods) if lag_periods else 30
                self.last_known_compras = df['_ct'].iloc[-max_lag:].tolist()

            df.drop(columns=['_ct'], inplace=True)

        # ── 8. Features de ventas (variable exogena para modelo de compras) ──
        if use_ventas and self._ventas_data is not None and not self._ventas_data.empty:
            v = self._ventas_data[['fecha', 'total']].copy()
            v['fecha'] = pd.to_datetime(v['fecha'])
            v = v.rename(columns={'total': '_vt'})
            df = df.merge(v, on=self.date_column, how='left')
            df['_vt'] = df['_vt'].fillna(0)

            base_vt = self._log_series(df['_vt']) if use_log else df['_vt']
            df['ventas_exog_lag1'] = base_vt.shift(1).fillna(0)
            df['ventas_exog_lag7'] = base_vt.shift(7).fillna(0)
            df['ventas_exog_rolling7'] = base_vt.rolling(7, min_periods=1).mean().fillna(0)

            if fit:
                max_lag = max(lag_periods) if lag_periods else 30
                self.last_known_ventas = df['_vt'].iloc[-max_lag:].tolist()

            df.drop(columns=['_vt'], inplace=True)

        return df

    def _get_feature_columns(self, df: pd.DataFrame) -> List[str]:
        """Devuelve columnas de features (excluye fecha y target)."""
        exclude = {self.date_column, self.config.target_column}
        return [c for c in df.columns if c not in exclude]

    # ──────────────────────────────────────────────────────────────
    # BaseModel overrides
    # ──────────────────────────────────────────────────────────────

    def _split_data(self, X, y):
        """Split temporal 70/30 respetando causalidad."""
        n = len(X)
        split_idx = int(n * (1 - self.config.test_size))
        X_train = X.iloc[:split_idx]
        X_test = X.iloc[split_idx:]
        y_train = y.iloc[:split_idx] if isinstance(y, pd.Series) else y[:split_idx]
        y_test = y.iloc[split_idx:] if isinstance(y, pd.Series) else y[split_idx:]
        return X_train, X_test, y_train, y_test

    def _preprocess(
        self,
        X: Union[pd.DataFrame, np.ndarray],
        fit: bool = False
    ) -> np.ndarray:
        """Normaliza con StandardScaler."""
        X_arr = np.array(X, dtype=float)
        if fit:
            self.scaler = StandardScaler()
            return self.scaler.fit_transform(X_arr)
        if self.scaler is not None:
            return self.scaler.transform(X_arr)
        return X_arr

    def _train(
        self,
        X_train: Union[pd.DataFrame, np.ndarray],
        y_train: Union[pd.Series, np.ndarray]
    ) -> None:
        """Entrena el estimador sklearn."""
        X_processed = self._preprocess(X_train, fit=True)
        y_arr = np.array(y_train, dtype=float)

        if self._log_transform:
            y_arr = np.log1p(np.maximum(y_arr, 0))

        self.model = self._get_sklearn_model()
        self.model.fit(X_processed, y_arr)

        self.intercept = float(self.model.intercept_)
        coef_names = self._base_feature_names or self.feature_names
        self.coefficients = {
            name: float(coef)
            for name, coef in zip(coef_names, self.model.coef_)
        }

        chosen_alpha = getattr(self.model, 'alpha_', None)
        logger.info(
            f"MultipleRegressionModel entrenado. "
            f"Features: {len(self.coefficients)}, "
            f"regularizacion: {self.config.hyperparameters.get('regularization')}, "
            f"log_transform: {self._log_transform}, auto_tune: {self._auto_tune}"
            + (f", alpha={chosen_alpha:.4f}" if chosen_alpha is not None else "")
        )

    def _predict(self, X: Union[pd.DataFrame, np.ndarray]) -> np.ndarray:
        """Predice, deshace log1p si aplica, y clampea a >= 0."""
        X_processed = self._preprocess(X, fit=False)
        raw = self.model.predict(X_processed)
        if self._log_transform:
            raw = np.expm1(raw)
        return np.maximum(raw, 0)

    def _get_feature_importance(self) -> Dict[str, float]:
        """Importancia de features como % del coeficiente absoluto."""
        if not self.coefficients:
            return {}
        total = sum(abs(v) for v in self.coefficients.values())
        if total == 0:
            return {k: 0.0 for k in self.coefficients}
        return {
            k: round(abs(v) / total * 100, 2)
            for k, v in sorted(
                self.coefficients.items(),
                key=lambda x: abs(x[1]),
                reverse=True
            )
        }

    def get_signed_coefficients(self) -> Dict[str, float]:
        """Coeficientes con signo ordenados por magnitud."""
        return dict(sorted(
            self.coefficients.items(),
            key=lambda x: abs(x[1]),
            reverse=True
        ))

    # ──────────────────────────────────────────────────────────────
    # Entrenamiento desde DataFrame
    # ──────────────────────────────────────────────────────────────

    def train_from_dataframe(
        self,
        df: pd.DataFrame,
        validation_split: bool = True
    ) -> ModelMetrics:
        """Entrena desde DataFrame con columnas 'fecha' y target."""
        df = df.sort_values(self.date_column).reset_index(drop=True)

        df_feat = self._build_features(df, fit=True)
        df_feat = df_feat.dropna().reset_index(drop=True)

        if df_feat.empty:
            raise ValueError(
                "No hay suficientes datos despues de aplicar lags. "
                "Minimo requerido: max(lag_periods) + 8 dias adicionales."
            )

        feature_cols = self._get_feature_columns(df_feat)
        self._base_feature_names = feature_cols
        self.feature_names = feature_cols

        X = df_feat[feature_cols]
        y = df_feat[self.config.target_column]

        return self.train(X, y, validation_split)

    # ──────────────────────────────────────────────────────────────
    # Forecast iterativo
    # ──────────────────────────────────────────────────────────────

    def forecast(
        self,
        periods: int,
        last_date=None,
        freq: str = 'D',
        future_compras_values: Optional[List[float]] = None,
        future_ventas_values: Optional[List[float]] = None,
    ) -> PredictionResult:
        """
        Genera predicciones futuras de forma iterativa.

        Buffer interno: siempre valores RAW (para que el usuario vea cifras reales).
        Features: si log_transform=True, se aplica log1p al acceder al buffer.
        EMA: mantenido en el espacio apropiado (log o raw) segun log_transform.

        Args:
            future_compras_values: Valores predichos de compras para los periodos futuros.
                Si se proveen, se usan en lugar del proxy por promedio histórico.
            future_ventas_values: Valores predichos de ventas (para modelo de compras).
                Si se proveen, se usan en lugar del proxy por promedio histórico.
        """
        if not self.is_fitted:
            raise ValueError("El modelo debe ser entrenado antes de generar predicciones")

        if periods > self.MAX_FORECAST_PERIODS:
            periods = self.MAX_FORECAST_PERIODS

        hp = self.config.hyperparameters
        lag_periods: List[int] = hp.get("lag_periods", [1, 7, 14, 30])
        rolling_windows: List[int] = hp.get("rolling_windows", [7, 14, 30])
        include_calendar: bool = hp.get("include_calendar", True)
        poly_degree: int = int(hp.get("polynomial_degree", 1))
        use_compras: bool = bool(hp.get("use_compras", True))
        use_ventas: bool = bool(hp.get("use_ventas", False))
        use_log: bool = self._log_transform

        start_date = last_date or self.last_date
        if start_date is None:
            raise ValueError("Se requiere last_date para generar forecast")

        future_dates = pd.date_range(
            start=pd.Timestamp(start_date) + pd.Timedelta(days=1),
            periods=periods,
            freq=freq
        )

        max_lag = max(lag_periods) if lag_periods else 30
        max_needed = max(max_lag, 8)

        # Buffer de valores RAW
        raw_buffer = list(self.last_known_values[-max_needed:])
        raw_compras = (
            list(self.last_known_compras[-max_needed:])
            if self.last_known_compras else [0.0] * max_needed
        )
        raw_ventas_exog = (
            list(self.last_known_ventas[-max_needed:])
            if self.last_known_ventas else [0.0] * max_needed
        )

        # Helpers para obtener feature value (log o raw) del buffer
        def _feat(buf: List[float], lag: int) -> float:
            val = float(buf[-lag]) if len(buf) >= lag else (float(np.mean(buf)) if buf else 0.0)
            return self._log_val(val) if use_log else val

        def _rolling_mean(buf: List[float], w: int) -> float:
            window = buf[-w:] if len(buf) >= w else buf
            vals = [self._log_val(v) for v in window] if use_log else window
            return float(np.mean(vals)) if vals else 0.0

        def _rolling_std(buf: List[float], w: int) -> float:
            window = buf[-w:] if len(buf) >= w else buf
            vals = [self._log_val(v) for v in window] if use_log else window
            return float(np.std(vals)) if len(vals) >= 2 else 0.0

        # EMA state (en el espacio apropiado)
        alpha7 = 2.0 / (7 + 1)
        alpha14 = 2.0 / (14 + 1)
        ema7 = self._last_ema7
        ema14 = self._last_ema14

        from scipy import stats
        z_score = stats.norm.ppf(0.975)

        predictions: List[float] = []
        lower_ci_list: List[float] = []
        upper_ci_list: List[float] = []

        ref_date = self.start_date

        for future_date in future_dates:
            row: Dict[str, float] = {}

            # ── Tendencia ──────────────────────────────────────────
            row['time_index'] = float((future_date - ref_date).days)
            if poly_degree >= 2:
                row['time_index_sq'] = row['time_index'] ** 2

            # ── Calendario ─────────────────────────────────────────
            if include_calendar:
                row['mes'] = float(future_date.month)
                row['dia_semana'] = float(future_date.dayofweek)
                row['dia_mes'] = float(future_date.day)
                row['trimestre'] = float(future_date.quarter)
                row['es_fin_semana'] = float(int(future_date.dayofweek in [5, 6]))
                row['month_sin'] = float(np.sin(2 * np.pi * future_date.month / 12))
                row['month_cos'] = float(np.cos(2 * np.pi * future_date.month / 12))
                row['dow_sin'] = float(np.sin(2 * np.pi * future_date.dayofweek / 7))
                row['dow_cos'] = float(np.cos(2 * np.pi * future_date.dayofweek / 7))
                woy = future_date.isocalendar()[1]
                row['week_sin'] = float(np.sin(2 * np.pi * woy / 52))
                row['week_cos'] = float(np.cos(2 * np.pi * woy / 52))

            # ── Lags (raw o log segun use_log) ─────────────────────
            for lag in lag_periods:
                row[f'ventas_lag{lag}'] = _feat(raw_buffer, lag)

            # ── Rolling ────────────────────────────────────────────
            for w in rolling_windows:
                row[f'rolling_mean_{w}d'] = _rolling_mean(raw_buffer, w)

            for w in [7, 14]:
                row[f'rolling_std_{w}d'] = _rolling_std(raw_buffer, w)

            # ── EMA (estado del paso anterior) ─────────────────────
            row['ema_7d'] = float(ema7)
            row['ema_14d'] = float(ema14)

            # ── Momentum / delta ───────────────────────────────────
            row['ventas_delta1'] = _feat(raw_buffer, 1) - _feat(raw_buffer, 2)
            row['ventas_delta7'] = _feat(raw_buffer, 1) - _feat(raw_buffer, 8)

            # ── Compras ────────────────────────────────────────────
            if use_compras and raw_compras:
                row['compras_lag1'] = self._log_val(raw_compras[-1]) if use_log else float(raw_compras[-1])
                ct7 = raw_compras[-7] if len(raw_compras) >= 7 else raw_compras[-1]
                row['compras_lag7'] = self._log_val(ct7) if use_log else float(ct7)
                window_c = raw_compras[-7:] if len(raw_compras) >= 7 else raw_compras
                vals_c = [self._log_val(v) for v in window_c] if use_log else window_c
                row['compras_rolling7'] = float(np.mean(vals_c))

            # ── Ventas exogenas (para modelo de compras) ───────────
            if use_ventas and raw_ventas_exog:
                row['ventas_exog_lag1'] = self._log_val(raw_ventas_exog[-1]) if use_log else float(raw_ventas_exog[-1])
                vt7 = raw_ventas_exog[-7] if len(raw_ventas_exog) >= 7 else raw_ventas_exog[-1]
                row['ventas_exog_lag7'] = self._log_val(vt7) if use_log else float(vt7)
                window_v = raw_ventas_exog[-7:] if len(raw_ventas_exog) >= 7 else raw_ventas_exog
                vals_v = [self._log_val(v) for v in window_v] if use_log else window_v
                row['ventas_exog_rolling7'] = float(np.mean(vals_v))

            # ── Prediccion ─────────────────────────────────────────
            feature_values = [row.get(f, 0.0) for f in self.feature_names]
            X_row = np.array([feature_values], dtype=float)
            X_proc = self._preprocess(X_row, fit=False)
            raw_pred_val = self.model.predict(X_proc)[0]
            if use_log:
                raw_pred_val = np.expm1(raw_pred_val)
            pred = float(max(0.0, raw_pred_val))

            rmse = (self.metrics.rmse if (self.metrics and self.metrics.rmse and self.metrics.rmse > 0)
                    else abs(pred) * 0.1 + 1)
            margin = z_score * rmse
            lower_ci_list.append(max(0.0, pred - margin))
            upper_ci_list.append(pred + margin)
            predictions.append(pred)

            # ── Actualizar buffers ─────────────────────────────────
            raw_buffer.append(pred)
            if len(raw_buffer) > max_needed:
                raw_buffer.pop(0)

            # Actualizar EMA en el espacio apropiado
            ema_input = self._log_val(pred) if use_log else pred
            ema7 = alpha7 * ema_input + (1.0 - alpha7) * ema7
            ema14 = alpha14 * ema_input + (1.0 - alpha14) * ema14

            if use_compras and raw_compras:
                # Usar valor provisto (pack forecast) o proxy por promedio histórico
                step_idx = len(predictions) - 1
                if future_compras_values and step_idx < len(future_compras_values):
                    next_compra = float(future_compras_values[step_idx])
                else:
                    next_compra = float(np.mean(raw_compras[-7:]) if len(raw_compras) >= 7 else np.mean(raw_compras))
                raw_compras.append(next_compra)
                if len(raw_compras) > max_needed:
                    raw_compras.pop(0)

            if use_ventas and raw_ventas_exog:
                # Usar valor provisto (pack forecast) o proxy por promedio histórico
                step_idx = len(predictions) - 1
                if future_ventas_values and step_idx < len(future_ventas_values):
                    next_venta = float(future_ventas_values[step_idx])
                else:
                    next_venta = float(np.mean(raw_ventas_exog[-7:]) if len(raw_ventas_exog) >= 7 else np.mean(raw_ventas_exog))
                raw_ventas_exog.append(next_venta)
                if len(raw_ventas_exog) > max_needed:
                    raw_ventas_exog.pop(0)

        return PredictionResult(
            predictions=predictions,
            dates=list(future_dates),
            confidence_lower=lower_ci_list,
            confidence_upper=upper_ci_list,
            model_type=self.model_type.value
        )

    # ──────────────────────────────────────────────────────────────
    # Persistencia
    # ──────────────────────────────────────────────────────────────

    def save(self, filepath: str) -> None:
        """Guarda el modelo con todo el estado necesario para forecast."""
        model_data = {
            "_model_type": self.model_type.value,
            "model": self.model,
            "config": self.config,
            "metrics": self.metrics,
            "feature_names": self.feature_names,
            "is_fitted": self.is_fitted,
            "status": self.status,
            "created_at": self.created_at,
            "trained_at": self.trained_at,
            "training_data_info": self.training_data_info,
            "date_column": self.date_column,
            "start_date": self.start_date,
            "last_date": self.last_date,
            "last_known_values": self.last_known_values,
            "last_known_compras": self.last_known_compras,
            "last_known_ventas": self.last_known_ventas,
            "_base_feature_names": self._base_feature_names,
            "scaler": self.scaler,
            "coefficients": self.coefficients,
            "intercept": self.intercept,
            "_last_ema7": self._last_ema7,
            "_last_ema14": self._last_ema14,
        }
        with open(filepath, 'wb') as f:
            pickle.dump(model_data, f)
        logger.info(f"MultipleRegressionModel guardado en {filepath}")

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

        self.date_column = model_data.get("date_column", "fecha")
        self.start_date = model_data.get("start_date")
        self.last_date = model_data.get("last_date")
        self.last_known_values = model_data.get("last_known_values", [])
        self.last_known_compras = model_data.get("last_known_compras", [])
        self.last_known_ventas = model_data.get("last_known_ventas", [])
        self._base_feature_names = model_data.get("_base_feature_names", self.feature_names)
        self.scaler = model_data.get("scaler")
        self.coefficients = model_data.get("coefficients", {})
        self.intercept = model_data.get("intercept", 0.0)
        self._last_ema7 = model_data.get("_last_ema7", 0.0)
        self._last_ema14 = model_data.get("_last_ema14", 0.0)
        self._compras_data = None
        self._ventas_data = None

        logger.info(f"MultipleRegressionModel cargado desde {filepath}")
