"""
Router de predicciones.
Endpoints para entrenamiento, prediccion y gestion de modelos.
RF-02: Prediccion de ventas.
"""

from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.orm import Session
from typing import Optional, List
from datetime import datetime, date
from pydantic import BaseModel, Field
import pandas as pd
import asyncio
from concurrent.futures import ThreadPoolExecutor

from app.database import get_db, db_manager
from app.services.prediction_service import PredictionService
from app.middleware.auth_middleware import get_current_user
from app.schemas.auth import TokenData
from app.schemas.prediction import (
    PackTrainRequest, PackTrainResponse, PackMetrics,
    PackForecastRequest, PackForecastResponse, PackForecastSeries,
    PackVersionInfo, PackInfoResponse,
)

router = APIRouter(prefix="/predictions", tags=["Predicciones"])

# Executor dedicado para entrenamientos ML (CPU-bound).
# max_workers=2 evita saturar la CPU; los entrenamientos son infrecuentes y pesados.
_train_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="ml-train")


# ==================== SCHEMAS ====================

class TrainModelRequest(BaseModel):
    """Request para entrenar un modelo."""
    model_type: str = Field(
        ...,
        description="Tipo de modelo: linear, arima, sarima, random_forest"
    )
    fecha_inicio: Optional[date] = Field(
        None,
        description="Fecha inicio de datos de entrenamiento"
    )
    fecha_fin: Optional[date] = Field(
        None,
        description="Fecha fin de datos de entrenamiento"
    )
    hyperparameters: Optional[dict] = Field(
        None,
        description="Hiperparametros del modelo"
    )
    nombre: Optional[str] = Field(
        None,
        description="Nombre descriptivo para identificar el modelo"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "model_type": "random_forest",
                "fecha_inicio": "2024-01-01",
                "fecha_fin": "2024-12-31",
                "hyperparameters": {
                    "n_estimators": 100,
                    "max_depth": 10
                },
                "nombre": "Modelo ventas Q1 2024"
            }
        }


class ForecastRequest(BaseModel):
    """Request para generar predicciones."""
    model_key: Optional[str] = Field(
        None,
        description="Clave del modelo a usar"
    )
    model_type: Optional[str] = Field(
        None,
        description="Tipo de modelo si no se especifica model_key"
    )
    periods: int = Field(
        30,
        ge=1,
        le=180,
        description="Numero de periodos a predecir (max 180 dias)"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "model_type": "random_forest",
                "periods": 30
            }
        }


class AutoSelectRequest(BaseModel):
    """Request para seleccion automatica de modelo."""
    fecha_inicio: Optional[date] = Field(
        None,
        description="Fecha inicio de datos"
    )
    fecha_fin: Optional[date] = Field(
        None,
        description="Fecha fin de datos"
    )


class TrainModelResponse(BaseModel):
    """Response de entrenamiento de modelo."""
    success: bool
    model_id: Optional[int] = None
    model_key: Optional[str] = None
    model_type: Optional[str] = None
    metrics: Optional[dict] = None
    meets_r2_threshold: Optional[bool] = None
    recommendation: Optional[str] = None
    training_samples: Optional[int] = None
    test_samples: Optional[int] = None
    error: Optional[str] = None
    issues: Optional[List[str]] = None


class ForecastResponse(BaseModel):
    """Response de prediccion."""
    success: bool
    predictions: Optional[dict] = None
    model_type: Optional[str] = None
    model_metrics: Optional[dict] = None
    periods: Optional[int] = None
    error: Optional[str] = None
    suggestion: Optional[str] = None
    quality_warning: Optional[bool] = None
    quality_message: Optional[str] = None


class ModelInfoResponse(BaseModel):
    """Informacion de un modelo entrenado."""
    model_key: str
    model_type: str
    is_fitted: bool
    metrics: Optional[dict] = None
    trained_at: Optional[str] = None


class AutoSelectResponse(BaseModel):
    """Response de seleccion automatica."""
    success: bool
    best_model: Optional[dict] = None
    meets_r2_threshold: Optional[bool] = None
    all_models: Optional[dict] = None
    recommendation: Optional[str] = None
    error: Optional[str] = None
    issues: Optional[List[str]] = None


class PredictionHistoryItem(BaseModel):
    """Item del historial de predicciones."""
    id: int
    fecha: Optional[str] = None
    valor_predicho: Optional[float] = None
    intervalo_inferior: Optional[float] = None
    intervalo_superior: Optional[float] = None
    confianza: Optional[float] = None


class UserModelResponse(BaseModel):
    """Modelo entrenado por el usuario."""
    model_id: int
    version_id: int
    model_key: Optional[str] = None
    model_type: Optional[str] = None
    nombre: Optional[str] = None
    precision: Optional[float] = None
    metricas: Optional[dict] = None
    estado: Optional[str] = None
    fecha_entrenamiento: Optional[str] = None
    is_loaded: bool = False


class SalesDataRequest(BaseModel):
    """Request para obtener datos de ventas."""
    fecha_inicio: Optional[date] = None
    fecha_fin: Optional[date] = None
    aggregation: str = Field(
        "D",
        description="Nivel de agregacion: D=diario, W=semanal, M=mensual"
    )


# ==================== ENDPOINTS ====================

@router.post(
    "/train",
    response_model=TrainModelResponse,
    summary="Entrenar modelo predictivo",
    description="""
    Entrena un modelo predictivo con los datos de ventas.

    Tipos de modelo disponibles:
    - linear: Regresion lineal con features temporales
    - arima: Modelo ARIMA para series de tiempo
    - sarima: Modelo SARIMA con estacionalidad (RN-03.05)
    - random_forest: Random Forest para prediccion (RF-02.03)

    Reglas de negocio:
    - RN-01.01: Minimo 6 meses de datos historicos
    - RN-03.01: Split 70/30 para entrenamiento/validacion
    - RN-03.02: R2 minimo de 0.7 para considerar modelo usable
    """
)
async def train_model(
    request: TrainModelRequest,
    current_user: TokenData = Depends(get_current_user)
):
    """Entrena un modelo predictivo."""
    # Extraer valores del request antes de entrar al executor (thread safety)
    fecha_inicio = datetime.combine(request.fecha_inicio, datetime.min.time()) if request.fecha_inicio else None
    fecha_fin = datetime.combine(request.fecha_fin, datetime.min.time()) if request.fecha_fin else None
    model_type = request.model_type
    hyperparameters = request.hyperparameters
    nombre = request.nombre
    user_id = current_user.idUsuario

    def _blocking():
        with db_manager.get_session_context() as db:
            service = PredictionService(db)
            return service.train_model(
                model_type=model_type,
                fecha_inicio=fecha_inicio,
                fecha_fin=fecha_fin,
                hyperparameters=hyperparameters,
                user_id=user_id,
                nombre=nombre
            )

    loop = asyncio.get_running_loop()
    result = await loop.run_in_executor(_train_executor, _blocking)
    return TrainModelResponse(**result)


@router.post(
    "/forecast",
    response_model=ForecastResponse,
    summary="Generar predicciones",
    description="""
    Genera predicciones de ventas futuras usando un modelo entrenado.

    Reglas de negocio:
    - RN-03.02: Solo modelos con R2 >= 0.7 pueden generar predicciones
    - RN-03.03: Maximo 6 meses (180 dias) de prediccion
    """
)
async def forecast(
    request: ForecastRequest,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Genera predicciones de ventas."""
    service = PredictionService(db)

    result = service.forecast(
        model_key=request.model_key,
        periods=request.periods,
        model_type=request.model_type
    )

    return ForecastResponse(**result)


@router.post(
    "/auto-select",
    response_model=AutoSelectResponse,
    summary="Seleccion automatica de modelo",
    description="""
    RF-02.06: Selecciona automaticamente el mejor modelo.

    Entrena todos los tipos de modelo disponibles y selecciona
    el que tenga mejor desempeno segun la metrica R2.
    """
)
async def auto_select_model(
    request: AutoSelectRequest,
    current_user: TokenData = Depends(get_current_user)
):
    """Selecciona automaticamente el mejor modelo."""
    fecha_inicio = datetime.combine(request.fecha_inicio, datetime.min.time()) if request.fecha_inicio else None
    fecha_fin = datetime.combine(request.fecha_fin, datetime.min.time()) if request.fecha_fin else None
    user_id = current_user.idUsuario

    def _blocking():
        with db_manager.get_session_context() as db:
            service = PredictionService(db)
            return service.auto_select_model(
                fecha_inicio=fecha_inicio,
                fecha_fin=fecha_fin,
                user_id=user_id
            )

    loop = asyncio.get_running_loop()
    result = await loop.run_in_executor(_train_executor, _blocking)
    return AutoSelectResponse(**result)


@router.get(
    "/models",
    response_model=List[ModelInfoResponse],
    summary="Listar modelos entrenados",
    description="Obtiene la lista de todos los modelos entrenados en la sesion."
)
async def list_models(
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Lista todos los modelos entrenados."""
    service = PredictionService(db)
    models = service.get_trained_models()
    return [ModelInfoResponse(**m) for m in models]


@router.get(
    "/models/user",
    response_model=List[UserModelResponse],
    summary="Modelos del usuario autenticado",
    description="Obtiene todos los modelos entrenados por el usuario actual, con su última versión activa."
)
async def get_user_models(
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Obtiene modelos del usuario autenticado."""
    service = PredictionService(db)
    models = service.get_user_models(current_user.idUsuario)
    return [UserModelResponse(**m) for m in models]


@router.get(
    "/history",
    response_model=List[PredictionHistoryItem],
    summary="Historial de predicciones",
    description="Obtiene el historial de predicciones guardadas."
)
async def get_prediction_history(
    limit: int = Query(100, ge=1, le=1000, description="Limite de registros"),
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Obtiene historial de predicciones."""
    service = PredictionService(db)
    history = service.get_prediction_history(limit=limit)
    return [PredictionHistoryItem(**h) for h in history]


@router.post(
    "/sales-data",
    summary="Obtener datos de ventas para analisis",
    description="""
    Obtiene datos de ventas agregados para analisis y visualizacion.

    Niveles de agregacion:
    - D: Diario
    - W: Semanal
    - M: Mensual
    """
)
async def get_sales_data(
    request: SalesDataRequest,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Obtiene datos de ventas agregados."""
    service = PredictionService(db)

    fecha_inicio = datetime.combine(request.fecha_inicio, datetime.min.time()) if request.fecha_inicio else None
    fecha_fin = datetime.combine(request.fecha_fin, datetime.min.time()) if request.fecha_fin else None

    df = service.get_sales_data(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        aggregation=request.aggregation,
        user_id=current_user.idUsuario
    )

    # Convertir DataFrame a lista de diccionarios
    data = df.to_dict(orient='records')

    # Convertir fechas a string
    for row in data:
        if 'fecha' in row and hasattr(row['fecha'], 'isoformat'):
            row['fecha'] = row['fecha'].isoformat()

    return {
        "success": True,
        "data": data,
        "count": len(data),
        "aggregation": request.aggregation
    }


@router.post(
    "/purchases-data",
    summary="Obtener datos de compras para analisis",
    description="""
    Obtiene datos de compras agregados para analisis y visualizacion.

    Niveles de agregacion:
    - D: Diario
    - W: Semanal
    - M: Mensual
    """
)
async def get_purchases_data(
    request: SalesDataRequest,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Obtiene datos de compras agregados."""
    service = PredictionService(db)

    fecha_inicio = datetime.combine(request.fecha_inicio, datetime.min.time()) if request.fecha_inicio else None
    fecha_fin = datetime.combine(request.fecha_fin, datetime.min.time()) if request.fecha_fin else None

    df = service.get_compras_data(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        user_id=current_user.idUsuario
    )

    # Aplicar agregacion igual que sales-data
    if not df.empty:
        df['fecha'] = pd.to_datetime(df['fecha'])
        agg = request.aggregation
        if agg == 'W':
            df = df.set_index('fecha').resample('W-SUN')['total'].sum().reset_index()
        elif agg == 'M':
            df = df.set_index('fecha').resample('M')['total'].sum().reset_index()

    data = df.to_dict(orient='records')
    for row in data:
        if 'fecha' in row and hasattr(row['fecha'], 'isoformat'):
            row['fecha'] = row['fecha'].isoformat()

    return {
        "success": True,
        "data": data,
        "count": len(data),
        "aggregation": request.aggregation
    }


@router.post(
    "/validate-data",
    summary="Validar datos para prediccion",
    description="""
    Valida que los datos cumplan los requisitos minimos para prediccion.

    RN-01.01: Minimo 6 meses de datos historicos.
    """
)
async def validate_data(
    request: SalesDataRequest,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Valida datos para prediccion."""
    service = PredictionService(db)

    fecha_inicio = datetime.combine(request.fecha_inicio, datetime.min.time()) if request.fecha_inicio else None
    fecha_fin = datetime.combine(request.fecha_fin, datetime.min.time()) if request.fecha_fin else None

    df = service.get_sales_data(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        aggregation=request.aggregation,
        user_id=current_user.idUsuario
    )

    valid, issues = service.validate_data_requirements(df)

    return {
        "valid": valid,
        "issues": issues,
        "data_points": len(df),
        "min_required": service.MIN_HISTORICAL_DAYS,
        "date_range": {
            "start": df['fecha'].min().isoformat() if not df.empty else None,
            "end": df['fecha'].max().isoformat() if not df.empty else None
        }
    }


@router.get(
    "/model-types",
    summary="Obtener tipos de modelo disponibles",
    description="Lista los tipos de modelo predictivo disponibles."
)
async def get_model_types(
    current_user: TokenData = Depends(get_current_user)
):
    """Lista tipos de modelo disponibles."""
    return {
        "model_types": [
            {
                "id": "linear",
                "name": "Regresion Lineal",
                "description": "Modelo de regresion lineal con features temporales",
                "use_case": "Tendencias lineales simples"
            },
            {
                "id": "multiple_regression",
                "name": "Regresion Multiple",
                "description": (
                    "Regresion lineal enriquecida con variables exogenas (compras), "
                    "lags de ventas, medias moviles y componentes ciclicos. "
                    "Regularizacion Ridge/Lasso/ElasticNet configurable."
                ),
                "use_case": (
                    "Mercados donde las compras (reposicion) anticipan ventas. "
                    "Supera a la regresion lineal simple en datos con autocorrelacion."
                )
            },
            {
                "id": "arima",
                "name": "ARIMA",
                "description": "AutoRegressive Integrated Moving Average",
                "use_case": "Series de tiempo sin estacionalidad marcada"
            },
            {
                "id": "sarima",
                "name": "SARIMA",
                "description": "Seasonal ARIMA con componente estacional",
                "use_case": "Series con patrones estacionales (RN-03.05)"
            },
            {
                "id": "random_forest",
                "name": "Random Forest",
                "description": "Ensemble de arboles de decision",
                "use_case": "Patrones complejos no lineales (RF-02.03)"
            },
            {
                "id": "ensemble",
                "name": "Modelo Ensemble",
                "description": "Stacking de modelos base con meta-learner Ridge o promedio ponderado",
                "use_case": "Maximo rendimiento combinando tendencia, lags y no-linealidad"
            },
            {
                "id": "xgboost",
                "name": "XGBoost",
                "description": "Gradient Boosting con regularizacion L1/L2 y lags automaticos",
                "use_case": "Relaciones no lineales complejas con alta precisión"
            },
            {
                "id": "prophet",
                "name": "Prophet",
                "description": "Modelo de Facebook para series de negocio con estacionalidad multiple",
                "use_case": "Negocios con patrones estacionales anuales y semanales claros"
            }
        ],
        "r2_threshold": PredictionService.R2_THRESHOLD,
        "max_forecast_days": 180
    }


# ==================== PERSISTENCIA DE MODELOS ====================

class LoadModelRequest(BaseModel):
    """Request para cargar un modelo."""
    model_key: str = Field(..., description="Clave del modelo a cargar")


class LoadModelResponse(BaseModel):
    """Response de carga de modelo."""
    success: bool
    model_key: Optional[str] = None
    model_type: Optional[str] = None
    is_fitted: Optional[bool] = None
    metrics: Optional[dict] = None
    trained_at: Optional[str] = None
    path: Optional[str] = None
    error: Optional[str] = None


class SavedModelInfo(BaseModel):
    """Informacion de modelo guardado en disco."""
    model_key: str
    filename: str
    size_bytes: int
    created_at: str
    modified_at: str
    is_loaded: bool


class LoadAllModelsResponse(BaseModel):
    """Response de carga de todos los modelos."""
    success: bool
    loaded: List[dict] = []
    failed: List[dict] = []
    total_loaded: int = 0
    total_failed: int = 0
    message: Optional[str] = None


class DeleteModelResponse(BaseModel):
    """Response de eliminacion de modelo."""
    success: bool
    model_key: Optional[str] = None
    deleted_from_memory: Optional[bool] = None
    deleted_from_disk: Optional[bool] = None
    error: Optional[str] = None


@router.post(
    "/models/load",
    response_model=LoadModelResponse,
    summary="Cargar modelo desde disco",
    description="""
    Carga un modelo previamente entrenado y guardado en disco.
    Esto permite reutilizar modelos entre reinicios del servidor.
    """
)
async def load_model(
    request: LoadModelRequest,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Carga un modelo desde disco."""
    service = PredictionService(db)
    result = service.load_model(request.model_key)
    return LoadModelResponse(**result)


@router.post(
    "/models/load-all",
    response_model=LoadAllModelsResponse,
    summary="Cargar todos los modelos guardados",
    description="""
    Carga todos los modelos disponibles en el directorio de modelos.
    Util para restaurar el estado despues de un reinicio del servidor.
    """
)
async def load_all_models(
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Carga todos los modelos desde disco."""
    service = PredictionService(db)
    result = service.load_all_models()
    return LoadAllModelsResponse(**result)


@router.get(
    "/models/saved",
    response_model=List[SavedModelInfo],
    summary="Listar modelos guardados en disco",
    description="""
    Lista todos los modelos que estan guardados en disco,
    incluyendo informacion sobre si estan cargados en memoria.
    """
)
async def list_saved_models(
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Lista modelos guardados en disco."""
    service = PredictionService(db)
    return service.get_saved_models()


@router.delete(
    "/models/{model_key}",
    response_model=DeleteModelResponse,
    summary="Eliminar modelo",
    description="""
    Elimina un modelo de memoria y de disco.
    Esta accion es irreversible.
    """
)
async def delete_model(
    model_key: str,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Elimina un modelo."""
    service = PredictionService(db)
    result = service.delete_model(model_key)
    return DeleteModelResponse(**result)


# Pack schemas imported from app.schemas.prediction

@router.post(
    "/train-pack",
    response_model=PackTrainResponse,
    summary="Entrenar pack de modelos (ventas + compras)",
    description="""
    Entrena dos modelos en un solo paso:
    - Modelo de ventas (multiple_regression con compras como exógena)
    - Modelo de compras (multiple_regression con ventas como exógena)

    Ambos se guardan juntos en un ModeloPack para forecast coordinado.
    Timeout recomendado en cliente: 240s (dos entrenamientos).
    """
)
async def train_pack(
    request: PackTrainRequest,
    current_user: TokenData = Depends(get_current_user)
):
    """Entrena pack de modelos ventas+compras."""
    fecha_inicio = datetime.combine(request.fecha_inicio, datetime.min.time()) if request.fecha_inicio else None
    fecha_fin    = datetime.combine(request.fecha_fin,    datetime.min.time()) if request.fecha_fin    else None
    nombre = request.nombre
    hyperparameters = request.hyperparameters
    user_id = current_user.idUsuario
    ventas_model_type = request.ventas_model_type or "multiple_regression"

    def _blocking():
        with db_manager.get_session_context() as db:
            service = PredictionService(db)
            return service.train_pack(
                nombre=nombre,
                fecha_inicio=fecha_inicio,
                fecha_fin=fecha_fin,
                hyperparameters=hyperparameters,
                user_id=user_id,
                ventas_model_type=ventas_model_type
            )

    loop = asyncio.get_running_loop()
    result = await loop.run_in_executor(_train_executor, _blocking)

    if not result.get("success"):
        return PackTrainResponse(**result)

    return PackTrainResponse(
        success=True,
        pack_id=result["pack_id"],
        pack_key=result["pack_key"],
        ventas=PackMetrics(**result["ventas"]),
        compras=PackMetrics(**result["compras"])
    )


@router.get(
    "/packs",
    response_model=List[PackInfoResponse],
    summary="Listar packs del usuario",
    description="Obtiene los packs activos entrenados por el usuario autenticado."
)
async def get_user_packs(
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Lista packs del usuario."""
    service = PredictionService(db)
    packs = service.get_user_packs(current_user.idUsuario)
    return [PackInfoResponse(**p) for p in packs]


@router.post(
    "/forecast-pack",
    response_model=PackForecastResponse,
    summary="Forecast coordinado de un pack",
    description="""
    Genera predicciones coordinadas:
    1. forecast compras (independiente)
    2. forecast ventas usando compras predichas (mayor precisión)

    Retorna dos series: ventas y compras para los próximos N períodos.
    """
)
async def forecast_pack(
    request: PackForecastRequest,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Genera forecast coordinado del pack."""
    service = PredictionService(db)
    result = service.forecast_pack(
        pack_key=request.pack_key,
        periods=request.periods
    )

    if not result.get("success"):
        return PackForecastResponse(**result)

    return PackForecastResponse(
        success=True,
        pack_key=result["pack_key"],
        periods=result["periods"],
        ventas=PackForecastSeries(**result["ventas"]),
        compras=PackForecastSeries(**result["compras"])
    )
