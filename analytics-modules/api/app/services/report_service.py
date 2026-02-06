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
    Producto, Categoria, Reporte, Usuario
)
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

        # Calcular por mes
        datos_mensuales = self._calcular_rentabilidad_mensual(ventas, compras)

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
