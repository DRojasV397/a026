"""
Servicio de alertas automaticas.
RF-04: Generacion de alertas basadas en anomalias y predicciones.
"""

import numpy as np
from typing import Optional, Dict, Any, List
from datetime import datetime, date, timedelta
from pathlib import Path
from sqlalchemy.orm import Session
from sqlalchemy import func, desc
from dataclasses import dataclass
from decimal import Decimal
from enum import Enum
import logging
import json

from app.models import Alerta, Prediccion, Venta, DetalleVenta, Compra, DetalleCompra, Producto, VersionModelo
from app.models.prediccion import ModeloPack
from app.repositories import VentaRepository
from app.repositories.alerta_repository import AlertaRepository
from app.repositories.prediccion_repository import PrediccionRepository
from app.analytics.anomaly.detector import AnomalyDetector, AnomalyResult, AnomalyType, Severity

logger = logging.getLogger(__name__)


class AlertType(str, Enum):
    """Tipos de alerta."""
    RIESGO = "Riesgo"
    OPORTUNIDAD = "Oportunidad"
    ANOMALIA = "Anomalia"
    TENDENCIA = "Tendencia"
    UMBRAL = "Umbral"


class AlertImportance(str, Enum):
    """Importancia de alerta."""
    ALTA = "Alta"
    MEDIA = "Media"
    BAJA = "Baja"


class AlertStatus(str, Enum):
    """Estado de alerta."""
    ACTIVA = "Activa"
    LEIDA = "Leida"
    RESUELTA = "Resuelta"
    IGNORADA = "Ignorada"


@dataclass
class AlertConfig:
    """Configuracion de umbrales de alertas."""
    risk_threshold: float = 15.0          # RN-04.01: Caida > 15%
    opportunity_threshold: float = 20.0   # RN-04.02: Subida > 20%
    anomaly_rate_threshold: float = 5.0   # RN-04.03: > 5% anomalias
    max_active_alerts: int = 10           # RN-04.05: Max 10 alertas simultaneas
    margen_minimo: float = 20.0           # Margen bruto minimo esperado (%)
    precio_cambio_threshold: float = 10.0 # % de cambio en precios de compra para alerta
    min_confidence: float = 70.0          # Nivel minimo de confianza para mostrar alertas (%)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "risk_threshold": self.risk_threshold,
            "opportunity_threshold": self.opportunity_threshold,
            "anomaly_rate_threshold": self.anomaly_rate_threshold,
            "max_active_alerts": self.max_active_alerts,
            "margen_minimo": self.margen_minimo,
            "precio_cambio_threshold": self.precio_cambio_threshold,
            "min_confidence": self.min_confidence
        }


class AlertService:
    """
    Servicio de alertas automaticas.

    RF-04: Generacion de alertas
    - RF-04.01: Detectar anomalias y tendencias
    - RF-04.02: Generar alertas prioritarias
    - RF-04.03: Incluir nivel de confianza
    - RF-04.04: Permitir configuracion de umbrales
    """

    # RN-04.05: Maximo alertas simultaneas
    MAX_ACTIVE_ALERTS = 10

    # Archivo de persistencia de configuracion (analytics-modules/api/alert_config.json)
    _CONFIG_FILE: Path = Path(__file__).parent.parent.parent / "alert_config.json"

    def __init__(self, db: Session):
        self.db = db
        self.alerta_repo = AlertaRepository(db)
        self.prediccion_repo = PrediccionRepository(db)
        self.venta_repo = VentaRepository(db)
        self.detector = AnomalyDetector()
        self.config = AlertConfig()
        self._load_config()

    # ─────────────────────────────────────────────────────────────────────────
    #  CONFIG PERSISTENCE
    # ─────────────────────────────────────────────────────────────────────────

    def _load_config(self) -> None:
        """Carga configuracion desde alert_config.json si existe."""
        try:
            if self._CONFIG_FILE.exists():
                data = json.loads(self._CONFIG_FILE.read_text(encoding="utf-8"))
                if "risk_threshold"          in data: self.config.risk_threshold          = float(data["risk_threshold"])
                if "opportunity_threshold"   in data: self.config.opportunity_threshold   = float(data["opportunity_threshold"])
                if "anomaly_rate_threshold"  in data: self.config.anomaly_rate_threshold  = float(data["anomaly_rate_threshold"])
                if "max_active_alerts"       in data: self.config.max_active_alerts       = int(data["max_active_alerts"])
                if "margen_minimo"           in data: self.config.margen_minimo           = float(data["margen_minimo"])
                if "precio_cambio_threshold" in data: self.config.precio_cambio_threshold = float(data["precio_cambio_threshold"])
                if "min_confidence"          in data: self.config.min_confidence          = float(data["min_confidence"])
                # Sincronizar detector con umbrales cargados
                self.detector.set_thresholds(
                    risk_threshold=self.config.risk_threshold,
                    opportunity_threshold=self.config.opportunity_threshold
                )
        except Exception as e:
            logger.warning(f"No se pudo cargar alert_config.json: {e}")

    def _save_config(self) -> None:
        """Persiste configuracion actual en alert_config.json."""
        try:
            self._CONFIG_FILE.write_text(
                json.dumps(self.config.to_dict(), indent=2, ensure_ascii=False),
                encoding="utf-8"
            )
        except Exception as e:
            logger.warning(f"No se pudo guardar alert_config.json: {e}")

    def configure_thresholds(
        self,
        risk_threshold: Optional[float] = None,
        opportunity_threshold: Optional[float] = None,
        anomaly_rate_threshold: Optional[float] = None,
        max_active_alerts: Optional[int] = None,
        margen_minimo: Optional[float] = None,
        precio_cambio_threshold: Optional[float] = None,
        min_confidence: Optional[float] = None
    ) -> Dict[str, Any]:
        """
        Configura umbrales de alertas.
        RF-04.04: Permitir configuracion de umbrales.
        """
        if risk_threshold is not None:
            self.config.risk_threshold = risk_threshold
            self.detector.set_thresholds(risk_threshold=risk_threshold)

        if opportunity_threshold is not None:
            self.config.opportunity_threshold = opportunity_threshold
            self.detector.set_thresholds(opportunity_threshold=opportunity_threshold)

        if anomaly_rate_threshold is not None:
            self.config.anomaly_rate_threshold = anomaly_rate_threshold

        if max_active_alerts is not None:
            self.config.max_active_alerts = max_active_alerts

        if margen_minimo is not None:
            self.config.margen_minimo = margen_minimo

        if precio_cambio_threshold is not None:
            self.config.precio_cambio_threshold = precio_cambio_threshold

        if min_confidence is not None:
            self.config.min_confidence = max(0.0, min(100.0, min_confidence))

        self._save_config()

        return {
            "success": True,
            "config": self.config.to_dict(),
            "mensaje": "Configuracion actualizada"
        }

    def get_config(self) -> Dict[str, Any]:
        """Obtiene configuracion actual de umbrales."""
        return {
            "success": True,
            "config": self.config.to_dict(),
            "detector_thresholds": self.detector.get_alert_thresholds()
        }

    def analyze_sales_for_alerts(
        self,
        fecha_inicio: Optional[date] = None,
        fecha_fin: Optional[date] = None
    ) -> Dict[str, Any]:
        """
        Analiza ventas y genera alertas automaticamente.

        Args:
            fecha_inicio: Fecha inicial del analisis
            fecha_fin: Fecha final del analisis

        Returns:
            Dict con alertas generadas
        """
        if fecha_fin is None:
            fecha_fin = date.today()
        if fecha_inicio is None:
            fecha_inicio = fecha_fin - timedelta(days=90)

        # Obtener ventas del periodo
        ventas = self.venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        if not ventas:
            return {
                "success": False,
                "error": "No hay ventas para analizar en el periodo"
            }

        # Agrupar ventas por dia
        ventas_por_dia = {}
        for venta in ventas:
            dia = venta.fecha.date() if hasattr(venta.fecha, 'date') else venta.fecha
            if dia not in ventas_por_dia:
                ventas_por_dia[dia] = 0
            ventas_por_dia[dia] += float(venta.total or 0)

        # Ordenar y preparar datos
        dias_ordenados = sorted(ventas_por_dia.keys())
        valores = [ventas_por_dia[d] for d in dias_ordenados]
        timestamps = [datetime.combine(d, datetime.min.time()) for d in dias_ordenados]

        # Analizar con detector
        analysis = self.detector.analyze_series(valores, timestamps)

        if not analysis.get("success"):
            return analysis

        # Generar alertas basadas en anomalias
        alertas_generadas = []
        anomalies = analysis.get("anomalies", [])

        for anomaly in anomalies:
            alerta = self._create_alert_from_anomaly(anomaly)
            if alerta:
                alertas_generadas.append(alerta)

        # RN-04.03: Alerta por tasa de anomalias alta
        if analysis.get("high_anomaly_rate_alert"):
            anomaly_rate = analysis.get("anomaly_rate", 0)
            alerta = self._create_anomaly_rate_alert(anomaly_rate, len(valores))
            if alerta:
                alertas_generadas.append(alerta)

        # RN-04.06: Priorizar por impacto
        alertas_generadas = self._prioritize_alerts(alertas_generadas)

        # RN-04.05: Limitar segun config.max_active_alerts
        alertas_generadas = alertas_generadas[:self.config.max_active_alerts]

        return {
            "success": True,
            "periodo": {
                "inicio": fecha_inicio.isoformat(),
                "fin": fecha_fin.isoformat()
            },
            "analisis": {
                "total_dias": len(valores),
                "total_anomalias": analysis.get("total_anomalies", 0),
                "tasa_anomalias": analysis.get("anomaly_rate", 0),
                "estadisticas": analysis.get("statistics", {})
            },
            "alertas_generadas": len(alertas_generadas),
            "alertas": [self._alert_to_dict(a) for a in alertas_generadas]
        }

    def _create_alert_from_anomaly(
        self,
        anomaly: Dict[str, Any]
    ) -> Optional[Alerta]:
        """Crea una alerta a partir de una anomalia detectada."""
        try:
            # Determinar tipo de alerta
            z_score = anomaly.get("z_score", 0)
            anomaly_type = anomaly.get("anomaly_type")

            if z_score < 0 and abs(z_score) > self.config.risk_threshold / 10:
                tipo = AlertType.RIESGO.value
                importancia = AlertImportance.ALTA.value
            elif z_score > 0 and z_score > self.config.opportunity_threshold / 10:
                tipo = AlertType.OPORTUNIDAD.value
                importancia = AlertImportance.MEDIA.value
            elif anomaly_type == "trend_break":
                tipo = AlertType.TENDENCIA.value
                importancia = AlertImportance.MEDIA.value
            else:
                tipo = AlertType.ANOMALIA.value
                importancia = self._map_severity_to_importance(anomaly.get("severity", "Baja"))

            # Crear alerta en BD
            alerta = Alerta(
                idPred=1,  # Placeholder si no hay prediccion asociada
                tipo=tipo,
                importancia=importancia,
                metrica="ventas_diarias",
                valorActual=Decimal(str(round(anomaly.get("value", 0), 2))),
                valorEsperado=Decimal(str(round(anomaly.get("expected_value", 0), 2))),
                nivelConfianza=Decimal(str(round(anomaly.get("confidence", 0), 4))),
                estado=AlertStatus.ACTIVA.value,
                creadaEn=datetime.now()
            )

            self.db.add(alerta)
            self.db.commit()
            self.db.refresh(alerta)

            return alerta

        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al crear alerta: {str(e)}")
            return None

    def _create_anomaly_rate_alert(
        self,
        anomaly_rate: float,
        total_records: int
    ) -> Optional[Alerta]:
        """Crea alerta por tasa de anomalias alta (RN-04.03)."""
        try:
            # Las anomalías se detectan en la serie de ventas → usar precisión de ventas
            best_precision = self._get_best_pack_precisions()["ventas"] / 100.0
            alerta = Alerta(
                idPred=1,
                tipo=AlertType.ANOMALIA.value,
                importancia=AlertImportance.ALTA.value,
                metrica="tasa_anomalias",
                valorActual=Decimal(str(round(anomaly_rate, 2))),
                valorEsperado=Decimal(str(self.config.anomaly_rate_threshold)),
                nivelConfianza=Decimal(str(round(best_precision, 4))),
                estado=AlertStatus.ACTIVA.value,
                creadaEn=datetime.now()
            )

            self.db.add(alerta)
            self.db.commit()
            self.db.refresh(alerta)

            return alerta

        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al crear alerta de tasa: {str(e)}")
            return None

    def _prioritize_alerts(self, alertas: List[Alerta]) -> List[Alerta]:
        """
        Prioriza alertas por impacto.
        RN-04.06: Priorizar por impacto economico.
        """
        def priority_key(alerta):
            # Prioridad por importancia
            imp_order = {"Alta": 0, "Media": 1, "Baja": 2}
            imp = imp_order.get(alerta.importancia, 2)

            # Prioridad por tipo
            tipo_order = {"Riesgo": 0, "Anomalia": 1, "Tendencia": 2, "Oportunidad": 3}
            tipo = tipo_order.get(alerta.tipo, 3)

            # Impacto economico (diferencia valor actual vs esperado)
            impacto = abs(float(alerta.valorActual or 0) - float(alerta.valorEsperado or 0))

            return (imp, tipo, -impacto)

        return sorted(alertas, key=priority_key)

    def _map_severity_to_importance(self, severity: str) -> str:
        """Mapea severidad de anomalia a importancia de alerta."""
        mapping = {
            "Critica": AlertImportance.ALTA.value,
            "Alta": AlertImportance.ALTA.value,
            "Media": AlertImportance.MEDIA.value,
            "Baja": AlertImportance.BAJA.value
        }
        return mapping.get(severity, AlertImportance.BAJA.value)

    def _alert_to_dict(self, alerta: Alerta) -> Dict[str, Any]:
        """Convierte alerta a diccionario."""
        # nivelConfianza se almacena como DECIMAL(5,4) en fracción 0-1 (ej. 0.90 = 90%)
        # Se multiplica por 100 para devolver porcentaje al frontend
        nivel_conf_pct = round(float(alerta.nivelConfianza) * 100, 1) if alerta.nivelConfianza else 0.0
        return {
            "id_alerta": alerta.idAlerta,
            "tipo": alerta.tipo,
            "importancia": alerta.importancia,
            "metrica": alerta.metrica,
            "valor_actual": float(alerta.valorActual) if alerta.valorActual else 0,
            "valor_esperado": float(alerta.valorEsperado) if alerta.valorEsperado else 0,
            "nivel_confianza": nivel_conf_pct,
            "estado": alerta.estado,
            "creada_en": alerta.creadaEn.isoformat() if alerta.creadaEn else None
        }

    def _get_best_pack_precisions(self) -> Dict[str, float]:
        """
        Obtiene las precisiones (R²×100) de AMBOS modelos del mejor pack activo.

        El mejor pack se selecciona por el mayor R² del modelo de ventas.

        Retorna un dict con:
          - 'ventas'  : precisión del modelo de ventas  [50, 99]
          - 'compras' : precisión del modelo de compras [50, 99]
          - 'combined': promedio de ambas               [50, 99]

        En ausencia de packs entrenados los tres valores son 75.0.
        """
        DEFAULT = {"ventas": 75.0, "compras": 75.0, "combined": 75.0}
        try:
            # Pack activo con mayor R² en el modelo de ventas
            best_pack = (
                self.db.query(ModeloPack)
                .join(VersionModelo, ModeloPack.idVersionVentas == VersionModelo.idVersion)
                .filter(
                    ModeloPack.estado == 'Activo',
                    VersionModelo.precision.isnot(None)
                )
                .order_by(desc(VersionModelo.precision))
                .first()
            )
            if not best_pack:
                return DEFAULT

            def _to_pct(version: Optional[VersionModelo]) -> float:
                if version and version.precision:
                    return max(50.0, min(99.0, float(version.precision) * 100))
                return 75.0

            v_ventas = self.db.query(VersionModelo).filter(
                VersionModelo.idVersion == best_pack.idVersionVentas
            ).first()
            v_compras = self.db.query(VersionModelo).filter(
                VersionModelo.idVersion == best_pack.idVersionCompras
            ).first()

            p_ventas  = _to_pct(v_ventas)
            p_compras = _to_pct(v_compras)
            combined  = (p_ventas + p_compras) / 2.0

            return {"ventas": p_ventas, "compras": p_compras, "combined": combined}

        except Exception as e:
            logger.warning(f"No se pudo obtener precisiones del mejor pack: {e}")
        return DEFAULT

    def _get_best_model_precision(self) -> float:
        """Retorna la precisión combinada del mejor pack (promedio ventas+compras)."""
        return self._get_best_pack_precisions()["combined"]

    # ─────────────────────────────────────────────────────────────────────────
    #  HELPER: PERSISTIR ALERTA
    # ─────────────────────────────────────────────────────────────────────────

    def _existe_alerta_reciente(self, metrica: str) -> bool:
        """
        Retorna True si ya existe alguna alerta con esa métrica creada HOY
        (sin importar el estado — incluye Activa, Leida, Resuelta, Ignorada).
        Evita re-crear alertas que el usuario ya resolvió en la misma sesión.
        """
        try:
            hoy_inicio = datetime.combine(date.today(), datetime.min.time())
            count = self.db.query(func.count(Alerta.idAlerta)).filter(
                Alerta.metrica == metrica,
                Alerta.creadaEn >= hoy_inicio
            ).scalar()
            return (count or 0) > 0
        except Exception:
            return False

    def _persistir_alerta(
        self,
        tipo: str,
        importancia: str,
        metrica: str,
        valor_actual: float,
        valor_esperado: float,
        confianza: float
    ) -> Optional[Alerta]:
        """
        Persiste una alerta en la BD si no existe ya una para la misma métrica
        creada HOY (independientemente del estado).  Usa idPred=1 como placeholder.

        Omite la alerta si su nivel de confianza (confianza×100) es menor al
        umbral mínimo configurado (config.min_confidence).
        """
        # confianza es fracción 0-1; min_confidence es porcentaje 0-100
        if (confianza * 100) < self.config.min_confidence:
            logger.debug(
                f"Alerta omitida ({metrica}): confianza {confianza*100:.1f}% "
                f"< umbral minimo {self.config.min_confidence:.0f}%"
            )
            return None

        if self._existe_alerta_reciente(metrica):
            return None  # Ya existe hoy: no duplicar
        try:
            alerta = Alerta(
                idPred=1,
                tipo=tipo,
                importancia=importancia,
                metrica=metrica[:40],
                valorActual=Decimal(str(round(valor_actual, 2))),
                valorEsperado=Decimal(str(round(valor_esperado, 2))),
                nivelConfianza=Decimal(str(round(confianza, 4))),
                estado=AlertStatus.ACTIVA.value,
                creadaEn=datetime.now()
            )
            self.db.add(alerta)
            self.db.commit()
            self.db.refresh(alerta)
            return alerta
        except Exception as e:
            self.db.rollback()
            logger.error(f"Error al persistir alerta ({tipo}/{metrica}): {str(e)}")
            return None

    # ─────────────────────────────────────────────────────────────────────────
    #  EVALUACIÓN: VENTAS (umbral vs histórico)
    # ─────────────────────────────────────────────────────────────────────────

    def _evaluar_alertas_ventas(self, fecha_fin: date) -> List[Alerta]:
        """
        Compara promedio diario de ventas de los últimos 30d vs los 90d anteriores.
        Genera RIESGO si la caída supera risk_threshold, OPORTUNIDAD si la subida
        supera opportunity_threshold.
        """
        reciente_inicio = fecha_fin - timedelta(days=29)
        historico_fin   = reciente_inicio - timedelta(days=1)
        historico_inicio = historico_fin - timedelta(days=89)

        suma_reciente = self.db.query(func.sum(Venta.total)).filter(
            Venta.fecha >= reciente_inicio,
            Venta.fecha <= fecha_fin
        ).scalar()

        suma_historico = self.db.query(func.sum(Venta.total)).filter(
            Venta.fecha >= historico_inicio,
            Venta.fecha <= historico_fin
        ).scalar()

        suma_reciente  = float(suma_reciente  or 0)
        suma_historico = float(suma_historico or 0)

        if suma_historico == 0:
            return []

        avg_reciente  = suma_reciente  / 30.0
        avg_historico = suma_historico / 90.0

        if avg_historico == 0:
            return []

        cambio_pct = ((avg_reciente - avg_historico) / avg_historico) * 100

        # Comparación histórica de ventas → usar precisión del modelo de ventas
        best_precision = self._get_best_pack_precisions()["ventas"] / 100.0

        alertas = []
        if cambio_pct <= -self.config.risk_threshold:
            alerta = self._persistir_alerta(
                tipo=AlertType.RIESGO.value,
                importancia=AlertImportance.ALTA.value,
                metrica="ventas_periodo",
                valor_actual=avg_reciente,
                valor_esperado=avg_historico,
                confianza=best_precision
            )
            if alerta:
                alertas.append(alerta)
        elif cambio_pct >= self.config.opportunity_threshold:
            alerta = self._persistir_alerta(
                tipo=AlertType.OPORTUNIDAD.value,
                importancia=AlertImportance.MEDIA.value,
                metrica="ventas_periodo",
                valor_actual=avg_reciente,
                valor_esperado=avg_historico,
                confianza=best_precision * 0.95  # Oportunidades ligeramente menos confiables
            )
            if alerta:
                alertas.append(alerta)

        return alertas

    # ─────────────────────────────────────────────────────────────────────────
    #  EVALUACIÓN: MARGEN BRUTO
    # ─────────────────────────────────────────────────────────────────────────

    def _evaluar_alertas_margen(
        self, fecha_inicio: date, fecha_fin: date
    ) -> List[Alerta]:
        """
        Calcula el margen bruto del período (ingresos - costos) / ingresos.
        Genera RIESGO si margen < margen_minimo, OPORTUNIDAD si margen > 40%.
        Requiere que Producto.costoUnitario esté poblado.
        """
        result = self.db.query(
            func.sum(DetalleVenta.cantidad * DetalleVenta.precioUnitario).label("ingresos"),
            func.sum(DetalleVenta.cantidad * Producto.costoUnitario).label("costos")
        ).join(Venta, DetalleVenta.idVenta == Venta.idVenta
        ).join(Producto, DetalleVenta.idProducto == Producto.idProducto
        ).filter(
            Venta.fecha >= fecha_inicio,
            Venta.fecha <= fecha_fin,
            Producto.costoUnitario.isnot(None)
        ).first()

        if not result or not result.ingresos or float(result.ingresos) == 0:
            return []

        ingresos = float(result.ingresos)
        costos   = float(result.costos or 0)
        margen   = ((ingresos - costos) / ingresos) * 100

        # Margen depende de ingresos (ventas) y costos (compras) → usar precisión combinada
        best_precision = self._get_best_pack_precisions()["combined"] / 100.0

        alertas = []
        if margen < self.config.margen_minimo:
            importancia = (
                AlertImportance.ALTA.value
                if margen < self.config.margen_minimo * 0.75
                else AlertImportance.MEDIA.value
            )
            alerta = self._persistir_alerta(
                tipo=AlertType.RIESGO.value,
                importancia=importancia,
                metrica="margen_bruto",
                valor_actual=margen,
                valor_esperado=self.config.margen_minimo,
                confianza=best_precision
            )
            if alerta:
                alertas.append(alerta)
        elif margen > 40.0:
            alerta = self._persistir_alerta(
                tipo=AlertType.OPORTUNIDAD.value,
                importancia=AlertImportance.MEDIA.value,
                metrica="margen_bruto",
                valor_actual=margen,
                valor_esperado=self.config.margen_minimo,
                confianza=best_precision * 0.95
            )
            if alerta:
                alertas.append(alerta)

        return alertas

    # ─────────────────────────────────────────────────────────────────────────
    #  EVALUACIÓN: PRECIOS DE COMPRA (alzas / bajas)
    # ─────────────────────────────────────────────────────────────────────────

    def _evaluar_alertas_precios_compras(self, fecha_fin: date) -> List[Alerta]:
        """
        Compara el costo promedio por unidad de cada producto en los últimos 30d
        vs los 30d anteriores. Genera RIESGO para alzas y OPORTUNIDAD para bajas
        que superen precio_cambio_threshold. Limita a las 3 más impactantes.
        """
        periodo_dias    = 30
        reciente_fin    = fecha_fin
        reciente_inicio = fecha_fin  - timedelta(days=periodo_dias - 1)
        anterior_fin    = reciente_inicio - timedelta(days=1)
        anterior_inicio = anterior_fin    - timedelta(days=periodo_dias - 1)

        def _costos_por_producto(d_inicio, d_fin):
            rows = self.db.query(
                DetalleCompra.idProducto,
                Producto.nombre,
                func.avg(DetalleCompra.costo).label("avg_costo"),
                func.sum(DetalleCompra.cantidad).label("total_cant")
            ).join(Compra, DetalleCompra.idCompra == Compra.idCompra
            ).join(Producto, DetalleCompra.idProducto == Producto.idProducto
            ).filter(
                Compra.fecha >= d_inicio,
                Compra.fecha <= d_fin
            ).group_by(DetalleCompra.idProducto, Producto.nombre).all()
            return {r.idProducto: r for r in rows}

        costos_rec = _costos_por_producto(reciente_inicio, reciente_fin)
        costos_ant = _costos_por_producto(anterior_inicio, anterior_fin)

        cambios = []
        for id_prod, rec in costos_rec.items():
            if id_prod not in costos_ant:
                continue
            ant = costos_ant[id_prod]
            avg_rec = float(rec.avg_costo or 0)
            avg_ant = float(ant.avg_costo or 0)
            if avg_ant == 0:
                continue
            cambio_pct = ((avg_rec - avg_ant) / avg_ant) * 100
            if abs(cambio_pct) >= self.config.precio_cambio_threshold:
                cambios.append({
                    "nombre":     rec.nombre or f"Producto {id_prod}",
                    "cambio_pct": cambio_pct,
                    "avg_rec":    avg_rec,
                    "avg_ant":    avg_ant,
                    "volumen":    float(rec.total_cant or 1)
                })

        # Priorizar por impacto económico (|cambio%| × volumen)
        cambios.sort(key=lambda x: abs(x["cambio_pct"]) * x["volumen"], reverse=True)
        cambios = cambios[:3]

        # Alertas de precios de compra → usar precisión del modelo de compras
        best_precision = self._get_best_pack_precisions()["compras"] / 100.0

        alertas = []
        for c in cambios:
            nombre_corto = c["nombre"][:30]
            metrica = f"precio: {nombre_corto}"[:40]
            tipo = AlertType.RIESGO.value if c["cambio_pct"] > 0 else AlertType.OPORTUNIDAD.value
            importancia = AlertImportance.MEDIA.value if c["cambio_pct"] > 0 else AlertImportance.BAJA.value
            alerta = self._persistir_alerta(
                tipo=tipo,
                importancia=importancia,
                metrica=metrica,
                valor_actual=c["avg_rec"],
                valor_esperado=c["avg_ant"],
                confianza=best_precision * 0.95
            )
            if alerta:
                alertas.append(alerta)

        return alertas

    # ─────────────────────────────────────────────────────────────────────────
    #  EVALUACIÓN: COMPRAS PREDICHAS vs REALES (Pack de modelos)
    # ─────────────────────────────────────────────────────────────────────────

    def _get_best_active_pack(self, user_id: Optional[int] = None) -> Optional[ModeloPack]:
        """
        Retorna el pack activo con mejor rendimiento (mayor R² del modelo de ventas).
        Si ningún pack tiene precisión registrada, devuelve el más reciente como fallback.
        """
        try:
            q = (
                self.db.query(ModeloPack)
                .join(VersionModelo, ModeloPack.idVersionVentas == VersionModelo.idVersion)
                .filter(
                    ModeloPack.estado == 'Activo',
                    VersionModelo.precision.isnot(None)
                )
            )
            if user_id is not None:
                q = q.filter(ModeloPack.creadoPor == user_id)
            result = q.order_by(desc(VersionModelo.precision)).first()
            if result:
                return result
            # Fallback: ningún pack tiene precisión registrada → el más reciente
            q2 = self.db.query(ModeloPack).filter(ModeloPack.estado == 'Activo')
            if user_id is not None:
                q2 = q2.filter(ModeloPack.creadoPor == user_id)
            return q2.order_by(desc(ModeloPack.creadoEn)).first()
        except Exception as e:
            logger.warning(f"No se pudo obtener mejor pack activo: {e}")
            return None

    def _evaluar_alertas_compras_predichas(
        self,
        fecha_fin: date,
        pack: ModeloPack
    ) -> List[Alerta]:
        """
        Compara compras reales de los últimos 30d vs las compras predichas para ese periodo.

        Flujo:
        1. Cargar el modelo de compras del pack
        2. Generar forecast de 30 días
        3. Sumar compras reales del mismo periodo
        4. Si desviación > risk_threshold  → RIESGO "compras_predichas"
           Si desviación < -opportunity_threshold → OPORTUNIDAD (ahorro inesperado)
        """
        from app.services.prediction_service import PredictionService, _global_trained_models, _global_model_last_access
        import os, pickle, time

        # Resolver model_key del modelo de compras del pack
        try:
            version_compras = self.db.query(VersionModelo).filter(
                VersionModelo.idVersion == pack.idVersionCompras
            ).first()
            if not version_compras:
                return []

            from app.models.prediccion import Modelo
            modelo_compras = self.db.query(Modelo).filter(
                Modelo.idModelo == version_compras.idModelo
            ).first()
            if not modelo_compras or not modelo_compras.modelKey:
                return []

            compras_key = modelo_compras.modelKey
        except Exception as e:
            logger.warning(f"No se pudo resolver model_key de compras: {e}")
            return []

        # Cargar modelo de compras (primero en memoria global, luego disco)
        from app.analytics.models.multiple_regression import MultipleRegressionModel

        compras_model = _global_trained_models.get(compras_key)
        if compras_model is None:
            model_path = os.path.join(PredictionService.MODELS_DIR, f"{compras_key}.pkl")
            if not os.path.exists(model_path):
                logger.info(f"Modelo de compras del pack no encontrado en disco: {model_path}")
                return []
            try:
                compras_model = MultipleRegressionModel(target_column='total', date_column='fecha')
                compras_model.load(model_path)
                _global_trained_models[compras_key] = compras_model
                _global_model_last_access[compras_key] = time.time()
            except Exception as e:
                logger.warning(f"Error cargando modelo de compras: {e}")
                return []

        # Generar forecast de 30 días
        try:
            result = compras_model.forecast(periods=30)
            forecast_values = result.predictions
            if not forecast_values:
                return []
            total_predicho = float(np.sum(forecast_values))
        except Exception as e:
            logger.warning(f"Error en forecast de compras del pack: {e}")
            return []

        # Compras reales de los últimos 30 días
        reciente_inicio = fecha_fin - timedelta(days=29)
        suma_real = self.db.query(func.sum(Compra.total)).filter(
            Compra.fecha >= reciente_inicio,
            Compra.fecha <= fecha_fin
        ).scalar()
        total_real = float(suma_real or 0)

        if total_real == 0 or total_predicho == 0:
            return []

        desviacion_pct = ((total_real - total_predicho) / total_predicho) * 100
        # Comparación real vs predicción del modelo de compras → usar precisión de compras
        best_precision = self._get_best_pack_precisions()["compras"] / 100.0

        alertas = []
        if desviacion_pct >= self.config.risk_threshold:
            # Compras reales superan las predichas → posible exceso de inventario / gasto inesperado
            alerta = self._persistir_alerta(
                tipo=AlertType.RIESGO.value,
                importancia=AlertImportance.MEDIA.value,
                metrica="compras_predichas",
                valor_actual=total_real,
                valor_esperado=total_predicho,
                confianza=best_precision * 0.90
            )
            if alerta:
                alertas.append(alerta)
        elif desviacion_pct <= -self.config.opportunity_threshold:
            # Compras reales mucho menores a las predichas → ahorro / eficiencia
            alerta = self._persistir_alerta(
                tipo=AlertType.OPORTUNIDAD.value,
                importancia=AlertImportance.BAJA.value,
                metrica="compras_predichas",
                valor_actual=total_real,
                valor_esperado=total_predicho,
                confianza=best_precision * 0.90
            )
            if alerta:
                alertas.append(alerta)

        return alertas

    def _evaluar_alertas_ventas_predichas(
        self,
        fecha_fin: date,
        pack: ModeloPack
    ) -> List[Alerta]:
        """
        Compara ventas reales de los últimos 30d vs las ventas predichas para ese periodo.

        Flujo:
        1. Cargar el modelo de ventas del pack
        2. Generar forecast de 30 días
        3. Sumar ventas reales del mismo periodo
        4. Si desviación <= -risk_threshold     → RIESGO "ventas_predichas" (ventas por debajo de lo predicho)
           Si desviación >= opportunity_threshold → OPORTUNIDAD (ventas superan predicciones)
        """
        from app.services.prediction_service import PredictionService, _global_trained_models, _global_model_last_access
        import os, pickle, time

        # Resolver model_key del modelo de ventas del pack
        try:
            version_ventas = self.db.query(VersionModelo).filter(
                VersionModelo.idVersion == pack.idVersionVentas
            ).first()
            if not version_ventas:
                return []

            from app.models.prediccion import Modelo
            modelo_ventas = self.db.query(Modelo).filter(
                Modelo.idModelo == version_ventas.idModelo
            ).first()
            if not modelo_ventas or not modelo_ventas.modelKey:
                return []

            ventas_key = modelo_ventas.modelKey
        except Exception as e:
            logger.warning(f"No se pudo resolver model_key de ventas: {e}")
            return []

        # Cargar modelo de ventas (primero en memoria global, luego disco)
        from app.analytics.models.multiple_regression import MultipleRegressionModel

        ventas_model = _global_trained_models.get(ventas_key)
        if ventas_model is None:
            model_path = os.path.join(PredictionService.MODELS_DIR, f"{ventas_key}.pkl")
            if not os.path.exists(model_path):
                logger.info(f"Modelo de ventas del pack no encontrado en disco: {model_path}")
                return []
            try:
                ventas_model = MultipleRegressionModel(target_column='total', date_column='fecha')
                ventas_model.load(model_path)
                _global_trained_models[ventas_key] = ventas_model
                _global_model_last_access[ventas_key] = time.time()
            except Exception as e:
                logger.warning(f"Error cargando modelo de ventas: {e}")
                return []

        # Generar forecast de 30 días
        try:
            result = ventas_model.forecast(periods=30)
            forecast_values = result.predictions
            if not forecast_values:
                return []
            total_predicho = float(np.sum(forecast_values))
        except Exception as e:
            logger.warning(f"Error en forecast de ventas del pack: {e}")
            return []

        # Ventas reales de los últimos 30 días
        reciente_inicio = fecha_fin - timedelta(days=29)
        suma_real = self.db.query(func.sum(Venta.total)).filter(
            Venta.fecha >= reciente_inicio,
            Venta.fecha <= fecha_fin
        ).scalar()
        total_real = float(suma_real or 0)

        if total_real == 0 or total_predicho == 0:
            return []

        desviacion_pct = ((total_real - total_predicho) / total_predicho) * 100
        # Comparación real vs predicción del modelo de ventas → usar precisión de ventas
        best_precision = self._get_best_pack_precisions()["ventas"] / 100.0

        alertas = []
        if desviacion_pct <= -self.config.risk_threshold:
            # Ventas reales muy por debajo de las predichas → bajo rendimiento
            alerta = self._persistir_alerta(
                tipo=AlertType.RIESGO.value,
                importancia=AlertImportance.MEDIA.value,
                metrica="ventas_predichas",
                valor_actual=total_real,
                valor_esperado=total_predicho,
                confianza=best_precision * 0.90
            )
            if alerta:
                alertas.append(alerta)
        elif desviacion_pct >= self.config.opportunity_threshold:
            # Ventas reales superan las predichas → mejor mes de lo esperado
            alerta = self._persistir_alerta(
                tipo=AlertType.OPORTUNIDAD.value,
                importancia=AlertImportance.BAJA.value,
                metrica="ventas_predichas",
                valor_actual=total_real,
                valor_esperado=total_predicho,
                confianza=best_precision * 0.90
            )
            if alerta:
                alertas.append(alerta)

        return alertas

    # ─────────────────────────────────────────────────────────────────────────
    #  EVALUACIÓN INTEGRAL (combina todos los tipos)
    # ─────────────────────────────────────────────────────────────────────────

    def evaluate_all_alerts(
        self,
        fecha_inicio: Optional[date] = None,
        fecha_fin: Optional[date] = None,
        user_id: Optional[int] = None
    ) -> Dict[str, Any]:
        """
        Ejecuta todas las evaluaciones de alertas en secuencia:
        1. Anomalías en ventas (AnomalyDetector)
        2. Umbral de ventas recientes vs histórico
        3. Margen bruto del período
        4. Alzas/bajas en precios de compra
        """
        if fecha_fin is None:
            fecha_fin = date.today()
        if fecha_inicio is None:
            fecha_inicio = fecha_fin - timedelta(days=90)

        resultados: Dict[str, Any] = {}

        # 1. Anomalías en ventas
        try:
            r = self.analyze_sales_for_alerts(fecha_inicio, fecha_fin)
            resultados["anomalias"] = {
                "success": r.get("success", False),
                "alertas_generadas": r.get("alertas_generadas", 0)
            }
        except Exception as e:
            logger.error(f"Error en anomalias: {e}")
            resultados["anomalias"] = {"success": False, "error": str(e)}

        # 2. Ventas vs histórico
        try:
            alertas = self._evaluar_alertas_ventas(fecha_fin)
            resultados["ventas"] = {"success": True, "alertas_generadas": len(alertas)}
        except Exception as e:
            logger.error(f"Error en ventas: {e}")
            resultados["ventas"] = {"success": False, "error": str(e)}

        # 3. Margen bruto
        try:
            alertas = self._evaluar_alertas_margen(fecha_inicio, fecha_fin)
            resultados["margen"] = {"success": True, "alertas_generadas": len(alertas)}
        except Exception as e:
            logger.error(f"Error en margen: {e}")
            resultados["margen"] = {"success": False, "error": str(e)}

        # 4. Precios de compra
        try:
            alertas = self._evaluar_alertas_precios_compras(fecha_fin)
            resultados["precios_compra"] = {"success": True, "alertas_generadas": len(alertas)}
        except Exception as e:
            logger.error(f"Error en precios_compra: {e}")
            resultados["precios_compra"] = {"success": False, "error": str(e)}

        # 5. Compras predichas (solo si hay pack activo)
        pack = None
        try:
            pack = self._get_best_active_pack(user_id=user_id)
            if pack:
                alertas = self._evaluar_alertas_compras_predichas(fecha_fin, pack)
                resultados["compras_predichas"] = {"success": True, "alertas_generadas": len(alertas)}
            else:
                resultados["compras_predichas"] = {"success": True, "alertas_generadas": 0, "info": "Sin pack activo"}
        except Exception as e:
            logger.error(f"Error en compras_predichas: {e}")
            resultados["compras_predichas"] = {"success": False, "error": str(e)}

        # 6. Ventas predichas (solo si hay pack activo)
        try:
            if pack:
                alertas = self._evaluar_alertas_ventas_predichas(fecha_fin, pack)
                resultados["ventas_predichas"] = {"success": True, "alertas_generadas": len(alertas)}
            else:
                resultados["ventas_predichas"] = {"success": True, "alertas_generadas": 0, "info": "Sin pack activo"}
        except Exception as e:
            logger.error(f"Error en ventas_predichas: {e}")
            resultados["ventas_predichas"] = {"success": False, "error": str(e)}

        total = sum(v.get("alertas_generadas", 0) for v in resultados.values())

        return {
            "success": True,
            "periodo": {
                "inicio": fecha_inicio.isoformat(),
                "fin":    fecha_fin.isoformat()
            },
            "evaluaciones": resultados,
            "total_alertas_generadas": total
        }

    def get_active_alerts(self) -> Dict[str, Any]:
        """
        Obtiene alertas activas.
        RN-04.05: Maximo alertas simultaneas segun config.
        Solo incluye alertas con nivel de confianza >= min_confidence.
        """
        limite = self.config.max_active_alerts
        alertas = self.alerta_repo.get_activas(limite=limite)

        # Filtrar por umbral minimo de confianza
        if self.config.min_confidence > 0:
            min_frac = self.config.min_confidence / 100.0
            alertas = [a for a in alertas if float(a.nivelConfianza or 0) >= min_frac]

        return {
            "success": True,
            "total": len(alertas),
            "max_permitidas": limite,
            "alertas": [self._alert_to_dict(a) for a in alertas],
            "por_tipo": self.alerta_repo.contar_por_tipo(),
            "por_importancia": self.alerta_repo.contar_por_importancia()
        }

    def get_alert_history(
        self,
        fecha_inicio: Optional[date] = None,
        fecha_fin: Optional[date] = None,
        tipo: Optional[str] = None,
        importancia: Optional[str] = None
    ) -> Dict[str, Any]:
        """Obtiene historial de alertas."""
        if fecha_fin is None:
            fecha_fin = datetime.now()
        else:
            fecha_fin = datetime.combine(fecha_fin, datetime.max.time())

        if fecha_inicio is None:
            fecha_inicio = fecha_fin - timedelta(days=30)
        else:
            fecha_inicio = datetime.combine(fecha_inicio, datetime.min.time())

        alertas = self.alerta_repo.get_historial(fecha_inicio, fecha_fin)

        # Filtrar por tipo si se especifica
        if tipo:
            alertas = [a for a in alertas if a.tipo == tipo]

        # Filtrar por importancia si se especifica
        if importancia:
            alertas = [a for a in alertas if a.importancia == importancia]

        # Filtrar por umbral minimo de confianza
        if self.config.min_confidence > 0:
            min_frac = self.config.min_confidence / 100.0
            alertas = [a for a in alertas if float(a.nivelConfianza or 0) >= min_frac]

        return {
            "success": True,
            "periodo": {
                "inicio": fecha_inicio.isoformat(),
                "fin": fecha_fin.isoformat()
            },
            "total": len(alertas),
            "alertas": [self._alert_to_dict(a) for a in alertas]
        }

    def mark_as_read(self, id_alerta: int) -> Dict[str, Any]:
        """Marca una alerta como leida."""
        alerta = self.alerta_repo.get_by_id(id_alerta)
        if not alerta:
            return {
                "success": False,
                "error": "Alerta no encontrada"
            }

        estado_anterior = alerta.estado

        if self.alerta_repo.marcar_como_leida(id_alerta):
            return {
                "success": True,
                "id_alerta": id_alerta,
                "estado_anterior": estado_anterior,
                "estado_nuevo": AlertStatus.LEIDA.value,
                "mensaje": "Alerta marcada como leida"
            }

        return {
            "success": False,
            "error": "No se pudo actualizar la alerta"
        }

    def change_status(
        self,
        id_alerta: int,
        nuevo_estado: str
    ) -> Dict[str, Any]:
        """Cambia el estado de una alerta."""
        alerta = self.alerta_repo.get_by_id(id_alerta)
        if not alerta:
            return {
                "success": False,
                "error": "Alerta no encontrada"
            }

        # Validar estado
        estados_validos = [s.value for s in AlertStatus]
        if nuevo_estado not in estados_validos:
            return {
                "success": False,
                "error": f"Estado invalido. Valores validos: {estados_validos}"
            }

        estado_anterior = alerta.estado

        if self.alerta_repo.cambiar_estado(id_alerta, nuevo_estado):
            return {
                "success": True,
                "id_alerta": id_alerta,
                "estado_anterior": estado_anterior,
                "estado_nuevo": nuevo_estado,
                "mensaje": "Estado actualizado"
            }

        return {
            "success": False,
            "error": "No se pudo actualizar el estado"
        }

    def get_summary(self) -> Dict[str, Any]:
        """Obtiene resumen de alertas."""
        resumen = self.alerta_repo.get_resumen()
        alertas_recientes = self.alerta_repo.get_activas(limite=5)

        return {
            "success": True,
            "resumen": {
                "total_activas": resumen.get("totalActivas", 0),
                "por_tipo": resumen.get("porTipo", {}),
                "por_importancia": resumen.get("porImportancia", {}),
                "limite_maximo": self.MAX_ACTIVE_ALERTS
            },
            "alertas_recientes": [self._alert_to_dict(a) for a in alertas_recientes],
            "config": self.config.to_dict()
        }

    def check_prediction_alerts(
        self,
        id_prediccion: int
    ) -> Dict[str, Any]:
        """
        Verifica si una prediccion genera alertas.

        Args:
            id_prediccion: ID de la prediccion

        Returns:
            Dict con alertas generadas
        """
        prediccion = self.prediccion_repo.get_by_id(id_prediccion)
        if not prediccion:
            return {
                "success": False,
                "error": "Prediccion no encontrada"
            }

        # Obtener valor real actual para comparar
        # (Esto dependeria de la entidad predicha)
        valor_predicho = float(prediccion.valorPredicho) if prediccion.valorPredicho else 0
        confianza = float(prediccion.confianza) if prediccion.confianza else 0

        alertas = []

        # Crear alerta si la confianza es baja
        if confianza < 0.7:
            alerta = Alerta(
                idPred=id_prediccion,
                tipo=AlertType.UMBRAL.value,
                importancia=AlertImportance.MEDIA.value,
                metrica="confianza_prediccion",
                valorActual=Decimal(str(confianza)),
                valorEsperado=Decimal("0.7"),
                nivelConfianza=Decimal(str(confianza)),
                estado=AlertStatus.ACTIVA.value,
                creadaEn=datetime.now()
            )
            self.db.add(alerta)
            self.db.commit()
            self.db.refresh(alerta)
            alertas.append(alerta)

        return {
            "success": True,
            "prediccion_id": id_prediccion,
            "valor_predicho": valor_predicho,
            "confianza": confianza,
            "alertas_generadas": len(alertas),
            "alertas": [self._alert_to_dict(a) for a in alertas]
        }

    def delete_alert(self, id_alerta: int) -> Dict[str, Any]:
        """Elimina una alerta."""
        alerta = self.alerta_repo.get_by_id(id_alerta)
        if not alerta:
            return {
                "success": False,
                "error": "Alerta no encontrada"
            }

        try:
            self.alerta_repo.delete(id_alerta)
            return {
                "success": True,
                "mensaje": f"Alerta {id_alerta} eliminada"
            }
        except Exception as e:
            return {
                "success": False,
                "error": f"Error al eliminar: {str(e)}"
            }
