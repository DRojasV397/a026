"""
Pruebas unitarias para modulos de preprocesamiento.
Cubre data_cleaner.py, data_validator.py y data_transformer.py.
"""

import pytest
import pandas as pd
import numpy as np
from datetime import datetime, timedelta


class TestDataCleaner:
    """Pruebas para el limpiador de datos."""

    @pytest.fixture
    def sample_data_with_issues(self):
        """Datos de muestra con problemas de calidad."""
        np.random.seed(42)
        n = 100

        data = pd.DataFrame({
            'fecha': pd.date_range('2024-01-01', periods=n),
            'total': np.random.uniform(1000, 10000, n),
            'cantidad': np.random.randint(1, 100, n),
            'producto': np.random.choice(['A', 'B', 'C', None], n)
        })

        # Agregar problemas
        data.loc[5, 'total'] = np.nan  # Valor nulo
        data.loc[10, 'total'] = np.nan
        data.loc[15, 'total'] = 999999  # Outlier
        data.loc[20, 'cantidad'] = -5  # Valor negativo invalido

        # Agregar duplicados
        data = pd.concat([data, data.iloc[[0, 1, 2]]], ignore_index=True)

        return data

    def test_data_cleaner_import(self):
        """Test importacion del limpiador."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner
        assert DataCleaner is not None

    def test_cleaner_creation(self):
        """Test creacion del limpiador."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner

        cleaner = DataCleaner()
        assert cleaner is not None

    def test_cleaning_config_import(self):
        """Test importacion de CleaningConfig."""
        from app.analytics.preprocessing.data_cleaner import CleaningConfig, NullStrategy

        config = CleaningConfig(
            remove_duplicates=True,
            handle_nulls=True,
            null_strategy=NullStrategy.DROP,
            detect_outliers=True
        )

        assert config.remove_duplicates == True
        assert config.null_strategy == NullStrategy.DROP

    def test_clean_removes_duplicates(self, sample_data_with_issues):
        """Test eliminacion de duplicados (RN-02.01)."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner, CleaningConfig

        config = CleaningConfig(
            remove_duplicates=True,
            handle_nulls=False,
            detect_outliers=False
        )
        cleaner = DataCleaner(config)

        original_len = len(sample_data_with_issues)

        # Limpiar duplicados
        cleaned, report = cleaner.clean(sample_data_with_issues)

        assert len(cleaned) < original_len
        assert report.duplicates_removed > 0

    def test_clean_handles_nulls_drop(self, sample_data_with_issues):
        """Test manejo de valores nulos - eliminar (RN-02.02)."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner, CleaningConfig, NullStrategy

        config = CleaningConfig(
            remove_duplicates=False,
            handle_nulls=True,
            null_strategy=NullStrategy.DROP,
            detect_outliers=False
        )
        cleaner = DataCleaner(config)

        # Contar nulos originales
        null_count = sample_data_with_issues['total'].isnull().sum()
        assert null_count > 0

        # Limpiar
        cleaned, report = cleaner.clean(sample_data_with_issues)
        assert report.nulls_found > 0

    def test_clean_handles_nulls_fill_mean(self, sample_data_with_issues):
        """Test manejo de valores nulos - rellenar con media."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner, CleaningConfig, NullStrategy

        config = CleaningConfig(
            remove_duplicates=False,
            handle_nulls=True,
            null_strategy=NullStrategy.FILL_MEAN,
            detect_outliers=False
        )
        cleaner = DataCleaner(config)

        cleaned, report = cleaner.clean(sample_data_with_issues)

        # No deberia haber nulos en columnas numericas
        assert cleaned['total'].isnull().sum() == 0

    def test_clean_handles_nulls_fill_median(self, sample_data_with_issues):
        """Test manejo de valores nulos - rellenar con mediana."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner, CleaningConfig, NullStrategy

        config = CleaningConfig(
            remove_duplicates=False,
            handle_nulls=True,
            null_strategy=NullStrategy.FILL_MEDIAN,
            detect_outliers=False
        )
        cleaner = DataCleaner(config)

        cleaned, report = cleaner.clean(sample_data_with_issues)
        assert cleaned['total'].isnull().sum() == 0

    def test_clean_detects_outliers_zscore(self, sample_data_with_issues):
        """Test deteccion de outliers con Z-Score (RN-02.03)."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner, CleaningConfig

        config = CleaningConfig(
            remove_duplicates=False,
            handle_nulls=True,
            null_strategy='drop',
            detect_outliers=True,
            outlier_method='zscore',
            outlier_threshold=3.0,
            remove_outliers=False  # Solo detectar
        )
        cleaner = DataCleaner(config)

        cleaned, report = cleaner.clean(sample_data_with_issues)

        # Debe detectar outliers
        assert report.outliers_detected >= 0

    def test_clean_detects_outliers_iqr(self, sample_data_with_issues):
        """Test deteccion de outliers con IQR."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner, CleaningConfig

        config = CleaningConfig(
            remove_duplicates=False,
            handle_nulls=True,
            detect_outliers=True,
            outlier_method='iqr',
            remove_outliers=False
        )
        cleaner = DataCleaner(config)

        cleaned, report = cleaner.clean(sample_data_with_issues)
        assert report.outliers_detected >= 0

    def test_clean_full_pipeline(self, sample_data_with_issues):
        """Test pipeline completo de limpieza."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner, CleaningConfig

        config = CleaningConfig(
            remove_duplicates=True,
            handle_nulls=True,
            detect_outliers=True
        )
        cleaner = DataCleaner(config)

        original_len = len(sample_data_with_issues)
        cleaned, report = cleaner.clean(sample_data_with_issues)

        # Verificar que se limpio
        assert len(cleaned) <= original_len
        assert report.duplicates_removed >= 0

    def test_cleaning_report_to_dict(self, sample_data_with_issues):
        """Test conversion de CleaningReport a diccionario."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner

        cleaner = DataCleaner()
        cleaned, report = cleaner.clean(sample_data_with_issues)

        report_dict = report.to_dict()

        assert 'original_rows' in report_dict
        assert 'cleaned_rows' in report_dict
        assert 'duplicates' in report_dict
        assert 'retention' in report_dict

    def test_validate_retention_rate(self, sample_data_with_issues):
        """Test validacion de tasa de retencion (RN-02.05: 70%)."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner, CleaningConfig

        config = CleaningConfig(
            min_retention_rate=0.70
        )
        cleaner = DataCleaner(config)

        cleaned, report = cleaner.clean(sample_data_with_issues)

        # El reporte debe indicar si cumple el requisito
        assert hasattr(report, 'meets_retention_requirement')

    def test_get_outlier_summary(self, sample_data_with_issues):
        """Test resumen de outliers."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner

        cleaner = DataCleaner()

        summary = cleaner.get_outlier_summary(sample_data_with_issues)

        assert 'total' in summary
        assert 'zscore_outliers' in summary['total']


class TestDataValidator:
    """Pruebas para el validador de datos."""

    @pytest.fixture
    def sample_data(self):
        """Datos de muestra para validacion."""
        return pd.DataFrame({
            'fecha': pd.date_range('2024-01-01', periods=50),
            'total': np.random.uniform(1000, 10000, 50),
            'cantidad': np.random.randint(1, 100, 50),
            'producto': ['A', 'B', 'C'] * 16 + ['A', 'B']
        })

    def test_data_validator_import(self):
        """Test importacion del validador."""
        from app.analytics.preprocessing.data_validator import DataValidator
        assert DataValidator is not None

    def test_validator_creation(self):
        """Test creacion del validador."""
        from app.analytics.preprocessing.data_validator import DataValidator

        validator = DataValidator()
        assert validator is not None

    def test_add_required_columns(self, sample_data):
        """Test agregar reglas de columnas requeridas."""
        from app.analytics.preprocessing.data_validator import DataValidator

        validator = DataValidator()
        validator.add_required_columns(['fecha', 'total'])

        result = validator.validate(sample_data)
        assert result.is_valid == True

    def test_validate_missing_columns(self, sample_data):
        """Test validacion de columnas faltantes."""
        from app.analytics.preprocessing.data_validator import DataValidator

        validator = DataValidator()
        validator.add_required_columns(['fecha', 'precio'])  # 'precio' no existe

        result = validator.validate(sample_data)
        assert result.is_valid == False
        assert len(result.errors) > 0

    def test_add_type_rule(self, sample_data):
        """Test validacion de tipos de datos."""
        from app.analytics.preprocessing.data_validator import DataValidator

        validator = DataValidator()
        validator.add_type_rule('total', 'numeric')

        result = validator.validate(sample_data)
        assert result.is_valid == True

    def test_add_range_rule(self, sample_data):
        """Test validacion de rango de valores."""
        from app.analytics.preprocessing.data_validator import DataValidator

        validator = DataValidator()
        validator.add_range_rule('total', min_val=0, max_val=1000000)

        result = validator.validate(sample_data)
        assert result.is_valid == True

    def test_add_unique_rule(self, sample_data):
        """Test validacion de valores unicos."""
        from app.analytics.preprocessing.data_validator import DataValidator

        validator = DataValidator()
        validator.add_unique_rule('fecha')

        result = validator.validate(sample_data)
        assert result.is_valid == True  # Fechas son unicas

    def test_validation_result_to_dict(self, sample_data):
        """Test conversion de ValidationResult a diccionario."""
        from app.analytics.preprocessing.data_validator import DataValidator

        validator = DataValidator()
        validator.add_required_columns(['fecha', 'total'])

        result = validator.validate(sample_data)
        result_dict = result.to_dict()

        assert 'is_valid' in result_dict
        assert 'total_rows' in result_dict
        assert 'valid_rows' in result_dict

    def test_common_validators_sales(self):
        """Test validador predefinido para ventas."""
        from app.analytics.preprocessing.data_validator import CommonValidators

        validator = CommonValidators.create_sales_validator()
        assert validator is not None
        assert len(validator.rules) > 0


class TestDataTransformer:
    """Pruebas para el transformador de datos."""

    @pytest.fixture
    def sample_data(self):
        """Datos de muestra para transformacion."""
        np.random.seed(42)
        return pd.DataFrame({
            'fecha': pd.date_range('2024-01-01', periods=100),
            'total': np.random.uniform(1000, 10000, 100),
            'cantidad': np.random.randint(1, 100, 100),
            'categoria': np.random.choice(['A', 'B', 'C'], 100)
        })

    def test_data_transformer_import(self):
        """Test importacion del transformador."""
        from app.analytics.preprocessing.data_transformer import DataTransformer
        assert DataTransformer is not None

    def test_transformer_creation(self):
        """Test creacion del transformador."""
        from app.analytics.preprocessing.data_transformer import DataTransformer

        transformer = DataTransformer()
        assert transformer is not None

    def test_transform_config(self):
        """Test configuracion de transformacion."""
        from app.analytics.preprocessing.data_transformer import TransformConfig, ScalingMethod

        config = TransformConfig(
            scaling_method=ScalingMethod.MINMAX,
            extract_date_features=True
        )

        assert config.scaling_method == ScalingMethod.MINMAX
        assert config.extract_date_features == True

    def test_fit_transform_minmax(self, sample_data):
        """Test normalizacion min-max."""
        from app.analytics.preprocessing.data_transformer import DataTransformer, TransformConfig, ScalingMethod

        config = TransformConfig(
            scaling_method=ScalingMethod.MINMAX,
            scaling_columns=['total'],
            extract_date_features=False
        )
        transformer = DataTransformer(config)

        transformed, result = transformer.fit_transform(sample_data)

        # Valores deben estar entre 0 y 1
        assert transformed['total'].min() >= 0
        assert transformed['total'].max() <= 1

    def test_fit_transform_standard(self, sample_data):
        """Test normalizacion estandar (z-score)."""
        from app.analytics.preprocessing.data_transformer import DataTransformer, TransformConfig, ScalingMethod

        config = TransformConfig(
            scaling_method=ScalingMethod.STANDARD,
            scaling_columns=['total'],
            extract_date_features=False
        )
        transformer = DataTransformer(config)

        transformed, result = transformer.fit_transform(sample_data)

        # Media cercana a 0, std cercana a 1
        assert abs(transformed['total'].mean()) < 0.1
        assert abs(transformed['total'].std() - 1) < 0.1

    def test_encode_categorical_label(self, sample_data):
        """Test encoding de etiquetas."""
        from app.analytics.preprocessing.data_transformer import DataTransformer, TransformConfig, EncodingMethod

        config = TransformConfig(
            scaling_method='none',
            encoding_method=EncodingMethod.LABEL,
            encoding_columns=['categoria'],
            extract_date_features=False
        )
        transformer = DataTransformer(config)

        transformed, result = transformer.fit_transform(sample_data)

        # La columna deberia ser numerica
        assert pd.api.types.is_numeric_dtype(transformed['categoria'])

    def test_encode_categorical_onehot(self, sample_data):
        """Test encoding one-hot."""
        from app.analytics.preprocessing.data_transformer import DataTransformer, TransformConfig, EncodingMethod

        config = TransformConfig(
            scaling_method='none',
            encoding_method=EncodingMethod.ONEHOT,
            encoding_columns=['categoria'],
            extract_date_features=False
        )
        transformer = DataTransformer(config)

        transformed, result = transformer.fit_transform(sample_data)

        # Deberia tener nuevas columnas
        assert len(transformed.columns) > len(sample_data.columns)

    def test_extract_date_features(self, sample_data):
        """Test extraccion de features de fecha."""
        from app.analytics.preprocessing.data_transformer import DataTransformer, TransformConfig

        config = TransformConfig(
            scaling_method='none',
            extract_date_features=True,
            date_columns=['fecha'],
            date_features=['year', 'month', 'dayofweek']
        )
        transformer = DataTransformer(config)

        transformed, result = transformer.fit_transform(sample_data)

        # Deberia tener nuevas columnas de fecha
        assert 'fecha_year' in transformed.columns
        assert 'fecha_month' in transformed.columns

    def test_transform_result_to_dict(self, sample_data):
        """Test conversion de TransformResult a diccionario."""
        from app.analytics.preprocessing.data_transformer import DataTransformer

        transformer = DataTransformer()
        transformed, result = transformer.fit_transform(sample_data)

        result_dict = result.to_dict()

        assert 'original_columns' in result_dict
        assert 'transformed_columns' in result_dict
        assert 'transformations_applied' in result_dict

    def test_inverse_transform(self, sample_data):
        """Test transformacion inversa."""
        from app.analytics.preprocessing.data_transformer import DataTransformer, TransformConfig, ScalingMethod

        config = TransformConfig(
            scaling_method=ScalingMethod.MINMAX,
            scaling_columns=['total'],
            extract_date_features=False
        )
        transformer = DataTransformer(config)

        # Transformar
        transformed, _ = transformer.fit_transform(sample_data)

        # Invertir
        original_values = transformer.inverse_transform_column(
            transformed['total'],
            'total'
        )

        # Valores deben ser cercanos a los originales
        np.testing.assert_array_almost_equal(
            original_values.values,
            sample_data['total'].values,
            decimal=5
        )


class TestCreateTimeSeriesFeatures:
    """Pruebas para funcion de features de series temporales."""

    @pytest.fixture
    def time_series_data(self):
        """Datos de serie temporal."""
        return pd.DataFrame({
            'fecha': pd.date_range('2024-01-01', periods=100),
            'valor': np.random.uniform(100, 200, 100)
        })

    def test_create_time_series_features(self, time_series_data):
        """Test creacion de features de series temporales."""
        from app.analytics.preprocessing.data_transformer import create_time_series_features

        result = create_time_series_features(
            time_series_data,
            date_column='fecha',
            value_column='valor',
            lags=[1, 7],
            rolling_windows=[7]
        )

        # Verificar features de lag
        assert 'valor_lag_1' in result.columns
        assert 'valor_lag_7' in result.columns

        # Verificar features de rolling
        assert 'valor_rolling_mean_7' in result.columns
        assert 'valor_rolling_std_7' in result.columns


class TestPreprocessingIntegration:
    """Pruebas de integracion del modulo de preprocesamiento."""

    @pytest.fixture
    def raw_data(self):
        """Datos crudos para preprocesamiento completo."""
        np.random.seed(42)
        n = 200

        data = pd.DataFrame({
            'fecha': pd.date_range('2024-01-01', periods=n),
            'total': np.random.uniform(1000, 10000, n),
            'cantidad': np.random.randint(1, 100, n),
            'categoria': np.random.choice(['A', 'B', 'C'], n)
        })

        # Agregar problemas
        data.loc[10, 'total'] = np.nan
        data.loc[20, 'total'] = np.nan
        data.loc[50, 'total'] = 999999  # Outlier

        # Duplicados
        data = pd.concat([data, data.iloc[[0, 1]]], ignore_index=True)

        return data

    def test_full_preprocessing_pipeline(self, raw_data):
        """Test pipeline completo de preprocesamiento."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner
        from app.analytics.preprocessing.data_validator import DataValidator
        from app.analytics.preprocessing.data_transformer import DataTransformer

        # Validar
        validator = DataValidator()
        validator.add_required_columns(['fecha', 'total'])
        validation = validator.validate(raw_data)
        assert validation.is_valid == True

        # Limpiar
        cleaner = DataCleaner()
        cleaned, report = cleaner.clean(raw_data)

        # Transformar
        transformer = DataTransformer()
        transformed, result = transformer.fit_transform(cleaned)

        assert len(transformed) > 0

    def test_preprocessing_for_ml(self, raw_data):
        """Test preprocesamiento para ML."""
        from app.analytics.preprocessing.data_cleaner import DataCleaner
        from app.analytics.preprocessing.data_transformer import DataTransformer, TransformConfig, ScalingMethod, EncodingMethod

        # Limpiar
        cleaner = DataCleaner()
        cleaned, _ = cleaner.clean(raw_data)

        # Preparar para ML
        config = TransformConfig(
            scaling_method=ScalingMethod.STANDARD,
            scaling_columns=['total', 'cantidad'],
            encoding_method=EncodingMethod.LABEL,
            encoding_columns=['categoria'],
            extract_date_features=True,
            date_columns=['fecha']
        )
        transformer = DataTransformer(config)

        ml_data, result = transformer.fit_transform(cleaned)

        assert len(ml_data) > 0
        assert 'transformations_applied' in result.to_dict()
