"""
Detector de anomalias para el sistema de alertas.
RF-04: Generacion de alertas automaticas.
"""

import numpy as np
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass, field
from enum import Enum
from datetime import datetime, date, timedelta
import logging

logger = logging.getLogger(__name__)


class AnomalyType(str, Enum):
    """Tipos de anomalia detectables."""
    OUTLIER = "outlier"  # Valor atipico
    TREND_BREAK = "trend_break"  # Ruptura de tendencia
    SEASONALITY_DEVIATION = "seasonality_deviation"  # Desviacion estacional
    SUDDEN_CHANGE = "sudden_change"  # Cambio repentino
    MISSING_DATA = "missing_data"  # Datos faltantes


class Severity(str, Enum):
    """Severidad de la anomalia."""
    LOW = "Baja"
    MEDIUM = "Media"
    HIGH = "Alta"
    CRITICAL = "Critica"


@dataclass
class AnomalyResult:
    """Resultado de deteccion de anomalia."""
    is_anomaly: bool
    anomaly_type: Optional[AnomalyType] = None
    severity: Severity = Severity.LOW
    value: float = 0.0
    expected_value: float = 0.0
    deviation: float = 0.0  # Desviacion en unidades de sigma
    z_score: float = 0.0
    confidence: float = 0.0  # 0 a 1
    description: str = ""
    index: Optional[int] = None
    timestamp: Optional[datetime] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "is_anomaly": self.is_anomaly,
            "anomaly_type": self.anomaly_type.value if self.anomaly_type else None,
            "severity": self.severity.value,
            "value": round(self.value, 2),
            "expected_value": round(self.expected_value, 2),
            "deviation": round(self.deviation, 2),
            "z_score": round(self.z_score, 2),
            "confidence": round(self.confidence, 4),
            "description": self.description,
            "index": self.index,
            "timestamp": self.timestamp.isoformat() if self.timestamp else None
        }


class AnomalyDetector:
    """
    Detector de anomalias usando metodos estadisticos.

    Metodos implementados:
    - Z-Score para deteccion de outliers
    - IQR (Interquartile Range)
    - Deteccion de cambios repentinos
    - Analisis de tendencia
    """

    # Umbrales por defecto
    DEFAULT_Z_THRESHOLD = 2.5  # Sigma para considerar anomalia
    DEFAULT_IQR_MULTIPLIER = 1.5
    DEFAULT_CHANGE_THRESHOLD = 0.15  # 15% cambio para riesgo (RN-04.01)
    DEFAULT_OPPORTUNITY_THRESHOLD = 0.20  # 20% subida para oportunidad (RN-04.02)
    DEFAULT_ANOMALY_RATE_THRESHOLD = 0.05  # 5% transacciones anomalas (RN-04.03)

    def __init__(
        self,
        z_threshold: float = DEFAULT_Z_THRESHOLD,
        iqr_multiplier: float = DEFAULT_IQR_MULTIPLIER,
        change_threshold: float = DEFAULT_CHANGE_THRESHOLD,
        opportunity_threshold: float = DEFAULT_OPPORTUNITY_THRESHOLD
    ):
        self.z_threshold = z_threshold
        self.iqr_multiplier = iqr_multiplier
        self.change_threshold = change_threshold
        self.opportunity_threshold = opportunity_threshold

    def detect_outliers_zscore(
        self,
        data: List[float],
        timestamps: Optional[List[datetime]] = None
    ) -> List[AnomalyResult]:
        """
        Detecta outliers usando Z-Score.

        Args:
            data: Lista de valores
            timestamps: Lista opcional de timestamps

        Returns:
            Lista de anomalias detectadas
        """
        if len(data) < 3:
            return []

        arr = np.array(data)
        mean = np.mean(arr)
        std = np.std(arr)

        if std == 0:
            return []

        anomalies = []
        z_scores = (arr - mean) / std

        for i, (value, z) in enumerate(zip(data, z_scores)):
            if abs(z) > self.z_threshold:
                severity = self._calculate_severity(abs(z))
                confidence = min(1.0, abs(z) / (self.z_threshold * 2))

                anomaly = AnomalyResult(
                    is_anomaly=True,
                    anomaly_type=AnomalyType.OUTLIER,
                    severity=severity,
                    value=value,
                    expected_value=mean,
                    deviation=abs(value - mean),
                    z_score=z,
                    confidence=confidence,
                    description=f"Valor atipico detectado: {value:.2f} (esperado ~{mean:.2f}, z={z:.2f})",
                    index=i,
                    timestamp=timestamps[i] if timestamps and i < len(timestamps) else None
                )
                anomalies.append(anomaly)

        return anomalies

    def detect_outliers_iqr(
        self,
        data: List[float],
        timestamps: Optional[List[datetime]] = None
    ) -> List[AnomalyResult]:
        """
        Detecta outliers usando IQR (Interquartile Range).

        Args:
            data: Lista de valores
            timestamps: Lista opcional de timestamps

        Returns:
            Lista de anomalias detectadas
        """
        if len(data) < 4:
            return []

        arr = np.array(data)
        q1 = np.percentile(arr, 25)
        q3 = np.percentile(arr, 75)
        iqr = q3 - q1

        lower_bound = q1 - self.iqr_multiplier * iqr
        upper_bound = q3 + self.iqr_multiplier * iqr

        anomalies = []
        median = np.median(arr)

        for i, value in enumerate(data):
            if value < lower_bound or value > upper_bound:
                deviation = abs(value - median) / iqr if iqr > 0 else 0
                severity = self._calculate_severity(deviation)

                anomaly = AnomalyResult(
                    is_anomaly=True,
                    anomaly_type=AnomalyType.OUTLIER,
                    severity=severity,
                    value=value,
                    expected_value=median,
                    deviation=abs(value - median),
                    z_score=deviation,
                    confidence=min(1.0, deviation / 3),
                    description=f"Outlier IQR: {value:.2f} fuera de [{lower_bound:.2f}, {upper_bound:.2f}]",
                    index=i,
                    timestamp=timestamps[i] if timestamps and i < len(timestamps) else None
                )
                anomalies.append(anomaly)

        return anomalies

    def detect_sudden_changes(
        self,
        data: List[float],
        timestamps: Optional[List[datetime]] = None,
        window_size: int = 3
    ) -> List[AnomalyResult]:
        """
        Detecta cambios repentinos en la serie.
        RN-04.01: Caida > 15% genera alerta de riesgo.
        RN-04.02: Subida > 20% genera alerta de oportunidad.

        Args:
            data: Lista de valores
            timestamps: Lista opcional de timestamps
            window_size: Tamano de ventana para comparacion

        Returns:
            Lista de anomalias detectadas
        """
        if len(data) < window_size + 1:
            return []

        anomalies = []

        for i in range(window_size, len(data)):
            # Promedio de ventana anterior
            window_avg = np.mean(data[i - window_size:i])
            current = data[i]

            if window_avg == 0:
                continue

            change_pct = (current - window_avg) / abs(window_avg)

            # Detectar caidas (riesgo)
            if change_pct < -self.change_threshold:
                severity = Severity.HIGH if change_pct < -0.30 else Severity.MEDIUM
                anomaly = AnomalyResult(
                    is_anomaly=True,
                    anomaly_type=AnomalyType.SUDDEN_CHANGE,
                    severity=severity,
                    value=current,
                    expected_value=window_avg,
                    deviation=abs(change_pct) * 100,
                    z_score=change_pct * 100,
                    confidence=min(1.0, abs(change_pct) / 0.50),
                    description=f"Caida significativa: {change_pct*100:.1f}% respecto al promedio anterior",
                    index=i,
                    timestamp=timestamps[i] if timestamps and i < len(timestamps) else None
                )
                anomalies.append(anomaly)

            # Detectar subidas (oportunidad)
            elif change_pct > self.opportunity_threshold:
                severity = Severity.MEDIUM  # Oportunidades son menos urgentes
                anomaly = AnomalyResult(
                    is_anomaly=True,
                    anomaly_type=AnomalyType.SUDDEN_CHANGE,
                    severity=severity,
                    value=current,
                    expected_value=window_avg,
                    deviation=change_pct * 100,
                    z_score=change_pct * 100,
                    confidence=min(1.0, change_pct / 0.50),
                    description=f"Subida significativa: +{change_pct*100:.1f}% respecto al promedio anterior",
                    index=i,
                    timestamp=timestamps[i] if timestamps and i < len(timestamps) else None
                )
                anomalies.append(anomaly)

        return anomalies

    def detect_trend_break(
        self,
        data: List[float],
        timestamps: Optional[List[datetime]] = None,
        min_periods: int = 5
    ) -> List[AnomalyResult]:
        """
        Detecta rupturas de tendencia.

        Args:
            data: Lista de valores
            timestamps: Lista opcional de timestamps
            min_periods: Periodos minimos para detectar tendencia

        Returns:
            Lista de anomalias detectadas
        """
        if len(data) < min_periods * 2:
            return []

        anomalies = []

        # Dividir en dos mitades y comparar pendientes
        mid = len(data) // 2

        # Calcular pendiente de primera mitad
        x1 = np.arange(mid)
        y1 = np.array(data[:mid])
        if len(x1) > 1:
            slope1 = np.polyfit(x1, y1, 1)[0]
        else:
            return []

        # Calcular pendiente de segunda mitad
        x2 = np.arange(len(data) - mid)
        y2 = np.array(data[mid:])
        if len(x2) > 1:
            slope2 = np.polyfit(x2, y2, 1)[0]
        else:
            return []

        # Detectar cambio de tendencia
        if slope1 != 0:
            slope_change = (slope2 - slope1) / abs(slope1)

            if abs(slope_change) > 0.5:  # Cambio significativo de pendiente
                trend_before = "ascendente" if slope1 > 0 else "descendente"
                trend_after = "ascendente" if slope2 > 0 else "descendente"

                if (slope1 > 0 and slope2 < 0) or (slope1 < 0 and slope2 > 0):
                    severity = Severity.HIGH
                    description = f"Ruptura de tendencia: de {trend_before} a {trend_after}"
                else:
                    severity = Severity.MEDIUM
                    description = f"Cambio en ritmo de tendencia {trend_after}"

                anomaly = AnomalyResult(
                    is_anomaly=True,
                    anomaly_type=AnomalyType.TREND_BREAK,
                    severity=severity,
                    value=data[-1],
                    expected_value=data[mid-1] + slope1 * (len(data) - mid),
                    deviation=abs(slope_change) * 100,
                    z_score=slope_change,
                    confidence=min(1.0, abs(slope_change)),
                    description=description,
                    index=mid,
                    timestamp=timestamps[mid] if timestamps and mid < len(timestamps) else None
                )
                anomalies.append(anomaly)

        return anomalies

    def analyze_series(
        self,
        data: List[float],
        timestamps: Optional[List[datetime]] = None
    ) -> Dict[str, Any]:
        """
        Analisis completo de una serie de datos.

        Args:
            data: Lista de valores
            timestamps: Lista opcional de timestamps

        Returns:
            Dict con resultados del analisis
        """
        if len(data) < 3:
            return {
                "success": False,
                "error": "Se requieren al menos 3 datos para el analisis"
            }

        # Ejecutar todos los detectores
        zscore_anomalies = self.detect_outliers_zscore(data, timestamps)
        iqr_anomalies = self.detect_outliers_iqr(data, timestamps)
        change_anomalies = self.detect_sudden_changes(data, timestamps)
        trend_anomalies = self.detect_trend_break(data, timestamps)

        # Combinar y deduplicar anomalias
        all_anomalies = zscore_anomalies + change_anomalies + trend_anomalies

        # Estadisticas basicas
        arr = np.array(data)
        stats = {
            "count": len(data),
            "mean": float(np.mean(arr)),
            "std": float(np.std(arr)),
            "min": float(np.min(arr)),
            "max": float(np.max(arr)),
            "median": float(np.median(arr))
        }

        # Tasa de anomalias
        anomaly_rate = len(all_anomalies) / len(data) if data else 0

        # RN-04.03: Alerta si anomalias > 5%
        high_anomaly_rate = anomaly_rate > self.DEFAULT_ANOMALY_RATE_THRESHOLD

        return {
            "success": True,
            "statistics": stats,
            "total_anomalies": len(all_anomalies),
            "anomaly_rate": round(anomaly_rate * 100, 2),
            "high_anomaly_rate_alert": high_anomaly_rate,
            "anomalies": [a.to_dict() for a in all_anomalies],
            "by_type": {
                "outliers": len(zscore_anomalies),
                "sudden_changes": len(change_anomalies),
                "trend_breaks": len(trend_anomalies)
            },
            "by_severity": {
                "high": sum(1 for a in all_anomalies if a.severity in [Severity.HIGH, Severity.CRITICAL]),
                "medium": sum(1 for a in all_anomalies if a.severity == Severity.MEDIUM),
                "low": sum(1 for a in all_anomalies if a.severity == Severity.LOW)
            }
        }

    def compare_with_baseline(
        self,
        current_value: float,
        baseline_values: List[float]
    ) -> AnomalyResult:
        """
        Compara un valor actual con una linea base historica.

        Args:
            current_value: Valor actual a evaluar
            baseline_values: Valores historicos de referencia

        Returns:
            Resultado de la comparacion
        """
        if len(baseline_values) < 2:
            return AnomalyResult(
                is_anomaly=False,
                description="Datos insuficientes para comparacion"
            )

        arr = np.array(baseline_values)
        mean = np.mean(arr)
        std = np.std(arr)

        if std == 0:
            is_anomaly = current_value != mean
            return AnomalyResult(
                is_anomaly=is_anomaly,
                value=current_value,
                expected_value=mean,
                description="Varianza cero en datos historicos"
            )

        z_score = (current_value - mean) / std
        is_anomaly = abs(z_score) > self.z_threshold

        # Calcular cambio porcentual
        pct_change = (current_value - mean) / abs(mean) if mean != 0 else 0

        # Determinar tipo y severidad
        if is_anomaly:
            if pct_change < -self.change_threshold:
                anomaly_type = AnomalyType.SUDDEN_CHANGE
                severity = Severity.HIGH if pct_change < -0.30 else Severity.MEDIUM
                description = f"Caida de {abs(pct_change)*100:.1f}% vs historico"
            elif pct_change > self.opportunity_threshold:
                anomaly_type = AnomalyType.SUDDEN_CHANGE
                severity = Severity.MEDIUM
                description = f"Incremento de {pct_change*100:.1f}% vs historico"
            else:
                anomaly_type = AnomalyType.OUTLIER
                severity = self._calculate_severity(abs(z_score))
                description = f"Valor atipico: z-score = {z_score:.2f}"
        else:
            anomaly_type = None
            severity = Severity.LOW
            description = "Valor dentro de rangos normales"

        return AnomalyResult(
            is_anomaly=is_anomaly,
            anomaly_type=anomaly_type,
            severity=severity,
            value=current_value,
            expected_value=mean,
            deviation=abs(current_value - mean),
            z_score=z_score,
            confidence=min(1.0, abs(z_score) / (self.z_threshold * 2)) if is_anomaly else 0,
            description=description
        )

    def _calculate_severity(self, z_score: float) -> Severity:
        """Calcula severidad basada en z-score."""
        if abs(z_score) > 4:
            return Severity.CRITICAL
        elif abs(z_score) > 3:
            return Severity.HIGH
        elif abs(z_score) > 2:
            return Severity.MEDIUM
        return Severity.LOW

    def get_alert_thresholds(self) -> Dict[str, float]:
        """Retorna los umbrales configurados."""
        return {
            "z_threshold": self.z_threshold,
            "iqr_multiplier": self.iqr_multiplier,
            "change_threshold": self.change_threshold * 100,  # Como porcentaje
            "opportunity_threshold": self.opportunity_threshold * 100,
            "anomaly_rate_threshold": self.DEFAULT_ANOMALY_RATE_THRESHOLD * 100
        }

    def set_thresholds(
        self,
        risk_threshold: Optional[float] = None,
        opportunity_threshold: Optional[float] = None,
        z_threshold: Optional[float] = None
    ):
        """
        Configura umbrales personalizados.

        Args:
            risk_threshold: Umbral de riesgo (porcentaje, ej: 15 para 15%)
            opportunity_threshold: Umbral de oportunidad (porcentaje)
            z_threshold: Umbral de z-score
        """
        if risk_threshold is not None:
            self.change_threshold = risk_threshold / 100
        if opportunity_threshold is not None:
            self.opportunity_threshold = opportunity_threshold / 100
        if z_threshold is not None:
            self.z_threshold = z_threshold
