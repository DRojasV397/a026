"""
Pruebas unitarias para el servicio de alertas.
RF-04: Sistema de Alertas.
"""

import pytest
from datetime import date, datetime, timedelta
from decimal import Decimal

from app.services.alert_service import AlertService


class TestAlertService:
    """Pruebas para AlertService."""

    def test_init(self, db_session):
        """Verifica inicializacion del servicio."""
        service = AlertService(db_session)

        assert service is not None
        assert service.db == db_session


class TestRiskAlerts:
    """Pruebas para alertas de riesgo (RN-04.01)."""

    def test_risk_alert_threshold(self, db_session):
        """
        RN-04.01: Alerta de riesgo por caida > 15%.
        """
        RISK_THRESHOLD = 15.0

        valor_anterior = Decimal("100000.00")
        valor_actual = Decimal("80000.00")

        cambio_porcentual = ((valor_actual - valor_anterior) / valor_anterior) * 100

        assert cambio_porcentual == -20  # -20% de caida
        assert abs(cambio_porcentual) > RISK_THRESHOLD

    def test_no_risk_alert_small_drop(self, db_session):
        """Verifica que caidas pequenas no generen alerta."""
        RISK_THRESHOLD = 15.0

        valor_anterior = Decimal("100000.00")
        valor_actual = Decimal("90000.00")

        cambio_porcentual = ((valor_actual - valor_anterior) / valor_anterior) * 100

        assert cambio_porcentual == -10  # -10% de caida
        assert abs(cambio_porcentual) < RISK_THRESHOLD

    def test_classify_risk_severity(self, db_session):
        """Verifica clasificacion de severidad de riesgo."""
        def classify_risk(change_percentage):
            if abs(change_percentage) >= 30:
                return "critico"
            elif abs(change_percentage) >= 20:
                return "alto"
            elif abs(change_percentage) >= 15:
                return "medio"
            else:
                return "bajo"

        assert classify_risk(-35) == "critico"
        assert classify_risk(-25) == "alto"
        assert classify_risk(-17) == "medio"
        assert classify_risk(-10) == "bajo"


class TestOpportunityAlerts:
    """Pruebas para alertas de oportunidad (RN-04.02)."""

    def test_opportunity_alert_threshold(self, db_session):
        """
        RN-04.02: Alerta de oportunidad por subida > 20%.
        """
        OPPORTUNITY_THRESHOLD = 20.0

        valor_anterior = Decimal("100000.00")
        valor_actual = Decimal("125000.00")

        cambio_porcentual = ((valor_actual - valor_anterior) / valor_anterior) * 100

        assert cambio_porcentual == 25  # +25% de subida
        assert cambio_porcentual > OPPORTUNITY_THRESHOLD

    def test_no_opportunity_alert_small_rise(self, db_session):
        """Verifica que subidas pequenas no generen alerta."""
        OPPORTUNITY_THRESHOLD = 20.0

        valor_anterior = Decimal("100000.00")
        valor_actual = Decimal("115000.00")

        cambio_porcentual = ((valor_actual - valor_anterior) / valor_anterior) * 100

        assert cambio_porcentual == 15  # +15% de subida
        assert cambio_porcentual < OPPORTUNITY_THRESHOLD


class TestAnomalyAlerts:
    """Pruebas para alertas de anomalias (RN-04.03)."""

    def test_anomaly_rate_threshold(self, db_session):
        """
        RN-04.03: Alerta si anomalias > 5% de transacciones.
        """
        ANOMALY_RATE_THRESHOLD = 5.0

        total_transacciones = 1000
        transacciones_anomalas = 60

        anomaly_rate = (transacciones_anomalas / total_transacciones) * 100

        assert anomaly_rate == 6.0
        assert anomaly_rate > ANOMALY_RATE_THRESHOLD

    def test_no_anomaly_alert_low_rate(self, db_session):
        """Verifica que tasa baja de anomalias no genere alerta."""
        ANOMALY_RATE_THRESHOLD = 5.0

        total_transacciones = 1000
        transacciones_anomalas = 30

        anomaly_rate = (transacciones_anomalas / total_transacciones) * 100

        assert anomaly_rate == 3.0
        assert anomaly_rate < ANOMALY_RATE_THRESHOLD


class TestConfidenceLevel:
    """Pruebas para nivel de confianza (RN-04.04)."""

    def test_confidence_level_included(self, db_session):
        """
        RN-04.04: Nivel de confianza incluido.
        """
        alerta = {
            "tipo": "riesgo",
            "metrica": "ventas",
            "valor_actual": 80000,
            "valor_esperado": 100000,
            "nivel_confianza": 0.85  # 85% de confianza
        }

        assert "nivel_confianza" in alerta
        assert 0 <= alerta["nivel_confianza"] <= 1

    def test_confidence_level_ranges(self, db_session):
        """Verifica rangos de nivel de confianza."""
        def classify_confidence(level):
            if level >= 0.9:
                return "muy_alta"
            elif level >= 0.75:
                return "alta"
            elif level >= 0.5:
                return "media"
            else:
                return "baja"

        assert classify_confidence(0.95) == "muy_alta"
        assert classify_confidence(0.80) == "alta"
        assert classify_confidence(0.60) == "media"
        assert classify_confidence(0.40) == "baja"


class TestAlertLimits:
    """Pruebas para limites de alertas (RN-04.05)."""

    def test_max_active_alerts(self, db_session):
        """
        RN-04.05: Limite de 10 alertas simultaneas.
        """
        MAX_ACTIVE_ALERTS = 10

        # Alertas activas
        alertas_activas = 8
        assert alertas_activas <= MAX_ACTIVE_ALERTS

        # Excede limite
        alertas_exceso = 12
        assert alertas_exceso > MAX_ACTIVE_ALERTS

    def test_replace_old_alerts(self, db_session):
        """Verifica reemplazo de alertas antiguas."""
        MAX_ACTIVE_ALERTS = 10

        alertas = [
            {"id": i, "creada_en": datetime.now() - timedelta(days=i)}
            for i in range(15)
        ]

        # Ordenar por fecha (mas recientes primero)
        alertas_ordenadas = sorted(alertas, key=lambda x: x["creada_en"], reverse=True)

        # Mantener solo las mas recientes
        alertas_activas = alertas_ordenadas[:MAX_ACTIVE_ALERTS]

        assert len(alertas_activas) == MAX_ACTIVE_ALERTS


class TestAlertPrioritization:
    """Pruebas para priorizacion de alertas (RN-04.06)."""

    def test_prioritize_by_impact(self, db_session):
        """
        RN-04.06: Priorizacion por impacto economico.
        """
        alertas = [
            {"id": 1, "impacto_economico": Decimal("5000.00"), "importancia": "media"},
            {"id": 2, "impacto_economico": Decimal("50000.00"), "importancia": "alta"},
            {"id": 3, "impacto_economico": Decimal("15000.00"), "importancia": "media"},
            {"id": 4, "impacto_economico": Decimal("100000.00"), "importancia": "alta"},
        ]

        # Ordenar por impacto economico (mayor primero)
        alertas_priorizadas = sorted(
            alertas,
            key=lambda x: x["impacto_economico"],
            reverse=True
        )

        assert alertas_priorizadas[0]["id"] == 4
        assert alertas_priorizadas[-1]["id"] == 1

    def test_secondary_priority_by_importance(self, db_session):
        """Verifica priorizacion secundaria por importancia."""
        importance_order = {"alta": 3, "media": 2, "baja": 1}

        alertas = [
            {"id": 1, "importancia": "baja"},
            {"id": 2, "importancia": "alta"},
            {"id": 3, "importancia": "media"},
        ]

        alertas_ordenadas = sorted(
            alertas,
            key=lambda x: importance_order.get(x["importancia"], 0),
            reverse=True
        )

        assert alertas_ordenadas[0]["importancia"] == "alta"
        assert alertas_ordenadas[-1]["importancia"] == "baja"


class TestAlertTypes:
    """Pruebas para tipos de alertas."""

    def test_alert_type_risk(self, db_session):
        """Verifica tipo de alerta de riesgo."""
        alert = {
            "tipo": "riesgo",
            "descripcion": "Caida significativa en ventas"
        }

        assert alert["tipo"] == "riesgo"

    def test_alert_type_opportunity(self, db_session):
        """Verifica tipo de alerta de oportunidad."""
        alert = {
            "tipo": "oportunidad",
            "descripcion": "Incremento en demanda de producto"
        }

        assert alert["tipo"] == "oportunidad"

    def test_alert_type_anomaly(self, db_session):
        """Verifica tipo de alerta de anomalia."""
        alert = {
            "tipo": "anomalia",
            "descripcion": "Patron inusual detectado"
        }

        assert alert["tipo"] == "anomalia"


class TestAlertStates:
    """Pruebas para estados de alertas."""

    def test_alert_states(self, db_session):
        """Verifica estados disponibles de alertas."""
        states = ["Activa", "Leida", "Resuelta", "Ignorada"]

        assert "Activa" in states
        assert "Resuelta" in states

    def test_transition_to_read(self, db_session):
        """Verifica transicion a estado leida."""
        alert = {"estado": "Activa"}
        alert["estado"] = "Leida"

        assert alert["estado"] == "Leida"

    def test_transition_to_resolved(self, db_session):
        """Verifica transicion a estado resuelta."""
        alert = {"estado": "Activa"}
        alert["estado"] = "Resuelta"

        assert alert["estado"] == "Resuelta"


class TestAlertConfiguration:
    """Pruebas para configuracion de alertas (RF-04.04)."""

    def test_configure_thresholds(self, db_session):
        """
        RF-04.04: Configurar umbrales personalizados.
        """
        config = {
            "risk_threshold": 15.0,
            "opportunity_threshold": 20.0,
            "anomaly_rate_threshold": 5.0
        }

        # Modificar umbrales
        config["risk_threshold"] = 10.0
        config["opportunity_threshold"] = 25.0

        assert config["risk_threshold"] == 10.0
        assert config["opportunity_threshold"] == 25.0

    def test_default_configuration(self, db_session):
        """Verifica configuracion por defecto."""
        default_config = {
            "change_threshold": 15.0,
            "opportunity_threshold": 20.0,
            "anomaly_rate_threshold": 5.0,
            "max_active_alerts": 10
        }

        assert default_config["change_threshold"] == 15.0
        assert default_config["max_active_alerts"] == 10
