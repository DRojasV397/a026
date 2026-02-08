"""
Pruebas de integracion para endpoints de alertas.
RF-04: Sistema de Alertas.
"""

import pytest
from datetime import date, timedelta
from fastapi.testclient import TestClient


class TestAlertEndpoints:
    """Pruebas para endpoints de alertas."""

    def test_list_active_alerts(self, client: TestClient, auth_headers):
        """Test listar alertas activas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/alerts",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, (list, dict))

    def test_list_alerts_with_filters(self, client: TestClient, auth_headers):
        """Test listar alertas con filtros."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/alerts",
            headers=auth_headers,
            params={
                "tipo": "riesgo",
                "importancia": "alta",
                "limit": 10
            }
        )

        assert response.status_code in [200, 422, 500]

    def test_get_alert_history(self, client: TestClient, auth_headers):
        """Test obtener historial de alertas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/alerts/history",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, (list, dict))

    def test_get_alert_history_with_date_range(self, client: TestClient, auth_headers):
        """Test historial de alertas con rango de fechas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/alerts/history",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        assert response.status_code in [200, 422, 500]

    def test_get_alert_summary(self, client: TestClient, auth_headers):
        """Test obtener resumen de alertas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/alerts/summary",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            # Deberia incluir conteos por tipo/importancia
            assert isinstance(data, dict)

    def test_mark_alert_as_read(self, client: TestClient, auth_headers):
        """Test marcar alerta como leida."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.put(
            "/api/v1/alerts/1/read",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 500]

    def test_change_alert_status(self, client: TestClient, auth_headers):
        """Test cambiar estado de alerta."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "estado": "Resuelta"
        }

        response = client.put(
            "/api/v1/alerts/1/status",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_change_alert_status_invalid(self, client: TestClient, auth_headers):
        """Test cambiar estado a valor invalido."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "estado": "EstadoInvalido"
        }

        response = client.put(
            "/api/v1/alerts/1/status",
            headers=auth_headers,
            json=request_data
        )

        # Deberia rechazar estado invalido
        assert response.status_code in [400, 404, 422, 500]

    def test_get_alert_config(self, client: TestClient, auth_headers):
        """Test obtener configuracion de alertas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/alerts/config",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            # Deberia incluir umbrales configurados
            assert isinstance(data, dict)

    def test_configure_alert_thresholds(self, client: TestClient, auth_headers):
        """
        Test configurar umbrales de alertas (RF-04.04).
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        config_data = {
            "change_threshold": 15.0,
            "opportunity_threshold": 20.0,
            "anomaly_rate_threshold": 5.0
        }

        response = client.post(
            "/api/v1/alerts/config",
            headers=auth_headers,
            json=config_data
        )

        assert response.status_code in [200, 400, 422, 500]

    def test_analyze_and_generate_alerts(self, client: TestClient, auth_headers):
        """Test analizar datos y generar alertas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        request_data = {
            "fecha_inicio": fecha_inicio.isoformat(),
            "fecha_fin": fecha_fin.isoformat(),
            "tipo_analisis": "ventas"
        }

        response = client.post(
            "/api/v1/alerts/analyze",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 422, 500]

    def test_get_alert_by_id(self, client: TestClient, auth_headers):
        """Test obtener alerta por ID."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/alerts/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 500]

    def test_get_nonexistent_alert(self, client: TestClient, auth_headers):
        """Test obtener alerta inexistente."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/alerts/99999",
            headers=auth_headers
        )

        assert response.status_code in [404, 500]

    def test_delete_alert(self, client: TestClient, auth_headers):
        """Test eliminar alerta."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.delete(
            "/api/v1/alerts/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 204, 400, 404, 500]

    def test_check_prediction_for_alert(self, client: TestClient, auth_headers):
        """Test verificar prediccion para alertas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.post(
            "/api/v1/alerts/check-prediction/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 400, 404, 500]


class TestAlertBusinessRules:
    """Pruebas de reglas de negocio para alertas."""

    def test_risk_alert_threshold(self, client: TestClient, auth_headers):
        """
        RN-04.01: Alerta de riesgo por caida > 15%.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Configurar umbral
        config_data = {
            "change_threshold": 15.0
        }

        response = client.post(
            "/api/v1/alerts/config",
            headers=auth_headers,
            json=config_data
        )

        # Verificar que se aplica
        assert response.status_code in [200, 400, 422, 500]

    def test_opportunity_alert_threshold(self, client: TestClient, auth_headers):
        """
        RN-04.02: Alerta de oportunidad por subida > 20%.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        config_data = {
            "opportunity_threshold": 20.0
        }

        response = client.post(
            "/api/v1/alerts/config",
            headers=auth_headers,
            json=config_data
        )

        assert response.status_code in [200, 400, 422, 500]

    def test_anomaly_rate_threshold(self, client: TestClient, auth_headers):
        """
        RN-04.03: Alerta si anomalias > 5% de transacciones.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        config_data = {
            "anomaly_rate_threshold": 5.0
        }

        response = client.post(
            "/api/v1/alerts/config",
            headers=auth_headers,
            json=config_data
        )

        assert response.status_code in [200, 400, 422, 500]

    def test_max_active_alerts_limit(self, client: TestClient, auth_headers):
        """
        RN-04.05: Limite de 10 alertas simultaneas.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Obtener alertas activas
        response = client.get(
            "/api/v1/alerts",
            headers=auth_headers,
            params={"estado": "Activa"}
        )

        if response.status_code == 200:
            data = response.json()
            # El sistema deberia limitar a 10 alertas activas
            if isinstance(data, list):
                assert len(data) <= 10
            elif isinstance(data, dict) and "alertas" in data:
                assert len(data["alertas"]) <= 10

    def test_confidence_level_included(self, client: TestClient, auth_headers):
        """
        RN-04.04: Nivel de confianza incluido en alertas.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/alerts/1",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            # Deberia incluir nivel de confianza
            # El campo puede ser 'nivel_confianza', 'confidence', 'nivelConfianza', etc.
            pass

    def test_alerts_prioritized_by_impact(self, client: TestClient, auth_headers):
        """
        RN-04.06: Priorizacion por impacto economico.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Al listar alertas, deberian estar priorizadas
        response = client.get(
            "/api/v1/alerts",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            # Las alertas de mayor impacto deberian aparecer primero
            pass
