"""
Router de alertas automaticas.
RF-04: Generacion de alertas basadas en anomalias y predicciones.
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import Optional
from datetime import date
from pydantic import BaseModel, Field
from enum import Enum

from app.database import get_db
from app.middleware.auth_middleware import get_current_user
from app.services.alert_service import AlertService

router = APIRouter(prefix="/alerts", tags=["Alertas"])


# === Schemas ===

class AlertStatusEnum(str, Enum):
    """Estados de alerta."""
    ACTIVA = "Activa"
    LEIDA = "Leida"
    RESUELTA = "Resuelta"
    IGNORADA = "Ignorada"


class ConfigureThresholdsRequest(BaseModel):
    """Request para configurar umbrales."""
    risk_threshold: Optional[float] = Field(None, ge=1, le=50, description="Umbral de riesgo (%)")
    opportunity_threshold: Optional[float] = Field(None, ge=1, le=100, description="Umbral de oportunidad (%)")
    anomaly_rate_threshold: Optional[float] = Field(None, ge=1, le=20, description="Umbral de tasa de anomalias (%)")


class ChangeStatusRequest(BaseModel):
    """Request para cambiar estado."""
    estado: AlertStatusEnum


# === Endpoints ===

@router.get("", summary="Listar alertas activas")
async def get_active_alerts(
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene alertas activas.

    RN-04.05: Maximo 10 alertas simultaneas.
    RN-04.06: Priorizadas por impacto.

    Las alertas se ordenan por:
    1. Importancia (Alta > Media > Baja)
    2. Tipo (Riesgo > Anomalia > Tendencia > Oportunidad)
    3. Impacto economico
    """
    service = AlertService(db)
    return service.get_active_alerts()


@router.get("/history", summary="Historial de alertas")
async def get_alert_history(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicial"),
    fecha_fin: Optional[date] = Query(None, description="Fecha final"),
    tipo: Optional[str] = Query(None, description="Tipo: Riesgo, Oportunidad, Anomalia, Tendencia"),
    importancia: Optional[str] = Query(None, description="Importancia: Alta, Media, Baja"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene historial de alertas.

    Permite filtrar por:
    - Rango de fechas
    - Tipo de alerta
    - Nivel de importancia
    """
    service = AlertService(db)
    return service.get_alert_history(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        tipo=tipo,
        importancia=importancia
    )


@router.get("/summary", summary="Resumen de alertas")
async def get_alerts_summary(
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene resumen de alertas activas.

    Incluye:
    - Conteo por tipo
    - Conteo por importancia
    - Alertas mas recientes
    - Configuracion actual
    """
    service = AlertService(db)
    return service.get_summary()


@router.put("/{id_alerta}/read", summary="Marcar como leida")
async def mark_alert_as_read(
    id_alerta: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Marca una alerta como leida.

    Cambia el estado de 'Activa' a 'Leida'.
    """
    service = AlertService(db)
    result = service.mark_as_read(id_alerta)

    if not result.get("success"):
        raise HTTPException(
            status_code=404,
            detail=result.get("error", "Alerta no encontrada")
        )

    return result


@router.put("/{id_alerta}/status", summary="Cambiar estado")
async def change_alert_status(
    id_alerta: int,
    request: ChangeStatusRequest,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Cambia el estado de una alerta.

    Estados posibles:
    - Activa: Alerta pendiente de atencion
    - Leida: Vista pero no resuelta
    - Resuelta: Problema atendido
    - Ignorada: Descartada
    """
    service = AlertService(db)
    result = service.change_status(id_alerta, request.estado.value)

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al cambiar estado")
        )

    return result


@router.post("/config", summary="Configurar umbrales")
async def configure_thresholds(
    request: ConfigureThresholdsRequest,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    RF-04.04: Configura umbrales de alertas.

    Umbrales configurables:
    - risk_threshold: Porcentaje de caida para alerta de riesgo (RN-04.01, default 15%)
    - opportunity_threshold: Porcentaje de subida para oportunidad (RN-04.02, default 20%)
    - anomaly_rate_threshold: Porcentaje de anomalias para alerta (RN-04.03, default 5%)
    """
    service = AlertService(db)
    return service.configure_thresholds(
        risk_threshold=request.risk_threshold,
        opportunity_threshold=request.opportunity_threshold,
        anomaly_rate_threshold=request.anomaly_rate_threshold
    )


@router.get("/config", summary="Obtener configuracion")
async def get_configuration(
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene configuracion actual de umbrales.
    """
    service = AlertService(db)
    return service.get_config()


@router.post("/analyze", summary="Analizar y generar alertas")
async def analyze_and_generate_alerts(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicial"),
    fecha_fin: Optional[date] = Query(None, description="Fecha final"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    RF-04.01: Analiza datos y genera alertas automaticas.

    Detecta:
    - Outliers (valores atipicos)
    - Cambios repentinos (caidas/subidas)
    - Rupturas de tendencia
    - Tasas de anomalias altas (RN-04.03)

    Las alertas generadas incluyen:
    - Nivel de confianza (RN-04.04)
    - Prioridad por impacto (RN-04.06)
    """
    service = AlertService(db)
    result = service.analyze_sales_for_alerts(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al analizar datos")
        )

    return result


@router.get("/{id_alerta}", summary="Obtener alerta")
async def get_alert(
    id_alerta: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene detalles de una alerta especifica.
    """
    service = AlertService(db)
    alerta = service.alerta_repo.get_by_id(id_alerta)

    if not alerta:
        raise HTTPException(
            status_code=404,
            detail="Alerta no encontrada"
        )

    return {
        "success": True,
        "alerta": {
            "id_alerta": alerta.idAlerta,
            "tipo": alerta.tipo,
            "importancia": alerta.importancia,
            "metrica": alerta.metrica,
            "valor_actual": float(alerta.valorActual) if alerta.valorActual else 0,
            "valor_esperado": float(alerta.valorEsperado) if alerta.valorEsperado else 0,
            "nivel_confianza": float(alerta.nivelConfianza) if alerta.nivelConfianza else 0,
            "estado": alerta.estado,
            "creada_en": alerta.creadaEn.isoformat() if alerta.creadaEn else None
        }
    }


@router.delete("/{id_alerta}", summary="Eliminar alerta")
async def delete_alert(
    id_alerta: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Elimina una alerta del sistema.

    Esta accion no se puede deshacer.
    """
    service = AlertService(db)
    result = service.delete_alert(id_alerta)

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al eliminar alerta")
        )

    return result


@router.post("/check-prediction/{id_prediccion}", summary="Verificar prediccion")
async def check_prediction_for_alerts(
    id_prediccion: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Verifica si una prediccion genera alertas.

    Evalua:
    - Nivel de confianza de la prediccion
    - Desviacion respecto a valores esperados
    """
    service = AlertService(db)
    result = service.check_prediction_alerts(id_prediccion)

    if not result.get("success"):
        raise HTTPException(
            status_code=404,
            detail=result.get("error", "Prediccion no encontrada")
        )

    return result
