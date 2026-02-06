"""
Router para carga y procesamiento de datos.
Endpoints para RF-01: Gestion de Datos de Ventas y Compras.
"""

from fastapi import APIRouter, Depends, File, UploadFile, HTTPException, Query
from sqlalchemy.orm import Session
from typing import Optional
import logging

from app.database import get_db
from app.services.data_service import DataService
from app.schemas.data_upload import (
    DataType, UploadResponse, ValidateRequest, ValidateResponse,
    PreviewResponse, CleanRequest, CleanResponse,
    ConfirmRequest, ConfirmResponse, QualityReportResponse
)
from app.middleware.auth_middleware import get_current_user
from app.schemas.auth import TokenData

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/data", tags=["Data Management"])


@router.post("/upload", response_model=UploadResponse)
async def upload_file(
    file: UploadFile = File(...),
    sheet_name: Optional[str] = Query(None, description="Nombre de hoja Excel"),
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Carga un archivo CSV o Excel para procesamiento.

    - **file**: Archivo CSV, XLSX o XLS
    - **sheet_name**: Nombre de la hoja (solo para Excel)

    Returns:
        UploadResponse con ID de upload y metadatos
    """
    # Validar tipo de archivo
    allowed_extensions = {'.csv', '.xlsx', '.xls'}
    filename = file.filename.lower()

    if not any(filename.endswith(ext) for ext in allowed_extensions):
        raise HTTPException(
            status_code=400,
            detail=f"Tipo de archivo no soportado. Use: {', '.join(allowed_extensions)}"
        )

    # Validar tamaño (max 10MB)
    max_size = 10 * 1024 * 1024
    content = await file.read()

    if len(content) > max_size:
        raise HTTPException(
            status_code=400,
            detail="El archivo excede el tamaño maximo de 10MB"
        )

    service = DataService(db)
    result = service.upload_file(content, file.filename, sheet_name)

    logger.info(f"Usuario {current_user.nombreUsuario} subio archivo: {file.filename}")

    return result


@router.post("/validate", response_model=ValidateResponse)
async def validate_structure(
    request: ValidateRequest,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Valida la estructura del archivo contra el tipo de datos esperado.

    - **upload_id**: ID del archivo cargado
    - **data_type**: Tipo de datos (ventas, compras, productos, inventario)
    - **column_mappings**: Mapeo opcional de columnas
    """
    service = DataService(db)
    upload = service.get_upload(request.upload_id)

    if not upload:
        raise HTTPException(status_code=404, detail="Upload no encontrado")

    return service.validate_structure(
        request.upload_id,
        request.data_type,
        request.column_mappings
    )


@router.get("/preview/{upload_id}", response_model=PreviewResponse)
async def get_preview(
    upload_id: str,
    rows: int = Query(default=10, ge=1, le=100),
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene una vista previa de los datos cargados.

    - **upload_id**: ID del archivo cargado
    - **rows**: Numero de filas a mostrar (1-100)
    """
    service = DataService(db)
    upload = service.get_upload(upload_id)

    if not upload:
        raise HTTPException(status_code=404, detail="Upload no encontrado")

    return service.get_preview(upload_id, rows)


@router.post("/clean", response_model=CleanResponse)
async def clean_data(
    request: CleanRequest,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Ejecuta limpieza de datos segun opciones especificadas.

    - **upload_id**: ID del archivo cargado
    - **options**: Opciones de limpieza (duplicados, nulos, outliers)

    Regla RN-02.05: El proceso debe mantener al menos 70% de los registros.
    """
    service = DataService(db)
    upload = service.get_upload(request.upload_id)

    if not upload:
        raise HTTPException(status_code=404, detail="Upload no encontrado")

    return service.clean_data(request.upload_id, request.options)


@router.post("/confirm", response_model=ConfirmResponse)
async def confirm_upload(
    request: ConfirmRequest,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Confirma la carga e inserta los datos en la base de datos.

    - **upload_id**: ID del archivo cargado
    - **data_type**: Tipo de datos
    - **column_mappings**: Mapeo de columnas del archivo a campos del sistema
    """
    service = DataService(db)
    upload = service.get_upload(request.upload_id)

    if not upload:
        raise HTTPException(status_code=404, detail="Upload no encontrado")

    result = service.confirm_upload(
        request.upload_id,
        request.data_type,
        request.column_mappings
    )

    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])

    logger.info(
        f"Usuario {current_user.nombreUsuario} confirmo carga: "
        f"{result['records_inserted']} registros"
    )

    return ConfirmResponse(
        upload_id=request.upload_id,
        success=result["success"],
        records_inserted=result["records_inserted"],
        records_updated=result.get("records_updated", 0),
        message=result["message"]
    )


@router.get("/quality-report/{upload_id}", response_model=QualityReportResponse)
async def get_quality_report(
    upload_id: str,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Genera un reporte de calidad de los datos cargados.

    - **upload_id**: ID del archivo cargado

    Returns:
        Reporte con metricas de completitud, unicidad, validez y outliers.
    """
    service = DataService(db)
    upload = service.get_upload(upload_id)

    if not upload:
        raise HTTPException(status_code=404, detail="Upload no encontrado")

    return service.get_quality_report(upload_id)


@router.delete("/{upload_id}")
async def delete_upload(
    upload_id: str,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Elimina un upload temporal.

    - **upload_id**: ID del archivo cargado
    """
    service = DataService(db)

    if not service.delete_upload(upload_id):
        raise HTTPException(status_code=404, detail="Upload no encontrado")

    return {"message": "Upload eliminado exitosamente"}


@router.get("/sheets/{upload_id}")
async def get_excel_sheets(
    upload_id: str,
    db: Session = Depends(get_db),
    current_user: TokenData = Depends(get_current_user)
):
    """
    Obtiene lista de hojas de un archivo Excel cargado.

    - **upload_id**: ID del archivo cargado (debe ser Excel)
    """
    service = DataService(db)
    upload = service.get_upload(upload_id)

    if not upload:
        raise HTTPException(status_code=404, detail="Upload no encontrado")

    # Solo disponible para archivos Excel
    file_type = upload.get("file_type")
    if file_type and file_type.value == "csv":
        return {"sheets": []}

    return {"sheets": ["Sheet1"]}  # Por ahora retorna default
