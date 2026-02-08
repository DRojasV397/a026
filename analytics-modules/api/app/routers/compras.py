"""
Router para gestion de Compras.
Endpoints para consulta y registro de compras.
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import date
from decimal import Decimal
import logging

from app.database import get_db
from app.models import Compra, DetalleCompra
from app.repositories import CompraRepository, DetalleCompraRepository
from app.schemas.compra import (
    CompraCreate, CompraUpdate, CompraResponse, CompraConDetalles,
    DetalleCompraCreate, DetalleCompraResponse
)
from app.schemas.common import MessageResponse
from app.middleware.auth_middleware import get_current_user
from app.schemas.auth import TokenData

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/compras", tags=["Compras"])


@router.get("", response_model=List[CompraResponse])
async def listar_compras(
    fecha_inicio: Optional[date] = Query(None, description="Fecha inicial del rango"),
    fecha_fin: Optional[date] = Query(None, description="Fecha final del rango"),
    proveedor: Optional[str] = Query(None, description="Filtrar por proveedor"),
    skip: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=1000),
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Lista todas las compras con filtros opcionales.

    - **fecha_inicio**: Filtrar desde esta fecha
    - **fecha_fin**: Filtrar hasta esta fecha
    - **proveedor**: Filtrar por nombre de proveedor
    - **skip**: Registros a saltar (paginacion)
    - **limit**: Maximo de registros a retornar
    """
    repo = CompraRepository(db)

    if proveedor:
        compras = repo.get_by_proveedor(proveedor)
    elif fecha_inicio and fecha_fin:
        compras = repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
    else:
        compras = repo.get_all(skip=skip, limit=limit)

    return compras


@router.get("/{id_compra}", response_model=CompraResponse)
async def obtener_compra(
    id_compra: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene una compra por su ID.
    """
    repo = CompraRepository(db)
    compra = repo.get_by_id(id_compra)

    if not compra:
        raise HTTPException(status_code=404, detail="Compra no encontrada")

    return compra


@router.get("/{id_compra}/completa", response_model=CompraConDetalles)
async def obtener_compra_completa(
    id_compra: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene una compra con sus detalles.
    """
    compra_repo = CompraRepository(db)
    compra = compra_repo.get_by_id(id_compra)

    if not compra:
        raise HTTPException(status_code=404, detail="Compra no encontrada")

    detalle_repo = DetalleCompraRepository(db)
    detalles = detalle_repo.get_by_compra(id_compra)

    return CompraConDetalles(
        idCompra=compra.idCompra,
        fecha=compra.fecha,
        proveedor=compra.proveedor,
        total=compra.total,
        moneda=compra.moneda,
        creadoPor=compra.creadoPor,
        detalles=detalles
    )


@router.post("", response_model=CompraResponse, status_code=201)
async def crear_compra(
    compra_data: CompraCreate,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Crea una nueva compra.

    - **fecha**: Fecha de la compra
    - **proveedor**: Nombre del proveedor
    - **total**: Monto total
    - **detalles**: Lista de productos comprados (opcional)
    """
    repo = CompraRepository(db)
    detalle_repo = DetalleCompraRepository(db)

    # Crear compra
    compra = Compra(
        fecha=compra_data.fecha,
        proveedor=compra_data.proveedor,
        total=compra_data.total,
        moneda=compra_data.moneda,
        creadoPor=current_user.idUsuario
    )

    created_compra = repo.create(compra)
    if not created_compra:
        raise HTTPException(status_code=400, detail="Error al crear compra")

    # Crear detalles si existen
    if compra_data.detalles:
        for i, detalle in enumerate(compra_data.detalles, start=1):
            det = DetalleCompra(
                idCompra=created_compra.idCompra,
                renglon=i,
                idProducto=detalle.idProducto,
                cantidad=detalle.cantidad,
                costo=detalle.costo,
                descuento=detalle.descuento,
                subtotal=detalle.cantidad * detalle.costo - (detalle.descuento or 0)
            )
            detalle_repo.create(det)

    logger.info(f"Compra creada: {created_compra.idCompra} por usuario {current_user.nombreUsuario}")
    return created_compra


@router.put("/{id_compra}", response_model=CompraResponse)
async def actualizar_compra(
    id_compra: int,
    compra_data: CompraUpdate,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Actualiza una compra existente.
    """
    repo = CompraRepository(db)
    compra = repo.get_by_id(id_compra)

    if not compra:
        raise HTTPException(status_code=404, detail="Compra no encontrada")

    update_data = compra_data.model_dump(exclude_unset=True)
    updated_compra = repo.update(id_compra, update_data)

    if not updated_compra:
        raise HTTPException(status_code=400, detail="Error al actualizar compra")

    return updated_compra


@router.delete("/{id_compra}", response_model=MessageResponse)
async def eliminar_compra(
    id_compra: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Elimina una compra.
    """
    repo = CompraRepository(db)
    compra = repo.get_by_id(id_compra)

    if not compra:
        raise HTTPException(status_code=404, detail="Compra no encontrada")

    if not repo.delete(id_compra):
        raise HTTPException(status_code=400, detail="Error al eliminar compra")

    return {"message": f"Compra {id_compra} eliminada exitosamente"}


@router.get("/{id_compra}/detalles", response_model=List[DetalleCompraResponse])
async def obtener_detalles_compra(
    id_compra: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene los detalles de una compra.
    """
    compra_repo = CompraRepository(db)
    if not compra_repo.get_by_id(id_compra):
        raise HTTPException(status_code=404, detail="Compra no encontrada")

    detalle_repo = DetalleCompraRepository(db)
    return detalle_repo.get_by_compra(id_compra)


@router.get("/resumen/mensual")
async def resumen_mensual(
    anio: int = Query(..., ge=2000, le=2100),
    mes: int = Query(..., ge=1, le=12),
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene resumen de compras de un mes.

    - **anio**: Ano del resumen
    - **mes**: Mes del resumen (1-12)
    """
    repo = CompraRepository(db)
    resumen = repo.get_resumen_mensual(anio, mes)

    return {
        "anio": anio,
        "mes": mes,
        "cantidad_compras": resumen['cantidad'],
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
    Obtiene el total de compras en un periodo.
    """
    repo = CompraRepository(db)
    total = repo.get_total_por_periodo(fecha_inicio, fecha_fin)

    return {
        "fecha_inicio": fecha_inicio.isoformat(),
        "fecha_fin": fecha_fin.isoformat(),
        "total": float(total)
    }


@router.get("/producto/{id_producto}/costo-promedio")
async def costo_promedio_producto(
    id_producto: int,
    fecha_inicio: date = Query(...),
    fecha_fin: date = Query(...),
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene el costo promedio de un producto en un periodo.
    """
    detalle_repo = DetalleCompraRepository(db)
    costo = detalle_repo.get_costo_promedio_producto(id_producto, fecha_inicio, fecha_fin)

    return {
        "id_producto": id_producto,
        "fecha_inicio": fecha_inicio.isoformat(),
        "fecha_fin": fecha_fin.isoformat(),
        "costo_promedio": float(costo)
    }
