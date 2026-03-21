"""
Servicio de generacion de reportes.
RF-07: Generacion de reportes en diferentes formatos.
"""

from typing import Optional, Dict, Any, List
from datetime import datetime, date, timedelta
from sqlalchemy.orm import Session
from sqlalchemy import func, desc
from decimal import Decimal
import logging
import json
import io

from app.models import (
    Venta, DetalleVenta, Compra, DetalleCompra,
    Producto, Categoria, Reporte, Usuario,
    Modelo, VersionModelo,
)
from app.models.prediccion import ModeloPack
from app.repositories import (
    VentaRepository, CompraRepository, ProductoRepository
)

logger = logging.getLogger(__name__)


class ReportService:
    """
    Servicio de generacion de reportes.

    RF-07: Reportes
    - Generacion de reportes de ventas
    - Generacion de reportes de compras
    - Generacion de reportes de rentabilidad
    - Exportacion en diferentes formatos
    """

    FORMATOS_SOPORTADOS = ["json", "csv", "excel"]

    def __init__(self, db: Session):
        self.db = db
        self.venta_repo = VentaRepository(db)
        self.compra_repo = CompraRepository(db)
        self.producto_repo = ProductoRepository(db)

    def generate_sales_report(
        self,
        fecha_inicio: date,
        fecha_fin: date,
        formato: str = "json",
        generado_por: Optional[int] = None,
        agrupar_por: str = "dia"
    ) -> Dict[str, Any]:
        """
        Genera reporte de ventas.

        Args:
            fecha_inicio: Fecha inicio del periodo
            fecha_fin: Fecha fin del periodo
            formato: Formato de salida (json, csv, excel)
            generado_por: ID del usuario que genera
            agrupar_por: Agrupacion (dia, semana, mes)

        Returns:
            Dict con el reporte
        """
        ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        # Agrupar datos
        datos_agrupados = self._agrupar_ventas(ventas, agrupar_por)

        # Calcular totales
        total_ventas = sum(float(v.total or 0) for v in ventas)
        num_transacciones = len(ventas)
        ticket_promedio = total_ventas / num_transacciones if num_transacciones > 0 else 0

        reporte_data = {
            "tipo_reporte": "ventas",
            "periodo": {
                "inicio": fecha_inicio.isoformat(),
                "fin": fecha_fin.isoformat()
            },
            "resumen": {
                "total_ventas": round(total_ventas, 2),
                "num_transacciones": num_transacciones,
                "ticket_promedio": round(ticket_promedio, 2)
            },
            "datos": datos_agrupados,
            "generado_en": datetime.now().isoformat()
        }

        # Registrar reporte en BD
        reporte_db = self._registrar_reporte(
            tipo="ventas",
            formato=formato,
            parametros={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
                "agrupar_por": agrupar_por
            },
            generado_por=generado_por
        )

        if formato == "csv":
            csv_content = self._to_csv(datos_agrupados, ["periodo", "total", "cantidad"])
            return {
                "success": True,
                "formato": "csv",
                "id_reporte": reporte_db.idReporte if reporte_db else None,
                "contenido": csv_content,
                "nombre_archivo": f"ventas_{fecha_inicio}_{fecha_fin}.csv"
            }
        elif formato == "excel":
            return {
                "success": True,
                "formato": "excel",
                "id_reporte": reporte_db.idReporte if reporte_db else None,
                "datos": reporte_data,
                "mensaje": "Use la libreria openpyxl en el cliente para generar el Excel"
            }
        else:
            return {
                "success": True,
                "formato": "json",
                "id_reporte": reporte_db.idReporte if reporte_db else None,
                "reporte": reporte_data
            }

    def _agrupar_ventas(self, ventas: List, agrupar_por: str) -> List[Dict]:
        """Agrupa ventas por periodo."""
        agrupado = {}

        for v in ventas:
            if not v.fecha:
                continue

            if agrupar_por == "dia":
                key = v.fecha.strftime("%Y-%m-%d")
            elif agrupar_por == "semana":
                semana = v.fecha.isocalendar()[1]
                key = f"{v.fecha.year}-W{semana:02d}"
            elif agrupar_por == "mes":
                key = v.fecha.strftime("%Y-%m")
            else:
                key = v.fecha.strftime("%Y-%m-%d")

            if key not in agrupado:
                agrupado[key] = {"total": 0, "cantidad": 0}

            agrupado[key]["total"] += float(v.total or 0)
            agrupado[key]["cantidad"] += 1

        return [
            {"periodo": k, "total": round(v["total"], 2), "cantidad": v["cantidad"]}
            for k, v in sorted(agrupado.items())
        ]

    def generate_purchases_report(
        self,
        fecha_inicio: date,
        fecha_fin: date,
        formato: str = "json",
        generado_por: Optional[int] = None,
        agrupar_por: str = "dia"
    ) -> Dict[str, Any]:
        """
        Genera reporte de compras.

        Args:
            fecha_inicio: Fecha inicio del periodo
            fecha_fin: Fecha fin del periodo
            formato: Formato de salida
            generado_por: ID del usuario
            agrupar_por: Agrupacion

        Returns:
            Dict con el reporte
        """
        compras = self.compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        # Agrupar datos
        datos_agrupados = self._agrupar_compras(compras, agrupar_por)

        # Calcular totales
        total_compras = sum(float(c.total or 0) for c in compras)
        num_transacciones = len(compras)
        compra_promedio = total_compras / num_transacciones if num_transacciones > 0 else 0

        reporte_data = {
            "tipo_reporte": "compras",
            "periodo": {
                "inicio": fecha_inicio.isoformat(),
                "fin": fecha_fin.isoformat()
            },
            "resumen": {
                "total_compras": round(total_compras, 2),
                "num_transacciones": num_transacciones,
                "compra_promedio": round(compra_promedio, 2)
            },
            "datos": datos_agrupados,
            "generado_en": datetime.now().isoformat()
        }

        # Registrar reporte
        reporte_db = self._registrar_reporte(
            tipo="compras",
            formato=formato,
            parametros={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
                "agrupar_por": agrupar_por
            },
            generado_por=generado_por
        )

        if formato == "csv":
            csv_content = self._to_csv(datos_agrupados, ["periodo", "total", "cantidad"])
            return {
                "success": True,
                "formato": "csv",
                "id_reporte": reporte_db.idReporte if reporte_db else None,
                "contenido": csv_content,
                "nombre_archivo": f"compras_{fecha_inicio}_{fecha_fin}.csv"
            }
        else:
            return {
                "success": True,
                "formato": "json",
                "id_reporte": reporte_db.idReporte if reporte_db else None,
                "reporte": reporte_data
            }

    def _agrupar_compras(self, compras: List, agrupar_por: str) -> List[Dict]:
        """Agrupa compras por periodo."""
        agrupado = {}

        for c in compras:
            if not c.fecha:
                continue

            if agrupar_por == "dia":
                key = c.fecha.strftime("%Y-%m-%d")
            elif agrupar_por == "semana":
                semana = c.fecha.isocalendar()[1]
                key = f"{c.fecha.year}-W{semana:02d}"
            elif agrupar_por == "mes":
                key = c.fecha.strftime("%Y-%m")
            else:
                key = c.fecha.strftime("%Y-%m-%d")

            if key not in agrupado:
                agrupado[key] = {"total": 0, "cantidad": 0}

            agrupado[key]["total"] += float(c.total or 0)
            agrupado[key]["cantidad"] += 1

        return [
            {"periodo": k, "total": round(v["total"], 2), "cantidad": v["cantidad"]}
            for k, v in sorted(agrupado.items())
        ]

    def generate_profitability_report(
        self,
        fecha_inicio: date,
        fecha_fin: date,
        formato: str = "json",
        generado_por: Optional[int] = None
    ) -> Dict[str, Any]:
        """
        Genera reporte de rentabilidad.

        Args:
            fecha_inicio: Fecha inicio
            fecha_fin: Fecha fin
            formato: Formato de salida
            generado_por: ID del usuario

        Returns:
            Dict con el reporte
        """
        ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
        compras = self.compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        # Calcular metricas
        ingresos = sum(float(v.total or 0) for v in ventas)
        costos = sum(float(c.total or 0) for c in compras)
        utilidad_bruta = ingresos - costos
        margen_bruto = (utilidad_bruta / ingresos * 100) if ingresos > 0 else 0
        roi = (utilidad_bruta / costos * 100) if costos > 0 else 0

        # Calcular desglose
        datos_mensuales = self._calcular_rentabilidad_mensual(ventas, compras)
        por_categoria   = self._calcular_rentabilidad_por_categoria(fecha_inicio, fecha_fin)
        por_producto    = self._calcular_rentabilidad_por_producto(fecha_inicio, fecha_fin)

        reporte_data = {
            "tipo_reporte": "rentabilidad",
            "periodo": {
                "inicio": fecha_inicio.isoformat(),
                "fin": fecha_fin.isoformat()
            },
            "resumen": {
                "ingresos_totales": round(ingresos, 2),
                "costos_totales": round(costos, 2),
                "utilidad_bruta": round(utilidad_bruta, 2),
                "margen_bruto_porcentaje": round(margen_bruto, 2),
                "roi_porcentaje": round(roi, 2)
            },
            "datos_mensuales": datos_mensuales,
            "por_categoria": por_categoria,
            "por_producto": por_producto,
            "generado_en": datetime.now().isoformat()
        }

        # Registrar reporte
        reporte_db = self._registrar_reporte(
            tipo="rentabilidad",
            formato=formato,
            parametros={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            },
            generado_por=generado_por
        )

        if formato == "csv":
            csv_content = self._to_csv(
                datos_mensuales,
                ["periodo", "ingresos", "costos", "utilidad", "margen"]
            )
            return {
                "success": True,
                "formato": "csv",
                "id_reporte": reporte_db.idReporte if reporte_db else None,
                "contenido": csv_content,
                "nombre_archivo": f"rentabilidad_{fecha_inicio}_{fecha_fin}.csv"
            }
        else:
            return {
                "success": True,
                "formato": "json",
                "id_reporte": reporte_db.idReporte if reporte_db else None,
                "reporte": reporte_data
            }

    def _calcular_rentabilidad_mensual(self, ventas: List, compras: List) -> List[Dict]:
        """Calcula rentabilidad mensual."""
        # Agrupar ventas por mes
        ventas_mes = {}
        for v in ventas:
            if v.fecha:
                key = v.fecha.strftime("%Y-%m")
                ventas_mes[key] = ventas_mes.get(key, 0) + float(v.total or 0)

        # Agrupar compras por mes
        compras_mes = {}
        for c in compras:
            if c.fecha:
                key = c.fecha.strftime("%Y-%m")
                compras_mes[key] = compras_mes.get(key, 0) + float(c.total or 0)

        # Combinar
        meses = sorted(set(list(ventas_mes.keys()) + list(compras_mes.keys())))

        datos = []
        for mes in meses:
            ingresos = ventas_mes.get(mes, 0)
            costos = compras_mes.get(mes, 0)
            utilidad = ingresos - costos
            margen = (utilidad / ingresos * 100) if ingresos > 0 else 0

            datos.append({
                "periodo": mes,
                "ingresos": round(ingresos, 2),
                "costos": round(costos, 2),
                "utilidad": round(utilidad, 2),
                "margen": round(margen, 2)
            })

        return datos

    def _calcular_rentabilidad_por_categoria(
        self, fecha_inicio: date, fecha_fin: date
    ) -> List[Dict]:
        """
        Rentabilidad agrupada por categoría.
        Ingresos = ventas (precio venta × cantidad).
        Costos   = COGS (costo unitario × cantidad vendida).
        """
        try:
            rows = (
                self.db.query(
                    Categoria.nombre.label("cat_nombre"),
                    func.sum(
                        DetalleVenta.cantidad * DetalleVenta.precioUnitario
                    ).label("ingresos"),
                    func.sum(
                        DetalleVenta.cantidad
                        * func.coalesce(Producto.costoUnitario, 0)
                    ).label("costos"),
                )
                .join(Venta, DetalleVenta.idVenta == Venta.idVenta)
                .join(Producto, DetalleVenta.idProducto == Producto.idProducto)
                .join(Categoria, Producto.idCategoria == Categoria.idCategoria)
                .filter(Venta.fecha >= fecha_inicio, Venta.fecha <= fecha_fin)
                .group_by(Categoria.nombre)
                .order_by(func.sum(DetalleVenta.cantidad * DetalleVenta.precioUnitario).desc())
                .all()
            )
        except Exception as e:
            logger.error(f"Error en rentabilidad por categoría: {e}")
            return []

        datos = []
        for r in rows:
            ing = round(float(r.ingresos or 0), 2)
            cos = round(float(r.costos  or 0), 2)
            uti = round(ing - cos, 2)
            mar = round((uti / ing * 100) if ing > 0 else 0, 2)
            datos.append({
                "categoria": r.cat_nombre or "Sin categoría",
                "ingresos":  ing,
                "costos":    cos,
                "utilidad":  uti,
                "margen":    mar,
            })
        return datos

    def _calcular_rentabilidad_por_producto(
        self, fecha_inicio: date, fecha_fin: date, top_n: int = 50
    ) -> List[Dict]:
        """
        Rentabilidad agrupada por producto (top_n por ingresos).
        Ingresos = precio venta × cantidad.
        Costos   = costo unitario × cantidad vendida.
        """
        try:
            rows = (
                self.db.query(
                    Producto.nombre.label("prod_nombre"),
                    Categoria.nombre.label("cat_nombre"),
                    func.sum(
                        DetalleVenta.cantidad * DetalleVenta.precioUnitario
                    ).label("ingresos"),
                    func.sum(
                        DetalleVenta.cantidad
                        * func.coalesce(Producto.costoUnitario, 0)
                    ).label("costos"),
                )
                .join(Venta, DetalleVenta.idVenta == Venta.idVenta)
                .join(Producto, DetalleVenta.idProducto == Producto.idProducto)
                .outerjoin(Categoria, Producto.idCategoria == Categoria.idCategoria)
                .filter(Venta.fecha >= fecha_inicio, Venta.fecha <= fecha_fin)
                .group_by(Producto.idProducto, Producto.nombre, Categoria.nombre)
                .order_by(func.sum(DetalleVenta.cantidad * DetalleVenta.precioUnitario).desc())
                .limit(top_n)
                .all()
            )
        except Exception as e:
            logger.error(f"Error en rentabilidad por producto: {e}")
            return []

        datos = []
        for r in rows:
            ing = round(float(r.ingresos or 0), 2)
            cos = round(float(r.costos  or 0), 2)
            uti = round(ing - cos, 2)
            mar = round((uti / ing * 100) if ing > 0 else 0, 2)
            datos.append({
                "nombre":    r.prod_nombre or "Sin nombre",
                "categoria": r.cat_nombre  or "Sin categoría",
                "ingresos":  ing,
                "costos":    cos,
                "utilidad":  uti,
                "margen":    mar,
            })
        return datos

    def generate_products_report(
        self,
        fecha_inicio: date,
        fecha_fin: date,
        formato: str = "json",
        generado_por: Optional[int] = None,
        top_n: int = 20
    ) -> Dict[str, Any]:
        """
        Genera reporte de productos.

        Args:
            fecha_inicio: Fecha inicio
            fecha_fin: Fecha fin
            formato: Formato de salida
            generado_por: ID del usuario
            top_n: Numero de productos top

        Returns:
            Dict con el reporte
        """
        # Top productos vendidos
        # Calculamos subtotal como cantidad * precioUnitario
        top_vendidos = self.db.query(
            DetalleVenta.idProducto,
            func.sum(DetalleVenta.cantidad).label('cantidad_vendida'),
            func.sum(DetalleVenta.cantidad * DetalleVenta.precioUnitario).label('ingresos')
        ).join(
            Venta, DetalleVenta.idVenta == Venta.idVenta
        ).filter(
            Venta.fecha >= fecha_inicio,
            Venta.fecha <= fecha_fin
        ).group_by(
            DetalleVenta.idProducto
        ).order_by(
            desc('cantidad_vendida')
        ).limit(top_n).all()

        productos_data = []
        for item in top_vendidos:
            producto = self.producto_repo.get_by_id(item.idProducto)
            if producto:
                productos_data.append({
                    "id_producto": item.idProducto,
                    "nombre": producto.nombre,
                    "categoria": producto.categoria.nombre if producto.categoria else None,
                    "cantidad_vendida": int(item.cantidad_vendida or 0),
                    "ingresos_generados": round(float(item.ingresos or 0), 2)
                })

        reporte_data = {
            "tipo_reporte": "productos",
            "periodo": {
                "inicio": fecha_inicio.isoformat(),
                "fin": fecha_fin.isoformat()
            },
            "resumen": {
                "total_productos_analizados": len(productos_data),
                "top_n": top_n
            },
            "productos": productos_data,
            "generado_en": datetime.now().isoformat()
        }

        # Registrar reporte
        reporte_db = self._registrar_reporte(
            tipo="productos",
            formato=formato,
            parametros={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
                "top_n": top_n
            },
            generado_por=generado_por
        )

        if formato == "csv":
            csv_content = self._to_csv(
                productos_data,
                ["id_producto", "nombre", "categoria", "cantidad_vendida", "ingresos_generados"]
            )
            return {
                "success": True,
                "formato": "csv",
                "id_reporte": reporte_db.idReporte if reporte_db else None,
                "contenido": csv_content,
                "nombre_archivo": f"productos_{fecha_inicio}_{fecha_fin}.csv"
            }
        else:
            return {
                "success": True,
                "formato": "json",
                "id_reporte": reporte_db.idReporte if reporte_db else None,
                "reporte": reporte_data
            }

    def _registrar_reporte(
        self,
        tipo: str,
        formato: str,
        parametros: Dict,
        generado_por: Optional[int]
    ) -> Optional[Reporte]:
        """Registra un reporte en la BD."""
        try:
            # Extraer periodo de los parametros
            periodo = f"{parametros.get('fecha_inicio', '')} a {parametros.get('fecha_fin', '')}"

            reporte = Reporte(
                tipo=tipo,
                formato=formato,
                periodo=periodo,
                generadoPor=generado_por,
                generadoEn=datetime.now()
            )
            self.db.add(reporte)
            self.db.commit()
            self.db.refresh(reporte)
            return reporte
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al registrar reporte: {str(e)}")
            return None

    def _to_csv(self, datos: List[Dict], columnas: List[str]) -> str:
        """Convierte datos a formato CSV."""
        if not datos:
            return ",".join(columnas) + "\n"

        lines = [",".join(columnas)]
        for row in datos:
            values = [str(row.get(col, "")) for col in columnas]
            lines.append(",".join(values))

        return "\n".join(lines)

    def list_reports(
        self,
        usuario_id: Optional[int] = None,
        tipo: Optional[str] = None,
        limit: int = 50
    ) -> Dict[str, Any]:
        """
        Lista reportes generados.

        Args:
            usuario_id: Filtrar por usuario
            tipo: Filtrar por tipo
            limit: Limite de resultados

        Returns:
            Dict con lista de reportes
        """
        try:
            query = self.db.query(Reporte)

            if usuario_id:
                query = query.filter(Reporte.generadoPor == usuario_id)
            if tipo:
                query = query.filter(Reporte.tipo == tipo)

            reportes = query.order_by(desc(Reporte.generadoEn)).limit(limit).all()

            return {
                "success": True,
                "total": len(reportes),
                "reportes": [
                    {
                        "id_reporte": r.idReporte,
                        "tipo": r.tipo,
                        "formato": r.formato,
                        "periodo": r.periodo,
                        "generado_por": r.generadoPor,
                        "fecha_generacion": r.generadoEn.isoformat() if r.generadoEn else None
                    } for r in reportes
                ]
            }
        except Exception as e:
            logger.error(f"Error al listar reportes: {str(e)}")
            return {"success": False, "error": str(e)}

    def get_report(self, id_reporte: int) -> Dict[str, Any]:
        """
        Obtiene un reporte por ID.

        Args:
            id_reporte: ID del reporte

        Returns:
            Dict con el reporte
        """
        try:
            reporte = self.db.query(Reporte).filter(
                Reporte.idReporte == id_reporte
            ).first()

            if not reporte:
                return {"success": False, "error": "Reporte no encontrado"}

            return {
                "success": True,
                "reporte": {
                    "id_reporte": reporte.idReporte,
                    "tipo": reporte.tipo,
                    "formato": reporte.formato,
                    "periodo": reporte.periodo,
                    "generado_por": reporte.generadoPor,
                    "fecha_generacion": reporte.generadoEn.isoformat() if reporte.generadoEn else None
                }
            }
        except Exception as e:
            logger.error(f"Error al obtener reporte: {str(e)}")
            return {"success": False, "error": str(e)}

    def generate_predictions_report(
        self,
        fecha_inicio: date,
        fecha_fin: date,
        formato: str = "json",
        generado_por: Optional[int] = None,
        user_id: Optional[int] = None
    ) -> Dict[str, Any]:
        """
        Genera un reporte de modelos predictivos y packs del usuario.

        Incluye:
        - Lista de modelos individuales entrenados (VersionModelo + Modelo)
        - Lista de packs activos con precisión de ventas y compras
        - Comparación de real vs predicho para el periodo solicitado (ventas)
        """
        try:
            # ── Modelos individuales ──────────────────────────────────────────
            query = self.db.query(
                VersionModelo.idVersion,
                VersionModelo.precision,
                VersionModelo.estado,
                VersionModelo.fechaEntrenamiento,
                Modelo.nombre.label('nombre'),
                Modelo.tipoModelo.label('tipoModelo'),
                Modelo.modelKey.label('modelKey'),
            ).join(
                Modelo, VersionModelo.idModelo == Modelo.idModelo
            ).filter(
                VersionModelo.estado == 'Activo',
                ~Modelo.modelKey.like('pack_%')
            )

            if user_id is not None:
                query = query.filter(Modelo.creadoPor == user_id)

            modelos_rows = query.order_by(desc(VersionModelo.idVersion)).all()

            modelos_data = [{
                "id_version":          row.idVersion,
                "nombre":              row.nombre or row.tipoModelo or "Modelo",
                "tipo":                row.tipoModelo or "",
                "model_key":           row.modelKey or "",
                "precision":           round(float(row.precision or 0), 4),
                "estado":              row.estado or "",
                "fecha_entrenamiento": row.fechaEntrenamiento.isoformat() if row.fechaEntrenamiento else None,
            } for row in modelos_rows]

            # ── Packs ─────────────────────────────────────────────────────────
            packs_query = self.db.query(ModeloPack).filter(ModeloPack.estado == 'Activo')
            if user_id is not None:
                packs_query = packs_query.filter(ModeloPack.creadoPor == user_id)
            packs = packs_query.order_by(desc(ModeloPack.creadoEn)).all()

            packs_data = []
            for p in packs:
                v = p.version_ventas
                c = p.version_compras
                packs_data.append({
                    "pack_id":           p.idPack,
                    "pack_key":          p.packKey,
                    "nombre":            p.nombre or p.packKey,
                    "creado_en":         p.creadoEn.isoformat() if p.creadoEn else None,
                    "ventas_precision":  round(float(v.precision or 0), 4) if v and v.precision is not None else None,
                    "compras_precision": round(float(c.precision or 0), 4) if c and c.precision is not None else None,
                    "estado":            p.estado,
                })

            # ── Ventas reales del periodo (para contexto) ─────────────────────
            ventas_reales = self.db.query(
                func.sum(Venta.total).label('total'),
                func.count(Venta.idVenta).label('transacciones')
            ).filter(
                Venta.fecha >= fecha_inicio,
                Venta.fecha <= fecha_fin
            ).first()

            total_ventas_real = float(ventas_reales.total or 0) if ventas_reales else 0
            num_transacciones = int(ventas_reales.transacciones or 0) if ventas_reales else 0

            # ── Compras reales del periodo ────────────────────────────────────
            compras_reales = self.db.query(
                func.sum(Compra.total).label('total')
            ).filter(
                Compra.fecha >= fecha_inicio,
                Compra.fecha <= fecha_fin
            ).scalar()
            total_compras_real = float(compras_reales or 0)

            reporte_data = {
                "tipo_reporte": "predicciones",
                "periodo": {
                    "inicio": fecha_inicio.isoformat(),
                    "fin":    fecha_fin.isoformat()
                },
                "resumen": {
                    "total_modelos":     len(modelos_data),
                    "total_packs":       len(packs_data),
                    "ventas_reales":     round(total_ventas_real, 2),
                    "compras_reales":    round(total_compras_real, 2),
                    "num_transacciones": num_transacciones,
                },
                "modelos":    modelos_data,
                "packs":      packs_data,
                "generado_en": datetime.now().isoformat(),
            }

            reporte_db = self._registrar_reporte(
                tipo="predicciones",
                formato=formato,
                parametros={
                    "fecha_inicio": fecha_inicio.isoformat(),
                    "fecha_fin":    fecha_fin.isoformat(),
                },
                generado_por=generado_por
            )

            if formato == "csv":
                columnas_modelos = ["nombre", "tipo", "precision", "estado", "fecha_entrenamiento"]
                csv_content = self._to_csv(modelos_data, columnas_modelos)
                return {
                    "success": True,
                    "formato": "csv",
                    "id_reporte": reporte_db.idReporte if reporte_db else None,
                    "contenido": csv_content,
                    "nombre_archivo": f"predicciones_{fecha_inicio}_{fecha_fin}.csv"
                }
            else:
                return {
                    "success": True,
                    "formato": "json",
                    "id_reporte": reporte_db.idReporte if reporte_db else None,
                    "reporte": reporte_data
                }

        except Exception as e:
            logger.error(f"Error al generar reporte de predicciones: {str(e)}")
            return {"success": False, "error": str(e)}
