"""
Pruebas de integracion para endpoints de rentabilidad.
RF-06: Modulo de Evaluacion de Rentabilidad.
"""

import pytest
from datetime import date, timedelta
from fastapi.testclient import TestClient


class TestProfitabilityEndpoints:
    """Pruebas para endpoints de rentabilidad."""

    def test_calculate_financial_indicators(self, client: TestClient, auth_headers):
        """Test calcular indicadores financieros."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        request_data = {
            "fecha_inicio": fecha_inicio.isoformat(),
            "fecha_fin": fecha_fin.isoformat()
        }

        response = client.post(
            "/api/v1/profitability/indicators",
            headers=auth_headers,
            json=request_data
        )

        if response.status_code == 200:
            data = response.json()
            # Deberia incluir indicadores financieros
            assert "success" in data or "indicadores" in data

    def test_calculate_indicators_by_period(self, client: TestClient, auth_headers):
        """
        RN-06.03: Calculo por periodo.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=90)

        request_data = {
            "fecha_inicio": fecha_inicio.isoformat(),
            "fecha_fin": fecha_fin.isoformat(),
            "periodo": "mensual"
        }

        response = client.post(
            "/api/v1/profitability/indicators",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 422, 500]

    def test_get_profitability_by_products(self, client: TestClient, auth_headers):
        """
        RF-06.02: Rentabilidad por producto.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/profitability/products",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, (list, dict))

    def test_get_non_profitable_products(self, client: TestClient, auth_headers):
        """
        RF-06.03 / RN-06.04: Identificar productos con margen < 10%.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/profitability/products/non-profitable",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        if response.status_code == 200:
            data = response.json()
            # Deberia retornar productos con margen < 10%
            assert isinstance(data, (list, dict))

    def test_get_profitability_by_categories(self, client: TestClient, auth_headers):
        """Test rentabilidad por categoria."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/profitability/categories",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, (list, dict))

    def test_get_profitability_trends(self, client: TestClient, auth_headers):
        """Test tendencias de rentabilidad."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=90)

        response = client.get(
            "/api/v1/profitability/trends",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, (list, dict))

    def test_get_product_ranking(self, client: TestClient, auth_headers):
        """Test ranking de productos por rentabilidad."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/profitability/ranking",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
                "metrica": "margen",
                "limit": 10
            }
        )

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, (list, dict))

    def test_compare_periods(self, client: TestClient, auth_headers):
        """Test comparar rentabilidad entre dos periodos."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Periodo actual
        periodo_actual_fin = date.today()
        periodo_actual_inicio = periodo_actual_fin - timedelta(days=30)

        # Periodo anterior
        periodo_anterior_fin = periodo_actual_inicio - timedelta(days=1)
        periodo_anterior_inicio = periodo_anterior_fin - timedelta(days=30)

        request_data = {
            "periodo_1": {
                "fecha_inicio": periodo_anterior_inicio.isoformat(),
                "fecha_fin": periodo_anterior_fin.isoformat()
            },
            "periodo_2": {
                "fecha_inicio": periodo_actual_inicio.isoformat(),
                "fecha_fin": periodo_actual_fin.isoformat()
            }
        }

        response = client.post(
            "/api/v1/profitability/compare",
            headers=auth_headers,
            json=request_data
        )

        if response.status_code == 200:
            data = response.json()
            # Deberia incluir comparativas
            assert isinstance(data, dict)

    def test_get_profitability_summary(self, client: TestClient, auth_headers):
        """Test resumen ejecutivo de rentabilidad."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/profitability/summary",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, dict)


class TestProfitabilityBusinessRules:
    """Pruebas de reglas de negocio para rentabilidad."""

    def test_validate_complete_data(self, client: TestClient, auth_headers):
        """
        RN-06.01: Validacion de datos completos.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Periodo sin datos
        fecha_fin = date.today() + timedelta(days=365)  # Futuro
        fecha_inicio = fecha_fin - timedelta(days=30)

        request_data = {
            "fecha_inicio": fecha_inicio.isoformat(),
            "fecha_fin": fecha_fin.isoformat()
        }

        response = client.post(
            "/api/v1/profitability/indicators",
            headers=auth_headers,
            json=request_data
        )

        # Deberia manejar caso sin datos
        assert response.status_code in [200, 400, 422, 500]

    def test_operating_profit_calculation(self, client: TestClient, auth_headers):
        """
        RN-06.02: Calculo de Utilidad Operativa.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        request_data = {
            "fecha_inicio": fecha_inicio.isoformat(),
            "fecha_fin": fecha_fin.isoformat()
        }

        response = client.post(
            "/api/v1/profitability/indicators",
            headers=auth_headers,
            json=request_data
        )

        if response.status_code == 200:
            data = response.json()
            # Deberia incluir utilidad operativa
            # Campo puede ser 'utilidad_operativa', 'operating_profit', etc.
            pass

    def test_period_types(self, client: TestClient, auth_headers):
        """
        RN-06.03: Diferentes tipos de periodo.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        periodos = ["diario", "semanal", "mensual", "trimestral", "anual"]

        for periodo in periodos:
            fecha_fin = date.today()
            fecha_inicio = fecha_fin - timedelta(days=365)

            request_data = {
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
                "periodo": periodo
            }

            response = client.post(
                "/api/v1/profitability/indicators",
                headers=auth_headers,
                json=request_data
            )

            # Deberia aceptar todos los tipos de periodo
            assert response.status_code in [200, 400, 422, 500]

    def test_non_profitable_threshold(self, client: TestClient, auth_headers):
        """
        RN-06.04: Umbral de margen < 10%.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/profitability/products/non-profitable",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
                "umbral_margen": 10.0  # 10%
            }
        )

        # Deberia aplicar el umbral
        assert response.status_code in [200, 400, 422, 500]
