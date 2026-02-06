"""
Router para gestion de Ventas.
Endpoints para consulta y registro de ventas.
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import date
from decimal import Decimal
import logging

from app.database import get_db
from app.models import Venta, DetalleVenta
from app.repositories import VentaRepository, DetalleVentaRepository
from app.schemas.venta import (
    VentaCreate, VentaUpdate, VentaResponse,
    DetalleVentaCreate, DetalleVentaResponse
)
from app.schemas.common import PaginatedResponse, MessageResponse
from app.middleware.auth_middleware import get_current_user
from app.schemas.auth import TokenData

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ventas", tags=["Ventas"])


@router.get("", response_model=List[VentaResponse])
async def listar_ventas(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicial del rango"),
    fecha_fin: Optional[date] = Query(None, description="Fecha final del rango"),
    skip: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=1000),
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Lista todas las ventas con filtros opcionales.

    - **fecha_inicio**: Filtrar desde esta fecha
    - **fecha_fin**: Filtrar hasta esta fecha
    - **skip**: Registros a saltar (paginacion)
    - **limit**: Maximo de registros a retornar
    """
    repo = VentaRepository(db)

    if fecha_inicio and fecha_fin:
        ventas = repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
    else:
        ventas = repo.get_all(skip=skip, limit=limit)

    return ventas


@router.get("/{id_venta}", response_model=VentaResponse)
async def obtener_venta(
    id_venta: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene una venta por su ID.
    """
    repo = VentaRepository(db)
    venta = repo.get_by_id(id_venta)

    if not venta:
        raise HTTPException(status_code=404, detail="Venta no encontrada")

    return venta


@router.post("", response_model=VentaResponse, status_code=201)
async def crear_venta(
    venta_data: VentaCreate,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Crea una nueva venta.

    - **fecha**: Fecha de la venta
    - **total**: Monto total
    - **detalles**: Lista de productos vendidos (opcional)
    """
    repo = VentaRepository(db)
    detalle_repo = DetalleVentaRepository(db)

    # Crear venta
    venta = Venta(
        fecha=venta_data.fecha,
        total=venta_data.total,
        moneda=venta_data.moneda,
        creadoPor=current_user.idUsuario
    )

    created_venta = repo.create(venta)
    if not created_venta:
        raise HTTPException(status_code=400, detail="Error al crear venta")

    # Crear detalles si existen
    if venta_data.detalles:
        for i, detalle in enumerate(venta_data.detalles, start=1):
            det = DetalleVenta(
                idVenta=created_venta.idVenta,
                renglon=i,
                idProducto=detalle.idProducto,
                cantidad=detalle.cantidad,
                precioUnitario=detalle.precioUnitario
            )
            detalle_repo.create(det)

    logger.info(f"Venta creada: {created_venta.idVenta} por usuario {current_user.nombreUsuario}")
    return created_venta


@router.put("/{id_venta}", response_model=VentaResponse)
async def actualizar_venta(
    id_venta: int,
    venta_data: VentaUpdate,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Actualiza una venta existente.
    """
    repo = VentaRepository(db)
    venta = repo.get_by_id(id_venta)

    if not venta:
        raise HTTPException(status_code=404, detail="Venta no encontrada")

    update_data = venta_data.model_dump(exclude_unset=True)
    updated_venta = repo.update(id_venta, update_data)

    if not updated_venta:
        raise HTTPException(status_code=400, detail="Error al actualizar venta")

    return updated_venta


@router.delete("/{id_venta}", response_model=MessageResponse)
async def eliminar_venta(
    id_venta: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Elimina una venta.
    """
    repo = VentaRepository(db)
    venta = repo.get_by_id(id_venta)

    if not venta:
        raise HTTPException(status_code=404, detail="Venta no encontrada")

    if not repo.delete(id_venta):
        raise HTTPException(status_code=400, detail="Error al eliminar venta")

    return {"message": f"Venta {id_venta} eliminada exitosamente"}


@router.get("/{id_venta}/detalles", response_model=List[DetalleVentaResponse])
async def obtener_detalles_venta(
    id_venta: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene los detalles de una venta.
    """
    venta_repo = VentaRepository(db)
    if not venta_repo.get_by_id(id_venta):
        raise HTTPException(status_code=404, detail="Venta no encontrada")

    detalle_repo = DetalleVentaRepository(db)
    return detalle_repo.get_by_venta(id_venta)


@router.get("/resumen/mensual")
async def resumen_mensual(
    anio: int = Query(..., ge=2000, le=2100),
    mes: int = Query(..., ge=1, le=12),
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene resumen de ventas de un mes.

    - **anio**: Ano del resumen
    - **mes**: Mes del resumen (1-12)
    """
    repo = VentaRepository(db)
    resumen = repo.get_resumen_mensual(anio, mes)

    return {
        "anio": anio,
        "mes": mes,
        "cantidad_ventas": resumen['cantidad'],
        "total": float(resumen['total']),
        "promedio": float(resumen['promedio'])
    }


@router.get("/total/periodo")
async def total_periodo(
    fecha_inicio: date = Query(...),
    fecha_fin: date = Query(...),
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene el total de ventas en un periodo.
    """
    repo = VentaRepository(db)
    total = repo.get_total_por_periodo(fecha_inicio, fecha_fin)

    return {
        "fecha_inicio": fecha_inicio.isoformat(),
        "fecha_fin": fecha_fin.isoformat(),
        "total": float(total)
    }
