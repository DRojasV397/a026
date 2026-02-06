"""
Pruebas unitarias para metricas de evaluacion de modelos.
Cubre evaluation/metrics.py y ModelEvaluator.
"""

import pytest
import pandas as pd
import numpy as np
from datetime import datetime


class TestRegressionMetrics:
    """Pruebas para metricas de regresion."""

    @pytest.fixture
    def sample_predictions(self):
        """Predicciones de muestra."""
        np.random.seed(42)
        n = 100

        y_true = np.random.uniform(1000, 10000, n)
        # Predicciones con algo de error
        y_pred = y_true + np.random.normal(0, 500, n)

        return y_true, y_pred

    @pytest.fixture
    def perfect_predictions(self):
        """Predicciones perfectas."""
        y_true = np.array([100, 200, 300, 400, 500])
        y_pred = np.array([100, 200, 300, 400, 500])
        return y_true, y_pred

    @pytest.fixture
    def bad_predictions(self):
        """Predicciones malas."""
        y_true = np.array([100, 200, 300, 400, 500])
        y_pred = np.array([500, 400, 300, 200, 100])  # Invertidas
        return y_true, y_pred

    def test_metrics_import(self):
        """Test importacion del modulo de metricas."""
        from app.analytics.evaluation.metrics import (
            calculate_regression_metrics, calculate_forecast_metrics,
            RegressionMetrics, ForecastMetrics, ModelEvaluator
        )
        assert calculate_regression_metrics is not None
        assert RegressionMetrics is not None
        assert ModelEvaluator is not None

    def test_regression_metrics_calculation(self, sample_predictions):
        """Test calculo de metricas de regresion."""
        from app.analytics.evaluation.metrics import calculate_regression_metrics

        y_true, y_pred = sample_predictions
        metrics = calculate_regression_metrics(y_true, y_pred)

        assert metrics.rmse >= 0
        assert metrics.mae >= 0
        assert metrics.r2_score is not None
        assert isinstance(metrics.rmse, (int, float))

    def test_regression_metrics_perfect_predictions(self, perfect_predictions):
        """Test metricas con predicciones perfectas."""
        from app.analytics.evaluation.metrics import calculate_regression_metrics

        y_true, y_pred = perfect_predictions
        metrics = calculate_regression_metrics(y_true, y_pred)

        assert metrics.rmse == 0 or metrics.rmse < 0.001
        assert metrics.mae == 0 or metrics.mae < 0.001
        assert metrics.r2_score == 1.0 or metrics.r2_score > 0.99

    def test_regression_metrics_bad_predictions(self, bad_predictions):
        """Test metricas con predicciones malas."""
        from app.analytics.evaluation.metrics import calculate_regression_metrics

        y_true, y_pred = bad_predictions
        metrics = calculate_regression_metrics(y_true, y_pred)

        # R2 deberia ser negativo o muy bajo
        assert metrics.r2_score < 0.5

    def test_regression_metrics_dataclass(self):
        """Test dataclass RegressionMetrics."""
        from app.analytics.evaluation.metrics import RegressionMetrics

        metrics = RegressionMetrics(
            r2_score=0.85,
            rmse=500.0,
            mae=400.0,
            mape=5.0
        )

        assert metrics.r2_score == 0.85
        assert metrics.rmse == 500.0

    def test_regression_metrics_to_dict(self):
        """Test conversion a diccionario."""
        from app.analytics.evaluation.metrics import RegressionMetrics

        metrics = RegressionMetrics(
            r2_score=0.85,
            rmse=500.0,
            mae=400.0
        )

        d = metrics.to_dict()

        assert isinstance(d, dict)
        assert 'r2_score' in d
        assert 'rmse' in d


class TestForecastMetrics:
    """Pruebas para metricas de pronostico."""

    @pytest.fixture
    def forecast_data(self):
        """Datos de pronostico."""
        np.random.seed(42)
        y_true = np.random.uniform(1000, 2000, 30)
        y_pred = y_true * np.random.uniform(0.9, 1.1, 30)
        return y_true, y_pred

    def test_forecast_metrics_import(self):
        """Test importacion de metricas de forecast."""
        from app.analytics.evaluation.metrics import ForecastMetrics
        assert ForecastMetrics is not None

    def test_forecast_metrics_calculation(self, forecast_data):
        """Test calculo de metricas de pronostico."""
        from app.analytics.evaluation.metrics import calculate_forecast_metrics

        y_true, y_pred = forecast_data
        metrics = calculate_forecast_metrics(y_true, y_pred)

        assert metrics.rmse >= 0
        assert metrics.mae >= 0
        assert metrics.mape >= 0
        assert 0 <= metrics.smape <= 200  # SMAPE entre 0 y 200%

    def test_forecast_metrics_to_dict(self):
        """Test conversion de ForecastMetrics a diccionario."""
        from app.analytics.evaluation.metrics import ForecastMetrics

        metrics = ForecastMetrics(
            rmse=100.0,
            mae=80.0,
            mape=5.0,
            smape=4.5
        )

        d = metrics.to_dict()
        assert 'rmse' in d
        assert 'smape' in d


class TestModelEvaluator:
    """Pruebas para el evaluador de modelos."""

    @pytest.fixture
    def sample_model_results(self):
        """Resultados de modelo de muestra."""
        np.random.seed(42)
        n = 100

        return {
            'y_true': np.random.uniform(1000, 10000, n),
            'y_pred': np.random.uniform(1000, 10000, n),
            'model_type': 'linear',
            'training_time': 1.5
        }

    def test_model_evaluator_import(self):
        """Test importacion del evaluador."""
        from app.analytics.evaluation.metrics import ModelEvaluator
        assert ModelEvaluator is not None

    def test_evaluator_creation(self):
        """Test creacion del evaluador."""
        from app.analytics.evaluation.metrics import ModelEvaluator

        evaluator = ModelEvaluator()
        assert evaluator is not None

    def test_evaluate_model(self, sample_model_results):
        """Test evaluacion de modelo."""
        from app.analytics.evaluation.metrics import ModelEvaluator

        evaluator = ModelEvaluator()

        result = evaluator.evaluate(
            sample_model_results['y_true'],
            sample_model_results['y_pred'],
            model_name='test_model'
        )

        assert 'regression_metrics' in result
        assert 'forecast_metrics' in result
        assert 'validation' in result

    def test_compare_models(self):
        """Test comparacion de modelos."""
        from app.analytics.evaluation.metrics import compare_models

        np.random.seed(42)
        y_true = np.random.uniform(1000, 10000, 100)

        predictions = {
            'model_a': y_true + np.random.normal(0, 100, 100),
            'model_b': y_true + np.random.normal(0, 500, 100),
            'model_c': y_true + np.random.normal(0, 1000, 100)
        }

        comparison = compare_models(y_true, predictions)

        # Verificar que retorna comparacion
        assert comparison is not None
        assert isinstance(comparison, list)
        assert len(comparison) == 3

        # El mejor modelo deberia ser model_a (menor error)
        assert comparison[0].model_name == 'model_a'
        assert comparison[0].is_best == True

    def test_validate_r2_threshold(self, sample_model_results):
        """Test validacion de umbral R2 (RN-03.02)."""
        from app.analytics.evaluation.metrics import ModelEvaluator

        evaluator = ModelEvaluator()

        # Evaluar
        result = evaluator.evaluate(
            sample_model_results['y_true'],
            sample_model_results['y_pred']
        )

        # Verificar umbral
        assert 'validation' in result
        assert 'meets_threshold' in result['validation']
        assert isinstance(result['validation']['meets_threshold'], bool)

    def test_get_evaluation_report(self, sample_model_results):
        """Test generacion de reporte de evaluacion."""
        from app.analytics.evaluation.metrics import ModelEvaluator

        evaluator = ModelEvaluator()

        # Evaluar varios modelos
        evaluator.evaluate(
            sample_model_results['y_true'],
            sample_model_results['y_pred'],
            model_name='model_1'
        )

        evaluator.evaluate(
            sample_model_results['y_true'],
            sample_model_results['y_true'] * 1.1,  # Otro modelo
            model_name='model_2'
        )

        report = evaluator.generate_report()

        assert 'total_evaluations' in report
        assert report['total_evaluations'] == 2

    def test_evaluate_time_series(self, sample_model_results):
        """Test evaluacion de series de tiempo."""
        from app.analytics.evaluation.metrics import ModelEvaluator

        evaluator = ModelEvaluator()

        result = evaluator.evaluate_time_series(
            sample_model_results['y_true'],
            sample_model_results['y_pred'],
            model_name='ts_model'
        )

        assert 'temporal_analysis' in result
        assert 'error_mean' in result['temporal_analysis']

    def test_cross_validate_model(self):
        """Test validacion cruzada."""
        from app.analytics.evaluation.metrics import cross_validate_model
        from sklearn.linear_model import LinearRegression

        np.random.seed(42)
        X = np.random.randn(100, 5)
        y = np.random.randn(100)

        model = LinearRegression()

        result = cross_validate_model(model, X, y, cv=5)

        assert 'cv_folds' in result
        assert 'mean_score' in result
        assert len(result['scores']) == 5


class TestMetricsEdgeCases:
    """Pruebas de casos borde para metricas."""

    def test_empty_arrays(self):
        """Test con arrays vacios."""
        from app.analytics.evaluation.metrics import calculate_regression_metrics

        y_true = np.array([])
        y_pred = np.array([])

        try:
            metrics = calculate_regression_metrics(y_true, y_pred)
            # Puede retornar NaN o fallar
        except (ValueError, ZeroDivisionError, IndexError):
            # Es aceptable que falle con arrays vacios
            pass

    def test_single_value(self):
        """Test con un solo valor."""
        from app.analytics.evaluation.metrics import calculate_regression_metrics

        y_true = np.array([100])
        y_pred = np.array([110])

        metrics = calculate_regression_metrics(y_true, y_pred)

        assert metrics.mae == 10
        assert metrics.rmse == 10

    def test_large_values(self):
        """Test con valores muy grandes."""
        from app.analytics.evaluation.metrics import calculate_regression_metrics

        y_true = np.array([1e10, 2e10, 3e10])
        y_pred = np.array([1.1e10, 1.9e10, 3.1e10])

        metrics = calculate_regression_metrics(y_true, y_pred)

        assert np.isfinite(metrics.rmse)
        assert np.isfinite(metrics.r2_score)

    def test_negative_values(self):
        """Test con valores negativos."""
        from app.analytics.evaluation.metrics import calculate_regression_metrics

        y_true = np.array([-100, -50, 0, 50, 100])
        y_pred = np.array([-90, -60, 10, 40, 110])

        metrics = calculate_regression_metrics(y_true, y_pred)

        assert metrics.rmse >= 0
        assert metrics.mae >= 0
        assert np.isfinite(metrics.r2_score)

    def test_identical_arrays(self):
        """Test con arrays identicos."""
        from app.analytics.evaluation.metrics import calculate_regression_metrics

        y = np.array([100, 200, 300, 400, 500])

        metrics = calculate_regression_metrics(y, y)

        assert metrics.rmse == 0 or metrics.rmse < 1e-10
        assert metrics.mae == 0 or metrics.mae < 1e-10
        assert metrics.r2_score == 1.0 or metrics.r2_score > 0.9999


class TestModelMetricsFromBaseModel:
    """Pruebas para ModelMetrics del base_model."""

    def test_model_metrics_import(self):
        """Test importacion de ModelMetrics."""
        from app.analytics.models.base_model import ModelMetrics
        assert ModelMetrics is not None

    def test_model_metrics_creation(self):
        """Test creacion de ModelMetrics."""
        from app.analytics.models.base_model import ModelMetrics

        metrics = ModelMetrics(
            r2_score=0.85,
            rmse=500.0,
            mae=400.0,
            mape=5.0,
            training_samples=100,
            test_samples=30
        )

        assert metrics.r2_score == 0.85
        assert metrics.rmse == 500.0
        assert metrics.mae == 400.0

    def test_model_metrics_to_dict(self):
        """Test conversion a diccionario."""
        from app.analytics.models.base_model import ModelMetrics

        metrics = ModelMetrics(
            r2_score=0.85,
            rmse=500.0,
            mae=400.0
        )

        d = metrics.to_dict()

        assert isinstance(d, dict)
        assert 'r2_score' in d
        assert 'rmse' in d


class TestModelComparisonResult:
    """Pruebas para ModelComparisonResult."""

    def test_comparison_result_creation(self):
        """Test creacion de resultado de comparacion."""
        from app.analytics.evaluation.metrics import ModelComparisonResult

        result = ModelComparisonResult(
            model_name='test_model',
            metrics={'r2_score': 0.85, 'rmse': 500},
            rank=1,
            is_best=True,
            meets_threshold=True
        )

        assert result.model_name == 'test_model'
        assert result.is_best == True

    def test_comparison_result_to_dict(self):
        """Test conversion a diccionario."""
        from app.analytics.evaluation.metrics import ModelComparisonResult

        result = ModelComparisonResult(
            model_name='test_model',
            metrics={'r2_score': 0.85},
            rank=1,
            is_best=True,
            meets_threshold=True
        )

        d = result.to_dict()

        assert 'model_name' in d
        assert 'metrics' in d
        assert 'is_best' in d
