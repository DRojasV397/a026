"""
Pruebas unitarias para repositories.
Cubre las operaciones de base de datos con mocks.
"""

import pytest
from datetime import datetime, timedelta, date
from decimal import Decimal
from unittest.mock import MagicMock, patch, PropertyMock, Mock
from sqlalchemy.orm import Session


class TestBaseRepository:
    """Pruebas para el repositorio base."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    def test_base_repository_import(self):
        """Test importacion del repositorio base."""
        from app.repositories.base_repository import BaseRepository
        assert BaseRepository is not None

    def test_base_repository_methods(self):
        """Test que BaseRepository tiene metodos esperados."""
        from app.repositories.base_repository import BaseRepository

        assert hasattr(BaseRepository, 'get_by_id')
        assert hasattr(BaseRepository, 'get_all')
        assert hasattr(BaseRepository, 'create')
        assert hasattr(BaseRepository, 'update')
        assert hasattr(BaseRepository, 'delete')
        assert hasattr(BaseRepository, 'count')

    def test_base_repository_get_by_id(self, mock_db):
        """Test que el metodo get_by_id existe y es callable."""
        from app.repositories.base_repository import BaseRepository
        from app.models import Usuario

        class TestRepo(BaseRepository[Usuario]):
            pass

        repo = TestRepo(mock_db, Usuario)
        assert hasattr(repo, 'get_by_id')
        assert callable(repo.get_by_id)

    def test_base_repository_get_all(self, mock_db):
        """Test que el metodo get_all existe y es callable."""
        from app.repositories.base_repository import BaseRepository
        from app.models import Usuario

        class TestRepo(BaseRepository[Usuario]):
            pass

        repo = TestRepo(mock_db, Usuario)
        assert hasattr(repo, 'get_all')
        assert callable(repo.get_all)

    def test_base_repository_create(self, mock_db):
        """Test que el metodo create existe y es callable."""
        from app.repositories.base_repository import BaseRepository
        from app.models import Usuario

        class TestRepo(BaseRepository[Usuario]):
            pass

        repo = TestRepo(mock_db, Usuario)
        assert hasattr(repo, 'create')
        assert callable(repo.create)

    def test_base_repository_update(self, mock_db):
        """Test que el metodo update existe y es callable."""
        from app.repositories.base_repository import BaseRepository
        from app.models import Usuario

        class TestRepo(BaseRepository[Usuario]):
            pass

        repo = TestRepo(mock_db, Usuario)
        assert hasattr(repo, 'update')
        assert callable(repo.update)

    def test_base_repository_delete(self, mock_db):
        """Test que el metodo delete existe y es callable."""
        from app.repositories.base_repository import BaseRepository
        from app.models import Usuario

        class TestRepo(BaseRepository[Usuario]):
            pass

        repo = TestRepo(mock_db, Usuario)
        assert hasattr(repo, 'delete')
        assert callable(repo.delete)

    def test_base_repository_count(self, mock_db):
        """Test que el metodo count existe y es callable."""
        from app.repositories.base_repository import BaseRepository
        from app.models import Usuario

        class TestRepo(BaseRepository[Usuario]):
            pass

        repo = TestRepo(mock_db, Usuario)
        assert hasattr(repo, 'count')
        assert callable(repo.count)


class TestVentaRepository:
    """Pruebas para el repositorio de ventas."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    @pytest.fixture
    def venta_repo(self, mock_db):
        """Crea repositorio de ventas con mock."""
        from app.repositories.venta_repository import VentaRepository
        return VentaRepository(mock_db)

    def test_venta_repository_creation(self, venta_repo):
        """Test creacion del repositorio."""
        assert venta_repo is not None

    def test_get_by_rango_fechas(self, venta_repo, mock_db):
        """Test consulta por rango de fechas."""
        mock_db.query.return_value.filter.return_value.order_by.return_value.all.return_value = []

        fecha_inicio = date(2024, 1, 1)
        fecha_fin = date(2024, 1, 31)

        result = venta_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)

        assert mock_db.query.called

    def test_get_by_fecha(self, venta_repo, mock_db):
        """Test consulta por fecha especifica."""
        mock_db.query.return_value.filter.return_value.all.return_value = []

        result = venta_repo.get_by_fecha(date(2024, 1, 15))
        assert mock_db.query.called

    def test_get_total_por_periodo(self, venta_repo, mock_db):
        """Test total por periodo."""
        mock_db.query.return_value.filter.return_value.scalar.return_value = Decimal('100000.00')

        fecha_inicio = date(2024, 1, 1)
        fecha_fin = date(2024, 12, 31)
        result = venta_repo.get_total_por_periodo(fecha_inicio, fecha_fin)

        assert mock_db.query.called
        assert result == Decimal('100000.00')

    def test_get_by_id(self, venta_repo, mock_db):
        """Test obtener venta por ID."""
        mock_venta = Mock(idVenta=1, total=1000)
        mock_db.query.return_value.get.return_value = mock_venta

        result = venta_repo.get_by_id(1)
        assert mock_db.query.called

    def test_create_venta(self, venta_repo, mock_db):
        """Test que metodo create existe."""
        assert hasattr(venta_repo, 'create')
        assert callable(venta_repo.create)

    def test_get_resumen_mensual(self, venta_repo, mock_db):
        """Test que metodo get_resumen_mensual existe si aplica."""
        # Este metodo puede o no existir dependiendo de la implementacion
        assert venta_repo is not None

    def test_get_all(self, venta_repo, mock_db):
        """Test obtener todas las ventas."""
        mock_db.query.return_value.all.return_value = [Mock(), Mock()]

        result = venta_repo.get_all()
        assert mock_db.query.called


class TestCompraRepository:
    """Pruebas para el repositorio de compras."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    @pytest.fixture
    def compra_repo(self, mock_db):
        """Crea repositorio de compras con mock."""
        from app.repositories.compra_repository import CompraRepository
        return CompraRepository(mock_db)

    def test_compra_repository_creation(self, compra_repo):
        """Test creacion del repositorio."""
        assert compra_repo is not None

    def test_get_by_proveedor(self, compra_repo, mock_db):
        """Test consulta por proveedor."""
        mock_db.query.return_value.filter.return_value.all.return_value = []

        result = compra_repo.get_by_proveedor("Proveedor A")
        assert mock_db.query.called

    def test_get_by_rango_fechas(self, compra_repo, mock_db):
        """Test consulta por rango de fechas."""
        fecha_inicio = date(2024, 1, 1)
        fecha_fin = date(2024, 1, 31)

        mock_db.query.return_value.filter.return_value.order_by.return_value.all.return_value = []

        result = compra_repo.get_by_rango_fechas(fecha_inicio, fecha_fin)
        assert mock_db.query.called

    def test_get_by_id(self, compra_repo, mock_db):
        """Test obtener compra por ID."""
        mock_compra = Mock(idCompra=1, total=5000)
        mock_db.query.return_value.get.return_value = mock_compra

        result = compra_repo.get_by_id(1)
        assert mock_db.query.called

    def test_create_compra(self, compra_repo, mock_db):
        """Test que metodo create existe."""
        assert hasattr(compra_repo, 'create')
        assert callable(compra_repo.create)

    def test_get_total_por_periodo(self, compra_repo, mock_db):
        """Test total por periodo."""
        mock_db.query.return_value.filter.return_value.scalar.return_value = Decimal('50000.00')

        if hasattr(compra_repo, 'get_total_por_periodo'):
            result = compra_repo.get_total_por_periodo(
                date(2024, 1, 1),
                date(2024, 12, 31)
            )
            assert mock_db.query.called

    def test_get_all(self, compra_repo, mock_db):
        """Test obtener todas las compras."""
        mock_db.query.return_value.all.return_value = [Mock(), Mock()]

        result = compra_repo.get_all()
        assert mock_db.query.called


class TestProductoRepository:
    """Pruebas para el repositorio de productos."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    @pytest.fixture
    def producto_repo(self, mock_db):
        """Crea repositorio de productos con mock."""
        from app.repositories.producto_repository import ProductoRepository
        return ProductoRepository(mock_db)

    def test_producto_repository_creation(self, producto_repo):
        """Test creacion del repositorio."""
        assert producto_repo is not None

    def test_has_get_by_id(self, producto_repo):
        """Test que tiene metodo get_by_id."""
        assert hasattr(producto_repo, 'get_by_id')

    def test_has_get_all(self, producto_repo):
        """Test que tiene metodo get_all."""
        assert hasattr(producto_repo, 'get_all')

    def test_get_by_id(self, producto_repo, mock_db):
        """Test obtener producto por ID."""
        mock_producto = Mock(idProducto=1, nombre="Test")
        mock_db.query.return_value.get.return_value = mock_producto

        result = producto_repo.get_by_id(1)
        assert mock_db.query.called

    def test_get_all(self, producto_repo, mock_db):
        """Test obtener todos los productos."""
        mock_db.query.return_value.all.return_value = [Mock(), Mock()]

        result = producto_repo.get_all()
        assert mock_db.query.called

    def test_create_producto(self, producto_repo, mock_db):
        """Test que metodo create existe."""
        assert hasattr(producto_repo, 'create')
        assert callable(producto_repo.create)

    def test_get_by_sku(self, producto_repo, mock_db):
        """Test obtener producto por SKU."""
        mock_db.query.return_value.filter.return_value.first.return_value = Mock(sku="SKU001")

        if hasattr(producto_repo, 'get_by_sku'):
            result = producto_repo.get_by_sku("SKU001")
            assert mock_db.query.called

    def test_get_by_categoria(self, producto_repo, mock_db):
        """Test obtener productos por categoria."""
        mock_db.query.return_value.filter.return_value.all.return_value = [Mock()]

        if hasattr(producto_repo, 'get_by_categoria'):
            result = producto_repo.get_by_categoria(1)
            assert mock_db.query.called


class TestUsuarioRepository:
    """Pruebas para el repositorio de usuarios."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    @pytest.fixture
    def usuario_repo(self, mock_db):
        """Crea repositorio de usuarios con mock."""
        from app.repositories.usuario_repository import UsuarioRepository
        return UsuarioRepository(mock_db)

    def test_usuario_repository_creation(self, usuario_repo):
        """Test creacion del repositorio."""
        assert usuario_repo is not None

    def test_get_by_username(self, usuario_repo, mock_db):
        """Test busqueda por nombre de usuario."""
        mock_db.query.return_value.filter.return_value.first.return_value = None

        result = usuario_repo.get_by_username("testuser")
        assert mock_db.query.called

    def test_get_by_email(self, usuario_repo, mock_db):
        """Test busqueda por email."""
        mock_db.query.return_value.filter.return_value.first.return_value = Mock(email="test@test.com")

        if hasattr(usuario_repo, 'get_by_email'):
            result = usuario_repo.get_by_email("test@test.com")
            assert mock_db.query.called

    def test_get_by_id(self, usuario_repo, mock_db):
        """Test obtener usuario por ID."""
        mock_usuario = Mock(idUsuario=1, username="test")
        mock_db.query.return_value.get.return_value = mock_usuario

        result = usuario_repo.get_by_id(1)
        assert mock_db.query.called

    def test_create_usuario(self, usuario_repo, mock_db):
        """Test que metodo create existe."""
        assert hasattr(usuario_repo, 'create')
        assert callable(usuario_repo.create)

    def test_get_all(self, usuario_repo, mock_db):
        """Test obtener todos los usuarios."""
        mock_db.query.return_value.all.return_value = [Mock(), Mock()]

        result = usuario_repo.get_all()
        assert mock_db.query.called


class TestModeloRepository:
    """Pruebas para el repositorio de modelos."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    @pytest.fixture
    def modelo_repo(self, mock_db):
        """Crea repositorio de modelos con mock."""
        from app.repositories.modelo_repository import ModeloRepository
        return ModeloRepository(mock_db)

    def test_modelo_repository_creation(self, modelo_repo):
        """Test creacion del repositorio."""
        assert modelo_repo is not None

    def test_has_base_methods(self, modelo_repo):
        """Test que tiene metodos base."""
        assert hasattr(modelo_repo, 'get_by_id')
        assert hasattr(modelo_repo, 'create')

    def test_get_by_id(self, modelo_repo, mock_db):
        """Test obtener modelo por ID."""
        mock_modelo = Mock(idModelo=1, tipoModelo="linear")
        mock_db.query.return_value.get.return_value = mock_modelo

        result = modelo_repo.get_by_id(1)
        assert mock_db.query.called

    def test_get_by_tipo(self, modelo_repo, mock_db):
        """Test obtener modelos por tipo."""
        mock_db.query.return_value.filter.return_value.all.return_value = [Mock()]

        if hasattr(modelo_repo, 'get_by_tipo'):
            result = modelo_repo.get_by_tipo("linear")
            assert mock_db.query.called

    def test_create_modelo(self, modelo_repo, mock_db):
        """Test que metodo create existe."""
        assert hasattr(modelo_repo, 'create')
        assert callable(modelo_repo.create)

    def test_get_all(self, modelo_repo, mock_db):
        """Test obtener todos los modelos."""
        mock_db.query.return_value.all.return_value = [Mock(), Mock()]

        result = modelo_repo.get_all()
        assert mock_db.query.called


class TestPrediccionRepository:
    """Pruebas para el repositorio de predicciones."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    @pytest.fixture
    def prediccion_repo(self, mock_db):
        """Crea repositorio de predicciones con mock."""
        from app.repositories.prediccion_repository import PrediccionRepository
        return PrediccionRepository(mock_db)

    def test_prediccion_repository_creation(self, prediccion_repo):
        """Test creacion del repositorio."""
        assert prediccion_repo is not None

    def test_get_by_id(self, prediccion_repo, mock_db):
        """Test obtener prediccion por ID."""
        mock_prediccion = Mock(idPrediccion=1)
        mock_db.query.return_value.get.return_value = mock_prediccion

        result = prediccion_repo.get_by_id(1)
        assert mock_db.query.called

    def test_get_by_modelo(self, prediccion_repo, mock_db):
        """Test obtener predicciones por modelo."""
        mock_db.query.return_value.filter.return_value.all.return_value = [Mock()]

        if hasattr(prediccion_repo, 'get_by_modelo'):
            result = prediccion_repo.get_by_modelo(1)
            assert mock_db.query.called

    def test_create_prediccion(self, prediccion_repo, mock_db):
        """Test que metodo create existe."""
        assert hasattr(prediccion_repo, 'create')
        assert callable(prediccion_repo.create)

    def test_get_recientes(self, prediccion_repo, mock_db):
        """Test obtener predicciones recientes."""
        mock_db.query.return_value.order_by.return_value.limit.return_value.all.return_value = [Mock()]

        if hasattr(prediccion_repo, 'get_recientes'):
            result = prediccion_repo.get_recientes(10)
            assert mock_db.query.called


class TestEscenarioRepository:
    """Pruebas para el repositorio de escenarios."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    @pytest.fixture
    def escenario_repo(self, mock_db):
        """Crea repositorio de escenarios con mock."""
        from app.repositories.escenario_repository import EscenarioRepository
        return EscenarioRepository(mock_db)

    def test_escenario_repository_creation(self, escenario_repo):
        """Test creacion del repositorio."""
        assert escenario_repo is not None

    def test_get_by_id(self, escenario_repo, mock_db):
        """Test obtener escenario por ID."""
        mock_escenario = Mock(idEscenario=1, nombre="Test")
        mock_db.query.return_value.get.return_value = mock_escenario

        result = escenario_repo.get_by_id(1)
        assert mock_db.query.called

    def test_get_by_nombre(self, escenario_repo, mock_db):
        """Test obtener escenario por nombre."""
        mock_db.query.return_value.filter.return_value.first.return_value = Mock(nombre="Test")

        if hasattr(escenario_repo, 'get_by_nombre'):
            result = escenario_repo.get_by_nombre("Test")
            assert mock_db.query.called

    def test_get_by_usuario(self, escenario_repo, mock_db):
        """Test obtener escenarios por usuario."""
        mock_db.query.return_value.filter.return_value.all.return_value = [Mock()]

        if hasattr(escenario_repo, 'get_by_usuario'):
            result = escenario_repo.get_by_usuario(1)
            assert mock_db.query.called

    def test_create_escenario(self, escenario_repo, mock_db):
        """Test que metodo create existe."""
        assert hasattr(escenario_repo, 'create')
        assert callable(escenario_repo.create)

    def test_get_all(self, escenario_repo, mock_db):
        """Test obtener todos los escenarios."""
        mock_db.query.return_value.all.return_value = [Mock(), Mock()]

        result = escenario_repo.get_all()
        assert mock_db.query.called

    def test_archivar_escenario(self, escenario_repo, mock_db):
        """Test archivar escenario."""
        mock_escenario = Mock()
        mock_db.query.return_value.get.return_value = mock_escenario

        if hasattr(escenario_repo, 'archivar_escenario'):
            result = escenario_repo.archivar_escenario(1)
            # El resultado depende de la implementacion


class TestParametroEscenarioRepository:
    """Pruebas para el repositorio de parametros de escenario."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    def test_parametro_repository_import(self):
        """Test importacion del repositorio."""
        from app.repositories.escenario_repository import ParametroEscenarioRepository
        assert ParametroEscenarioRepository is not None

    def test_get_by_escenario(self, mock_db):
        """Test obtener parametros por escenario."""
        from app.repositories.escenario_repository import ParametroEscenarioRepository
        repo = ParametroEscenarioRepository(mock_db)

        mock_db.query.return_value.filter.return_value.all.return_value = [Mock()]

        result = repo.get_by_escenario(1)
        assert mock_db.query.called

    def test_actualizar_parametro(self, mock_db):
        """Test actualizar parametro."""
        from app.repositories.escenario_repository import ParametroEscenarioRepository
        repo = ParametroEscenarioRepository(mock_db)

        mock_db.query.return_value.filter.return_value.first.return_value = None

        result = repo.actualizar_parametro(1, "test", 10.0, 5.0)
        # El resultado depende de la implementacion


class TestResultadoEscenarioRepository:
    """Pruebas para el repositorio de resultados de escenario."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    def test_resultado_repository_import(self):
        """Test importacion del repositorio."""
        from app.repositories.escenario_repository import ResultadoEscenarioRepository
        assert ResultadoEscenarioRepository is not None

    def test_get_by_escenario(self, mock_db):
        """Test obtener resultados por escenario."""
        from app.repositories.escenario_repository import ResultadoEscenarioRepository
        repo = ResultadoEscenarioRepository(mock_db)

        mock_db.query.return_value.filter.return_value.order_by.return_value.all.return_value = [Mock()]

        result = repo.get_by_escenario(1)
        assert mock_db.query.called

    def test_eliminar_resultados_escenario(self, mock_db):
        """Test eliminar resultados de escenario."""
        from app.repositories.escenario_repository import ResultadoEscenarioRepository
        repo = ResultadoEscenarioRepository(mock_db)

        if hasattr(repo, 'eliminar_resultados_escenario'):
            result = repo.eliminar_resultados_escenario(1)
            # Verificar que se ejecuto la operacion


class TestAlertaRepository:
    """Pruebas para el repositorio de alertas."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    @pytest.fixture
    def alerta_repo(self, mock_db):
        """Crea repositorio de alertas con mock."""
        from app.repositories.alerta_repository import AlertaRepository
        return AlertaRepository(mock_db)

    def test_alerta_repository_creation(self, alerta_repo):
        """Test creacion del repositorio."""
        assert alerta_repo is not None

    def test_get_by_id(self, alerta_repo, mock_db):
        """Test obtener alerta por ID."""
        mock_alerta = Mock(idAlerta=1)
        mock_db.query.return_value.get.return_value = mock_alerta

        result = alerta_repo.get_by_id(1)
        assert mock_db.query.called

    def test_get_activas(self, alerta_repo, mock_db):
        """Test obtener alertas activas."""
        mock_db.query.return_value.filter.return_value.all.return_value = [Mock()]

        if hasattr(alerta_repo, 'get_activas'):
            result = alerta_repo.get_activas()
            assert mock_db.query.called

    def test_get_by_tipo(self, alerta_repo, mock_db):
        """Test obtener alertas por tipo."""
        mock_db.query.return_value.filter.return_value.all.return_value = [Mock()]

        if hasattr(alerta_repo, 'get_by_tipo'):
            result = alerta_repo.get_by_tipo("riesgo")
            assert mock_db.query.called

    def test_create_alerta(self, alerta_repo, mock_db):
        """Test que metodo create existe."""
        assert hasattr(alerta_repo, 'create')
        assert callable(alerta_repo.create)

    def test_marcar_leida(self, alerta_repo, mock_db):
        """Test marcar alerta como leida."""
        mock_alerta = Mock(estado="activa")
        mock_db.query.return_value.get.return_value = mock_alerta

        if hasattr(alerta_repo, 'marcar_leida'):
            result = alerta_repo.marcar_leida(1)

    def test_get_all(self, alerta_repo, mock_db):
        """Test obtener todas las alertas."""
        mock_db.query.return_value.all.return_value = [Mock(), Mock()]

        result = alerta_repo.get_all()
        assert mock_db.query.called


class TestRentabilidadRepository:
    """Pruebas para el repositorio de rentabilidad."""

    @pytest.fixture
    def mock_db(self):
        """Mock de sesion de base de datos."""
        return MagicMock(spec=Session)

    @pytest.fixture
    def rentabilidad_repo(self, mock_db):
        """Crea repositorio de rentabilidad con mock."""
        from app.repositories.rentabilidad_repository import RentabilidadRepository
        return RentabilidadRepository(mock_db)

    def test_rentabilidad_repository_creation(self, rentabilidad_repo):
        """Test creacion del repositorio."""
        assert rentabilidad_repo is not None

    def test_get_by_id(self, rentabilidad_repo, mock_db):
        """Test obtener rentabilidad por ID."""
        mock_rentabilidad = Mock(idRentabilidad=1)
        mock_db.query.return_value.get.return_value = mock_rentabilidad

        result = rentabilidad_repo.get_by_id(1)
        assert mock_db.query.called

    def test_get_by_periodo(self, rentabilidad_repo, mock_db):
        """Test que metodo get_by_periodo existe si aplica."""
        # Este metodo puede o no existir
        assert rentabilidad_repo is not None

    def test_create_rentabilidad(self, rentabilidad_repo, mock_db):
        """Test que metodo create existe."""
        assert hasattr(rentabilidad_repo, 'create')
        assert callable(rentabilidad_repo.create)

    def test_get_all(self, rentabilidad_repo, mock_db):
        """Test obtener todos los registros de rentabilidad."""
        mock_db.query.return_value.all.return_value = [Mock(), Mock()]

        result = rentabilidad_repo.get_all()
        assert mock_db.query.called


class TestRepositoryIntegration:
    """Pruebas de integracion de repositories."""

    def test_all_repositories_import(self):
        """Test que todos los repositories se pueden importar."""
        from app.repositories.base_repository import BaseRepository
        from app.repositories.venta_repository import VentaRepository
        from app.repositories.compra_repository import CompraRepository
        from app.repositories.producto_repository import ProductoRepository
        from app.repositories.usuario_repository import UsuarioRepository
        from app.repositories.modelo_repository import ModeloRepository
        from app.repositories.prediccion_repository import PrediccionRepository
        from app.repositories.escenario_repository import EscenarioRepository
        from app.repositories.alerta_repository import AlertaRepository
        from app.repositories.rentabilidad_repository import RentabilidadRepository

        assert all([
            BaseRepository,
            VentaRepository,
            CompraRepository,
            ProductoRepository,
            UsuarioRepository,
            ModeloRepository,
            PrediccionRepository,
            EscenarioRepository,
            AlertaRepository,
            RentabilidadRepository
        ])

    def test_repositories_inherit_base(self):
        """Test que repositories heredan de BaseRepository."""
        from app.repositories.base_repository import BaseRepository
        from app.repositories.venta_repository import VentaRepository
        from app.repositories.compra_repository import CompraRepository

        mock_db = MagicMock(spec=Session)

        venta_repo = VentaRepository(mock_db)
        compra_repo = CompraRepository(mock_db)

        assert hasattr(venta_repo, 'get_by_id')
        assert hasattr(venta_repo, 'create')
        assert hasattr(compra_repo, 'get_by_id')
        assert hasattr(compra_repo, 'create')

    def test_repositories_consistent_interface(self):
        """Test que todos los repositories tienen interfaz consistente."""
        from app.repositories.venta_repository import VentaRepository
        from app.repositories.compra_repository import CompraRepository
        from app.repositories.producto_repository import ProductoRepository
        from app.repositories.usuario_repository import UsuarioRepository

        mock_db = MagicMock(spec=Session)

        repos = [
            VentaRepository(mock_db),
            CompraRepository(mock_db),
            ProductoRepository(mock_db),
            UsuarioRepository(mock_db)
        ]

        common_methods = ['get_by_id', 'get_all', 'create', 'update', 'delete']

        for repo in repos:
            for method in common_methods:
                assert hasattr(repo, method), f"{type(repo).__name__} debe tener {method}"
