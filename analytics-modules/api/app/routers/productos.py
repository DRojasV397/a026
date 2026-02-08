"""
Endpoints de la API para Productos y Categorías.
"""

from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.orm import Session
from typing import List, Optional

from app.database import get_db
from app.schemas import (
    ProductoCreate, ProductoUpdate, ProductoResponse,
    CategoriaCreate, CategoriaUpdate, CategoriaResponse
)
from app.services import ProductoService, CategoriaService


# Routers
router = APIRouter(prefix="/productos", tags=["Productos"])
categoria_router = APIRouter(prefix="/categorias", tags=["Categorías"])


# Endpoints de Productos
@router.post("/", response_model=ProductoResponse, status_code=status.HTTP_201_CREATED)
def create_producto(producto_data: ProductoCreate, db: Session = Depends(get_db)):
    """Crea un nuevo producto."""
    try:
        service = ProductoService(db)
        producto = service.create_producto(producto_data)
        if not producto:
            raise HTTPException(status_code=400, detail="Error al crear producto")
        return producto
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/", response_model=List[ProductoResponse])
def get_productos(
    skip: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=1000),
    activos_only: bool = False,
    categoria_id: Optional[int] = None,
    search: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """
    Obtiene productos con filtros opcionales.

    - **skip**: Número de registros a saltar (paginación)
    - **limit**: Número máximo de registros a retornar
    - **activos_only**: Si es True, solo retorna productos activos
    - **categoria_id**: Filtra por categoría específica
    - **search**: Búsqueda de texto en nombre/descripción
    """
    service = ProductoService(db)
    return service.get_productos(
        skip=skip,
        limit=limit,
        activos_only=activos_only,
        categoria_id=categoria_id
    )


@router.get("/{producto_id}", response_model=ProductoResponse)
def get_producto(producto_id: int, db: Session = Depends(get_db)):
    """Obtiene un producto por ID."""
    service = ProductoService(db)
    producto = service.get_producto(producto_id)
    if not producto:
        raise HTTPException(status_code=404, detail="Producto no encontrado")
    return producto


@router.put("/{producto_id}", response_model=ProductoResponse)
def update_producto(producto_id: int, producto_data: ProductoUpdate, db: Session = Depends(get_db)):
    """Actualiza un producto existente."""
    service = ProductoService(db)
    producto = service.update_producto(producto_id, producto_data)
    if not producto:
        raise HTTPException(status_code=404, detail="Producto no encontrado")
    return producto


@router.delete("/{producto_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_producto(producto_id: int, db: Session = Depends(get_db)):
    """Elimina un producto."""
    service = ProductoService(db)
    if not service.delete_producto(producto_id):
        raise HTTPException(status_code=404, detail="Producto no encontrado")


# Endpoints de Categorías
@categoria_router.post("/", response_model=CategoriaResponse, status_code=status.HTTP_201_CREATED)
def create_categoria(categoria_data: CategoriaCreate, db: Session = Depends(get_db)):
    """Crea una nueva categoría."""
    try:
        service = CategoriaService(db)
        categoria = service.create_categoria(categoria_data)
        if not categoria:
            raise HTTPException(status_code=400, detail="Error al crear categoría")
        return categoria
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@categoria_router.get("/", response_model=List[CategoriaResponse])
def get_categorias(
    skip: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=1000),
    activas_only: bool = False,
    db: Session = Depends(get_db)
):
    """Obtiene todas las categorías con paginación."""
    service = CategoriaService(db)
    return service.get_categorias(skip=skip, limit=limit, activas_only=activas_only)


@categoria_router.get("/{categoria_id}", response_model=CategoriaResponse)
def get_categoria(categoria_id: int, db: Session = Depends(get_db)):
    """Obtiene una categoría por ID."""
    service = CategoriaService(db)
    categoria = service.get_categoria(categoria_id)
    if not categoria:
        raise HTTPException(status_code=404, detail="Categoría no encontrada")
    return categoria


@categoria_router.put("/{categoria_id}", response_model=CategoriaResponse)
def update_categoria(categoria_id: int, categoria_data: CategoriaUpdate, db: Session = Depends(get_db)):
    """Actualiza una categoría existente."""
    service = CategoriaService(db)
    categoria = service.update_categoria(categoria_id, categoria_data)
    if not categoria:
        raise HTTPException(status_code=404, detail="Categoría no encontrada")
    return categoria


@categoria_router.delete("/{categoria_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_categoria(categoria_id: int, db: Session = Depends(get_db)):
    """Elimina una categoría."""
    service = CategoriaService(db)
    if not service.delete_categoria(categoria_id):
        raise HTTPException(status_code=404, detail="Categoría no encontrada")
