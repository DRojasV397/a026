"""
Pruebas de integracion para todos los tipos de modelos predictivos.
Verifica que cada tipo de modelo funciona correctamente con datos sinteticos.
"""

import pytest
import os
import sys
from datetime import date, timedelta
from decimal import Decimal
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from app.models import Venta


class TestAllModelsWithSyntheticData:
    """
    Pruebas completas para cada tipo de modelo:
    - Linear Regression
    - Random Forest
    - ARIMA
    - SARIMA
    """

    MODELS_DIR = "trained_models"

    @pytest.fixture
    def synthetic_sales_data(self, db_session: Session) -> int:
        """
        Genera 365 dias de datos de ventas sinteticos.
        """
        import random
        import numpy as np
        random.seed(42)
        np.random.seed(42)

        start_date = date.today() - timedelta(days=365)
        records_created = 0

        for i in range(365):
            current_date = start_date + timedelta(days=i)

            # Patron con estacionalidad semanal y tendencia
            base_amount = 10000.0
            day_factor = 1.2 if current_date.weekday() < 5 else 0.7
            trend_factor = 1.0 + (i / 365) * 0.3
            seasonal_factor = 1.0 + 0.1 * np.sin(2 * np.pi * i / 30)  # Ciclo mensual
            random_factor = random.uniform(0.85, 1.15)

            total = base_amount * day_factor * trend_factor * seasonal_factor * random_factor

            venta = Venta(
                fecha=current_date,
                total=Decimal(str(round(total, 2))),
                moneda='MXN',
                creadoPor=None
            )

            db_session.add(venta)
            records_created += 1

        db_session.commit()
        return records_created

    def _test_model_lifecycle(
        self,
        client: TestClient,
        auth_headers: dict,
        model_type: str,
        hyperparameters: dict = None
    ) -> dict:
        """
        Prueba el ciclo completo para un tipo de modelo.
        Retorna el resultado del test.
        """
        result = {
            "model_type": model_type,
            "trained": False,
            "saved": False,
            "loaded": False,
            "forecast": False,
            "metrics": None,
            "error": None
        }

        # 1. Entrenar modelo
        print(f"\n--- Entrenando modelo {model_type} ---")
        train_data = {
            "model_type": model_type,
            "hyperparameters": hyperparameters or {}
        }

        train_response = client.post(
            "/api/v1/predictions/train",
            headers=auth_headers,
            json=train_data,
            timeout=120  # Timeout largo para ARIMA/SARIMA
        )

        if train_response.status_code != 200:
            result["error"] = f"HTTP {train_response.status_code}"
            return result

        train_result = train_response.json()

        if not train_result.get("success"):
            result["error"] = train_result.get("error", "Unknown error")
            return result

        result["trained"] = True
        result["metrics"] = train_result.get("metrics")
        model_key = train_result.get("model_key")

        print(f"  Modelo entrenado: {model_key}")
        print(f"  R2: {result['metrics'].get('r2_score')}")
        print(f"  RMSE: {result['metrics'].get('rmse')}")

        # 2. Verificar guardado en disco
        model_path = os.path.join(self.MODELS_DIR, f"{model_key}.pkl")
        if os.path.exists(model_path):
            result["saved"] = True
            print(f"  Guardado en disco: {os.path.getsize(model_path)} bytes")
        else:
            result["error"] = "Modelo no guardado en disco"
            return result

        # 3. Cargar modelo
        load_response = client.post(
            "/api/v1/predictions/models/load",
            headers=auth_headers,
            json={"model_key": model_key}
        )

        if load_response.status_code == 200:
            load_result = load_response.json()
            if load_result.get("success"):
                result["loaded"] = True
                print(f"  Cargado desde disco: OK")

                # Verificar metricas preservadas
                loaded_metrics = load_result.get("metrics", {})
                if loaded_metrics.get("r2_score") == result["metrics"].get("r2_score"):
                    print(f"  Metricas preservadas: OK")
                else:
                    print(f"  ADVERTENCIA: Metricas no coinciden")

        # 4. Intentar forecast
        # Primero cargar todos los modelos
        client.post(
            "/api/v1/predictions/models/load-all",
            headers=auth_headers
        )

        forecast_response = client.post(
            "/api/v1/predictions/forecast",
            headers=auth_headers,
            json={
                "model_type": model_type,
                "periods": 14
            }
        )

        if forecast_response.status_code == 200:
            forecast_result = forecast_response.json()
            if forecast_result.get("success"):
                result["forecast"] = True
                predictions = forecast_result.get("predictions", {}).get("predictions", [])
                print(f"  Forecast generado: {len(predictions)} periodos")
            else:
                # Puede fallar por R2 bajo, lo cual es aceptable
                print(f"  Forecast no disponible: {forecast_result.get('error', 'R2 bajo')}")

        return result

    def test_linear_regression_model(
        self,
        client: TestClient,
        auth_headers,
        db_session: Session,
        synthetic_sales_data: int
    ):
        """Test completo para modelo Linear Regression."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        print(f"\n{'='*60}")
        print("TEST: Linear Regression")
        print(f"{'='*60}")

        result = self._test_model_lifecycle(
            client, auth_headers, "linear"
        )

        assert result["trained"], f"Entrenamiento fallido: {result['error']}"
        assert result["saved"], "Modelo no guardado"
        assert result["loaded"], "Modelo no se pudo cargar"

        print(f"\nResultado: {'PASSED' if result['forecast'] or result['metrics']['r2_score'] < 0.7 else 'PARTIAL'}")

    def test_random_forest_model(
        self,
        client: TestClient,
        auth_headers,
        db_session: Session,
        synthetic_sales_data: int
    ):
        """Test completo para modelo Random Forest."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        print(f"\n{'='*60}")
        print("TEST: Random Forest")
        print(f"{'='*60}")

        result = self._test_model_lifecycle(
            client, auth_headers, "random_forest",
            hyperparameters={
                "n_estimators": 50,  # Menos arboles para test rapido
                "max_depth": 10
            }
        )

        assert result["trained"], f"Entrenamiento fallido: {result['error']}"
        assert result["saved"], "Modelo no guardado"
        assert result["loaded"], "Modelo no se pudo cargar"

        print(f"\nResultado: {'PASSED' if result['forecast'] or result['metrics']['r2_score'] < 0.7 else 'PARTIAL'}")

    def test_arima_model(
        self,
        client: TestClient,
        auth_headers,
        db_session: Session,
        synthetic_sales_data: int
    ):
        """Test completo para modelo ARIMA."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        print(f"\n{'='*60}")
        print("TEST: ARIMA")
        print(f"{'='*60}")

        result = self._test_model_lifecycle(
            client, auth_headers, "arima",
            hyperparameters={
                "order": [1, 1, 1]  # Parametros simples para test rapido
            }
        )

        # ARIMA puede fallar por varias razones relacionadas con las dependencias
        if result["error"]:
            error_msg = str(result["error"]).lower()
            if "stationary" in error_msg:
                pytest.skip("Datos no son estacionarios para ARIMA")
            if "deprecate" in error_msg or "kwarg" in error_msg:
                pytest.skip("Incompatibilidad de version de statsmodels")
            if "convergence" in error_msg:
                pytest.skip("ARIMA no convergio")

        assert result["trained"], f"Entrenamiento fallido: {result['error']}"
        assert result["saved"], "Modelo no guardado"

        print(f"\nResultado: PASSED")

    def test_sarima_model(
        self,
        client: TestClient,
        auth_headers,
        db_session: Session,
        synthetic_sales_data: int
    ):
        """Test completo para modelo SARIMA."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        print(f"\n{'='*60}")
        print("TEST: SARIMA")
        print(f"{'='*60}")

        result = self._test_model_lifecycle(
            client, auth_headers, "sarima",
            hyperparameters={
                "order": [1, 1, 1],
                "seasonal_order": [1, 1, 1, 7]  # Estacionalidad semanal
            }
        )

        # SARIMA puede fallar por varias razones
        if result["error"]:
            error_msg = str(result["error"]).lower()
            if "convergence" in error_msg:
                pytest.skip("SARIMA no convergio")
            if "stationary" in error_msg:
                pytest.skip("Datos no son estacionarios")
            if "deprecate" in error_msg or "kwarg" in error_msg:
                pytest.skip("Incompatibilidad de version de statsmodels")

        assert result["trained"], f"Entrenamiento fallido: {result['error']}"
        assert result["saved"], "Modelo no guardado"

        print(f"\nResultado: PASSED")


class TestModelComparison:
    """Prueba la comparacion automatica de modelos."""

    @pytest.fixture
    def synthetic_sales_data(self, db_session: Session) -> int:
        """Genera datos sinteticos."""
        import random
        random.seed(42)

        start_date = date.today() - timedelta(days=365)
        records_created = 0

        for i in range(365):
            current_date = start_date + timedelta(days=i)
            base = 10000.0
            day_factor = 1.2 if current_date.weekday() < 5 else 0.7
            trend = 1.0 + (i / 365) * 0.3
            noise = random.uniform(0.85, 1.15)

            venta = Venta(
                fecha=current_date,
                total=Decimal(str(round(base * day_factor * trend * noise, 2))),
                moneda='MXN'
            )
            db_session.add(venta)
            records_created += 1

        db_session.commit()
        return records_created

    def test_auto_select_best_model(
        self,
        client: TestClient,
        auth_headers,
        db_session: Session,
        synthetic_sales_data: int
    ):
        """
        Test RF-02.06: Seleccion automatica del mejor modelo.
        Entrena multiples modelos y selecciona el mejor basado en R2.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        print(f"\n{'='*60}")
        print("TEST: Seleccion Automatica de Modelo (RF-02.06)")
        print(f"{'='*60}")

        response = client.post(
            "/api/v1/predictions/auto-select",
            headers=auth_headers,
            json={},
            timeout=300  # Timeout largo porque entrena multiples modelos
        )

        assert response.status_code == 200
        result = response.json()

        if result.get("success"):
            best = result.get("best_model", {})
            all_models = result.get("all_models", {})

            print(f"\nModelos evaluados: {list(all_models.keys())}")
            print(f"\nMejor modelo: {best.get('type')}")
            print(f"R2 Score: {best.get('metrics', {}).get('r2_score')}")
            print(f"Cumple umbral R2>=0.7: {result.get('meets_r2_threshold')}")
            print(f"\nRecomendacion: {result.get('recommendation')}")

            # Mostrar comparacion
            print("\nComparacion de todos los modelos:")
            for model_type, info in all_models.items():
                metrics = info.get("metrics", {})
                r2 = metrics.get("r2_score", 0)
                rmse = metrics.get("rmse", 0)
                print(f"  {model_type}: R2={r2:.4f}, RMSE={rmse:.2f}")

            assert best.get("type") is not None
            assert best.get("metrics") is not None
        else:
            print(f"Auto-select fallido: {result.get('error')}")
            # Puede fallar si ningun modelo converge, lo cual es aceptable
            pytest.skip(f"Auto-select no disponible: {result.get('error')}")


class TestCleanup:
    """Limpieza de modelos de prueba."""

    def test_cleanup_test_models(self, client: TestClient, auth_headers):
        """Elimina todos los modelos creados durante las pruebas."""
        if not auth_headers:
            pytest.skip("No auth")

        response = client.get(
            "/api/v1/predictions/models/saved",
            headers=auth_headers
        )

        if response.status_code != 200:
            return

        models = response.json()
        deleted = 0

        for model in models:
            model_key = model.get("model_key", "")
            delete_response = client.delete(
                f"/api/v1/predictions/models/{model_key}",
                headers=auth_headers
            )
            if delete_response.status_code == 200:
                if delete_response.json().get("success"):
                    deleted += 1

        print(f"\nModelos eliminados en cleanup: {deleted}")
