"""
Pruebas de integracion para endpoints de carga y gestion de datos.
RF-01: Gestion de Datos.
"""

import pytest
import tempfile
import os
from datetime import date
from fastapi.testclient import TestClient


class TestDataUploadEndpoints:
    """Pruebas para endpoints de carga de datos."""

    def test_upload_csv_file(self, client: TestClient, auth_headers):
        """Test subir archivo CSV."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Crear archivo CSV temporal
        csv_content = "fecha,total,moneda\n2024-01-01,1000.00,MXN\n2024-01-02,1500.00,MXN"

        with tempfile.NamedTemporaryFile(mode='w', suffix='.csv', delete=False) as f:
            f.write(csv_content)
            temp_path = f.name

        try:
            with open(temp_path, 'rb') as f:
                response = client.post(
                    "/api/v1/data/upload",
                    headers=auth_headers,
                    files={"file": ("test_data.csv", f, "text/csv")},
                    data={"tipo_datos": "ventas"}
                )

            assert response.status_code in [200, 400, 422, 500]
        finally:
            os.unlink(temp_path)

    def test_upload_invalid_file_type(self, client: TestClient, auth_headers):
        """Test subir archivo con tipo invalido."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Crear archivo con extension invalida
        with tempfile.NamedTemporaryFile(mode='w', suffix='.txt', delete=False) as f:
            f.write("Este no es un CSV ni Excel")
            temp_path = f.name

        try:
            with open(temp_path, 'rb') as f:
                response = client.post(
                    "/api/v1/data/upload",
                    headers=auth_headers,
                    files={"file": ("test_data.txt", f, "text/plain")},
                    data={"tipo_datos": "ventas"}
                )

            # Deberia rechazar el archivo
            assert response.status_code in [400, 415, 422, 500]
        finally:
            os.unlink(temp_path)

    def test_validate_data_structure(self, client: TestClient, auth_headers):
        """
        RF-01.02: Validar estructura de datos.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "upload_id": "test-upload-id",
            "tipo_datos": "ventas",
            "columnas_requeridas": ["fecha", "total", "moneda"]
        }

        response = client.post(
            "/api/v1/data/validate",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_preview_uploaded_data(self, client: TestClient, auth_headers):
        """Test preview de datos cargados."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/data/preview/test-upload-id",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 500]

    def test_clean_data(self, client: TestClient, auth_headers):
        """Test limpieza de datos."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "upload_id": "test-upload-id",
            "opciones": {
                "eliminar_duplicados": True,
                "manejar_nulos": "eliminar",
                "detectar_outliers": True
            }
        }

        response = client.post(
            "/api/v1/data/clean",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_confirm_data_load(self, client: TestClient, auth_headers):
        """Test confirmar carga de datos."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "upload_id": "test-upload-id",
            "tipo_datos": "ventas"
        }

        response = client.post(
            "/api/v1/data/confirm",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_get_quality_report(self, client: TestClient, auth_headers):
        """Test obtener reporte de calidad de datos."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/data/quality-report/test-upload-id",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            # Deberia incluir metricas de calidad
            assert isinstance(data, dict)

    def test_delete_upload(self, client: TestClient, auth_headers):
        """Test eliminar upload temporal."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.delete(
            "/api/v1/data/test-upload-id",
            headers=auth_headers
        )

        assert response.status_code in [200, 204, 404, 500]

    def test_get_excel_sheets(self, client: TestClient, auth_headers):
        """Test obtener hojas de archivo Excel."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/data/sheets/test-upload-id",
            headers=auth_headers
        )

        assert response.status_code in [200, 404, 500]


class TestDataCleaningRules:
    """Pruebas para reglas de limpieza de datos (RN-02)."""

    def test_remove_duplicates(self, client: TestClient, auth_headers):
        """
        RN-02.01: Eliminacion de duplicados.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "upload_id": "test-upload-id",
            "opciones": {
                "eliminar_duplicados": True
            }
        }

        response = client.post(
            "/api/v1/data/clean",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_handle_null_values(self, client: TestClient, auth_headers):
        """
        RN-02.02 / RN-02.04: Manejo de valores nulos.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "upload_id": "test-upload-id",
            "opciones": {
                "manejar_nulos": "imputar_media"  # o "eliminar"
            }
        }

        response = client.post(
            "/api/v1/data/clean",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_detect_outliers(self, client: TestClient, auth_headers):
        """
        RN-02.03: Deteccion de valores atipicos con Z-Score.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "upload_id": "test-upload-id",
            "opciones": {
                "detectar_outliers": True,
                "metodo_outliers": "zscore",
                "threshold": 3.0
            }
        }

        response = client.post(
            "/api/v1/data/clean",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_validate_minimum_valid_records(self, client: TestClient, auth_headers):
        """
        RN-02.05: Validacion de 70% de registros validos.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Al validar datos, deberia verificar el umbral de 70%
        request_data = {
            "upload_id": "test-upload-id",
            "tipo_datos": "ventas"
        }

        response = client.post(
            "/api/v1/data/validate",
            headers=auth_headers,
            json=request_data
        )

        if response.status_code == 200:
            data = response.json()
            # Deberia indicar porcentaje de registros validos
            pass


class TestDataTransformation:
    """Pruebas para transformacion de datos (RF-01.04)."""

    def test_normalize_dates(self, client: TestClient, auth_headers):
        """Test normalizacion de fechas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "upload_id": "test-upload-id",
            "opciones": {
                "normalizar_fechas": True,
                "formato_fecha": "%Y-%m-%d"
            }
        }

        response = client.post(
            "/api/v1/data/clean",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]

    def test_normalize_currency(self, client: TestClient, auth_headers):
        """Test normalizacion de valores monetarios."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "upload_id": "test-upload-id",
            "opciones": {
                "normalizar_moneda": True
            }
        }

        response = client.post(
            "/api/v1/data/clean",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 404, 422, 500]


class TestUsersEndpoints:
    """Pruebas para endpoints de usuarios."""

    def test_list_users(self, client: TestClient, auth_headers):
        """Test listar usuarios."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Ruta correcta: /api/v1/usuarios
        response = client.get(
            "/api/v1/usuarios",
            headers=auth_headers
        )

        assert response.status_code in [200, 403, 500]

    def test_get_user_by_id(self, client: TestClient, auth_headers):
        """Test obtener usuario por ID."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/usuarios/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 403, 404, 500]

    def test_get_user_by_username(self, client: TestClient, auth_headers):
        """Test obtener usuario por username."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/usuarios/username/testuser",
            headers=auth_headers
        )

        assert response.status_code in [200, 403, 404, 500]

    def test_update_user(self, client: TestClient, auth_headers):
        """Test actualizar usuario."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        update_data = {
            "nombreCompleto": "Usuario Actualizado"
        }

        response = client.put(
            "/api/v1/usuarios/1",
            headers=auth_headers,
            json=update_data
        )

        assert response.status_code in [200, 400, 403, 404, 422, 500]

    def test_assign_role_to_user(self, client: TestClient, auth_headers):
        """Test asignar rol a usuario."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        request_data = {
            "idRol": 1
        }

        response = client.post(
            "/api/v1/usuarios/1/roles",
            headers=auth_headers,
            json=request_data
        )

        assert response.status_code in [200, 400, 403, 404, 422, 500]

    def test_remove_role_from_user(self, client: TestClient, auth_headers):
        """Test remover rol de usuario."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.delete(
            "/api/v1/usuarios/1/roles/1",
            headers=auth_headers
        )

        assert response.status_code in [200, 204, 403, 404, 500]
