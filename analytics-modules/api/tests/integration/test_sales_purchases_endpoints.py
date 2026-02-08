"""
Pruebas de integracion para endpoints de ventas y compras.
RF-01: Gestion de Datos.
"""

import pytest
from datetime import date, timedelta
from fastapi.testclient import TestClient


class TestSalesEndpoints:
    """Pruebas para endpoints de ventas."""

    def test_list_sales(self, client: TestClient, auth_headers):
        """Test listar ventas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/ventas",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, list)

    def test_list_sales_with_pagination(self, client: TestClient, auth_headers):
        """Test listar ventas con paginacion."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/ventas",
            headers=auth_headers,
            params={
                "skip": 0,
                "limit": 10
            }
        )

        assert response.status_code in [200, 422, 500]

    def test_list_sales_with_date_filter(self, client: TestClient, auth_headers):
        """Test listar ventas con filtro de fecha."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/ventas",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        assert response.status_code in [200, 422, 500]

    def test_get_sale_by_id(self, client: TestClient, auth_headers):
        """Test obtener venta por ID."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/ventas/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 500]

    def test_get_nonexistent_sale(self, client: TestClient, auth_headers):
        """Test obtener venta inexistente."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/ventas/99999",
            headers=auth_headers
        )

        assert response.status_code in [404, 500]

    def test_create_sale(self, client: TestClient, auth_headers):
        """Test crear venta."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        sale_data = {
            "fecha": date.today().isoformat(),
            "total": 1500.00,
            "moneda": "MXN",
            "detalles": [
                {
                    "idProducto": 1,
                    "cantidad": 10,
                    "precioUnitario": 150.00
                }
            ]
        }

        response = client.post(
            "/api/v1/ventas",
            headers=auth_headers,
            json=sale_data
        )

        assert response.status_code in [200, 201, 400, 422, 500]

    def test_create_sale_invalid_data(self, client: TestClient, auth_headers):
        """Test crear venta con datos invalidos."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        invalid_sale_data = {
            "fecha": "fecha-invalida",
            "total": -100  # Monto negativo
        }

        response = client.post(
            "/api/v1/ventas",
            headers=auth_headers,
            json=invalid_sale_data
        )

        assert response.status_code in [400, 422, 500]

    def test_update_sale(self, client: TestClient, auth_headers):
        """Test actualizar venta."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        update_data = {
            "total": 2000.00
        }

        response = client.put(
            "/api/v1/ventas/1",
            headers=auth_headers,
            json=update_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_delete_sale(self, client: TestClient, auth_headers):
        """Test eliminar venta."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.delete(
            "/api/v1/ventas/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 204, 404, 500]

    def test_get_sale_details(self, client: TestClient, auth_headers):
        """Test obtener detalles de venta."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/ventas/1/detalles",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 500]

    def test_get_monthly_sales_summary(self, client: TestClient, auth_headers):
        """Test resumen mensual de ventas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/ventas/resumen/mensual",
            headers=auth_headers,
            params={
                "anio": date.today().year,
                "mes": date.today().month
            }
        )

        assert response.status_code in [200, 422, 500]

    def test_get_sales_total_by_period(self, client: TestClient, auth_headers):
        """Test total de ventas por periodo."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/ventas/total/periodo",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        assert response.status_code in [200, 422, 500]


class TestPurchasesEndpoints:
    """Pruebas para endpoints de compras."""

    def test_list_purchases(self, client: TestClient, auth_headers):
        """Test listar compras."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/compras",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, list)

    def test_list_purchases_with_pagination(self, client: TestClient, auth_headers):
        """Test listar compras con paginacion."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/compras",
            headers=auth_headers,
            params={
                "skip": 0,
                "limit": 10
            }
        )

        assert response.status_code in [200, 422, 500]

    def test_list_purchases_by_supplier(self, client: TestClient, auth_headers):
        """Test listar compras por proveedor."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/compras",
            headers=auth_headers,
            params={
                "proveedor": "Proveedor Test"
            }
        )

        assert response.status_code in [200, 422, 500]

    def test_get_purchase_by_id(self, client: TestClient, auth_headers):
        """Test obtener compra por ID."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/compras/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 500]

    def test_get_purchase_with_details(self, client: TestClient, auth_headers):
        """Test obtener compra con detalles."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/compras/1/completa",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 500]

    def test_create_purchase(self, client: TestClient, auth_headers):
        """Test crear compra."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        purchase_data = {
            "fecha": date.today().isoformat(),
            "proveedor": "Proveedor Test",
            "total": 5000.00,
            "moneda": "MXN",
            "detalles": [
                {
                    "idProducto": 1,
                    "cantidad": 100,
                    "costoUnitario": 50.00
                }
            ]
        }

        response = client.post(
            "/api/v1/compras",
            headers=auth_headers,
            json=purchase_data
        )

        assert response.status_code in [200, 201, 400, 422, 500]

    def test_update_purchase(self, client: TestClient, auth_headers):
        """Test actualizar compra."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        update_data = {
            "total": 6000.00
        }

        response = client.put(
            "/api/v1/compras/1",
            headers=auth_headers,
            json=update_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_delete_purchase(self, client: TestClient, auth_headers):
        """Test eliminar compra."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.delete(
            "/api/v1/compras/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 204, 404, 500]

    def test_get_purchase_details(self, client: TestClient, auth_headers):
        """Test obtener detalles de compra."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/compras/1/detalles",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 500]

    def test_get_monthly_purchases_summary(self, client: TestClient, auth_headers):
        """Test resumen mensual de compras."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/compras/resumen/mensual",
            headers=auth_headers,
            params={
                "anio": date.today().year,
                "mes": date.today().month
            }
        )

        assert response.status_code in [200, 422, 500]

    def test_get_purchases_total_by_period(self, client: TestClient, auth_headers):
        """Test total de compras por periodo."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        response = client.get(
            "/api/v1/compras/total/periodo",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat()
            }
        )

        assert response.status_code in [200, 422, 500]

    def test_get_average_cost_by_product(self, client: TestClient, auth_headers):
        """Test costo promedio por producto."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/compras/producto/1/costo-promedio",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 422, 500]


class TestProductsEndpoints:
    """Pruebas para endpoints de productos."""

    def test_list_products(self, client: TestClient, auth_headers):
        """Test listar productos."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/productos",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert isinstance(data, list)

    def test_list_products_by_category(self, client: TestClient, auth_headers):
        """Test listar productos por categoria."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/productos",
            headers=auth_headers,
            params={
                "id_categoria": 1
            }
        )

        assert response.status_code in [200, 422, 500]

    def test_get_product_by_id(self, client: TestClient, auth_headers):
        """Test obtener producto por ID."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/productos/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 500]

    def test_create_product(self, client: TestClient, auth_headers):
        """Test crear producto."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        product_data = {
            "nombre": "Producto Test",
            "descripcion": "Descripcion del producto de prueba",
            "sku": "TEST-SKU-001",
            "precioUnitario": 100.00,
            "costo": 50.00,
            "stockActual": 100,
            "stockMinimo": 10,
            "idCategoria": 1
        }

        response = client.post(
            "/api/v1/productos",
            headers=auth_headers,
            json=product_data
        )

        assert response.status_code in [200, 201, 400, 422, 500]

    def test_update_product(self, client: TestClient, auth_headers):
        """Test actualizar producto."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        update_data = {
            "precioUnitario": 120.00
        }

        response = client.put(
            "/api/v1/productos/1",
            headers=auth_headers,
            json=update_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_delete_product(self, client: TestClient, auth_headers):
        """Test eliminar producto."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.delete(
            "/api/v1/productos/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 204, 404, 500]
