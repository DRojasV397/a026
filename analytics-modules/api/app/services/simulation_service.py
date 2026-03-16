"""
Servicio de simulacion de escenarios.
RF-05: Simulacion de escenarios financieros.
"""

import numpy as np
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime, date, timedelta
from dateutil.relativedelta import relativedelta
from sqlalchemy.orm import Session
from sqlalchemy import func
from dataclasses import dataclass, field
from decimal import Decimal
from enum import Enum
import logging
import json

from app.models import (
    Escenario, ParametroEscenario, ResultadoEscenario,
    Venta, DetalleVenta, Compra, DetalleCompra, Producto, Categoria
)
from app.repositories import (
    VentaRepository, CompraRepository, ProductoRepository
)
from app.repositories.escenario_repository import (
    EscenarioRepository, ParametroEscenarioRepository, ResultadoEscenarioRepository
)

logger = logging.getLogger(__name__)


class ParameterType(str, Enum):
    """Tipos de parametros de simulacion."""
    PRECIO = "precio"
    COSTO = "costo"
    DEMANDA = "demanda"
    PORCENTAJE = "porcentaje"


class IndicatorType(str, Enum):
    """Tipos de indicadores de resultado (KPIs)."""
    INGRESOS = "ingresos"
    COSTOS = "costos"
    UTILIDAD_BRUTA = "utilidad_bruta"
    MARGEN_BRUTO = "margen_bruto"
    UNIDADES = "unidades"


@dataclass
class ScenarioParameter:
    """Parametro de escenario."""
    nombre: str
    valor_base: float
    valor_actual: float
    descripcion: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "nombre": self.nombre,
            "valor_base": self.valor_base,
            "valor_actual": self.valor_actual,
            "descripcion": self.descripcion
        }


@dataclass
class SimulationResult:
    """Resultado de simulacion para un periodo."""
    periodo: date
    kpi: str
    valor_base: float
    valor_simulado: float
    diferencia: float
    porcentaje_cambio: float

    def to_dict(self) -> Dict[str, Any]:
        return {
            "periodo": self.periodo.isoformat() if isinstance(self.periodo, date) else self.periodo,
            "kpi": self.kpi,
            "valor_base": round(self.valor_base, 2),
            "valor_simulado": round(self.valor_simulado, 2),
            "diferencia": round(self.diferencia, 2),
            "porcentaje_cambio": round(self.porcentaje_cambio, 2)
        }


@dataclass
class ScenarioSummary:
    """Resumen de escenario."""
    id_escenario: int
    nombre: str
    descripcion: Optional[str]
    horizonte_meses: int
    fecha_creacion: datetime
    num_parametros: int
    num_resultados: int
    total_ingresos_simulados: float = 0.0
    total_utilidad_simulada: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id_escenario": self.id_escenario,
            "nombre": self.nombre,
            "descripcion": self.descripcion,
            "horizonte_meses": self.horizonte_meses,
            "fecha_creacion": self.fecha_creacion.isoformat() if self.fecha_creacion else None,
            "num_parametros": self.num_parametros,
            "num_resultados": self.num_resultados,
            "total_ingresos_simulados": round(self.total_ingresos_simulados, 2),
            "total_utilidad_simulada": round(self.total_utilidad_simulada, 2)
        }


class SimulationService:
    """
    Servicio de simulacion de escenarios.

    RF-05: Simulacion de escenarios
    - RF-05.01: Modificar variables (precio, costo, demanda)
    - RF-05.02: Proyectar impacto financiero
    - RF-05.03: Comparar multiples escenarios
    """

    # RN-05.01: Variacion maxima permitida
    MAX_VARIATION = 50.0  # +/- 50%

    # RN-05.03: Maximo escenarios a comparar
    MAX_SCENARIOS_COMPARE = 5

    # Periodos maximos de simulacion
    MAX_PERIODS = 12

    def __init__(self, db: Session):
        self.db = db
        self.escenario_repo = EscenarioRepository(db)
        self.parametro_repo = ParametroEscenarioRepository(db)
        self.resultado_repo = ResultadoEscenarioRepository(db)
        self.venta_repo = VentaRepository(db)
        self.compra_repo = CompraRepository(db)
        self.producto_repo = ProductoRepository(db)

    def create_scenario(
        self,
        nombre: str,
        descripcion: Optional[str] = None,
        basado_en_historico: bool = True,
        periodos: int = 6,
        creado_por: Optional[int] = None
    ) -> Dict[str, Any]:
        """
        Crea un nuevo escenario de simulacion.

        Args:
            nombre: Nombre del escenario
            descripcion: Descripcion opcional
            basado_en_historico: Si usar datos historicos como base (RN-05.02)
            periodos: Numero de periodos a simular
            creado_por: ID del usuario creador

        Returns:
            Dict con el escenario creado
        """
        # Validar nombre unico
        existing = self.escenario_repo.get_by_nombre(nombre)
        if existing:
            return {
                "success": False,
                "error": f"Ya existe un escenario con el nombre '{nombre}'"
            }

        # Validar periodos
        if periodos > self.MAX_PERIODS:
            periodos = self.MAX_PERIODS

        # Crear escenario (usando columnas reales de BD)
        escenario = Escenario(
            nombre=nombre,
            descripcion=descripcion,
            horizonteMeses=periodos,
            creadoPor=creado_por,
            creadoEn=datetime.now()
        )

        try:
            self.db.add(escenario)
            self.db.commit()
            self.db.refresh(escenario)

            # Si se basa en historico, cargar parametros base
            if basado_en_historico:
                self._initialize_base_parameters(escenario.idEscenario, periodos)

            return {
                "success": True,
                "escenario": {
                    "id_escenario": escenario.idEscenario,
                    "nombre": escenario.nombre,
                    "descripcion": escenario.descripcion,
                    "horizonte_meses": escenario.horizonteMeses,
                    "fecha_creacion": escenario.creadoEn.isoformat() if escenario.creadoEn else None
                },
                "mensaje": "Escenario creado exitosamente"
            }

        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al crear escenario: {str(e)}")
            return {
                "success": False,
                "error": f"Error al crear escenario: {str(e)}"
            }

    def _get_base_by_period(self, horizonte_meses: int, granularidad: str) -> list:
        """
        Devuelve lista de (date, ventas_base, compras_base) para el horizonte de simulacion,
        usando datos historicos reales como base para aportar varianza natural periodo a periodo.
        """
        from collections import defaultdict

        today = date.today()
        hist_inicio = today - timedelta(days=400)   # ~13 meses de historia
        ventas  = self.venta_repo.get_by_rango_fechas(hist_inicio, today)
        compras = self.compra_repo.get_by_rango_fechas(hist_inicio, today)

        def as_date(d):
            return d.date() if hasattr(d, "date") and callable(d.date) else d

        if granularidad == "semanal":
            v_by_week, c_by_week = defaultdict(float), defaultdict(float)
            for v in ventas:
                d = as_date(v.fecha); wk = d - timedelta(days=d.weekday())
                v_by_week[wk] += float(v.total or 0)
            for c in compras:
                d = as_date(c.fecha); wk = d - timedelta(days=d.weekday())
                c_by_week[wk] += float(c.total or 0)

            hist_weeks = sorted(v_by_week.keys())
            avg_v = float(np.mean(list(v_by_week.values()))) if v_by_week else 0
            avg_c = float(np.mean(list(c_by_week.values()))) if c_by_week else avg_v * 0.6

            result = []
            for i in range(horizonte_meses * 4):
                future_wk = today + timedelta(weeks=i + 1)
                same_wk_last_yr = future_wk - timedelta(weeks=52)
                if hist_weeks:
                    nearest = min(hist_weeks, key=lambda w: abs((w - same_wk_last_yr).days))
                    v_val = v_by_week.get(nearest, avg_v)
                    c_val = c_by_week.get(nearest, avg_c)
                else:
                    v_val, c_val = avg_v, avg_c
                result.append((future_wk, max(0.0, v_val), max(0.0, c_val)))
            return result

        elif granularidad == "diaria":
            v_by_day, c_by_day = defaultdict(float), defaultdict(float)
            for v in ventas:
                d = as_date(v.fecha); v_by_day[d] += float(v.total or 0)
            for c in compras:
                d = as_date(c.fecha); c_by_day[d] += float(c.total or 0)

            all_days = sorted(v_by_day.keys())
            avg_v = float(np.mean(list(v_by_day.values()))) if v_by_day else 0
            avg_c = float(np.mean(list(c_by_day.values()))) if c_by_day else avg_v * 0.6

            result = []
            for i in range(horizonte_meses * 30):
                future_day = today + timedelta(days=i + 1)
                same_day_last_yr = future_day - timedelta(days=365)
                if all_days:
                    nearest = min(all_days, key=lambda d2: abs((d2 - same_day_last_yr).days))
                    v_val = v_by_day.get(nearest, avg_v)
                    c_val = c_by_day.get(nearest, avg_c)
                else:
                    v_val, c_val = avg_v, avg_c
                result.append((future_day, max(0.0, v_val), max(0.0, c_val)))
            return result

        else:  # mensual
            v_by_month, c_by_month = defaultdict(float), defaultdict(float)
            for v in ventas:
                d = as_date(v.fecha); mk = date(d.year, d.month, 1)
                v_by_month[mk] += float(v.total or 0)
            for c in compras:
                d = as_date(c.fecha); mk = date(d.year, d.month, 1)
                c_by_month[mk] += float(c.total or 0)

            avg_v = float(np.mean(list(v_by_month.values()))) if v_by_month else 0
            avg_c = float(np.mean(list(c_by_month.values()))) if c_by_month else avg_v * 0.6

            result = []
            for i in range(horizonte_meses):
                future_m = today.replace(day=1) + relativedelta(months=i)
                hist_m   = future_m - relativedelta(years=1)
                v_val = v_by_month.get(hist_m, avg_v)
                c_val = c_by_month.get(hist_m, avg_c)
                result.append((future_m, max(0.0, v_val), max(0.0, c_val)))
            return result

    def _get_product_ventas_by_period(
        self, horizonte_meses: int, granularidad: str, product_ids: list
    ) -> dict:
        """
        Devuelve un dict {producto_id: [(date, ventas_base, compras_base), ...]}
        con datos historicos por producto para los productos indicados.
        """
        from collections import defaultdict

        today = date.today()
        hist_inicio = today - timedelta(days=400)

        # Obtener detalles de ventas y compras filtrados por productos
        ventas_det = (
            self.db.query(DetalleVenta, Venta)
            .join(Venta, DetalleVenta.idVenta == Venta.idVenta)
            .filter(
                DetalleVenta.idProducto.in_(product_ids),
                Venta.fecha >= hist_inicio,
                Venta.fecha <= today
            )
            .all()
        )
        compras_det = (
            self.db.query(DetalleCompra, Compra)
            .join(Compra, DetalleCompra.idCompra == Compra.idCompra)
            .filter(
                DetalleCompra.idProducto.in_(product_ids),
                Compra.fecha >= hist_inicio,
                Compra.fecha <= today
            )
            .all()
        )

        def as_date(d):
            return d.date() if hasattr(d, "date") and callable(d.date) else d

        # Acumular por (producto, periodo)
        def dv_subtotal(dv):
            return float(dv.cantidad or 0) * float(dv.precioUnitario or 0)

        def dc_subtotal(dc):
            return float(dc.subtotal or 0) or float(dc.costo or 0) * float(dc.cantidad or 0)

        if granularidad == "semanal":
            v_map = defaultdict(lambda: defaultdict(float))
            c_map = defaultdict(lambda: defaultdict(float))
            for dv, v in ventas_det:
                d = as_date(v.fecha); wk = d - timedelta(days=d.weekday())
                v_map[dv.idProducto][wk] += dv_subtotal(dv)
            for dc, c in compras_det:
                d = as_date(c.fecha); wk = d - timedelta(days=d.weekday())
                c_map[dc.idProducto][wk] += dc_subtotal(dc)

            result = {}
            for pid in product_ids:
                pv = v_map[pid]; pc = c_map[pid]
                avg_v = float(np.mean(list(pv.values()))) if pv else 0
                avg_c = float(np.mean(list(pc.values()))) if pc else avg_v * 0.6
                periods = []
                hist_weeks = sorted(pv.keys())
                for i in range(horizonte_meses * 4):
                    future_wk = today + timedelta(weeks=i + 1)
                    same_wk_last_yr = future_wk - timedelta(weeks=52)
                    if hist_weeks:
                        nearest = min(hist_weeks, key=lambda w: abs((w - same_wk_last_yr).days))
                        v_val = pv.get(nearest, avg_v)
                        c_val = pc.get(nearest, avg_c)
                    else:
                        v_val, c_val = avg_v, avg_c
                    periods.append((future_wk, max(0.0, v_val), max(0.0, c_val)))
                result[pid] = periods
            return result

        elif granularidad == "diaria":
            v_map = defaultdict(lambda: defaultdict(float))
            c_map = defaultdict(lambda: defaultdict(float))
            for dv, v in ventas_det:
                d = as_date(v.fecha); v_map[dv.idProducto][d] += dv_subtotal(dv)
            for dc, c in compras_det:
                d = as_date(c.fecha); c_map[dc.idProducto][d] += dc_subtotal(dc)

            result = {}
            for pid in product_ids:
                pv = v_map[pid]; pc = c_map[pid]
                avg_v = float(np.mean(list(pv.values()))) if pv else 0
                avg_c = float(np.mean(list(pc.values()))) if pc else avg_v * 0.6
                all_days = sorted(pv.keys())
                periods = []
                for i in range(horizonte_meses * 30):
                    future_day = today + timedelta(days=i + 1)
                    same_day_last_yr = future_day - timedelta(days=365)
                    if all_days:
                        nearest = min(all_days, key=lambda d2: abs((d2 - same_day_last_yr).days))
                        v_val = pv.get(nearest, avg_v); c_val = pc.get(nearest, avg_c)
                    else:
                        v_val, c_val = avg_v, avg_c
                    periods.append((future_day, max(0.0, v_val), max(0.0, c_val)))
                result[pid] = periods
            return result

        else:  # mensual
            v_map = defaultdict(lambda: defaultdict(float))
            c_map = defaultdict(lambda: defaultdict(float))
            for dv, v in ventas_det:
                d = as_date(v.fecha); mk = date(d.year, d.month, 1)
                v_map[dv.idProducto][mk] += dv_subtotal(dv)
            for dc, c in compras_det:
                d = as_date(c.fecha); mk = date(d.year, d.month, 1)
                c_map[dc.idProducto][mk] += dc_subtotal(dc)

            result = {}
            for pid in product_ids:
                pv = v_map[pid]; pc = c_map[pid]
                avg_v = float(np.mean(list(pv.values()))) if pv else 0
                avg_c = float(np.mean(list(pc.values()))) if pc else avg_v * 0.6
                periods = []
                for i in range(horizonte_meses):
                    future_m = today.replace(day=1) + relativedelta(months=i)
                    hist_m = future_m - relativedelta(years=1)
                    v_val = pv.get(hist_m, avg_v); c_val = pc.get(hist_m, avg_c)
                    periods.append((future_m, max(0.0, v_val), max(0.0, c_val)))
                result[pid] = periods
            return result

    def _initialize_base_parameters(self, id_escenario: int, periodos: int):
        """Inicializa parametros base del escenario con datos historicos."""
        # Obtener datos historicos
        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=180)  # 6 meses

        ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
        compras = self.compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        # Calcular promedios mensuales
        ingresos_mensuales = sum(float(v.total or 0) for v in ventas) / 6 if ventas else 0
        costos_mensuales = sum(float(c.total or 0) for c in compras) / 6 if compras else 0

        # Guardar parametros base (valorBase = historico, valorActual = valor a simular)
        parametros_base = [
            ("variacion_precio", 0.0, 0.0),
            ("variacion_costo", 0.0, 0.0),
            ("variacion_demanda", 0.0, 0.0),
            ("ingresos_base_mensual", ingresos_mensuales, ingresos_mensuales),
            ("costos_base_mensual", costos_mensuales, costos_mensuales),
            ("periodos_simulacion", float(periodos), float(periodos))
        ]

        for param, valor_base, valor_actual in parametros_base:
            self.parametro_repo.actualizar_parametro(
                id_escenario, param, valor_actual, valor_base
            )

    def modify_parameters(
        self,
        id_escenario: int,
        parametros: List[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """
        Modifica parametros de un escenario.

        Args:
            id_escenario: ID del escenario
            parametros: Lista de parametros a modificar

        Returns:
            Dict con resultado de la operacion
        """
        # Verificar escenario existe
        escenario = self.escenario_repo.get_by_id(id_escenario)
        if not escenario:
            return {
                "success": False,
                "error": "Escenario no encontrado"
            }

        modificados = 0
        errores = []

        for param in parametros:
            nombre = param.get("parametro") or param.get("nombre")
            valor_actual = param.get("valorActual") or param.get("valor")
            valor_base = param.get("valorBase")
            producto_id = param.get("productoId") or param.get("producto_id")

            # RN-05.01: Validar variacion maxima para parametros de variacion
            if nombre and nombre.startswith("variacion_"):
                try:
                    valor_num = float(str(valor_actual).replace('%', ''))
                    if abs(valor_num) > self.MAX_VARIATION:
                        errores.append(
                            f"Parametro '{nombre}': variacion {valor_num}% excede el maximo de +/-{self.MAX_VARIATION}%"
                        )
                        continue
                except ValueError:
                    pass

            # Actualizar parametro
            try:
                valor_actual_float = float(valor_actual) if valor_actual is not None else 0.0
                valor_base_float = float(valor_base) if valor_base is not None else None
                producto_id_int = int(producto_id) if producto_id is not None else None

                if self.parametro_repo.actualizar_parametro(
                    id_escenario, nombre, valor_actual_float, valor_base_float, producto_id_int
                ):
                    modificados += 1
                else:
                    errores.append(f"Error al actualizar parametro '{nombre}'")
            except (ValueError, TypeError) as e:
                errores.append(f"Valor invalido para parametro '{nombre}': {str(e)}")

        return {
            "success": modificados > 0,
            "id_escenario": id_escenario,
            "parametros_modificados": modificados,
            "errores": errores if errores else None,
            "mensaje": f"Se modificaron {modificados} parametros"
        }

    def run_simulation(
        self,
        id_escenario: int,
        guardar_resultados: bool = True,
        granularidad: str = "semanal"
    ) -> Dict[str, Any]:
        """
        Ejecuta la simulacion de un escenario.

        Args:
            id_escenario: ID del escenario
            guardar_resultados: Si guardar resultados en BD

        Returns:
            Dict con resultados de la simulacion
        """
        # Obtener escenario
        escenario = self.escenario_repo.get_by_id(id_escenario)
        if not escenario:
            return {
                "success": False,
                "error": "Escenario no encontrado"
            }

        # Obtener parametros (solo globales: productoId IS NULL)
        parametros = self.parametro_repo.get_by_escenario(id_escenario)
        params_dict = {}
        for p in parametros:
            if p.productoId is None:
                params_dict[p.parametro] = {
                    "base": float(p.valorBase or 0),
                    "actual": float(p.valorActual or 0)
                }

        # Obtener variaciones globales
        var_precio  = params_dict.get("variacion_precio",  {}).get("actual", 0) / 100
        var_costo   = params_dict.get("variacion_costo",   {}).get("actual", 0) / 100
        var_demanda = params_dict.get("variacion_demanda", {}).get("actual", 0) / 100
        periodos    = int(params_dict.get("periodos_simulacion", {}).get("actual", escenario.horizonteMeses or 6))

        # Cargar overrides por producto
        overrides = self.parametro_repo.get_product_overrides(id_escenario)
        override_map: Dict[int, Dict[str, float]] = {}
        for o in overrides:
            pid = o.productoId
            if pid not in override_map:
                override_map[pid] = {}
            override_map[pid][o.parametro] = float(o.valorActual or 0)

        # Obtener base histórica real por periodo (aporta varianza natural)
        base_periods = self._get_base_by_period(periodos, granularidad)

        # Fallback si no hay datos históricos: usar promedios planos guardados en BD
        if not base_periods:
            ingresos_flat = params_dict.get("ingresos_base_mensual", {}).get("base", 0)
            costos_flat   = params_dict.get("costos_base_mensual",   {}).get("base", 0)
            fecha_inicio  = date.today().replace(day=1)
            base_periods  = [
                (fecha_inicio + relativedelta(months=i), ingresos_flat, costos_flat)
                for i in range(periodos)
            ]

        # Si hay overrides, obtener datos históricos por producto
        product_periods: Dict[int, list] = {}
        if override_map:
            product_periods = self._get_product_ventas_by_period(
                periodos, granularidad, list(override_map.keys())
            )

        # Generar resultados por periodo
        resultados: List[SimulationResult] = []
        total_ingresos = 0
        total_costos   = 0
        total_utilidad = 0

        # Factores globales (constantes; la varianza viene del dato base historico)
        factor_ingresos_global = (1 + var_precio) * (1 + var_demanda)
        factor_costos_global   = (1 + var_costo)  * (1 + var_demanda * 0.7)

        # Pre-calcular factores por producto override (indexados por periodo)
        # Los factores de override se calculan período a período sumando ingresos/costos diferenciados
        override_ingresos_by_period: Dict[int, float] = {}  # {period_idx: ingresos_override}
        override_costos_by_period:   Dict[int, float] = {}

        if override_map:
            for pid, ov_params in override_map.items():
                ov_var_precio  = ov_params.get("variacion_precio",  var_precio  * 100) / 100
                ov_var_costo   = ov_params.get("variacion_costo",   var_costo   * 100) / 100
                ov_var_demanda = ov_params.get("variacion_demanda", var_demanda * 100) / 100
                ov_factor_ing  = (1 + ov_var_precio) * (1 + ov_var_demanda)
                ov_factor_cos  = (1 + ov_var_costo)  * (1 + ov_var_demanda * 0.7)

                prod_data = product_periods.get(pid, [])
                for idx, (_, v_base, c_base) in enumerate(prod_data):
                    override_ingresos_by_period[idx] = (
                        override_ingresos_by_period.get(idx, 0) + v_base * ov_factor_ing
                    )
                    override_costos_by_period[idx] = (
                        override_costos_by_period.get(idx, 0) + c_base * ov_factor_cos
                    )

            # Calcular base de productos con override para restarlo de la base global
            # Asumimos que los overrides representan una fracción del total
            # Si no hay datos de producto, mantenemos sin ajuste

        for idx, (periodo_date, ingresos_base, costos_base) in enumerate(base_periods):
            if override_map and override_ingresos_by_period:
                # Ingresos mixtos: override products + remaining global
                override_ing = override_ingresos_by_period.get(idx, 0)
                override_cos = override_costos_by_period.get(idx, 0)
                # Productos sin override: usar factores globales sobre la base restante
                remaining_ing = max(0.0, ingresos_base - override_ing / factor_ingresos_global) * factor_ingresos_global
                remaining_cos = max(0.0, costos_base  - override_cos  / factor_costos_global)  * factor_costos_global
                ingresos_sim = override_ing + remaining_ing
                costos_sim   = override_cos + remaining_cos
            else:
                ingresos_sim = ingresos_base * factor_ingresos_global
                costos_sim   = costos_base   * factor_costos_global

            utilidad_base = ingresos_base - costos_base
            utilidad_sim  = ingresos_sim  - costos_sim

            margen_base = (utilidad_base / ingresos_base * 100) if ingresos_base > 0 else 0
            margen_sim  = (utilidad_sim  / ingresos_sim  * 100) if ingresos_sim  > 0 else 0

            results_periodo = [
                SimulationResult(
                    periodo=periodo_date,
                    kpi=IndicatorType.INGRESOS.value,
                    valor_base=ingresos_base,
                    valor_simulado=ingresos_sim,
                    diferencia=ingresos_sim - ingresos_base,
                    porcentaje_cambio=((ingresos_sim - ingresos_base) / ingresos_base * 100) if ingresos_base > 0 else 0
                ),
                SimulationResult(
                    periodo=periodo_date,
                    kpi=IndicatorType.COSTOS.value,
                    valor_base=costos_base,
                    valor_simulado=costos_sim,
                    diferencia=costos_sim - costos_base,
                    porcentaje_cambio=((costos_sim - costos_base) / costos_base * 100) if costos_base > 0 else 0
                ),
                SimulationResult(
                    periodo=periodo_date,
                    kpi=IndicatorType.UTILIDAD_BRUTA.value,
                    valor_base=utilidad_base,
                    valor_simulado=utilidad_sim,
                    diferencia=utilidad_sim - utilidad_base,
                    porcentaje_cambio=((utilidad_sim - utilidad_base) / abs(utilidad_base) * 100) if utilidad_base != 0 else 0
                ),
                SimulationResult(
                    periodo=periodo_date,
                    kpi=IndicatorType.MARGEN_BRUTO.value,
                    valor_base=margen_base,
                    valor_simulado=margen_sim,
                    diferencia=margen_sim - margen_base,
                    porcentaje_cambio=margen_sim - margen_base
                )
            ]

            resultados.extend(results_periodo)
            total_ingresos += ingresos_sim
            total_costos   += costos_sim
            total_utilidad += utilidad_sim

        # Guardar resultados si se solicita
        if guardar_resultados:
            # Limpiar resultados anteriores
            self.resultado_repo.eliminar_resultados_escenario(id_escenario)

            # Guardar nuevos resultados
            for res in resultados:
                resultado_db = ResultadoEscenario(
                    idEscenario=id_escenario,
                    periodo=res.periodo,
                    kpi=res.kpi,
                    valor=Decimal(str(round(res.valor_simulado, 2))),
                    confianza=Decimal("0.85")  # Confianza por defecto
                )
                self.db.add(resultado_db)

            self.db.commit()

        # Calcular resumen
        resumen = {
            "total_ingresos_simulados": round(total_ingresos, 2),
            "total_costos_simulados": round(total_costos, 2),
            "total_utilidad_simulada": round(total_utilidad, 2),
            "margen_promedio": round(
                (total_utilidad / total_ingresos * 100) if total_ingresos > 0 else 0, 2
            ),
            "variaciones_aplicadas": {
                "precio": f"{var_precio * 100:+.1f}%",
                "costo": f"{var_costo * 100:+.1f}%",
                "demanda": f"{var_demanda * 100:+.1f}%"
            },
            "overrides_por_producto": len(override_map)
        }

        return {
            "success": True,
            "id_escenario": id_escenario,
            "nombre": escenario.nombre,
            "resultados": [r.to_dict() for r in resultados],
            "resumen": resumen,
            "fecha_ejecucion": datetime.now().isoformat(),
            "advertencia": "Los resultados son de caracter informativo y no constituyen predicciones garantizadas (RN-05.04)."
        }

    def get_scenario(self, id_escenario: int) -> Dict[str, Any]:
        """Obtiene un escenario con sus parametros y resultados."""
        escenario = self.escenario_repo.get_by_id(id_escenario)
        if not escenario:
            return {
                "success": False,
                "error": "Escenario no encontrado"
            }

        parametros = self.parametro_repo.get_by_escenario(id_escenario)
        resultados = self.resultado_repo.get_by_escenario(id_escenario)

        return {
            "success": True,
            "escenario": {
                "id_escenario": escenario.idEscenario,
                "nombre": escenario.nombre,
                "descripcion": escenario.descripcion,
                "horizonte_meses": escenario.horizonteMeses,
                "fecha_creacion": escenario.creadoEn.isoformat() if escenario.creadoEn else None,
                "creado_por": escenario.creadoPor
            },
            "parametros": [
                {
                    "parametro": p.parametro,
                    "valor_base": float(p.valorBase) if p.valorBase else 0,
                    "valor_actual": float(p.valorActual) if p.valorActual else 0
                } for p in parametros
            ],
            "resultados": [
                {
                    "periodo": r.periodo.isoformat() if isinstance(r.periodo, date) else str(r.periodo),
                    "kpi": r.kpi,
                    "valor": float(r.valor) if r.valor else 0,
                    "confianza": float(r.confianza) if r.confianza else None
                } for r in resultados
            ]
        }

    def list_scenarios(
        self,
        usuario_id: Optional[int] = None,
        solo_activos: bool = False
    ) -> Dict[str, Any]:
        """Lista escenarios disponibles."""
        if usuario_id:
            escenarios = self.escenario_repo.get_by_usuario(usuario_id)
        else:
            escenarios = self.escenario_repo.get_all()

        summaries = []
        for esc in escenarios:
            parametros = self.parametro_repo.get_by_escenario(esc.idEscenario)
            resultados = self.resultado_repo.get_by_escenario(esc.idEscenario)

            # Calcular totales de resultados
            total_ingresos = sum(
                float(r.valor or 0) for r in resultados
                if r.kpi == IndicatorType.INGRESOS.value
            )
            total_utilidad = sum(
                float(r.valor or 0) for r in resultados
                if r.kpi == IndicatorType.UTILIDAD_BRUTA.value
            )

            summary = ScenarioSummary(
                id_escenario=esc.idEscenario,
                nombre=esc.nombre,
                descripcion=esc.descripcion,
                horizonte_meses=esc.horizonteMeses or 6,
                fecha_creacion=esc.creadoEn,
                num_parametros=len(parametros),
                num_resultados=len(resultados),
                total_ingresos_simulados=total_ingresos,
                total_utilidad_simulada=total_utilidad
            )
            summaries.append(summary)

        return {
            "success": True,
            "total": len(summaries),
            "escenarios": [s.to_dict() for s in summaries]
        }

    def compare_scenarios(
        self,
        escenario_ids: List[int]
    ) -> Dict[str, Any]:
        """
        Compara multiples escenarios.
        RN-05.03: Maximo 5 escenarios simultaneos.

        Args:
            escenario_ids: Lista de IDs de escenarios a comparar

        Returns:
            Dict con comparacion de escenarios
        """
        # Validar cantidad
        if len(escenario_ids) < 2:
            return {
                "success": False,
                "error": "Se requieren al menos 2 escenarios para comparar"
            }

        if len(escenario_ids) > self.MAX_SCENARIOS_COMPARE:
            return {
                "success": False,
                "error": f"Maximo {self.MAX_SCENARIOS_COMPARE} escenarios para comparar (RN-05.03)"
            }

        # Obtener escenarios
        escenarios_data = []
        for esc_id in escenario_ids:
            escenario = self.escenario_repo.get_by_id(esc_id)
            if not escenario:
                return {
                    "success": False,
                    "error": f"Escenario {esc_id} no encontrado"
                }
            escenarios_data.append(escenario)

        # Obtener resultados de cada escenario
        all_results = {}
        for esc in escenarios_data:
            resultados = self.resultado_repo.get_by_escenario(esc.idEscenario)
            all_results[esc.idEscenario] = resultados

        # Encontrar periodos comunes
        periodos = set()
        for results in all_results.values():
            for r in results:
                periodos.add(r.periodo)
        periodos = sorted(list(periodos))

        # Construir comparacion
        comparaciones = []
        kpis = [IndicatorType.INGRESOS.value, IndicatorType.UTILIDAD_BRUTA.value, IndicatorType.MARGEN_BRUTO.value]

        for kpi in kpis:
            for periodo in periodos:
                valores = {}
                for esc_id, results in all_results.items():
                    for r in results:
                        if r.periodo == periodo and r.kpi == kpi:
                            valores[esc_id] = float(r.valor) if r.valor else 0
                            break
                    if esc_id not in valores:
                        valores[esc_id] = 0

                if valores:
                    mejor = max(valores, key=valores.get)
                    peor = min(valores, key=valores.get)

                    comparaciones.append({
                        "kpi": kpi,
                        "periodo": periodo.isoformat() if isinstance(periodo, date) else str(periodo),
                        "valores": valores,
                        "mejor_escenario": mejor,
                        "peor_escenario": peor
                    })

        # Obtener parámetros de variación por escenario (para mostrar en comparativa)
        parametros_por_escenario = {}
        for esc in escenarios_data:
            params = self.parametro_repo.get_by_escenario(esc.idEscenario)
            variaciones = {}
            for p in params:
                if p.productoId is None:
                    variaciones[p.parametro] = float(p.valorActual or 0)
            parametros_por_escenario[esc.idEscenario] = variaciones

        # Calcular resumen por escenario
        resumen = {}
        for esc in escenarios_data:
            results = all_results[esc.idEscenario]
            total_ingresos = sum(
                float(r.valor or 0) for r in results
                if r.kpi == IndicatorType.INGRESOS.value
            )
            total_utilidad = sum(
                float(r.valor or 0) for r in results
                if r.kpi == IndicatorType.UTILIDAD_BRUTA.value
            )

            resumen[esc.idEscenario] = {
                "nombre": esc.nombre,
                "total_ingresos": round(total_ingresos, 2),
                "total_utilidad": round(total_utilidad, 2),
                "margen_promedio": round(
                    (total_utilidad / total_ingresos * 100) if total_ingresos > 0 else 0, 2
                )
            }

        # Determinar mejor escenario general
        mejor_general = max(resumen.keys(), key=lambda x: resumen[x]["total_utilidad"]) if resumen else None

        return {
            "success": True,
            "escenarios": [
                {
                    "id_escenario": e.idEscenario,
                    "nombre": e.nombre,
                    "horizonte_meses": e.horizonteMeses
                } for e in escenarios_data
            ],
            "comparaciones": comparaciones,
            "resumen_por_escenario": resumen,
            "parametros_por_escenario": parametros_por_escenario,
            "mejor_escenario": {
                "id": mejor_general,
                "nombre": resumen[mejor_general]["nombre"],
                "razon": "Mayor utilidad total simulada"
            } if mejor_general else None
        }

    def save_scenario(self, id_escenario: int) -> Dict[str, Any]:
        """Guarda/confirma un escenario."""
        escenario = self.escenario_repo.get_by_id(id_escenario)
        if not escenario:
            return {
                "success": False,
                "error": "Escenario no encontrado"
            }

        # Verificar que tiene resultados
        resultados = self.resultado_repo.get_by_escenario(id_escenario)
        if not resultados:
            return {
                "success": False,
                "error": "El escenario no tiene resultados. Ejecute la simulacion primero."
            }

        return {
            "success": True,
            "id_escenario": id_escenario,
            "mensaje": "Escenario guardado exitosamente"
        }

    def archive_scenario(self, id_escenario: int) -> Dict[str, Any]:
        """Archiva (elimina) un escenario."""
        if self.escenario_repo.archivar_escenario(id_escenario):
            return {
                "success": True,
                "id_escenario": id_escenario,
                "mensaje": "Escenario archivado exitosamente"
            }
        return {
            "success": False,
            "error": "No se pudo archivar el escenario"
        }

    def delete_scenario(self, id_escenario: int) -> Dict[str, Any]:
        """Elimina un escenario y sus datos relacionados."""
        escenario = self.escenario_repo.get_by_id(id_escenario)
        if not escenario:
            return {
                "success": False,
                "error": "Escenario no encontrado"
            }

        try:
            # Eliminar resultados
            self.resultado_repo.eliminar_resultados_escenario(id_escenario)
            # Eliminar parametros
            self.parametro_repo.eliminar_parametros_escenario(id_escenario)
            # Eliminar escenario
            self.escenario_repo.delete(id_escenario)

            return {
                "success": True,
                "mensaje": f"Escenario '{escenario.nombre}' eliminado exitosamente"
            }

        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al eliminar escenario: {str(e)}")
            return {
                "success": False,
                "error": f"Error al eliminar escenario: {str(e)}"
            }

    def clone_scenario(
        self,
        id_escenario: int,
        nuevo_nombre: str,
        creado_por: Optional[int] = None
    ) -> Dict[str, Any]:
        """Clona un escenario existente."""
        # Obtener escenario original
        original = self.escenario_repo.get_by_id(id_escenario)
        if not original:
            return {
                "success": False,
                "error": "Escenario original no encontrado"
            }

        # Verificar nombre unico
        if self.escenario_repo.get_by_nombre(nuevo_nombre):
            return {
                "success": False,
                "error": f"Ya existe un escenario con el nombre '{nuevo_nombre}'"
            }

        try:
            # Crear nuevo escenario
            nuevo = Escenario(
                nombre=nuevo_nombre,
                descripcion=f"Clonado de: {original.nombre}",
                horizonteMeses=original.horizonteMeses,
                creadoPor=creado_por,
                creadoEn=datetime.now()
            )
            self.db.add(nuevo)
            self.db.commit()
            self.db.refresh(nuevo)

            # Copiar parametros
            parametros_orig = self.parametro_repo.get_by_escenario(id_escenario)
            for param in parametros_orig:
                self.parametro_repo.actualizar_parametro(
                    nuevo.idEscenario,
                    param.parametro,
                    float(param.valorActual or 0),
                    float(param.valorBase or 0),
                    param.productoId
                )

            return {
                "success": True,
                "escenario_original": id_escenario,
                "nuevo_escenario": {
                    "id_escenario": nuevo.idEscenario,
                    "nombre": nuevo.nombre,
                    "horizonte_meses": nuevo.horizonteMeses
                },
                "mensaje": "Escenario clonado exitosamente"
            }

        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al clonar escenario: {str(e)}")
            return {
                "success": False,
                "error": f"Error al clonar escenario: {str(e)}"
            }
