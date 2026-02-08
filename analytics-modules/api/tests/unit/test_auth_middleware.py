"""
Tests para middleware de autenticacion.
Cubre las funciones de autenticacion y autorizacion.
"""

import pytest
from unittest.mock import Mock, patch, AsyncMock, MagicMock
from fastapi import HTTPException
import asyncio


class TestGetCurrentUser:
    """Tests para get_current_user."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.mark.asyncio
    async def test_no_token_returns_none(self, mock_db):
        """Test que sin token retorna None."""
        from app.middleware.auth_middleware import get_current_user

        result = await get_current_user(token=None, db=mock_db)

        assert result is None

    @pytest.mark.asyncio
    async def test_invalid_token_returns_none(self, mock_db):
        """Test que token invalido retorna None."""
        from app.middleware.auth_middleware import get_current_user

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth:
            mock_auth.return_value.verify_token.return_value = None

            result = await get_current_user(token="invalid_token", db=mock_db)

            assert result is None

    @pytest.mark.asyncio
    async def test_valid_token_returns_user(self, mock_db):
        """Test que token valido retorna usuario."""
        from app.middleware.auth_middleware import get_current_user

        mock_user = Mock()
        mock_token_data = Mock()
        mock_token_data.idUsuario = 1

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth, \
             patch('app.repositories.UsuarioRepository') as mock_repo:
            mock_auth.return_value.verify_token.return_value = mock_token_data
            mock_repo.return_value.get_by_id.return_value = mock_user

            result = await get_current_user(token="valid_token", db=mock_db)

            assert result == mock_user


class TestGetCurrentActiveUser:
    """Tests para get_current_active_user."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.mark.asyncio
    async def test_no_token_raises_exception(self, mock_db):
        """Test que sin token lanza excepcion."""
        from app.middleware.auth_middleware import get_current_active_user

        with pytest.raises(HTTPException) as exc_info:
            await get_current_active_user(token=None, db=mock_db)

        assert exc_info.value.status_code == 401

    @pytest.mark.asyncio
    async def test_invalid_token_raises_exception(self, mock_db):
        """Test que token invalido lanza excepcion."""
        from app.middleware.auth_middleware import get_current_active_user

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth:
            mock_auth.return_value.verify_token.return_value = None

            with pytest.raises(HTTPException) as exc_info:
                await get_current_active_user(token="invalid", db=mock_db)

            assert exc_info.value.status_code == 401

    @pytest.mark.asyncio
    async def test_user_not_found_raises_exception(self, mock_db):
        """Test que usuario no encontrado lanza excepcion."""
        from app.middleware.auth_middleware import get_current_active_user

        mock_token_data = Mock()
        mock_token_data.idUsuario = 1

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth, \
             patch('app.repositories.UsuarioRepository') as mock_repo:
            mock_auth.return_value.verify_token.return_value = mock_token_data
            mock_repo.return_value.get_by_id.return_value = None

            with pytest.raises(HTTPException) as exc_info:
                await get_current_active_user(token="valid", db=mock_db)

            assert exc_info.value.status_code == 401

    @pytest.mark.asyncio
    async def test_inactive_user_raises_exception(self, mock_db):
        """Test que usuario inactivo lanza excepcion."""
        from app.middleware.auth_middleware import get_current_active_user

        mock_user = Mock()
        mock_user.estado = "inactivo"
        mock_token_data = Mock()
        mock_token_data.idUsuario = 1

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth, \
             patch('app.repositories.UsuarioRepository') as mock_repo:
            mock_auth.return_value.verify_token.return_value = mock_token_data
            mock_repo.return_value.get_by_id.return_value = mock_user

            with pytest.raises(HTTPException) as exc_info:
                await get_current_active_user(token="valid", db=mock_db)

            assert exc_info.value.status_code == 403

    @pytest.mark.asyncio
    async def test_active_user_returns_user(self, mock_db):
        """Test que usuario activo retorna usuario."""
        from app.middleware.auth_middleware import get_current_active_user

        mock_user = Mock()
        mock_user.estado = "activo"
        mock_token_data = Mock()
        mock_token_data.idUsuario = 1

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth, \
             patch('app.repositories.UsuarioRepository') as mock_repo:
            mock_auth.return_value.verify_token.return_value = mock_token_data
            mock_repo.return_value.get_by_id.return_value = mock_user

            result = await get_current_active_user(token="valid", db=mock_db)

            assert result == mock_user

    @pytest.mark.asyncio
    async def test_user_with_none_estado_returns_user(self, mock_db):
        """Test que usuario con estado None se considera activo."""
        from app.middleware.auth_middleware import get_current_active_user

        mock_user = Mock()
        mock_user.estado = None  # None se trata como activo
        mock_token_data = Mock()
        mock_token_data.idUsuario = 1

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth, \
             patch('app.repositories.UsuarioRepository') as mock_repo:
            mock_auth.return_value.verify_token.return_value = mock_token_data
            mock_repo.return_value.get_by_id.return_value = mock_user

            result = await get_current_active_user(token="valid", db=mock_db)

            assert result == mock_user


class TestRequireRoles:
    """Tests para require_roles."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    def test_require_roles_returns_callable(self):
        """Test que require_roles retorna una funcion callable."""
        from app.middleware.auth_middleware import require_roles

        checker = require_roles(["Admin"])

        assert callable(checker)

    @pytest.mark.asyncio
    async def test_no_token_raises_exception(self, mock_db):
        """Test que sin token lanza excepcion."""
        from app.middleware.auth_middleware import require_roles

        checker = require_roles(["Admin"])

        with pytest.raises(HTTPException) as exc_info:
            await checker(token=None, db=mock_db)

        assert exc_info.value.status_code == 401

    @pytest.mark.asyncio
    async def test_invalid_token_raises_exception(self, mock_db):
        """Test que token invalido lanza excepcion."""
        from app.middleware.auth_middleware import require_roles

        checker = require_roles(["Admin"])

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth:
            mock_auth.return_value.verify_token.return_value = None

            with pytest.raises(HTTPException) as exc_info:
                await checker(token="invalid", db=mock_db)

            assert exc_info.value.status_code == 401

    @pytest.mark.asyncio
    async def test_missing_role_raises_exception(self, mock_db):
        """Test que sin rol requerido lanza excepcion."""
        from app.middleware.auth_middleware import require_roles

        checker = require_roles(["Admin"])

        mock_token_data = Mock()
        mock_token_data.idUsuario = 1
        mock_token_data.roles = ["User"]  # No es Admin

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth:
            mock_auth.return_value.verify_token.return_value = mock_token_data

            with pytest.raises(HTTPException) as exc_info:
                await checker(token="valid", db=mock_db)

            assert exc_info.value.status_code == 403

    @pytest.mark.asyncio
    async def test_user_not_found_raises_exception(self, mock_db):
        """Test que usuario no encontrado lanza excepcion."""
        from app.middleware.auth_middleware import require_roles

        checker = require_roles(["Admin"])

        mock_token_data = Mock()
        mock_token_data.idUsuario = 1
        mock_token_data.roles = ["Admin"]

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth, \
             patch('app.repositories.UsuarioRepository') as mock_repo:
            mock_auth.return_value.verify_token.return_value = mock_token_data
            mock_repo.return_value.get_by_id.return_value = None

            with pytest.raises(HTTPException) as exc_info:
                await checker(token="valid", db=mock_db)

            assert exc_info.value.status_code == 401

    @pytest.mark.asyncio
    async def test_valid_role_returns_user(self, mock_db):
        """Test que con rol correcto retorna usuario."""
        from app.middleware.auth_middleware import require_roles

        checker = require_roles(["Admin", "Administrador"])

        mock_user = Mock()
        mock_token_data = Mock()
        mock_token_data.idUsuario = 1
        mock_token_data.roles = ["Admin"]

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth, \
             patch('app.repositories.UsuarioRepository') as mock_repo:
            mock_auth.return_value.verify_token.return_value = mock_token_data
            mock_repo.return_value.get_by_id.return_value = mock_user

            result = await checker(token="valid", db=mock_db)

            assert result == mock_user

    @pytest.mark.asyncio
    async def test_none_roles_raises_exception(self, mock_db):
        """Test que sin roles en token lanza excepcion."""
        from app.middleware.auth_middleware import require_roles

        checker = require_roles(["Admin"])

        mock_token_data = Mock()
        mock_token_data.idUsuario = 1
        mock_token_data.roles = None

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth:
            mock_auth.return_value.verify_token.return_value = mock_token_data

            with pytest.raises(HTTPException) as exc_info:
                await checker(token="valid", db=mock_db)

            assert exc_info.value.status_code == 403

    @pytest.mark.asyncio
    async def test_multiple_allowed_roles(self, mock_db):
        """Test que cualquier rol permitido funciona."""
        from app.middleware.auth_middleware import require_roles

        checker = require_roles(["Admin", "Administrador", "Operativo"])

        mock_user = Mock()
        mock_token_data = Mock()
        mock_token_data.idUsuario = 1
        mock_token_data.roles = ["Operativo"]

        with patch('app.middleware.auth_middleware.AuthService') as mock_auth, \
             patch('app.repositories.UsuarioRepository') as mock_repo:
            mock_auth.return_value.verify_token.return_value = mock_token_data
            mock_repo.return_value.get_by_id.return_value = mock_user

            result = await checker(token="valid", db=mock_db)

            assert result == mock_user


class TestPreConfiguredDependencies:
    """Tests para dependencias preconfiguradas."""

    def test_require_admin_exists(self):
        """Test que require_admin existe y es callable."""
        from app.middleware.auth_middleware import require_admin

        assert callable(require_admin)

    def test_require_operativo_exists(self):
        """Test que require_operativo existe y es callable."""
        from app.middleware.auth_middleware import require_operativo

        assert callable(require_operativo)


class TestOAuth2Scheme:
    """Tests para el esquema OAuth2."""

    def test_oauth2_scheme_exists(self):
        """Test que el esquema OAuth2 existe."""
        from app.middleware.auth_middleware import oauth2_scheme

        assert oauth2_scheme is not None
        assert oauth2_scheme.scheme_name == "OAuth2PasswordBearer"
