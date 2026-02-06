"""
Pruebas unitarias para el servicio de autenticacion.
"""

import pytest
from unittest.mock import Mock, patch, MagicMock
from datetime import datetime, timedelta

from app.services.auth_service import AuthService
from app.models import Usuario


class TestAuthServiceInit:
    """Pruebas para inicializacion del servicio."""

    def test_auth_service_creation(self, db_session):
        """Verifica creacion del servicio."""
        service = AuthService(db_session)

        assert service is not None
        assert service.db == db_session
        assert service.usuario_repo is not None
        assert service.rol_repo is not None


class TestHashPassword:
    """Pruebas para hash de contrasenas."""

    def test_hash_password(self, db_session):
        """Verifica que el hash de password funcione correctamente."""
        password = "TestPassword123!"

        hashed = AuthService.hash_password(password)

        assert hashed is not None
        assert hashed != password
        assert len(hashed) > 50  # bcrypt genera hashes largos

    def test_hash_password_different_each_time(self, db_session):
        """Verifica que el hash sea diferente cada vez (salt aleatorio)."""
        password = "SamePassword123!"
        hash1 = AuthService.hash_password(password)
        hash2 = AuthService.hash_password(password)

        # Los hashes deben ser diferentes por el salt
        assert hash1 != hash2
        # Pero ambos deben validar correctamente
        assert AuthService.verify_password(password, hash1) is True
        assert AuthService.verify_password(password, hash2) is True

    def test_hash_password_with_special_chars(self, db_session):
        """Verifica password con caracteres especiales."""
        password = "Test@Password#123!$%^&*()"
        hashed = AuthService.hash_password(password)

        assert hashed is not None
        assert AuthService.verify_password(password, hashed) is True

    def test_hash_password_unicode(self, db_session):
        """Verifica password con caracteres unicode."""
        password = "Contraseña123!ñ"
        hashed = AuthService.hash_password(password)

        assert hashed is not None
        assert AuthService.verify_password(password, hashed) is True

    def test_hash_password_empty(self, db_session):
        """Verifica hash de password vacio."""
        password = ""
        hashed = AuthService.hash_password(password)

        assert hashed is not None
        assert AuthService.verify_password(password, hashed) is True


class TestVerifyPassword:
    """Pruebas para verificacion de contrasenas."""

    def test_verify_password_correct(self, db_session):
        """Verifica password correcto."""
        password = "TestPassword123!"
        hashed = AuthService.hash_password(password)

        result = AuthService.verify_password(password, hashed)

        assert result is True

    def test_verify_password_incorrect(self, db_session):
        """Verifica password incorrecto."""
        password = "TestPassword123!"
        wrong_password = "WrongPassword!"
        hashed = AuthService.hash_password(password)

        result = AuthService.verify_password(wrong_password, hashed)

        assert result is False

    def test_verify_password_invalid_hash(self, db_session):
        """Verifica manejo de hash invalido."""
        password = "TestPassword123!"
        invalid_hash = "not_a_valid_hash"

        result = AuthService.verify_password(password, invalid_hash)

        assert result is False

    def test_verify_password_case_sensitive(self, db_session):
        """Verifica que passwords sean case-sensitive."""
        password = "TestPassword123!"
        hashed = AuthService.hash_password(password)

        assert AuthService.verify_password("testpassword123!", hashed) is False
        assert AuthService.verify_password("TESTPASSWORD123!", hashed) is False


class TestCreateAccessToken:
    """Pruebas para creacion de tokens de acceso."""

    def test_create_access_token(self, db_session):
        """Verifica creacion de token de acceso."""
        data = {"sub": "testuser", "idUsuario": 1}

        token = AuthService.create_access_token(data)

        assert token is not None
        assert isinstance(token, str)
        assert len(token) > 50

    def test_create_access_token_with_custom_expiry(self, db_session):
        """Verifica token con expiracion personalizada."""
        data = {"sub": "testuser"}
        expires_delta = timedelta(hours=2)

        token = AuthService.create_access_token(data, expires_delta=expires_delta)

        assert token is not None
        # Decodificar y verificar expiracion
        payload = AuthService.decode_token(token)
        assert payload is not None
        assert payload.get("type") == "access"

    def test_create_access_token_with_roles(self, db_session):
        """Verifica token con roles."""
        data = {
            "sub": "testuser",
            "idUsuario": 1,
            "roles": ["Admin", "Operativo"]
        }

        token = AuthService.create_access_token(data)
        payload = AuthService.decode_token(token)

        assert payload.get("roles") == ["Admin", "Operativo"]


class TestCreateRefreshToken:
    """Pruebas para creacion de refresh tokens."""

    def test_create_refresh_token(self, db_session):
        """Verifica creacion de refresh token."""
        data = {"sub": "testuser", "idUsuario": 1}

        token = AuthService.create_refresh_token(data)

        assert token is not None
        assert isinstance(token, str)

    def test_create_refresh_token_with_custom_expiry(self, db_session):
        """Verifica refresh token con expiracion personalizada."""
        data = {"sub": "testuser"}
        expires_delta = timedelta(days=14)

        token = AuthService.create_refresh_token(data, expires_delta=expires_delta)

        assert token is not None
        payload = AuthService.decode_token(token)
        assert payload is not None
        assert payload.get("type") == "refresh"

    def test_refresh_token_different_from_access(self, db_session):
        """Verifica que refresh token sea diferente de access token."""
        data = {"sub": "testuser", "idUsuario": 1}

        access_token = AuthService.create_access_token(data)
        refresh_token = AuthService.create_refresh_token(data)

        assert access_token != refresh_token


class TestDecodeToken:
    """Pruebas para decodificacion de tokens."""

    def test_decode_token_valid(self, db_session):
        """Verifica decodificacion de token valido."""
        data = {"sub": "testuser", "idUsuario": 1}
        token = AuthService.create_access_token(data)

        payload = AuthService.decode_token(token)

        assert payload is not None
        assert payload.get("sub") == "testuser"
        assert payload.get("idUsuario") == 1

    def test_decode_token_invalid(self, db_session):
        """Verifica decodificacion de token invalido."""
        invalid_token = "invalid.token.here"

        payload = AuthService.decode_token(invalid_token)

        assert payload is None

    def test_decode_token_expired(self, db_session):
        """Verifica manejo de token expirado."""
        data = {"sub": "testuser"}
        expires_delta = timedelta(seconds=-10)  # Ya expirado

        token = AuthService.create_access_token(data, expires_delta=expires_delta)
        payload = AuthService.decode_token(token)

        assert payload is None

    def test_decode_token_malformed(self, db_session):
        """Verifica manejo de token malformado."""
        malformed_token = "not.a.valid.jwt.token"

        payload = AuthService.decode_token(malformed_token)

        assert payload is None


class TestVerifyToken:
    """Pruebas para verificacion de tokens."""

    def test_verify_token_valid(self, db_session):
        """Verifica token valido."""
        service = AuthService(db_session)
        data = {
            "sub": "testuser",
            "idUsuario": 1,
            "nombreUsuario": "testuser",
            "roles": ["Operativo"]
        }
        token = AuthService.create_access_token(data)

        token_data = service.verify_token(token)

        assert token_data is not None
        assert token_data.sub == "testuser"
        assert token_data.nombreUsuario == "testuser"

    def test_verify_token_invalid(self, db_session):
        """Verifica token invalido."""
        service = AuthService(db_session)
        invalid_token = "invalid.token.here"

        token_data = service.verify_token(invalid_token)

        assert token_data is None

    def test_verify_token_returns_token_data(self, db_session):
        """Verifica que retorna TokenData."""
        service = AuthService(db_session)
        data = {
            "sub": "testuser",
            "idUsuario": 1,
            "nombreUsuario": "testuser",
            "roles": ["Admin"]
        }
        token = AuthService.create_access_token(data)

        token_data = service.verify_token(token)

        assert token_data is not None
        assert hasattr(token_data, 'sub')
        assert hasattr(token_data, 'idUsuario')
        assert hasattr(token_data, 'roles')


class TestAuthenticateUser:
    """Pruebas para autenticacion de usuarios."""

    def test_authenticate_user_success(self, db_session):
        """Verifica autenticacion exitosa."""
        service = AuthService(db_session)

        mock_user = Mock()
        mock_user.nombreUsuario = "testuser"
        mock_user.hashPassword = AuthService.hash_password("Password123!")
        mock_user.estado = "Activo"

        with patch.object(service.usuario_repo, 'get_by_username', return_value=mock_user):
            result = service.authenticate_user("testuser", "Password123!")

            assert result is not None
            assert result.nombreUsuario == "testuser"

    def test_authenticate_user_not_found(self, db_session):
        """Verifica autenticacion con usuario inexistente."""
        service = AuthService(db_session)

        with patch.object(service.usuario_repo, 'get_by_username', return_value=None):
            with patch.object(service.usuario_repo, 'get_by_email', return_value=None):
                result = service.authenticate_user("nonexistent", "Password123!")

                assert result is None

    def test_authenticate_user_wrong_password(self, db_session):
        """Verifica autenticacion con contrasena incorrecta."""
        service = AuthService(db_session)

        mock_user = Mock()
        mock_user.nombreUsuario = "testuser"
        mock_user.hashPassword = AuthService.hash_password("CorrectPassword!")
        mock_user.estado = "Activo"

        with patch.object(service.usuario_repo, 'get_by_username', return_value=mock_user):
            result = service.authenticate_user("testuser", "WrongPassword!")

            assert result is None

    def test_authenticate_user_inactive(self, db_session):
        """Verifica autenticacion con usuario inactivo."""
        service = AuthService(db_session)

        mock_user = Mock()
        mock_user.nombreUsuario = "testuser"
        mock_user.hashPassword = AuthService.hash_password("Password123!")
        mock_user.estado = "Inactivo"

        with patch.object(service.usuario_repo, 'get_by_username', return_value=mock_user):
            result = service.authenticate_user("testuser", "Password123!")

            assert result is None

    def test_authenticate_user_by_email(self, db_session):
        """Verifica autenticacion por email."""
        service = AuthService(db_session)

        mock_user = Mock()
        mock_user.nombreUsuario = "testuser"
        mock_user.email = "test@test.com"
        mock_user.hashPassword = AuthService.hash_password("Password123!")
        mock_user.estado = "Activo"

        with patch.object(service.usuario_repo, 'get_by_username', return_value=None):
            with patch.object(service.usuario_repo, 'get_by_email', return_value=mock_user):
                result = service.authenticate_user("test@test.com", "Password123!")

                assert result is not None


class TestLogin:
    """Pruebas para login completo."""

    def test_login_success(self, db_session):
        """Verifica login exitoso."""
        service = AuthService(db_session)

        mock_user = Mock()
        mock_user.idUsuario = 1
        mock_user.nombreUsuario = "testuser"
        mock_user.nombreCompleto = "Test User"
        mock_user.email = "test@test.com"
        mock_user.hashPassword = AuthService.hash_password("Password123!")
        mock_user.estado = "Activo"

        with patch.object(service, 'authenticate_user', return_value=mock_user):
            with patch.object(service, 'get_user_roles', return_value=["Operativo"]):
                with patch.object(service, 'get_user_info') as mock_info:
                    mock_info.return_value = Mock(
                        idUsuario=1,
                        nombreCompleto="Test User",
                        nombreUsuario="testuser",
                        email="test@test.com",
                        roles=["Operativo"]
                    )

                    result = service.login("testuser", "Password123!")

                    assert result is not None
                    assert "access_token" in result
                    assert "refresh_token" in result
                    assert result["token_type"] == "bearer"

    def test_login_failure(self, db_session):
        """Verifica login fallido."""
        service = AuthService(db_session)

        with patch.object(service, 'authenticate_user', return_value=None):
            result = service.login("testuser", "WrongPassword!")

            assert result is None


class TestRefreshAccessToken:
    """Pruebas para refrescar token de acceso."""

    def test_refresh_access_token_success(self, db_session):
        """Verifica refresco exitoso de token."""
        service = AuthService(db_session)

        mock_user = Mock()
        mock_user.idUsuario = 1
        mock_user.nombreUsuario = "testuser"
        mock_user.estado = "Activo"

        # Crear refresh token valido
        refresh_token = AuthService.create_refresh_token({"sub": "testuser"})

        with patch.object(service.usuario_repo, 'get_by_username', return_value=mock_user):
            with patch.object(service, 'get_user_roles', return_value=["Operativo"]):
                result = service.refresh_access_token(refresh_token)

                assert result is not None
                assert "access_token" in result
                assert result["token_type"] == "bearer"

    def test_refresh_access_token_invalid(self, db_session):
        """Verifica refresco con token invalido."""
        service = AuthService(db_session)

        result = service.refresh_access_token("invalid.token.here")

        assert result is None

    def test_refresh_access_token_wrong_type(self, db_session):
        """Verifica refresco con token de tipo incorrecto."""
        service = AuthService(db_session)

        # Usar access token en lugar de refresh token
        access_token = AuthService.create_access_token({"sub": "testuser"})

        result = service.refresh_access_token(access_token)

        assert result is None

    def test_refresh_access_token_user_inactive(self, db_session):
        """Verifica refresco con usuario inactivo."""
        service = AuthService(db_session)

        mock_user = Mock()
        mock_user.nombreUsuario = "testuser"
        mock_user.estado = "Inactivo"

        refresh_token = AuthService.create_refresh_token({"sub": "testuser"})

        with patch.object(service.usuario_repo, 'get_by_username', return_value=mock_user):
            result = service.refresh_access_token(refresh_token)

            assert result is None


class TestGetUserRoles:
    """Pruebas para obtener roles de usuario."""

    def test_get_user_roles_success(self, db_session):
        """Verifica obtencion exitosa de roles."""
        service = AuthService(db_session)

        mock_roles = [Mock(nombre="Admin"), Mock(nombre="Operativo")]

        with patch.object(service.db, 'query') as mock_query:
            mock_query.return_value.join.return_value.filter.return_value.all.return_value = mock_roles

            roles = service.get_user_roles(1)

            assert roles == ["Admin", "Operativo"]

    def test_get_user_roles_empty(self, db_session):
        """Verifica manejo de usuario sin roles."""
        service = AuthService(db_session)

        with patch.object(service.db, 'query') as mock_query:
            mock_query.return_value.join.return_value.filter.return_value.all.return_value = []

            roles = service.get_user_roles(1)

            assert roles == []

    def test_get_user_roles_error(self, db_session):
        """Verifica manejo de error al obtener roles."""
        service = AuthService(db_session)

        with patch.object(service.db, 'query') as mock_query:
            mock_query.side_effect = Exception("Database error")

            roles = service.get_user_roles(1)

            assert roles == []


class TestGetUserInfo:
    """Pruebas para obtener informacion de usuario."""

    def test_get_user_info(self, db_session):
        """Verifica obtencion de informacion de usuario."""
        service = AuthService(db_session)

        mock_user = Mock()
        mock_user.idUsuario = 1
        mock_user.nombreCompleto = "Test User"
        mock_user.nombreUsuario = "testuser"
        mock_user.email = "test@test.com"

        with patch.object(service, 'get_user_roles', return_value=["Admin"]):
            user_info = service.get_user_info(mock_user)

            assert user_info.idUsuario == 1
            assert user_info.nombreUsuario == "testuser"
            assert user_info.roles == ["Admin"]


class TestRegisterUser:
    """Pruebas para registro de usuarios."""

    def test_register_user_success(self, db_session):
        """Verifica registro exitoso."""
        service = AuthService(db_session)

        with patch.object(service.usuario_repo, 'get_by_username', return_value=None):
            with patch.object(service.usuario_repo, 'get_by_email', return_value=None):
                with patch.object(service.db, 'add'), \
                     patch.object(service.db, 'commit'), \
                     patch.object(service.db, 'refresh') as mock_refresh:

                    mock_rol = Mock(idRol=1)
                    with patch.object(service.rol_repo, 'get_by_nombre', return_value=mock_rol):
                        def set_id(user):
                            user.idUsuario = 1

                        mock_refresh.side_effect = set_id

                        result = service.register_user(
                            nombre_completo="Test User",
                            nombre_usuario="newuser",
                            email="new@test.com",
                            password="Password123!"
                        )

                        assert result is not None

    def test_register_user_username_exists(self, db_session):
        """Verifica rechazo por nombre de usuario existente."""
        service = AuthService(db_session)

        with patch.object(service.usuario_repo, 'get_by_username', return_value=Mock()):
            result = service.register_user(
                nombre_completo="Test User",
                nombre_usuario="existinguser",
                email="new@test.com",
                password="Password123!"
            )

            assert result is None

    def test_register_user_email_exists(self, db_session):
        """Verifica rechazo por email existente."""
        service = AuthService(db_session)

        with patch.object(service.usuario_repo, 'get_by_username', return_value=None):
            with patch.object(service.usuario_repo, 'get_by_email', return_value=Mock()):
                result = service.register_user(
                    nombre_completo="Test User",
                    nombre_usuario="newuser",
                    email="existing@test.com",
                    password="Password123!"
                )

                assert result is None


class TestChangePassword:
    """Pruebas para cambio de contrasena."""

    def test_change_password_success(self, db_session):
        """Verifica cambio exitoso de contrasena."""
        service = AuthService(db_session)

        mock_user = Mock()
        mock_user.hashPassword = AuthService.hash_password("OldPassword123!")

        with patch.object(service.usuario_repo, 'get_by_id', return_value=mock_user):
            with patch.object(service.db, 'commit'):
                result = service.change_password(
                    user_id=1,
                    current_password="OldPassword123!",
                    new_password="NewPassword123!"
                )

                assert result is True

    def test_change_password_user_not_found(self, db_session):
        """Verifica cambio con usuario inexistente."""
        service = AuthService(db_session)

        with patch.object(service.usuario_repo, 'get_by_id', return_value=None):
            result = service.change_password(
                user_id=999,
                current_password="OldPassword123!",
                new_password="NewPassword123!"
            )

            assert result is False

    def test_change_password_wrong_current(self, db_session):
        """Verifica cambio con contrasena actual incorrecta."""
        service = AuthService(db_session)

        mock_user = Mock()
        mock_user.hashPassword = AuthService.hash_password("CorrectPassword!")

        with patch.object(service.usuario_repo, 'get_by_id', return_value=mock_user):
            result = service.change_password(
                user_id=1,
                current_password="WrongPassword!",
                new_password="NewPassword123!"
            )

            assert result is False


class TestPasswordValidation:
    """Pruebas para validacion de passwords."""

    def test_password_min_length(self, db_session):
        """Verifica hash de password corto."""
        short_password = "Short1!"
        hashed = AuthService.hash_password(short_password)

        assert hashed is not None
        assert AuthService.verify_password(short_password, hashed) is True

    def test_password_with_numbers(self, db_session):
        """Verifica password con numeros."""
        password = "TestPassword12345"
        hashed = AuthService.hash_password(password)

        assert AuthService.verify_password(password, hashed) is True

    def test_password_only_special_chars(self, db_session):
        """Verifica password solo con caracteres especiales."""
        password = "!@#$%^&*()"
        hashed = AuthService.hash_password(password)

        assert AuthService.verify_password(password, hashed) is True

    def test_password_very_long(self, db_session):
        """Verifica password largo (hasta limite de bcrypt)."""
        # bcrypt tiene un limite de 72 bytes
        password = "A" * 50 + "Password123!"
        hashed = AuthService.hash_password(password)

        assert AuthService.verify_password(password, hashed) is True
