"""
Router de Dashboard y Reportes.
RF-07: Dashboard ejecutivo y generacion de reportes.
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import PlainTextResponse
from sqlalchemy.orm import Session
from typing import Optional, List
from datetime import date, timedelta
from pydantic import BaseModel, Field
from enum import Enum

from app.database import get_db
from app.middleware.auth_middleware import get_current_user
from app.services.dashboard_service import DashboardService
from app.services.report_service import ReportService

router = APIRouter(prefix="/dashboard", tags=["Dashboard y Reportes"])


# === Enums ===

class ReportFormat(str, Enum):
    JSON = "json"
    CSV = "csv"
    EXCEL = "excel"


class GroupBy(str, Enum):
    DIA = "dia"
    SEMANA = "semana"
    MES = "mes"


class ReportType(str, Enum):
    VENTAS = "ventas"
    COMPRAS = "compras"
    RENTABILIDAD = "rentabilidad"
    PRODUCTOS = "productos"


# === Schemas ===

class GenerateReportRequest(BaseModel):
    """Request para generar reporte."""
    tipo: ReportType = Field(..., description="Tipo de reporte")
    fecha_inicio: date = Field(..., description="Fecha inicio del periodo")
    fecha_fin: date = Field(..., description="Fecha fin del periodo")
    formato: ReportFormat = Field(default=ReportFormat.JSON, description="Formato de salida")
    agrupar_por: GroupBy = Field(default=GroupBy.DIA, description="Agrupacion temporal")
    top_n: int = Field(default=20, ge=1, le=100, description="Top N (para reporte de productos)")


# === Endpoints Dashboard ===

@router.get("/executive", summary="Dashboard ejecutivo")
async def get_executive_dashboard(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicio (default: 30 dias atras)"),
    fecha_fin: Optional[date] = Query(None, description="Fecha fin (default: hoy)"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene el dashboard ejecutivo con KPIs consolidados.

    Incluye:
    - Resumen de ventas del periodo
    - Resumen de compras del periodo
    - KPIs financieros (utilidad, margen, ROI)
    - Alertas activas
    - Tendencias semanales
    - Top productos vendidos
    """
    service = DashboardService(db)
    result = service.get_executive_dashboard(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=500,
            detail=result.get("error", "Error al generar dashboard")
        )

    return result


@router.get("/kpi/{kpi_name}", summary="Detalle de KPI")
async def get_kpi_detail(
    kpi_name: str,
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicio"),
    fecha_fin: Optional[date] = Query(None, description="Fecha fin"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene detalle de un KPI especifico.

    KPIs disponibles:
    - ventas: Detalle de ventas con serie temporal
    - compras: Detalle de compras con serie temporal
    - margen: Detalle de margen bruto
    - roi: Detalle de retorno de inversion
    - alertas: Detalle de alertas por tipo y estado
    """
    service = DashboardService(db)
    result = service.get_kpi_detail(
        kpi_name=kpi_name,
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al obtener KPI")
        )

    return result


@router.get("/scenarios", summary="Resumen de escenarios")
async def get_scenarios_summary(
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene resumen de escenarios de simulacion.

    Muestra los escenarios mas recientes y estadisticas generales.
    """
    service = DashboardService(db)
    result = service.get_scenario_summary()

    if not result.get("success"):
        raise HTTPException(
            status_code=500,
            detail=result.get("error", "Error al obtener escenarios")
        )

    return result


@router.get("/predictions", summary="Predicciones recientes")
async def get_recent_predictions(
    limit: int = Query(10, ge=1, le=50, description="Limite de resultados"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene las predicciones mas recientes.

    Incluye tipo de entidad, valor predicho y nivel de confianza.
    """
    service = DashboardService(db)
    result = service.get_recent_predictions(limit=limit)

    if not result.get("success"):
        raise HTTPException(
            status_code=500,
            detail=result.get("error", "Error al obtener predicciones")
        )

    return result


@router.get("/compare", summary="Comparar real vs predicho")
async def compare_actual_vs_predicted(
    fecha_inicio: date = Query(..., description="Fecha inicio"),
    fecha_fin: date = Query(..., description="Fecha fin"),
    tipo_entidad: str = Query("producto", description="Tipo de entidad"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Compara valores reales vs predichos (RF-03.05).

    Calcula la diferencia y porcentaje de error entre
    lo que se predijo y lo que realmente ocurrio.
    """
    service = DashboardService(db)
    result = service.compare_actual_vs_predicted(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        tipo_entidad=tipo_entidad
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=500,
            detail=result.get("error", "Error al comparar")
        )

    return result


# === Endpoints Preferencias de Usuario ===

class PreferenceItem(BaseModel):
    """Item de preferencia."""
    kpi: str = Field(..., description="Nombre del KPI")
    valor: str = Field(default="1", description="Visibilidad: 1=visible, 0=oculto")


class UpdatePreferencesRequest(BaseModel):
    """Request para actualizar preferencias."""
    preferencias: List[PreferenceItem] = Field(..., min_length=1)


@router.get("/users/{user_id}/preferences", summary="Obtener preferencias de usuario")
async def get_user_preferences(
    user_id: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene las preferencias de KPIs de un usuario.
    """
    service = DashboardService(db)
    result = service.get_user_preferences(user_id)

    if not result.get("success"):
        raise HTTPException(
            status_code=500,
            detail=result.get("error", "Error al obtener preferencias")
        )

    return result


@router.put("/users/{user_id}/preferences", summary="Actualizar preferencias de usuario")
async def update_user_preferences(
    user_id: int,
    request: UpdatePreferencesRequest,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Actualiza las preferencias de KPIs de un usuario.
    """
    service = DashboardService(db)
    result = service.update_user_preferences(
        user_id=user_id,
        preferencias=[p.model_dump() for p in request.preferencias]
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al actualizar preferencias")
        )

    return result


# === Endpoints Reportes ===

@router.get("/reports/types", summary="Tipos de reportes disponibles")
async def get_report_types(
    current_user: dict = Depends(get_current_user)
):
    """
    Lista los tipos de reportes disponibles.

    Incluye descripcion y formatos soportados.
    """
    return {
        "success": True,
        "tipos": [
            {
                "tipo": "ventas",
                "descripcion": "Reporte de ventas por periodo",
                "formatos": ["json", "csv", "excel"],
                "agrupaciones": ["dia", "semana", "mes"]
            },
            {
                "tipo": "compras",
                "descripcion": "Reporte de compras por periodo",
                "formatos": ["json", "csv", "excel"],
                "agrupaciones": ["dia", "semana", "mes"]
            },
            {
                "tipo": "rentabilidad",
                "descripcion": "Reporte de rentabilidad mensual",
                "formatos": ["json", "csv", "excel"],
                "agrupaciones": ["mes"]
            },
            {
                "tipo": "productos",
                "descripcion": "Reporte de productos mas vendidos",
                "formatos": ["json", "csv", "excel"],
                "parametros": {"top_n": "Numero de productos (1-100)"}
            }
        ]
    }


@router.post("/reports/generate", summary="Generar reporte")
async def generate_report(
    request: GenerateReportRequest,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Genera un reporte en el formato especificado.

    Tipos de reporte:
    - ventas: Reporte de ventas agrupado por periodo
    - compras: Reporte de compras agrupado por periodo
    - rentabilidad: Reporte de rentabilidad mensual
    - productos: Reporte de productos mas vendidos

    Formatos disponibles:
    - json: Datos estructurados
    - csv: Texto separado por comas
    - excel: Metadatos para generacion de Excel
    """
    service = ReportService(db)

    if request.tipo == ReportType.VENTAS:
        result = service.generate_sales_report(
            fecha_inicio=request.fecha_inicio,
            fecha_fin=request.fecha_fin,
            formato=request.formato.value,
            generado_por=current_user.idUsuario,
            agrupar_por=request.agrupar_por.value
        )
    elif request.tipo == ReportType.COMPRAS:
        result = service.generate_purchases_report(
            fecha_inicio=request.fecha_inicio,
            fecha_fin=request.fecha_fin,
            formato=request.formato.value,
            generado_por=current_user.idUsuario,
            agrupar_por=request.agrupar_por.value
        )
    elif request.tipo == ReportType.RENTABILIDAD:
        result = service.generate_profitability_report(
            fecha_inicio=request.fecha_inicio,
            fecha_fin=request.fecha_fin,
            formato=request.formato.value,
            generado_por=current_user.idUsuario
        )
    elif request.tipo == ReportType.PRODUCTOS:
        result = service.generate_products_report(
            fecha_inicio=request.fecha_inicio,
            fecha_fin=request.fecha_fin,
            formato=request.formato.value,
            generado_por=current_user.idUsuario,
            top_n=request.top_n
        )
    else:
        raise HTTPException(
            status_code=400,
            detail=f"Tipo de reporte no soportado: {request.tipo}"
        )

    if not result.get("success"):
        raise HTTPException(
            status_code=500,
            detail=result.get("error", "Error al generar reporte")
        )

    # Si es CSV, retornar como texto plano
    if request.formato == ReportFormat.CSV:
        return PlainTextResponse(
            content=result.get("contenido", ""),
            media_type="text/csv",
            headers={
                "Content-Disposition": f"attachment; filename={result.get('nombre_archivo', 'reporte.csv')}"
            }
        )

    return result


@router.get("/reports/sales", summary="Reporte rapido de ventas")
async def quick_sales_report(
    fecha_inicio: date = Query(..., description="Fecha inicio"),
    fecha_fin: date = Query(..., description="Fecha fin"),
    formato: ReportFormat = Query(ReportFormat.JSON, description="Formato de salida"),
    agrupar_por: GroupBy = Query(GroupBy.DIA, description="Agrupacion"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Genera reporte rapido de ventas via GET.

    Alternativa simplificada al endpoint POST.
    """
    service = ReportService(db)
    result = service.generate_sales_report(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        formato=formato.value,
        generado_por=current_user.idUsuario,
        agrupar_por=agrupar_por.value
    )

    if not result.get("success"):
        raise HTTPException(status_code=500, detail=result.get("error"))

    if formato == ReportFormat.CSV:
        return PlainTextResponse(
            content=result.get("contenido", ""),
            media_type="text/csv"
        )

    return result


@router.get("/reports/purchases", summary="Reporte rapido de compras")
async def quick_purchases_report(
    fecha_inicio: date = Query(..., description="Fecha inicio"),
    fecha_fin: date = Query(..., description="Fecha fin"),
    formato: ReportFormat = Query(ReportFormat.JSON, description="Formato de salida"),
    agrupar_por: GroupBy = Query(GroupBy.DIA, description="Agrupacion"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Genera reporte rapido de compras via GET.
    """
    service = ReportService(db)
    result = service.generate_purchases_report(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        formato=formato.value,
        generado_por=current_user.idUsuario,
        agrupar_por=agrupar_por.value
    )

    if not result.get("success"):
        raise HTTPException(status_code=500, detail=result.get("error"))

    if formato == ReportFormat.CSV:
        return PlainTextResponse(
            content=result.get("contenido", ""),
            media_type="text/csv"
        )

    return result


@router.get("/reports/profitability", summary="Reporte rapido de rentabilidad")
async def quick_profitability_report(
    fecha_inicio: date = Query(..., description="Fecha inicio"),
    fecha_fin: date = Query(..., description="Fecha fin"),
    formato: ReportFormat = Query(ReportFormat.JSON, description="Formato de salida"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Genera reporte rapido de rentabilidad via GET.
    """
    service = ReportService(db)
    result = service.generate_profitability_report(
        fecha_inicio=fecha_inicio,
        fecha_fin=fecha_fin,
        formato=formato.value,
        generado_por=current_user.idUsuario
    )

    if not result.get("success"):
        raise HTTPException(status_code=500, detail=result.get("error"))

    if formato == ReportFormat.CSV:
        return PlainTextResponse(
            content=result.get("contenido", ""),
            media_type="text/csv"
        )

    return result


@router.get("/reports", summary="Listar reportes generados")
async def list_reports(
    tipo: Optional[str] = Query(None, description="Filtrar por tipo"),
    limit: int = Query(50, ge=1, le=100, description="Limite de resultados"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Lista los reportes generados.

    Puede filtrar por tipo de reporte y usuario.
    """
    service = ReportService(db)
    result = service.list_reports(
        usuario_id=current_user.idUsuario,
        tipo=tipo,
        limit=limit
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=500,
            detail=result.get("error", "Error al listar reportes")
        )

    return result


@router.get("/reports/{id_reporte}", summary="Obtener reporte por ID")
async def get_report(
    id_reporte: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene los metadatos de un reporte generado por ID.
    """
    service = ReportService(db)
    result = service.get_report(id_reporte)

    if not result.get("success"):
        raise HTTPException(
            status_code=404,
            detail=result.get("error", "Reporte no encontrado")
        )

    return result
