"""
Tests para DashboardService.
RF-07: Dashboard ejecutivo con KPIs consolidados.
"""

import pytest
from unittest.mock import Mock, MagicMock, patch
from datetime import date, datetime, timedelta
from decimal import Decimal

from app.services.dashboard_service import DashboardService


class TestDashboardServiceInit:
    """Tests de inicializacion de DashboardService."""

    def test_init_creates_repositories(self):
        """Verifica que la inicializacion crea los repositorios."""
        mock_db = Mock()
        service = DashboardService(mock_db)

        assert service.db == mock_db
        assert service.venta_repo is not None
        assert service.compra_repo is not None
        assert service.producto_repo is not None


class TestGetExecutiveDashboard:
    """Tests para get_executive_dashboard."""

    @pytest.fixture
    def mock_db(self):
        """Fixture para mock de base de datos."""
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        """Fixture para DashboardService."""
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_get_executive_dashboard_default_dates(self, dashboard_service):
        """Test dashboard sin fechas (usa valores por defecto)."""
        with patch.object(dashboard_service, '_get_sales_summary') as mock_sales, \
             patch.object(dashboard_service, '_get_purchases_summary') as mock_purchases, \
             patch.object(dashboard_service, '_calculate_financial_kpis') as mock_kpis, \
             patch.object(dashboard_service, '_get_active_alerts') as mock_alerts, \
             patch.object(dashboard_service, '_get_trends') as mock_trends, \
             patch.object(dashboard_service, '_get_top_products') as mock_top:

            mock_sales.return_value = {"total": 1000}
            mock_purchases.return_value = {"total": 500}
            mock_kpis.return_value = {"utilidad_bruta": 500}
            mock_alerts.return_value = {"total": 0}
            mock_trends.return_value = {"ventas": [], "compras": []}
            mock_top.return_value = {"por_cantidad": []}

            result = dashboard_service.get_executive_dashboard()

            assert result["success"] is True
            assert "periodo" in result
            assert "resumen_ventas" in result
            assert "resumen_compras" in result
            assert "kpis_financieros" in result
            assert "alertas_activas" in result
            assert "tendencias" in result
            assert "top_productos" in result
            assert "fecha_generacion" in result

    def test_get_executive_dashboard_with_dates(self, dashboard_service):
        """Test dashboard con fechas especificas."""
        fecha_inicio = date(2024, 1, 1)
        fecha_fin = date(2024, 1, 31)

        with patch.object(dashboard_service, '_get_sales_summary') as mock_sales, \
             patch.object(dashboard_service, '_get_purchases_summary') as mock_purchases, \
             patch.object(dashboard_service, '_calculate_financial_kpis') as mock_kpis, \
             patch.object(dashboard_service, '_get_active_alerts') as mock_alerts, \
             patch.object(dashboard_service, '_get_trends') as mock_trends, \
             patch.object(dashboard_service, '_get_top_products') as mock_top:

            mock_sales.return_value = {"total": 2000}
            mock_purchases.return_value = {"total": 1000}
            mock_kpis.return_value = {"utilidad_bruta": 1000}
            mock_alerts.return_value = {"total": 5}
            mock_trends.return_value = {"ventas": [], "compras": []}
            mock_top.return_value = {"por_cantidad": []}

            result = dashboard_service.get_executive_dashboard(fecha_inicio, fecha_fin)

            assert result["success"] is True
            assert result["periodo"]["fecha_inicio"] == "2024-01-01"
            assert result["periodo"]["fecha_fin"] == "2024-01-31"


class TestSalesSummary:
    """Tests para _get_sales_summary."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_sales_summary_with_sales(self, dashboard_service):
        """Test resumen de ventas con datos."""
        mock_venta1 = Mock(total=Decimal('1000.00'))
        mock_venta2 = Mock(total=Decimal('1500.00'))

        dashboard_service.venta_repo.get_by_rango_fechas = Mock(
            side_effect=[
                [mock_venta1, mock_venta2],  # Periodo actual
                [Mock(total=Decimal('2000.00'))]  # Periodo anterior
            ]
        )

        result = dashboard_service._get_sales_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["total"] == 2500.0
        assert result["cantidad"] == 2
        assert result["ticket_promedio"] == 1250.0
        assert "variacion_periodo_anterior" in result
        assert "tendencia" in result

    def test_sales_summary_no_sales(self, dashboard_service):
        """Test resumen de ventas sin datos."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[])

        result = dashboard_service._get_sales_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["total"] == 0
        assert result["cantidad"] == 0
        assert result["ticket_promedio"] == 0

    def test_sales_summary_tendencia_alza(self, dashboard_service):
        """Test tendencia al alza."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(
            side_effect=[
                [Mock(total=Decimal('2000.00'))],  # Actual
                [Mock(total=Decimal('1000.00'))]   # Anterior
            ]
        )

        result = dashboard_service._get_sales_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["tendencia"] == "alza"
        assert result["variacion_periodo_anterior"] == 100.0

    def test_sales_summary_tendencia_baja(self, dashboard_service):
        """Test tendencia a la baja."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(
            side_effect=[
                [Mock(total=Decimal('500.00'))],   # Actual
                [Mock(total=Decimal('1000.00'))]   # Anterior
            ]
        )

        result = dashboard_service._get_sales_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["tendencia"] == "baja"
        assert result["variacion_periodo_anterior"] == -50.0

    def test_sales_summary_tendencia_estable(self, dashboard_service):
        """Test tendencia estable."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(
            side_effect=[
                [Mock(total=Decimal('1000.00'))],
                [Mock(total=Decimal('1000.00'))]
            ]
        )

        result = dashboard_service._get_sales_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["tendencia"] == "estable"

    def test_sales_summary_exception(self, dashboard_service):
        """Test manejo de excepciones."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(side_effect=Exception("DB Error"))

        result = dashboard_service._get_sales_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["total"] == 0
        assert result["tendencia"] == "sin_datos"


class TestPurchasesSummary:
    """Tests para _get_purchases_summary."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_purchases_summary_with_purchases(self, dashboard_service):
        """Test resumen de compras con datos."""
        mock_compra1 = Mock(total=Decimal('800.00'))
        mock_compra2 = Mock(total=Decimal('1200.00'))

        dashboard_service.compra_repo.get_by_rango_fechas = Mock(
            side_effect=[
                [mock_compra1, mock_compra2],
                [Mock(total=Decimal('1500.00'))]
            ]
        )

        result = dashboard_service._get_purchases_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["total"] == 2000.0
        assert result["cantidad"] == 2
        assert result["compra_promedio"] == 1000.0

    def test_purchases_summary_no_purchases(self, dashboard_service):
        """Test resumen de compras sin datos."""
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(return_value=[])

        result = dashboard_service._get_purchases_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["total"] == 0
        assert result["cantidad"] == 0
        assert result["compra_promedio"] == 0

    def test_purchases_summary_tendencia_alza(self, dashboard_service):
        """Test tendencia al alza en compras."""
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(
            side_effect=[
                [Mock(total=Decimal('3000.00'))],
                [Mock(total=Decimal('1000.00'))]
            ]
        )

        result = dashboard_service._get_purchases_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["tendencia"] == "alza"

    def test_purchases_summary_tendencia_baja(self, dashboard_service):
        """Test tendencia a la baja en compras."""
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(
            side_effect=[
                [Mock(total=Decimal('500.00'))],
                [Mock(total=Decimal('1500.00'))]
            ]
        )

        result = dashboard_service._get_purchases_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["tendencia"] == "baja"

    def test_purchases_summary_exception(self, dashboard_service):
        """Test manejo de excepciones en compras."""
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(side_effect=Exception("DB Error"))

        result = dashboard_service._get_purchases_summary(date(2024, 1, 1), date(2024, 1, 31))

        assert result["total"] == 0
        assert result["tendencia"] == "sin_datos"


class TestFinancialKPIs:
    """Tests para _calculate_financial_kpis."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_financial_kpis_calculation(self, dashboard_service):
        """Test calculo de KPIs financieros."""
        resumen_ventas = {"total": 10000}
        resumen_compras = {"total": 6000}

        result = dashboard_service._calculate_financial_kpis(resumen_ventas, resumen_compras)

        assert result["ingresos_totales"] == 10000
        assert result["costos_totales"] == 6000
        assert result["utilidad_bruta"] == 4000
        assert result["margen_bruto_porcentaje"] == 40.0
        assert result["roi_porcentaje"] == pytest.approx(66.67, rel=0.01)

    def test_financial_kpis_zero_ingresos(self, dashboard_service):
        """Test con ingresos cero."""
        resumen_ventas = {"total": 0}
        resumen_compras = {"total": 1000}

        result = dashboard_service._calculate_financial_kpis(resumen_ventas, resumen_compras)

        assert result["margen_bruto_porcentaje"] == 0

    def test_financial_kpis_zero_costos(self, dashboard_service):
        """Test con costos cero."""
        resumen_ventas = {"total": 5000}
        resumen_compras = {"total": 0}

        result = dashboard_service._calculate_financial_kpis(resumen_ventas, resumen_compras)

        assert result["roi_porcentaje"] == 0


class TestFinancialStatus:
    """Tests para _get_financial_status."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_financial_status_excelente(self, dashboard_service):
        """Test estado financiero excelente."""
        assert dashboard_service._get_financial_status(35) == "excelente"
        assert dashboard_service._get_financial_status(30) == "excelente"

    def test_financial_status_bueno(self, dashboard_service):
        """Test estado financiero bueno."""
        assert dashboard_service._get_financial_status(25) == "bueno"
        assert dashboard_service._get_financial_status(20) == "bueno"

    def test_financial_status_aceptable(self, dashboard_service):
        """Test estado financiero aceptable."""
        assert dashboard_service._get_financial_status(15) == "aceptable"
        assert dashboard_service._get_financial_status(10) == "aceptable"

    def test_financial_status_bajo(self, dashboard_service):
        """Test estado financiero bajo."""
        assert dashboard_service._get_financial_status(5) == "bajo"
        assert dashboard_service._get_financial_status(0) == "bajo"

    def test_financial_status_critico(self, dashboard_service):
        """Test estado financiero critico."""
        assert dashboard_service._get_financial_status(-5) == "critico"
        assert dashboard_service._get_financial_status(-20) == "critico"


class TestActiveAlerts:
    """Tests para _get_active_alerts."""

    @pytest.fixture
    def mock_db(self):
        db = Mock()
        db.query.return_value.filter.return_value.order_by.return_value.limit.return_value.all.return_value = []
        return db

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_active_alerts_with_alerts(self, dashboard_service):
        """Test alertas activas con datos."""
        mock_alerta = Mock()
        mock_alerta.idAlerta = 1
        mock_alerta.tipo = "stock_bajo"
        mock_alerta.importancia = "alta"
        mock_alerta.metrica = "stock"
        mock_alerta.valorActual = Decimal('5.0')
        mock_alerta.valorEsperado = Decimal('20.0')
        mock_alerta.creadaEn = datetime(2024, 1, 15, 10, 30)

        dashboard_service.db.query.return_value.filter.return_value.order_by.return_value.limit.return_value.all.return_value = [mock_alerta]

        result = dashboard_service._get_active_alerts()

        assert result["total"] == 1
        assert "stock_bajo" in result["por_tipo"]
        assert result["por_importancia"]["alta"] == 1

    def test_active_alerts_no_alerts(self, dashboard_service):
        """Test sin alertas activas."""
        dashboard_service.db.query.return_value.filter.return_value.order_by.return_value.limit.return_value.all.return_value = []

        result = dashboard_service._get_active_alerts()

        assert result["total"] == 0
        assert result["alertas"] == []

    def test_active_alerts_exception(self, dashboard_service):
        """Test manejo de excepciones en alertas."""
        dashboard_service.db.query.side_effect = Exception("DB Error")

        result = dashboard_service._get_active_alerts()

        assert result["total"] == 0
        assert result["alertas"] == []


class TestTrends:
    """Tests para _get_trends."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_trends_with_data(self, dashboard_service):
        """Test tendencias con datos."""
        mock_venta = Mock()
        mock_venta.fecha = date(2024, 1, 15)
        mock_venta.total = Decimal('1000.00')

        mock_compra = Mock()
        mock_compra.fecha = date(2024, 1, 15)
        mock_compra.total = Decimal('500.00')

        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[mock_venta])
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(return_value=[mock_compra])

        result = dashboard_service._get_trends(date(2024, 1, 1), date(2024, 1, 31))

        assert "ventas" in result
        assert "compras" in result
        assert len(result["ventas"]) == 1
        assert len(result["compras"]) == 1

    def test_trends_no_data(self, dashboard_service):
        """Test tendencias sin datos."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[])
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(return_value=[])

        result = dashboard_service._get_trends(date(2024, 1, 1), date(2024, 1, 31))

        assert result["ventas"] == []
        assert result["compras"] == []

    def test_trends_null_dates(self, dashboard_service):
        """Test tendencias con fechas nulas."""
        mock_venta = Mock()
        mock_venta.fecha = None
        mock_venta.total = Decimal('1000.00')

        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[mock_venta])
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(return_value=[])

        result = dashboard_service._get_trends(date(2024, 1, 1), date(2024, 1, 31))

        assert result["ventas"] == []

    def test_trends_exception(self, dashboard_service):
        """Test manejo de excepciones en tendencias."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(side_effect=Exception("DB Error"))

        result = dashboard_service._get_trends(date(2024, 1, 1), date(2024, 1, 31))

        assert result["ventas"] == []
        assert result["compras"] == []


class TestTopProducts:
    """Tests para _get_top_products."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_top_products_with_data(self, dashboard_service):
        """Test top productos con datos."""
        mock_result = Mock()
        mock_result.idProducto = 1
        mock_result.total_cantidad = 100
        mock_result.total_ingresos = Decimal('5000.00')

        mock_producto = Mock()
        mock_producto.nombre = "Producto A"
        mock_producto.categoria = Mock(nombre="Categoria 1")

        dashboard_service.db.query.return_value.join.return_value.filter.return_value.group_by.return_value.order_by.return_value.limit.return_value.all.return_value = [mock_result]
        dashboard_service.producto_repo.get_by_id = Mock(return_value=mock_producto)

        result = dashboard_service._get_top_products(date(2024, 1, 1), date(2024, 1, 31))

        assert result["total_productos_vendidos"] == 1
        assert len(result["por_cantidad"]) == 1
        assert result["por_cantidad"][0]["nombre"] == "Producto A"

    def test_top_products_no_data(self, dashboard_service):
        """Test top productos sin datos."""
        dashboard_service.db.query.return_value.join.return_value.filter.return_value.group_by.return_value.order_by.return_value.limit.return_value.all.return_value = []

        result = dashboard_service._get_top_products(date(2024, 1, 1), date(2024, 1, 31))

        assert result["por_cantidad"] == []
        assert result["total_productos_vendidos"] == 0

    def test_top_products_exception(self, dashboard_service):
        """Test manejo de excepciones en top productos."""
        dashboard_service.db.query.side_effect = Exception("DB Error")

        result = dashboard_service._get_top_products(date(2024, 1, 1), date(2024, 1, 31))

        assert result["por_cantidad"] == []
        assert result["total_productos_vendidos"] == 0


class TestKPIDetail:
    """Tests para get_kpi_detail."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_kpi_detail_ventas(self, dashboard_service):
        """Test detalle de KPI ventas."""
        with patch.object(dashboard_service, '_detail_ventas') as mock_detail:
            mock_detail.return_value = {"success": True, "kpi": "ventas"}

            result = dashboard_service.get_kpi_detail("ventas")

            assert result["success"] is True

    def test_kpi_detail_compras(self, dashboard_service):
        """Test detalle de KPI compras."""
        with patch.object(dashboard_service, '_detail_compras') as mock_detail:
            mock_detail.return_value = {"success": True, "kpi": "compras"}

            result = dashboard_service.get_kpi_detail("compras")

            assert result["success"] is True

    def test_kpi_detail_margen(self, dashboard_service):
        """Test detalle de KPI margen."""
        with patch.object(dashboard_service, '_detail_margen') as mock_detail:
            mock_detail.return_value = {"success": True, "kpi": "margen"}

            result = dashboard_service.get_kpi_detail("margen")

            assert result["success"] is True

    def test_kpi_detail_roi(self, dashboard_service):
        """Test detalle de KPI ROI."""
        with patch.object(dashboard_service, '_detail_roi') as mock_detail:
            mock_detail.return_value = {"success": True, "kpi": "roi"}

            result = dashboard_service.get_kpi_detail("roi")

            assert result["success"] is True

    def test_kpi_detail_alertas(self, dashboard_service):
        """Test detalle de KPI alertas."""
        with patch.object(dashboard_service, '_detail_alertas') as mock_detail:
            mock_detail.return_value = {"success": True, "kpi": "alertas"}

            result = dashboard_service.get_kpi_detail("alertas")

            assert result["success"] is True

    def test_kpi_detail_unknown(self, dashboard_service):
        """Test KPI desconocido."""
        result = dashboard_service.get_kpi_detail("desconocido")

        assert result["success"] is False
        assert "error" in result

    def test_kpi_detail_with_dates(self, dashboard_service):
        """Test detalle de KPI con fechas."""
        with patch.object(dashboard_service, '_detail_ventas') as mock_detail:
            mock_detail.return_value = {"success": True}

            result = dashboard_service.get_kpi_detail("ventas", date(2024, 1, 1), date(2024, 1, 31))

            mock_detail.assert_called_once_with(date(2024, 1, 1), date(2024, 1, 31))


class TestDetailVentas:
    """Tests para _detail_ventas."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_detail_ventas_with_data(self, dashboard_service):
        """Test detalle de ventas con datos."""
        mock_venta = Mock()
        mock_venta.fecha = date(2024, 1, 15)
        mock_venta.total = Decimal('1000.00')

        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[mock_venta])

        result = dashboard_service._detail_ventas(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is True
        assert result["kpi"] == "ventas"
        assert result["resumen"]["total"] == 1000.0
        assert result["resumen"]["transacciones"] == 1

    def test_detail_ventas_no_data(self, dashboard_service):
        """Test detalle de ventas sin datos."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[])

        result = dashboard_service._detail_ventas(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is True
        assert result["resumen"]["total"] == 0
        assert result["resumen"]["transacciones"] == 0


class TestDetailCompras:
    """Tests para _detail_compras."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_detail_compras_with_data(self, dashboard_service):
        """Test detalle de compras con datos."""
        mock_compra = Mock()
        mock_compra.fecha = date(2024, 1, 15)
        mock_compra.total = Decimal('500.00')

        dashboard_service.compra_repo.get_by_rango_fechas = Mock(return_value=[mock_compra])

        result = dashboard_service._detail_compras(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is True
        assert result["kpi"] == "compras"
        assert result["resumen"]["total"] == 500.0

    def test_detail_compras_no_data(self, dashboard_service):
        """Test detalle de compras sin datos."""
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(return_value=[])

        result = dashboard_service._detail_compras(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is True
        assert result["resumen"]["total"] == 0


class TestDetailMargen:
    """Tests para _detail_margen."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_detail_margen_with_data(self, dashboard_service):
        """Test detalle de margen con datos."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(
            return_value=[Mock(total=Decimal('10000.00'))]
        )
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(
            return_value=[Mock(total=Decimal('6000.00'))]
        )

        result = dashboard_service._detail_margen(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is True
        assert result["kpi"] == "margen"
        assert result["resumen"]["margen_bruto_porcentaje"] == 40.0

    def test_detail_margen_zero_ingresos(self, dashboard_service):
        """Test margen con ingresos cero."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[])
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(
            return_value=[Mock(total=Decimal('1000.00'))]
        )

        result = dashboard_service._detail_margen(date(2024, 1, 1), date(2024, 1, 31))

        assert result["resumen"]["margen_bruto_porcentaje"] == 0


class TestInterpretarMargen:
    """Tests para _interpretar_margen."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_margen_excelente(self, dashboard_service):
        """Test interpretacion margen excelente."""
        result = dashboard_service._interpretar_margen(45)
        assert "excelente" in result.lower()

    def test_margen_bueno(self, dashboard_service):
        """Test interpretacion margen bueno."""
        result = dashboard_service._interpretar_margen(30)
        assert "bueno" in result.lower()

    def test_margen_aceptable(self, dashboard_service):
        """Test interpretacion margen aceptable."""
        result = dashboard_service._interpretar_margen(18)
        assert "aceptable" in result.lower()

    def test_margen_bajo(self, dashboard_service):
        """Test interpretacion margen bajo."""
        result = dashboard_service._interpretar_margen(8)
        assert "bajo" in result.lower()

    def test_margen_negativo(self, dashboard_service):
        """Test interpretacion margen negativo."""
        result = dashboard_service._interpretar_margen(-10)
        assert "negativo" in result.lower() or "perdidas" in result.lower()


class TestDetailROI:
    """Tests para _detail_roi."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_detail_roi_with_data(self, dashboard_service):
        """Test detalle de ROI con datos."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(
            return_value=[Mock(total=Decimal('15000.00'))]
        )
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(
            return_value=[Mock(total=Decimal('10000.00'))]
        )

        result = dashboard_service._detail_roi(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is True
        assert result["kpi"] == "roi"
        assert result["resumen"]["roi_porcentaje"] == 50.0

    def test_detail_roi_zero_inversion(self, dashboard_service):
        """Test ROI con inversion cero."""
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(
            return_value=[Mock(total=Decimal('5000.00'))]
        )
        dashboard_service.compra_repo.get_by_rango_fechas = Mock(return_value=[])

        result = dashboard_service._detail_roi(date(2024, 1, 1), date(2024, 1, 31))

        assert result["resumen"]["roi_porcentaje"] == 0


class TestInterpretarROI:
    """Tests para _interpretar_roi."""

    @pytest.fixture
    def mock_db(self):
        return Mock()

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_roi_excelente(self, dashboard_service):
        """Test interpretacion ROI excelente."""
        result = dashboard_service._interpretar_roi(60)
        assert "excelente" in result.lower()

    def test_roi_bueno(self, dashboard_service):
        """Test interpretacion ROI bueno."""
        result = dashboard_service._interpretar_roi(30)
        assert "bueno" in result.lower()

    def test_roi_aceptable(self, dashboard_service):
        """Test interpretacion ROI aceptable."""
        result = dashboard_service._interpretar_roi(15)
        assert "aceptable" in result.lower()

    def test_roi_bajo(self, dashboard_service):
        """Test interpretacion ROI bajo."""
        result = dashboard_service._interpretar_roi(5)
        assert "bajo" in result.lower()

    def test_roi_negativo(self, dashboard_service):
        """Test interpretacion ROI negativo."""
        result = dashboard_service._interpretar_roi(-15)
        assert "negativo" in result.lower()


class TestDetailAlertas:
    """Tests para _detail_alertas."""

    @pytest.fixture
    def mock_db(self):
        db = Mock()
        db.query.return_value.filter.return_value.order_by.return_value.all.return_value = []
        return db

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_detail_alertas_with_data(self, dashboard_service):
        """Test detalle de alertas con datos."""
        mock_alerta = Mock()
        mock_alerta.estado = "Activa"
        mock_alerta.tipo = "stock_bajo"
        mock_alerta.importancia = "alta"

        dashboard_service.db.query.return_value.filter.return_value.order_by.return_value.all.return_value = [mock_alerta]

        result = dashboard_service._detail_alertas(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is True
        assert result["kpi"] == "alertas"
        assert result["resumen"]["total"] == 1

    def test_detail_alertas_no_data(self, dashboard_service):
        """Test detalle de alertas sin datos."""
        dashboard_service.db.query.return_value.filter.return_value.order_by.return_value.all.return_value = []

        result = dashboard_service._detail_alertas(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is True
        assert result["resumen"]["total"] == 0


class TestScenarioSummary:
    """Tests para get_scenario_summary."""

    @pytest.fixture
    def mock_db(self):
        db = Mock()
        db.query.return_value.order_by.return_value.limit.return_value.all.return_value = []
        db.query.return_value.count.return_value = 0
        return db

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_scenario_summary_with_data(self, dashboard_service):
        """Test resumen de escenarios con datos."""
        mock_escenario = Mock()
        mock_escenario.idEscenario = 1
        mock_escenario.nombre = "Escenario 1"
        mock_escenario.horizonteMeses = 6
        mock_escenario.creadoEn = datetime(2024, 1, 15)

        dashboard_service.db.query.return_value.order_by.return_value.limit.return_value.all.return_value = [mock_escenario]
        dashboard_service.db.query.return_value.count.return_value = 5

        result = dashboard_service.get_scenario_summary()

        assert result["success"] is True
        assert result["total_escenarios"] == 5
        assert len(result["recientes"]) == 1

    def test_scenario_summary_no_data(self, dashboard_service):
        """Test resumen de escenarios sin datos."""
        dashboard_service.db.query.return_value.order_by.return_value.limit.return_value.all.return_value = []
        dashboard_service.db.query.return_value.count.return_value = 0

        result = dashboard_service.get_scenario_summary()

        assert result["success"] is True
        assert result["recientes"] == []

    def test_scenario_summary_exception(self, dashboard_service):
        """Test manejo de excepciones."""
        dashboard_service.db.query.side_effect = Exception("DB Error")

        result = dashboard_service.get_scenario_summary()

        assert result["success"] is False
        assert "error" in result


class TestRecentPredictions:
    """Tests para get_recent_predictions."""

    @pytest.fixture
    def mock_db(self):
        db = Mock()
        db.query.return_value.order_by.return_value.limit.return_value.all.return_value = []
        return db

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_recent_predictions_with_data(self, dashboard_service):
        """Test predicciones recientes con datos."""
        mock_pred = Mock()
        mock_pred.idPred = 1
        mock_pred.entidad = "producto"
        mock_pred.claveEntidad = "P001"
        mock_pred.periodo = "2024-01"
        mock_pred.valorPredicho = Decimal('1000.00')
        mock_pred.nivelConfianza = Decimal('0.95')

        dashboard_service.db.query.return_value.order_by.return_value.limit.return_value.all.return_value = [mock_pred]

        result = dashboard_service.get_recent_predictions()

        assert result["success"] is True
        assert result["total"] == 1
        assert result["predicciones"][0]["entidad"] == "producto"

    def test_recent_predictions_no_data(self, dashboard_service):
        """Test predicciones recientes sin datos."""
        dashboard_service.db.query.return_value.order_by.return_value.limit.return_value.all.return_value = []

        result = dashboard_service.get_recent_predictions()

        assert result["success"] is True
        assert result["total"] == 0

    def test_recent_predictions_exception(self, dashboard_service):
        """Test manejo de excepciones."""
        dashboard_service.db.query.side_effect = Exception("DB Error")

        result = dashboard_service.get_recent_predictions()

        assert result["success"] is False


class TestUserPreferences:
    """Tests para get_user_preferences."""

    @pytest.fixture
    def mock_db(self):
        db = Mock()
        db.query.return_value.filter.return_value.order_by.return_value.all.return_value = []
        return db

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_user_preferences_with_data(self, dashboard_service):
        """Test preferencias de usuario con datos."""
        mock_pref = Mock()
        mock_pref.idPreferencia = 1
        mock_pref.kpi = "ventas"
        mock_pref.visible = 1
        mock_pref.orden = 1

        dashboard_service.db.query.return_value.filter.return_value.order_by.return_value.all.return_value = [mock_pref]

        result = dashboard_service.get_user_preferences(1)

        assert result["success"] is True
        assert result["id_usuario"] == 1
        assert len(result["preferencias"]) == 1

    def test_user_preferences_no_data(self, dashboard_service):
        """Test preferencias de usuario sin datos."""
        dashboard_service.db.query.return_value.filter.return_value.order_by.return_value.all.return_value = []

        result = dashboard_service.get_user_preferences(1)

        assert result["success"] is True
        assert result["preferencias"] == []

    def test_user_preferences_exception(self, dashboard_service):
        """Test manejo de excepciones."""
        dashboard_service.db.query.side_effect = Exception("DB Error")

        result = dashboard_service.get_user_preferences(1)

        assert result["success"] is False


class TestUpdateUserPreferences:
    """Tests para update_user_preferences."""

    @pytest.fixture
    def mock_db(self):
        db = Mock()
        db.query.return_value.filter.return_value.first.return_value = None
        return db

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_update_preferences_create_new(self, dashboard_service):
        """Test crear nuevas preferencias."""
        dashboard_service.db.query.return_value.filter.return_value.first.return_value = None

        preferencias = [{"kpi": "ventas", "valor": "1"}]
        result = dashboard_service.update_user_preferences(1, preferencias)

        assert result["success"] is True
        assert result["creadas"] == 1
        dashboard_service.db.commit.assert_called_once()

    def test_update_preferences_update_existing(self, dashboard_service):
        """Test actualizar preferencias existentes."""
        mock_existing = Mock()
        mock_existing.visible = 1
        mock_existing.orden = 1

        dashboard_service.db.query.return_value.filter.return_value.first.return_value = mock_existing

        preferencias = [{"kpi": "ventas", "valor": "0"}]
        result = dashboard_service.update_user_preferences(1, preferencias)

        assert result["success"] is True
        assert result["actualizadas"] == 1

    def test_update_preferences_skip_empty_kpi(self, dashboard_service):
        """Test omitir preferencia sin KPI."""
        preferencias = [{"kpi": None, "valor": "1"}, {"valor": "1"}]
        result = dashboard_service.update_user_preferences(1, preferencias)

        assert result["success"] is True
        assert result["creadas"] == 0
        assert result["actualizadas"] == 0

    def test_update_preferences_exception(self, dashboard_service):
        """Test manejo de excepciones."""
        dashboard_service.db.query.side_effect = Exception("DB Error")

        preferencias = [{"kpi": "ventas", "valor": "1"}]
        result = dashboard_service.update_user_preferences(1, preferencias)

        assert result["success"] is False
        dashboard_service.db.rollback.assert_called_once()


class TestCompareActualVsPredicted:
    """Tests para compare_actual_vs_predicted."""

    @pytest.fixture
    def mock_db(self):
        db = Mock()
        db.query.return_value.filter.return_value.all.return_value = []
        return db

    @pytest.fixture
    def dashboard_service(self, mock_db):
        with patch('app.services.dashboard_service.VentaRepository'), \
             patch('app.services.dashboard_service.CompraRepository'), \
             patch('app.services.dashboard_service.ProductoRepository'):
            return DashboardService(mock_db)

    def test_compare_with_data(self, dashboard_service):
        """Test comparacion con datos."""
        mock_pred = Mock()
        mock_pred.valorPredicho = Decimal('1000.00')

        mock_venta = Mock()
        mock_venta.total = Decimal('1050.00')

        dashboard_service.db.query.return_value.filter.return_value.all.return_value = [mock_pred]
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[mock_venta])

        result = dashboard_service.compare_actual_vs_predicted(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is True
        assert result["comparacion"]["valor_real"] == 1050.0
        assert result["comparacion"]["valor_predicho"] == 1000.0

    def test_compare_precision_excelente(self, dashboard_service):
        """Test precision excelente (error <= 5%)."""
        mock_pred = Mock()
        mock_pred.valorPredicho = Decimal('1000.00')

        mock_venta = Mock()
        mock_venta.total = Decimal('1040.00')  # 4% error

        dashboard_service.db.query.return_value.filter.return_value.all.return_value = [mock_pred]
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[mock_venta])

        result = dashboard_service.compare_actual_vs_predicted(date(2024, 1, 1), date(2024, 1, 31))

        assert result["comparacion"]["precision"] == "excelente"

    def test_compare_precision_buena(self, dashboard_service):
        """Test precision buena (error 5-10%)."""
        mock_pred = Mock()
        mock_pred.valorPredicho = Decimal('1000.00')

        mock_venta = Mock()
        mock_venta.total = Decimal('1080.00')  # 8% error

        dashboard_service.db.query.return_value.filter.return_value.all.return_value = [mock_pred]
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[mock_venta])

        result = dashboard_service.compare_actual_vs_predicted(date(2024, 1, 1), date(2024, 1, 31))

        assert result["comparacion"]["precision"] == "buena"

    def test_compare_precision_aceptable(self, dashboard_service):
        """Test precision aceptable (error 10-20%)."""
        mock_pred = Mock()
        mock_pred.valorPredicho = Decimal('1000.00')

        mock_venta = Mock()
        mock_venta.total = Decimal('1150.00')  # 15% error

        dashboard_service.db.query.return_value.filter.return_value.all.return_value = [mock_pred]
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[mock_venta])

        result = dashboard_service.compare_actual_vs_predicted(date(2024, 1, 1), date(2024, 1, 31))

        assert result["comparacion"]["precision"] == "aceptable"

    def test_compare_precision_baja(self, dashboard_service):
        """Test precision baja (error > 20%)."""
        mock_pred = Mock()
        mock_pred.valorPredicho = Decimal('1000.00')

        mock_venta = Mock()
        mock_venta.total = Decimal('1300.00')  # 30% error

        dashboard_service.db.query.return_value.filter.return_value.all.return_value = [mock_pred]
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[mock_venta])

        result = dashboard_service.compare_actual_vs_predicted(date(2024, 1, 1), date(2024, 1, 31))

        assert result["comparacion"]["precision"] == "baja"

    def test_compare_no_predictions(self, dashboard_service):
        """Test sin predicciones."""
        mock_venta = Mock()
        mock_venta.total = Decimal('1000.00')

        dashboard_service.db.query.return_value.filter.return_value.all.return_value = []
        dashboard_service.venta_repo.get_by_rango_fechas = Mock(return_value=[mock_venta])

        result = dashboard_service.compare_actual_vs_predicted(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is True
        assert result["comparacion"]["porcentaje_error"] == 0

    def test_compare_exception(self, dashboard_service):
        """Test manejo de excepciones."""
        dashboard_service.db.query.side_effect = Exception("DB Error")

        result = dashboard_service.compare_actual_vs_predicted(date(2024, 1, 1), date(2024, 1, 31))

        assert result["success"] is False
        assert "error" in result
