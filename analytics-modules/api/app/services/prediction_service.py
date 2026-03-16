"""
Servicio de prediccion.
Orquesta el entrenamiento, evaluacion y uso de modelos predictivos.
"""

import json
import pandas as pd
import numpy as np
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime, timedelta
from sqlalchemy.orm import Session
import logging
import os
import pickle

from app.models import Venta, Modelo, VersionModelo, Prediccion
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
    # 0.5 es un umbral realista para datos diarios de retail con 1 año de historia.
    # R2 > 0.7 es excelente; 0.5-0.7 es aceptable; < 0.5 es un aviso de calidad baja.
    R2_THRESHOLD = 0.5

    # Minimo de datos historicos (RN-01.01: 6 meses)
    MIN_HISTORICAL_DAYS = 180

    def __init__(self, db: Session, auto_load: bool = False):
        global _global_trained_models, _global_model_ids

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
        known_types = ['multiple_regression', 'random_forest', 'ensemble', 'xgboost', 'prophet', 'linear', 'sarima', 'arima']

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

    def validate_data_requirements(
        self,
        df: pd.DataFrame
    ) -> Tuple[bool, List[str]]:
        """
        Valida que los datos cumplan los requisitos minimos.
        RN-01.01: Minimo 6 meses de datos historicos.

        Returns:
            Tuple[bool, List[str]]: (cumple_requisitos, lista_de_problemas)
        """
        issues = []

        if df.empty:
            issues.append("No hay datos disponibles")
            return False, issues

        # Verificar rango minimo de fechas (no cantidad de filas, ya que
        # la agregacion semanal/mensual reduce el numero de registros)
        date_span = (df['fecha'].max() - df['fecha'].min()).days
        if date_span < self.MIN_HISTORICAL_DAYS:
            issues.append(
                f"Rango de datos insuficiente: {date_span} días. "
                f"Mínimo requerido: {self.MIN_HISTORICAL_DAYS} días (6 meses)"
            )

        # Verificar valores nulos
        null_count = df['total'].isna().sum()
        if null_count > 0:
            issues.append(f"Hay {null_count} valores nulos en los datos")

        # Verificar valores negativos
        negative_count = (df['total'] < 0).sum()
        if negative_count > 0:
            issues.append(f"Hay {negative_count} valores negativos")

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

        # Validar requisitos
        valid, issues = self.validate_data_requirements(df)
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
                    else "Modelo no cumple umbral minimo R2=0.7"
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
        compras_data=None
    ) -> BaseModel:
        """Crea una instancia del modelo especificado."""
        params = hyperparameters or {}

        if model_type == 'linear':
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

        # Entrenar todos los modelos
        model_types = ['linear', 'arima', 'sarima', 'random_forest']
        results = {}
        predictions = {}

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

            except Exception as e:
                logger.warning(f"Error con modelo {model_type}: {str(e)}")
                continue

        if not results:
            return {
                "success": False,
                "error": "No se pudo entrenar ningun modelo"
            }

        # Comparar modelos
        best_model = max(
            results.items(),
            key=lambda x: x[1].get("metrics", {}).get("r2_score", 0)
        )

        best_type, best_result = best_model
        meets_threshold = best_result.get("metrics", {}).get("r2_score", 0) >= self.R2_THRESHOLD

        return {
            "success": True,
            "best_model": {
                "type": best_type,
                "model_key": best_result.get("model_key"),
                "metrics": best_result.get("metrics")
            },
            "meets_r2_threshold": meets_threshold,
            "all_models": {
                k: {
                    "metrics": v.get("metrics"),
                    "meets_threshold": v.get("meets_r2_threshold")
                }
                for k, v in results.items()
            },
            "recommendation": (
                f"Usar modelo {best_type} con R2={best_result.get('metrics', {}).get('r2_score', 0):.4f}"
                if meets_threshold else
                "Ningun modelo cumple el umbral minimo. Considerar mas datos."
            )
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
            # Determinar tipo de modelo desde el nombre
            # Los nombres tienen formato: {model_type}_{timestamp}
            # Ej: linear_20260202, random_forest_20260202, arima_20260202
            model_type = self._extract_model_type_from_key(model_key)

            # Crear instancia vacia del modelo correcto
            model = self._create_model(model_type)

            # Cargar estado desde disco
            model.load(model_path)

            # Guardar en memoria
            self._trained_models[model_key] = model

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

        Args:
            user_id: ID del usuario

        Returns:
            Lista de dicts con info del modelo y versión
        """
        try:
            modelos = self.modelo_repo.get_by_usuario(user_id)
            result = []
            for modelo in modelos:
                version = self.version_repo.get_ultima_version(modelo.idModelo)
                if version is None:
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
