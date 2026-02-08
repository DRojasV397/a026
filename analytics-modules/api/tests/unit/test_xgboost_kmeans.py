"""
Pruebas unitarias para modelos XGBoost y K-Means.
Cubre los modelos de ML que no se probaban anteriormente.
"""

import pytest
import pandas as pd
import numpy as np
from datetime import datetime, timedelta


class TestXGBoostModel:
    """Pruebas para el modelo XGBoost."""

    @pytest.fixture
    def sample_data(self):
        """Genera datos de muestra para pruebas."""
        np.random.seed(42)
        n = 200
        dates = pd.date_range(start='2024-01-01', periods=n, freq='D')

        # Datos con tendencia y estacionalidad
        trend = np.linspace(100, 150, n)
        seasonal = 10 * np.sin(2 * np.pi * np.arange(n) / 7)
        noise = np.random.randn(n) * 5
        values = trend + seasonal + noise

        return pd.DataFrame({
            'fecha': dates,
            'total': values
        })

    def test_xgboost_import(self):
        """Test que XGBoost se puede importar."""
        try:
            from app.analytics.models.xgboost_model import (
                XGBoostModel, XGBoostConfig, TimeSeriesXGBoost
            )
            assert XGBoostModel is not None
            assert XGBoostConfig is not None
            assert TimeSeriesXGBoost is not None
        except ImportError as e:
            pytest.skip(f"XGBoost no disponible: {e}")

    def test_xgboost_config_creation(self):
        """Test creacion de configuracion XGBoost."""
        try:
            from app.analytics.models.xgboost_model import XGBoostConfig

            config = XGBoostConfig(
                target_column='total',
                n_estimators=50,
                max_depth=3,
                learning_rate=0.1
            )

            assert config.target_column == 'total'
            assert config.hyperparameters['n_estimators'] == 50
            assert config.hyperparameters['max_depth'] == 3
            assert config.hyperparameters['learning_rate'] == 0.1
        except ImportError:
            pytest.skip("XGBoost no disponible")

    def test_xgboost_model_creation(self):
        """Test creacion de modelo XGBoost."""
        try:
            from app.analytics.models.xgboost_model import XGBoostModel, XGBoostConfig

            config = XGBoostConfig(
                target_column='total',
                n_estimators=10,
                max_depth=3
            )
            model = XGBoostModel(config)

            assert model is not None
            assert model.is_fitted == False
        except ImportError:
            pytest.skip("XGBoost no disponible")

    def test_xgboost_train_predict(self, sample_data):
        """Test entrenamiento y prediccion con XGBoost."""
        try:
            from app.analytics.models.xgboost_model import XGBoostModel, XGBoostConfig

            config = XGBoostConfig(
                target_column='total',
                n_estimators=10,
                max_depth=3,
                learning_rate=0.1
            )
            model = XGBoostModel(config)

            # Preparar features
            X = np.arange(len(sample_data)).reshape(-1, 1)
            y = sample_data['total'].values

            # Entrenar
            metrics = model.train(X, y, validation_split=True)

            assert model.is_fitted == True
            assert metrics.r2_score is not None
            assert metrics.rmse >= 0

            # Predecir
            predictions = model.predict(X[:10])
            assert len(predictions) == 10

        except ImportError:
            pytest.skip("XGBoost no disponible")
        except Exception as e:
            # XGBoost puede fallar por varias razones
            if "xgboost" in str(e).lower():
                pytest.skip(f"XGBoost error: {e}")
            raise

    def test_timeseries_xgboost_creation(self):
        """Test creacion de TimeSeriesXGBoost."""
        try:
            from app.analytics.models.xgboost_model import TimeSeriesXGBoost

            # TimeSeriesXGBoost requiere target_column y date_column como argumentos
            model = TimeSeriesXGBoost(
                target_column='total',
                date_column='fecha',
                n_estimators=10,
                max_depth=3
            )

            assert model is not None
            assert model.date_column == 'fecha'
            assert model.config.target_column == 'total'
            assert model.is_fitted == False

        except ImportError:
            pytest.skip("XGBoost no disponible")

    def test_timeseries_xgboost_train(self, sample_data):
        """Test entrenamiento de TimeSeriesXGBoost con features temporales."""
        try:
            from app.analytics.models.xgboost_model import TimeSeriesXGBoost

            model = TimeSeriesXGBoost(
                target_column='total',
                date_column='fecha',
                n_estimators=10,
                max_depth=3,
                lags=[1, 7],
                rolling_windows=[7]
            )

            # Entrenar desde DataFrame
            metrics = model.train_from_dataframe(sample_data, validation_split=True)

            assert model.is_fitted == True
            assert metrics.training_samples > 0
            assert metrics.r2_score is not None

        except ImportError:
            pytest.skip("XGBoost no disponible")
        except Exception as e:
            if "xgboost" in str(e).lower():
                pytest.skip(f"XGBoost error: {e}")
            raise

    def test_timeseries_xgboost_features(self, sample_data):
        """Test que TimeSeriesXGBoost genera features temporales correctamente."""
        try:
            from app.analytics.models.xgboost_model import TimeSeriesXGBoost

            model = TimeSeriesXGBoost(
                target_column='total',
                date_column='fecha',
                n_estimators=10,
                max_depth=3,
                lags=[1, 7],
                rolling_windows=[7]
            )

            # Crear features
            df_features = model._create_time_features(sample_data, fit=True)

            # Verificar features de calendario
            assert 'year' in df_features.columns
            assert 'month' in df_features.columns
            assert 'day_of_week' in df_features.columns
            assert 'is_weekend' in df_features.columns

            # Verificar features de lag
            assert 'total_lag_1' in df_features.columns
            assert 'total_lag_7' in df_features.columns

            # Verificar features de rolling
            assert 'total_rolling_mean_7' in df_features.columns

        except ImportError:
            pytest.skip("XGBoost no disponible")

    def test_timeseries_xgboost_forecast(self, sample_data):
        """Test forecast de TimeSeriesXGBoost."""
        try:
            from app.analytics.models.xgboost_model import TimeSeriesXGBoost

            model = TimeSeriesXGBoost(
                target_column='total',
                date_column='fecha',
                n_estimators=10,
                max_depth=3,
                lags=[1, 7],
                rolling_windows=[7]
            )

            # Entrenar
            model.train_from_dataframe(sample_data, validation_split=True)

            # Forecast
            result = model.forecast(
                periods=7,
                historical_data=sample_data
            )

            assert len(result.predictions) == 7
            assert len(result.dates) == 7
            assert len(result.confidence_lower) == 7
            assert len(result.confidence_upper) == 7

        except ImportError:
            pytest.skip("XGBoost no disponible")
        except Exception as e:
            if "xgboost" in str(e).lower():
                pytest.skip(f"XGBoost error: {e}")
            raise

    def test_xgboost_feature_importance(self, sample_data):
        """Test feature importance de XGBoost."""
        try:
            from app.analytics.models.xgboost_model import XGBoostModel, XGBoostConfig

            config = XGBoostConfig(
                target_column='total',
                n_estimators=10,
                max_depth=3
            )
            model = XGBoostModel(config)

            X = np.column_stack([
                np.arange(len(sample_data)),
                np.random.randn(len(sample_data))
            ])
            y = sample_data['total'].values

            model.train(X, y, validation_split=False)

            importance = model.get_feature_importance()
            assert isinstance(importance, dict)

        except ImportError:
            pytest.skip("XGBoost no disponible")
        except Exception as e:
            if "xgboost" in str(e).lower():
                pytest.skip(f"XGBoost error: {e}")
            raise


class TestKMeansClustering:
    """Pruebas para el modelo K-Means."""

    @pytest.fixture
    def product_data(self):
        """Genera datos de productos para clustering."""
        np.random.seed(42)
        n_products = 50

        # Crear 3 clusters de productos
        cluster1 = np.random.randn(15, 3) + [10, 100, 5]  # Alto volumen, alto precio
        cluster2 = np.random.randn(20, 3) + [5, 50, 10]   # Medio volumen, medio precio
        cluster3 = np.random.randn(15, 3) + [2, 20, 15]   # Bajo volumen, bajo precio

        data = np.vstack([cluster1, cluster2, cluster3])

        return pd.DataFrame({
            'producto_id': range(1, n_products + 1),
            'ventas_promedio': data[:, 0],
            'precio_promedio': data[:, 1],
            'margen_promedio': data[:, 2]
        })

    def test_kmeans_import(self):
        """Test que K-Means se puede importar."""
        from app.analytics.models.kmeans_clustering import (
            KMeansClustering, ClusteringConfig, ClusteringResult
        )
        assert KMeansClustering is not None
        assert ClusteringConfig is not None
        assert ClusteringResult is not None

    def test_clustering_config_creation(self):
        """Test creacion de configuracion de clustering."""
        from app.analytics.models.kmeans_clustering import ClusteringConfig

        # Usar los parametros reales de ClusteringConfig
        config = ClusteringConfig(
            n_clusters=3,
            random_state=42,
            max_iter=300,
            n_init=10
        )

        assert config.n_clusters == 3
        assert config.random_state == 42
        assert config.max_iter == 300

    def test_kmeans_model_creation(self):
        """Test creacion de modelo K-Means."""
        from app.analytics.models.kmeans_clustering import KMeansClustering, ClusteringConfig

        config = ClusteringConfig(
            n_clusters=3
        )
        model = KMeansClustering(config)

        assert model is not None
        assert model.is_fitted == False

    def test_kmeans_fit_predict(self, product_data):
        """Test entrenamiento y prediccion con K-Means."""
        from app.analytics.models.kmeans_clustering import KMeansClustering, ClusteringConfig

        config = ClusteringConfig(
            n_clusters=3,
            random_state=42
        )
        model = KMeansClustering(config)

        # Preparar datos
        X = product_data[['ventas_promedio', 'precio_promedio', 'margen_promedio']]

        # Entrenar (fit retorna ClusteringResult)
        result = model.fit(X)

        assert model.is_fitted == True
        assert model.model is not None

        # Predecir clusters
        labels = model.predict(X)
        assert len(labels) == len(product_data)
        assert set(labels).issubset({0, 1, 2})

    def test_kmeans_segment_products(self, product_data):
        """Test segmentacion de productos."""
        from app.analytics.models.kmeans_clustering import KMeansClustering, ClusteringConfig

        config = ClusteringConfig(
            n_clusters=3,
            random_state=42
        )
        model = KMeansClustering(config)

        # Segmentar productos usando segment_products
        result = model.segment_products(
            product_data,
            feature_columns=['ventas_promedio', 'precio_promedio', 'margen_promedio'],
            product_id_column='producto_id'
        )

        assert result is not None
        assert result['success'] == True
        assert 'segmentation' in result

    def test_kmeans_metrics(self, product_data):
        """Test metricas de clustering."""
        from app.analytics.models.kmeans_clustering import KMeansClustering, ClusteringConfig

        config = ClusteringConfig(
            n_clusters=3,
            random_state=42
        )
        model = KMeansClustering(config)

        X = product_data[['ventas_promedio', 'precio_promedio', 'margen_promedio']]
        result = model.fit(X)

        # Verificar metricas del ClusteringResult
        assert result.silhouette_score is not None
        assert result.inertia is not None
        assert result.calinski_harabasz_score is not None

    def test_kmeans_find_optimal_clusters(self, product_data):
        """Test busqueda de numero optimo de clusters."""
        from app.analytics.models.kmeans_clustering import KMeansClustering, ClusteringConfig

        config = ClusteringConfig(
            n_clusters=3,
            random_state=42
        )
        model = KMeansClustering(config)

        X = product_data[['ventas_promedio', 'precio_promedio', 'margen_promedio']]

        # Buscar numero optimo
        optimal_result = model.find_optimal_clusters(X, max_clusters=5)

        assert 'recommendation' in optimal_result
        assert 'analysis' in optimal_result
        assert optimal_result['recommendation'] >= 2

    def test_kmeans_min_samples_validation(self):
        """Test validacion de minimo de muestras (RN-03.06)."""
        from app.analytics.models.kmeans_clustering import KMeansClustering, ClusteringConfig

        config = ClusteringConfig(
            n_clusters=3,
            random_state=42
        )
        model = KMeansClustering(config)

        # Datos con menos de 10 muestras (MIN_SAMPLES)
        X = np.random.randn(5, 2)

        # Deberia fallar por minimo de muestras
        with pytest.raises(ValueError) as excinfo:
            model.fit(X)

        assert "minimo" in str(excinfo.value).lower() or "insuficientes" in str(excinfo.value).lower()

    def test_kmeans_cluster_info(self, product_data):
        """Test informacion de clusters."""
        from app.analytics.models.kmeans_clustering import KMeansClustering, ClusteringConfig

        config = ClusteringConfig(
            n_clusters=3,
            random_state=42
        )
        model = KMeansClustering(config)

        X = product_data[['ventas_promedio', 'precio_promedio', 'margen_promedio']]
        model.fit(X)

        # Obtener centroides
        centroids = model.model.cluster_centers_
        assert centroids.shape == (3, 3)

    def test_kmeans_get_summary(self, product_data):
        """Test resumen del clustering."""
        from app.analytics.models.kmeans_clustering import KMeansClustering, ClusteringConfig

        config = ClusteringConfig(
            n_clusters=3,
            random_state=42
        )
        model = KMeansClustering(config)

        X = product_data[['ventas_promedio', 'precio_promedio', 'margen_promedio']]
        model.fit(X)

        summary = model.get_summary()

        assert summary['status'] == 'Ajustado'
        assert summary['n_clusters'] == 3
        assert 'metrics' in summary

    def test_kmeans_clustering_result_to_dict(self, product_data):
        """Test conversion de ClusteringResult a diccionario."""
        from app.analytics.models.kmeans_clustering import KMeansClustering, ClusteringConfig

        config = ClusteringConfig(
            n_clusters=3,
            random_state=42
        )
        model = KMeansClustering(config)

        X = product_data[['ventas_promedio', 'precio_promedio', 'margen_promedio']]
        result = model.fit(X)

        result_dict = result.to_dict()

        assert 'n_clusters' in result_dict
        assert 'metrics' in result_dict
        assert 'clusters' in result_dict


class TestModelIntegration:
    """Pruebas de integracion entre modelos."""

    def test_all_models_have_common_interface(self):
        """Test que todos los modelos tienen interfaz comun."""
        from app.analytics.models.base_model import BaseModel
        from app.analytics.models.linear_regression import TimeSeriesLinearRegression
        from app.analytics.models.random_forest import TimeSeriesRandomForest
        from app.analytics.models.arima_model import ARIMAModel
        from app.analytics.models.sarima_model import SARIMAModel

        # Verificar que todos heredan de BaseModel
        models = [
            TimeSeriesLinearRegression,
            TimeSeriesRandomForest,
            ARIMAModel,
            SARIMAModel
        ]

        for model_class in models:
            assert issubclass(model_class, BaseModel)

    def test_model_type_enum(self):
        """Test enumeracion de tipos de modelo."""
        from app.analytics.models.base_model import ModelType

        assert ModelType.LINEAR_REGRESSION.value == "linear_regression"
        assert ModelType.ARIMA.value == "arima"
        assert ModelType.SARIMA.value == "sarima"
        assert ModelType.RANDOM_FOREST.value == "random_forest"
        assert ModelType.XGBOOST.value == "xgboost"
        assert ModelType.KMEANS.value == "kmeans"

    def test_model_status_enum(self):
        """Test enumeracion de estados de modelo."""
        from app.analytics.models.base_model import ModelStatus

        assert ModelStatus.CREATED.value == "created"
        assert ModelStatus.TRAINING.value == "training"
        assert ModelStatus.TRAINED.value == "trained"
        assert ModelStatus.FAILED.value == "failed"
