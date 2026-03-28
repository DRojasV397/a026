"""
Servicio de prediccion.
Orquesta el entrenamiento, evaluacion y uso de modelos predictivos.
"""

import json
import time
import pandas as pd
import numpy as np
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime, timedelta
from sqlalchemy.orm import Session
import logging
import os
import pickle

from sqlalchemy import desc
from app.models import Venta, Modelo, VersionModelo, Prediccion
from app.models.prediccion import ModeloPack
from app.repositories import VentaRepository, CompraRepository, ModeloRepository, VersionModeloRepository, PrediccionRepository
from app.analytics.models import (
    BaseModel, ModelConfig, ModelMetrics, PredictionResult, ModelType,
    LinearRegressionModel, ARIMAModel, SARIMAModel, RandomForestModel
)
from app.analytics.models.linear_regression import (
    LinearRegressionConfig, TimeSeriesLinearRegression
)
from app.analytics.models.arima_model import ARIMAConfig
from app.analytics.models.sarima_model import SARIMAConfig
from app.analytics.models.random_forest import RandomForestConfig, TimeSeriesRandomForest
from app.analytics.evaluation import ModelEvaluator, compare_models

logger = logging.getLogger(__name__)

# Almacenamiento global de modelos para persistir entre requests
# Esto permite que los modelos cargados esten disponibles para todas las instancias del servicio
_global_trained_models: Dict[str, BaseModel] = {}
_global_model_ids: Dict[str, Optional[int]] = {}
_global_model_last_access: Dict[str, float] = {}  # timestamp Unix del último acceso

# Modelos no usados por más de este tiempo son eliminados de RAM
_MODEL_TTL_SECONDS = 2 * 3600  # 2 horas


def _evict_expired_models() -> None:
    """Elimina de RAM los modelos que superaron el TTL de inactividad."""
    global _global_trained_models, _global_model_ids, _global_model_last_access
    now = time.time()
    expired = [
        k for k, t in list(_global_model_last_access.items())
        if now - t > _MODEL_TTL_SECONDS
    ]
    for k in expired:
        _global_trained_models.pop(k, None)
        _global_model_ids.pop(k, None)
        _global_model_last_access.pop(k, None)
    if expired:
        logger.info(f"TTL: {len(expired)} modelo(s) eliminado(s) de RAM por inactividad.")


class PredictionService:
    """
    Servicio principal de prediccion.

    Responsabilidades:
    - Entrenar modelos predictivos
    - Generar predicciones de ventas
    - Evaluar y comparar modelos
    - Seleccionar automaticamente el mejor modelo (RF-02.06)
    """

    # Directorio para guardar modelos
    MODELS_DIR = "trained_models"

    # Umbral minimo de R2 (RN-03.02)
    # R2 >= 0.7 es el estándar de calidad requerido para que un modelo sea usable.
    R2_THRESHOLD = 0.7

    # Minimo de datos historicos (RN-01.01: 6 meses)
    MIN_HISTORICAL_DAYS = 180

    def __init__(self, db: Session, auto_load: bool = False):
        global _global_trained_models, _global_model_ids, _global_model_last_access

        # Liberar modelos expirados antes de operar con el dict global
        _evict_expired_models()

        self.db = db
        self.venta_repo = VentaRepository(db)
        self.compra_repo = CompraRepository(db)
        self.modelo_repo = ModeloRepository(db)
        self.version_repo = VersionModeloRepository(db)
        self.prediccion_repo = PrediccionRepository(db)
        self.evaluator = ModelEvaluator()

        # Usar almacenamiento global para persistir modelos entre requests
        self._trained_models = _global_trained_models
        self._trained_model_ids = _global_model_ids

        # Crear directorio de modelos si no existe
        os.makedirs(self.MODELS_DIR, exist_ok=True)

        # Cargar modelos existentes si se solicita
        if auto_load:
            self.load_all_models()

    def _extract_model_type_from_key(self, model_key: str) -> str:
        """
        Extrae el tipo de modelo desde la clave del modelo.

        Los nombres tienen formato: {model_type}_{timestamp}
        Ej: linear_20260202, random_forest_20260202, arima_20260202

        Args:
            model_key: Clave del modelo (ej: random_forest_20260202174718)

        Returns:
            Tipo de modelo (ej: random_forest)
        """
        # Tipos de modelo conocidos (ordenados por longitud descendente para match correcto)
        known_types = ['multiple_regression', 'pack_ventas', 'pack_compras', 'random_forest',
                       'ensemble', 'xgboost', 'prophet', 'compras', 'linear', 'sarima', 'arima']

        for model_type in known_types:
            if model_key.startswith(model_type + '_'):
                return model_type

        # Fallback: usar el primer segmento antes de _
        return model_key.split('_')[0]

    def get_sales_data(
        self,
        fecha_inicio: Optional[datetime] = None,
        fecha_fin: Optional[datetime] = None,
        aggregation: str = 'D',  # D=diario, W=semanal, M=mensual
        user_id: Optional[int] = None
    ) -> pd.DataFrame:
        """
        Obtiene datos de ventas agregados.

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final
            aggregation: Nivel de agregacion

        Returns:
            DataFrame con fecha y total de ventas
        """
        # Si no hay fechas, usar ultimos 2 años
        if fecha_fin is None:
            fecha_fin = datetime.now().date()
        if fecha_inicio is None:
            fecha_inicio = fecha_fin - timedelta(days=730)

        ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin, user_id=user_id)

        if not ventas:
            return pd.DataFrame(columns=['fecha', 'total'])

        # Convertir a DataFrame
        data = [{'fecha': v.fecha, 'total': float(v.total or 0)} for v in ventas]
        df = pd.DataFrame(data)

        # Asegurar tipo datetime
        df['fecha'] = pd.to_datetime(df['fecha'])

        # Agregar por periodo
        if aggregation == 'D':
            df_agg = df.groupby('fecha')['total'].sum().reset_index()
            # Rellenar días sin ventas con 0 para serie temporal regular
            all_dates = pd.date_range(df_agg['fecha'].min(), df_agg['fecha'].max(), freq='D')
            df_agg = (
                df_agg.set_index('fecha')
                      .reindex(all_dates, fill_value=0)
                      .rename_axis('fecha')
                      .reset_index()
            )
        elif aggregation == 'W':
            df_agg = df.set_index('fecha').resample('W-SUN')['total'].sum().reset_index()
        elif aggregation == 'M':
            df_agg = df.set_index('fecha').resample('ME')['total'].sum().reset_index()
        else:
            df_agg = df.groupby('fecha')['total'].sum().reset_index()

        return df_agg.sort_values('fecha')

    def get_compras_data(
        self,
        fecha_inicio: Optional[datetime] = None,
        fecha_fin: Optional[datetime] = None,
        user_id: Optional[int] = None
    ) -> pd.DataFrame:
        """
        Obtiene datos de compras agregados por fecha.

        Utilizado como variable exogena en MultipleRegressionModel para
        capturar la correlacion entre compras (reposicion de inventario)
        y ventas futuras.

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final
            user_id: No se usa (compras son globales), reservado para consistencia

        Returns:
            DataFrame con columnas 'fecha' y 'total' (compras diarias)
        """
        if fecha_fin is None:
            fecha_fin = datetime.now().date()
        if fecha_inicio is None:
            fecha_inicio = fecha_fin - timedelta(days=730)

        compras = self.compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        if not compras:
            return pd.DataFrame(columns=['fecha', 'total'])

        data = [{'fecha': c.fecha, 'total': float(c.total or 0)} for c in compras]
        df = pd.DataFrame(data)
        df['fecha'] = pd.to_datetime(df['fecha'])

        # Agregar por dia y rellenar huecos con 0
        df_agg = df.groupby('fecha')['total'].sum().reset_index()
        all_dates = pd.date_range(df_agg['fecha'].min(), df_agg['fecha'].max(), freq='D')
        df_agg = (
            df_agg.set_index('fecha')
                  .reindex(all_dates, fill_value=0)
                  .rename_axis('fecha')
                  .reset_index()
        )
        return df_agg.sort_values('fecha')

    # Requisitos mínimos de días por tipo de modelo.
    # SARIMA y Prophet necesitan al menos una temporada completa (365 días) para
    # aprender patrones estacionales. Ensemble se guía por el modelo más exigente
    # que lo componga; por defecto se exige 365.
    _MODEL_MIN_DAYS: Dict[str, int] = {
        "linear":              180,
        "multiple_regression": 180,
        "arima":               180,
        "sarima":              365,
        "random_forest":       180,
        "ensemble":            365,
        "xgboost":             180,
        "prophet":             365,
    }

    def validate_data_requirements(
        self,
        df: pd.DataFrame,
        model_type: Optional[str] = None,
    ) -> Tuple[bool, List[str]]:
        """
        Valida que los datos cumplan los requisitos minimos de cantidad y calidad.

        Comprobaciones:
        - DataFrame no vacio
        - Rango de fechas suficiente (global: 180 días; por modelo si es mayor)
        - Sin valores nulos en la columna 'total'
        - Sin valores negativos
        - Advertencia si >80% de registros son cero (datos degenerados)

        Args:
            df: DataFrame con columnas 'fecha' y 'total'
            model_type: Tipo de modelo a entrenar. Si se provee, aplica el
                        mínimo específico del modelo (puede ser mayor que 180 días).

        Returns:
            Tuple[bool, List[str]]: (cumple_requisitos, lista_de_problemas)
        """
        issues = []

        if df.empty:
            issues.append("No hay datos disponibles")
            return False, issues

        # ── Rango de fechas ──────────────────────────────────────────────────
        date_span = (df['fecha'].max() - df['fecha'].min()).days

        # Mínimo global (6 meses) o mínimo específico del modelo si es mayor
        min_days = self.MIN_HISTORICAL_DAYS
        if model_type:
            model_min = self._MODEL_MIN_DAYS.get(model_type, self.MIN_HISTORICAL_DAYS)
            if model_min > min_days:
                min_days = model_min

        if date_span < min_days:
            model_label = f" para {model_type}" if model_type else ""
            issues.append(
                f"Rango de datos insuficiente{model_label}: {date_span} días. "
                f"Mínimo requerido: {min_days} días"
                + (" (se necesita al menos una temporada completa para detectar "
                   "estacionalidad)" if min_days >= 365 else " (6 meses)")
            )

        # ── Valores nulos ────────────────────────────────────────────────────
        null_count = int(df['total'].isna().sum())
        if null_count > 0:
            issues.append(f"Hay {null_count} valores nulos en la columna 'total'")

        # ── Valores negativos ────────────────────────────────────────────────
        negative_count = int((df['total'] < 0).sum())
        if negative_count > 0:
            issues.append(
                f"Hay {negative_count} valores negativos en 'total'. "
                "Los datos de ventas deben ser >= 0"
            )

        # ── Datos degenerados (>80% ceros) ───────────────────────────────────
        # No bloquea el entrenamiento pero se incluye como advertencia en issues
        # para que el router pueda incluirlo en la respuesta.
        total_rows = len(df)
        zero_count = int((df['total'] == 0).sum())
        zero_pct = zero_count / total_rows * 100 if total_rows > 0 else 0
        if zero_pct > 80:
            issues.append(
                f"Advertencia: el {zero_pct:.0f}% de los registros tienen valor 0. "
                "El modelo puede producir predicciones poco fiables con datos tan dispersos"
            )

        return len(issues) == 0, issues

    def train_model(
        self,
        model_type: str,
        fecha_inicio: Optional[datetime] = None,
        fecha_fin: Optional[datetime] = None,
        hyperparameters: Optional[Dict[str, Any]] = None,
        user_id: Optional[int] = None,
        nombre: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Entrena un modelo predictivo.

        Args:
            model_type: Tipo de modelo (linear, arima, sarima, random_forest)
            fecha_inicio: Fecha inicial de datos
            fecha_fin: Fecha final de datos
            hyperparameters: Hiperparametros del modelo
            user_id: ID del usuario (filtra datos propios + legacy)

        Returns:
            Dict con resultados del entrenamiento
        """
        # Todos los modelos usan datos diarios. SARIMA captura el patrón DOW con s=7
        # y variables exógenas DOW. ARIMA lo captura parcialmente con el componente AR.
        # La agregación semanal para ARIMA daba solo ~37 muestras: insuficiente.
        df = self.get_sales_data(fecha_inicio, fecha_fin, aggregation='D', user_id=user_id)

        # Validar requisitos con chequeos específicos del modelo
        valid, issues = self.validate_data_requirements(df, model_type=model_type)
        if not valid:
            return {
                "success": False,
                "error": "Datos no cumplen requisitos",
                "issues": issues
            }

        # Para regresion multiple y ensemble, obtener datos de compras
        compras_df = None
        hp = hyperparameters or {}
        if model_type == 'multiple_regression' and hp.get('use_compras', True):
            compras_df = self.get_compras_data(fecha_inicio, fecha_fin, user_id=user_id)
        elif model_type == 'ensemble':
            compras_df = self.get_compras_data(fecha_inicio, fecha_fin, user_id=user_id)

        # Crear modelo segun tipo
        model = self._create_model(model_type, hyperparameters, compras_data=compras_df)

        try:
            # Todos los modelos usan train_from_dataframe
            if model_type in ['arima', 'sarima', 'linear', 'random_forest',
                               'multiple_regression', 'ensemble', 'xgboost', 'prophet']:
                metrics = model.train_from_dataframe(df)
            else:
                raise ValueError(f"Tipo de modelo no soportado: {model_type}")

            # Validar umbral R2 (RN-03.02)
            meets_threshold = metrics.r2_score >= self.R2_THRESHOLD

            # Guardar modelo en memoria
            model_key = f"{model_type}_{datetime.now().strftime('%Y%m%d%H%M%S')}"
            self._trained_models[model_key] = model
            _global_model_last_access[model_key] = time.time()

            # Guardar en BD y registrar idVersion para asociar predicciones
            version_db = self._save_model_to_db(model, model_key, metrics, user_id=user_id, nombre=nombre)
            self._trained_model_ids[model_key] = version_db.idVersion if version_db else None

            # Guardar modelo en disco
            model_path = os.path.join(self.MODELS_DIR, f"{model_key}.pkl")
            model.save(model_path)

            logger.info(
                f"Modelo {model_type} entrenado. "
                f"R2: {metrics.r2_score:.4f}, RMSE: {metrics.rmse:.4f}"
            )

            metrics_dict = metrics.to_dict()

            # Incluir importancia de features para modelos que la soportan
            if hasattr(model, 'get_feature_importance'):
                fi = model.get_feature_importance()
                if fi:
                    metrics_dict['feature_importance'] = fi

            return {
                "success": True,
                "model_id": version_db.idVersion if version_db else None,
                "model_key": model_key,
                "model_type": model_type,
                "metrics": metrics_dict,
                "meets_r2_threshold": meets_threshold,
                "recommendation": (
                    "Modelo apto para predicciones" if meets_threshold
                    else f"Modelo no cumple umbral minimo R2={self.R2_THRESHOLD}"
                ),
                "training_samples": metrics.training_samples,
                "test_samples": metrics.test_samples
            }

        except Exception as e:
            logger.error(f"Error entrenando modelo: {str(e)}")
            return {
                "success": False,
                "error": str(e)
            }

    def _create_model(
        self,
        model_type: str,
        hyperparameters: Optional[Dict[str, Any]] = None,
        compras_data=None,
        ventas_data=None
    ) -> BaseModel:
        """Crea una instancia del modelo especificado."""
        params = hyperparameters or {}

        if model_type in ('linear', 'linear_regression'):
            return TimeSeriesLinearRegression(
                target_column='total',
                date_column='fecha',
                **params
            )
        elif model_type == 'arima':
            config = ARIMAConfig(
                target_column='total',
                date_column='fecha',
                **params
            )
            return ARIMAModel(config)
        elif model_type == 'sarima':
            config = SARIMAConfig(
                target_column='total',
                date_column='fecha',
                **params
            )
            return SARIMAModel(config)
        elif model_type == 'random_forest':
            return TimeSeriesRandomForest(
                target_column='total',
                date_column='fecha',
                **params
            )
        elif model_type == 'multiple_regression':
            from app.analytics.models.multiple_regression import MultipleRegressionModel
            # Filtrar solo parametros conocidos por MultipleRegressionConfig
            valid_keys = {
                'regularization', 'alpha', 'l1_ratio', 'use_compras',
                'lag_periods', 'rolling_windows', 'include_calendar', 'polynomial_degree',
                'log_transform', 'auto_tune',
                'test_size', 'random_state'
            }
            filtered_params = {k: v for k, v in params.items() if k in valid_keys}
            return MultipleRegressionModel(
                target_column='total',
                date_column='fecha',
                compras_data=compras_data,
                **filtered_params
            )
        elif model_type == 'ensemble':
            from app.analytics.models.ensemble_model import EnsembleModel, EnsembleConfig
            valid_keys = {'base_models', 'meta_learner', 'split_ratio'}
            filtered_params = {k: v for k, v in params.items() if k in valid_keys}
            config = EnsembleConfig(**filtered_params)
            return EnsembleModel(config, compras_data=compras_data)
        elif model_type == 'xgboost':
            from app.analytics.models.xgboost_model import TimeSeriesXGBoost
            valid_keys = {'n_estimators', 'max_depth', 'learning_rate', 'subsample',
                          'colsample_bytree', 'min_child_weight', 'reg_alpha', 'reg_lambda'}
            filtered_params = {k: v for k, v in params.items() if k in valid_keys}
            return TimeSeriesXGBoost(target_column='total', date_column='fecha', **filtered_params)
        elif model_type == 'prophet':
            from app.analytics.models.prophet_model import ProphetModel
            valid_keys = {'changepoint_prior_scale', 'yearly_seasonality', 'weekly_seasonality'}
            filtered_params = {k: v for k, v in params.items() if k in valid_keys}
            return ProphetModel(target_column='total', date_column='fecha', **filtered_params)
        elif model_type == 'compras':
            from app.analytics.models.multiple_regression import MultipleRegressionModel
            valid_keys = {
                'regularization', 'alpha', 'l1_ratio', 'use_ventas',
                'lag_periods', 'rolling_windows', 'include_calendar', 'polynomial_degree',
                'log_transform', 'auto_tune', 'test_size', 'random_state'
            }
            filtered_params = {k: v for k, v in params.items() if k in valid_keys}
            # Modelos de compras no usan compras propias como exógena
            filtered_params.setdefault('use_compras', False)
            return MultipleRegressionModel(
                target_column='total',
                date_column='fecha',
                ventas_data=ventas_data,
                **filtered_params
            )
        elif model_type == 'pack_ventas':
            # Modelo de ventas de un pack — MultipleRegression con compras como exógena
            from app.analytics.models.multiple_regression import MultipleRegressionModel
            valid_keys = {
                'regularization', 'alpha', 'l1_ratio', 'use_compras',
                'lag_periods', 'rolling_windows', 'include_calendar', 'polynomial_degree',
                'log_transform', 'auto_tune', 'test_size', 'random_state'
            }
            filtered_params = {k: v for k, v in params.items() if k in valid_keys}
            filtered_params.setdefault('use_compras', True)
            return MultipleRegressionModel(
                target_column='total',
                date_column='fecha',
                compras_data=compras_data,
                **filtered_params
            )
        elif model_type == 'pack_compras':
            # Modelo de compras de un pack — MultipleRegression con ventas como exógena
            from app.analytics.models.multiple_regression import MultipleRegressionModel
            valid_keys = {
                'regularization', 'alpha', 'l1_ratio', 'use_ventas',
                'lag_periods', 'rolling_windows', 'include_calendar', 'polynomial_degree',
                'log_transform', 'auto_tune', 'test_size', 'random_state'
            }
            filtered_params = {k: v for k, v in params.items() if k in valid_keys}
            filtered_params.setdefault('use_compras', False)
            return MultipleRegressionModel(
                target_column='total',
                date_column='fecha',
                ventas_data=ventas_data,
                **filtered_params
            )
        else:
            raise ValueError(f"Tipo de modelo no soportado: {model_type}")

    def _save_model_to_db(
        self,
        model: BaseModel,
        model_key: str,
        metrics: ModelMetrics,
        user_id: Optional[int] = None,
        nombre: Optional[str] = None
    ) -> Optional[VersionModelo]:
        """
        Guarda Modelo y VersionModelo en la BD.
        Retorna la VersionModelo creada (su idVersion es la FK que usa Prediccion).
        """
        try:
            modelo = Modelo(
                tipoModelo=model.model_type.value,
                objetivo=f"Prediccion de ventas - {model_key}",
                creadoEn=datetime.now(),
                creadoPor=user_id,
                modelKey=model_key,
                nombre=nombre
            )
            modelo = self.modelo_repo.create(modelo)

            # Clamp R² para evitar overflow en columna NUMERIC de SQL Server.
            # R² puede ser negativo (modelo peor que la media); se limita a [-9.9, 1.0].
            precision_safe = float(max(-9.9, min(1.0, metrics.r2_score)))
            version = VersionModelo(
                idModelo=modelo.idModelo,
                numeroVersion="1.0",
                algoritmo=model.model_type.value,
                parametros=None,
                metricas=json.dumps(metrics.to_dict()),
                precision=precision_safe,
                estado='Activo',
                fechaEntrenamiento=datetime.now()
            )
            return self.version_repo.create(version)
        except Exception as e:
            logger.error(f"Error guardando modelo en BD: {str(e)}")
            return None

    def forecast(
        self,
        model_key: Optional[str] = None,
        periods: int = 30,
        model_type: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Genera predicciones futuras.

        Args:
            model_key: Clave del modelo a usar
            periods: Numero de periodos a predecir (RN-03.03: max 6 meses)
            model_type: Tipo de modelo si no se especifica model_key

        Returns:
            Dict con predicciones
        """
        # Validar limite de periodos (RN-03.03: 6 meses max)
        max_periods = 180  # ~6 meses
        if periods > max_periods:
            logger.warning(f"Limitando prediccion a {max_periods} periodos")
            periods = max_periods

        # Obtener modelo
        model = self._get_model(model_key, model_type)
        if model is None:
            return {
                "success": False,
                "error": "Modelo no encontrado. Entrene un modelo primero."
            }

        # Advertencia de calidad (RN-03.02) — no bloquea, solo informa
        quality_ok = model.metrics.r2_score >= self.R2_THRESHOLD
        if not quality_ok:
            logger.warning(
                f"Modelo con R2={model.metrics.r2_score:.4f} por debajo del umbral "
                f"R2={self.R2_THRESHOLD}. Se generan predicciones con calidad reducida."
            )

        try:
            # Generar predicciones
            result = model.forecast(periods=periods)

            # Resolver el ID de BD del modelo para persistir predicciones correctamente
            resolved_key = model_key or next(
                (k for k, m in self._trained_models.items() if m is model), None
            )
            db_model_id = self._trained_model_ids.get(resolved_key) if resolved_key else None

            # Guardar predicciones en BD
            self._save_predictions_to_db(result, resolved_key or "", model_id=db_model_id)

            # Construir predictions en formato plano esperado por el frontend Java:
            # { "dates": [...], "values": [...], "lower_ci": [...], "upper_ci": [...] }
            raw = result.to_dict().get("predictions", [])
            flat_predictions = {
                "dates": [p["date"] for p in raw],
                "values": [p["value"] for p in raw],
                "lower_ci": [p.get("confidence_lower") for p in raw],
                "upper_ci": [p.get("confidence_upper") for p in raw],
            }

            response = {
                "success": True,
                "predictions": flat_predictions,
                "model_type": model.model_type.value,
                "model_metrics": model.metrics.to_dict(),
                "periods": periods,
                "quality_warning": not quality_ok,
            }
            if not quality_ok:
                response["quality_message"] = (
                    f"R² actual ({model.metrics.r2_score:.2f}) por debajo del umbral "
                    f"recomendado ({self.R2_THRESHOLD}). "
                    "Las predicciones son orientativas."
                )
            return response

        except Exception as e:
            logger.error(f"Error generando prediccion: {str(e)}")
            return {
                "success": False,
                "error": str(e)
            }

    def _get_model(
        self,
        model_key: Optional[str] = None,
        model_type: Optional[str] = None
    ) -> Optional[BaseModel]:
        """Obtiene un modelo entrenado. Auto-carga desde disco si no está en RAM."""
        if model_key and model_key in self._trained_models:
            _global_model_last_access[model_key] = time.time()
            return self._trained_models[model_key]

        # Auto-cargar desde disco si no está en memoria
        if model_key and model_key not in self._trained_models:
            model_path = os.path.join(self.MODELS_DIR, f"{model_key}.pkl")
            if os.path.exists(model_path):
                result = self.load_model(model_key)
                if result.get("success"):
                    return self._trained_models.get(model_key)

        # Buscar por tipo
        if model_type:
            for key, model in self._trained_models.items():
                if model_type in key:
                    return model

        # Retornar el ultimo modelo entrenado
        if self._trained_models:
            return list(self._trained_models.values())[-1]

        return None

    def _save_predictions_to_db(
        self,
        result: PredictionResult,
        model_key: str,
        model_id: Optional[int] = None
    ) -> None:
        """Guarda predicciones en la BD."""
        try:
            for date, pred in zip(result.dates, result.predictions):
                # periodo se almacena como string (campo VARCHAR(20) en BD)
                if hasattr(date, 'strftime'):
                    periodo_str = date.strftime('%Y-%m-%d')
                else:
                    periodo_str = str(date)

                prediccion = Prediccion(
                    idVersion=model_id,
                    entidad="General",
                    claveEntidad=0,
                    periodo=periodo_str,
                    valorPredicho=float(pred) if pred is not None else None,
                    nivelConfianza=float(result.confidence_level) if result.confidence_level is not None else None
                )
                self.prediccion_repo.create(prediccion)
        except Exception as e:
            logger.error(f"Error guardando predicciones: {str(e)}")

    def auto_select_model(
        self,
        fecha_inicio: Optional[datetime] = None,
        fecha_fin: Optional[datetime] = None,
        user_id: Optional[int] = None
    ) -> Dict[str, Any]:
        """
        Selecciona automaticamente el mejor modelo.
        RF-02.06: Seleccion automatica basada en metricas.

        Returns:
            Dict con el mejor modelo y comparacion
        """
        logger.info("Iniciando seleccion automatica de modelo...")

        # Obtener datos del usuario
        df = self.get_sales_data(fecha_inicio, fecha_fin, user_id=user_id)

        valid, issues = self.validate_data_requirements(df)
        if not valid:
            return {"success": False, "error": "Datos insuficientes", "issues": issues}

        # Entrenar todos los modelos candidatos.
        # Se excluye solo 'prophet' (especializado, mejor configurar manualmente).
        model_types = ['linear', 'arima', 'sarima', 'random_forest', 'multiple_regression', 'xgboost', 'ensemble']
        results = {}
        predictions = {}
        errors: Dict[str, str] = {}

        for model_type in model_types:
            try:
                logger.info(f"Entrenando modelo {model_type}...")
                result = self.train_model(model_type, fecha_inicio, fecha_fin, user_id=user_id)

                if result.get("success"):
                    results[model_type] = result
                    model = self._get_model(result.get("model_key"))
                    if model:
                        # Obtener predicciones para comparacion
                        y_pred = model.predict(df[['fecha', 'total']])
                        predictions[model_type] = y_pred
                else:
                    errors[model_type] = result.get("error") or "Entrenamiento fallido sin detalle"

            except Exception as e:
                logger.warning(f"Error con modelo {model_type}: {str(e)}")
                errors[model_type] = str(e)
                continue

        if not results:
            issues = [f"{t}: {msg}" for t, msg in errors.items()]
            return {
                "success": False,
                "error": "Ningún modelo pudo entrenarse con los datos disponibles.",
                "issues": issues
            }

        # Comparar modelos
        best_model = max(
            results.items(),
            key=lambda x: x[1].get("metrics", {}).get("r2_score", 0)
        )

        best_type, best_result = best_model
        meets_threshold = best_result.get("metrics", {}).get("r2_score", 0) >= self.R2_THRESHOLD

        all_models_summary = {
            k: {
                "metrics": v.get("metrics"),
                "meets_threshold": v.get("meets_r2_threshold")
            }
            for k, v in results.items()
        }
        # Incluir modelos que fallaron con su motivo
        for t, msg in errors.items():
            all_models_summary[t] = {"error": msg, "meets_threshold": False}

        return {
            "success": True,
            "best_model": {
                "type": best_type,
                "model_key": best_result.get("model_key"),
                "metrics": best_result.get("metrics")
            },
            "meets_r2_threshold": meets_threshold,
            "all_models": all_models_summary,
            "recommendation": (
                f"Usar modelo {best_type} con R2={best_result.get('metrics', {}).get('r2_score', 0):.4f}"
                if meets_threshold else
                f"Ningún modelo supera R2={self.R2_THRESHOLD}. Considerar más datos históricos."
            ),
            "issues": [f"{t}: {msg}" for t, msg in errors.items()] if errors else None
        }

    def get_trained_models(self) -> List[Dict[str, Any]]:
        """Lista todos los modelos entrenados."""
        models = []
        for key, model in self._trained_models.items():
            models.append({
                "model_key": key,
                "model_type": model.model_type.value,
                "is_fitted": model.is_fitted,
                "metrics": model.metrics.to_dict() if model.is_fitted else None,
                "trained_at": model.trained_at.isoformat() if model.trained_at else None
            })
        return models

    def get_prediction_history(
        self,
        limit: int = 100
    ) -> List[Dict[str, Any]]:
        """Obtiene historial de predicciones."""
        predicciones = self.prediccion_repo.get_all(limit=limit)
        return [
            {
                "id": p.idPred,
                "fecha": p.periodo if p.periodo else None,
                "valor_predicho": float(p.valorPredicho) if p.valorPredicho else None,
                "confianza": float(p.nivelConfianza) if p.nivelConfianza else None
            }
            for p in predicciones
        ]

    def load_model(self, model_key: str) -> Dict[str, Any]:
        """
        Carga un modelo previamente entrenado desde disco.

        Args:
            model_key: Clave del modelo a cargar

        Returns:
            Dict con informacion del modelo cargado
        """
        model_path = os.path.join(self.MODELS_DIR, f"{model_key}.pkl")

        if not os.path.exists(model_path):
            return {
                "success": False,
                "error": f"Modelo no encontrado: {model_key}",
                "path": model_path
            }

        try:
            # Leer el pkl para determinar el tipo real guardado
            with open(model_path, 'rb') as f:
                peek_data = pickle.load(f)

            # Prioridad 1: campo auto-descriptivo (añadido en versiones recientes)
            if "_model_type" in peek_data:
                actual_type = peek_data["_model_type"]
            # Prioridad 2: detectar EnsembleModel por claves únicas (pkls sin _model_type)
            elif "_meta_model" in peek_data or "_fitted_bases" in peek_data:
                actual_type = "ensemble"
                logger.info(f"Detectado EnsembleModel por claves para {model_key}")
            else:
                # Fallback: extraer desde el nombre de la clave
                actual_type = self._extract_model_type_from_key(model_key)

            # Crear instancia vacía del tipo correcto y cargar estado
            model = self._create_model(actual_type)
            model.load(model_path)

            # Guardar en memoria
            self._trained_models[model_key] = model
            _global_model_last_access[model_key] = time.time()

            logger.info(f"Modelo cargado desde disco: {model_key}")

            return {
                "success": True,
                "model_key": model_key,
                "model_type": model.model_type.value,
                "is_fitted": model.is_fitted,
                "metrics": model.metrics.to_dict() if model.is_fitted else None,
                "trained_at": model.trained_at.isoformat() if model.trained_at else None,
                "path": model_path
            }

        except Exception as e:
            logger.error(f"Error cargando modelo {model_key}: {str(e)}")
            return {
                "success": False,
                "error": str(e),
                "model_key": model_key
            }

    def load_all_models(self) -> Dict[str, Any]:
        """
        Carga todos los modelos disponibles en el directorio de modelos.

        Returns:
            Dict con resumen de modelos cargados
        """
        loaded = []
        failed = []

        if not os.path.exists(self.MODELS_DIR):
            return {
                "success": True,
                "loaded": [],
                "failed": [],
                "message": "Directorio de modelos vacio"
            }

        # Buscar archivos .pkl en el directorio
        for filename in os.listdir(self.MODELS_DIR):
            if filename.endswith('.pkl'):
                model_key = filename[:-4]  # Quitar extension .pkl

                result = self.load_model(model_key)

                if result.get("success"):
                    loaded.append({
                        "model_key": model_key,
                        "model_type": result.get("model_type"),
                        "metrics": result.get("metrics")
                    })
                else:
                    failed.append({
                        "model_key": model_key,
                        "error": result.get("error")
                    })

        logger.info(f"Modelos cargados: {len(loaded)}, fallidos: {len(failed)}")

        return {
            "success": True,
            "loaded": loaded,
            "failed": failed,
            "total_loaded": len(loaded),
            "total_failed": len(failed)
        }

    def get_saved_models(self) -> List[Dict[str, Any]]:
        """
        Lista todos los modelos guardados en disco.

        Returns:
            Lista de modelos disponibles en disco
        """
        models = []

        if not os.path.exists(self.MODELS_DIR):
            return models

        for filename in os.listdir(self.MODELS_DIR):
            if filename.endswith('.pkl'):
                model_key = filename[:-4]
                model_path = os.path.join(self.MODELS_DIR, filename)

                # Obtener informacion basica del archivo
                stat = os.stat(model_path)
                models.append({
                    "model_key": model_key,
                    "filename": filename,
                    "size_bytes": stat.st_size,
                    "created_at": datetime.fromtimestamp(stat.st_ctime).isoformat(),
                    "modified_at": datetime.fromtimestamp(stat.st_mtime).isoformat(),
                    "is_loaded": model_key in self._trained_models
                })

        return models

    def get_user_models(self, user_id: int) -> List[Dict[str, Any]]:
        """
        Obtiene los modelos entrenados por un usuario, con su última versión.
        Excluye los modelos que pertenecen a un pack activo (se acceden solo via el pack).

        Args:
            user_id: ID del usuario

        Returns:
            Lista de dicts con info del modelo y versión
        """
        try:
            # Recopilar los idVersion que ya forman parte de un pack activo
            pack_version_ids: set = set()
            packs = self.db.query(ModeloPack).filter(
                ModeloPack.creadoPor == user_id,
                ModeloPack.estado == 'Activo'
            ).all()
            for pack in packs:
                pack_version_ids.add(pack.idVersionVentas)
                pack_version_ids.add(pack.idVersionCompras)

            modelos = self.modelo_repo.get_by_usuario(user_id)
            result = []
            for modelo in modelos:
                version = self.version_repo.get_ultima_version(modelo.idModelo)
                if version is None:
                    continue
                # Omitir modelos que pertenecen a un pack activo
                if version.idVersion in pack_version_ids:
                    continue
                metricas = None
                if version.metricas:
                    try:
                        metricas = json.loads(version.metricas)
                    except Exception:
                        metricas = None
                result.append({
                    "model_id": modelo.idModelo,
                    "version_id": version.idVersion,
                    "model_key": modelo.modelKey,
                    "model_type": modelo.tipoModelo,
                    "nombre": modelo.nombre,
                    "precision": float(version.precision) if version.precision is not None else None,
                    "metricas": metricas,
                    "estado": version.estado,
                    "fecha_entrenamiento": version.fechaEntrenamiento.isoformat() if version.fechaEntrenamiento else None,
                    "is_loaded": modelo.modelKey in self._trained_models if modelo.modelKey else False
                })
            return result
        except Exception as e:
            logger.error(f"Error obteniendo modelos del usuario: {str(e)}")
            return []

    def delete_model(self, model_key: str) -> Dict[str, Any]:
        """
        Elimina un modelo de memoria y disco.

        Args:
            model_key: Clave del modelo a eliminar

        Returns:
            Dict con resultado de la operacion
        """
        model_path = os.path.join(self.MODELS_DIR, f"{model_key}.pkl")
        deleted_from_memory = False
        deleted_from_disk = False

        # Eliminar de memoria
        if model_key in self._trained_models:
            del self._trained_models[model_key]
            _global_model_last_access.pop(model_key, None)
            deleted_from_memory = True

        # Eliminar de disco
        if os.path.exists(model_path):
            os.remove(model_path)
            deleted_from_disk = True

        if not deleted_from_memory and not deleted_from_disk:
            return {
                "success": False,
                "error": f"Modelo no encontrado: {model_key}"
            }

        logger.info(f"Modelo eliminado: {model_key}")

        return {
            "success": True,
            "model_key": model_key,
            "deleted_from_memory": deleted_from_memory,
            "deleted_from_disk": deleted_from_disk
        }

    # ──────────────────────────────────────────────────────────────────────────
    # PACK DE MODELOS (Ventas + Compras)
    # ──────────────────────────────────────────────────────────────────────────

    def train_pack(
        self,
        nombre: Optional[str] = None,
        fecha_inicio: Optional[datetime] = None,
        fecha_fin: Optional[datetime] = None,
        hyperparameters: Optional[Dict[str, Any]] = None,
        user_id: Optional[int] = None,
        ventas_model_type: str = "multiple_regression"
    ) -> Dict[str, Any]:
        """
        Entrena un pack de modelos (ventas + compras) en un solo paso.

        - Modelo de ventas: multiple_regression con compras como exógena (use_compras=True)
        - Modelo de compras: multiple_regression con ventas como exógena (use_ventas=True)

        Returns:
            Dict con métricas de ambos modelos y el pack_key para forecast coordinado.
        """
        hp = hyperparameters or {}

        # ── Obtener datos ──────────────────────────────────────────────────
        ventas_df  = self.get_sales_data(fecha_inicio, fecha_fin, aggregation='D', user_id=user_id)
        compras_df = self.get_compras_data(fecha_inicio, fecha_fin, user_id=user_id)

        # Validar mínimo de días en ambas series
        valid_v, issues_v = self.validate_data_requirements(ventas_df)
        valid_c, issues_c = self.validate_data_requirements(compras_df)

        if not valid_v:
            return {"success": False, "error": "Datos de ventas insuficientes", "issues": issues_v}
        if not valid_c:
            return {"success": False, "error": "Datos de compras insuficientes", "issues": issues_c}

        timestamp = datetime.now().strftime('%Y%m%d%H%M%S')

        try:
            # ── 1. Modelo de VENTAS (con compras como exógena) ──────────────
            ventas_hp = {**hp.get('ventas', {}), 'use_compras': True}
            ventas_model = self._create_model(ventas_model_type, ventas_hp, compras_data=compras_df)
            ventas_metrics = ventas_model.train_from_dataframe(ventas_df)

            ventas_key = f"pack_ventas_{timestamp}"
            ventas_version = self._save_model_to_db(
                ventas_model, ventas_key, ventas_metrics,
                user_id=user_id,
                nombre=f"{nombre} — Ventas" if nombre else "Pack — Ventas"
            )
            self._trained_models[ventas_key] = ventas_model
            self._trained_model_ids[ventas_key] = ventas_version.idVersion if ventas_version else None
            _global_model_last_access[ventas_key] = time.time()
            ventas_model.save(os.path.join(self.MODELS_DIR, f"{ventas_key}.pkl"))

            # ── 2. Modelo de COMPRAS (con ventas como exógena) ─────────────
            compras_hp = {**hp.get('compras', {}), 'use_ventas': True}
            compras_model = self._create_model('compras', compras_hp, ventas_data=ventas_df)
            compras_metrics = compras_model.train_from_dataframe(compras_df)

            compras_key = f"pack_compras_{timestamp}"
            compras_version = self._save_model_to_db(
                compras_model, compras_key, compras_metrics,
                user_id=user_id,
                nombre=f"{nombre} — Compras" if nombre else "Pack — Compras"
            )
            self._trained_models[compras_key] = compras_model
            self._trained_model_ids[compras_key] = compras_version.idVersion if compras_version else None
            _global_model_last_access[compras_key] = time.time()
            compras_model.save(os.path.join(self.MODELS_DIR, f"{compras_key}.pkl"))

            # ── 3. Guardar ModeloPack en BD ────────────────────────────────
            pack_key = f"pack_{timestamp}"
            pack = ModeloPack(
                packKey=pack_key,
                nombre=nombre,
                idVersionVentas=ventas_version.idVersion,
                idVersionCompras=compras_version.idVersion,
                creadoPor=user_id
            )
            self.db.add(pack)
            self.db.commit()
            self.db.refresh(pack)

            logger.info(f"Pack entrenado: {pack_key}, ventas R2={ventas_metrics.r2_score:.4f}, compras R2={compras_metrics.r2_score:.4f}")

            return {
                "success": True,
                "pack_key": pack_key,
                "pack_id": pack.idPack,
                "ventas": {
                    "model_key": ventas_key,
                    "metrics": ventas_metrics.to_dict(),
                    "meets_r2_threshold": ventas_metrics.r2_score >= self.R2_THRESHOLD
                },
                "compras": {
                    "model_key": compras_key,
                    "metrics": compras_metrics.to_dict(),
                    "meets_r2_threshold": compras_metrics.r2_score >= self.R2_THRESHOLD
                }
            }

        except Exception as e:
            logger.error(f"Error entrenando pack: {str(e)}")
            return {"success": False, "error": str(e)}

    def forecast_pack(
        self,
        pack_key: str,
        periods: int = 30
    ) -> Dict[str, Any]:
        """
        Genera predicciones coordinadas para un pack (compras → ventas).

        Flujo:
        1. forecast compras independientemente
        2. forecast ventas usando compras predichas como future_compras_values
           (mayor precisión que usar proxy por promedio)
        """
        periods = min(periods, 180)

        pack = self.db.query(ModeloPack).filter(ModeloPack.packKey == pack_key).first()
        if not pack:
            return {"success": False, "error": f"Pack no encontrado: {pack_key}"}

        try:
            # Resolver model_keys desde VersionModelo → Modelo.modelKey
            version_ventas  = self.db.query(VersionModelo).filter(VersionModelo.idVersion == pack.idVersionVentas).first()
            version_compras = self.db.query(VersionModelo).filter(VersionModelo.idVersion == pack.idVersionCompras).first()

            if not version_ventas or not version_compras:
                return {"success": False, "error": "Versiones del pack no encontradas en BD"}

            ventas_modelo  = self.db.query(Modelo).filter(Modelo.idModelo == version_ventas.idModelo).first()
            compras_modelo = self.db.query(Modelo).filter(Modelo.idModelo == version_compras.idModelo).first()

            ventas_key  = ventas_modelo.modelKey  if ventas_modelo  else None
            compras_key = compras_modelo.modelKey if compras_modelo else None

            if not ventas_key or not compras_key:
                return {"success": False, "error": "No se encontraron model_keys del pack"}

            # Obtener/cargar modelos
            ventas_model  = self._get_model(ventas_key)
            compras_model = self._get_model(compras_key)

            if not ventas_model or not compras_model:
                return {"success": False, "error": "No se pudieron cargar los modelos del pack"}

            # 1. Forecast de compras (independiente)
            compras_result = compras_model.forecast(periods=periods)
            compras_raw = compras_result.to_dict().get("predictions", [])
            flat_compras = {
                "dates":    [p["date"]  for p in compras_raw],
                "values":   [p["value"] for p in compras_raw],
                "lower_ci": [p.get("confidence_lower") for p in compras_raw],
                "upper_ci": [p.get("confidence_upper") for p in compras_raw],
            }

            # 2. Forecast de ventas usando valores de compras predichos
            compras_values = [p["value"] for p in compras_raw]
            ventas_result = ventas_model.forecast(periods=periods, future_compras_values=compras_values)
            ventas_raw = ventas_result.to_dict().get("predictions", [])
            flat_ventas = {
                "dates":    [p["date"]  for p in ventas_raw],
                "values":   [p["value"] for p in ventas_raw],
                "lower_ci": [p.get("confidence_lower") for p in ventas_raw],
                "upper_ci": [p.get("confidence_upper") for p in ventas_raw],
            }

            return {
                "success": True,
                "pack_key": pack_key,
                "periods": periods,
                "ventas": {"predictions": flat_ventas, "model_key": ventas_key},
                "compras": {"predictions": flat_compras, "model_key": compras_key},
            }

        except Exception as e:
            logger.error(f"Error en forecast_pack {pack_key}: {str(e)}")
            return {"success": False, "error": str(e)}

    def get_user_packs(self, user_id: int) -> List[Dict[str, Any]]:
        """
        Lista los packs activos del usuario con métricas de ambos modelos.
        """
        try:
            packs = (
                self.db.query(ModeloPack)
                .filter(ModeloPack.creadoPor == user_id, ModeloPack.estado == 'Activo')
                .order_by(desc(ModeloPack.creadoEn))
                .all()
            )

            result = []
            for p in packs:
                def _metricas(version: VersionModelo) -> Optional[dict]:
                    if version and version.metricas:
                        try:
                            return json.loads(version.metricas)
                        except Exception:
                            pass
                    return None

                result.append({
                    "pack_id":    p.idPack,
                    "pack_key":   p.packKey,
                    "nombre":     p.nombre,
                    "creado_en":  p.creadoEn.isoformat() if p.creadoEn else None,
                    "ventas": {
                        "version_id": p.idVersionVentas,
                        "precision":  float(p.version_ventas.precision) if p.version_ventas and p.version_ventas.precision else None,
                        "metricas":   _metricas(p.version_ventas)
                    },
                    "compras": {
                        "version_id": p.idVersionCompras,
                        "precision":  float(p.version_compras.precision) if p.version_compras and p.version_compras.precision else None,
                        "metricas":   _metricas(p.version_compras)
                    },
                })
            return result

        except Exception as e:
            logger.error(f"Error obteniendo packs del usuario {user_id}: {str(e)}")
            return []
