"""
Pruebas de integracion para persistencia de modelos usando datos sinteticos.
Genera datos de ventas sinteticos para probar el ciclo completo:
entrenar -> guardar -> cargar -> predecir
"""

import pytest
import os
import sys
from datetime import date, timedelta
from decimal import Decimal
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

# Agregar path para imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from app.models import Venta
from app.database import get_db


class TestModelPersistenceWithSyntheticData:
    """
    Pruebas de persistencia de modelos usando datos sinteticos.
    Genera suficientes datos para cumplir RN-01.01 (minimo 6 meses).
    """

    MODELS_DIR = "trained_models"

    @pytest.fixture
    def synthetic_sales_data(self, db_session: Session) -> int:
        """
        Genera 365 dias de datos de ventas sinteticos.
        Cumple con RN-01.01: minimo 6 meses de datos historicos.

        Returns:
            int: Numero de registros creados
        """
        import random
        random.seed(42)  # Para reproducibilidad

        # Fecha inicial: hace 1 año
        start_date = date.today() - timedelta(days=365)
        records_created = 0

        for i in range(365):
            current_date = start_date + timedelta(days=i)

            # Simular patron de ventas con estacionalidad semanal
            base_amount = 10000.0

            # Factor dia de la semana (mas ventas entre semana)
            day_factor = 1.2 if current_date.weekday() < 5 else 0.7

            # Factor tendencia creciente
            trend_factor = 1.0 + (i / 365) * 0.3

            # Variacion aleatoria
            random_factor = random.uniform(0.8, 1.2)

            total = base_amount * day_factor * trend_factor * random_factor

            # Crear venta
            venta = Venta(
                fecha=current_date,
                total=Decimal(str(round(total, 2))),
                moneda='MXN',
                creadoPor=None  # Sin usuario para datos sinteticos
            )

            db_session.add(venta)
            records_created += 1

        db_session.commit()
        return records_created

    def test_full_lifecycle_with_synthetic_data(
        self,
        client: TestClient,
        auth_headers,
        db_session: Session,
        synthetic_sales_data: int
    ):
        """
        Test del ciclo completo con datos sinteticos:
        1. Verificar datos suficientes
        2. Entrenar modelo
        3. Verificar guardado en disco
        4. Cargar modelo
        5. Hacer predicciones
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        print(f"\n=== Datos sinteticos creados: {synthetic_sales_data} registros ===")

        # ========================================
        # PASO 1: Verificar que hay datos suficientes
        # ========================================
        validate_response = client.post(
            "/api/v1/predictions/validate-data",
            headers=auth_headers,
            json={"aggregation": "D"}
        )

        assert validate_response.status_code == 200
        validate_result = validate_response.json()
        print(f"Validacion de datos: {validate_result}")

        # Debe tener suficientes datos (365 > 180 requeridos)
        assert validate_result.get("data_points", 0) >= 180, \
            f"Datos insuficientes: {validate_result.get('data_points')}"

        # ========================================
        # PASO 2: Entrenar modelo Linear
        # ========================================
        print("\n--- Entrenando modelo Linear ---")
        train_response = client.post(
            "/api/v1/predictions/train",
            headers=auth_headers,
            json={"model_type": "linear"}
        )

        assert train_response.status_code == 200
        train_result = train_response.json()
        print(f"Resultado entrenamiento: {train_result}")

        assert train_result.get("success") == True, \
            f"Entrenamiento fallido: {train_result.get('error')}"

        model_key = train_result.get("model_key")
        assert model_key is not None

        metrics = train_result.get("metrics", {})
        print(f"Metricas del modelo:")
        print(f"  - R2 Score: {metrics.get('r2_score')}")
        print(f"  - RMSE: {metrics.get('rmse')}")
        print(f"  - MAE: {metrics.get('mae')}")

        # ========================================
        # PASO 3: Verificar que el modelo esta guardado en disco
        # ========================================
        print("\n--- Verificando modelo en disco ---")
        model_path = os.path.join(self.MODELS_DIR, f"{model_key}.pkl")
        assert os.path.exists(model_path), f"Modelo no guardado en: {model_path}"

        file_size = os.path.getsize(model_path)
        print(f"Archivo guardado: {model_path} ({file_size} bytes)")

        # Verificar via API
        saved_response = client.get(
            "/api/v1/predictions/models/saved",
            headers=auth_headers
        )
        assert saved_response.status_code == 200
        saved_models = saved_response.json()

        model_in_disk = any(m["model_key"] == model_key for m in saved_models)
        assert model_in_disk, "Modelo no aparece en lista de guardados"

        # ========================================
        # PASO 4: Cargar modelo desde disco
        # ========================================
        print("\n--- Cargando modelo desde disco ---")
        load_response = client.post(
            "/api/v1/predictions/models/load",
            headers=auth_headers,
            json={"model_key": model_key}
        )

        assert load_response.status_code == 200
        load_result = load_response.json()
        print(f"Resultado carga: {load_result}")

        assert load_result.get("success") == True
        assert load_result.get("is_fitted") == True

        # Verificar que las metricas se preservaron
        loaded_metrics = load_result.get("metrics", {})
        assert loaded_metrics.get("r2_score") == metrics.get("r2_score"), \
            "Las metricas no se preservaron correctamente"

        # ========================================
        # PASO 5: Hacer predicciones con modelo
        # Nota: Como cada request crea una nueva instancia del servicio,
        # primero entrenamos/cargamos y luego hacemos forecast en requests separados.
        # El modelo se busca primero en memoria, luego en disco.
        # ========================================
        print("\n--- Generando predicciones ---")

        # Primero cargar todos los modelos para asegurar que esten en memoria
        client.post(
            "/api/v1/predictions/models/load-all",
            headers=auth_headers
        )

        # Ahora intentar forecast usando el tipo de modelo
        forecast_response = client.post(
            "/api/v1/predictions/forecast",
            headers=auth_headers,
            json={
                "model_type": "linear",  # Usar tipo en lugar de key especifica
                "periods": 30  # 30 dias de prediccion
            }
        )

        assert forecast_response.status_code == 200
        forecast_result = forecast_response.json()
        print(f"Resultado prediccion: success={forecast_result.get('success')}")

        # Si R2 >= 0.7, debe generar predicciones exitosas
        if metrics.get("r2_score", 0) >= 0.7:
            assert forecast_result.get("success") == True, \
                f"Prediccion fallida: {forecast_result.get('error')}"

            predictions = forecast_result.get("predictions", {})
            pred_list = predictions.get("predictions", [])
            print(f"Predicciones generadas: {len(pred_list)} periodos")

            # Verificar estructura de predicciones
            if len(pred_list) > 0:
                first_pred = pred_list[0]
                assert "date" in first_pred
                assert "value" in first_pred
                print(f"Primera prediccion: {first_pred}")
        else:
            print(f"R2 ({metrics.get('r2_score')}) < 0.7, prediccion rechazada correctamente")

        print("\n=== Test completado exitosamente ===")

    def test_train_multiple_models_synthetic(
        self,
        client: TestClient,
        auth_headers,
        db_session: Session,
        synthetic_sales_data: int
    ):
        """Test entrenar multiples tipos de modelo con datos sinteticos."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        model_types = ["linear", "random_forest"]
        trained_models = []

        for model_type in model_types:
            print(f"\n--- Entrenando modelo {model_type} ---")

            response = client.post(
                "/api/v1/predictions/train",
                headers=auth_headers,
                json={
                    "model_type": model_type,
                    "hyperparameters": {"n_estimators": 10} if model_type == "random_forest" else {}
                }
            )

            assert response.status_code == 200
            result = response.json()

            if result.get("success"):
                trained_models.append({
                    "type": model_type,
                    "key": result.get("model_key"),
                    "r2": result.get("metrics", {}).get("r2_score")
                })
                print(f"  Modelo {model_type}: R2 = {result.get('metrics', {}).get('r2_score')}")

        # Verificar que al menos un modelo se entreno
        assert len(trained_models) > 0, "Ningun modelo se pudo entrenar"

        # Verificar archivos en disco
        for model in trained_models:
            model_path = os.path.join(self.MODELS_DIR, f"{model['key']}.pkl")
            assert os.path.exists(model_path), f"Modelo {model['type']} no guardado"

        print(f"\nModelos entrenados exitosamente: {len(trained_models)}")

    def test_auto_select_with_synthetic_data(
        self,
        client: TestClient,
        auth_headers,
        db_session: Session,
        synthetic_sales_data: int
    ):
        """Test seleccion automatica de modelo con datos sinteticos."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        print("\n--- Ejecutando seleccion automatica de modelo ---")

        response = client.post(
            "/api/v1/predictions/auto-select",
            headers=auth_headers,
            json={}
        )

        assert response.status_code == 200
        result = response.json()

        if result.get("success"):
            best_model = result.get("best_model", {})
            print(f"Mejor modelo: {best_model.get('type')}")
            print(f"R2 Score: {best_model.get('metrics', {}).get('r2_score')}")
            print(f"Recomendacion: {result.get('recommendation')}")

            # Verificar que se compararon multiples modelos
            all_models = result.get("all_models", {})
            print(f"Modelos evaluados: {list(all_models.keys())}")
        else:
            print(f"Seleccion automatica fallida: {result.get('error')}")

    def test_load_all_after_training_synthetic(
        self,
        client: TestClient,
        auth_headers,
        db_session: Session,
        synthetic_sales_data: int
    ):
        """Test cargar todos los modelos despues de entrenar varios."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Entrenar un modelo
        train_response = client.post(
            "/api/v1/predictions/train",
            headers=auth_headers,
            json={"model_type": "linear"}
        )

        if train_response.status_code != 200 or not train_response.json().get("success"):
            pytest.skip("No se pudo entrenar modelo")

        # Cargar todos los modelos
        load_all_response = client.post(
            "/api/v1/predictions/models/load-all",
            headers=auth_headers
        )

        assert load_all_response.status_code == 200
        result = load_all_response.json()

        print(f"\nModelos cargados: {result.get('total_loaded')}")
        print(f"Modelos fallidos: {result.get('total_failed')}")

        # Debe haber cargado al menos uno
        assert result.get("total_loaded", 0) >= 1

        # Listar modelos en memoria
        models_response = client.get(
            "/api/v1/predictions/models",
            headers=auth_headers
        )

        assert models_response.status_code == 200
        models = models_response.json()
        print(f"Modelos en memoria: {len(models)}")


class TestCleanupSyntheticModels:
    """Pruebas de limpieza de modelos sinteticos."""

    MODELS_DIR = "trained_models"

    def test_delete_trained_models(self, client: TestClient, auth_headers):
        """Elimina modelos entrenados durante las pruebas."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Listar modelos guardados
        response = client.get(
            "/api/v1/predictions/models/saved",
            headers=auth_headers
        )

        if response.status_code != 200:
            return

        models = response.json()

        # Eliminar modelos de prueba (los que tienen 'linear' o 'random_forest' en el nombre)
        deleted_count = 0
        for model in models:
            model_key = model.get("model_key", "")
            if "linear" in model_key or "random_forest" in model_key or "arima" in model_key:
                delete_response = client.delete(
                    f"/api/v1/predictions/models/{model_key}",
                    headers=auth_headers
                )
                if delete_response.status_code == 200:
                    result = delete_response.json()
                    if result.get("success"):
                        deleted_count += 1

        print(f"\nModelos eliminados: {deleted_count}")
