"""
Router de evaluacion de rentabilidad.
RF-06: Evaluacion de rentabilidad de productos y categorias.
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import Optional
from datetime import date, timedelta
from pydantic import BaseModel, Field
from enum import Enum

from app.database import get_db
from app.middleware.auth_middleware import get_current_user
from app.services.profitability_service import ProfitabilityService, PeriodType

router = APIRouter(prefix="/profitability", tags=["Rentabilidad"])


# === Schemas ===

class PeriodTypeEnum(str, Enum):
    """Tipos de periodo para analisis."""
    DAILY = "daily"
    WEEKLY = "weekly"
    MONTHLY = "monthly"
    QUARTERLY = "quarterly"
    YEARLY = "yearly"


class IndicatorsRequest(BaseModel):
    """Request para calcular indicadores."""
    fecha_inicio: Optional[date] = None
    fecha_fin: Optional[date] = None
    activos_totales: Optional[float] = Field(None, description="Total de activos para ROA")
    patrimonio: Optional[float] = Field(None, description="Patrimonio neto para ROE")


class ComparePeriodsRequest(BaseModel):
    """Request para comparar periodos."""
    periodo1_inicio: date
    periodo1_fin: date
    periodo2_inicio: date
    periodo2_fin: date


# === Endpoints ===

@router.post("/indicators", summary="Calcular indicadores financieros")
async def calculate_indicators(
    request: IndicatorsRequest,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    RF-06.01: Calcula indicadores financieros generales.

    Indicadores calculados:
    - Ingresos y costos totales
    - Utilidad bruta y margen bruto
    - Utilidad operativa (RN-06.02)
    - Margen operativo y neto
    - ROA y ROE (si se proporcionan activos/patrimonio)
    """
    service = ProfitabilityService(db)
    result = service.calculate_indicators(
        fecha_inicio=request.fecha_inicio,
        fecha_fin=request.fecha_fin,
        activos_totales=request.activos_totales,
        patrimonio=request.patrimonio
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail={
                "message": result.get("error", "Error al calcular indicadores"),
                "issues": result.get("issues", [])
            }
        )

    return result


@router.get("/products", summary="Rentabilidad por producto")
async def get_product_profitability(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicial"),
    fecha_fin: Optional[date] = Query(None, description="Fecha final"),
    categoria_id: Optional[int] = Query(None, description="Filtrar por categoria"),
    solo_no_rentables: bool = Query(False, description="Solo productos no rentables"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    RF-06.02: Obtiene rentabilidad por producto.

    RN-06.04: Identifica productos no rentables (margen < 10%).

    Retorna para cada producto:
    - Unidades vendidas
    - Ingresos y costos
    - Utilidad y margen
    - Indicador de rentabilidad
    """
    service = ProfitabilityService(db)
    result = service.get_product_profitability(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        categoria_id=categoria_id,
        solo_no_rentables=solo_no_rentables
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al obtener rentabilidad")
        )

    return result


@router.get("/products/non-profitable", summary="Productos no rentables")
async def get_non_profitable_products(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicial"),
    fecha_fin: Optional[date] = Query(None, description="Fecha final"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    RF-06.03: Lista productos con margen < 10% (RN-06.04).

    Permite identificar productos que requieren atencion:
    - Ajuste de precios
    - Reduccion de costos
    - Descontinuacion
    """
    service = ProfitabilityService(db)
    result = service.get_product_profitability(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        solo_no_rentables=True
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al obtener productos no rentables")
        )

    return result


@router.get("/categories", summary="Rentabilidad por categoria")
async def get_category_profitability(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicial"),
    fecha_fin: Optional[date] = Query(None, description="Fecha final"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene rentabilidad agregada por categoria.

    Retorna para cada categoria:
    - Numero de productos
    - Ingresos y costos totales
    - Utilidad y margen
    - Productos rentables vs no rentables
    """
    service = ProfitabilityService(db)
    result = service.get_category_profitability(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al obtener rentabilidad por categoria")
        )

    return result


@router.get("/trends", summary="Tendencias de rentabilidad")
async def get_profitability_trends(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicial"),
    fecha_fin: Optional[date] = Query(None, description="Fecha final"),
    period_type: PeriodTypeEnum = Query(
        PeriodTypeEnum.MONTHLY,
        description="Tipo de periodo"
    ),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    RN-06.03: Obtiene tendencias de rentabilidad por periodo.

    Periodos soportados:
    - daily: Diario
    - weekly: Semanal
    - monthly: Mensual
    - quarterly: Trimestral
    - yearly: Anual

    Incluye variaciones porcentuales vs periodo anterior.
    """
    service = ProfitabilityService(db)

    # Convertir enum del router al enum del servicio
    service_period = PeriodType(period_type.value)

    result = service.get_profitability_trends(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        period_type=service_period
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al obtener tendencias")
        )

    return result


@router.get("/ranking", summary="Ranking de productos")
async def get_product_ranking(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicial"),
    fecha_fin: Optional[date] = Query(None, description="Fecha final"),
    metric: str = Query("utilidad", description="Metrica: utilidad, margen, ingresos, unidades_vendidas"),
    limit: int = Query(10, ge=1, le=100, description="Numero de productos"),
    ascending: bool = Query(False, description="Orden ascendente (para ver peores)"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene ranking de productos por metrica especificada.

    Metricas disponibles:
    - utilidad: Utilidad neta
    - margen: Margen de ganancia (%)
    - ingresos: Ingresos totales
    - unidades_vendidas: Volumen de ventas

    Use ascending=true para ver los productos con peor desempeno.
    """
    service = ProfitabilityService(db)
    result = service.get_product_ranking(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        metric=metric,
        limit=limit,
        ascending=ascending
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al obtener ranking")
        )

    return result


@router.post("/compare", summary="Comparar periodos")
async def compare_periods(
    request: ComparePeriodsRequest,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Compara rentabilidad entre dos periodos.

    Calcula variaciones porcentuales en:
    - Ingresos
    - Costos
    - Utilidad bruta
    - Margen bruto
    - Utilidad operativa

    Incluye conclusion textual automatica.
    """
    service = ProfitabilityService(db)
    result = service.compare_periods(
        periodo1_inicio=request.periodo1_inicio,
        periodo1_fin=request.periodo1_fin,
        periodo2_inicio=request.periodo2_inicio,
        periodo2_fin=request.periodo2_fin
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail={
                "message": result.get("error", "Error al comparar periodos"),
                "periodo1_error": result.get("periodo1_error"),
                "periodo2_error": result.get("periodo2_error")
            }
        )

    return result


@router.get("/summary", summary="Resumen de rentabilidad")
async def get_profitability_summary(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicial"),
    fecha_fin: Optional[date] = Query(None, description="Fecha final"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene resumen ejecutivo de rentabilidad.

    Combina:
    - Indicadores financieros principales
    - Top 5 productos mas rentables
    - Top 5 productos menos rentables
    - Resumen por categorias
    """
    service = ProfitabilityService(db)

    # Calcular indicadores
    indicators = service.calculate_indicators(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin
    )

    # Top 5 mas rentables
    top_products = service.get_product_ranking(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        metric="utilidad",
        limit=5,
        ascending=False
    )

    # Top 5 menos rentables
    bottom_products = service.get_product_ranking(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        metric="utilidad",
        limit=5,
        ascending=True
    )

    # Resumen por categorias
    categories = service.get_category_profitability(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin
    )

    return {
        "success": True,
        "periodo": indicators.get("indicators", {}).get("periodo") if indicators.get("success") else None,
        "indicadores": indicators.get("indicators") if indicators.get("success") else None,
        "top_productos_rentables": top_products.get("ranking", []) if top_products.get("success") else [],
        "productos_menos_rentables": bottom_products.get("ranking", []) if bottom_products.get("success") else [],
        "resumen_categorias": categories.get("categorias", []) if categories.get("success") else [],
        "alertas": _generate_alerts(indicators, top_products, bottom_products)
    }


def _generate_alerts(indicators: dict, top: dict, bottom: dict) -> list:
    """Genera alertas basadas en el analisis."""
    alerts = []

    if indicators.get("success"):
        ind = indicators.get("indicators", {})

        # Alerta de margen bajo
        if ind.get("margen_bruto", 0) < 10:
            alerts.append({
                "tipo": "warning",
                "mensaje": f"Margen bruto bajo: {ind.get('margen_bruto', 0)}% (recomendado > 10%)"
            })

        # Alerta de perdidas
        if ind.get("utilidad_neta", 0) < 0:
            alerts.append({
                "tipo": "critical",
                "mensaje": "La empresa esta operando con perdidas"
            })

    # Alerta de productos no rentables
    if bottom.get("success"):
        no_rentables = [p for p in bottom.get("ranking", []) if not p.get("es_rentable", True)]
        if no_rentables:
            alerts.append({
                "tipo": "info",
                "mensaje": f"Hay {len(no_rentables)} productos con margen < 10% que requieren atencion"
            })

    return alerts
