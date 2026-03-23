"""
Tests para middleware de autenticacion.
Cubre get_current_user, get_current_active_user y require_roles.

Cambios reflejados:
- get_current_user ahora lanza 401 (antes retornaba None) — B1
- require_roles ahora verifica usuario activo antes de roles — B3
"""

import pytest
from unittest.mock import Mock, patch
from fastapi import HTTPException


# ─────────────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────────────

def _mock_token_data(user_id: int = 1, roles: list = None):
    td = Mock()
    td.idUsuario = user_id
    td.roles = roles or []
    return td


def _mock_user(estado: str = "Activo"):
    u = Mock()
    u.estado = estado
    return u


def _patch_auth(token_data, user=None):
    """Context manager que parchea AuthService y UsuarioRepository."""
    from contextlib import ExitStack
    stack = ExitStack()
    mock_auth = stack.enter_context(
        patch("app.middleware.auth_middleware.AuthService")
    )
    mock_repo = stack.enter_context(
        patch("app.repositories.UsuarioRepository")
    )
    mock_auth.return_value.verify_token.return_value = token_data
    mock_repo.return_value.get_by_id.return_value = user
    return stack


# ─────────────────────────────────────────────────────────────────────────────
# get_current_user
# ─────────────────────────────────────────────────────────────────────────────

class TestGetCurrentUser:
    """
    get_current_user ahora lanza 401 en lugar de retornar None.
    Cualquier endpoint que lo use retorna 401 sin token válido.
    """

    @pytest.fixture
    def db(self):
        return Mock()

    @pytest.mark.asyncio
    async def test_no_token_raises_401(self, db):
        """Sin token → 401 (no retorna None)."""
        from app.middleware.auth_middleware import get_current_user
        with pytest.raises(HTTPException) as exc:
            await get_current_user(token=None, db=db)
        assert exc.value.status_code == 401

    @pytest.mark.asyncio
    async def test_invalid_token_raises_401(self, db):
        """Token inválido → 401."""
        from app.middleware.auth_middleware import get_current_user
        with patch("app.middleware.auth_middleware.AuthService") as mock_auth:
            mock_auth.return_value.verify_token.return_value = None
            with pytest.raises(HTTPException) as exc:
                await get_current_user(token="bad_token", db=db)
        assert exc.value.status_code == 401

    @pytest.mark.asyncio
    async def test_user_not_in_db_raises_401(self, db):
        """Token válido pero usuario borrado de BD → 401."""
        from app.middleware.auth_middleware import get_current_user
        with _patch_auth(_mock_token_data(), user=None):
            with pytest.raises(HTTPException) as exc:
                await get_current_user(token="valid_token", db=db)
        assert exc.value.status_code == 401

    @pytest.mark.asyncio
    async def test_valid_token_returns_user(self, db):
        """Token válido + usuario en BD → retorna usuario."""
        from app.middleware.auth_middleware import get_current_user
        expected = _mock_user()
        with _patch_auth(_mock_token_data(), user=expected):
            result = await get_current_user(token="valid_token", db=db)
        assert result is expected

    @pytest.mark.asyncio
    async def test_inactive_user_is_returned(self, db):
        """
        get_current_user NO verifica estado activo (solo get_current_active_user
        y require_roles lo hacen). Usuario inactivo igual retorna el objeto.
        """
        from app.middleware.auth_middleware import get_current_user
        inactive = _mock_user(estado="inactivo")
        with _patch_auth(_mock_token_data(), user=inactive):
            result = await get_current_user(token="valid_token", db=db)
        assert result is inactive


# ─────────────────────────────────────────────────────────────────────────────
# get_current_active_user
# ─────────────────────────────────────────────────────────────────────────────

class TestGetCurrentActiveUser:

    @pytest.fixture
    def db(self):
        return Mock()

    @pytest.mark.asyncio
    async def test_no_token_raises_401(self, db):
        from app.middleware.auth_middleware import get_current_active_user
        with pytest.raises(HTTPException) as exc:
            await get_current_active_user(token=None, db=db)
        assert exc.value.status_code == 401

    @pytest.mark.asyncio
    async def test_invalid_token_raises_401(self, db):
        from app.middleware.auth_middleware import get_current_active_user
        with patch("app.middleware.auth_middleware.AuthService") as mock_auth:
            mock_auth.return_value.verify_token.return_value = None
            with pytest.raises(HTTPException) as exc:
                await get_current_active_user(token="bad", db=db)
        assert exc.value.status_code == 401

    @pytest.mark.asyncio
    async def test_user_not_found_raises_401(self, db):
        from app.middleware.auth_middleware import get_current_active_user
        with _patch_auth(_mock_token_data(), user=None):
            with pytest.raises(HTTPException) as exc:
                await get_current_active_user(token="valid", db=db)
        assert exc.value.status_code == 401

    @pytest.mark.asyncio
    @pytest.mark.parametrize("estado", ["inactivo", "Inactivo", "INACTIVO", "suspendido"])
    async def test_inactive_user_raises_403(self, db, estado):
        """Cualquier estado que no sea 'activo' (case-insensitive) → 403."""
        from app.middleware.auth_middleware import get_current_active_user
        with _patch_auth(_mock_token_data(), user=_mock_user(estado=estado)):
            with pytest.raises(HTTPException) as exc:
                await get_current_active_user(token="valid", db=db)
        assert exc.value.status_code == 403

    @pytest.mark.asyncio
    @pytest.mark.parametrize("estado", ["Activo", "activo", "ACTIVO"])
    async def test_active_user_returns_user(self, db, estado):
        """'activo' (case-insensitive) → retorna usuario."""
        from app.middleware.auth_middleware import get_current_active_user
        user = _mock_user(estado=estado)
        with _patch_auth(_mock_token_data(), user=user):
            result = await get_current_active_user(token="valid", db=db)
        assert result is user

    @pytest.mark.asyncio
    async def test_estado_none_treated_as_active(self, db):
        """estado=None se trata como activo (usuario sin estado explícito)."""
        from app.middleware.auth_middleware import get_current_active_user
        user = _mock_user(estado=None)
        with _patch_auth(_mock_token_data(), user=user):
            result = await get_current_active_user(token="valid", db=db)
        assert result is user


# ─────────────────────────────────────────────────────────────────────────────
# require_roles
# ─────────────────────────────────────────────────────────────────────────────

class TestRequireRoles:

    @pytest.fixture
    def db(self):
        return Mock()

    def test_returns_callable(self):
        from app.middleware.auth_middleware import require_roles
        assert callable(require_roles(["Admin"]))

    @pytest.mark.asyncio
    async def test_no_token_raises_401(self, db):
        from app.middleware.auth_middleware import require_roles
        checker = require_roles(["Admin"])
        with pytest.raises(HTTPException) as exc:
            await checker(token=None, db=db)
        assert exc.value.status_code == 401

    @pytest.mark.asyncio
    async def test_invalid_token_raises_401(self, db):
        from app.middleware.auth_middleware import require_roles
        checker = require_roles(["Admin"])
        with patch("app.middleware.auth_middleware.AuthService") as mock_auth:
            mock_auth.return_value.verify_token.return_value = None
            with pytest.raises(HTTPException) as exc:
                await checker(token="bad", db=db)
        assert exc.value.status_code == 401

    @pytest.mark.asyncio
    async def test_user_not_found_raises_401(self, db):
        from app.middleware.auth_middleware import require_roles
        checker = require_roles(["Admin"])
        with _patch_auth(_mock_token_data(roles=["Admin"]), user=None):
            with pytest.raises(HTTPException) as exc:
                await checker(token="valid", db=db)
        assert exc.value.status_code == 401

    # ── B3: verificación de usuario activo ───────────────────────────────────

    @pytest.mark.asyncio
    async def test_inactive_user_with_correct_role_raises_403(self, db):
        """
        B3: Usuario con el rol correcto pero inactivo → 403.
        Antes de este fix, un usuario desactivado con token vigente
        pasaba el filtro de roles.
        """
        from app.middleware.auth_middleware import require_roles
        checker = require_roles(["Admin"])
        td = _mock_token_data(roles=["Admin"])
        inactive = _mock_user(estado="inactivo")
        with _patch_auth(td, user=inactive):
            with pytest.raises(HTTPException) as exc:
                await checker(token="valid", db=db)
        assert exc.value.status_code == 403

    @pytest.mark.asyncio
    async def test_active_user_without_role_raises_403(self, db):
        """Usuario activo pero sin el rol requerido → 403."""
        from app.middleware.auth_middleware import require_roles
        checker = require_roles(["Admin"])
        td = _mock_token_data(roles=["Operativo"])
        user = _mock_user(estado="Activo")
        with _patch_auth(td, user=user):
            with pytest.raises(HTTPException) as exc:
                await checker(token="valid", db=db)
        assert exc.value.status_code == 403

    @pytest.mark.asyncio
    async def test_active_user_with_role_returns_user(self, db):
        """Usuario activo + rol correcto → retorna usuario."""
        from app.middleware.auth_middleware import require_roles
        checker = require_roles(["Admin", "Administrador"])
        td = _mock_token_data(roles=["Administrador"])
        user = _mock_user(estado="Activo")
        with _patch_auth(td, user=user):
            result = await checker(token="valid", db=db)
        assert result is user

    @pytest.mark.asyncio
    async def test_any_allowed_role_passes(self, db):
        """Basta con uno de los roles permitidos."""
        from app.middleware.auth_middleware import require_roles
        checker = require_roles(["Admin", "Administrador", "Operativo"])
        td = _mock_token_data(roles=["Operativo"])
        user = _mock_user(estado="Activo")
        with _patch_auth(td, user=user):
            result = await checker(token="valid", db=db)
        assert result is user

    @pytest.mark.asyncio
    async def test_empty_roles_in_token_raises_403(self, db):
        """Token sin roles → 403."""
        from app.middleware.auth_middleware import require_roles
        checker = require_roles(["Admin"])
        td = _mock_token_data(roles=None)
        user = _mock_user(estado="Activo")
        with _patch_auth(td, user=user):
            with pytest.raises(HTTPException) as exc:
                await checker(token="valid", db=db)
        assert exc.value.status_code == 403


# ─────────────────────────────────────────────────────────────────────────────
# Dependencias preconfiguradas
# ─────────────────────────────────────────────────────────────────────────────

class TestPreConfiguredDependencies:

    def test_require_admin_is_callable(self):
        from app.middleware.auth_middleware import require_admin
        assert callable(require_admin)

    def test_require_operativo_is_callable(self):
        from app.middleware.auth_middleware import require_operativo
        assert callable(require_operativo)

    def test_oauth2_scheme_exists(self):
        from app.middleware.auth_middleware import oauth2_scheme
        assert oauth2_scheme is not None
        assert oauth2_scheme.scheme_name == "OAuth2PasswordBearer"
