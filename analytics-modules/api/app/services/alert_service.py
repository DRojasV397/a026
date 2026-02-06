"""
Servicio de alertas automaticas.
RF-04: Generacion de alertas basadas en anomalias y predicciones.
"""

import numpy as np
from typing import Optional, Dict, Any, List
from datetime import datetime, date, timedelta
from sqlalchemy.orm import Session
from sqlalchemy import func
from dataclasses import dataclass
from decimal import Decimal
from enum import Enum
import logging
import json

from app.models import Alerta, Prediccion, Venta, DetalleVenta
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
    risk_threshold: float = 15.0  # RN-04.01: Caida > 15%
    opportunity_threshold: float = 20.0  # RN-04.02: Subida > 20%
    anomaly_rate_threshold: float = 5.0  # RN-04.03: > 5% anomalias
    max_active_alerts: int = 10  # RN-04.05: Max 10 alertas simultaneas

    def to_dict(self) -> Dict[str, Any]:
        return {
            "risk_threshold": self.risk_threshold,
            "opportunity_threshold": self.opportunity_threshold,
            "anomaly_rate_threshold": self.anomaly_rate_threshold,
            "max_active_alerts": self.max_active_alerts
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

    def __init__(self, db: Session):
        self.db = db
        self.alerta_repo = AlertaRepository(db)
        self.prediccion_repo = PrediccionRepository(db)
        self.venta_repo = VentaRepository(db)
        self.detector = AnomalyDetector()
        self.config = AlertConfig()

    def configure_thresholds(
        self,
        risk_threshold: Optional[float] = None,
        opportunity_threshold: Optional[float] = None,
        anomaly_rate_threshold: Optional[float] = None
    ) -> Dict[str, Any]:
        """
        Configura umbrales de alertas.
        RF-04.04: Permitir configuracion de umbrales.

        Args:
            risk_threshold: Umbral de riesgo (%)
            opportunity_threshold: Umbral de oportunidad (%)
            anomaly_rate_threshold: Umbral de tasa de anomalias (%)

        Returns:
            Dict con configuracion actualizada
        """
        if risk_threshold is not None:
            self.config.risk_threshold = risk_threshold
            self.detector.set_thresholds(risk_threshold=risk_threshold)

        if opportunity_threshold is not None:
            self.config.opportunity_threshold = opportunity_threshold
            self.detector.set_thresholds(opportunity_threshold=opportunity_threshold)

        if anomaly_rate_threshold is not None:
            self.config.anomaly_rate_threshold = anomaly_rate_threshold

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

        # RN-04.05: Limitar a 10 alertas activas
        alertas_generadas = alertas_generadas[:self.MAX_ACTIVE_ALERTS]

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
            alerta = Alerta(
                idPred=1,
                tipo=AlertType.ANOMALIA.value,
                importancia=AlertImportance.ALTA.value,
                metrica="tasa_anomalias",
                valorActual=Decimal(str(round(anomaly_rate, 2))),
                valorEsperado=Decimal(str(self.config.anomaly_rate_threshold)),
                nivelConfianza=Decimal("0.95"),
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
        return {
            "id_alerta": alerta.idAlerta,
            "tipo": alerta.tipo,
            "importancia": alerta.importancia,
            "metrica": alerta.metrica,
            "valor_actual": float(alerta.valorActual) if alerta.valorActual else 0,
            "valor_esperado": float(alerta.valorEsperado) if alerta.valorEsperado else 0,
            "nivel_confianza": float(alerta.nivelConfianza) if alerta.nivelConfianza else 0,
            "estado": alerta.estado,
            "creada_en": alerta.creadaEn.isoformat() if alerta.creadaEn else None
        }

    def get_active_alerts(self) -> Dict[str, Any]:
        """
        Obtiene alertas activas.
        RN-04.05: Maximo 10 alertas simultaneas.
        """
        alertas = self.alerta_repo.get_activas(limite=self.MAX_ACTIVE_ALERTS)

        return {
            "success": True,
            "total": len(alertas),
            "max_permitidas": self.MAX_ACTIVE_ALERTS,
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
