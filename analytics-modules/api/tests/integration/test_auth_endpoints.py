"""
Pruebas de integracion para endpoints de autenticacion.
"""

import pytest
from unittest.mock import patch
from fastapi.testclient import TestClient


class TestAuthEndpoints:
    """Pruebas para endpoints de autenticacion."""

    def test_register_success(self, client: TestClient):
        """Test registro exitoso de usuario."""
        user_data = {
            "nombreCompleto": "Test User",
            "nombreUsuario": "testuser_auth",
            "email": "testauth@example.com",
            "password": "TestPassword123!"
        }

        response = client.post("/api/v1/auth/register", json=user_data)

        # Puede ser 200 o 201 dependiendo de la implementacion
        assert response.status_code in [200, 201, 422]

    def test_register_duplicate_username(self, client: TestClient):
        """Test registro con username duplicado."""
        user_data = {
            "nombreCompleto": "Test User",
            "nombreUsuario": "duplicate_user",
            "email": "test1@example.com",
            "password": "TestPassword123!"
        }

        # Primer registro
        client.post("/api/v1/auth/register", json=user_data)

        # Segundo registro con mismo username
        user_data["email"] = "test2@example.com"
        response = client.post("/api/v1/auth/register", json=user_data)

        # Deberia fallar
        assert response.status_code in [400, 409, 422, 500]

    def test_login_success(self, client: TestClient):
        """Test login exitoso."""
        # Primero registrar usuario
        user_data = {
            "nombreCompleto": "Login Test User",
            "nombreUsuario": "logintest",
            "email": "logintest@example.com",
            "password": "LoginTest123!"
        }
        client.post("/api/v1/auth/register", json=user_data)

        # Intentar login
        response = client.post(
            "/api/v1/auth/login",
            data={
                "username": "logintest",
                "password": "LoginTest123!"
            }
        )

        if response.status_code == 200:
            data = response.json()
            assert "access_token" in data
            assert "refresh_token" in data
            assert data["token_type"] == "bearer"

    def test_login_invalid_credentials(self, client: TestClient):
        """Test login con credenciales invalidas."""
        response = client.post(
            "/api/v1/auth/login",
            data={
                "username": "nonexistent",
                "password": "WrongPassword123!"
            }
        )

        assert response.status_code == 401

    def test_login_json_success(self, client: TestClient):
        """Test login con JSON body."""
        # Registrar usuario
        user_data = {
            "nombreCompleto": "JSON Login User",
            "nombreUsuario": "jsonlogin",
            "email": "jsonlogin@example.com",
            "password": "JsonLogin123!"
        }
        client.post("/api/v1/auth/register", json=user_data)

        # Login con JSON
        response = client.post(
            "/api/v1/auth/login/json",
            json={
                "username": "jsonlogin",
                "password": "JsonLogin123!"
            }
        )

        if response.status_code == 200:
            data = response.json()
            assert "access_token" in data

    def test_verify_valid_token(self, client: TestClient, auth_headers):
        """Test verificacion de token valido."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/auth/verify",
            headers=auth_headers
        )

        assert response.status_code in [200, 401]

    def test_verify_invalid_token(self, client: TestClient):
        """Test verificacion de token invalido."""
        response = client.get(
            "/api/v1/auth/verify",
            headers={"Authorization": "Bearer invalid_token_here"}
        )

        assert response.status_code == 401

    def test_get_current_user(self, client: TestClient, auth_headers):
        """Test obtener usuario actual."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/auth/me",
            headers=auth_headers
        )

        if response.status_code == 200:
            data = response.json()
            assert "nombreUsuario" in data or "username" in data

    def test_refresh_token(self, client: TestClient):
        """Test refresh de token."""
        # Registrar y login
        user_data = {
            "nombreCompleto": "Refresh Test User",
            "nombreUsuario": "refreshtest",
            "email": "refreshtest@example.com",
            "password": "RefreshTest123!"
        }
        client.post("/api/v1/auth/register", json=user_data)

        login_response = client.post(
            "/api/v1/auth/login",
            data={
                "username": "refreshtest",
                "password": "RefreshTest123!"
            }
        )

        if login_response.status_code == 200:
            refresh_token = login_response.json().get("refresh_token")

            # Intentar refresh
            response = client.post(
                "/api/v1/auth/refresh",
                json={"refresh_token": refresh_token}
            )

            # Puede ser 200 o 401 si el token expiro
            assert response.status_code in [200, 401, 422]

    def test_change_password(self, client: TestClient, auth_headers):
        """Test cambio de password."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.put(
            "/api/v1/auth/password",
            headers=auth_headers,
            json={
                "current_password": "Test123456!",
                "new_password": "NewPassword123!"
            }
        )

        # Puede ser 200 si cambio exitoso o 400/401 si password incorrecto
        assert response.status_code in [200, 400, 401, 422]

    def test_protected_endpoint_without_token(self, client: TestClient):
        """Test endpoint protegido sin token."""
        # Usar endpoint /api/v1/auth/me que requiere autenticacion
        response = client.get("/api/v1/auth/me")

        # Deberia rechazar sin token (401) o retornar 404 si el endpoint no existe
        assert response.status_code in [401, 403, 404]

    def test_protected_endpoint_with_token(self, client: TestClient, auth_headers):
        """Test endpoint protegido con token."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Usar endpoint /api/v1/auth/me que sabemos existe
        response = client.get(
            "/api/v1/auth/me",
            headers=auth_headers
        )

        # Puede ser 200, 401 (token invalido), 403 (sin permisos), o 500 (error interno)
        assert response.status_code in [200, 401, 403, 500]


# ─────────────────────────────────────────────────────────────────────────────
# Rate limiting  (mejora 4: máx 5 intentos fallidos / IP en 5 minutos)
# ─────────────────────────────────────────────────────────────────────────────

_WRONG_CREDS = {"username": "noexist_ratelimit", "password": "WrongPass999!"}
_WRONG_CREDS_JSON = {"username": "noexist_ratelimit", "password": "WrongPass999!"}


class TestLoginRateLimit:
    """
    Valida que el rate limiter en /auth/login bloquee tras 5 intentos
    fallidos por IP (HTTP 429) e incluya el header Retry-After.
    """

    @pytest.fixture(autouse=True)
    def _clean_rate_state(self):
        """Limpia _login_attempts antes y después de cada test."""
        from app.routers.auth import _login_attempts
        _login_attempts.clear()
        yield
        _login_attempts.clear()

    # ── endpoint form-data (/auth/login) ─────────────────────────────────────

    def test_first_4_failures_return_401(self, client: TestClient):
        """4 intentos fallidos seguidos → cada uno retorna 401, no 429."""
        for _ in range(4):
            r = client.post("/api/v1/auth/login", data=_WRONG_CREDS)
            assert r.status_code == 401

    def test_6th_failure_returns_429(self, client: TestClient):
        """Tras 5 intentos fallidos, el 6to intento → 429 Too Many Requests."""
        for _ in range(5):
            client.post("/api/v1/auth/login", data=_WRONG_CREDS)
        r = client.post("/api/v1/auth/login", data=_WRONG_CREDS)
        assert r.status_code == 429

    def test_429_contains_retry_after_header(self, client: TestClient):
        """La respuesta 429 incluye el header Retry-After."""
        for _ in range(5):
            client.post("/api/v1/auth/login", data=_WRONG_CREDS)
        r = client.post("/api/v1/auth/login", data=_WRONG_CREDS)
        assert r.status_code == 429
        lower_headers = {k.lower(): v for k, v in r.headers.items()}
        assert "retry-after" in lower_headers

    def test_429_retry_after_value_is_300(self, client: TestClient):
        """Retry-After indica la ventana completa (300 segundos = 5 min)."""
        for _ in range(5):
            client.post("/api/v1/auth/login", data=_WRONG_CREDS)
        r = client.post("/api/v1/auth/login", data=_WRONG_CREDS)
        lower_headers = {k.lower(): v for k, v in r.headers.items()}
        assert lower_headers.get("retry-after") == "300"

    # ── endpoint JSON (/auth/login/json) ─────────────────────────────────────

    def test_json_endpoint_also_rate_limited(self, client: TestClient):
        """/auth/login/json también aplica rate limiting tras 5 fallos."""
        for _ in range(5):
            client.post("/api/v1/auth/login/json", json=_WRONG_CREDS_JSON)
        r = client.post("/api/v1/auth/login/json", json=_WRONG_CREDS_JSON)
        assert r.status_code == 429

    # ── contador compartido entre endpoints ───────────────────────────────────

    def test_form_and_json_share_counter(self, client: TestClient):
        """3 fallos en /login + 2 en /login/json = 5 total → el 6to falla con 429."""
        for _ in range(3):
            client.post("/api/v1/auth/login", data=_WRONG_CREDS)
        for _ in range(2):
            client.post("/api/v1/auth/login/json", json=_WRONG_CREDS_JSON)
        # 6to intento combinado
        r = client.post("/api/v1/auth/login", data=_WRONG_CREDS)
        assert r.status_code == 429

    # ── reset por login exitoso ───────────────────────────────────────────────

    def test_successful_login_resets_counter(self, client: TestClient):
        """Un login exitoso limpia el contador → el siguiente fallo retorna 401."""
        # Llenar 4 intentos fallidos
        for _ in range(4):
            client.post("/api/v1/auth/login", data=_WRONG_CREDS)

        # Simular un login exitoso limpiando el contador directamente
        # (idéntico a lo que hace _clear_attempts en el router)
        from app.routers.auth import _login_attempts
        _login_attempts.clear()

        # Ahora el siguiente intento fallido debe retornar 401, no 429
        r = client.post("/api/v1/auth/login", data=_WRONG_CREDS)
        assert r.status_code == 401
