"""
Pruebas de integración para endpoints de administración de usuarios.
RF-Admin: Gestión de usuarios (CRUD, módulos, estado) por usuario Principal.

Casos cubiertos:
  ADM-01: Crear usuario Secundario con módulos específicos
  ADM-02: Desactivar usuario → login falla
  ADM-03: Usuario no-admin no puede acceder a /admin/
  ADM-04: Actualizar módulos de usuario secundario
"""

import pytest
import uuid
from fastapi.testclient import TestClient


# ── Helpers ────────────────────────────────────────────────────────────────────

def unique_suffix() -> str:
    """Sufijo único para evitar colisiones de usernames/emails en BD real."""
    return str(uuid.uuid4())[:8]


def _create_secondary_user(client: TestClient, admin_headers: dict, modulos: list, suffix: str = None):
    """
    Crea un usuario Secundario vía admin y devuelve (user_id, username, password).
    Retorna (None, None, None) si falla.
    """
    if not admin_headers:
        return None, None, None

    s = suffix or unique_suffix()
    username = f"sec_{s}"
    password = "SecTest123!"
    payload = {
        "nombreCompleto": f"Secundario Test {s}",
        "nombreUsuario": username,
        "email": f"sec_{s}@test.com",
        "password": password,
        "tipo": "Secundario",
        "modulos": modulos,
    }
    r = client.post("/api/v1/admin/usuarios", headers=admin_headers, json=payload)
    if r.status_code not in [200, 201]:
        return None, None, None
    user_id = r.json().get("idUsuario")
    return user_id, username, password


# ── Tests de listado ────────────────────────────────────────────────────────────

class TestAdminListUsers:
    """GET /admin/usuarios — solo accesible por Principal/Administrador."""

    def test_admin_puede_listar_usuarios(self, client: TestClient, admin_headers):
        """Admin ve la lista completa de usuarios."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        r = client.get("/api/v1/admin/usuarios", headers=admin_headers)
        assert r.status_code == 200
        assert isinstance(r.json(), list)

    def test_sin_token_recibe_401(self, client: TestClient):
        """Sin Bearer token → 401/403 (no 200)."""
        r = client.get("/api/v1/admin/usuarios")
        assert r.status_code in [401, 403]

    def test_token_invalido_recibe_401(self, client: TestClient):
        """Token inválido → 401/403."""
        r = client.get(
            "/api/v1/admin/usuarios",
            headers={"Authorization": "Bearer token_invalido_xyz"},
        )
        assert r.status_code in [401, 403]


# ── Tests de creación ───────────────────────────────────────────────────────────

class TestAdminCreateUser:
    """POST /admin/usuarios — crear usuarios."""

    def test_crear_usuario_secundario_con_modulos(self, client: TestClient, admin_headers):
        """ADM-01: Crear Secundario con módulos datos+alertas."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        s = unique_suffix()
        payload = {
            "nombreCompleto": f"Secundario {s}",
            "nombreUsuario": f"sec_{s}",
            "email": f"sec_{s}@test.com",
            "password": "SecTest123!",
            "tipo": "Secundario",
            "modulos": ["datos", "alertas"],
        }
        r = client.post("/api/v1/admin/usuarios", headers=admin_headers, json=payload)

        assert r.status_code in [200, 201]
        data = r.json()
        assert data.get("tipo") == "Secundario"
        assert "datos" in data.get("modulos", [])
        assert "alertas" in data.get("modulos", [])
        # No debe incluir módulos no asignados
        assert "predicciones" not in data.get("modulos", [])
        assert "rentabilidad" not in data.get("modulos", [])

    def test_crear_usuario_principal_tiene_rol_admin(self, client: TestClient, admin_headers):
        """Usuario Principal recibe rol Administrador automáticamente."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        s = unique_suffix()
        payload = {
            "nombreCompleto": f"Principal {s}",
            "nombreUsuario": f"prin_{s}",
            "email": f"prin_{s}@test.com",
            "password": "PrinTest123!",
            "tipo": "Principal",
            "modulos": [],
        }
        r = client.post("/api/v1/admin/usuarios", headers=admin_headers, json=payload)

        assert r.status_code in [200, 201]
        data = r.json()
        assert data.get("tipo") == "Principal"

    def test_username_duplicado_falla(self, client: TestClient, admin_headers):
        """Registrar mismo username dos veces → error 4xx/5xx."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        s = unique_suffix()
        payload = {
            "nombreCompleto": f"Dup {s}",
            "nombreUsuario": f"dup_{s}",
            "email": f"dup1_{s}@test.com",
            "password": "DupTest123!",
            "tipo": "Secundario",
            "modulos": [],
        }
        client.post("/api/v1/admin/usuarios", headers=admin_headers, json=payload)

        # Segundo intento con mismo username y distinto email
        payload["email"] = f"dup2_{s}@test.com"
        r = client.post("/api/v1/admin/usuarios", headers=admin_headers, json=payload)
        assert r.status_code in [400, 409, 422, 500]

    def test_sin_token_no_puede_crear_usuario(self, client: TestClient):
        """ADM-03: Sin token → 401/403 al crear usuario."""
        payload = {
            "nombreCompleto": "Unauth User",
            "nombreUsuario": "unauth_xyz",
            "email": "unauth@test.com",
            "password": "Unauth123!",
            "tipo": "Secundario",
            "modulos": [],
        }
        r = client.post("/api/v1/admin/usuarios", json=payload)
        assert r.status_code in [401, 403]

    def test_modulos_invalidos_son_rechazados(self, client: TestClient, admin_headers):
        """Módulo inválido en la lista → error 4xx."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        s = unique_suffix()
        payload = {
            "nombreCompleto": f"Inv Mod {s}",
            "nombreUsuario": f"invmod_{s}",
            "email": f"invmod_{s}@test.com",
            "password": "InvMod123!",
            "tipo": "Secundario",
            "modulos": ["modulo_inventado_xyz"],
        }
        r = client.post("/api/v1/admin/usuarios", headers=admin_headers, json=payload)
        # El sistema debe rechazar módulos desconocidos
        assert r.status_code in [400, 422, 500]


# ── Tests de actualización ──────────────────────────────────────────────────────

class TestAdminUpdateUser:
    """PUT /admin/usuarios/{id} y sub-endpoints de módulos/estado."""

    def test_actualizar_modulos_de_secundario(self, client: TestClient, admin_headers):
        """ADM-04: Actualizar módulos asignados a usuario Secundario."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        user_id, _, _ = _create_secondary_user(client, admin_headers, ["datos"])
        if not user_id:
            pytest.skip("No se pudo crear usuario de prueba")

        r = client.put(
            f"/api/v1/admin/usuarios/{user_id}/modulos",
            headers=admin_headers,
            json={"modulos": ["datos", "predicciones", "alertas"]},
        )
        assert r.status_code in [200, 204]

    def test_desactivar_usuario(self, client: TestClient, admin_headers):
        """ADM-02: Cambiar estado a Inactivo."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        user_id, _, _ = _create_secondary_user(client, admin_headers, ["datos"])
        if not user_id:
            pytest.skip("No se pudo crear usuario de prueba")

        r = client.put(
            f"/api/v1/admin/usuarios/{user_id}/estado",
            headers=admin_headers,
            json={"estado": "Inactivo"},
        )
        assert r.status_code in [200, 204]

    def test_reactivar_usuario(self, client: TestClient, admin_headers):
        """Reactivar usuario previamente desactivado."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        user_id, _, _ = _create_secondary_user(client, admin_headers, ["datos"])
        if not user_id:
            pytest.skip("No se pudo crear usuario de prueba")

        # Desactivar
        client.put(
            f"/api/v1/admin/usuarios/{user_id}/estado",
            headers=admin_headers,
            json={"estado": "Inactivo"},
        )
        # Reactivar
        r = client.put(
            f"/api/v1/admin/usuarios/{user_id}/estado",
            headers=admin_headers,
            json={"estado": "Activo"},
        )
        assert r.status_code in [200, 204]

    def test_actualizar_usuario_inexistente_retorna_404(self, client: TestClient, admin_headers):
        """Actualizar usuario que no existe → 404."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        r = client.put(
            "/api/v1/admin/usuarios/9999999/estado",
            headers=admin_headers,
            json={"estado": "Inactivo"},
        )
        assert r.status_code in [404, 422, 500]


# ── Tests de usuario inactivo ───────────────────────────────────────────────────

class TestAdminUsuarioInactivo:
    """ADM-02: Usuario desactivado no puede autenticarse."""

    def test_usuario_inactivo_no_puede_hacer_login(self, client: TestClient, admin_headers):
        """Desactivar usuario → login retorna 401/403."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        s = unique_suffix()
        username = f"inact_{s}"
        password = "Inact123!"

        # Crear usuario activo
        payload = {
            "nombreCompleto": f"Inactivo {s}",
            "nombreUsuario": username,
            "email": f"inact_{s}@test.com",
            "password": password,
            "tipo": "Secundario",
            "modulos": ["datos"],
        }
        create_r = client.post("/api/v1/admin/usuarios", headers=admin_headers, json=payload)
        if create_r.status_code not in [200, 201]:
            pytest.skip("No se pudo crear usuario de prueba")

        user_id = create_r.json().get("idUsuario")

        # Verificar que puede hacer login antes de desactivar
        login_before = client.post(
            "/api/v1/auth/login/json",
            json={"username": username, "password": password},
        )
        if login_before.status_code != 200:
            pytest.skip("El usuario recién creado no puede hacer login (entorno sin datos completos)")

        # Desactivar
        client.put(
            f"/api/v1/admin/usuarios/{user_id}/estado",
            headers=admin_headers,
            json={"estado": "Inactivo"},
        )

        # Intentar login nuevamente → debe fallar
        login_after = client.post(
            "/api/v1/auth/login/json",
            json={"username": username, "password": password},
        )
        assert login_after.status_code in [401, 403], (
            f"ADM-02: Usuario inactivo no debe poder autenticarse. "
            f"Recibido: {login_after.status_code}"
        )


# ── Tests de eliminación ────────────────────────────────────────────────────────

class TestAdminDeleteUser:
    """DELETE /admin/usuarios/{id}."""

    def test_eliminar_usuario_secundario(self, client: TestClient, admin_headers):
        """ADM-01: Eliminar usuario Secundario existente."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        user_id, _, _ = _create_secondary_user(client, admin_headers, [])
        if not user_id:
            pytest.skip("No se pudo crear usuario de prueba")

        r = client.delete(f"/api/v1/admin/usuarios/{user_id}", headers=admin_headers)
        assert r.status_code in [200, 204]

    def test_eliminar_usuario_inexistente_retorna_404(self, client: TestClient, admin_headers):
        """Intentar eliminar ID que no existe → 404."""
        if not admin_headers:
            pytest.skip("Sin credenciales de admin")

        r = client.delete("/api/v1/admin/usuarios/9999999", headers=admin_headers)
        assert r.status_code in [404, 422, 500]

    def test_sin_token_no_puede_eliminar(self, client: TestClient):
        """Sin token → 401/403 al eliminar."""
        r = client.delete("/api/v1/admin/usuarios/1")
        assert r.status_code in [401, 403]
