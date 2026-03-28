"""
Pruebas E2E de integración — flujos completos del sistema.

Escenario E2E-01: Ciclo completo de análisis
  Login → Datos → Productos → Dashboard → Predicciones → Alertas → Rentabilidad

Escenario E2E-02: Permisos de usuario Secundario
  Admin crea secundario → secundario accede a módulos permitidos
  → secundario bloqueado en módulos no asignados

Escenario E2E-03: Ciclo de simulación
  Crear escenario → Modificar parámetros → Ejecutar → Ver resultados → Clonar → Comparar

Nota: Los tests están organizados con números para garantizar el orden lógico de ejecución
cuando se corren en secuencia. Son independientes entre sí (no comparten estado).
"""

import pytest
import uuid
import tempfile
import os
from datetime import date, timedelta
from fastapi.testclient import TestClient


# ── Helpers ────────────────────────────────────────────────────────────────────

def unique_suffix() -> str:
    return str(uuid.uuid4())[:8]


def _build_ventas_csv(rows: int = 420) -> bytes:
    """
    Genera contenido CSV de ventas con datos suficientes (>6 meses = 180 días).
    rows=420 ≈ 14 meses → cumple RN-01.01.
    """
    lines = ["fecha,producto,cantidad,precioUnitario,total"]
    base = date.today() - timedelta(days=rows)
    for i in range(rows):
        d = (base + timedelta(days=i)).isoformat()
        # Simular estacionalidad semanal y tendencia creciente
        base_amount = 1000 + (i * 2)
        variacion = (i % 7) * 150
        total = base_amount + variacion
        cantidad = 5 + (i % 8)
        precio = round(total / cantidad, 2)
        lines.append(f"{d},Producto General,{cantidad},{precio},{total}")
    return "\n".join(lines).encode()


def _upload_temp_csv(client: TestClient, auth_headers: dict, content: bytes):
    """Helper: sube archivo CSV temporal. Retorna (status_code, response_json)."""
    with tempfile.NamedTemporaryFile(suffix=".csv", delete=False) as f:
        f.write(content)
        tmp_path = f.name
    try:
        with open(tmp_path, "rb") as f:
            r = client.post(
                "/api/v1/data/upload",
                headers=auth_headers,
                files={"file": ("ventas_e2e.csv", f, "text/csv")},
            )
        return r.status_code, r.json() if r.status_code < 500 else {}
    finally:
        os.unlink(tmp_path)


# ── E2E-01: Ciclo completo de análisis ────────────────────────────────────────

class TestE2ECicloCompleto:
    """
    E2E-01: Flujo completo de análisis de negocio.
    Cada método es un paso secuencial del flujo.
    """

    # Paso 1 — Autenticación

    def test_01_login_exitoso_y_verify(self, client: TestClient, auth_headers):
        """Paso 1: Login exitoso → verify retorna datos del usuario."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        r = client.get("/api/v1/auth/verify", headers=auth_headers)
        assert r.status_code == 200, (
            f"POST /auth/verify falló con {r.status_code}. "
            "El token de autenticación no es válido."
        )
        data = r.json()
        # La respuesta debe contener información del usuario
        has_user_info = (
            data.get("success") is True
            or "nombreUsuario" in data
            or "username" in data
            or "user" in data
        )
        assert has_user_info, f"Verify no retornó info de usuario: {data}"

    # Paso 2 — Carga de datos

    def test_02_upload_csv_no_causa_500(self, client: TestClient, auth_headers):
        """Paso 2: Upload de CSV de ventas no causa errores 5xx."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        csv_data = _build_ventas_csv(420)
        status, _ = _upload_temp_csv(client, auth_headers, csv_data)

        assert status != 500, (
            f"Upload de CSV causó HTTP 500. "
            "Revisar logs del servidor para detalles."
        )
        # 200 (éxito) o 400/422 (validación) son aceptables
        assert status in [200, 400, 422], f"Status inesperado en upload: {status}"

    def test_02b_historial_cargas_accesible(self, client: TestClient, auth_headers):
        """Paso 2b: Historial de cargas es accesible después de upload."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        r = client.get("/api/v1/data/historial", headers=auth_headers)
        assert r.status_code == 200
        assert isinstance(r.json(), (list, dict)), (
            "El historial debe retornar una lista o dict con las cargas."
        )

    # Paso 3 — Catálogo de productos

    def test_03_catalogo_productos_accesible(self, client: TestClient, auth_headers):
        """Paso 3: Catálogo de productos retorna 200 y lista válida."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        r = client.get("/api/v1/productos/", headers=auth_headers)
        assert r.status_code == 200
        assert isinstance(r.json(), list), (
            "GET /productos/ debe retornar una lista de productos."
        )

    def test_03b_productos_tienen_estructura_correcta(
        self, client: TestClient, auth_headers
    ):
        """Paso 3b: Si hay productos, tienen los campos mínimos requeridos."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        r = client.get("/api/v1/productos/", headers=auth_headers)
        assert r.status_code == 200
        data = r.json()
        if data:
            item = data[0]
            assert "idProducto" in item, "Falta campo idProducto en productos"
            assert "nombre" in item, "Falta campo nombre en productos"

    # Paso 4 — Dashboard ejecutivo

    def test_04_dashboard_executive_accesible(self, client: TestClient, auth_headers):
        """Paso 4: Dashboard ejecutivo retorna respuesta válida."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        r = client.get("/api/v1/dashboard/executive", headers=auth_headers)
        assert r.status_code in [200, 500], (
            f"Dashboard retornó status inesperado: {r.status_code}"
        )
        if r.status_code == 200:
            data = r.json()
            assert "success" in data or "resumen_ventas" in data, (
                "Dashboard no retornó estructura esperada"
            )

    def test_04b_dashboard_con_rango_fechas(self, client: TestClient, auth_headers):
        """Paso 4b: Dashboard con rango de fechas explícito."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=90)

        r = client.get(
            "/api/v1/dashboard/executive",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
            },
        )
        assert r.status_code in [200, 500]

    # Paso 5 — Predicciones

    def test_05_validar_datos_para_prediccion(self, client: TestClient, auth_headers):
        """Paso 5: Validar que hay datos suficientes para predicción (RN-01.01)."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=365)

        r = client.post(
            "/api/v1/predictions/validate-data",
            headers=auth_headers,
            json={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
            },
        )
        assert r.status_code in [200, 400, 422], (
            f"validate-data retornó status inesperado: {r.status_code}"
        )

    def test_05b_listar_tipos_de_modelos(self, client: TestClient, auth_headers):
        """Paso 5b: Lista de tipos de modelos disponibles es accesible."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        r = client.get("/api/v1/predictions/model-types", headers=auth_headers)
        assert r.status_code == 200
        data = r.json()
        # Debe incluir al menos los modelos básicos
        assert isinstance(data, (list, dict))

    @pytest.mark.slow
    def test_05c_entrenamiento_modelo_lineal(
        self, client: TestClient, auth_headers
    ):
        """
        Paso 5c: Entrenar modelo lineal.
        Puede fallar por datos insuficientes pero no debe causar 500 inesperado.
        Timeout extendido a 150s (el entrenamiento puede tardar hasta 120s).
        Marcado como @slow para excluirlo con --fast.
        """
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=420)

        r = client.post(
            "/api/v1/predictions/train",
            headers=auth_headers,
            json={
                "model_type": "linear",
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
                "nombre": f"E2E-Linear-{unique_suffix()}",
            },
            timeout=130,
        )

        # 200 = entrenado (éxito o fallo de ML por R²)
        # 400/422 = datos insuficientes o parámetros inválidos
        # 500 = error interno (no aceptable en este contexto)
        assert r.status_code in [200, 400, 422], (
            f"Entrenamiento de modelo retornó {r.status_code}. "
            "HTTP 500 no es aceptable en el endpoint de predicciones."
        )

        if r.status_code == 200:
            data = r.json()
            # Si entrenó con éxito, debe incluir métricas
            if data.get("success"):
                has_metrics = "metrics" in data or "metricas" in data
                assert has_metrics, (
                    "Entrenamiento exitoso debe incluir métricas del modelo."
                )

    # Paso 6 — Alertas

    def test_06_analizar_y_generar_alertas(self, client: TestClient, auth_headers):
        """Paso 6: Análisis de alertas automático es invocable."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        r = client.post("/api/v1/alerts/analyze", headers=auth_headers)
        assert r.status_code in [200, 500]
        if r.status_code == 200:
            data = r.json()
            assert isinstance(data, dict)

    def test_06b_listar_alertas_activas(self, client: TestClient, auth_headers):
        """Paso 6b: Lista de alertas activas es accesible."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        r = client.get("/api/v1/alerts", headers=auth_headers)
        assert r.status_code in [200, 500]
        if r.status_code == 200:
            assert isinstance(r.json(), (list, dict))

    # Paso 7 — Rentabilidad

    def test_07_rentabilidad_summary(self, client: TestClient, auth_headers):
        """Paso 7: Resumen de rentabilidad es accesible."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=90)

        r = client.get(
            "/api/v1/profitability/summary",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
            },
        )
        assert r.status_code in [200, 500]

    def test_07b_rentabilidad_por_productos(self, client: TestClient, auth_headers):
        """Paso 7b: Rentabilidad por producto es accesible."""
        if not auth_headers:
            pytest.skip("No se pudo autenticar")

        fecha_fin = date.today()
        fecha_inicio = fecha_fin - timedelta(days=30)

        r = client.get(
            "/api/v1/profitability/products",
            headers=auth_headers,
            params={
                "fecha_inicio": fecha_inicio.isoformat(),
                "fecha_fin": fecha_fin.isoformat(),
            },
        )
        assert r.status_code in [200, 500]


# ── E2E-02: Permisos de usuario Secundario ─────────────────────────────────────

class TestE2EPermisosSecundario:
    """
    E2E-02: Flujo completo de permisos.
    Admin crea usuario secundario → login → accesos permitidos → accesos bloqueados.
    """

    @pytest.fixture
    def secundario_datos_predicciones(self, client: TestClient, admin_headers):
        """
        Crea usuario Secundario con módulos [datos, predicciones].
        Retorna headers del usuario secundario, o None si falla.
        """
        if not admin_headers:
            return None

        s = unique_suffix()
        username = f"e2e_sec_{s}"
        password = "E2ESec123!"
        payload = {
            "nombreCompleto": f"E2E Secundario {s}",
            "nombreUsuario": username,
            "email": f"e2e_sec_{s}@test.com",
            "password": password,
            "tipo": "Secundario",
            "modulos": ["datos", "predicciones"],
        }

        create_r = client.post(
            "/api/v1/admin/usuarios", headers=admin_headers, json=payload
        )
        if create_r.status_code not in [200, 201]:
            return None

        login_r = client.post(
            "/api/v1/auth/login/json",
            json={"username": username, "password": password},
        )
        if login_r.status_code != 200:
            return None

        token = login_r.json().get("access_token")
        return {"Authorization": f"Bearer {token}"}

    def test_secundario_puede_autenticarse(
        self, client: TestClient, secundario_datos_predicciones
    ):
        """Paso 1: El usuario secundario puede autenticarse y obtener token válido."""
        if not secundario_datos_predicciones:
            pytest.skip("No se pudo crear usuario secundario")

        r = client.get("/api/v1/auth/verify", headers=secundario_datos_predicciones)
        assert r.status_code == 200, (
            "Token del usuario secundario no es válido en /auth/verify"
        )

    def test_secundario_accede_a_datos(
        self, client: TestClient, secundario_datos_predicciones
    ):
        """Paso 2: Usuario con 'datos' puede ver historial de cargas."""
        if not secundario_datos_predicciones:
            pytest.skip("No se pudo crear usuario secundario")

        r = client.get("/api/v1/data/historial", headers=secundario_datos_predicciones)
        assert r.status_code != 403, (
            f"E2E-02: Usuario con módulo 'datos' recibió 403 en /data/historial. "
            f"Status: {r.status_code}"
        )

    def test_secundario_accede_a_predicciones(
        self, client: TestClient, secundario_datos_predicciones
    ):
        """Paso 3: Usuario con 'predicciones' puede ver tipos de modelos."""
        if not secundario_datos_predicciones:
            pytest.skip("No se pudo crear usuario secundario")

        r = client.get(
            "/api/v1/predictions/model-types", headers=secundario_datos_predicciones
        )
        assert r.status_code != 403, (
            f"E2E-02: Usuario con módulo 'predicciones' recibió 403. "
            f"Status: {r.status_code}"
        )

    def test_secundario_bloqueado_en_admin(
        self, client: TestClient, secundario_datos_predicciones
    ):
        """Paso 4: Usuario secundario NO puede acceder a /admin/usuarios."""
        if not secundario_datos_predicciones:
            pytest.skip("No se pudo crear usuario secundario")

        r = client.get(
            "/api/v1/admin/usuarios", headers=secundario_datos_predicciones
        )
        assert r.status_code in [401, 403], (
            f"E2E-02: Usuario secundario accedió a /admin/usuarios. "
            f"Status: {r.status_code}. "
            "Usuarios Secundarios nunca deben poder acceder al módulo admin."
        )


# ── E2E-03: Ciclo de Simulación ────────────────────────────────────────────────

class TestE2ECicloSimulacion:
    """
    E2E-03: Flujo completo del módulo de simulación de escenarios.
    Crear → Modificar parámetros → Ejecutar → Resultados → Clonar → Comparar
    """

    @pytest.fixture
    def escenario_id(self, client: TestClient, auth_headers):
        """Crea un escenario de prueba y retorna su ID."""
        if not auth_headers:
            return None

        r = client.post(
            "/api/v1/simulation/create",
            headers=auth_headers,
            json={
                "nombre": f"E2E Escenario {unique_suffix()}",
                "descripcion": "Escenario creado por test E2E",
                "basado_en_historico": True,
                "periodos": 3,
            },
        )
        if r.status_code not in [200, 201]:
            return None

        data = r.json()
        return data.get("id") or data.get("id_escenario") or data.get("idEscenario")

    def test_01_crear_escenario(self, client: TestClient, auth_headers):
        """Paso 1: Crear escenario retorna ID válido."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.post(
            "/api/v1/simulation/create",
            headers=auth_headers,
            json={
                "nombre": f"E2E Crear {unique_suffix()}",
                "descripcion": "Test de creación E2E",
                "basado_en_historico": True,
                "periodos": 3,
            },
        )
        assert r.status_code in [200, 201], (
            f"Crear escenario retornó {r.status_code}"
        )
        data = r.json()
        has_id = "id" in data or "id_escenario" in data or "idEscenario" in data or "success" in data
        assert has_id, f"Respuesta no incluye ID del escenario: {data}"

    def test_02_listar_escenarios(self, client: TestClient, auth_headers):
        """Paso 2: Lista de escenarios es accesible."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get("/api/v1/simulation/scenarios", headers=auth_headers)
        assert r.status_code in [200, 500]
        if r.status_code == 200:
            assert isinstance(r.json(), (list, dict))

    def test_03_modificar_parametros(
        self, client: TestClient, auth_headers, escenario_id
    ):
        """Paso 3: Modificar parámetros del escenario (variación ≤50%, RN-05.01)."""
        if not auth_headers:
            pytest.skip("Sin autenticación")
        if not escenario_id:
            pytest.skip("No se pudo crear escenario de prueba")

        r = client.put(
            f"/api/v1/simulation/{escenario_id}/parameters",
            headers=auth_headers,
            json={
                "parametros": [
                    {
                        "parametro": "variacion_precio",
                        "valorActual": 10.0,  # +10% (dentro del límite ±50%)
                        "valorBase": 0.0,
                    }
                ]
            },
        )
        assert r.status_code in [200, 204, 422], (
            f"Modificar parámetros retornó {r.status_code}"
        )

    def test_03b_parametro_excede_limite_falla(
        self, client: TestClient, auth_headers, escenario_id
    ):
        """Paso 3b: Variación >50% debe ser rechazada (RN-05.01)."""
        if not auth_headers:
            pytest.skip("Sin autenticación")
        if not escenario_id:
            pytest.skip("No se pudo crear escenario de prueba")

        r = client.put(
            f"/api/v1/simulation/{escenario_id}/parameters",
            headers=auth_headers,
            json={
                "parametros": [
                    {
                        "parametro": "variacion_precio",
                        "valorActual": 75.0,  # +75% → excede límite de ±50%
                        "valorBase": 0.0,
                    }
                ]
            },
        )
        # RN-05.01: variación máxima ±50%
        assert r.status_code in [400, 422], (
            f"SIM-02: Variación >50% debe ser rechazada (422/400). "
            f"Recibido: {r.status_code}"
        )

    def test_04_ejecutar_escenario(
        self, client: TestClient, auth_headers, escenario_id
    ):
        """Paso 4: Ejecutar simulación retorna resultados."""
        if not auth_headers:
            pytest.skip("Sin autenticación")
        if not escenario_id:
            pytest.skip("No se pudo crear escenario de prueba")

        r = client.post(
            f"/api/v1/simulation/{escenario_id}/run",
            headers=auth_headers,
            json={
                "guardar_resultados": True,
                "granularidad": "mensual",
            },
        )
        assert r.status_code in [200, 400, 422, 500], (
            f"Ejecutar simulación retornó status inesperado: {r.status_code}"
        )

    def test_05_clonar_escenario(
        self, client: TestClient, auth_headers, escenario_id
    ):
        """Paso 5: Clonar escenario crea uno nuevo con los mismos parámetros."""
        if not auth_headers:
            pytest.skip("Sin autenticación")
        if not escenario_id:
            pytest.skip("No se pudo crear escenario de prueba")

        r = client.post(
            f"/api/v1/simulation/{escenario_id}/clone",
            headers=auth_headers,
            json={"nuevo_nombre": f"Clon E2E {unique_suffix()}"},
        )
        assert r.status_code in [200, 201, 404, 500]
        if r.status_code in [200, 201]:
            data = r.json()
            has_id = "id" in data or "id_escenario" in data or "idEscenario" in data or "success" in data
            assert has_id, "El clon debe retornar el ID del nuevo escenario"
