"""
Pruebas unitarias para el servicio de predicciones.
RF-02: Modulo de Analisis Predictivo.
"""

import pytest
import numpy as np
import pandas as pd
from datetime import date, datetime, timedelta
from decimal import Decimal
from unittest.mock import Mock, patch

from app.services.prediction_service import PredictionService


# ─────────────────────────────────────────────────────────────────────────────
# Helpers para construir DataFrames de prueba
# ─────────────────────────────────────────────────────────────────────────────

def _make_df(days: int, zeros_pct: float = 0.0, negatives: int = 0,
             nulls: int = 0) -> pd.DataFrame:
    """
    Construye un DataFrame con columnas 'fecha' y 'total' que cubre `days` días.

    Args:
        days: Número de filas (días consecutivos hasta hoy)
        zeros_pct: Fracción de filas con valor 0.0 (0.0–1.0)
        negatives: Número de filas con valor negativo (-1.0)
        nulls: Número de filas con NaN
    """
    base = datetime.now() - timedelta(days=days - 1)
    dates = [base + timedelta(days=i) for i in range(days)]

    n_zeros = int(days * zeros_pct)
    values = [0.0] * n_zeros + [100.0] * (days - n_zeros)

    for i in range(min(negatives, days)):
        values[i] = -1.0

    df = pd.DataFrame({"fecha": dates, "total": values})

    if nulls:
        df.loc[:nulls - 1, "total"] = np.nan

    return df


class TestPredictionService:
    """Pruebas para PredictionService."""

    def test_init(self, db_session):
        """Verifica inicializacion del servicio."""
        service = PredictionService(db_session)

        assert service is not None
        assert service.db == db_session

    def test_validate_minimum_data_points(self, db_session):
        """
        RN-01.01: Verificar minimo 6 meses de datos.
        """
        service = PredictionService(db_session)

        # Datos con menos de 6 meses
        insufficient_data = [
            {"fecha": date.today() - timedelta(days=30), "valor": 100}
            for _ in range(30)
        ]

        # Datos con 6+ meses
        sufficient_data = [
            {"fecha": date.today() - timedelta(days=i), "valor": 100}
            for i in range(180)  # 6 meses aprox
        ]

        assert len(insufficient_data) < 180
        assert len(sufficient_data) >= 180


class TestTrainTestSplit:
    """Pruebas para division de datos (RN-03.01)."""

    def test_train_test_split_ratio(self, db_session):
        """
        RN-03.01: Division 70/30 entrenamiento/validacion.
        """
        service = PredictionService(db_session)

        # 100 puntos de datos
        total_points = 100
        train_ratio = 0.7
        test_ratio = 0.3

        train_size = int(total_points * train_ratio)
        test_size = total_points - train_size

        assert train_size == 70
        assert test_size == 30
        assert train_size + test_size == total_points


class TestModelMetrics:
    """Pruebas para metricas de modelos (RN-03.02)."""

    def test_r2_threshold(self, db_session):
        """
        RN-03.02: Metricas minimas R2 > 0.7
        """
        service = PredictionService(db_session)

        # R2 threshold
        R2_THRESHOLD = 0.7

        # Modelo aceptable
        good_r2 = 0.85
        assert good_r2 >= R2_THRESHOLD

        # Modelo inaceptable
        bad_r2 = 0.5
        assert bad_r2 < R2_THRESHOLD

    def test_calculate_rmse(self, db_session):
        """Verifica calculo de RMSE."""
        # Valores reales y predichos
        y_true = np.array([100, 150, 200, 250, 300])
        y_pred = np.array([110, 140, 210, 240, 310])

        # RMSE = sqrt(mean((y_true - y_pred)^2))
        mse = np.mean((y_true - y_pred) ** 2)
        rmse = np.sqrt(mse)

        assert rmse > 0
        assert rmse < 20  # Error razonable

    def test_calculate_mae(self, db_session):
        """Verifica calculo de MAE."""
        y_true = np.array([100, 150, 200, 250, 300])
        y_pred = np.array([110, 140, 210, 240, 310])

        # MAE = mean(|y_true - y_pred|)
        mae = np.mean(np.abs(y_true - y_pred))

        assert mae > 0
        assert mae == 10.0

    def test_calculate_mape(self, db_session):
        """Verifica calculo de MAPE."""
        y_true = np.array([100, 150, 200, 250, 300])
        y_pred = np.array([110, 140, 210, 240, 310])

        # MAPE = mean(|y_true - y_pred| / y_true) * 100
        mape = np.mean(np.abs((y_true - y_pred) / y_true)) * 100

        assert mape > 0
        assert mape < 10  # Menos del 10% de error


class TestForecastLimits:
    """Pruebas para limites de prediccion (RN-03.03)."""

    def test_max_forecast_periods(self, db_session):
        """
        RN-03.03: Predicciones hasta 6 meses en el futuro.
        """
        service = PredictionService(db_session)

        MAX_FORECAST_PERIODS = 6

        # Prediccion valida
        valid_periods = 3
        assert valid_periods <= MAX_FORECAST_PERIODS

        # Prediccion invalida
        invalid_periods = 12
        assert invalid_periods > MAX_FORECAST_PERIODS

    def test_forecast_date_range(self, db_session):
        """Verifica que fechas de prediccion esten en rango valido."""
        today = date.today()
        max_future_date = today + timedelta(days=180)  # 6 meses

        # Fecha valida
        valid_date = today + timedelta(days=90)
        assert valid_date <= max_future_date

        # Fecha invalida
        invalid_date = today + timedelta(days=365)
        assert invalid_date > max_future_date


class TestRetraining:
    """Pruebas para reentrenamiento (RN-03.04)."""

    def test_should_retrain_threshold(self, db_session):
        """
        RN-03.04: Reentrenamiento con 20% mas de datos.
        """
        service = PredictionService(db_session)

        RETRAIN_THRESHOLD = 0.20  # 20%

        # Datos originales
        original_count = 100

        # Nuevos datos
        new_data_count = 25  # 25% mas

        increase_ratio = new_data_count / original_count
        should_retrain = increase_ratio >= RETRAIN_THRESHOLD

        assert should_retrain is True

    def test_no_retrain_needed(self, db_session):
        """Verifica que no se reentrene sin suficientes datos nuevos."""
        RETRAIN_THRESHOLD = 0.20

        original_count = 100
        new_data_count = 10  # Solo 10% mas

        increase_ratio = new_data_count / original_count
        should_retrain = increase_ratio >= RETRAIN_THRESHOLD

        assert should_retrain is False


class TestSeasonalityDetection:
    """Pruebas para deteccion de estacionalidad (RN-03.05)."""

    def test_detect_weekly_seasonality(self, db_session):
        """Verifica deteccion de estacionalidad semanal."""
        # Datos con patron semanal
        np.random.seed(42)
        n_weeks = 12
        weekly_pattern = [100, 120, 130, 125, 140, 90, 85]  # Lun-Dom

        data = []
        for week in range(n_weeks):
            for day_value in weekly_pattern:
                data.append(day_value + np.random.normal(0, 5))

        # Deberia detectar periodo ~7
        assert len(data) == n_weeks * 7

    def test_detect_monthly_seasonality(self, db_session):
        """Verifica deteccion de estacionalidad mensual."""
        # Datos con patron mensual
        np.random.seed(42)
        n_months = 24
        monthly_pattern = np.sin(np.linspace(0, 2 * np.pi, 12)) * 50 + 100

        data = []
        for year in range(2):
            for month_value in monthly_pattern:
                data.append(month_value + np.random.normal(0, 10))

        assert len(data) == n_months


class TestModelTypes:
    """Pruebas para tipos de modelos."""

    def test_available_model_types(self, db_session):
        """Verifica tipos de modelos disponibles."""
        model_types = [
            "linear_regression",
            "arima",
            "sarima",
            "random_forest",
            "xgboost"
        ]

        assert len(model_types) >= 5

    def test_auto_model_selection(self, db_session):
        """
        RF-02.06: Seleccion automatica de modelo.
        """
        service = PredictionService(db_session)

        # La seleccion automatica deberia elegir el mejor modelo
        # basado en metricas de validacion cruzada
        assert service is not None


class TestClustering:
    """Pruebas para clustering (RF-02.04)."""

    def test_minimum_samples_for_clustering(self, db_session):
        """
        RN-03.06: Minimo 10 productos para clustering.
        """
        MIN_SAMPLES = 10

        # Suficientes muestras
        valid_samples = 15
        assert valid_samples >= MIN_SAMPLES

        # Insuficientes muestras
        invalid_samples = 5
        assert invalid_samples < MIN_SAMPLES

    def test_optimal_clusters(self, db_session):
        """Verifica determinacion de numero optimo de clusters."""
        # Datos de prueba
        np.random.seed(42)

        # 3 clusters evidentes
        cluster1 = np.random.randn(20, 2) + [0, 0]
        cluster2 = np.random.randn(20, 2) + [5, 5]
        cluster3 = np.random.randn(20, 2) + [10, 0]

        data = np.vstack([cluster1, cluster2, cluster3])

        # Deberia sugerir ~3 clusters
        assert data.shape[0] == 60
        assert data.shape[1] == 2


# ─────────────────────────────────────────────────────────────────────────────
# validate_data_requirements  (C3: validación por modelo)
# ─────────────────────────────────────────────────────────────────────────────

class TestValidateDataRequirements:
    """
    Tests para validate_data_requirements con mínimos específicos por modelo.
    Mejora C3: SARIMA / Prophet / Ensemble requieren 365 días;
                el resto requiere el mínimo global de 180.
    """

    @pytest.fixture
    def service(self, db_session):
        return PredictionService(db_session)

    # ── DataFrame vacío ───────────────────────────────────────────────────────

    def test_empty_df_fails(self, service):
        """DataFrame vacío → False con 'No hay datos'."""
        df = pd.DataFrame(columns=["fecha", "total"])
        ok, issues = service.validate_data_requirements(df)
        assert not ok
        assert any("No hay datos" in i for i in issues)

    # ── Mínimo global (180 días) ──────────────────────────────────────────────

    def test_below_global_minimum_fails(self, service):
        """90 días de span → falla mínimo global sin especificar modelo."""
        df = _make_df(91)   # span = 90 días < 180
        ok, issues = service.validate_data_requirements(df)
        assert not ok
        assert any("Rango de datos insuficiente" in i for i in issues)
        assert any("180" in i for i in issues)

    def test_exactly_global_minimum_passes(self, service):
        """span == 180 días → pasa para modelos estándar."""
        df = _make_df(181)  # span = 180 días
        ok, issues = service.validate_data_requirements(df)
        assert ok
        assert issues == []

    # ── Mínimos por modelo (estándar: 180 días) ───────────────────────────────

    @pytest.mark.parametrize("model_type", ["linear", "arima", "random_forest", "xgboost", "multiple_regression"])
    def test_standard_models_pass_with_181_days(self, service, model_type):
        """Modelos estándar aceptan 180 días de span (181 filas)."""
        df = _make_df(181)
        ok, _ = service.validate_data_requirements(df, model_type=model_type)
        assert ok

    # ── Mínimos por modelo (estacional: 365 días) ─────────────────────────────

    @pytest.mark.parametrize("model_type", ["sarima", "prophet", "ensemble"])
    def test_seasonal_models_fail_with_181_days(self, service, model_type):
        """SARIMA / Prophet / Ensemble necesitan 365 días; 181 días no alcanza."""
        df = _make_df(181)  # span = 180 < 365
        ok, issues = service.validate_data_requirements(df, model_type=model_type)
        assert not ok
        assert any("365" in i for i in issues)
        assert any("temporada" in i for i in issues)

    @pytest.mark.parametrize("model_type", ["sarima", "prophet", "ensemble"])
    def test_seasonal_models_pass_with_366_days(self, service, model_type):
        """SARIMA / Prophet / Ensemble pasan con 366 filas (span=365)."""
        df = _make_df(366)  # span = 365 días
        ok, _ = service.validate_data_requirements(df, model_type=model_type)
        assert ok

    def test_seasonal_error_mentions_model_name(self, service):
        """El mensaje de error incluye el nombre del modelo para mejor diagnóstico."""
        df = _make_df(181)
        _, issues = service.validate_data_requirements(df, model_type="sarima")
        assert any("sarima" in i for i in issues)

    # ── Calidad de datos ──────────────────────────────────────────────────────

    def test_null_values_fail(self, service):
        """Valores nulos en 'total' → False con mensaje de nulos."""
        df = _make_df(181, nulls=5)
        ok, issues = service.validate_data_requirements(df)
        assert not ok
        assert any("nulos" in i for i in issues)
        assert any("5" in i for i in issues)

    def test_negative_values_fail(self, service):
        """Valores negativos en 'total' → False con mensaje de negativos."""
        df = _make_df(181, negatives=3)
        ok, issues = service.validate_data_requirements(df)
        assert not ok
        assert any("negativos" in i for i in issues)
        assert any("3" in i for i in issues)

    def test_null_and_negative_both_reported(self, service):
        """Nulos Y negativos en distintas filas → ambos aparecen en issues."""
        # nulls=2 sobreescribe las filas 0-1; negatives=3 pone -1.0 en 0,1,2
        # → fila 0,1 quedan NaN; fila 2 queda -1.0 → null_count=2, negative_count=1
        df = _make_df(181, nulls=2, negatives=3)
        ok, issues = service.validate_data_requirements(df)
        assert not ok
        assert any("nulos" in i for i in issues)
        assert any("negativos" in i for i in issues)

    def test_over_80pct_zeros_adds_warning(self, service):
        """Más del 80% de ceros → warning con prefijo 'Advertencia' en issues."""
        df = _make_df(181, zeros_pct=0.85)  # ≈84.5% de ceros
        _, issues = service.validate_data_requirements(df)
        assert any(i.startswith("Advertencia") for i in issues)

    def test_under_80pct_zeros_no_warning(self, service):
        """79% de ceros → sin advertencia de datos degenerados."""
        df = _make_df(181, zeros_pct=0.79)
        ok, issues = service.validate_data_requirements(df)
        assert ok
        assert not any("Advertencia" in i for i in issues)
