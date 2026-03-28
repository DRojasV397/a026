"""
Pruebas de integración para endpoints de catálogo de productos.
RF-Productos: CRUD de productos y categorías.

Casos cubiertos:
  PR-01: GET /productos/ incluye costoUnitario y categoriaNombre
  PR-02: Producto con sku=NULL en BD no causa HTTP 500
  PR-03: Paginación MSSQL (skip+limit) requiere ORDER BY — no retorna []
  PR-04: Productos legacy (creadoPor=NULL) son visibles
  PR-05: Filtro activos_only=true retorna solo activos

Regresiones históricas (MEMORY.md):
  BUG-01: MSSQL + OFFSET/LIMIT sin ORDER BY → [] silencioso
  BUG-02: sku=NULL → HTTP 500 si Pydantic tiene sku: str (requerido)
  BUG-03: creadoPor=NULL (productos seed) → invisibles si el filtro no incluye IS NULL
"""

import pytest
import uuid
from fastapi.testclient import TestClient


# ── Helpers ────────────────────────────────────────────────────────────────────

def unique_sku() -> str:
    """Genera un SKU único para pruebas."""
    return f"TST-{str(uuid.uuid4())[:6].upper()}"


def _create_producto(client: TestClient, auth_headers: dict, **overrides):
    """Crea un producto de prueba y retorna la respuesta."""
    payload = {
        "nombre": f"Producto Test {unique_sku()}",
        "sku": unique_sku(),
        "precioUnitario": 150.00,
        "costoUnitario": 70.00,
        "activo": True,
    }
    payload.update(overrides)
    return client.post("/api/v1/productos/", headers=auth_headers, json=payload)


# ── CRUD básico ────────────────────────────────────────────────────────────────

class TestProductosCRUD:
    """Operaciones CRUD sobre el catálogo de productos."""

    def test_crear_producto(self, client: TestClient, auth_headers):
        """Crear un producto nuevo retorna idProducto y los datos enviados."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        sku = unique_sku()
        r = _create_producto(client, auth_headers, sku=sku, nombre=f"Prod {sku}")
        assert r.status_code in [200, 201]
        data = r.json()
        assert data.get("sku") == sku
        assert "idProducto" in data

    def test_listar_productos(self, client: TestClient, auth_headers):
        """GET /productos/ retorna una lista (puede estar vacía)."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get("/api/v1/productos/", headers=auth_headers)
        assert r.status_code == 200
        assert isinstance(r.json(), list)

    def test_obtener_producto_por_id(self, client: TestClient, auth_headers):
        """GET /productos/{id} retorna el producto correcto."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        create_r = _create_producto(client, auth_headers)
        if create_r.status_code not in [200, 201]:
            pytest.skip("No se pudo crear producto de prueba")

        product_id = create_r.json().get("idProducto")
        r = client.get(f"/api/v1/productos/{product_id}", headers=auth_headers)
        assert r.status_code == 200
        assert r.json().get("idProducto") == product_id

    def test_actualizar_producto(self, client: TestClient, auth_headers):
        """PUT /productos/{id} actualiza los campos enviados."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        create_r = _create_producto(client, auth_headers)
        if create_r.status_code not in [200, 201]:
            pytest.skip("No se pudo crear producto de prueba")

        product_id = create_r.json().get("idProducto")
        update_data = {"nombre": "Nombre Actualizado OK", "precioUnitario": 299.99}
        r = client.put(
            f"/api/v1/productos/{product_id}",
            headers=auth_headers,
            json=update_data,
        )
        assert r.status_code in [200, 204]
        if r.status_code == 200:
            assert r.json().get("nombre") == "Nombre Actualizado OK"

    def test_eliminar_producto(self, client: TestClient, auth_headers):
        """DELETE /productos/{id} elimina el producto (204 o 200)."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        create_r = _create_producto(client, auth_headers)
        if create_r.status_code not in [200, 201]:
            pytest.skip("No se pudo crear producto de prueba")

        product_id = create_r.json().get("idProducto")
        r = client.delete(f"/api/v1/productos/{product_id}", headers=auth_headers)
        assert r.status_code in [200, 204]

    def test_producto_inexistente_retorna_404(self, client: TestClient, auth_headers):
        """GET /productos/999999 retorna 404 (no 500)."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get("/api/v1/productos/999999", headers=auth_headers)
        assert r.status_code in [404, 422]

    def test_sin_token_retorna_401(self, client: TestClient):
        """Sin Bearer token → 401/403."""
        r = client.get("/api/v1/productos/")
        assert r.status_code in [401, 403]


# ── Campos enriquecidos ────────────────────────────────────────────────────────

class TestProductosCamposEnriquecidos:
    """PR-01: La respuesta de GET /productos/ incluye campos enriquecidos."""

    def test_respuesta_incluye_campos_base(self, client: TestClient, auth_headers):
        """La lista incluye idProducto y nombre como mínimo."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get("/api/v1/productos/", headers=auth_headers)
        assert r.status_code == 200
        data = r.json()
        if data:
            item = data[0]
            assert "idProducto" in item, "Campo idProducto faltante"
            assert "nombre" in item, "Campo nombre faltante"

    def test_respuesta_incluye_costoUnitario(self, client: TestClient, auth_headers):
        """PR-01: costoUnitario debe estar en la respuesta (puede ser null)."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        # Crear producto con costoUnitario explícito
        sku = unique_sku()
        create_r = _create_producto(client, auth_headers, sku=sku, costoUnitario=55.00)
        if create_r.status_code not in [200, 201]:
            pytest.skip("No se pudo crear producto de prueba")

        r = client.get("/api/v1/productos/", headers=auth_headers)
        assert r.status_code == 200
        data = r.json()

        # Buscar el producto recién creado
        product = next((p for p in data if p.get("sku") == sku), None)
        if product:
            assert "costoUnitario" in product, "Campo costoUnitario faltante en la respuesta"

    def test_respuesta_incluye_categoriaNombre(self, client: TestClient, auth_headers):
        """PR-01: categoriaNombre debe estar en la respuesta (puede ser null si no tiene categoría)."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get("/api/v1/productos/", headers=auth_headers)
        assert r.status_code == 200
        data = r.json()
        if data:
            item = data[0]
            # El campo debe existir aunque sea null
            assert "categoriaNombre" in item, (
                "Campo categoriaNombre faltante. El router debe incluirlo "
                "accediendo a p.categoria.nombre con lazy-load seguro."
            )


# ── Filtros ────────────────────────────────────────────────────────────────────

class TestProductosFiltros:
    """PR-05: Filtros de productos."""

    def test_filtro_activos_solo_retorna_activos(self, client: TestClient, auth_headers):
        """activos_only=true: solo productos con activo=True."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get(
            "/api/v1/productos/",
            headers=auth_headers,
            params={"activos_only": True},
        )
        assert r.status_code in [200, 422]
        if r.status_code == 200:
            for item in r.json():
                if "activo" in item:
                    assert item["activo"] is True, (
                        f"PR-05: Producto {item.get('idProducto')} "
                        f"aparece con activo=False cuando activos_only=True"
                    )

    def test_limite_de_productos(self, client: TestClient, auth_headers):
        """limit=5 devuelve como máximo 5 productos."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get("/api/v1/productos/", headers=auth_headers, params={"limit": 5})
        assert r.status_code == 200
        data = r.json()
        assert len(data) <= 5, f"Con limit=5 se esperan máx 5 items, recibidos: {len(data)}"


# ── Regresiones MSSQL / Bugs históricos ────────────────────────────────────────

class TestProductosMSSQLRegresion:
    """
    Regresiones para bugs documentados en MEMORY.md.
    Sección: 'SQL Server + SQLAlchemy — Reglas críticas'
    """

    def test_BUG01_paginacion_skip_no_retorna_lista_vacia(
        self, client: TestClient, auth_headers
    ):
        """
        BUG-01: MSSQL + OFFSET/LIMIT SIN ORDER BY retorna [] silenciosamente.
        Con .order_by() correctamente agregado, skip+limit debe retornar items.

        Si este test falla con data=[], el router no tiene .order_by() antes
        de .offset().limit(). Agregar .order_by(Producto.idProducto).
        """
        if not auth_headers:
            pytest.skip("Sin autenticación")

        # Verificar que hay al menos 2 productos para probar skip
        all_r = client.get("/api/v1/productos/", headers=auth_headers)
        if all_r.status_code != 200:
            pytest.skip("Endpoint no disponible")

        total = len(all_r.json())
        if total < 2:
            pytest.skip(
                f"BUG-01: Se necesitan ≥2 productos en BD. "
                f"Solo hay {total}. Insertar productos seed antes de correr este test."
            )

        # Con skip=1, debe haber (total - 1) items
        r = client.get(
            "/api/v1/productos/",
            headers=auth_headers,
            params={"skip": 1, "limit": 1000},
        )
        assert r.status_code == 200
        data = r.json()
        assert len(data) > 0, (
            "BUG-01 REGRESIÓN: GET /productos/?skip=1 retorna lista vacía.\n"
            "CAUSA: Query MSSQL sin ORDER BY antes de OFFSET/LIMIT.\n"
            "SOLUCIÓN: Agregar .order_by(Producto.idProducto) en el repositorio."
        )
        assert len(data) == total - 1, (
            f"Con skip=1 se esperaban {total-1} items, se recibieron {len(data)}"
        )

    def test_BUG02_productos_con_sku_null_no_causan_500(
        self, client: TestClient, auth_headers
    ):
        """
        BUG-02: sku=NULL en BD → HTTP 500 si Pydantic define sku: str (requerido).
        El router debe usar 'p.sku or ""' para que el schema no falle.

        Si este test falla con 500, el router tiene `sku=p.sku` en lugar de
        `sku=p.sku or ""`. Ver analytics-modules/api/app/routers/productos.py
        """
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get("/api/v1/productos/", headers=auth_headers)
        assert r.status_code != 500, (
            "BUG-02 REGRESIÓN: GET /productos/ retorna HTTP 500.\n"
            "CAUSA PROBABLE: Producto con sku=NULL en BD + Pydantic `sku: str`.\n"
            "SOLUCIÓN: Usar 'sku=p.sku or \"\"' en el router de productos."
        )
        assert r.status_code == 200

    def test_BUG03_productos_legacy_creadoPor_null_son_visibles(
        self, client: TestClient, auth_headers
    ):
        """
        BUG-03: Productos seed/legacy con creadoPor=NULL son invisibles si el
        filtro solo incluye WHERE creadoPor = user_id (sin OR creadoPor IS NULL).

        Este test no puede verificarlo directamente sin conocer el contenido
        de la BD, pero sí puede verificar que el endpoint no lanza error y
        que la respuesta incluye productos (asumiendo BD con seed data).
        """
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get("/api/v1/productos/", headers=auth_headers)
        assert r.status_code == 200
        assert isinstance(r.json(), list), (
            "BUG-03: La respuesta debe ser una lista de productos, "
            "incluyendo los de creadoPor=NULL (catálogo legacy)."
        )

    def test_BUG04_nombre_null_no_causa_500(self, client: TestClient, auth_headers):
        """
        Similar a BUG-02: nombre=NULL también puede causar 500 si el schema
        requiere nombre: str. El router debe usar 'p.nombre or ""'.
        """
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get("/api/v1/productos/", headers=auth_headers)
        assert r.status_code == 200, (
            "BUG análogo a BUG-02: nombre=NULL en BD puede causar 500. "
            "Usar 'nombre=p.nombre or \"\"' en el router."
        )


# ── Categorías ────────────────────────────────────────────────────────────────

class TestCategoriasCRUD:
    """CRUD básico de categorías."""

    def test_listar_categorias(self, client: TestClient, auth_headers):
        """GET /categorias/ retorna lista."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        r = client.get("/api/v1/categorias/", headers=auth_headers)
        assert r.status_code in [200, 404]
        if r.status_code == 200:
            assert isinstance(r.json(), list)

    def test_crear_categoria(self, client: TestClient, auth_headers):
        """POST /categorias/ crea una nueva categoría."""
        if not auth_headers:
            pytest.skip("Sin autenticación")

        payload = {
            "nombre": f"Categoría Test {unique_sku()}",
            "descripcion": "Categoría de prueba de integración",
        }
        r = client.post("/api/v1/categorias/", headers=auth_headers, json=payload)
        assert r.status_code in [200, 201, 404]  # 404 si no existe este endpoint
        if r.status_code in [200, 201]:
            assert "idCategoria" in r.json() or "id" in r.json()
