"""
Servicio de Dashboard.
RF-07: Dashboard ejecutivo con KPIs consolidados.
"""

from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime, date, timedelta
from dateutil.relativedelta import relativedelta
from sqlalchemy.orm import Session
from sqlalchemy import func, desc
from decimal import Decimal
import logging

from app.models import (
    Venta, DetalleVenta, Compra, DetalleCompra,
    Producto, Categoria, Alerta, Prediccion,
    Rentabilidad, ResultadoFinanciero, Escenario,
    PreferenciaUsuario
)
from app.repositories import (
    VentaRepository, CompraRepository, ProductoRepository
)

logger = logging.getLogger(__name__)


class DashboardService:
    """
    Servicio de Dashboard ejecutivo.

    RF-07: Dashboard ejecutivo
    - Consolidacion de KPIs principales
    - Visualizacion de tendencias
    - Alertas activas
    - Resumen de predicciones
    """

    def __init__(self, db: Session):
        self.db = db
        self.venta_repo = VentaRepository(db)
        self.compra_repo = CompraRepository(db)
        self.producto_repo = ProductoRepository(db)

    def get_executive_dashboard(
        self,
        fecha_inicio: Optional[date] = None,
        fecha_fin: Optional[date] = None
    ) -> Dict[str, Any]:
        """
        Obtiene el dashboard ejecutivo con KPIs consolidados.

        Args:
            fecha_inicio: Fecha inicio del periodo
            fecha_fin: Fecha fin del periodo

        Returns:
            Dict con datos del dashboard
        """
        # Establecer fechas por defecto (ultimo mes)
        if not fecha_fin:
            fecha_fin = date.today()
        if not fecha_inicio:
            fecha_inicio = fecha_fin - timedelta(days=30)

        # Obtener datos
        resumen_ventas = self._get_sales_summary(fecha_inicio, fecha_fin)
        resumen_compras = self._get_purchases_summary(fecha_inicio, fecha_fin)
        kpis_financieros = self._calculate_financial_kpis(resumen_ventas, resumen_compras)
        alertas_activas = self._get_active_alerts()
        tendencias = self._get_trends(fecha_inicio, fecha_fin)
        top_productos = self._get_top_products(fecha_inicio, fecha_fin)

        return {
            "success": True,
            "periodo": {
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            },
            "resumen_ventas": resumen_ventas,
            "resumen_compras": resumen_compras,
            "kpis_financieros": kpis_financieros,
            "alertas_activas": alertas_activas,
            "tendencias": tendencias,
            "top_productos": top_productos,
            "fecha_generacion": datetime.now().isoformat()
        }

    def _get_sales_summary(self, fecha_inicio: date, fecha_fin: date) -> Dict[str, Any]:
        """Obtiene resumen de ventas del periodo."""
        try:
            ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

            total_ventas = sum(float(v.total or 0) for v in ventas)
            num_ventas = len(ventas)
            ticket_promedio = total_ventas / num_ventas if num_ventas > 0 else 0

            # Periodo anterior para comparacion
            dias_periodo = (fecha_fin - fecha_inicio).days
            fecha_inicio_ant = fecha_inicio - timedelta(days=dias_periodo)
            fecha_fin_ant = fecha_inicio - timedelta(days=1)

            ventas_ant = self.venta_repo.get_by_rango_fechas(fecha_inicio_ant, fecha_fin_ant)
            total_ventas_ant = sum(float(v.total or 0) for v in ventas_ant)

            variacion = ((total_ventas - total_ventas_ant) / total_ventas_ant * 100) if total_ventas_ant > 0 else 0

            return {
                "total": round(total_ventas, 2),
                "cantidad": num_ventas,
                "ticket_promedio": round(ticket_promedio, 2),
                "variacion_periodo_anterior": round(variacion, 2),
                "tendencia": "alza" if variacion > 0 else "baja" if variacion < 0 else "estable"
            }
        except Exception as e:
            logger.error(f"Error al obtener resumen de ventas: {str(e)}")
            return {
                "total": 0, "cantidad": 0, "ticket_promedio": 0,
                "variacion_periodo_anterior": 0, "tendencia": "sin_datos"
            }

    def _get_purchases_summary(self, fecha_inicio: date, fecha_fin: date) -> Dict[str, Any]:
        """Obtiene resumen de compras del periodo."""
        try:
            compras = self.compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

            total_compras = sum(float(c.total or 0) for c in compras)
            num_compras = len(compras)
            compra_promedio = total_compras / num_compras if num_compras > 0 else 0

            # Periodo anterior
            dias_periodo = (fecha_fin - fecha_inicio).days
            fecha_inicio_ant = fecha_inicio - timedelta(days=dias_periodo)
            fecha_fin_ant = fecha_inicio - timedelta(days=1)

            compras_ant = self.compra_repo.get_by_rango_fechas(fecha_inicio_ant, fecha_fin_ant)
            total_compras_ant = sum(float(c.total or 0) for c in compras_ant)

            variacion = ((total_compras - total_compras_ant) / total_compras_ant * 100) if total_compras_ant > 0 else 0

            return {
                "total": round(total_compras, 2),
                "cantidad": num_compras,
                "compra_promedio": round(compra_promedio, 2),
                "variacion_periodo_anterior": round(variacion, 2),
                "tendencia": "alza" if variacion > 0 else "baja" if variacion < 0 else "estable"
            }
        except Exception as e:
            logger.error(f"Error al obtener resumen de compras: {str(e)}")
            return {
                "total": 0, "cantidad": 0, "compra_promedio": 0,
                "variacion_periodo_anterior": 0, "tendencia": "sin_datos"
            }

    def _calculate_financial_kpis(
        self,
        resumen_ventas: Dict,
        resumen_compras: Dict
    ) -> Dict[str, Any]:
        """Calcula KPIs financieros principales."""
        ingresos = resumen_ventas.get("total", 0)
        costos = resumen_compras.get("total", 0)

        utilidad_bruta = ingresos - costos
        margen_bruto = (utilidad_bruta / ingresos * 100) if ingresos > 0 else 0

        # ROI simplificado
        roi = (utilidad_bruta / costos * 100) if costos > 0 else 0

        return {
            "ingresos_totales": round(ingresos, 2),
            "costos_totales": round(costos, 2),
            "utilidad_bruta": round(utilidad_bruta, 2),
            "margen_bruto_porcentaje": round(margen_bruto, 2),
            "roi_porcentaje": round(roi, 2),
            "estado_financiero": self._get_financial_status(margen_bruto)
        }

    def _get_financial_status(self, margen_bruto: float) -> str:
        """Determina el estado financiero basado en el margen."""
        if margen_bruto >= 30:
            return "excelente"
        elif margen_bruto >= 20:
            return "bueno"
        elif margen_bruto >= 10:
            return "aceptable"
        elif margen_bruto >= 0:
            return "bajo"
        else:
            return "critico"

    def _get_active_alerts(self, limit: int = 10) -> Dict[str, Any]:
        """Obtiene alertas activas."""
        try:
            alertas = self.db.query(Alerta).filter(
                Alerta.estado == 'Activa'
            ).order_by(
                desc(Alerta.creadaEn)
            ).limit(limit).all()

            alertas_por_tipo = {}
            alertas_por_importancia = {"alta": 0, "media": 0, "baja": 0}

            alertas_lista = []
            for a in alertas:
                alertas_lista.append({
                    "id": a.idAlerta,
                    "tipo": a.tipo,
                    "importancia": a.importancia,
                    "metrica": a.metrica,
                    "valor_actual": float(a.valorActual) if a.valorActual else 0,
                    "valor_esperado": float(a.valorEsperado) if a.valorEsperado else None,
                    "creada_en": a.creadaEn.isoformat() if a.creadaEn else None
                })

                # Conteo por tipo
                tipo = a.tipo or "otro"
                alertas_por_tipo[tipo] = alertas_por_tipo.get(tipo, 0) + 1

                # Conteo por importancia
                imp = (a.importancia or "media").lower()
                if imp in alertas_por_importancia:
                    alertas_por_importancia[imp] += 1

            return {
                "total": len(alertas),
                "por_tipo": alertas_por_tipo,
                "por_importancia": alertas_por_importancia,
                "alertas": alertas_lista
            }
        except Exception as e:
            logger.error(f"Error al obtener alertas activas: {str(e)}")
            return {
                "total": 0,
                "por_tipo": {},
                "por_importancia": {"alta": 0, "media": 0, "baja": 0},
                "alertas": []
            }

    def _get_trends(self, fecha_inicio: date, fecha_fin: date) -> Dict[str, Any]:
        """Obtiene tendencias de ventas y compras."""
        try:
            # Agrupar ventas por semana
            ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
            ventas_por_semana = {}

            for v in ventas:
                if v.fecha:
                    semana = v.fecha.isocalendar()[1]
                    anio = v.fecha.year
                    key = f"{anio}-W{semana:02d}"
                    if key not in ventas_por_semana:
                        ventas_por_semana[key] = 0
                    ventas_por_semana[key] += float(v.total or 0)

            # Ordenar por semana
            tendencia_ventas = [
                {"periodo": k, "valor": round(v, 2)}
                for k, v in sorted(ventas_por_semana.items())
            ]

            # Compras por semana
            compras = self.compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
            compras_por_semana = {}

            for c in compras:
                if c.fecha:
                    semana = c.fecha.isocalendar()[1]
                    anio = c.fecha.year
                    key = f"{anio}-W{semana:02d}"
                    if key not in compras_por_semana:
                        compras_por_semana[key] = 0
                    compras_por_semana[key] += float(c.total or 0)

            tendencia_compras = [
                {"periodo": k, "valor": round(v, 2)}
                for k, v in sorted(compras_por_semana.items())
            ]

            return {
                "ventas": tendencia_ventas,
                "compras": tendencia_compras
            }
        except Exception as e:
            logger.error(f"Error al obtener tendencias: {str(e)}")
            return {"ventas": [], "compras": []}

    def _get_top_products(
        self,
        fecha_inicio: date,
        fecha_fin: date,
        limit: int = 10
    ) -> Dict[str, Any]:
        """Obtiene los productos mas vendidos."""
        try:
            # Query para top productos por cantidad vendida
            # Calculamos subtotal como cantidad * precioUnitario
            top_por_cantidad = self.db.query(
                DetalleVenta.idProducto,
                func.sum(DetalleVenta.cantidad).label('total_cantidad'),
                func.sum(DetalleVenta.cantidad * DetalleVenta.precioUnitario).label('total_ingresos')
            ).join(
                Venta, DetalleVenta.idVenta == Venta.idVenta
            ).filter(
                Venta.fecha >= fecha_inicio,
                Venta.fecha <= fecha_fin
            ).group_by(
                DetalleVenta.idProducto
            ).order_by(
                desc('total_cantidad')
            ).limit(limit).all()

            productos_top = []
            for item in top_por_cantidad:
                producto = self.producto_repo.get_by_id(item.idProducto)
                if producto:
                    productos_top.append({
                        "id_producto": item.idProducto,
                        "nombre": producto.nombre,
                        "categoria": producto.categoria.nombre if producto.categoria else None,
                        "cantidad_vendida": int(item.total_cantidad or 0),
                        "ingresos_generados": round(float(item.total_ingresos or 0), 2)
                    })

            return {
                "por_cantidad": productos_top,
                "total_productos_vendidos": len(productos_top)
            }
        except Exception as e:
            logger.error(f"Error al obtener top productos: {str(e)}")
            return {"por_cantidad": [], "total_productos_vendidos": 0}

    def get_kpi_detail(
        self,
        kpi_name: str,
        fecha_inicio: Optional[date] = None,
        fecha_fin: Optional[date] = None
    ) -> Dict[str, Any]:
        """
        Obtiene detalle de un KPI especifico.

        Args:
            kpi_name: Nombre del KPI
            fecha_inicio: Fecha inicio
            fecha_fin: Fecha fin

        Returns:
            Dict con detalle del KPI
        """
        if not fecha_fin:
            fecha_fin = date.today()
        if not fecha_inicio:
            fecha_inicio = fecha_fin - timedelta(days=30)

        kpi_handlers = {
            "ventas": self._detail_ventas,
            "compras": self._detail_compras,
            "margen": self._detail_margen,
            "roi": self._detail_roi,
            "alertas": self._detail_alertas
        }

        handler = kpi_handlers.get(kpi_name.lower())
        if not handler:
            return {
                "success": False,
                "error": f"KPI '{kpi_name}' no reconocido. KPIs disponibles: {list(kpi_handlers.keys())}"
            }

        return handler(fecha_inicio, fecha_fin)

    def _detail_ventas(self, fecha_inicio: date, fecha_fin: date) -> Dict[str, Any]:
        """Detalle de ventas."""
        ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        # Agrupar por dia
        ventas_diarias = {}
        for v in ventas:
            if v.fecha:
                dia = v.fecha.strftime("%Y-%m-%d")
                if dia not in ventas_diarias:
                    ventas_diarias[dia] = {"total": 0, "cantidad": 0}
                ventas_diarias[dia]["total"] += float(v.total or 0)
                ventas_diarias[dia]["cantidad"] += 1

        serie_temporal = [
            {"fecha": k, "total": round(v["total"], 2), "cantidad": v["cantidad"]}
            for k, v in sorted(ventas_diarias.items())
        ]

        total = sum(float(v.total or 0) for v in ventas)
        promedio_diario = total / len(ventas_diarias) if ventas_diarias else 0

        return {
            "success": True,
            "kpi": "ventas",
            "periodo": {"inicio": fecha_inicio.isoformat(), "fin": fecha_fin.isoformat()},
            "resumen": {
                "total": round(total, 2),
                "transacciones": len(ventas),
                "promedio_diario": round(promedio_diario, 2),
                "dias_con_ventas": len(ventas_diarias)
            },
            "serie_temporal": serie_temporal
        }

    def _detail_compras(self, fecha_inicio: date, fecha_fin: date) -> Dict[str, Any]:
        """Detalle de compras."""
        compras = self.compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        compras_diarias = {}
        for c in compras:
            if c.fecha:
                dia = c.fecha.strftime("%Y-%m-%d")
                if dia not in compras_diarias:
                    compras_diarias[dia] = {"total": 0, "cantidad": 0}
                compras_diarias[dia]["total"] += float(c.total or 0)
                compras_diarias[dia]["cantidad"] += 1

        serie_temporal = [
            {"fecha": k, "total": round(v["total"], 2), "cantidad": v["cantidad"]}
            for k, v in sorted(compras_diarias.items())
        ]

        total = sum(float(c.total or 0) for c in compras)
        promedio_diario = total / len(compras_diarias) if compras_diarias else 0

        return {
            "success": True,
            "kpi": "compras",
            "periodo": {"inicio": fecha_inicio.isoformat(), "fin": fecha_fin.isoformat()},
            "resumen": {
                "total": round(total, 2),
                "transacciones": len(compras),
                "promedio_diario": round(promedio_diario, 2),
                "dias_con_compras": len(compras_diarias)
            },
            "serie_temporal": serie_temporal
        }

    def _detail_margen(self, fecha_inicio: date, fecha_fin: date) -> Dict[str, Any]:
        """Detalle de margen bruto."""
        ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
        compras = self.compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        ingresos = sum(float(v.total or 0) for v in ventas)
        costos = sum(float(c.total or 0) for c in compras)
        utilidad = ingresos - costos
        margen = (utilidad / ingresos * 100) if ingresos > 0 else 0

        return {
            "success": True,
            "kpi": "margen",
            "periodo": {"inicio": fecha_inicio.isoformat(), "fin": fecha_fin.isoformat()},
            "resumen": {
                "ingresos": round(ingresos, 2),
                "costos": round(costos, 2),
                "utilidad_bruta": round(utilidad, 2),
                "margen_bruto_porcentaje": round(margen, 2)
            },
            "interpretacion": self._interpretar_margen(margen)
        }

    def _interpretar_margen(self, margen: float) -> str:
        """Interpreta el margen bruto."""
        if margen >= 40:
            return "Margen excelente. El negocio tiene alta rentabilidad."
        elif margen >= 25:
            return "Margen bueno. Rentabilidad saludable."
        elif margen >= 15:
            return "Margen aceptable. Considere optimizar costos."
        elif margen >= 0:
            return "Margen bajo. Se recomienda revisar estructura de costos."
        else:
            return "Margen negativo. El negocio opera con perdidas."

    def _detail_roi(self, fecha_inicio: date, fecha_fin: date) -> Dict[str, Any]:
        """Detalle de ROI."""
        ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
        compras = self.compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        ingresos = sum(float(v.total or 0) for v in ventas)
        inversion = sum(float(c.total or 0) for c in compras)
        ganancia = ingresos - inversion
        roi = (ganancia / inversion * 100) if inversion > 0 else 0

        return {
            "success": True,
            "kpi": "roi",
            "periodo": {"inicio": fecha_inicio.isoformat(), "fin": fecha_fin.isoformat()},
            "resumen": {
                "ingresos": round(ingresos, 2),
                "inversion": round(inversion, 2),
                "ganancia_neta": round(ganancia, 2),
                "roi_porcentaje": round(roi, 2)
            },
            "interpretacion": self._interpretar_roi(roi)
        }

    def _interpretar_roi(self, roi: float) -> str:
        """Interpreta el ROI."""
        if roi >= 50:
            return "ROI excelente. Retorno de inversion muy alto."
        elif roi >= 25:
            return "ROI bueno. Inversion rentable."
        elif roi >= 10:
            return "ROI aceptable. Considere mejorar eficiencia."
        elif roi >= 0:
            return "ROI bajo. Revise la estrategia de inversion."
        else:
            return "ROI negativo. La inversion no esta generando retorno."

    def _detail_alertas(self, fecha_inicio: date, fecha_fin: date) -> Dict[str, Any]:
        """Detalle de alertas."""
        alertas = self.db.query(Alerta).filter(
            Alerta.creadaEn >= fecha_inicio,
            Alerta.creadaEn <= fecha_fin
        ).order_by(desc(Alerta.creadaEn)).all()

        por_estado = {"Activa": 0, "Resuelta": 0, "Ignorada": 0}
        por_tipo = {}
        por_importancia = {"alta": 0, "media": 0, "baja": 0}

        for a in alertas:
            estado = a.estado or "Activa"
            por_estado[estado] = por_estado.get(estado, 0) + 1

            tipo = a.tipo or "otro"
            por_tipo[tipo] = por_tipo.get(tipo, 0) + 1

            imp = (a.importancia or "media").lower()
            if imp in por_importancia:
                por_importancia[imp] += 1

        return {
            "success": True,
            "kpi": "alertas",
            "periodo": {"inicio": fecha_inicio.isoformat(), "fin": fecha_fin.isoformat()},
            "resumen": {
                "total": len(alertas),
                "por_estado": por_estado,
                "por_tipo": por_tipo,
                "por_importancia": por_importancia
            }
        }

    def get_scenario_summary(self) -> Dict[str, Any]:
        """Obtiene resumen de escenarios de simulacion."""
        try:
            escenarios = self.db.query(Escenario).order_by(
                desc(Escenario.creadoEn)
            ).limit(5).all()

            return {
                "success": True,
                "total_escenarios": self.db.query(Escenario).count(),
                "recientes": [
                    {
                        "id": e.idEscenario,
                        "nombre": e.nombre,
                        "horizonte_meses": e.horizonteMeses,
                        "creado_en": e.creadoEn.isoformat() if e.creadoEn else None
                    } for e in escenarios
                ]
            }
        except Exception as e:
            logger.error(f"Error al obtener resumen de escenarios: {str(e)}")
            return {"success": False, "error": str(e)}

    def get_recent_predictions(self, limit: int = 10) -> Dict[str, Any]:
        """Obtiene predicciones recientes."""
        try:
            predicciones = self.db.query(Prediccion).order_by(
                desc(Prediccion.idPred)
            ).limit(limit).all()

            return {
                "success": True,
                "total": len(predicciones),
                "predicciones": [
                    {
                        "id": p.idPred,
                        "entidad": p.entidad,
                        "clave_entidad": p.claveEntidad,
                        "periodo": p.periodo,
                        "valor_predicho": float(p.valorPredicho) if p.valorPredicho else None,
                        "nivel_confianza": float(p.nivelConfianza) if p.nivelConfianza else None
                    } for p in predicciones
                ]
            }
        except Exception as e:
            logger.error(f"Error al obtener predicciones recientes: {str(e)}")
            return {"success": False, "error": str(e)}

    def get_user_preferences(self, user_id: int) -> Dict[str, Any]:
        """Obtiene preferencias de un usuario."""
        try:
            preferencias = self.db.query(PreferenciaUsuario).filter(
                PreferenciaUsuario.idUsuario == user_id,
                PreferenciaUsuario.visible == 1
            ).order_by(PreferenciaUsuario.orden).all()

            return {
                "success": True,
                "id_usuario": user_id,
                "preferencias": [
                    {
                        "id": p.idPreferencia,
                        "kpi": p.kpi,
                        "visible": p.visible,
                        "orden": p.orden
                    } for p in preferencias
                ]
            }
        except Exception as e:
            logger.error(f"Error al obtener preferencias: {str(e)}")
            return {"success": False, "error": str(e)}

    def update_user_preferences(
        self,
        user_id: int,
        preferencias: List[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """
        Actualiza preferencias de un usuario.

        Args:
            user_id: ID del usuario
            preferencias: Lista de {kpi, valor} donde valor indica visibilidad/orden

        Returns:
            Dict con resultado
        """
        try:
            actualizadas = 0
            creadas = 0

            for idx, pref in enumerate(preferencias):
                kpi = pref.get("kpi")
                valor = pref.get("valor", "1")  # Por defecto visible

                if not kpi:
                    continue

                # Buscar preferencia existente
                existing = self.db.query(PreferenciaUsuario).filter(
                    PreferenciaUsuario.idUsuario == user_id,
                    PreferenciaUsuario.kpi == kpi
                ).first()

                if existing:
                    existing.visible = 1 if valor == "1" or valor == 1 else 0
                    existing.orden = idx + 1
                    actualizadas += 1
                else:
                    nueva = PreferenciaUsuario(
                        idUsuario=user_id,
                        kpi=kpi,
                        visible=1 if valor == "1" or valor == 1 else 0,
                        orden=idx + 1
                    )
                    self.db.add(nueva)
                    creadas += 1

            self.db.commit()

            return {
                "success": True,
                "id_usuario": user_id,
                "actualizadas": actualizadas,
                "creadas": creadas,
                "mensaje": f"Preferencias actualizadas: {actualizadas}, creadas: {creadas}"
            }
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al actualizar preferencias: {str(e)}")
            return {"success": False, "error": str(e)}

    def compare_actual_vs_predicted(
        self,
        fecha_inicio: date,
        fecha_fin: date,
        tipo_entidad: str = "producto"
    ) -> Dict[str, Any]:
        """
        Compara valores reales vs predichos (RF-03.05).

        Args:
            fecha_inicio: Fecha inicio
            fecha_fin: Fecha fin
            tipo_entidad: Tipo de entidad a comparar

        Returns:
            Dict con comparacion real vs predicho
        """
        try:
            # Obtener predicciones del periodo (filtrar por entidad y periodo string)
            periodo_inicio = fecha_inicio.strftime("%Y-%m")
            periodo_fin = fecha_fin.strftime("%Y-%m")

            predicciones = self.db.query(Prediccion).filter(
                Prediccion.entidad == tipo_entidad,
                Prediccion.periodo >= periodo_inicio,
                Prediccion.periodo <= periodo_fin
            ).all()

            # Obtener ventas reales del periodo
            ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
            total_real = sum(float(v.total or 0) for v in ventas)

            # Calcular total predicho
            total_predicho = sum(float(p.valorPredicho or 0) for p in predicciones)

            # Calcular diferencia
            diferencia = total_real - total_predicho
            porcentaje_error = (abs(diferencia) / total_predicho * 100) if total_predicho > 0 else 0

            # Determinar precision
            if porcentaje_error <= 5:
                precision = "excelente"
            elif porcentaje_error <= 10:
                precision = "buena"
            elif porcentaje_error <= 20:
                precision = "aceptable"
            else:
                precision = "baja"

            return {
                "success": True,
                "periodo": {
                    "inicio": fecha_inicio.isoformat(),
                    "fin": fecha_fin.isoformat()
                },
                "comparacion": {
                    "valor_real": round(total_real, 2),
                    "valor_predicho": round(total_predicho, 2),
                    "diferencia": round(diferencia, 2),
                    "porcentaje_error": round(porcentaje_error, 2),
                    "precision": precision
                },
                "num_predicciones": len(predicciones),
                "num_ventas": len(ventas)
            }
        except Exception as e:
            logger.error(f"Error al comparar real vs predicho: {str(e)}")
            return {"success": False, "error": str(e)}
