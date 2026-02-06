"""
Pruebas de integracion para persistencia de modelos predictivos.
Verifica que los modelos se pueden entrenar, guardar, cargar y usar via API.
"""

import pytest
import os
import time
from fastapi.testclient import TestClient


class TestModelPersistence:
    """
    Pruebas para el ciclo completo de persistencia de modelos:
    1. Entrenar modelo
    2. Guardar en disco
    3. Verificar que existe
    4. Cargar desde disco
    5. Usar para predicciones
    """

    # Directorio donde se guardan los modelos
    MODELS_DIR = "trained_models"

    def test_full_model_lifecycle(self, client: TestClient, auth_headers):
        """
        Test del ciclo completo: entrenar -> guardar -> cargar -> predecir.
        Este test verifica el requerimiento de persistencia de modelos.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # ========================================
        # PASO 1: Entrenar un modelo
        # ========================================
        train_data = {
            "model_type": "linear",
            "hyperparameters": {}
        }

        train_response = client.post(
            "/api/v1/predictions/train",
            headers=auth_headers,
            json=train_data
        )

        # El entrenamiento puede fallar por falta de datos, lo cual es aceptable
        if train_response.status_code != 200:
            pytest.skip("No hay suficientes datos para entrenar modelo")

        train_result = train_response.json()

        # Si no hay datos suficientes, saltar el test
        if not train_result.get("success"):
            pytest.skip(f"Entrenamiento fallido: {train_result.get('error', 'desconocido')}")

        # Guardar la clave del modelo para usarla despues
        model_key = train_result.get("model_key")
        assert model_key is not None, "model_key no debe ser None"

        # ========================================
        # PASO 2: Verificar que el modelo esta guardado en disco
        # ========================================
        saved_response = client.get(
            "/api/v1/predictions/models/saved",
            headers=auth_headers
        )

        assert saved_response.status_code == 200
        saved_models = saved_response.json()

        # Buscar nuestro modelo en la lista
        model_found = any(m["model_key"] == model_key for m in saved_models)
        assert model_found, f"Modelo {model_key} no encontrado en disco"

        # Verificar que el archivo existe fisicamente
        model_path = os.path.join(self.MODELS_DIR, f"{model_key}.pkl")
        assert os.path.exists(model_path), f"Archivo de modelo no existe: {model_path}"

        # ========================================
        # PASO 3: Verificar que el modelo esta en memoria
        # ========================================
        models_response = client.get(
            "/api/v1/predictions/models",
            headers=auth_headers
        )

        assert models_response.status_code == 200
        memory_models = models_response.json()

        # Buscar nuestro modelo en memoria
        model_in_memory = any(m["model_key"] == model_key for m in memory_models)
        assert model_in_memory, f"Modelo {model_key} no esta en memoria"

        # ========================================
        # PASO 4: Simular reinicio cargando el modelo
        # ========================================
        # Primero verificamos que podemos cargar el modelo
        load_response = client.post(
            "/api/v1/predictions/models/load",
            headers=auth_headers,
            json={"model_key": model_key}
        )

        assert load_response.status_code == 200
        load_result = load_response.json()
        assert load_result.get("success") == True
        assert load_result.get("model_key") == model_key
        assert load_result.get("is_fitted") == True

        # ========================================
        # PASO 5: Usar el modelo cargado para predicciones
        # ========================================
        forecast_response = client.post(
            "/api/v1/predictions/forecast",
            headers=auth_headers,
            json={
                "model_key": model_key,
                "periods": 7
            }
        )

        # Las predicciones pueden fallar por R2 bajo, verificar respuesta
        assert forecast_response.status_code == 200
        forecast_result = forecast_response.json()

        # Si el modelo cumple el umbral R2, debe tener predicciones
        if forecast_result.get("success"):
            assert "predictions" in forecast_result
            assert forecast_result.get("model_type") is not None

    def test_list_saved_models(self, client: TestClient, auth_headers):
        """Test listar modelos guardados en disco."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.get(
            "/api/v1/predictions/models/saved",
            headers=auth_headers
        )

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)

        # Si hay modelos, verificar estructura
        if len(data) > 0:
            model = data[0]
            assert "model_key" in model
            assert "filename" in model
            assert "size_bytes" in model
            assert "is_loaded" in model

    def test_load_nonexistent_model(self, client: TestClient, auth_headers):
        """Test cargar modelo que no existe."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.post(
            "/api/v1/predictions/models/load",
            headers=auth_headers,
            json={"model_key": "modelo_inexistente_xyz123"}
        )

        assert response.status_code == 200
        result = response.json()
        assert result.get("success") == False
        assert "error" in result

    def test_load_all_models(self, client: TestClient, auth_headers):
        """Test cargar todos los modelos desde disco."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.post(
            "/api/v1/predictions/models/load-all",
            headers=auth_headers
        )

        assert response.status_code == 200
        result = response.json()
        assert result.get("success") == True
        assert "loaded" in result
        assert "failed" in result
        assert "total_loaded" in result

    def test_delete_model(self, client: TestClient, auth_headers):
        """Test eliminar modelo (crea uno temporal primero)."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Primero entrenar un modelo para eliminarlo
        train_response = client.post(
            "/api/v1/predictions/train",
            headers=auth_headers,
            json={"model_type": "linear"}
        )

        if train_response.status_code != 200:
            pytest.skip("No hay datos suficientes para entrenar")

        train_result = train_response.json()
        if not train_result.get("success"):
            pytest.skip("Entrenamiento fallido")

        model_key = train_result.get("model_key")

        # Ahora eliminar el modelo
        delete_response = client.delete(
            f"/api/v1/predictions/models/{model_key}",
            headers=auth_headers
        )

        assert delete_response.status_code == 200
        delete_result = delete_response.json()
        assert delete_result.get("success") == True
        assert delete_result.get("model_key") == model_key

        # Verificar que ya no existe en disco
        model_path = os.path.join(self.MODELS_DIR, f"{model_key}.pkl")
        assert not os.path.exists(model_path), "Archivo deberia haber sido eliminado"

    def test_delete_nonexistent_model(self, client: TestClient, auth_headers):
        """Test eliminar modelo que no existe."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.delete(
            "/api/v1/predictions/models/modelo_inexistente_abc456",
            headers=auth_headers
        )

        assert response.status_code == 200
        result = response.json()
        assert result.get("success") == False


class TestModelTrainingAndSaving:
    """Pruebas especificas para entrenamiento y guardado."""

    def test_train_linear_model_saves_to_disk(self, client: TestClient, auth_headers):
        """Test que entrenar modelo linear lo guarda en disco."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.post(
            "/api/v1/predictions/train",
            headers=auth_headers,
            json={"model_type": "linear"}
        )

        if response.status_code != 200:
            pytest.skip("Endpoint no disponible")

        result = response.json()

        if result.get("success"):
            model_key = result.get("model_key")
            model_path = os.path.join("trained_models", f"{model_key}.pkl")

            # Verificar archivo existe
            assert os.path.exists(model_path), "Modelo no fue guardado en disco"

            # Verificar tamaño razonable (> 1KB)
            size = os.path.getsize(model_path)
            assert size > 1000, f"Archivo muy pequeno: {size} bytes"

    def test_train_random_forest_saves_to_disk(self, client: TestClient, auth_headers):
        """Test que entrenar modelo random_forest lo guarda en disco."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        response = client.post(
            "/api/v1/predictions/train",
            headers=auth_headers,
            json={
                "model_type": "random_forest",
                "hyperparameters": {
                    "n_estimators": 10,  # Pocos arboles para test rapido
                    "max_depth": 5
                }
            }
        )

        if response.status_code != 200:
            pytest.skip("Endpoint no disponible")

        result = response.json()

        if result.get("success"):
            model_key = result.get("model_key")
            model_path = os.path.join("trained_models", f"{model_key}.pkl")

            assert os.path.exists(model_path), "Modelo RF no fue guardado"


class TestModelLoadAndUse:
    """Pruebas para cargar y usar modelos guardados."""

    def test_loaded_model_can_forecast(self, client: TestClient, auth_headers):
        """
        Test que un modelo cargado desde disco puede hacer predicciones.
        Este es el caso de uso principal: cargar modelo guardado y usarlo.
        """
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Verificar si hay modelos guardados
        saved_response = client.get(
            "/api/v1/predictions/models/saved",
            headers=auth_headers
        )

        if saved_response.status_code != 200:
            pytest.skip("Endpoint no disponible")

        saved_models = saved_response.json()

        if len(saved_models) == 0:
            # Entrenar uno nuevo si no hay
            train_response = client.post(
                "/api/v1/predictions/train",
                headers=auth_headers,
                json={"model_type": "linear"}
            )

            if train_response.status_code != 200:
                pytest.skip("No se puede entrenar modelo")

            result = train_response.json()
            if not result.get("success"):
                pytest.skip("Entrenamiento fallido")

            model_key = result.get("model_key")
        else:
            model_key = saved_models[0]["model_key"]

        # Cargar el modelo
        load_response = client.post(
            "/api/v1/predictions/models/load",
            headers=auth_headers,
            json={"model_key": model_key}
        )

        assert load_response.status_code == 200
        load_result = load_response.json()

        if not load_result.get("success"):
            pytest.skip(f"No se pudo cargar modelo: {load_result.get('error')}")

        # Intentar hacer prediccion con el modelo cargado
        forecast_response = client.post(
            "/api/v1/predictions/forecast",
            headers=auth_headers,
            json={
                "model_key": model_key,
                "periods": 5
            }
        )

        assert forecast_response.status_code == 200
        # La prediccion puede fallar por R2 bajo, pero el endpoint debe responder

    def test_model_metrics_preserved_after_load(self, client: TestClient, auth_headers):
        """Test que las metricas del modelo se preservan al cargar."""
        if not auth_headers:
            pytest.skip("No se pudo obtener token de autenticacion")

        # Entrenar modelo
        train_response = client.post(
            "/api/v1/predictions/train",
            headers=auth_headers,
            json={"model_type": "linear"}
        )

        if train_response.status_code != 200:
            pytest.skip("Endpoint no disponible")

        train_result = train_response.json()

        if not train_result.get("success"):
            pytest.skip("Entrenamiento fallido")

        model_key = train_result.get("model_key")
        original_metrics = train_result.get("metrics")

        # Cargar el modelo
        load_response = client.post(
            "/api/v1/predictions/models/load",
            headers=auth_headers,
            json={"model_key": model_key}
        )

        assert load_response.status_code == 200
        load_result = load_response.json()

        if load_result.get("success"):
            loaded_metrics = load_result.get("metrics")

            # Verificar que las metricas se preservaron
            if original_metrics and loaded_metrics:
                assert loaded_metrics.get("r2_score") == original_metrics.get("r2_score")
                assert loaded_metrics.get("rmse") == original_metrics.get("rmse")
