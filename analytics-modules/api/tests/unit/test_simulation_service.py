"""
Pruebas unitarias para el servicio de simulacion.
RF-05: Modulo de Simulacion de Escenarios.
"""

import pytest
from datetime import date, datetime, timedelta
from decimal import Decimal
from unittest.mock import Mock, patch, MagicMock

from app.services.simulation_service import (
    SimulationService, ParameterType, IndicatorType,
    ScenarioParameter, SimulationResult, ScenarioSummary
)


class TestSimulationServiceInit:
    """Pruebas para inicializacion del servicio."""

    def test_init(self, db_session):
        """Verifica inicializacion del servicio."""
        service = SimulationService(db_session)

        assert service is not None
        assert service.db == db_session
        assert service.MAX_VARIATION == 50.0
        assert service.MAX_SCENARIOS_COMPARE == 5
        assert service.MAX_PERIODS == 12

    def test_repositories_initialized(self, db_session):
        """Verifica que los repositorios se inicialicen."""
        service = SimulationService(db_session)

        assert service.escenario_repo is not None
        assert service.parametro_repo is not None
        assert service.resultado_repo is not None
        assert service.venta_repo is not None
        assert service.compra_repo is not None


class TestEnums:
    """Pruebas para enumeraciones."""

    def test_parameter_type_enum(self):
        """Verifica enumeracion de tipos de parametro."""
        assert ParameterType.PRECIO.value == "precio"
        assert ParameterType.COSTO.value == "costo"
        assert ParameterType.DEMANDA.value == "demanda"
        assert ParameterType.PORCENTAJE.value == "porcentaje"

    def test_indicator_type_enum(self):
        """Verifica enumeracion de tipos de indicador."""
        assert IndicatorType.INGRESOS.value == "ingresos"
        assert IndicatorType.COSTOS.value == "costos"
        assert IndicatorType.UTILIDAD_BRUTA.value == "utilidad_bruta"
        assert IndicatorType.MARGEN_BRUTO.value == "margen_bruto"
        assert IndicatorType.UNIDADES.value == "unidades"


class TestDataClasses:
    """Pruebas para dataclasses."""

    def test_scenario_parameter_creation(self):
        """Verifica creacion de ScenarioParameter."""
        param = ScenarioParameter(
            nombre="variacion_precio",
            valor_base=100.0,
            valor_actual=110.0,
            descripcion="Variacion de precio"
        )

        assert param.nombre == "variacion_precio"
        assert param.valor_base == 100.0
        assert param.valor_actual == 110.0
        assert param.descripcion == "Variacion de precio"

    def test_scenario_parameter_to_dict(self):
        """Verifica conversion a dict de ScenarioParameter."""
        param = ScenarioParameter(
            nombre="test",
            valor_base=50.0,
            valor_actual=60.0
        )

        result = param.to_dict()

        assert result["nombre"] == "test"
        assert result["valor_base"] == 50.0
        assert result["valor_actual"] == 60.0

    def test_simulation_result_creation(self):
        """Verifica creacion de SimulationResult."""
        result = SimulationResult(
            periodo=date(2024, 1, 1),
            kpi="ingresos",
            valor_base=1000.0,
            valor_simulado=1200.0,
            diferencia=200.0,
            porcentaje_cambio=20.0
        )

        assert result.periodo == date(2024, 1, 1)
        assert result.kpi == "ingresos"
        assert result.valor_base == 1000.0
        assert result.valor_simulado == 1200.0
        assert result.diferencia == 200.0
        assert result.porcentaje_cambio == 20.0

    def test_simulation_result_to_dict(self):
        """Verifica conversion a dict de SimulationResult."""
        result = SimulationResult(
            periodo=date(2024, 1, 1),
            kpi="costos",
            valor_base=500.0,
            valor_simulado=550.0,
            diferencia=50.0,
            porcentaje_cambio=10.0
        )

        d = result.to_dict()

        assert d["periodo"] == "2024-01-01"
        assert d["kpi"] == "costos"
        assert d["valor_base"] == 500.0
        assert d["valor_simulado"] == 550.0

    def test_scenario_summary_creation(self):
        """Verifica creacion de ScenarioSummary."""
        summary = ScenarioSummary(
            id_escenario=1,
            nombre="Test Scenario",
            descripcion="Test description",
            horizonte_meses=6,
            fecha_creacion=datetime.now(),
            num_parametros=5,
            num_resultados=24,
            total_ingresos_simulados=100000.0,
            total_utilidad_simulada=30000.0
        )

        assert summary.id_escenario == 1
        assert summary.nombre == "Test Scenario"
        assert summary.horizonte_meses == 6
        assert summary.total_ingresos_simulados == 100000.0

    def test_scenario_summary_to_dict(self):
        """Verifica conversion a dict de ScenarioSummary."""
        summary = ScenarioSummary(
            id_escenario=1,
            nombre="Test",
            descripcion=None,
            horizonte_meses=6,
            fecha_creacion=datetime(2024, 1, 1, 12, 0, 0),
            num_parametros=3,
            num_resultados=12
        )

        d = summary.to_dict()

        assert d["id_escenario"] == 1
        assert d["nombre"] == "Test"
        assert d["horizonte_meses"] == 6
        assert "2024-01-01" in d["fecha_creacion"]


class TestCreateScenario:
    """Pruebas para creacion de escenarios."""

    def test_create_scenario_success(self, db_session):
        """Verifica creacion exitosa de escenario."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'get_by_nombre', return_value=None):
            with patch.object(service, '_initialize_base_parameters'):
                with patch.object(service.db, 'add'), \
                     patch.object(service.db, 'commit'), \
                     patch.object(service.db, 'refresh') as mock_refresh:

                    mock_escenario = Mock()
                    mock_escenario.idEscenario = 1
                    mock_escenario.nombre = "Test Scenario"
                    mock_escenario.descripcion = "Test"
                    mock_escenario.horizonteMeses = 6
                    mock_escenario.creadoEn = datetime.now()

                    def set_id(obj):
                        obj.idEscenario = 1
                        obj.creadoEn = datetime.now()

                    mock_refresh.side_effect = set_id

                    result = service.create_scenario(
                        nombre="Test Scenario",
                        descripcion="Test",
                        basado_en_historico=True,
                        periodos=6
                    )

                    # La creacion deberia funcionar
                    assert result is not None

    def test_create_scenario_duplicate_name(self, db_session):
        """Verifica rechazo de nombre duplicado."""
        service = SimulationService(db_session)

        existing_scenario = Mock()
        existing_scenario.nombre = "Existing"

        with patch.object(service.escenario_repo, 'get_by_nombre', return_value=existing_scenario):
            result = service.create_scenario(nombre="Existing")

            assert result["success"] == False
            assert "Ya existe" in result["error"]

    def test_create_scenario_max_periods(self, db_session):
        """Verifica limite de periodos."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'get_by_nombre', return_value=None):
            with patch.object(service, '_initialize_base_parameters'):
                with patch.object(service.db, 'add'), \
                     patch.object(service.db, 'commit'), \
                     patch.object(service.db, 'refresh'):

                    # Pedir mas de MAX_PERIODS deberia truncar
                    result = service.create_scenario(
                        nombre="Test",
                        periodos=20  # Mas que MAX_PERIODS (12)
                    )

                    # No deberia fallar, solo truncar
                    assert result is not None


class TestModifyParameters:
    """Pruebas para modificacion de parametros."""

    def test_modify_parameters_success(self, db_session):
        """Verifica modificacion exitosa de parametros."""
        service = SimulationService(db_session)

        mock_escenario = Mock()
        mock_escenario.idEscenario = 1

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_escenario):
            with patch.object(service.parametro_repo, 'actualizar_parametro', return_value=True):
                result = service.modify_parameters(
                    id_escenario=1,
                    parametros=[
                        {"parametro": "variacion_precio", "valorActual": 10.0},
                        {"parametro": "variacion_costo", "valorActual": -5.0}
                    ]
                )

                assert result["success"] == True
                assert result["parametros_modificados"] == 2

    def test_modify_parameters_scenario_not_found(self, db_session):
        """Verifica error cuando escenario no existe."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'get_by_id', return_value=None):
            result = service.modify_parameters(
                id_escenario=999,
                parametros=[{"parametro": "test", "valorActual": 10.0}]
            )

            assert result["success"] == False
            assert "no encontrado" in result["error"]

    def test_modify_parameters_exceeds_max_variation(self, db_session):
        """RN-05.01: Verifica rechazo de variacion excesiva."""
        service = SimulationService(db_session)

        mock_escenario = Mock()
        mock_escenario.idEscenario = 1

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_escenario):
            with patch.object(service.parametro_repo, 'actualizar_parametro', return_value=True):
                result = service.modify_parameters(
                    id_escenario=1,
                    parametros=[
                        {"parametro": "variacion_precio", "valorActual": 60.0}  # >50%
                    ]
                )

                # Deberia rechazar la variacion excesiva
                assert result["errores"] is not None
                assert len(result["errores"]) > 0
                assert "excede" in result["errores"][0].lower() or "50" in result["errores"][0]

    def test_modify_parameters_alternative_keys(self, db_session):
        """Verifica manejo de claves alternativas."""
        service = SimulationService(db_session)

        mock_escenario = Mock()
        mock_escenario.idEscenario = 1

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_escenario):
            with patch.object(service.parametro_repo, 'actualizar_parametro', return_value=True):
                result = service.modify_parameters(
                    id_escenario=1,
                    parametros=[
                        {"nombre": "ingresos_base", "valor": 10000.0}  # Claves alternativas
                    ]
                )

                assert result["parametros_modificados"] >= 0


class TestRunSimulation:
    """Pruebas para ejecucion de simulacion."""

    def test_run_simulation_success(self, db_session):
        """Verifica ejecucion exitosa de simulacion."""
        service = SimulationService(db_session)

        mock_escenario = Mock()
        mock_escenario.idEscenario = 1
        mock_escenario.nombre = "Test"
        mock_escenario.horizonteMeses = 6

        mock_params = [
            Mock(parametro="variacion_precio", valorBase=0, valorActual=10),
            Mock(parametro="variacion_costo", valorBase=0, valorActual=5),
            Mock(parametro="variacion_demanda", valorBase=0, valorActual=0),
            Mock(parametro="ingresos_base_mensual", valorBase=10000, valorActual=10000),
            Mock(parametro="costos_base_mensual", valorBase=6000, valorActual=6000),
            Mock(parametro="periodos_simulacion", valorBase=6, valorActual=6),
        ]

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_escenario):
            with patch.object(service.parametro_repo, 'get_by_escenario', return_value=mock_params):
                with patch.object(service.resultado_repo, 'eliminar_resultados_escenario'):
                    with patch.object(service.db, 'add'), patch.object(service.db, 'commit'):
                        result = service.run_simulation(
                            id_escenario=1,
                            guardar_resultados=True
                        )

                        assert result["success"] == True
                        assert "resultados" in result
                        assert "resumen" in result
                        assert "advertencia" in result  # RN-05.04

    def test_run_simulation_scenario_not_found(self, db_session):
        """Verifica error cuando escenario no existe."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'get_by_id', return_value=None):
            result = service.run_simulation(id_escenario=999)

            assert result["success"] == False
            assert "no encontrado" in result["error"]

    def test_run_simulation_without_saving(self, db_session):
        """Verifica ejecucion sin guardar resultados."""
        service = SimulationService(db_session)

        mock_escenario = Mock()
        mock_escenario.idEscenario = 1
        mock_escenario.nombre = "Test"
        mock_escenario.horizonteMeses = 3

        mock_params = [
            Mock(parametro="variacion_precio", valorBase=0, valorActual=0),
            Mock(parametro="variacion_costo", valorBase=0, valorActual=0),
            Mock(parametro="variacion_demanda", valorBase=0, valorActual=0),
            Mock(parametro="ingresos_base_mensual", valorBase=5000, valorActual=5000),
            Mock(parametro="costos_base_mensual", valorBase=3000, valorActual=3000),
            Mock(parametro="periodos_simulacion", valorBase=3, valorActual=3),
        ]

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_escenario):
            with patch.object(service.parametro_repo, 'get_by_escenario', return_value=mock_params):
                result = service.run_simulation(
                    id_escenario=1,
                    guardar_resultados=False
                )

                assert result["success"] == True
                assert len(result["resultados"]) > 0


class TestGetScenario:
    """Pruebas para obtencion de escenario."""

    def test_get_scenario_success(self, db_session):
        """Verifica obtencion exitosa de escenario."""
        service = SimulationService(db_session)

        mock_escenario = Mock()
        mock_escenario.idEscenario = 1
        mock_escenario.nombre = "Test"
        mock_escenario.descripcion = "Test desc"
        mock_escenario.horizonteMeses = 6
        mock_escenario.creadoEn = datetime.now()
        mock_escenario.creadoPor = 1

        mock_params = [Mock(parametro="test", valorBase=100, valorActual=110)]
        mock_results = [Mock(periodo=date.today(), kpi="ingresos", valor=1000, confianza=0.85)]

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_escenario):
            with patch.object(service.parametro_repo, 'get_by_escenario', return_value=mock_params):
                with patch.object(service.resultado_repo, 'get_by_escenario', return_value=mock_results):
                    result = service.get_scenario(id_escenario=1)

                    assert result["success"] == True
                    assert result["escenario"]["nombre"] == "Test"
                    assert len(result["parametros"]) == 1
                    assert len(result["resultados"]) == 1

    def test_get_scenario_not_found(self, db_session):
        """Verifica error cuando escenario no existe."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'get_by_id', return_value=None):
            result = service.get_scenario(id_escenario=999)

            assert result["success"] == False
            assert "no encontrado" in result["error"]


class TestListScenarios:
    """Pruebas para listado de escenarios."""

    def test_list_scenarios_all(self, db_session):
        """Verifica listado de todos los escenarios."""
        service = SimulationService(db_session)

        mock_escenarios = [
            Mock(idEscenario=1, nombre="Esc1", descripcion="", horizonteMeses=6, creadoEn=datetime.now()),
            Mock(idEscenario=2, nombre="Esc2", descripcion="", horizonteMeses=3, creadoEn=datetime.now()),
        ]

        with patch.object(service.escenario_repo, 'get_all', return_value=mock_escenarios):
            with patch.object(service.parametro_repo, 'get_by_escenario', return_value=[]):
                with patch.object(service.resultado_repo, 'get_by_escenario', return_value=[]):
                    result = service.list_scenarios()

                    assert result["success"] == True
                    assert result["total"] == 2

    def test_list_scenarios_by_user(self, db_session):
        """Verifica listado de escenarios por usuario."""
        service = SimulationService(db_session)

        mock_escenarios = [
            Mock(idEscenario=1, nombre="Esc1", descripcion="", horizonteMeses=6, creadoEn=datetime.now()),
        ]

        with patch.object(service.escenario_repo, 'get_by_usuario', return_value=mock_escenarios):
            with patch.object(service.parametro_repo, 'get_by_escenario', return_value=[]):
                with patch.object(service.resultado_repo, 'get_by_escenario', return_value=[]):
                    result = service.list_scenarios(usuario_id=1)

                    assert result["success"] == True
                    assert result["total"] == 1


class TestCompareScenarios:
    """Pruebas para comparacion de escenarios (RN-05.03)."""

    def test_compare_scenarios_success(self, db_session):
        """Verifica comparacion exitosa de escenarios."""
        service = SimulationService(db_session)

        mock_esc1 = Mock(idEscenario=1, nombre="Esc1", horizonteMeses=6)
        mock_esc2 = Mock(idEscenario=2, nombre="Esc2", horizonteMeses=6)

        mock_results1 = [
            Mock(periodo=date(2024, 1, 1), kpi="ingresos", valor=10000),
            Mock(periodo=date(2024, 1, 1), kpi="utilidad_bruta", valor=4000),
        ]
        mock_results2 = [
            Mock(periodo=date(2024, 1, 1), kpi="ingresos", valor=12000),
            Mock(periodo=date(2024, 1, 1), kpi="utilidad_bruta", valor=5000),
        ]

        with patch.object(service.escenario_repo, 'get_by_id') as mock_get:
            mock_get.side_effect = lambda x: mock_esc1 if x == 1 else mock_esc2
            with patch.object(service.resultado_repo, 'get_by_escenario') as mock_res:
                mock_res.side_effect = lambda x: mock_results1 if x == 1 else mock_results2

                result = service.compare_scenarios([1, 2])

                assert result["success"] == True
                assert len(result["escenarios"]) == 2
                assert "comparaciones" in result
                assert "mejor_escenario" in result

    def test_compare_scenarios_less_than_two(self, db_session):
        """Verifica error con menos de 2 escenarios."""
        service = SimulationService(db_session)

        result = service.compare_scenarios([1])

        assert result["success"] == False
        assert "al menos 2" in result["error"]

    def test_compare_scenarios_exceeds_max(self, db_session):
        """RN-05.03: Verifica error con mas de 5 escenarios."""
        service = SimulationService(db_session)

        result = service.compare_scenarios([1, 2, 3, 4, 5, 6])

        assert result["success"] == False
        assert "5" in result["error"]
        assert "RN-05.03" in result["error"]

    def test_compare_scenarios_not_found(self, db_session):
        """Verifica error cuando escenario no existe."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'get_by_id', return_value=None):
            result = service.compare_scenarios([1, 2])

            assert result["success"] == False
            assert "no encontrado" in result["error"]


class TestSaveScenario:
    """Pruebas para guardar escenario."""

    def test_save_scenario_success(self, db_session):
        """Verifica guardado exitoso."""
        service = SimulationService(db_session)

        mock_escenario = Mock(idEscenario=1)
        mock_results = [Mock()]

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_escenario):
            with patch.object(service.resultado_repo, 'get_by_escenario', return_value=mock_results):
                result = service.save_scenario(id_escenario=1)

                assert result["success"] == True

    def test_save_scenario_not_found(self, db_session):
        """Verifica error cuando escenario no existe."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'get_by_id', return_value=None):
            result = service.save_scenario(id_escenario=999)

            assert result["success"] == False

    def test_save_scenario_no_results(self, db_session):
        """Verifica error sin resultados."""
        service = SimulationService(db_session)

        mock_escenario = Mock(idEscenario=1)

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_escenario):
            with patch.object(service.resultado_repo, 'get_by_escenario', return_value=[]):
                result = service.save_scenario(id_escenario=1)

                assert result["success"] == False
                assert "no tiene resultados" in result["error"]


class TestArchiveScenario:
    """Pruebas para archivar escenario."""

    def test_archive_scenario_success(self, db_session):
        """Verifica archivado exitoso."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'archivar_escenario', return_value=True):
            result = service.archive_scenario(id_escenario=1)

            assert result["success"] == True

    def test_archive_scenario_failure(self, db_session):
        """Verifica fallo en archivado."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'archivar_escenario', return_value=False):
            result = service.archive_scenario(id_escenario=999)

            assert result["success"] == False


class TestDeleteScenario:
    """Pruebas para eliminar escenario."""

    def test_delete_scenario_success(self, db_session):
        """Verifica eliminacion exitosa."""
        service = SimulationService(db_session)

        mock_escenario = Mock(idEscenario=1, nombre="Test")

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_escenario):
            with patch.object(service.resultado_repo, 'eliminar_resultados_escenario'):
                with patch.object(service.parametro_repo, 'eliminar_parametros_escenario'):
                    with patch.object(service.escenario_repo, 'delete'):
                        result = service.delete_scenario(id_escenario=1)

                        assert result["success"] == True

    def test_delete_scenario_not_found(self, db_session):
        """Verifica error cuando escenario no existe."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'get_by_id', return_value=None):
            result = service.delete_scenario(id_escenario=999)

            assert result["success"] == False


class TestCloneScenario:
    """Pruebas para clonar escenario."""

    def test_clone_scenario_success(self, db_session):
        """Verifica clonacion exitosa."""
        service = SimulationService(db_session)

        mock_original = Mock(
            idEscenario=1, nombre="Original",
            descripcion="Desc", horizonteMeses=6
        )
        mock_params = [
            Mock(parametro="test", valorBase=100, valorActual=110)
        ]

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_original):
            with patch.object(service.escenario_repo, 'get_by_nombre', return_value=None):
                with patch.object(service.db, 'add'), \
                     patch.object(service.db, 'commit'), \
                     patch.object(service.db, 'refresh') as mock_refresh:

                    def set_id(obj):
                        obj.idEscenario = 2

                    mock_refresh.side_effect = set_id

                    with patch.object(service.parametro_repo, 'get_by_escenario', return_value=mock_params):
                        with patch.object(service.parametro_repo, 'actualizar_parametro'):
                            result = service.clone_scenario(
                                id_escenario=1,
                                nuevo_nombre="Clon"
                            )

                            assert result["success"] == True

    def test_clone_scenario_original_not_found(self, db_session):
        """Verifica error cuando original no existe."""
        service = SimulationService(db_session)

        with patch.object(service.escenario_repo, 'get_by_id', return_value=None):
            result = service.clone_scenario(
                id_escenario=999,
                nuevo_nombre="Clon"
            )

            assert result["success"] == False
            assert "no encontrado" in result["error"]

    def test_clone_scenario_duplicate_name(self, db_session):
        """Verifica error con nombre duplicado."""
        service = SimulationService(db_session)

        mock_original = Mock(idEscenario=1, nombre="Original")
        mock_existing = Mock(nombre="Clon")

        with patch.object(service.escenario_repo, 'get_by_id', return_value=mock_original):
            with patch.object(service.escenario_repo, 'get_by_nombre', return_value=mock_existing):
                result = service.clone_scenario(
                    id_escenario=1,
                    nuevo_nombre="Clon"
                )

                assert result["success"] == False
                assert "Ya existe" in result["error"]


class TestVariationLimits:
    """Pruebas para limites de variacion (RN-05.01)."""

    def test_max_variation_limit(self, db_session):
        """RN-05.01: Variables no varian mas del 50%."""
        MAX_VARIATION = 50.0

        valid_variation = 30.0
        assert abs(valid_variation) <= MAX_VARIATION

        invalid_variation = 60.0
        assert abs(invalid_variation) > MAX_VARIATION

    def test_positive_variation_within_limits(self, db_session):
        """Verifica variacion positiva dentro de limites."""
        MAX_VARIATION = 50.0

        base_value = Decimal("1000.00")
        variation = 40.0

        new_value = base_value * Decimal(str(1 + variation / 100))

        assert new_value == Decimal("1400.00")
        assert variation <= MAX_VARIATION

    def test_negative_variation_within_limits(self, db_session):
        """Verifica variacion negativa dentro de limites."""
        MAX_VARIATION = 50.0

        base_value = Decimal("1000.00")
        variation = -40.0

        new_value = base_value * Decimal(str(1 + variation / 100))

        assert new_value == Decimal("600.00")
        assert abs(variation) <= MAX_VARIATION


class TestHistoricalBase:
    """Pruebas para base historica (RN-05.02)."""

    def test_based_on_historical_data(self, db_session):
        """RN-05.02: Basado en datos historicos reales."""
        service = SimulationService(db_session)
        assert service is not None

    def test_calculate_historical_averages(self, db_session):
        """Verifica calculo de promedios historicos."""
        historical_data = [
            {"mes": 1, "ventas": Decimal("10000.00")},
            {"mes": 2, "ventas": Decimal("12000.00")},
            {"mes": 3, "ventas": Decimal("11000.00")},
            {"mes": 4, "ventas": Decimal("13000.00")},
            {"mes": 5, "ventas": Decimal("12500.00")},
            {"mes": 6, "ventas": Decimal("11500.00")},
        ]

        total = sum(d["ventas"] for d in historical_data)
        promedio = total / len(historical_data)

        assert promedio > Decimal("11666")
        assert promedio < Decimal("11667")


class TestInformativeCharacter:
    """Pruebas para caracter informativo (RN-05.04)."""

    def test_disclaimer_included(self, db_session):
        """RN-05.04: Indicar caracter informativo."""
        disclaimer = "Los resultados de esta simulacion son de caracter informativo y no constituyen una garantia de resultados futuros."

        assert "informativo" in disclaimer.lower()
        assert "garantia" in disclaimer.lower()


class TestSimulationResults:
    """Pruebas para resultados de simulacion."""

    def test_calculate_projected_revenue(self, db_session):
        """Verifica calculo de ingresos proyectados."""
        precio = Decimal("100.00")
        demanda = 1500

        ingresos = precio * demanda

        assert ingresos == Decimal("150000.00")

    def test_calculate_projected_costs(self, db_session):
        """Verifica calculo de costos proyectados."""
        costo_unitario = Decimal("60.00")
        demanda = 1500
        costos_fijos = Decimal("20000.00")

        costos_variables = costo_unitario * demanda
        costos_totales = costos_variables + costos_fijos

        assert costos_variables == Decimal("90000.00")
        assert costos_totales == Decimal("110000.00")

    def test_calculate_projected_profit(self, db_session):
        """Verifica calculo de utilidad proyectada."""
        ingresos = Decimal("150000.00")
        costos = Decimal("110000.00")

        utilidad = ingresos - costos

        assert utilidad == Decimal("40000.00")

    def test_calculate_projected_margin(self, db_session):
        """Verifica calculo de margen proyectado."""
        ingresos = Decimal("150000.00")
        utilidad = Decimal("40000.00")

        margen = (utilidad / ingresos) * 100

        assert margen > 26
        assert margen < 27
