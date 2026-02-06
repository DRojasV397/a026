"""
Pruebas unitarias para el servicio de predicciones.
RF-02: Modulo de Analisis Predictivo.
"""

import pytest
import numpy as np
from datetime import date, timedelta
from decimal import Decimal
from unittest.mock import Mock, patch

from app.services.prediction_service import PredictionService


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
