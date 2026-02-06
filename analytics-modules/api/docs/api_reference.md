# Referencia de API - Sistema de Inteligencia Empresarial

## Descripcion General

API REST para el Sistema de Inteligencia Empresarial para PYMEs (TT 2026-A026).

**Base URL:** `http://localhost:8000/api/v1`

**Documentacion Interactiva:**
- Swagger UI: `http://localhost:8000/docs`
- ReDoc: `http://localhost:8000/redoc`
- OpenAPI JSON: `http://localhost:8000/openapi.json`

---

## Autenticacion

La API utiliza autenticacion JWT (JSON Web Tokens). Todos los endpoints (excepto login y registro) requieren un token valido.

### Headers Requeridos

```
Authorization: Bearer <token>
Content-Type: application/json
```

### Endpoints de Autenticacion

#### POST /auth/login
Inicia sesion y obtiene token JWT.

**Request Body (JSON):**
```json
{
  "username": "string",
  "password": "string"
}
```

**Response:**
```json
{
  "access_token": "string",
  "token_type": "bearer",
  "expires_in": 3600,
  "user": {
    "idUsuario": 1,
    "nombreUsuario": "string",
    "nombreCompleto": "string",
    "email": "string",
    "roles": ["admin"]
  }
}
```

#### POST /auth/register
Registra un nuevo usuario.

**Request Body:**
```json
{
  "nombreCompleto": "string",
  "nombreUsuario": "string",
  "email": "user@example.com",
  "password": "string"
}
```

#### GET /auth/me
Obtiene informacion del usuario autenticado.

#### POST /auth/refresh
Renueva el token JWT.

#### POST /auth/logout
Invalida el token actual.

---

## Modulo de Predicciones (RF-02)

### POST /predictions/train
Entrena un modelo predictivo.

**Request Body:**
```json
{
  "model_type": "linear|random_forest|arima|sarima",
  "fecha_inicio": "2024-01-01",
  "fecha_fin": "2024-12-31",
  "hyperparameters": {
    "n_estimators": 100,
    "max_depth": 10
  }
}
```

**Response:**
```json
{
  "success": true,
  "model_id": 1,
  "model_key": "random_forest_20240215123456",
  "model_type": "random_forest",
  "metrics": {
    "r2_score": 0.85,
    "rmse": 1234.56,
    "mae": 987.65,
    "mape": 5.23
  },
  "meets_r2_threshold": true,
  "recommendation": "Modelo apto para predicciones",
  "training_samples": 250,
  "test_samples": 107
}
```

### POST /predictions/forecast
Genera predicciones de ventas.

**Request Body:**
```json
{
  "model_type": "random_forest",
  "periods": 30
}
```

**Response:**
```json
{
  "success": true,
  "predictions": {
    "predictions": [
      {"date": "2024-01-01", "value": 15000.50, "confidence_lower": 14000.00, "confidence_upper": 16000.00}
    ],
    "confidence_level": 0.95,
    "model_type": "random_forest"
  },
  "model_type": "random_forest",
  "periods": 30
}
```

### POST /predictions/auto-select
Selecciona automaticamente el mejor modelo (RF-02.06).

**Response:**
```json
{
  "success": true,
  "best_model": {
    "type": "random_forest",
    "metrics": {"r2_score": 0.87}
  },
  "meets_r2_threshold": true,
  "all_models": {
    "linear": {"metrics": {"r2_score": 0.75}},
    "random_forest": {"metrics": {"r2_score": 0.87}}
  },
  "recommendation": "Random Forest tiene el mejor desempeno"
}
```

### GET /predictions/models
Lista modelos entrenados en memoria.

### GET /predictions/models/saved
Lista modelos guardados en disco.

### POST /predictions/models/load
Carga un modelo desde disco.

**Request Body:**
```json
{
  "model_key": "random_forest_20240215123456"
}
```

### POST /predictions/models/load-all
Carga todos los modelos guardados en disco.

### DELETE /predictions/models/{model_key}
Elimina un modelo de memoria y disco.

---

## Modulo de Rentabilidad (RF-06)

### POST /profitability/indicators
Calcula indicadores financieros.

**Request Body:**
```json
{
  "fecha_inicio": "2024-01-01",
  "fecha_fin": "2024-12-31",
  "periodo": "mensual"
}
```

**Response:**
```json
{
  "success": true,
  "indicadores": {
    "margen_bruto": 35.5,
    "margen_operativo": 20.3,
    "margen_neto": 15.2,
    "roa": 12.5,
    "roe": 18.7,
    "utilidad_operativa": 150000.00
  }
}
```

### GET /profitability/products
Rentabilidad por producto (RF-06.02).

### GET /profitability/products/non-profitable
Productos no rentables (margen < 10%) (RF-06.03).

### GET /profitability/categories
Rentabilidad por categoria.

### GET /profitability/trends
Tendencias de rentabilidad temporal.

### POST /profitability/compare
Compara rentabilidad entre dos periodos.

---

## Modulo de Simulacion (RF-05)

### POST /simulation/create
Crea un nuevo escenario de simulacion.

**Request Body:**
```json
{
  "nombre": "Escenario Optimista",
  "descripcion": "Incremento de precios del 10%"
}
```

### PUT /simulation/{id}/parameters
Modifica parametros del escenario (RF-05.01).

**Request Body:**
```json
{
  "parametros": [
    {"nombre": "precio_promedio", "valor_actual": 100, "valor_modificado": 110, "variacion_porcentual": 10}
  ]
}
```

**Nota:** Las variaciones no pueden exceder +/- 50% (RN-05.01).

### POST /simulation/{id}/run
Ejecuta la simulacion.

### GET /simulation/{id}/results
Obtiene resultados de la simulacion.

### POST /simulation/compare
Compara hasta 5 escenarios (RN-05.03).

**Request Body:**
```json
{
  "scenario_ids": [1, 2, 3]
}
```

### GET /simulation/scenarios
Lista escenarios guardados.

---

## Modulo de Alertas (RF-04)

### GET /alerts
Lista alertas activas (maximo 10 por RN-04.05).

### GET /alerts/history
Historial de alertas.

### GET /alerts/summary
Resumen de alertas por tipo e importancia.

### POST /alerts/analyze
Analiza datos y genera alertas automaticamente.

- Alerta de riesgo: caida > 15% (RN-04.01)
- Alerta de oportunidad: subida > 20% (RN-04.02)
- Alerta de anomalias: > 5% transacciones (RN-04.03)

### PUT /alerts/{id}/read
Marca alerta como leida.

### PUT /alerts/{id}/status
Cambia estado de alerta.

### POST /alerts/config
Configura umbrales de alertas (RF-04.04).

---

## Modulo de Dashboard (RF-03)

### GET /dashboard/executive
Dashboard ejecutivo con KPIs consolidados.

**Response:**
```json
{
  "success": true,
  "kpis": {
    "ventas_totales": 1500000.00,
    "compras_totales": 900000.00,
    "margen_bruto": 40.0,
    "productos_activos": 45,
    "alertas_pendientes": 3
  },
  "tendencias": {...},
  "alertas_recientes": [...]
}
```

### GET /dashboard/kpi/{kpi_name}
Detalle de un KPI especifico.

### GET /dashboard/predictions
Predicciones recientes.

### GET /dashboard/compare
Comparar valores reales vs predichos (RF-03.05).

### GET /dashboard/users/{id}/preferences
Obtener preferencias de usuario.

### PUT /dashboard/users/{id}/preferences
Actualizar preferencias de usuario.

---

## Modulo de Reportes (RF-07)

### POST /dashboard/reports/generate
Genera un reporte.

**Request Body:**
```json
{
  "tipo": "ventas|compras|rentabilidad|productos",
  "fecha_inicio": "2024-01-01",
  "fecha_fin": "2024-12-31",
  "formato": "json|csv",
  "agrupacion": "diario|semanal|mensual"
}
```

### GET /dashboard/reports/types
Tipos de reportes disponibles.

### GET /dashboard/reports/{id}
Obtener reporte por ID.

### GET /dashboard/reports
Listar reportes generados.

### GET /dashboard/reports/sales
Reporte rapido de ventas.

### GET /dashboard/reports/purchases
Reporte rapido de compras.

### GET /dashboard/reports/profitability
Reporte rapido de rentabilidad.

---

## Gestion de Datos (RF-01)

### POST /data/upload
Sube archivo CSV o Excel.

**Request:** multipart/form-data con archivo

### POST /data/validate
Valida estructura del archivo (RF-01.02).

### GET /data/preview/{id}
Preview de datos cargados.

### POST /data/clean
Ejecuta limpieza de datos:
- Duplicados (RN-02.01)
- Valores nulos (RN-02.02, RN-02.04)
- Outliers con Z-Score (RN-02.03)
- Mantiene 70% registros (RN-02.05)

### POST /data/confirm
Confirma e importa los datos.

### GET /data/quality-report/{id}
Reporte de calidad de datos.

---

## Ventas y Compras

### Ventas

| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| GET | /ventas | Listar ventas con paginacion |
| GET | /ventas/{id} | Obtener venta por ID |
| POST | /ventas | Crear nueva venta |
| GET | /ventas/resumen/mensual | Resumen mensual |
| GET | /ventas/total/periodo | Total por periodo |

### Compras

| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| GET | /compras | Listar compras con paginacion |
| GET | /compras/{id} | Obtener compra por ID |
| POST | /compras | Crear nueva compra |
| GET | /compras/resumen/mensual | Resumen mensual |
| GET | /compras/total/periodo | Total por periodo |

---

## Codigos de Estado HTTP

| Codigo | Descripcion |
|--------|-------------|
| 200 | Exitoso |
| 201 | Creado |
| 400 | Error de validacion |
| 401 | No autenticado |
| 403 | No autorizado |
| 404 | No encontrado |
| 422 | Error de procesamiento |
| 500 | Error interno |

---

## Reglas de Negocio Importantes

### Predicciones
- **RN-01.01:** Minimo 6 meses de datos historicos
- **RN-03.01:** Division 70/30 entrenamiento/validacion
- **RN-03.02:** R2 >= 0.7 para modelo usable
- **RN-03.03:** Maximo 6 meses (180 dias) de prediccion

### Simulacion
- **RN-05.01:** Variables no varian mas de +/- 50%
- **RN-05.03:** Maximo 5 escenarios simultaneos

### Alertas
- **RN-04.01:** Alerta riesgo si caida > 15%
- **RN-04.02:** Alerta oportunidad si subida > 20%
- **RN-04.05:** Maximo 10 alertas activas simultaneas

### Rentabilidad
- **RN-06.04:** Producto no rentable si margen < 10%

---

## Ejemplos de Uso

### Flujo Completo de Prediccion

```bash
# 1. Login
curl -X POST http://localhost:8000/api/v1/auth/login/json \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password123"}'

# 2. Entrenar modelo
curl -X POST http://localhost:8000/api/v1/predictions/train \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"model_type": "random_forest"}'

# 3. Generar prediccion
curl -X POST http://localhost:8000/api/v1/predictions/forecast \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"model_type": "random_forest", "periods": 30}'
```

---

**Version:** 1.0
**Ultima actualizacion:** 02-Febrero-2026
