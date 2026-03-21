"""
Servicio de evaluacion de rentabilidad.
RF-06: Evaluacion de rentabilidad de productos y categorias.
"""

import pandas as pd
import numpy as np
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime, date, timedelta
from dateutil.relativedelta import relativedelta
from sqlalchemy.orm import Session
from sqlalchemy import func, extract
from dataclasses import dataclass, field
from enum import Enum
import logging

from app.models import Venta, DetalleVenta, Compra, DetalleCompra, Producto, Categoria
from app.repositories import VentaRepository, CompraRepository, ProductoRepository

logger = logging.getLogger(__name__)


class PeriodType(str, Enum):
    """Tipos de periodo para analisis."""
    DAILY = "daily"
    WEEKLY = "weekly"
    MONTHLY = "monthly"
    QUARTERLY = "quarterly"
    YEARLY = "yearly"


@dataclass
class FinancialIndicators:
    """Indicadores financieros calculados."""
    ingresos_totales: float = 0.0
    costos_totales: float = 0.0
    utilidad_bruta: float = 0.0
    margen_bruto: float = 0.0  # (Utilidad Bruta / Ingresos) * 100
    utilidad_operativa: float = 0.0  # RN-06.02
    margen_operativo: float = 0.0
    utilidad_neta: float = 0.0
    margen_neto: float = 0.0
    roa: float = 0.0  # Return on Assets
    roe: float = 0.0  # Return on Equity
    rotacion_inventario: float = 0.0
    periodo_inicio: Optional[date] = None
    periodo_fin: Optional[date] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "ingresos_totales": round(self.ingresos_totales, 2),
            "costos_totales": round(self.costos_totales, 2),
            "utilidad_bruta": round(self.utilidad_bruta, 2),
            "margen_bruto": round(self.margen_bruto, 2),
            "utilidad_operativa": round(self.utilidad_operativa, 2),
            "margen_operativo": round(self.margen_operativo, 2),
            "utilidad_neta": round(self.utilidad_neta, 2),
            "margen_neto": round(self.margen_neto, 2),
            "roa": round(self.roa, 2),
            "roe": round(self.roe, 2),
            "rotacion_inventario": round(self.rotacion_inventario, 2),
            "periodo": {
                "inicio": self.periodo_inicio.isoformat() if self.periodo_inicio else None,
                "fin": self.periodo_fin.isoformat() if self.periodo_fin else None
            }
        }


@dataclass
class ProductProfitability:
    """Rentabilidad de un producto."""
    id_producto: int
    nombre: str
    categoria: Optional[str] = None
    unidades_vendidas: int = 0
    ingresos: float = 0.0
    costo_total: float = 0.0
    utilidad: float = 0.0
    margen: float = 0.0
    precio_promedio_venta: float = 0.0
    costo_promedio: float = 0.0
    es_rentable: bool = True  # RN-06.04: margen >= 10%
    ranking: int = 0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id_producto": self.id_producto,
            "nombre": self.nombre,
            "categoria": self.categoria,
            "unidades_vendidas": self.unidades_vendidas,
            "ingresos": round(self.ingresos, 2),
            "costo_total": round(self.costo_total, 2),
            "utilidad": round(self.utilidad, 2),
            "margen": round(self.margen, 2),
            "precio_promedio_venta": round(self.precio_promedio_venta, 2),
            "costo_promedio": round(self.costo_promedio, 2),
            "es_rentable": self.es_rentable,
            "ranking": self.ranking
        }


@dataclass
class CategoryProfitability:
    """Rentabilidad de una categoria."""
    id_categoria: int
    nombre: str
    num_productos: int = 0
    unidades_vendidas: int = 0
    ingresos: float = 0.0
    costo_total: float = 0.0
    utilidad: float = 0.0
    margen: float = 0.0
    productos_rentables: int = 0
    productos_no_rentables: int = 0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id_categoria": self.id_categoria,
            "nombre": self.nombre,
            "num_productos": self.num_productos,
            "unidades_vendidas": self.unidades_vendidas,
            "ingresos": round(self.ingresos, 2),
            "costo_total": round(self.costo_total, 2),
            "utilidad": round(self.utilidad, 2),
            "margen": round(self.margen, 2),
            "productos_rentables": self.productos_rentables,
            "productos_no_rentables": self.productos_no_rentables
        }


@dataclass
class ProfitabilityTrend:
    """Tendencia de rentabilidad en el tiempo."""
    periodo: str
    fecha_inicio: date
    fecha_fin: date
    ingresos: float = 0.0
    costos: float = 0.0
    utilidad: float = 0.0
    margen: float = 0.0
    variacion_ingresos: Optional[float] = None  # vs periodo anterior
    variacion_utilidad: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "periodo": self.periodo,
            "fecha_inicio": self.fecha_inicio.isoformat(),
            "fecha_fin": self.fecha_fin.isoformat(),
            "ingresos": round(self.ingresos, 2),
            "costos": round(self.costos, 2),
            "utilidad": round(self.utilidad, 2),
            "margen": round(self.margen, 2),
            "variacion_ingresos": round(self.variacion_ingresos, 2) if self.variacion_ingresos else None,
            "variacion_utilidad": round(self.variacion_utilidad, 2) if self.variacion_utilidad else None
        }


class ProfitabilityService:
    """
    Servicio de evaluacion de rentabilidad.

    RF-06: Evaluacion de rentabilidad
    - RF-06.01: Calcular indicadores financieros
    - RF-06.02: Rentabilidad por producto
    - RF-06.03: Identificar productos no rentables
    """

    # RN-06.04: Umbral minimo de margen para considerar rentable
    MIN_PROFIT_MARGIN = 10.0  # 10%

    # Gastos operativos estimados (porcentaje de ingresos)
    OPERATING_EXPENSES_RATE = 0.15  # 15%

    def __init__(self, db: Session):
        self.db = db
        self.venta_repo = VentaRepository(db)
        self.compra_repo = CompraRepository(db)
        self.producto_repo = ProductoRepository(db)

    def validate_data_completeness(
        self,
        fecha_inicio: date,
        fecha_fin: date
    ) -> Tuple[bool, List[str]]:
        """
        Valida que existan datos suficientes para el analisis.
        Solo requiere que haya ventas; las compras son opcionales (el costo
        se puede estimar desde costoUnitario del producto si no hay compras).

        Returns:
            Tuple[bool, List[str]]: (es_valido, lista_problemas)
        """
        issues = []

        # Verificar ventas en el periodo (requerido)
        ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
        if not ventas:
            issues.append(f"No hay ventas registradas entre {fecha_inicio} y {fecha_fin}")

        # Verificar productos (requerido)
        productos = self.producto_repo.get_all()
        if not productos:
            issues.append("No hay productos registrados")

        return len(issues) == 0, issues

    def calculate_indicators(
        self,
        fecha_inicio: Optional[date] = None,
        fecha_fin: Optional[date] = None,
        activos_totales: Optional[float] = None,
        patrimonio: Optional[float] = None
    ) -> Dict[str, Any]:
        """
        Calcula indicadores financieros generales.
        RF-06.01: Calcular indicadores financieros.

        Args:
            fecha_inicio: Fecha inicial del periodo
            fecha_fin: Fecha final del periodo
            activos_totales: Total de activos (para ROA)
            patrimonio: Patrimonio neto (para ROE)

        Returns:
            Dict con indicadores financieros
        """
        # Fechas por defecto: ultimo mes
        if fecha_fin is None:
            fecha_fin = date.today()
        if fecha_inicio is None:
            fecha_inicio = fecha_fin - timedelta(days=30)

        # Obtener ventas del periodo
        ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
        ingresos_totales = sum(float(v.total or 0) for v in ventas)

        # Obtener compras del periodo (costos)
        compras = self.compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
        costos_totales = sum(float(c.total or 0) for c in compras)

        # Calcular indicadores
        indicators = FinancialIndicators(
            periodo_inicio=fecha_inicio,
            periodo_fin=fecha_fin
        )

        indicators.ingresos_totales = ingresos_totales
        indicators.costos_totales = costos_totales

        # Utilidad Bruta
        indicators.utilidad_bruta = ingresos_totales - costos_totales

        # Margen Bruto
        if ingresos_totales > 0:
            indicators.margen_bruto = (indicators.utilidad_bruta / ingresos_totales) * 100

        # Gastos operativos estimados
        gastos_operativos = ingresos_totales * self.OPERATING_EXPENSES_RATE

        # Utilidad Operativa (RN-06.02)
        indicators.utilidad_operativa = indicators.utilidad_bruta - gastos_operativos

        # Margen Operativo
        if ingresos_totales > 0:
            indicators.margen_operativo = (indicators.utilidad_operativa / ingresos_totales) * 100

        # Utilidad Neta (simplificada, sin impuestos)
        indicators.utilidad_neta = indicators.utilidad_operativa

        # Margen Neto
        if ingresos_totales > 0:
            indicators.margen_neto = (indicators.utilidad_neta / ingresos_totales) * 100

        # ROA (Return on Assets)
        if activos_totales and activos_totales > 0:
            indicators.roa = (indicators.utilidad_neta / activos_totales) * 100

        # ROE (Return on Equity)
        if patrimonio and patrimonio > 0:
            indicators.roe = (indicators.utilidad_neta / patrimonio) * 100

        return {
            "success": True,
            "indicators": indicators.to_dict(),
            "summary": {
                "periodo": f"{fecha_inicio} a {fecha_fin}",
                "total_ventas": len(ventas),
                "total_compras": len(compras),
                "rentable": indicators.margen_bruto >= self.MIN_PROFIT_MARGIN
            }
        }

    def get_product_profitability(
        self,
        fecha_inicio: Optional[date] = None,
        fecha_fin: Optional[date] = None,
        categoria_id: Optional[int] = None,
        solo_no_rentables: bool = False
    ) -> Dict[str, Any]:
        """
        Calcula rentabilidad por producto.
        RF-06.02: Rentabilidad por producto.
        RN-06.04: Identificar productos no rentables (margen < 10%).

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final
            categoria_id: Filtrar por categoria
            solo_no_rentables: Solo mostrar productos no rentables

        Returns:
            Dict con rentabilidad de productos
        """
        if fecha_fin is None:
            fecha_fin = date.today()
        if fecha_inicio is None:
            fecha_inicio = fecha_fin - timedelta(days=30)

        # Obtener todos los productos
        productos = self.producto_repo.get_all()
        if categoria_id:
            productos = [p for p in productos if p.idCategoria == categoria_id]

        if not productos:
            return {
                "success": False,
                "error": "No hay productos para analizar"
            }

        # Calcular rentabilidad por producto
        profitability_list: List[ProductProfitability] = []

        for producto in productos:
            prof = self._calculate_product_profitability(
                producto, fecha_inicio, fecha_fin
            )
            if prof:
                # RN-06.04: Marcar si es rentable
                prof.es_rentable = prof.margen >= self.MIN_PROFIT_MARGIN
                profitability_list.append(prof)

        # Filtrar solo no rentables si se solicita
        if solo_no_rentables:
            profitability_list = [p for p in profitability_list if not p.es_rentable]

        # Ordenar por utilidad (mayor a menor) y asignar ranking
        profitability_list.sort(key=lambda x: x.utilidad, reverse=True)
        for i, prof in enumerate(profitability_list):
            prof.ranking = i + 1

        # Estadisticas
        total_productos = len(profitability_list)
        rentables = sum(1 for p in profitability_list if p.es_rentable)
        no_rentables = total_productos - rentables

        return {
            "success": True,
            "periodo": {
                "inicio": fecha_inicio.isoformat(),
                "fin": fecha_fin.isoformat()
            },
            "resumen": {
                "total_productos": total_productos,
                "productos_rentables": rentables,
                "productos_no_rentables": no_rentables,
                "porcentaje_rentables": round((rentables / total_productos * 100) if total_productos > 0 else 0, 2),
                "umbral_rentabilidad": self.MIN_PROFIT_MARGIN
            },
            "productos": [p.to_dict() for p in profitability_list]
        }

    def _calculate_product_profitability(
        self,
        producto: Producto,
        fecha_inicio: date,
        fecha_fin: date
    ) -> Optional[ProductProfitability]:
        """Calcula la rentabilidad de un producto especifico."""
        # Obtener ventas del producto en el periodo
        ventas_query = self.db.query(
            func.sum(DetalleVenta.cantidad).label('total_cantidad'),
            func.sum(DetalleVenta.cantidad * DetalleVenta.precioUnitario).label('total_ingresos'),
            func.avg(DetalleVenta.precioUnitario).label('precio_promedio')
        ).join(
            Venta, DetalleVenta.idVenta == Venta.idVenta
        ).filter(
            DetalleVenta.idProducto == producto.idProducto,
            Venta.fecha >= fecha_inicio,
            Venta.fecha <= fecha_fin
        ).first()

        # Obtener compras del producto en el periodo (para costo)
        compras_query = self.db.query(
            func.sum(DetalleCompra.cantidad).label('total_cantidad'),
            func.sum(DetalleCompra.subtotal).label('total_costo'),
            func.avg(DetalleCompra.costo).label('costo_promedio')
        ).join(
            Compra, DetalleCompra.idCompra == Compra.idCompra
        ).filter(
            DetalleCompra.idProducto == producto.idProducto,
            Compra.fecha >= fecha_inicio,
            Compra.fecha <= fecha_fin
        ).first()

        # Si no hay ventas, retornar con valores en 0
        unidades_vendidas = int(ventas_query.total_cantidad or 0)
        ingresos = float(ventas_query.total_ingresos or 0)
        precio_promedio = float(ventas_query.precio_promedio or 0)

        # Calcular costo
        if compras_query.costo_promedio:
            costo_unitario = float(compras_query.costo_promedio)
        elif producto.costoUnitario:
            costo_unitario = float(producto.costoUnitario)
        else:
            # Estimar costo como 60% del precio de venta
            costo_unitario = precio_promedio * 0.6 if precio_promedio > 0 else 0

        costo_total = costo_unitario * unidades_vendidas

        # Calcular utilidad y margen
        utilidad = ingresos - costo_total
        margen = (utilidad / ingresos * 100) if ingresos > 0 else 0

        # Obtener categoria
        categoria_nombre = None
        if producto.idCategoria:
            categoria = self.db.query(Categoria).filter(
                Categoria.idCategoria == producto.idCategoria
            ).first()
            if categoria:
                categoria_nombre = categoria.nombre

        return ProductProfitability(
            id_producto=producto.idProducto,
            nombre=producto.nombre or f"Producto {producto.idProducto}",
            categoria=categoria_nombre,
            unidades_vendidas=unidades_vendidas,
            ingresos=ingresos,
            costo_total=costo_total,
            utilidad=utilidad,
            margen=margen,
            precio_promedio_venta=precio_promedio,
            costo_promedio=costo_unitario
        )

    def get_category_profitability(
        self,
        fecha_inicio: Optional[date] = None,
        fecha_fin: Optional[date] = None
    ) -> Dict[str, Any]:
        """
        Calcula rentabilidad por categoria.

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final

        Returns:
            Dict con rentabilidad por categoria
        """
        if fecha_fin is None:
            fecha_fin = date.today()
        if fecha_inicio is None:
            fecha_inicio = fecha_fin - timedelta(days=30)

        # Obtener categorias
        categorias = self.db.query(Categoria).all()

        if not categorias:
            return {
                "success": False,
                "error": "No hay categorias registradas"
            }

        category_list: List[CategoryProfitability] = []

        for categoria in categorias:
            # Obtener productos de la categoria
            productos = self.db.query(Producto).filter(
                Producto.idCategoria == categoria.idCategoria
            ).all()

            cat_prof = CategoryProfitability(
                id_categoria=categoria.idCategoria,
                nombre=categoria.nombre or f"Categoria {categoria.idCategoria}",
                num_productos=len(productos)
            )

            # Calcular metricas agregadas
            for producto in productos:
                prod_prof = self._calculate_product_profitability(
                    producto, fecha_inicio, fecha_fin
                )
                if prod_prof:
                    cat_prof.unidades_vendidas += prod_prof.unidades_vendidas
                    cat_prof.ingresos += prod_prof.ingresos
                    cat_prof.costo_total += prod_prof.costo_total
                    cat_prof.utilidad += prod_prof.utilidad

                    if prod_prof.margen >= self.MIN_PROFIT_MARGIN:
                        cat_prof.productos_rentables += 1
                    else:
                        cat_prof.productos_no_rentables += 1

            # Calcular margen de la categoria
            if cat_prof.ingresos > 0:
                cat_prof.margen = (cat_prof.utilidad / cat_prof.ingresos) * 100

            category_list.append(cat_prof)

        # Ordenar por utilidad
        category_list.sort(key=lambda x: x.utilidad, reverse=True)

        return {
            "success": True,
            "periodo": {
                "inicio": fecha_inicio.isoformat(),
                "fin": fecha_fin.isoformat()
            },
            "categorias": [c.to_dict() for c in category_list],
            "resumen": {
                "total_categorias": len(category_list),
                "ingresos_totales": round(sum(c.ingresos for c in category_list), 2),
                "utilidad_total": round(sum(c.utilidad for c in category_list), 2)
            }
        }

    def get_profitability_projection(
        self,
        user_id: int,
        periods: int = 30
    ) -> Dict[str, Any]:
        """
        Proyecta la rentabilidad futura usando el mejor pack activo del usuario.

        Metodología:
        1. Obtiene el mejor pack activo (mayor R² de ventas).
        2. Llama a forecast_pack para obtener ventas y compras proyectadas (aggregate).
        3. Obtiene mix histórico de productos (últimos N días) para desagregar el total.
        4. Distribuye la proyección total por producto usando su share histórico.
        5. Agrega por categoría y calcula indicadores generales.

        Args:
            user_id: ID del usuario autenticado.
            periods: Días a proyectar (7–180).

        Returns:
            Dict con proyecciones generales, por categoría y por producto.
        """
        from app.services.prediction_service import PredictionService
        from app.models.prediccion import ModeloPack, VersionModelo
        from sqlalchemy import desc

        # ── 1. Mejor pack activo ──────────────────────────────────────────────
        best_pack = (
            self.db.query(ModeloPack)
            .join(VersionModelo, ModeloPack.idVersionVentas == VersionModelo.idVersion)
            .filter(
                ModeloPack.estado == 'Activo',
                ModeloPack.creadoPor == user_id,
                VersionModelo.precision.isnot(None)
            )
            .order_by(desc(VersionModelo.precision))
            .first()
        )
        if not best_pack:
            # Fallback: cualquier pack activo (sin filtro de usuario)
            best_pack = (
                self.db.query(ModeloPack)
                .join(VersionModelo, ModeloPack.idVersionVentas == VersionModelo.idVersion)
                .filter(ModeloPack.estado == 'Activo', VersionModelo.precision.isnot(None))
                .order_by(desc(VersionModelo.precision))
                .first()
            )
        if not best_pack:
            return {"success": False, "error": "No hay packs de modelos activos disponibles"}

        # ── 2. Forecast coordinado del pack ───────────────────────────────────
        try:
            pred_service = PredictionService(self.db)
            forecast_result = pred_service.forecast_pack(best_pack.packKey, periods)
        except Exception as e:
            logger.error(f"Error en forecast_pack: {e}")
            return {"success": False, "error": f"Error al generar forecast: {str(e)}"}

        if not forecast_result.get("success"):
            return {"success": False, "error": forecast_result.get("error", "Error en forecast")}

        total_forecast_ventas = sum(forecast_result["ventas"]["predictions"]["values"])

        # ── 3. Mix histórico anual para shares y ratios de costo estables ───────
        # Usamos 1 año completo para obtener un mix representativo de todos los
        # productos.  Comparar contra "últimos N días" genera variaciones enormes
        # cuando el seed-data tiene pocas transacciones recientes.
        fecha_fin_hist = date.today()
        fecha_ini_hist = fecha_fin_hist - timedelta(days=365)

        productos = self.producto_repo.get_all()
        hist_data = []
        hist_total_ingresos_1yr = 0.0
        hist_total_costos_1yr   = 0.0

        for producto in productos:
            prof = self._calculate_product_profitability(producto, fecha_ini_hist, fecha_fin_hist)
            if prof and prof.ingresos > 0:
                hist_total_ingresos_1yr += prof.ingresos
                hist_total_costos_1yr   += prof.costo_total
                hist_data.append(prof)

        if hist_total_ingresos_1yr <= 0:
            return {"success": False, "error": "Sin datos históricos de ventas para proyectar"}

        # Baseline esperado para el período de proyección (escalado desde 1 año)
        # Representa lo que el histórico esperaría para N días, sin crecimiento
        hist_baseline_ing  = hist_total_ingresos_1yr * (periods / 365)
        hist_baseline_cos  = hist_total_costos_1yr   * (periods / 365)
        hist_baseline_util = hist_baseline_ing - hist_baseline_cos

        # ── 4. Proyección por producto ─────────────────────────────────────────
        por_producto = []
        category_map: Dict[str, Dict[str, float]] = {}

        for prof in hist_data:
            # Share calculado sobre el año completo → más estable
            share      = prof.ingresos / hist_total_ingresos_1yr
            cost_ratio = (prof.costo_total / prof.ingresos) if prof.ingresos > 0 else 0.6

            proj_ing  = total_forecast_ventas * share
            proj_cos  = proj_ing * cost_ratio
            proj_util = proj_ing - proj_cos
            proj_marg = (proj_util / proj_ing * 100) if proj_ing > 0 else 0.0

            por_producto.append({
                "id_producto":          prof.id_producto,
                "nombre":               prof.nombre,
                "categoria":            prof.categoria or "Sin Categoría",
                "ingresos_proyectados": round(proj_ing,  2),
                "costos_proyectados":   round(proj_cos,  2),
                "utilidad_proyectada":  round(proj_util, 2),
                "margen_proyectado":    round(proj_marg, 2),
            })

            cat = prof.categoria or "Sin Categoría"
            if cat not in category_map:
                category_map[cat] = {"ingresos": 0.0, "costos": 0.0}
            category_map[cat]["ingresos"] += proj_ing
            category_map[cat]["costos"]   += proj_cos

        # ── 5. Proyección por categoría ────────────────────────────────────────
        por_categoria = []
        for cat_nombre, cat_vals in sorted(
            category_map.items(), key=lambda x: x[1]["ingresos"], reverse=True
        ):
            cat_ing  = cat_vals["ingresos"]
            cat_cos  = cat_vals["costos"]
            cat_util = cat_ing - cat_cos
            cat_marg = (cat_util / cat_ing * 100) if cat_ing > 0 else 0.0
            por_categoria.append({
                "nombre":               cat_nombre,
                "ingresos_proyectados": round(cat_ing,  2),
                "costos_proyectados":   round(cat_cos,  2),
                "utilidad_proyectada":  round(cat_util, 2),
                "margen_proyectado":    round(cat_marg, 2),
            })

        # ── 6. Indicadores generales proyectados ──────────────────────────────
        total_proj_ing   = sum(p["ingresos_proyectados"] for p in por_producto)
        total_proj_cos   = sum(p["costos_proyectados"]   for p in por_producto)
        util_bruta       = total_proj_ing - total_proj_cos
        margen_bruto     = (util_bruta / total_proj_ing * 100) if total_proj_ing > 0 else 0.0

        gastos_op        = total_proj_ing * self.OPERATING_EXPENSES_RATE
        util_operativa   = util_bruta - gastos_op
        margen_operativo = (util_operativa / total_proj_ing * 100) if total_proj_ing > 0 else 0.0

        util_neta  = util_operativa
        margen_neto = (util_neta / total_proj_ing * 100) if total_proj_ing > 0 else 0.0

        # Variación vs baseline histórico escalado al mismo período
        # Representa crecimiento/decrecimiento esperado según el modelo
        var_ingresos = (
            (total_proj_ing - hist_baseline_ing) / hist_baseline_ing * 100
            if hist_baseline_ing > 0 else 0.0
        )
        var_utilidad = (
            (util_bruta - hist_baseline_util) / abs(hist_baseline_util) * 100
            if hist_baseline_util != 0 else 0.0
        )

        # ── 7. Metadatos del pack ──────────────────────────────────────────────
        v_ventas  = best_pack.version_ventas
        v_compras = best_pack.version_compras
        prec_ventas  = float(v_ventas.precision)  * 100 if (v_ventas  and v_ventas.precision)  else 0.0
        prec_compras = float(v_compras.precision) * 100 if (v_compras and v_compras.precision) else 0.0

        start_date = date.today() + timedelta(days=1)
        end_date   = start_date   + timedelta(days=periods - 1)

        return {
            "success":                 True,
            "pack_key":                best_pack.packKey,
            "pack_nombre":             best_pack.nombre or best_pack.packKey,
            "precision_ventas":        round(prec_ventas,  2),
            "precision_compras":       round(prec_compras, 2),
            "periods":                 periods,
            "fecha_inicio_proyeccion": start_date.isoformat(),
            "fecha_fin_proyeccion":    end_date.isoformat(),
            "general": {
                "ingresos_proyectados": round(total_proj_ing,   2),
                "costos_proyectados":   round(total_proj_cos,   2),
                "utilidad_proyectada":  round(util_bruta,       2),
                "margen_bruto":         round(margen_bruto,     2),
                "margen_operativo":     round(margen_operativo, 2),
                "margen_neto":          round(margen_neto,      2),
                "variacion_ingresos":   round(var_ingresos,     2),
                "variacion_utilidad":   round(var_utilidad,     2),
            },
            "por_categoria": por_categoria,
            "por_producto":  sorted(
                por_producto,
                key=lambda x: x["ingresos_proyectados"],
                reverse=True
            ),
        }

    def get_profitability_trends(
        self,
        fecha_inicio: Optional[date] = None,
        fecha_fin: Optional[date] = None,
        period_type: PeriodType = PeriodType.MONTHLY
    ) -> Dict[str, Any]:
        """
        Obtiene tendencias de rentabilidad en el tiempo.
        RN-06.03: Calculo por periodo mensual, trimestral, anual.

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final
            period_type: Tipo de periodo (monthly, quarterly, yearly)

        Returns:
            Dict con tendencias de rentabilidad
        """
        if fecha_fin is None:
            fecha_fin = date.today()
        if fecha_inicio is None:
            # Por defecto ultimo año
            fecha_inicio = fecha_fin - timedelta(days=365)

        # Generar periodos
        periods = self._generate_periods(fecha_inicio, fecha_fin, period_type)

        trends: List[ProfitabilityTrend] = []
        prev_trend: Optional[ProfitabilityTrend] = None

        for period_start, period_end, period_label in periods:
            # Obtener ventas del periodo
            ventas = self.venta_repo.get_by_rango_fechas(period_start, period_end)
            ingresos = sum(float(v.total or 0) for v in ventas)

            # Obtener compras del periodo
            compras = self.compra_repo.get_by_rango_fechas(period_start, period_end)
            costos = sum(float(c.total or 0) for c in compras)

            utilidad = ingresos - costos
            margen = (utilidad / ingresos * 100) if ingresos > 0 else 0

            trend = ProfitabilityTrend(
                periodo=period_label,
                fecha_inicio=period_start,
                fecha_fin=period_end,
                ingresos=ingresos,
                costos=costos,
                utilidad=utilidad,
                margen=margen
            )

            # Calcular variaciones vs periodo anterior
            if prev_trend and prev_trend.ingresos > 0:
                trend.variacion_ingresos = (
                    (ingresos - prev_trend.ingresos) / prev_trend.ingresos * 100
                )
            if prev_trend and prev_trend.utilidad != 0:
                trend.variacion_utilidad = (
                    (utilidad - prev_trend.utilidad) / abs(prev_trend.utilidad) * 100
                )

            trends.append(trend)
            prev_trend = trend

        return {
            "success": True,
            "periodo": {
                "inicio": fecha_inicio.isoformat(),
                "fin": fecha_fin.isoformat(),
                "tipo": period_type.value
            },
            "tendencias": [t.to_dict() for t in trends],
            "resumen": {
                "periodos_analizados": len(trends),
                "ingresos_promedio": round(
                    sum(t.ingresos for t in trends) / len(trends) if trends else 0, 2
                ),
                "margen_promedio": round(
                    sum(t.margen for t in trends) / len(trends) if trends else 0, 2
                )
            }
        }

    def _generate_periods(
        self,
        fecha_inicio: date,
        fecha_fin: date,
        period_type: PeriodType
    ) -> List[Tuple[date, date, str]]:
        """Genera lista de periodos entre las fechas."""
        periods = []
        current = fecha_inicio

        while current <= fecha_fin:
            if period_type == PeriodType.DAILY:
                period_end = current
                label = current.strftime("%Y-%m-%d")
                next_period = current + timedelta(days=1)

            elif period_type == PeriodType.WEEKLY:
                # Inicio de semana (lunes)
                period_start = current - timedelta(days=current.weekday())
                period_end = period_start + timedelta(days=6)
                if period_end > fecha_fin:
                    period_end = fecha_fin
                label = f"Sem {period_start.strftime('%Y-%m-%d')}"
                next_period = period_start + timedelta(days=7)
                current = period_start

            elif period_type == PeriodType.MONTHLY:
                period_start = current.replace(day=1)
                # Ultimo dia del mes
                if current.month == 12:
                    period_end = date(current.year + 1, 1, 1) - timedelta(days=1)
                else:
                    period_end = date(current.year, current.month + 1, 1) - timedelta(days=1)
                if period_end > fecha_fin:
                    period_end = fecha_fin
                label = current.strftime("%Y-%m")
                next_period = period_end + timedelta(days=1)
                current = period_start

            elif period_type == PeriodType.QUARTERLY:
                quarter = (current.month - 1) // 3 + 1
                period_start = date(current.year, (quarter - 1) * 3 + 1, 1)
                if quarter == 4:
                    period_end = date(current.year + 1, 1, 1) - timedelta(days=1)
                else:
                    period_end = date(current.year, quarter * 3 + 1, 1) - timedelta(days=1)
                if period_end > fecha_fin:
                    period_end = fecha_fin
                label = f"{current.year}-Q{quarter}"
                next_period = period_end + timedelta(days=1)
                current = period_start

            elif period_type == PeriodType.YEARLY:
                period_start = date(current.year, 1, 1)
                period_end = date(current.year, 12, 31)
                if period_end > fecha_fin:
                    period_end = fecha_fin
                label = str(current.year)
                next_period = date(current.year + 1, 1, 1)
                current = period_start

            else:
                break

            periods.append((current, period_end, label))
            current = next_period

            # Evitar bucle infinito
            if len(periods) > 365:
                break

        return periods

    def get_product_ranking(
        self,
        fecha_inicio: Optional[date] = None,
        fecha_fin: Optional[date] = None,
        metric: str = "utilidad",
        limit: int = 10,
        ascending: bool = False
    ) -> Dict[str, Any]:
        """
        Obtiene ranking de productos por metrica especificada.

        Args:
            fecha_inicio: Fecha inicial
            fecha_fin: Fecha final
            metric: Metrica para ordenar (utilidad, margen, ingresos, unidades)
            limit: Numero de productos a retornar
            ascending: Orden ascendente (para ver peores)

        Returns:
            Dict con ranking de productos
        """
        # Obtener rentabilidad de todos los productos
        result = self.get_product_profitability(fecha_inicio, fecha_fin)

        if not result.get("success"):
            return result

        productos = result.get("productos", [])

        # Ordenar por metrica
        valid_metrics = ["utilidad", "margen", "ingresos", "unidades_vendidas"]
        if metric not in valid_metrics:
            metric = "utilidad"

        productos.sort(
            key=lambda x: x.get(metric, 0),
            reverse=not ascending
        )

        # Limitar resultados
        productos = productos[:limit]

        # Reasignar ranking
        for i, p in enumerate(productos):
            p["ranking"] = i + 1

        return {
            "success": True,
            "periodo": result.get("periodo"),
            "metrica_ordenamiento": metric,
            "orden": "ascendente" if ascending else "descendente",
            "ranking": productos,
            "descripcion": (
                f"Top {limit} productos por {metric} "
                f"({'menor' if ascending else 'mayor'} primero)"
            )
        }

    def compare_periods(
        self,
        periodo1_inicio: date,
        periodo1_fin: date,
        periodo2_inicio: date,
        periodo2_fin: date
    ) -> Dict[str, Any]:
        """
        Compara rentabilidad entre dos periodos.

        Args:
            periodo1_inicio: Inicio periodo 1
            periodo1_fin: Fin periodo 1
            periodo2_inicio: Inicio periodo 2
            periodo2_fin: Fin periodo 2

        Returns:
            Dict con comparacion de periodos
        """
        # Calcular indicadores de cada periodo
        result1 = self.calculate_indicators(periodo1_inicio, periodo1_fin)
        result2 = self.calculate_indicators(periodo2_inicio, periodo2_fin)

        if not result1.get("success") or not result2.get("success"):
            return {
                "success": False,
                "error": "No se pudieron calcular indicadores para ambos periodos",
                "periodo1_error": result1.get("error"),
                "periodo2_error": result2.get("error")
            }

        ind1 = result1["indicators"]
        ind2 = result2["indicators"]

        # Calcular variaciones
        def calc_variation(v1, v2):
            if v1 == 0:
                return None
            return round((v2 - v1) / abs(v1) * 100, 2)

        comparison = {
            "ingresos": {
                "periodo1": ind1["ingresos_totales"],
                "periodo2": ind2["ingresos_totales"],
                "variacion": calc_variation(ind1["ingresos_totales"], ind2["ingresos_totales"])
            },
            "costos": {
                "periodo1": ind1["costos_totales"],
                "periodo2": ind2["costos_totales"],
                "variacion": calc_variation(ind1["costos_totales"], ind2["costos_totales"])
            },
            "utilidad_bruta": {
                "periodo1": ind1["utilidad_bruta"],
                "periodo2": ind2["utilidad_bruta"],
                "variacion": calc_variation(ind1["utilidad_bruta"], ind2["utilidad_bruta"])
            },
            "margen_bruto": {
                "periodo1": ind1["margen_bruto"],
                "periodo2": ind2["margen_bruto"],
                "diferencia": round(ind2["margen_bruto"] - ind1["margen_bruto"], 2)
            },
            "utilidad_operativa": {
                "periodo1": ind1["utilidad_operativa"],
                "periodo2": ind2["utilidad_operativa"],
                "variacion": calc_variation(ind1["utilidad_operativa"], ind2["utilidad_operativa"])
            }
        }

        return {
            "success": True,
            "periodo1": {
                "inicio": periodo1_inicio.isoformat(),
                "fin": periodo1_fin.isoformat()
            },
            "periodo2": {
                "inicio": periodo2_inicio.isoformat(),
                "fin": periodo2_fin.isoformat()
            },
            "comparacion": comparison,
            "conclusion": self._generate_comparison_conclusion(comparison)
        }

    def _generate_comparison_conclusion(self, comparison: Dict) -> str:
        """Genera conclusion textual de la comparacion."""
        conclusions = []

        # Analizar ingresos
        var_ingresos = comparison["ingresos"]["variacion"]
        if var_ingresos:
            if var_ingresos > 0:
                conclusions.append(f"Ingresos aumentaron {var_ingresos}%")
            else:
                conclusions.append(f"Ingresos disminuyeron {abs(var_ingresos)}%")

        # Analizar margen
        dif_margen = comparison["margen_bruto"]["diferencia"]
        if dif_margen > 0:
            conclusions.append(f"Margen bruto mejoro {dif_margen} puntos porcentuales")
        elif dif_margen < 0:
            conclusions.append(f"Margen bruto empeoro {abs(dif_margen)} puntos porcentuales")

        # Analizar utilidad
        var_utilidad = comparison["utilidad_operativa"]["variacion"]
        if var_utilidad:
            if var_utilidad > 0:
                conclusions.append(f"Utilidad operativa crecio {var_utilidad}%")
            else:
                conclusions.append(f"Utilidad operativa cayo {abs(var_utilidad)}%")

        return ". ".join(conclusions) if conclusions else "Sin cambios significativos"
