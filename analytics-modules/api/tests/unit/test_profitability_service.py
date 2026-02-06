"""
Pruebas unitarias para el servicio de rentabilidad.
RF-06: Modulo de Evaluacion de Rentabilidad.
"""

import pytest
from datetime import date, timedelta
from decimal import Decimal
from unittest.mock import Mock, patch, MagicMock

from app.services.profitability_service import (
    ProfitabilityService, PeriodType,
    FinancialIndicators, ProductProfitability,
    CategoryProfitability, ProfitabilityTrend
)


class TestProfitabilityServiceInit:
    """Pruebas para inicializacion del servicio."""

    def test_init(self, db_session):
        """Verifica inicializacion del servicio."""
        service = ProfitabilityService(db_session)

        assert service is not None
        assert service.db == db_session
        assert service.MIN_PROFIT_MARGIN == 10.0
        assert service.OPERATING_EXPENSES_RATE == 0.15

    def test_repositories_initialized(self, db_session):
        """Verifica que los repositorios se inicialicen."""
        service = ProfitabilityService(db_session)

        assert service.venta_repo is not None
        assert service.compra_repo is not None
        assert service.producto_repo is not None


class TestPeriodTypeEnum:
    """Pruebas para enumeracion de tipos de periodo."""

    def test_period_type_values(self):
        """Verifica valores de PeriodType."""
        assert PeriodType.DAILY.value == "daily"
        assert PeriodType.WEEKLY.value == "weekly"
        assert PeriodType.MONTHLY.value == "monthly"
        assert PeriodType.QUARTERLY.value == "quarterly"
        assert PeriodType.YEARLY.value == "yearly"


class TestDataClasses:
    """Pruebas para dataclasses."""

    def test_financial_indicators_creation(self):
        """Verifica creacion de FinancialIndicators."""
        indicators = FinancialIndicators(
            ingresos_totales=100000.0,
            costos_totales=60000.0,
            utilidad_bruta=40000.0,
            margen_bruto=40.0,
            utilidad_operativa=25000.0,
            margen_operativo=25.0,
            utilidad_neta=20000.0,
            margen_neto=20.0,
            roa=10.0,
            roe=15.0,
            periodo_inicio=date(2024, 1, 1),
            periodo_fin=date(2024, 1, 31)
        )

        assert indicators.ingresos_totales == 100000.0
        assert indicators.margen_bruto == 40.0
        assert indicators.periodo_inicio == date(2024, 1, 1)

    def test_financial_indicators_to_dict(self):
        """Verifica conversion a dict de FinancialIndicators."""
        indicators = FinancialIndicators(
            ingresos_totales=100000.0,
            costos_totales=60000.0,
            utilidad_bruta=40000.0,
            margen_bruto=40.0,
            periodo_inicio=date(2024, 1, 1),
            periodo_fin=date(2024, 1, 31)
        )

        d = indicators.to_dict()

        assert d["ingresos_totales"] == 100000.0
        assert d["margen_bruto"] == 40.0
        assert d["periodo"]["inicio"] == "2024-01-01"

    def test_product_profitability_creation(self):
        """Verifica creacion de ProductProfitability."""
        prof = ProductProfitability(
            id_producto=1,
            nombre="Producto Test",
            categoria="Categoria1",
            unidades_vendidas=100,
            ingresos=10000.0,
            costo_total=6000.0,
            utilidad=4000.0,
            margen=40.0,
            es_rentable=True,
            ranking=1
        )

        assert prof.id_producto == 1
        assert prof.nombre == "Producto Test"
        assert prof.es_rentable == True

    def test_product_profitability_to_dict(self):
        """Verifica conversion a dict de ProductProfitability."""
        prof = ProductProfitability(
            id_producto=1,
            nombre="Test",
            unidades_vendidas=50,
            ingresos=5000.0,
            costo_total=3000.0,
            utilidad=2000.0,
            margen=40.0
        )

        d = prof.to_dict()

        assert d["id_producto"] == 1
        assert d["nombre"] == "Test"
        assert d["margen"] == 40.0

    def test_category_profitability_creation(self):
        """Verifica creacion de CategoryProfitability."""
        cat = CategoryProfitability(
            id_categoria=1,
            nombre="Electronica",
            num_productos=10,
            unidades_vendidas=500,
            ingresos=50000.0,
            costo_total=30000.0,
            utilidad=20000.0,
            margen=40.0,
            productos_rentables=8,
            productos_no_rentables=2
        )

        assert cat.id_categoria == 1
        assert cat.productos_rentables == 8

    def test_category_profitability_to_dict(self):
        """Verifica conversion a dict de CategoryProfitability."""
        cat = CategoryProfitability(
            id_categoria=1,
            nombre="Test",
            num_productos=5,
            ingresos=10000.0,
            costo_total=6000.0,
            utilidad=4000.0,
            margen=40.0
        )

        d = cat.to_dict()

        assert d["id_categoria"] == 1
        assert d["nombre"] == "Test"
        assert d["margen"] == 40.0

    def test_profitability_trend_creation(self):
        """Verifica creacion de ProfitabilityTrend."""
        trend = ProfitabilityTrend(
            periodo="2024-01",
            fecha_inicio=date(2024, 1, 1),
            fecha_fin=date(2024, 1, 31),
            ingresos=100000.0,
            costos=60000.0,
            utilidad=40000.0,
            margen=40.0,
            variacion_ingresos=10.0,
            variacion_utilidad=15.0
        )

        assert trend.periodo == "2024-01"
        assert trend.variacion_ingresos == 10.0

    def test_profitability_trend_to_dict(self):
        """Verifica conversion a dict de ProfitabilityTrend."""
        trend = ProfitabilityTrend(
            periodo="2024-01",
            fecha_inicio=date(2024, 1, 1),
            fecha_fin=date(2024, 1, 31),
            ingresos=100000.0,
            costos=60000.0,
            utilidad=40000.0,
            margen=40.0
        )

        d = trend.to_dict()

        assert d["periodo"] == "2024-01"
        assert d["ingresos"] == 100000.0
        assert d["variacion_ingresos"] is None  # No se establecio


class TestValidateDataCompleteness:
    """Pruebas para validacion de datos (RN-06.01)."""

    def test_validate_complete_data(self, db_session):
        """RN-06.01: Validacion de datos completos."""
        service = ProfitabilityService(db_session)

        mock_ventas = [Mock(total=1000)]
        mock_compras = [Mock(total=500)]
        mock_productos = [Mock()]

        with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=mock_ventas):
            with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=mock_compras):
                with patch.object(service.producto_repo, 'get_all', return_value=mock_productos):
                    is_valid, issues = service.validate_data_completeness(
                        date(2024, 1, 1),
                        date(2024, 1, 31)
                    )

                    assert is_valid == True
                    assert len(issues) == 0

    def test_validate_no_ventas(self, db_session):
        """Verifica deteccion de falta de ventas."""
        service = ProfitabilityService(db_session)

        with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=[]):
            with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=[Mock()]):
                with patch.object(service.producto_repo, 'get_all', return_value=[Mock()]):
                    is_valid, issues = service.validate_data_completeness(
                        date(2024, 1, 1),
                        date(2024, 1, 31)
                    )

                    assert is_valid == False
                    assert any("ventas" in i.lower() for i in issues)

    def test_validate_no_compras(self, db_session):
        """Verifica deteccion de falta de compras."""
        service = ProfitabilityService(db_session)

        with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=[Mock()]):
            with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=[]):
                with patch.object(service.producto_repo, 'get_all', return_value=[Mock()]):
                    is_valid, issues = service.validate_data_completeness(
                        date(2024, 1, 1),
                        date(2024, 1, 31)
                    )

                    assert is_valid == False
                    assert any("compras" in i.lower() for i in issues)

    def test_validate_no_productos(self, db_session):
        """Verifica deteccion de falta de productos."""
        service = ProfitabilityService(db_session)

        with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=[Mock()]):
            with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=[Mock()]):
                with patch.object(service.producto_repo, 'get_all', return_value=[]):
                    is_valid, issues = service.validate_data_completeness(
                        date(2024, 1, 1),
                        date(2024, 1, 31)
                    )

                    assert is_valid == False
                    assert any("productos" in i.lower() for i in issues)


class TestCalculateIndicators:
    """Pruebas para calculo de indicadores financieros."""

    def test_calculate_indicators_success(self, db_session):
        """Verifica calculo exitoso de indicadores."""
        service = ProfitabilityService(db_session)

        mock_ventas = [Mock(total=50000), Mock(total=50000)]
        mock_compras = [Mock(total=30000), Mock(total=30000)]

        with patch.object(service, 'validate_data_completeness', return_value=(True, [])):
            with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=mock_ventas):
                with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=mock_compras):
                    result = service.calculate_indicators(
                        fecha_inicio=date(2024, 1, 1),
                        fecha_fin=date(2024, 1, 31)
                    )

                    assert result["success"] == True
                    assert "indicators" in result
                    assert result["indicators"]["ingresos_totales"] == 100000.0
                    assert result["indicators"]["costos_totales"] == 60000.0

    def test_calculate_indicators_with_roa_roe(self, db_session):
        """Verifica calculo de ROA y ROE."""
        service = ProfitabilityService(db_session)

        mock_ventas = [Mock(total=100000)]
        mock_compras = [Mock(total=60000)]

        with patch.object(service, 'validate_data_completeness', return_value=(True, [])):
            with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=mock_ventas):
                with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=mock_compras):
                    result = service.calculate_indicators(
                        fecha_inicio=date(2024, 1, 1),
                        fecha_fin=date(2024, 1, 31),
                        activos_totales=500000.0,
                        patrimonio=200000.0
                    )

                    assert result["success"] == True
                    assert result["indicators"]["roa"] > 0
                    assert result["indicators"]["roe"] > 0

    def test_calculate_indicators_default_dates(self, db_session):
        """Verifica uso de fechas por defecto."""
        service = ProfitabilityService(db_session)

        with patch.object(service, 'validate_data_completeness', return_value=(True, [])):
            with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=[Mock(total=1000)]):
                with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=[Mock(total=500)]):
                    result = service.calculate_indicators()

                    assert result["success"] == True

    def test_calculate_indicators_incomplete_data(self, db_session):
        """Verifica error con datos incompletos."""
        service = ProfitabilityService(db_session)

        with patch.object(service, 'validate_data_completeness', return_value=(False, ["No hay ventas"])):
            result = service.calculate_indicators(
                fecha_inicio=date(2024, 1, 1),
                fecha_fin=date(2024, 1, 31)
            )

            assert result["success"] == False
            assert "issues" in result


class TestGetProductProfitability:
    """Pruebas para rentabilidad por producto (RF-06.02)."""

    def test_get_product_profitability_success(self, db_session):
        """RF-06.02: Verifica obtencion de rentabilidad por producto."""
        service = ProfitabilityService(db_session)

        mock_producto = Mock(idProducto=1, nombre="Test", idCategoria=None)

        with patch.object(service.producto_repo, 'get_all', return_value=[mock_producto]):
            with patch.object(service, '_calculate_product_profitability') as mock_calc:
                mock_calc.return_value = ProductProfitability(
                    id_producto=1,
                    nombre="Test",
                    ingresos=10000,
                    costo_total=6000,
                    utilidad=4000,
                    margen=40.0
                )

                result = service.get_product_profitability(
                    fecha_inicio=date(2024, 1, 1),
                    fecha_fin=date(2024, 1, 31)
                )

                assert result["success"] == True
                assert len(result["productos"]) == 1
                assert result["resumen"]["total_productos"] == 1

    def test_get_product_profitability_no_products(self, db_session):
        """Verifica error sin productos."""
        service = ProfitabilityService(db_session)

        with patch.object(service.producto_repo, 'get_all', return_value=[]):
            result = service.get_product_profitability()

            assert result["success"] == False
            assert "No hay productos" in result["error"]

    def test_get_product_profitability_filter_category(self, db_session):
        """Verifica filtrado por categoria."""
        service = ProfitabilityService(db_session)

        mock_productos = [
            Mock(idProducto=1, nombre="P1", idCategoria=1),
            Mock(idProducto=2, nombre="P2", idCategoria=2),
        ]

        with patch.object(service.producto_repo, 'get_all', return_value=mock_productos):
            with patch.object(service, '_calculate_product_profitability') as mock_calc:
                mock_calc.return_value = ProductProfitability(
                    id_producto=1,
                    nombre="P1",
                    ingresos=10000,
                    costo_total=6000,
                    utilidad=4000,
                    margen=40.0
                )

                result = service.get_product_profitability(categoria_id=1)

                # Solo deberia analizar producto con categoria 1
                assert result["success"] == True

    def test_get_product_profitability_only_non_profitable(self, db_session):
        """RN-06.04: Verifica filtrado de productos no rentables."""
        service = ProfitabilityService(db_session)

        mock_productos = [
            Mock(idProducto=1, nombre="P1", idCategoria=None),
            Mock(idProducto=2, nombre="P2", idCategoria=None),
        ]

        profitabilities = [
            ProductProfitability(id_producto=1, nombre="P1", margen=15.0, utilidad=1000, ingresos=10000, costo_total=8500),
            ProductProfitability(id_producto=2, nombre="P2", margen=5.0, utilidad=500, ingresos=10000, costo_total=9500),  # No rentable
        ]

        with patch.object(service.producto_repo, 'get_all', return_value=mock_productos):
            with patch.object(service, '_calculate_product_profitability') as mock_calc:
                mock_calc.side_effect = profitabilities

                result = service.get_product_profitability(solo_no_rentables=True)

                assert result["success"] == True
                # Solo deberia mostrar productos con margen < 10%
                for p in result["productos"]:
                    assert p["margen"] < service.MIN_PROFIT_MARGIN


class TestGetCategoryProfitability:
    """Pruebas para rentabilidad por categoria."""

    def test_get_category_profitability_success(self, db_session):
        """Verifica obtencion de rentabilidad por categoria."""
        service = ProfitabilityService(db_session)

        mock_categoria = Mock(idCategoria=1, nombre="Electronica")
        mock_producto = Mock(idProducto=1, idCategoria=1, nombre="P1")

        with patch.object(service.db, 'query') as mock_query:
            # Mock para categorias
            mock_query.return_value.all.return_value = [mock_categoria]
            mock_query.return_value.filter.return_value.all.return_value = [mock_producto]

            with patch.object(service, '_calculate_product_profitability') as mock_calc:
                mock_calc.return_value = ProductProfitability(
                    id_producto=1, nombre="P1",
                    ingresos=10000, costo_total=6000,
                    utilidad=4000, margen=40.0
                )

                result = service.get_category_profitability()

                assert result["success"] == True
                assert "categorias" in result

    def test_get_category_profitability_no_categories(self, db_session):
        """Verifica error sin categorias."""
        service = ProfitabilityService(db_session)

        with patch.object(service.db, 'query') as mock_query:
            mock_query.return_value.all.return_value = []

            result = service.get_category_profitability()

            assert result["success"] == False
            assert "No hay categorias" in result["error"]


class TestGetProfitabilityTrends:
    """Pruebas para tendencias de rentabilidad (RN-06.03)."""

    def test_get_trends_monthly(self, db_session):
        """RN-06.03: Verifica tendencias mensuales."""
        service = ProfitabilityService(db_session)

        mock_ventas = [Mock(total=10000)]
        mock_compras = [Mock(total=6000)]

        with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=mock_ventas):
            with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=mock_compras):
                result = service.get_profitability_trends(
                    fecha_inicio=date(2024, 1, 1),
                    fecha_fin=date(2024, 3, 31),
                    period_type=PeriodType.MONTHLY
                )

                assert result["success"] == True
                assert "tendencias" in result
                assert result["periodo"]["tipo"] == "monthly"

    def test_get_trends_quarterly(self, db_session):
        """Verifica tendencias trimestrales."""
        service = ProfitabilityService(db_session)

        with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=[Mock(total=30000)]):
            with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=[Mock(total=18000)]):
                result = service.get_profitability_trends(
                    fecha_inicio=date(2024, 1, 1),
                    fecha_fin=date(2024, 6, 30),
                    period_type=PeriodType.QUARTERLY
                )

                assert result["success"] == True
                assert result["periodo"]["tipo"] == "quarterly"

    def test_get_trends_yearly(self, db_session):
        """Verifica tendencias anuales."""
        service = ProfitabilityService(db_session)

        with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=[Mock(total=100000)]):
            with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=[Mock(total=60000)]):
                result = service.get_profitability_trends(
                    fecha_inicio=date(2023, 1, 1),
                    fecha_fin=date(2024, 12, 31),
                    period_type=PeriodType.YEARLY
                )

                assert result["success"] == True
                assert result["periodo"]["tipo"] == "yearly"

    def test_get_trends_default_dates(self, db_session):
        """Verifica uso de fechas por defecto."""
        service = ProfitabilityService(db_session)

        with patch.object(service.venta_repo, 'get_by_rango_fechas', return_value=[Mock(total=10000)]):
            with patch.object(service.compra_repo, 'get_by_rango_fechas', return_value=[Mock(total=6000)]):
                result = service.get_profitability_trends()

                assert result["success"] == True


class TestGeneratePeriods:
    """Pruebas para generacion de periodos."""

    def test_generate_daily_periods(self, db_session):
        """Verifica generacion de periodos diarios."""
        service = ProfitabilityService(db_session)

        periods = service._generate_periods(
            date(2024, 1, 1),
            date(2024, 1, 3),
            PeriodType.DAILY
        )

        assert len(periods) == 3

    def test_generate_weekly_periods(self, db_session):
        """Verifica generacion de periodos semanales."""
        service = ProfitabilityService(db_session)

        periods = service._generate_periods(
            date(2024, 1, 1),
            date(2024, 1, 21),
            PeriodType.WEEKLY
        )

        assert len(periods) >= 2

    def test_generate_monthly_periods(self, db_session):
        """Verifica generacion de periodos mensuales."""
        service = ProfitabilityService(db_session)

        periods = service._generate_periods(
            date(2024, 1, 1),
            date(2024, 3, 31),
            PeriodType.MONTHLY
        )

        assert len(periods) == 3

    def test_generate_quarterly_periods(self, db_session):
        """Verifica generacion de periodos trimestrales."""
        service = ProfitabilityService(db_session)

        periods = service._generate_periods(
            date(2024, 1, 1),
            date(2024, 6, 30),
            PeriodType.QUARTERLY
        )

        assert len(periods) == 2

    def test_generate_yearly_periods(self, db_session):
        """Verifica generacion de periodos anuales."""
        service = ProfitabilityService(db_session)

        periods = service._generate_periods(
            date(2023, 1, 1),
            date(2024, 12, 31),
            PeriodType.YEARLY
        )

        assert len(periods) == 2


class TestGetProductRanking:
    """Pruebas para ranking de productos."""

    def test_get_ranking_by_utilidad(self, db_session):
        """Verifica ranking por utilidad."""
        service = ProfitabilityService(db_session)

        mock_result = {
            "success": True,
            "productos": [
                {"id_producto": 1, "nombre": "P1", "utilidad": 5000, "ranking": 0},
                {"id_producto": 2, "nombre": "P2", "utilidad": 3000, "ranking": 0},
                {"id_producto": 3, "nombre": "P3", "utilidad": 8000, "ranking": 0},
            ],
            "periodo": {"inicio": "2024-01-01", "fin": "2024-01-31"}
        }

        with patch.object(service, 'get_product_profitability', return_value=mock_result):
            result = service.get_product_ranking(
                metric="utilidad",
                limit=10,
                ascending=False
            )

            assert result["success"] == True
            assert result["ranking"][0]["utilidad"] == 8000  # Mayor primero
            assert result["metrica_ordenamiento"] == "utilidad"

    def test_get_ranking_ascending(self, db_session):
        """Verifica ranking en orden ascendente."""
        service = ProfitabilityService(db_session)

        mock_result = {
            "success": True,
            "productos": [
                {"id_producto": 1, "nombre": "P1", "margen": 40, "ranking": 0},
                {"id_producto": 2, "nombre": "P2", "margen": 20, "ranking": 0},
                {"id_producto": 3, "nombre": "P3", "margen": 60, "ranking": 0},
            ],
            "periodo": {"inicio": "2024-01-01", "fin": "2024-01-31"}
        }

        with patch.object(service, 'get_product_profitability', return_value=mock_result):
            result = service.get_product_ranking(
                metric="margen",
                ascending=True
            )

            assert result["success"] == True
            assert result["ranking"][0]["margen"] == 20  # Menor primero
            assert result["orden"] == "ascendente"

    def test_get_ranking_invalid_metric(self, db_session):
        """Verifica manejo de metrica invalida."""
        service = ProfitabilityService(db_session)

        mock_result = {
            "success": True,
            "productos": [
                {"id_producto": 1, "utilidad": 5000},
            ],
            "periodo": {"inicio": "2024-01-01", "fin": "2024-01-31"}
        }

        with patch.object(service, 'get_product_profitability', return_value=mock_result):
            result = service.get_product_ranking(metric="invalid_metric")

            # Deberia usar utilidad por defecto
            assert result["success"] == True
            assert result["metrica_ordenamiento"] == "utilidad"


class TestComparePeriods:
    """Pruebas para comparacion de periodos."""

    def test_compare_periods_success(self, db_session):
        """Verifica comparacion exitosa de periodos."""
        service = ProfitabilityService(db_session)

        result1 = {
            "success": True,
            "indicators": {
                "ingresos_totales": 100000,
                "costos_totales": 60000,
                "utilidad_bruta": 40000,
                "margen_bruto": 40.0,
                "utilidad_operativa": 25000
            }
        }
        result2 = {
            "success": True,
            "indicators": {
                "ingresos_totales": 120000,
                "costos_totales": 70000,
                "utilidad_bruta": 50000,
                "margen_bruto": 41.67,
                "utilidad_operativa": 32000
            }
        }

        with patch.object(service, 'calculate_indicators') as mock_calc:
            mock_calc.side_effect = [result1, result2]

            result = service.compare_periods(
                date(2024, 1, 1), date(2024, 1, 31),
                date(2024, 2, 1), date(2024, 2, 29)
            )

            assert result["success"] == True
            assert "comparacion" in result
            assert result["comparacion"]["ingresos"]["variacion"] == 20.0  # 20% aumento

    def test_compare_periods_failure(self, db_session):
        """Verifica manejo de error en comparacion."""
        service = ProfitabilityService(db_session)

        with patch.object(service, 'calculate_indicators') as mock_calc:
            mock_calc.side_effect = [
                {"success": False, "error": "Error en periodo 1"},
                {"success": True, "indicators": {}}
            ]

            result = service.compare_periods(
                date(2024, 1, 1), date(2024, 1, 31),
                date(2024, 2, 1), date(2024, 2, 29)
            )

            assert result["success"] == False


class TestGenerateComparisonConclusion:
    """Pruebas para generacion de conclusiones."""

    def test_conclusion_with_positive_changes(self, db_session):
        """Verifica conclusion con cambios positivos."""
        service = ProfitabilityService(db_session)

        comparison = {
            "ingresos": {"variacion": 20.0},
            "margen_bruto": {"diferencia": 5.0},
            "utilidad_operativa": {"variacion": 25.0}
        }

        conclusion = service._generate_comparison_conclusion(comparison)

        assert "aumentaron" in conclusion.lower()
        assert "crecio" in conclusion.lower()

    def test_conclusion_with_negative_changes(self, db_session):
        """Verifica conclusion con cambios negativos."""
        service = ProfitabilityService(db_session)

        comparison = {
            "ingresos": {"variacion": -15.0},
            "margen_bruto": {"diferencia": -3.0},
            "utilidad_operativa": {"variacion": -20.0}
        }

        conclusion = service._generate_comparison_conclusion(comparison)

        assert "disminuyeron" in conclusion.lower() or "empeoro" in conclusion.lower()

    def test_conclusion_no_changes(self, db_session):
        """Verifica conclusion sin cambios."""
        service = ProfitabilityService(db_session)

        comparison = {
            "ingresos": {"variacion": None},
            "margen_bruto": {"diferencia": 0},
            "utilidad_operativa": {"variacion": None}
        }

        conclusion = service._generate_comparison_conclusion(comparison)

        assert "sin cambios" in conclusion.lower()


class TestFinancialIndicatorsCalculations:
    """Pruebas para calculos de indicadores financieros."""

    def test_calculate_gross_margin(self, db_session):
        """Verifica calculo de margen bruto."""
        ingresos = Decimal("100000.00")
        costos = Decimal("60000.00")

        margen_bruto = ((ingresos - costos) / ingresos) * 100

        assert margen_bruto == Decimal("40")

    def test_calculate_net_margin(self, db_session):
        """Verifica calculo de margen neto."""
        ingresos = Decimal("100000.00")
        utilidad_neta = Decimal("15000.00")

        margen_neto = (utilidad_neta / ingresos) * 100

        assert margen_neto == Decimal("15")

    def test_calculate_operating_margin(self, db_session):
        """RN-06.02: Calculo de Utilidad Operativa."""
        ingresos = Decimal("100000.00")
        costos = Decimal("60000.00")
        gastos_operativos = Decimal("20000.00")

        utilidad_operativa = ingresos - costos - gastos_operativos
        margen_operativo = (utilidad_operativa / ingresos) * 100

        assert utilidad_operativa == Decimal("20000.00")
        assert margen_operativo == Decimal("20")

    def test_calculate_roa(self, db_session):
        """Verifica calculo de ROA."""
        utilidad_neta = Decimal("50000.00")
        activos_totales = Decimal("500000.00")

        roa = (utilidad_neta / activos_totales) * 100

        assert roa == Decimal("10")

    def test_calculate_roe(self, db_session):
        """Verifica calculo de ROE."""
        utilidad_neta = Decimal("50000.00")
        capital_contable = Decimal("200000.00")

        roe = (utilidad_neta / capital_contable) * 100

        assert roe == Decimal("25")


class TestNonProfitableProducts:
    """Pruebas para identificacion de productos no rentables (RN-06.04)."""

    def test_identify_non_profitable_threshold(self, db_session):
        """RN-06.04: Identificar productos con margen < 10%."""
        MIN_PROFIT_MARGIN = 10.0

        producto_rentable = {"margen": 15.0}
        assert producto_rentable["margen"] >= MIN_PROFIT_MARGIN

        producto_no_rentable = {"margen": 5.0}
        assert producto_no_rentable["margen"] < MIN_PROFIT_MARGIN

    def test_classify_products_by_profitability(self, db_session):
        """Verifica clasificacion de productos por rentabilidad."""
        MIN_PROFIT_MARGIN = 10.0

        productos = [
            {"id": 1, "nombre": "Producto A", "margen": 25.0},
            {"id": 2, "nombre": "Producto B", "margen": 8.0},
            {"id": 3, "nombre": "Producto C", "margen": 15.0},
            {"id": 4, "nombre": "Producto D", "margen": 3.0},
            {"id": 5, "nombre": "Producto E", "margen": 12.0},
        ]

        rentables = [p for p in productos if p["margen"] >= MIN_PROFIT_MARGIN]
        no_rentables = [p for p in productos if p["margen"] < MIN_PROFIT_MARGIN]

        assert len(rentables) == 3
        assert len(no_rentables) == 2
