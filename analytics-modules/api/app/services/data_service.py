"""
Servicio de carga y procesamiento de datos.
Maneja la carga de archivos CSV/Excel y su procesamiento.
"""

import pandas as pd
import numpy as np
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime
from sqlalchemy.orm import Session
import uuid
import logging

from app.utils.file_parser import FileParser, ParseResult
from app.utils.exceptions import FileParseError, ValidationError
from app.schemas.data_upload import (
    DataType, UploadStatus, UploadResponse,
    ValidateResponse, ColumnValidation, PreviewResponse,
    CleaningOptions, CleaningResult, CleanResponse,
    QualityReportResponse, QualityMetric,
    REQUIRED_COLUMNS, OPTIONAL_COLUMNS
)
from app.models import Venta, DetalleVenta, Compra, DetalleCompra, Producto, Categoria, HistorialCarga
from app.repositories import VentaRepository, CompraRepository, ProductoRepository, CategoriaRepository, DetalleVentaRepository, DetalleCompraRepository

logger = logging.getLogger(__name__)

# Almacenamiento compartido a nivel de modulo (en produccion usar Redis/cache)
_shared_uploads: Dict[str, Dict[str, Any]] = {}


class DataService:
    """Servicio de gestion de datos."""

    def __init__(self, db: Session):
        self.db = db
        self.parser = FileParser()
        # Usar almacenamiento compartido
        self._uploads = _shared_uploads

    def upload_file(
        self,
        file_content: bytes,
        filename: str,
        user_id: int,
        sheet_name: Optional[str] = None
    ) -> UploadResponse:
        """
        Carga un archivo y lo almacena temporalmente.

        Args:
            file_content: Contenido del archivo
            filename: Nombre del archivo
            sheet_name: Hoja de Excel (opcional)

        Returns:
            UploadResponse: Respuesta con info del upload
        """
        upload_id = str(uuid.uuid4())

        try:
            result = self.parser.parse_file(file_content, filename, sheet_name)

            if not result.success:
                return UploadResponse(
                    upload_id=upload_id,
                    filename=filename,
                    file_type=result.file_type.value if result.file_type else "unknown",
                    total_rows=0,
                    status=UploadStatus.ERROR,
                    message="; ".join(result.errors),
                    column_info={}
                )

            # Almacenar datos temporalmente
            self._uploads[upload_id] = {
                "filename": filename,
                "data": result.data,
                "column_info": result.column_info,
                "status": UploadStatus.PENDING,
                "created_at": datetime.now(),
                "file_type": result.file_type,
                "user_id": user_id
            }

            logger.info(f"Archivo cargado: {upload_id} - {filename} ({result.total_rows} filas)")

            return UploadResponse(
                upload_id=upload_id,
                filename=filename,
                file_type=result.file_type.value,
                total_rows=result.total_rows,
                status=UploadStatus.PENDING,
                message="Archivo cargado exitosamente",
                column_info=result.column_info
            )

        except FileParseError as e:
            logger.error(f"Error al cargar archivo: {str(e)}")
            return UploadResponse(
                upload_id=upload_id,
                filename=filename,
                file_type="unknown",
                total_rows=0,
                status=UploadStatus.ERROR,
                message=str(e),
                column_info={}
            )

    def get_upload(self, upload_id: str) -> Optional[Dict[str, Any]]:
        """Obtiene un upload por ID."""
        return self._uploads.get(upload_id)

    def validate_structure(
        self,
        upload_id: str,
        data_type: DataType,
        column_mappings: Optional[Dict[str, str]] = None
    ) -> ValidateResponse:
        """
        Valida la estructura del archivo contra el tipo de datos.

        Args:
            upload_id: ID del upload
            data_type: Tipo de datos esperado
            column_mappings: Mapeo personalizado de columnas

        Returns:
            ValidateResponse: Resultado de validacion
        """
        upload = self.get_upload(upload_id)
        if not upload:
            return ValidateResponse(
                upload_id=upload_id,
                valid=False,
                data_type=data_type,
                columns=[],
                errors=["Upload no encontrado"]
            )

        df = upload["data"]
        required = REQUIRED_COLUMNS.get(data_type, [])
        optional = OPTIONAL_COLUMNS.get(data_type, [])

        columns_validation = []
        missing_required = []
        warnings = []

        # Aplicar mapeo si existe
        df_columns = set(df.columns.str.lower())
        if column_mappings:
            for source, target in column_mappings.items():
                if source.lower() in df_columns:
                    df_columns.add(target.lower())

        # Validar columnas requeridas
        for col in required:
            found = col.lower() in df_columns
            if not found:
                missing_required.append(col)

            col_info = upload["column_info"].get(col, {})
            columns_validation.append(ColumnValidation(
                name=col,
                found=found,
                data_type=col_info.get("suggested_type"),
                null_count=col_info.get("null_count", 0),
                null_percentage=col_info.get("null_percentage", 0)
            ))

        # Validar columnas opcionales
        for col in optional:
            found = col.lower() in df_columns
            if not found:
                warnings.append(f"Columna opcional '{col}' no encontrada")

            columns_validation.append(ColumnValidation(
                name=col,
                found=found,
                data_type=upload["column_info"].get(col, {}).get("suggested_type")
            ))

        # Actualizar estado
        is_valid = len(missing_required) == 0
        upload["status"] = UploadStatus.READY if is_valid else UploadStatus.ERROR

        return ValidateResponse(
            upload_id=upload_id,
            valid=is_valid,
            data_type=data_type,
            columns=columns_validation,
            missing_required=missing_required,
            warnings=warnings,
            errors=[f"Columna requerida faltante: {c}" for c in missing_required]
        )

    def get_preview(
        self,
        upload_id: str,
        rows: int = 10
    ) -> PreviewResponse:
        """
        Obtiene una vista previa de los datos.

        Args:
            upload_id: ID del upload
            rows: Numero de filas

        Returns:
            PreviewResponse: Vista previa
        """
        upload = self.get_upload(upload_id)
        if not upload:
            return PreviewResponse(
                upload_id=upload_id,
                total_rows=0,
                preview_rows=0,
                columns=[],
                data=[]
            )

        df = upload["data"]
        preview = self.parser.get_preview(df, rows)

        return PreviewResponse(
            upload_id=upload_id,
            total_rows=len(df),
            preview_rows=len(preview),
            columns=list(df.columns),
            data=preview
        )

    def clean_data(
        self,
        upload_id: str,
        options: CleaningOptions
    ) -> CleanResponse:
        """
        Limpia los datos segun las opciones especificadas.

        Args:
            upload_id: ID del upload
            options: Opciones de limpieza

        Returns:
            CleanResponse: Resultado de limpieza
        """
        upload = self.get_upload(upload_id)
        if not upload:
            return CleanResponse(
                upload_id=upload_id,
                status=UploadStatus.ERROR,
                result=CleaningResult(
                    original_rows=0, cleaned_rows=0, removed_rows=0,
                    duplicates_removed=0, nulls_handled=0, outliers_detected=0,
                    quality_score=0
                ),
                message="Upload no encontrado"
            )

        upload["status"] = UploadStatus.CLEANING
        df = upload["data"].copy()
        original_rows = len(df)
        warnings = []

        duplicates_removed = 0
        nulls_handled = 0
        outliers_detected = 0

        # Eliminar duplicados
        if options.remove_duplicates:
            before = len(df)
            df = df.drop_duplicates()
            duplicates_removed = before - len(df)
            if duplicates_removed > 0:
                warnings.append(f"Se eliminaron {duplicates_removed} filas duplicadas")

        # Manejar nulos
        if options.handle_nulls:
            null_counts = df.isna().sum().sum()
            if options.null_strategy == "drop":
                before = len(df)
                df = df.dropna()
                nulls_handled = before - len(df)
            elif options.null_strategy == "fill_zero":
                df = df.fillna(0)
                nulls_handled = null_counts
            elif options.null_strategy == "fill_mean":
                numeric_cols = df.select_dtypes(include=[np.number]).columns
                df[numeric_cols] = df[numeric_cols].fillna(df[numeric_cols].mean())
                nulls_handled = null_counts
            elif options.null_strategy == "fill_median":
                numeric_cols = df.select_dtypes(include=[np.number]).columns
                df[numeric_cols] = df[numeric_cols].fillna(df[numeric_cols].median())
                nulls_handled = null_counts

        # Detectar outliers usando Z-score
        if options.detect_outliers:
            numeric_cols = df.select_dtypes(include=[np.number]).columns
            for col in numeric_cols:
                if len(df) > 0:
                    z_scores = np.abs((df[col] - df[col].mean()) / df[col].std())
                    outliers = z_scores > options.outlier_threshold
                    outliers_detected += outliers.sum()

            if outliers_detected > 0:
                warnings.append(f"Se detectaron {outliers_detected} valores atipicos")

        # Normalizar texto
        if options.normalize_text:
            text_cols = df.select_dtypes(include=['object']).columns
            for col in text_cols:
                df[col] = df[col].astype(str).str.strip()

        # Calcular calidad
        cleaned_rows = len(df)
        removed_rows = original_rows - cleaned_rows

        # Verificar regla RN-02.05: mantener al menos 70% de registros
        retention_rate = cleaned_rows / original_rows * 100 if original_rows > 0 else 0
        if retention_rate < 70:
            warnings.append(f"Atencion: Solo se retiene {retention_rate:.1f}% de los datos (minimo recomendado: 70%)")

        quality_score = self._calculate_quality_score(df)

        # Actualizar datos
        upload["data"] = df
        upload["status"] = UploadStatus.READY

        result = CleaningResult(
            original_rows=original_rows,
            cleaned_rows=cleaned_rows,
            removed_rows=removed_rows,
            duplicates_removed=duplicates_removed,
            nulls_handled=nulls_handled,
            outliers_detected=outliers_detected,
            quality_score=quality_score,
            warnings=warnings
        )

        logger.info(f"Datos limpiados: {upload_id} - {cleaned_rows}/{original_rows} filas")

        return CleanResponse(
            upload_id=upload_id,
            status=UploadStatus.READY,
            result=result,
            message=f"Limpieza completada. {cleaned_rows} filas validas de {original_rows}"
        )

    def _calculate_quality_score(self, df: pd.DataFrame) -> float:
        """Calcula un puntaje de calidad de los datos."""
        if len(df) == 0:
            return 0.0

        # Completitud: porcentaje de valores no nulos
        completeness = (1 - df.isna().sum().sum() / df.size) * 100

        # Unicidad: porcentaje de filas unicas
        uniqueness = len(df.drop_duplicates()) / len(df) * 100

        # Promedio ponderado
        score = (completeness * 0.6 + uniqueness * 0.4)
        return round(score, 2)

    def get_quality_report(self, upload_id: str) -> QualityReportResponse:
        """
        Genera un reporte de calidad de datos.

        Args:
            upload_id: ID del upload

        Returns:
            QualityReportResponse: Reporte de calidad
        """
        upload = self.get_upload(upload_id)
        if not upload:
            return QualityReportResponse(
                upload_id=upload_id,
                overall_score=0,
                total_rows=0,
                valid_rows=0,
                metrics=[],
                issues=["Upload no encontrado"]
            )

        df = upload["data"]
        metrics = []
        issues = []
        recommendations = []

        for col in df.columns:
            col_data = df[col]

            # Completitud
            completeness = (1 - col_data.isna().sum() / len(df)) * 100

            # Unicidad
            uniqueness = col_data.nunique() / len(df) * 100 if len(df) > 0 else 0

            # Validez (simplificado)
            validity = completeness  # Se puede mejorar con reglas especificas

            # Contar outliers
            outliers = 0
            if pd.api.types.is_numeric_dtype(col_data):
                if col_data.std() > 0:
                    z_scores = np.abs((col_data - col_data.mean()) / col_data.std())
                    outliers = (z_scores > 3).sum()

            metrics.append(QualityMetric(
                column=col,
                completeness=round(completeness, 2),
                uniqueness=round(uniqueness, 2),
                validity=round(validity, 2),
                outliers_count=int(outliers)
            ))

            # Detectar issues
            if completeness < 90:
                issues.append(f"Columna '{col}' tiene {100-completeness:.1f}% de valores nulos")
            if outliers > 0:
                issues.append(f"Columna '{col}' tiene {outliers} valores atipicos")

        overall_score = self._calculate_quality_score(df)

        # Recomendaciones
        if overall_score < 70:
            recommendations.append("Considere revisar la fuente de datos")
        if any(m.completeness < 80 for m in metrics):
            recommendations.append("Algunas columnas tienen muchos valores faltantes")

        return QualityReportResponse(
            upload_id=upload_id,
            overall_score=overall_score,
            total_rows=len(df),
            valid_rows=len(df.dropna()),
            metrics=metrics,
            issues=issues,
            recommendations=recommendations
        )

    def confirm_upload(
        self,
        upload_id: str,
        data_type: DataType,
        column_mappings: Dict[str, str],
        user_id: Optional[int] = None
    ) -> Dict[str, Any]:
        """
        Confirma la carga e inserta los datos en la BD.

        Args:
            upload_id: ID del upload
            data_type: Tipo de datos
            column_mappings: Mapeo de columnas
            user_id: ID del usuario autenticado (fuente autoritativa)

        Returns:
            Dict: Resultado de la insercion
        """
        upload = self.get_upload(upload_id)
        if not upload:
            return {"success": False, "message": "Upload no encontrado"}

        df = upload["data"]
        # Usar user_id del parámetro (fuente autoritativa del router).
        # Si no viene (llamada directa sin autenticación), caer a lo que
        # se guardó durante el upload.
        if user_id is None:
            user_id = upload.get("user_id")
        filename = upload.get("filename")

        # Renombrar columnas segun mapeo
        df = df.rename(columns={v: k for k, v in column_mappings.items()})

        try:
            if data_type == DataType.VENTAS:
                inserted = self._insert_ventas(df, user_id)
                updated = 0
            elif data_type == DataType.COMPRAS:
                inserted = self._insert_compras(df, user_id)
                updated = 0
            elif data_type == DataType.PRODUCTOS:
                inserted, updated = self._insert_productos(df, user_id)
            elif data_type == DataType.INVENTARIO:
                inserted, updated = self._insert_inventario(df, user_id)
            else:
                return {"success": False, "message": f"Tipo de datos no soportado: {data_type.value}"}

            upload["status"] = UploadStatus.CONFIRMED

            # Registrar en historial
            if user_id:
                self._save_historial(upload_id, data_type.value, filename, inserted, updated, user_id)

            logger.info(f"Datos confirmados: {upload_id} - {inserted} insertados, {updated} actualizados")

            msg_parts = []
            if inserted:
                msg_parts.append(f"{inserted} registros nuevos insertados")
            if updated:
                msg_parts.append(f"{updated} registros existentes actualizados")
            message = " y ".join(msg_parts) if msg_parts else "Sin cambios"

            return {
                "success": True,
                "records_inserted": inserted,
                "records_updated": updated,
                "message": message
            }

        except Exception as e:
            logger.error(f"Error al confirmar upload: {str(e)}")
            return {"success": False, "message": str(e)}

    def _insert_ventas(self, df: pd.DataFrame, user_id: Optional[int] = None) -> int:
        """Inserta datos de ventas en la BD, incluyendo DetalleVenta si el CSV lo tiene."""
        repo = VentaRepository(self.db)
        repo_detalle = DetalleVentaRepository(self.db)
        repo_prod = ProductoRepository(self.db)
        inserted = 0

        has_sku_producto = 'sku_producto' in df.columns   # columna "producto_sku" renombrada
        has_producto = 'producto' in df.columns           # columna "producto_nombre" renombrada
        has_cantidad = 'cantidad' in df.columns
        has_precio = 'precio_unitario' in df.columns

        for _, row in df.iterrows():
            try:
                venta = Venta(
                    fecha=pd.to_datetime(row.get('fecha')).date(),
                    total=float(row.get('total', 0)),
                    creadoPor=user_id
                )
                created = repo.create(venta)
                if not created:
                    continue
                inserted += 1

                # Crear DetalleVenta si el CSV incluye columnas de producto
                if (has_sku_producto or has_producto) and has_cantidad and has_precio:
                    sku_val = str(row.get('sku_producto', '')).strip() if has_sku_producto else ''
                    nombre_val = str(row.get('producto', '')).strip() if has_producto else ''
                    cantidad_raw = row.get('cantidad')
                    precio_raw = row.get('precio_unitario')
                    if (sku_val or nombre_val) and cantidad_raw is not None and precio_raw is not None:
                        producto = None
                        # Preferir lookup por SKU (más confiable)
                        if sku_val:
                            producto = repo_prod.get_by_sku(sku_val)
                        # Fallback: buscar por nombre contra el usuario
                        if not producto and nombre_val and user_id:
                            producto = repo_prod.get_by_nombre_y_usuario(nombre_val, user_id)
                        if producto:
                            detalle = DetalleVenta(
                                idVenta=created.idVenta,
                                renglon=1,
                                idProducto=producto.idProducto,
                                cantidad=float(cantidad_raw),
                                precioUnitario=float(precio_raw)
                            )
                            repo_detalle.create(detalle)
                        else:
                            logger.warning(
                                f"Venta {created.idVenta}: producto sku='{sku_val}' nombre='{nombre_val}' "
                                f"no encontrado, DetalleVenta omitido"
                            )
            except Exception as e:
                logger.warning(f"Error al insertar venta: {str(e)}")
                continue

        return inserted

    def _insert_compras(self, df: pd.DataFrame, user_id: Optional[int] = None) -> int:
        """Inserta datos de compras en la BD, incluyendo DetalleCompra si el CSV lo tiene."""
        repo = CompraRepository(self.db)
        repo_detalle = DetalleCompraRepository(self.db)
        repo_prod = ProductoRepository(self.db)
        inserted = 0

        has_sku_producto = 'sku_producto' in df.columns   # columna "producto_sku" renombrada
        has_producto = 'producto' in df.columns           # columna "producto_nombre" renombrada
        has_cantidad = 'cantidad' in df.columns
        has_costo = 'costo' in df.columns

        for _, row in df.iterrows():
            try:
                compra = Compra(
                    fecha=pd.to_datetime(row.get('fecha')).date(),
                    proveedor=str(row.get('proveedor', '')),
                    total=float(row.get('total', 0)),
                    creadoPor=user_id
                )
                created = repo.create(compra)
                if not created:
                    continue
                inserted += 1

                # Crear DetalleCompra si el CSV incluye columnas de producto
                if (has_sku_producto or has_producto) and has_cantidad and has_costo:
                    sku_val = str(row.get('sku_producto', '')).strip() if has_sku_producto else ''
                    nombre_val = str(row.get('producto', '')).strip() if has_producto else ''
                    cantidad_raw = row.get('cantidad')
                    costo_raw = row.get('costo')
                    if (sku_val or nombre_val) and cantidad_raw is not None and costo_raw is not None:
                        producto = None
                        # Preferir lookup por SKU (más confiable)
                        if sku_val:
                            producto = repo_prod.get_by_sku(sku_val)
                        # Fallback: buscar por nombre contra el usuario
                        if not producto and nombre_val and user_id:
                            producto = repo_prod.get_by_nombre_y_usuario(nombre_val, user_id)
                        if producto:
                            cantidad = float(cantidad_raw)
                            costo = float(costo_raw)
                            detalle = DetalleCompra(
                                idCompra=created.idCompra,
                                renglon=1,
                                idProducto=producto.idProducto,
                                cantidad=cantidad,
                                costo=costo,
                                subtotal=round(cantidad * costo, 2)
                            )
                            repo_detalle.create(detalle)
                        else:
                            logger.warning(
                                f"Compra {created.idCompra}: producto sku='{sku_val}' nombre='{nombre_val}' "
                                f"no encontrado, DetalleCompra omitido"
                            )
            except Exception as e:
                logger.warning(f"Error al insertar compra: {str(e)}")
                continue

        return inserted

    def _insert_productos(
        self, df: pd.DataFrame, user_id: Optional[int] = None
    ) -> tuple[int, int]:
        """
        Inserta o actualiza productos en la BD asociados al usuario.

        - Si el SKU ya existe para ese usuario, actualiza precio y costo.
        - Si no existe, lo crea asignando el usuario como propietario.

        Returns:
            Tuple (insertados, actualizados)
        """
        repo = ProductoRepository(self.db)
        cat_repo = CategoriaRepository(self.db)
        categoria_cache: Dict[str, int] = {}
        inserted = 0
        updated = 0

        for _, row in df.iterrows():
            try:
                sku = str(row.get('sku', '')).strip()
                nombre = str(row.get('nombre', '')).strip()
                if not nombre:
                    continue

                # Resolver idCategoria desde nombre de categoría
                cat_nombre = str(row.get('categoria', 'General')).strip() or 'General'
                if cat_nombre not in categoria_cache:
                    categoria = cat_repo.get_by_nombre(cat_nombre)
                    if not categoria:
                        categoria = cat_repo.create(Categoria(nombre=cat_nombre))
                    categoria_cache[cat_nombre] = categoria.idCategoria

                precio_raw = row.get('precio')
                costo_raw = row.get('costo')
                precio = float(precio_raw) if precio_raw is not None else None
                costo = float(costo_raw) if costo_raw is not None else None

                # Buscar producto existente del mismo usuario por SKU
                existing = None
                if sku and user_id:
                    existing = repo.get_by_sku_y_usuario(sku, user_id)

                if existing:
                    # Actualizar precio y costo si vienen en el archivo
                    updates: Dict[str, Any] = {}
                    if precio is not None:
                        updates['precioUnitario'] = precio
                    if costo is not None:
                        updates['costoUnitario'] = costo
                    updates['nombre'] = nombre
                    updates['idCategoria'] = categoria_cache[cat_nombre]
                    repo.update(existing.idProducto, updates)
                    updated += 1
                else:
                    producto = Producto(
                        sku=sku,
                        nombre=nombre,
                        precioUnitario=precio,
                        costoUnitario=costo,
                        idCategoria=categoria_cache[cat_nombre],
                        creadoPor=user_id
                    )
                    created = repo.create(producto)
                    if created:
                        inserted += 1

            except Exception as e:
                logger.warning(f"Error al procesar producto: {str(e)}")
                continue

        return inserted, updated

    def _insert_inventario(
        self, df: pd.DataFrame, user_id: Optional[int] = None
    ) -> tuple[int, int]:
        """
        Actualiza el stock de productos existentes por SKU.

        - Si el SKU existe para el usuario: actualiza stock, stockMinimo,
          stockMaximo y ubicacion.
        - Si el SKU no existe: se omite (no se puede crear un producto
          desde datos de inventario sin nombre ni categoría).

        Returns:
            Tuple (no_encontrados_omitidos, actualizados)
        """
        repo = ProductoRepository(self.db)
        updated = 0
        skipped = 0

        for _, row in df.iterrows():
            try:
                sku = str(row.get('sku', '')).strip()
                if not sku:
                    skipped += 1
                    continue

                # Buscar producto del usuario por SKU (incluye legacy con creadoPor=NULL)
                existing = None
                if user_id:
                    existing = repo.get_by_sku_y_usuario(sku, user_id)
                if not existing:
                    existing = repo.get_by_sku(sku)

                if not existing:
                    logger.warning(f"Inventario: SKU '{sku}' no encontrado, omitiendo")
                    skipped += 1
                    continue

                updates: dict = {}

                cantidad_raw = row.get('cantidad')
                if cantidad_raw is not None and str(cantidad_raw).strip() != '':
                    updates['stock'] = int(float(cantidad_raw))

                minimo_raw = row.get('minimo')
                if minimo_raw is not None and str(minimo_raw).strip() != '':
                    updates['stockMinimo'] = int(float(minimo_raw))

                maximo_raw = row.get('maximo')
                if maximo_raw is not None and str(maximo_raw).strip() != '':
                    updates['stockMaximo'] = int(float(maximo_raw))

                ubicacion_raw = row.get('ubicacion')
                if ubicacion_raw is not None and str(ubicacion_raw).strip() != '':
                    updates['ubicacion'] = str(ubicacion_raw).strip()

                if updates:
                    repo.update(existing.idProducto, updates)
                    updated += 1

            except Exception as e:
                logger.warning(f"Error al actualizar inventario SKU '{row.get('sku')}': {str(e)}")
                continue

        return skipped, updated

    def _save_historial(
        self,
        upload_id: str,
        tipo_datos: str,
        nombre_archivo: Optional[str],
        insertados: int,
        actualizados: int,
        user_id: int
    ) -> None:
        """Guarda un registro en el historial de cargas."""
        try:
            # Usar SAVEPOINT para aislar este INSERT de la transacción principal.
            # Si falla (ej. columna faltante), solo se revierte el historial;
            # los datos reales (ventas/productos/etc.) ya insertados no se pierden.
            with self.db.begin_nested():
                historial = HistorialCarga(
                    uploadId=upload_id,
                    tipoDatos=tipo_datos,
                    nombreArchivo=nombre_archivo,
                    registrosInsertados=insertados,
                    registrosActualizados=actualizados,
                    cargadoPor=user_id,
                    estado='exitoso'
                )
                self.db.add(historial)
        except Exception as e:
            logger.error(f"Error al guardar historial de carga: {str(e)}")

    def get_historial_cargas(
        self,
        user_id: int,
        tipo_datos: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Obtiene el historial de cargas de un usuario.

        Args:
            user_id: ID del usuario
            tipo_datos: Filtrar por tipo (ventas/compras/productos)

        Returns:
            Dict con items y total
        """
        try:
            query = self.db.query(HistorialCarga).filter(
                HistorialCarga.cargadoPor == user_id
            )
            if tipo_datos:
                query = query.filter(HistorialCarga.tipoDatos == tipo_datos)

            items = query.order_by(HistorialCarga.cargadoEn.desc()).all()
            return {"items": items, "total": len(items)}
        except Exception as e:
            logger.error(f"Error al obtener historial de cargas: {str(e)}")
            return {"items": [], "total": 0}

    def delete_upload(self, upload_id: str) -> bool:
        """Elimina un upload temporal."""
        if upload_id in self._uploads:
            del self._uploads[upload_id]
            return True
        return False
