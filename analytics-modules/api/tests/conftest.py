"""
Configuracion de pytest y fixtures compartidas.
"""

import os
import sys
import urllib
from datetime import date, datetime, timedelta
from decimal import Decimal
from typing import Generator, Dict, Any

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.pool import StaticPool

# Agregar el directorio raiz al path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.database import Base, get_db, db_manager
from app.config import settings
from main import app


# ============================================================
# Configuracion de Base de Datos de Prueba
# ============================================================

# Determinar si usar BD real o SQLite en memoria
USE_REAL_DB = os.environ.get("USE_REAL_DB", "true").lower() == "true"

if USE_REAL_DB:
    # Usar la BD SQL Server real (TTA026 con autenticacion Windows)
    # Reutilizar la configuracion existente
    test_engine = db_manager.engine
    TestingSessionLocal = db_manager.session_factory
else:
    # Usar SQLite en memoria para pruebas rapidas/unitarias
    SQLALCHEMY_TEST_DATABASE_URL = "sqlite:///:memory:"
    test_engine = create_engine(
        SQLALCHEMY_TEST_DATABASE_URL,
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)


def override_get_db():
    """Override de la dependencia get_db para pruebas."""
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


# ============================================================
# Fixtures de Base de Datos
# ============================================================

@pytest.fixture(scope="function")
def db_session() -> Generator[Session, None, None]:
    """
    Crea una sesion de BD limpia para cada test.
    """
    if USE_REAL_DB:
        # Usar la BD real sin crear/eliminar tablas
        session = TestingSessionLocal()
        try:
            yield session
        finally:
            session.rollback()  # Revertir cambios del test
            session.close()
    else:
        # SQLite: crear tablas temporales
        Base.metadata.create_all(bind=test_engine)
        session = TestingSessionLocal()
        try:
            yield session
        finally:
            session.close()
            Base.metadata.drop_all(bind=test_engine)


@pytest.fixture(scope="function")
def client(db_session: Session) -> Generator[TestClient, None, None]:
    """
    Cliente de prueba con BD override.
    """
    if USE_REAL_DB:
        # Usar el get_db real sin override
        with TestClient(app) as test_client:
            yield test_client
    else:
        # Override para SQLite
        app.dependency_overrides[get_db] = lambda: db_session
        with TestClient(app) as test_client:
            yield test_client
        app.dependency_overrides.clear()


# ============================================================
# Fixtures de Datos de Prueba
# ============================================================

@pytest.fixture
def sample_user_data() -> Dict[str, Any]:
    """Datos de usuario de prueba."""
    return {
        "nombreCompleto": "Usuario Test",
        "nombreUsuario": "testuser",
        "email": "test@example.com",
        "password": "Test123456!"
    }


@pytest.fixture
def sample_product_data() -> Dict[str, Any]:
    """Datos de producto de prueba."""
    return {
        "nombre": "Producto Test",
        "descripcion": "Descripcion del producto de prueba",
        "sku": "TEST-001",
        "precioUnitario": 100.00,
        "costo": 50.00,
        "stockActual": 100,
        "stockMinimo": 10,
        "idCategoria": 1
    }


@pytest.fixture
def sample_category_data() -> Dict[str, Any]:
    """Datos de categoria de prueba."""
    return {
        "nombre": "Categoria Test",
        "descripcion": "Descripcion de categoria de prueba"
    }


@pytest.fixture
def sample_sale_data() -> Dict[str, Any]:
    """Datos de venta de prueba."""
    return {
        "fecha": date.today().isoformat(),
        "total": 1500.00,
        "moneda": "MXN",
        "detalles": [
            {
                "idProducto": 1,
                "cantidad": 10,
                "precioUnitario": 150.00
            }
        ]
    }


@pytest.fixture
def sample_purchase_data() -> Dict[str, Any]:
    """Datos de compra de prueba."""
    return {
        "fecha": date.today().isoformat(),
        "proveedor": "Proveedor Test",
        "total": 5000.00,
        "moneda": "MXN",
        "detalles": [
            {
                "idProducto": 1,
                "cantidad": 100,
                "costoUnitario": 50.00
            }
        ]
    }


@pytest.fixture
def sample_scenario_data() -> Dict[str, Any]:
    """Datos de escenario de prueba."""
    return {
        "nombre": "Escenario Test",
        "descripcion": "Escenario de prueba para simulacion",
        "horizonte_meses": 6
    }


@pytest.fixture
def sample_alert_config() -> Dict[str, Any]:
    """Configuracion de alertas de prueba."""
    return {
        "change_threshold": 15.0,
        "opportunity_threshold": 20.0,
        "anomaly_rate_threshold": 5.0
    }


# ============================================================
# Fixtures de Autenticacion
# ============================================================

# Credenciales para pruebas con BD real
# Usuario creado especificamente para pruebas en TTA026
TEST_USER_CREDENTIALS = {
    "username": os.environ.get("TEST_USERNAME", "testintegration"),
    "password": os.environ.get("TEST_PASSWORD", "Test123456!")
}


@pytest.fixture
def auth_headers(client: TestClient, sample_user_data: Dict) -> Dict[str, str]:
    """
    Registra un usuario y retorna headers de autenticacion.
    """
    if USE_REAL_DB:
        # Con BD real, intentar login con usuario existente
        response = client.post(
            "/api/v1/auth/login",
            data={
                "username": TEST_USER_CREDENTIALS["username"],
                "password": TEST_USER_CREDENTIALS["password"]
            }
        )

        if response.status_code == 200:
            token = response.json().get("access_token")
            return {"Authorization": f"Bearer {token}"}

        # Si no funciona, intentar registrar y login
        register_data = {
            "nombreCompleto": "Test User Integration",
            "nombreUsuario": "test_integration",
            "email": "test_integration@test.com",
            "password": "TestIntegration123!"
        }
        client.post("/api/v1/auth/register", json=register_data)

        response = client.post(
            "/api/v1/auth/login",
            data={
                "username": "test_integration",
                "password": "TestIntegration123!"
            }
        )

        if response.status_code == 200:
            token = response.json().get("access_token")
            return {"Authorization": f"Bearer {token}"}

        return {}
    else:
        # Con SQLite, registrar usuario nuevo
        client.post("/api/v1/auth/register", json=sample_user_data)

        response = client.post(
            "/api/v1/auth/login",
            data={
                "username": sample_user_data["nombreUsuario"],
                "password": sample_user_data["password"]
            }
        )

        if response.status_code == 200:
            token = response.json().get("access_token")
            return {"Authorization": f"Bearer {token}"}

        return {}


@pytest.fixture
def admin_headers(client: TestClient) -> Dict[str, str]:
    """
    Headers de autenticacion para usuario admin.
    """
    if USE_REAL_DB:
        # Usar las mismas credenciales de test
        response = client.post(
            "/api/v1/auth/login",
            data={
                "username": TEST_USER_CREDENTIALS["username"],
                "password": TEST_USER_CREDENTIALS["password"]
            }
        )

        if response.status_code == 200:
            token = response.json().get("access_token")
            return {"Authorization": f"Bearer {token}"}

        return {}
    else:
        admin_data = {
            "nombreCompleto": "Admin User",
            "nombreUsuario": "admin",
            "email": "admin@example.com",
            "password": "Admin123456!"
        }

        client.post("/api/v1/auth/register", json=admin_data)

        response = client.post(
            "/api/v1/auth/login",
            data={
                "username": admin_data["nombreUsuario"],
                "password": admin_data["password"]
            }
        )

        if response.status_code == 200:
            token = response.json().get("access_token")
            return {"Authorization": f"Bearer {token}"}

        return {}


# ============================================================
# Fixtures de Datos Historicos (para pruebas de ML)
# ============================================================

@pytest.fixture
def historical_sales_data() -> list:
    """
    Genera datos historicos de ventas para pruebas de ML.
    Minimo 6 meses de datos segun RN-01.01.
    """
    data = []
    base_date = date.today() - timedelta(days=365)

    for i in range(365):
        current_date = base_date + timedelta(days=i)
        # Simular estacionalidad semanal
        day_factor = 1.0 + (0.2 if current_date.weekday() < 5 else -0.3)
        # Simular tendencia creciente
        trend_factor = 1.0 + (i / 365) * 0.2
        # Valor base con variacion
        base_value = 10000 * day_factor * trend_factor

        data.append({
            "fecha": current_date.isoformat(),
            "total": round(base_value + (i % 7) * 500, 2)
        })

    return data


@pytest.fixture
def time_series_data() -> list:
    """
    Datos de serie temporal para pruebas de modelos predictivos.
    """
    import numpy as np

    np.random.seed(42)
    n_points = 100

    # Generar serie con tendencia y estacionalidad
    trend = np.linspace(100, 200, n_points)
    seasonality = 20 * np.sin(np.linspace(0, 4 * np.pi, n_points))
    noise = np.random.normal(0, 10, n_points)

    values = trend + seasonality + noise

    return [
        {"periodo": i, "valor": round(float(v), 2)}
        for i, v in enumerate(values)
    ]


# ============================================================
# Utilidades de Prueba
# ============================================================

def create_test_user(db: Session, **kwargs) -> Any:
    """Crea un usuario de prueba en la BD."""
    from app.models import Usuario
    from passlib.context import CryptContext

    pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

    defaults = {
        "nombreCompleto": "Test User",
        "nombreUsuario": "testuser",
        "email": "test@test.com",
        "hashPassword": pwd_context.hash("Test123!"),
        "estado": "Activo"
    }
    defaults.update(kwargs)

    user = Usuario(**defaults)
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def create_test_category(db: Session, **kwargs) -> Any:
    """Crea una categoria de prueba."""
    from app.models import Categoria

    defaults = {
        "nombre": "Test Category",
        "descripcion": "Test description"
    }
    defaults.update(kwargs)

    category = Categoria(**defaults)
    db.add(category)
    db.commit()
    db.refresh(category)
    return category


def create_test_product(db: Session, categoria_id: int = None, **kwargs) -> Any:
    """Crea un producto de prueba."""
    from app.models import Producto

    defaults = {
        "nombre": "Test Product",
        "sku": "TEST-SKU-001",
        "precioUnitario": Decimal("100.00"),
        "costo": Decimal("50.00"),
        "stockActual": 100,
        "idCategoria": categoria_id
    }
    defaults.update(kwargs)

    product = Producto(**defaults)
    db.add(product)
    db.commit()
    db.refresh(product)
    return product


def create_test_sale(db: Session, user_id: int = None, **kwargs) -> Any:
    """Crea una venta de prueba."""
    from app.models import Venta

    defaults = {
        "fecha": date.today(),
        "total": Decimal("1000.00"),
        "moneda": "MXN",
        "creadoPor": user_id
    }
    defaults.update(kwargs)

    sale = Venta(**defaults)
    db.add(sale)
    db.commit()
    db.refresh(sale)
    return sale
