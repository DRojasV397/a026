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
from app.middleware.auth_middleware import get_current_user
from app.schemas.auth import TokenData


# Routers
router = APIRouter(prefix="/productos", tags=["Productos"])
categoria_router = APIRouter(prefix="/categorias", tags=["Categorías"])


# Endpoints de Productos
@router.post("/", response_model=ProductoResponse, status_code=status.HTTP_201_CREATED)
def create_producto(
    producto_data: ProductoCreate,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Crea un nuevo producto en el catálogo del usuario autenticado."""
    try:
        service = ProductoService(db)
        producto = service.create_producto(producto_data, current_user.idUsuario)
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
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene los productos del usuario autenticado con filtros opcionales.

    - **skip**: Número de registros a saltar (paginación)
    - **limit**: Número máximo de registros a retornar
    - **activos_only**: Si es True, solo retorna productos activos
    - **categoria_id**: Filtra por categoría específica
    """
    service = ProductoService(db)
    productos = service.get_productos(
        user_id=current_user.idUsuario,
        skip=skip,
        limit=limit,
        activos_only=activos_only,
        categoria_id=categoria_id
    )
    result = []
    for p in productos:
        try:
            cat_nombre = p.categoria.nombre if p.categoria else None
        except Exception:
            cat_nombre = None
        result.append({
            "idProducto":      p.idProducto,
            "sku":             p.sku or "",
            "nombre":          p.nombre or "",
            "descripcion":     None,
            "idCategoria":     p.idCategoria,
            "precioUnitario":  float(p.precioUnitario) if p.precioUnitario is not None else None,
            "costoUnitario":   float(p.costoUnitario)  if p.costoUnitario  is not None else None,
            "costo":           float(p.costoUnitario)  if p.costoUnitario  is not None else None,
            "existencia":      0,
            "activo":          p.activo if p.activo is not None else 1,
            "categoriaNombre": cat_nombre,
        })
    return result


@router.get("/{producto_id}", response_model=ProductoResponse)
def get_producto(
    producto_id: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Obtiene un producto por ID (solo si pertenece al usuario autenticado)."""
    service = ProductoService(db)
    producto = service.get_producto(producto_id, current_user.idUsuario)
    if not producto:
        raise HTTPException(status_code=404, detail="Producto no encontrado")
    return producto


@router.put("/{producto_id}", response_model=ProductoResponse)
def update_producto(
    producto_id: int,
    producto_data: ProductoUpdate,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Actualiza un producto existente del usuario autenticado."""
    service = ProductoService(db)
    producto = service.update_producto(producto_id, producto_data, current_user.idUsuario)
    if not producto:
        raise HTTPException(status_code=404, detail="Producto no encontrado")
    return producto


@router.delete("/{producto_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_producto(
    producto_id: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Elimina un producto del catálogo del usuario autenticado."""
    service = ProductoService(db)
    if not service.delete_producto(producto_id, current_user.idUsuario):
        raise HTTPException(status_code=404, detail="Producto no encontrado")


# Endpoints de Categorías (compartidas, sin filtro de usuario)
@categoria_router.post("/", response_model=CategoriaResponse, status_code=status.HTTP_201_CREATED)
def create_categoria(
    categoria_data: CategoriaCreate,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
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
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Obtiene todas las categorías con paginación."""
    service = CategoriaService(db)
    return service.get_categorias(skip=skip, limit=limit, activas_only=activas_only)


@categoria_router.get("/{categoria_id}", response_model=CategoriaResponse)
def get_categoria(
    categoria_id: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Obtiene una categoría por ID."""
    service = CategoriaService(db)
    categoria = service.get_categoria(categoria_id)
    if not categoria:
        raise HTTPException(status_code=404, detail="Categoría no encontrada")
    return categoria


@categoria_router.put("/{categoria_id}", response_model=CategoriaResponse)
def update_categoria(
    categoria_id: int,
    categoria_data: CategoriaUpdate,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Actualiza una categoría existente."""
    service = CategoriaService(db)
    categoria = service.update_categoria(categoria_id, categoria_data)
    if not categoria:
        raise HTTPException(status_code=404, detail="Categoría no encontrada")
    return categoria


@categoria_router.delete("/{categoria_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_categoria(
    categoria_id: int,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """Elimina una categoría."""
    service = CategoriaService(db)
    if not service.delete_categoria(categoria_id):
        raise HTTPException(status_code=404, detail="Categoría no encontrada")
