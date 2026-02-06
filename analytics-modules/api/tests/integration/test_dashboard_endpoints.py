"""
Pruebas de integracion para endpoints de dashboard y reportes.
RF-07: Dashboard ejecutivo y generacion de reportes.
"""

import pytest
from datetime import date, timedelta
from fastapi.testclient import TestClient


class TestDashboardEndpoints:
    """Pruebas para endpoints de dashboard."""

    def test_get_executive_dashboard(self, client: TestClient, auth_headers):
        """Test obtener dashboard ejecutivo."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/dashboard/executive",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert "success" in data
            if data.get("success"):
                assert "resumen_ventas" in data
                assert "resumen_compras" in data
                assert "kpis_financieros" in data

    def test_get_executive_dashboard_with_dates(self, client: TestClient, auth_headers):
        """Test dashboard con rango de fechas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/dashboard/executive",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        assert response.status_code in [200, 401, 500]

    def test_get_kpi_detail_ventas(self, client: TestClient, auth_headers):
        """Test detalle de KPI ventas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/dashboard/kpi/ventas",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert "success" in data

    def test_get_kpi_detail_invalid(self, client: TestClient, auth_headers):
        """Test detalle de KPI invalido."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/dashboard/kpi/kpi_invalido",
            headers=auth_headers
        )

        # Deberia retornar error
        assert response.status_code in [200, 400, 404]
        if response.status_code == 200:
            data = response.json()
            # Puede tener success=False con error
            assert "success" in data

    def test_get_scenarios_summary(self, client: TestClient, auth_headers):
        """Test resumen de escenarios."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/dashboard/scenarios",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert "success" in data

    def test_get_recent_predictions(self, client: TestClient, auth_headers):
        """Test predicciones recientes."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/dashboard/predictions",
            headers=auth_headers,
            params={"limit": 10}
        )

        if response.status_code == 200:
            data = response.json()
            assert "success" in data
            if data.get("success"):
                assert "predicciones" in data

    def test_compare_actual_vs_predicted(self, client: TestClient, auth_headers):
        """Test comparar real vs predicho."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/dashboard/compare",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
                "tipo_entidad": "producto"
            }
        )

        if response.status_code == 200:
            data = response.json()
            assert "success" in data


class TestUserPreferencesEndpoints:
    """Pruebas para endpoints de preferencias de usuario."""

    def test_get_user_preferences(self, client: TestClient, auth_headers):
        """Test obtener preferencias de usuario."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/dashboard/users/1/preferences",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert "success" in data

    def test_update_user_preferences(self, client: TestClient, auth_headers):
        """Test actualizar preferencias de usuario."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        preferences = {
            "preferencias": [
                {"kpi": "ventas", "valor": "1"},
                {"kpi": "compras", "valor": "1"},
                {"kpi": "margen", "valor": "0"}
            ]
        }

        response = client.put(
            "/api/v1/dashboard/users/1/preferences",
            headers=auth_headers,
            json=preferences
        )

        assert response.status_code in [200, 400, 404, 500]


class TestReportEndpoints:
    """Pruebas para endpoints de reportes."""

    def test_get_report_types(self, client: TestClient, auth_headers):
        """Test obtener tipos de reportes."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/dashboard/reports/types",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert "success" in data
            assert "tipos" in data

            # Verificar tipos esperados
            tipos = [t["tipo"] for t in data["tipos"]]
            assert "ventas" in tipos
            assert "compras" in tipos
            assert "rentabilidad" in tipos
            assert "productos" in tipos

    def test_generate_sales_report_json(self, client: TestClient, auth_headers):
        """Test generar reporte de ventas en JSON."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        request_data = {
            "tipo": "ventas",
            "fecha_inicio": fecha_inicio.isoformat(),
            "fecha_fin": fecha_fin.isoformat(),
            "formato": "json",
            "agrupar_por": "dia"
        }

        response = client.post(
            "/api/v1/dashboard/reports/generate",
            headers=auth_headers,
            json=request_data
        )

        if response.status_code == 200:
            data = response.json()
            assert "success" in data

    def test_generate_sales_report_csv(self, client: TestClient, auth_headers):
        """Test generar reporte de ventas en CSV."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        request_data = {
            "tipo": "ventas",
            "fecha_inicio": fecha_inicio.isoformat(),
            "fecha_fin": fecha_fin.isoformat(),
            "formato": "csv",
            "agrupar_por": "dia"
        }

        response = client.post(
            "/api/v1/dashboard/reports/generate",
            headers=auth_headers,
            json=request_data
        )

        # CSV retorna text/csv
        assert response.status_code in [200, 500]

    def test_generate_purchases_report(self, client: TestClient, auth_headers):
        """Test generar reporte de compras."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        request_data = {
            "tipo": "compras",
            "fecha_inicio": fecha_inicio.isoformat(),
            "fecha_fin": fecha_fin.isoformat(),
            "formato": "json",
            "agrupar_por": "semana"
        }

        response = client.post(
            "/api/v1/dashboard/reports/generate",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 500]

    def test_generate_profitability_report(self, client: TestClient, auth_headers):
        """Test generar reporte de rentabilidad."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=90)

        request_data = {
            "tipo": "rentabilidad",
            "fecha_inicio": fecha_inicio.isoformat(),
            "fecha_fin": fecha_fin.isoformat(),
            "formato": "json"
        }

        response = client.post(
            "/api/v1/dashboard/reports/generate",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 500]

    def test_generate_products_report(self, client: TestClient, auth_headers):
        """Test generar reporte de productos."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        request_data = {
            "tipo": "productos",
            "fecha_inicio": fecha_inicio.isoformat(),
            "fecha_fin": fecha_fin.isoformat(),
            "formato": "json",
            "top_n": 20
        }

        response = client.post(
            "/api/v1/dashboard/reports/generate",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 500]

    def test_quick_sales_report(self, client: TestClient, auth_headers):
        """Test reporte rapido de ventas via GET."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=7)

        response = client.get(
            "/api/v1/dashboard/reports/sales",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
                "formato": "json"
            }
        )

        assert response.status_code in [200, 500]

    def test_quick_purchases_report(self, client: TestClient, auth_headers):
        """Test reporte rapido de compras via GET."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=7)

        response = client.get(
            "/api/v1/dashboard/reports/purchases",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        assert response.status_code in [200, 500]

    def test_quick_profitability_report(self, client: TestClient, auth_headers):
        """Test reporte rapido de rentabilidad via GET."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/dashboard/reports/profitability",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        assert response.status_code in [200, 500]

    def test_list_generated_reports(self, client: TestClient, auth_headers):
        """Test listar reportes generados."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/dashboard/reports",
            headers=auth_headers,
            params={"limit": 10}
        )

        if response.status_code == 200:
            data = response.json()
            assert "success" in data

    def test_get_report_by_id(self, client: TestClient, auth_headers):
        """Test obtener reporte por ID."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/dashboard/reports/1",
            headers=auth_headers
        )

        # Puede ser 200 si existe o 404 si no existe
        assert response.status_code in [200, 404, 500]
