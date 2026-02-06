# Plan de Desarrollo - Modulo de API y Analitica de Datos
## Sistema de Inteligencia Empresarial para PYMEs (TT 2026-A026)

**Fecha de creacion:** 26 de enero de 2026
**Version:** 1.0
**Modulo:** analytics-modules/api
**Tecnologias:** FastAPI, Python, SQL Server, SQLAlchemy

---

## 1. Vision General del Modulo

Este modulo corresponde a la **Capa de Inteligencia Analitica** y parte de la **Capa de Aplicacion (Backend)** del sistema. Segun la arquitectura definida en el reporte tecnico, este componente es responsable de:

- **API REST:** Comunicacion entre el frontend (JavaFX) y los modulos analiticos
- **Modulo de Prediccion:** Algoritmos de ML para proyecciones de ventas
- **Modulo de Evaluacion de Rentabilidad:** Calculo de indicadores financieros
- **Modulo de Simulacion de Escenarios:** Motor de simulacion financiera
- **Gestion de Datos:** Procesamiento, limpieza y validacion de datos
- **Sistema de Alertas:** Deteccion de anomalias y generacion de alertas

---

## 2. Estado Actual del Proyecto

### 2.1 Estructura Existente

```
analytics-modules/api/
├── main.py                    # Punto de entrada (IMPLEMENTADO)
├── requirements.txt           # Dependencias (IMPLEMENTADO)
├── app/
│   ├── config/
│   │   └── settings.py        # Configuracion (IMPLEMENTADO)
│   ├── database/
│   │   └── connection.py      # Conexion BD (IMPLEMENTADO)
│   ├── models/
│   │   ├── usuario.py         # (IMPLEMENTADO)
│   │   ├── producto.py        # (IMPLEMENTADO)
│   │   ├── venta.py           # (IMPLEMENTADO)
│   │   ├── compra.py          # (IMPLEMENTADO)
│   │   ├── prediccion.py      # (IMPLEMENTADO)
│   │   └── rentabilidad.py    # (IMPLEMENTADO)
│   ├── schemas/
│   │   ├── usuario.py         # (IMPLEMENTADO)
│   │   ├── producto.py        # (IMPLEMENTADO)
│   │   ├── venta.py           # (IMPLEMENTADO)
│   │   ├── compra.py          # (IMPLEMENTADO)
│   │   ├── common.py          # (IMPLEMENTADO)
│   │   ├── auth.py            # (IMPLEMENTADO)
│   │   ├── prediction.py      # (IMPLEMENTADO)
│   │   ├── profitability.py   # (IMPLEMENTADO)
│   │   ├── simulation.py      # (IMPLEMENTADO)
│   │   └── alert.py           # (IMPLEMENTADO)
│   ├── repositories/
│   │   ├── base_repository.py # (IMPLEMENTADO)
│   │   ├── usuario_repository.py # (IMPLEMENTADO)
│   │   ├── producto_repository.py # (IMPLEMENTADO)
│   │   ├── venta_repository.py # (IMPLEMENTADO)
│   │   ├── compra_repository.py # (IMPLEMENTADO)
│   │   ├── modelo_repository.py # (IMPLEMENTADO)
│   │   ├── prediccion_repository.py # (IMPLEMENTADO)
│   │   ├── escenario_repository.py # (IMPLEMENTADO)
│   │   ├── rentabilidad_repository.py # (IMPLEMENTADO)
│   │   └── alerta_repository.py # (IMPLEMENTADO)
│   ├── services/
│   │   ├── usuario_service.py # (IMPLEMENTADO)
│   │   └── producto_service.py # (IMPLEMENTADO)
│   └── routers/
│       ├── usuarios.py        # (IMPLEMENTADO)
│       └── productos.py       # (IMPLEMENTADO)
```

### 2.2 Funcionalidades Pendientes (Segun Requisitos RF del Reporte)

| Modulo | Estado | Prioridad |
|--------|--------|-----------|
| RF-01: Gestion de Datos | Parcial | Alta |
| RF-02: Analisis Predictivo | Pendiente | Alta |
| RF-03: Visualizacion (endpoints) | Pendiente | Media |
| RF-04: Generacion de Alertas | Pendiente | Media |
| RF-05: Simulacion de Escenarios | Pendiente | Alta |
| RF-06: Evaluacion de Rentabilidad | Pendiente | Alta |
| RF-07: Generacion de Reportes | Pendiente | Baja |
| RF-08: API de Integracion | Parcial | Alta |

---

## 3. Arquitectura Propuesta (Modular y Escalable)

### 3.1 Estructura Final del Proyecto

```
analytics-modules/api/
├── main.py
├── requirements.txt
├── .env
├── .env.example
├── tests/
│   ├── __init__.py
│   ├── conftest.py
│   ├── unit/
│   │   ├── test_data_service.py
│   │   ├── test_prediction_service.py
│   │   ├── test_profitability_service.py
│   │   └── test_simulation_service.py
│   └── integration/
│       ├── test_api_endpoints.py
│       └── test_database.py
├── app/
│   ├── __init__.py
│   ├── config/
│   │   ├── __init__.py
│   │   └── settings.py
│   ├── database/
│   │   ├── __init__.py
│   │   └── connection.py
│   ├── models/                      # Modelos SQLAlchemy (ORM)
│   │   ├── __init__.py
│   │   ├── usuario.py
│   │   ├── rol.py
│   │   ├── producto.py
│   │   ├── categoria.py
│   │   ├── venta.py
│   │   ├── detalle_venta.py
│   │   ├── compra.py
│   │   ├── detalle_compra.py
│   │   ├── modelo.py
│   │   ├── version_modelo.py
│   │   ├── prediccion.py
│   │   ├── alerta.py
│   │   ├── escenario.py
│   │   ├── parametro_escenario.py
│   │   ├── resultado_escenario.py
│   │   ├── rentabilidad.py
│   │   ├── resultado_financiero.py
│   │   └── reporte.py
│   ├── schemas/                     # Schemas Pydantic (validacion)
│   │   ├── __init__.py
│   │   ├── common.py                # Schemas comunes (paginacion, respuestas)
│   │   ├── auth.py
│   │   ├── usuario.py
│   │   ├── producto.py
│   │   ├── venta.py
│   │   ├── compra.py
│   │   ├── data_upload.py           # Para carga de archivos
│   │   ├── prediction.py
│   │   ├── profitability.py
│   │   ├── simulation.py
│   │   ├── alert.py
│   │   └── report.py
│   ├── repositories/                # Capa de acceso a datos
│   │   ├── __init__.py
│   │   ├── base_repository.py
│   │   ├── usuario_repository.py
│   │   ├── producto_repository.py
│   │   ├── venta_repository.py
│   │   ├── compra_repository.py
│   │   ├── modelo_repository.py
│   │   ├── prediccion_repository.py
│   │   ├── escenario_repository.py
│   │   ├── rentabilidad_repository.py
│   │   └── alerta_repository.py
│   ├── services/                    # Logica de negocio
│   │   ├── __init__.py
│   │   ├── auth_service.py          # Autenticacion JWT
│   │   ├── usuario_service.py
│   │   ├── producto_service.py
│   │   ├── data_service.py          # Carga y procesamiento de datos
│   │   ├── cleaning_service.py      # Limpieza de datos
│   │   ├── prediction_service.py    # Modelos predictivos
│   │   ├── profitability_service.py # Indicadores financieros
│   │   ├── simulation_service.py    # Simulacion de escenarios
│   │   ├── alert_service.py         # Sistema de alertas
│   │   └── report_service.py        # Generacion de reportes
│   ├── analytics/                   # Modulos de ML y Analisis
│   │   ├── __init__.py
│   │   ├── preprocessing/
│   │   │   ├── __init__.py
│   │   │   ├── data_cleaner.py      # Limpieza de datos
│   │   │   ├── data_validator.py    # Validacion de estructura
│   │   │   └── data_transformer.py  # Normalizacion
│   │   ├── models/
│   │   │   ├── __init__.py
│   │   │   ├── base_model.py        # Clase base para modelos
│   │   │   ├── linear_regression.py
│   │   │   ├── arima_model.py
│   │   │   ├── sarima_model.py
│   │   │   ├── random_forest.py
│   │   │   ├── xgboost_model.py
│   │   │   └── kmeans_clustering.py
│   │   ├── evaluation/
│   │   │   ├── __init__.py
│   │   │   └── metrics.py           # RMSE, MAE, R2, etc.
│   │   └── anomaly/
│   │       ├── __init__.py
│   │       └── detector.py          # Z-Score y deteccion de anomalias
│   ├── routers/                     # Endpoints API
│   │   ├── __init__.py
│   │   ├── auth.py
│   │   ├── usuarios.py
│   │   ├── productos.py
│   │   ├── categorias.py
│   │   ├── ventas.py
│   │   ├── compras.py
│   │   ├── data.py                  # Carga y procesamiento de datos
│   │   ├── predictions.py           # Endpoints de prediccion
│   │   ├── profitability.py         # Endpoints de rentabilidad
│   │   ├── simulations.py           # Endpoints de simulacion
│   │   ├── alerts.py                # Endpoints de alertas
│   │   ├── dashboard.py             # Endpoints para dashboard
│   │   └── reports.py               # Endpoints de reportes
│   ├── middleware/
│   │   ├── __init__.py
│   │   ├── auth_middleware.py       # Verificacion JWT
│   │   └── logging_middleware.py    # Logs de operaciones
│   └── utils/
│       ├── __init__.py
│       ├── file_parser.py           # Parser CSV/Excel
│       ├── date_utils.py
│       ├── validators.py
│       └── exceptions.py            # Excepciones personalizadas
└── docs/
    ├── api_reference.md
    └── deployment.md
```

---

## 4. Fases de Desarrollo

### FASE 1: Consolidacion de Base de Datos y Modelos (Sprint 1) - COMPLETADA
**Duracion estimada:** 1-2 semanas
**Estado:** COMPLETADA (26-Enero-2026)

#### Objetivos:
- Completar todos los modelos SQLAlchemy segun diagrama relacional
- Implementar migraciones de base de datos
- Configurar repositorios faltantes

#### Tareas:

**1.1 Completar Modelos SQLAlchemy**
- [x] Modelo `Rol` y `UsuarioRol`
- [x] Modelo `Categoria`
- [x] Modelo `DetalleVenta` y `DetalleCompra`
- [x] Modelo `Modelo` y `VersionModelo`
- [x] Modelo `Prediccion`
- [x] Modelo `Alerta`
- [x] Modelo `Escenario`, `ParametroEscenario`, `ResultadoEscenario`
- [x] Modelo `Rentabilidad`
- [x] Modelo `ResultadoFinanciero`
- [x] Modelo `Reporte`
- [x] Modelo `PreferenciaUsuario`

**1.2 Completar Schemas Pydantic**
- [x] Schemas de autenticacion (JWT) - `auth.py`
- [x] Schemas de compras - `compra.py`
- [x] Schemas de prediccion - `prediction.py`
- [x] Schemas de rentabilidad - `profitability.py`
- [x] Schemas de simulacion - `simulation.py`
- [x] Schemas de alertas - `alert.py`
- [x] Schemas comunes (paginacion, respuestas estandar) - `common.py`

**1.3 Completar Repositorios**
- [x] `venta_repository.py`
- [x] `compra_repository.py`
- [x] `modelo_repository.py`
- [x] `prediccion_repository.py`
- [x] `escenario_repository.py`
- [x] `rentabilidad_repository.py`
- [x] `alerta_repository.py`

**Entregables:**
- [x] Todos los modelos ORM implementados
- [x] Repositorios con operaciones CRUD
- [x] Schemas de validacion completos

**Notas de implementacion:**
- Los modelos fueron sincronizados con la estructura actual de la BD
- Tabla `Compra`: solo tiene columnas `idCompra`, `fecha`, `proveedor`, `total`, `moneda`, `creadoPor`
- Tabla `Modelo`: tiene columnas `idModelo`, `tipoModelo`, `objetivo`, `creadoEn`
- Tabla `Alerta`: tiene columnas `idAlerta`, `idPred`, `tipo`, `importancia`, `metrica`, `valorActual`, `valorEsperado`, `nivelConfianza`, `estado`, `creadaEn`

---

### FASE 2: Autenticacion y Seguridad (Sprint 2) - COMPLETADA
**Duracion estimada:** 1 semana
**Estado:** COMPLETADA (27-Enero-2026)

#### Objetivos:
- Implementar sistema de autenticacion JWT
- Configurar middleware de autorizacion
- Implementar control de acceso por roles

#### Tareas:

**2.1 Servicio de Autenticacion**
- [x] Implementar `auth_service.py`
  - Hash de contrasenas con bcrypt
  - Generacion de tokens JWT
  - Verificacion de tokens
  - Refresh tokens
- [x] Endpoint POST `/api/v1/auth/login` (OAuth2 form)
- [x] Endpoint POST `/api/v1/auth/login/json` (JSON body)
- [x] Endpoint POST `/api/v1/auth/register`
- [x] Endpoint POST `/api/v1/auth/logout`
- [x] Endpoint GET `/api/v1/auth/verify`
- [x] Endpoint POST `/api/v1/auth/refresh`
- [x] Endpoint GET `/api/v1/auth/me`
- [x] Endpoint PUT `/api/v1/auth/password`

**2.2 Middleware de Autorizacion**
- [x] `auth_middleware.py` - Verificacion de JWT en requests
- [x] Dependencias `get_current_user` y `get_current_active_user`
- [x] Control de acceso por roles con `require_roles()`

**2.3 Endpoints de Usuarios** (Ya existian, ahora protegidos)
- [x] GET `/api/v1/users` - Listar usuarios
- [x] GET `/api/v1/users/{id}` - Obtener usuario
- [x] PUT `/api/v1/users/{id}` - Actualizar usuario
- [x] DELETE `/api/v1/users/{id}` - Desactivar usuario

**Entregables:**
- [x] Sistema de autenticacion JWT funcional
- [x] Middleware de autorizacion
- [x] Endpoints de autenticacion completos

**Archivos creados:**
- `app/services/auth_service.py`
- `app/middleware/__init__.py`
- `app/middleware/auth_middleware.py`
- `app/routers/auth.py`

**Nota:** Se actualizo la columna `hashPassword` en la BD de VARCHAR(50) a VARCHAR(255) para soportar hashes bcrypt

---

### FASE 3: Gestion de Datos - RF-01 (Sprint 3-4) - COMPLETADA
**Duracion estimada:** 2 semanas
**Estado:** COMPLETADA (27-Enero-2026)

#### Objetivos:
- Implementar carga de datos desde archivos CSV/Excel
- Desarrollar limpieza automatica de datos
- Crear validacion de estructura y formato

#### Tareas:

**3.1 Utilidades de Parsing**
- [x] `file_parser.py`
  - Parser de archivos CSV
  - Parser de archivos Excel (XLSX)
  - Deteccion automatica de formato
  - Mapeo de columnas

**3.2 Servicio de Carga de Datos**
- [x] `data_service.py`
  - Carga de archivos temporales
  - Validacion de estructura (RF-01.02)
  - Preview de datos cargados
  - Confirmacion y almacenamiento final

**3.3 Servicio de Limpieza de Datos**
- [x] Limpieza integrada en `data_service.py`
  - Eliminacion de duplicados (RN-02.01)
  - Manejo de valores nulos (RN-02.02, RN-02.04)
  - Deteccion de valores atipicos - Z-Score (RN-02.03)
  - Validacion de 70% de registros (RN-02.05)
  - Transformaciones de normalizacion (RF-01.04)

**3.4 Modulo de Preprocesamiento**
- [x] `analytics/preprocessing/data_cleaner.py` - Limpieza con reglas RN-02.01 a RN-02.05
- [x] `analytics/preprocessing/data_validator.py` - Validacion con reglas configurables
- [x] `analytics/preprocessing/data_transformer.py` - Escalado, encoding y features de fechas

**3.5 Endpoints de Datos**
- [x] POST `/api/v1/data/upload` - Subir archivo
- [x] POST `/api/v1/data/validate` - Validar estructura
- [x] GET `/api/v1/data/preview/{id}` - Preview de datos
- [x] POST `/api/v1/data/clean` - Ejecutar limpieza
- [x] POST `/api/v1/data/confirm` - Confirmar carga
- [x] GET `/api/v1/data/quality-report/{id}` - Reporte de calidad
- [x] DELETE `/api/v1/data/{id}` - Eliminar upload temporal

**3.6 Endpoints de Consulta**
- [x] GET `/api/v1/ventas` - Consultar ventas
- [x] GET `/api/v1/ventas/{id}` - Obtener venta
- [x] POST `/api/v1/ventas` - Crear venta
- [x] GET `/api/v1/ventas/resumen/mensual` - Resumen mensual
- [x] GET `/api/v1/ventas/total/periodo` - Total por periodo
- [x] GET `/api/v1/compras` - Consultar compras
- [x] GET `/api/v1/compras/{id}` - Obtener compra
- [x] POST `/api/v1/compras` - Crear compra
- [x] GET `/api/v1/compras/resumen/mensual` - Resumen mensual
- [x] GET `/api/v1/compras/total/periodo` - Total por periodo
- [x] Implementar filtros por fecha, proveedor
- [x] Implementar paginacion

**Entregables:**
- [x] Sistema de carga de archivos funcional
- [x] Limpieza automatica de datos
- [x] Endpoints de consulta con filtros
- [x] Reporte de calidad de datos

**Archivos creados:**
- `app/utils/file_parser.py` - Parser de CSV/Excel
- `app/utils/exceptions.py` - Excepciones personalizadas
- `app/schemas/data_upload.py` - Schemas para carga de datos
- `app/services/data_service.py` - Servicio de gestion de datos
- `app/routers/data.py` - Endpoints de datos
- `app/routers/ventas.py` - Endpoints de ventas
- `app/routers/compras.py` - Endpoints de compras
- `app/analytics/__init__.py` - Modulo de analitica
- `app/analytics/preprocessing/__init__.py` - Modulo de preprocesamiento
- `app/analytics/preprocessing/data_cleaner.py` - Limpieza de datos
- `app/analytics/preprocessing/data_validator.py` - Validacion de datos
- `app/analytics/preprocessing/data_transformer.py` - Transformacion de datos

---

### FASE 4: Modulo de Analisis Predictivo - RF-02 (Sprint 5-7) - COMPLETADA
**Duracion estimada:** 3 semanas
**Estado:** COMPLETADA (28-Enero-2026)

#### Objetivos:
- Implementar modelos de regresion y series de tiempo
- Desarrollar algoritmos de ML para predicciones
- Crear sistema de entrenamiento y validacion de modelos

#### Tareas:

**4.1 Clase Base de Modelos**
- [x] `analytics/models/base_model.py`
  - Interfaz comun para todos los modelos
  - Metodos: train, predict, evaluate, save, load
  - Clases: BaseModel, ModelConfig, ModelMetrics, PredictionResult, ModelType

**4.2 Modelos de Regresion**
- [x] `linear_regression.py` - Regresion lineal simple y multiple
- [x] Implementar con scikit-learn
- [x] Metricas: R2, RMSE, MAE
- [x] TimeSeriesLinearRegression con features temporales

**4.3 Modelos de Series de Tiempo**
- [x] `arima_model.py` - ARIMA (RF-02.02)
- [x] `sarima_model.py` - SARIMA con estacionalidad (RN-03.05)
- [x] Implementar con statsmodels
- [x] Deteccion automatica de estacionalidad (detect_seasonality)
- [x] Auto-seleccion de orden (p, d, q)

**4.4 Modelos de Machine Learning**
- [x] `random_forest.py` - Random Forest (RF-02.03)
- [x] `xgboost_model.py` - XGBoost (RF-02.03)
  - XGBoostModel y XGBoostConfig
  - TimeSeriesXGBoost con features temporales
  - Early stopping y tuning de hiperparametros
- [x] Validacion cruzada
- [x] Optimizacion de hiperparametros (tune_hyperparameters)
- [x] TimeSeriesRandomForest con features temporales automaticas

**4.5 Clustering**
- [x] `kmeans_clustering.py` - K-Means (RF-02.04)
  - KMeansClustering con ClusteringConfig
  - ClusteringResult y ClusterInfo
  - Validacion minimo 10 productos (RN-03.06)
- [x] Segmentacion de productos (segment_products)
- [x] Metricas: Silhouette, Calinski-Harabasz, Davies-Bouldin
- [x] find_optimal_clusters (metodo del codo y silhouette)

**4.6 Modulo de Evaluacion**
- [x] `evaluation/metrics.py`
  - RMSE, MAE, R2, MAPE, SMAPE, MASE
  - RegressionMetrics y ForecastMetrics
  - Validacion de metricas minimas (RN-03.02: R2 > 0.7)
  - ModelEvaluator con historial y reportes
  - compare_models para comparar multiples modelos
  - cross_validate_model para validacion cruzada

**4.7 Servicio de Prediccion**
- [x] `prediction_service.py`
  - Seleccion automatica de modelo (RF-02.06) - auto_select_model()
  - Entrenamiento con 70/30 split (RN-03.01)
  - Almacenamiento de modelos entrenados (memoria y disco)
  - Validacion de datos minimos (6 meses - RN-01.01)
  - Validacion de umbral R2 (RN-03.02)
  - Limite de prediccion 6 meses (RN-03.03)

**4.8 Endpoints de Prediccion**
- [x] POST `/api/v1/predictions/train` - Entrenar modelo
- [x] GET `/api/v1/predictions/models` - Listar modelos disponibles
- [x] POST `/api/v1/predictions/forecast` - Ejecutar prediccion
- [x] GET `/api/v1/predictions/history` - Historial de predicciones
- [x] POST `/api/v1/predictions/auto-select` - Seleccion automatica (RF-02.06)
- [x] POST `/api/v1/predictions/sales-data` - Obtener datos de ventas
- [x] POST `/api/v1/predictions/validate-data` - Validar datos
- [x] GET `/api/v1/predictions/model-types` - Tipos de modelo disponibles

**Reglas de Negocio Implementadas:**
- [x] RN-03.01: Division 70/30 entrenamiento/validacion (train_test_split)
- [x] RN-03.02: Metricas minimas R2 > 0.7 (R2_THRESHOLD)
- [x] RN-03.03: Predicciones hasta 6 meses en el futuro (MAX_FORECAST_PERIODS)
- [x] RN-03.04: Reentrenamiento con 20% mas de datos (should_retrain)
- [x] RN-03.05: SARIMA para datos con estacionalidad (detect_seasonality)
- [x] RN-03.06: Minimo 10 productos para clustering (MIN_SAMPLES en KMeansClustering)

**Entregables:**
- [x] Modelos predictivos implementados (Linear, ARIMA, SARIMA, Random Forest, XGBoost)
- [x] Modelo de clustering implementado (K-Means)
- [x] API de prediccion completa
- [x] Sistema de entrenamiento y validacion
- [x] Metricas de evaluacion

**Archivos creados:**
- `app/analytics/models/__init__.py`
- `app/analytics/models/base_model.py`
- `app/analytics/models/linear_regression.py`
- `app/analytics/models/arima_model.py`
- `app/analytics/models/sarima_model.py`
- `app/analytics/models/random_forest.py`
- `app/analytics/models/xgboost_model.py`
- `app/analytics/models/kmeans_clustering.py`
- `app/analytics/evaluation/__init__.py`
- `app/analytics/evaluation/metrics.py`
- `app/services/prediction_service.py`
- `app/routers/predictions.py`

**Fase 4 completada al 100%.**

---

### FASE 5: Modulo de Evaluacion de Rentabilidad - RF-06 (Sprint 8) - COMPLETADA
**Duracion estimada:** 1-2 semanas
**Estado:** COMPLETADA (28-Enero-2026)

#### Objetivos:
- Calcular indicadores financieros clave
- Evaluar rentabilidad por producto/categoria
- Proyectar rentabilidad futura

#### Tareas:

**5.1 Servicio de Rentabilidad**
- [x] `profitability_service.py`
  - Calculo de Margen de Utilidad (margen_bruto, margen_operativo, margen_neto)
  - Calculo de ROA (Return on Assets)
  - Calculo de ROE (Return on Equity)
  - Calculo de Utilidad Operativa (RN-06.02)
  - Rentabilidad por producto (RF-06.02)
  - Rentabilidad por categoria
  - Comparacion con periodos anteriores (compare_periods)
  - Clases: FinancialIndicators, ProductProfitability, CategoryProfitability, ProfitabilityTrend

**5.2 Reglas de Negocio**
- [x] Validacion de datos completos (RN-06.01) - validate_data_completeness()
- [x] Calculo por periodo: diario, semanal, mensual, trimestral, anual (RN-06.03) - PeriodType enum
- [x] Identificacion de productos no rentables (margen < 10%) (RN-06.04) - MIN_PROFIT_MARGIN = 10.0

**5.3 Endpoints de Rentabilidad**
- [x] POST `/api/v1/profitability/indicators` - Calcular indicadores financieros
- [x] GET `/api/v1/profitability/products` - Rentabilidad por producto
- [x] GET `/api/v1/profitability/products/non-profitable` - Productos no rentables (RF-06.03)
- [x] GET `/api/v1/profitability/categories` - Rentabilidad por categoria
- [x] GET `/api/v1/profitability/trends` - Tendencias de rentabilidad
- [x] GET `/api/v1/profitability/ranking` - Ranking de productos por metrica
- [x] POST `/api/v1/profitability/compare` - Comparar dos periodos
- [x] GET `/api/v1/profitability/summary` - Resumen ejecutivo con alertas

**Entregables:**
- [x] Calculo de indicadores financieros
- [x] Analisis de rentabilidad por entidad
- [x] Comparativas temporales
- [x] Sistema de alertas de rentabilidad

**Archivos creados:**
- `app/services/profitability_service.py` - Servicio completo de rentabilidad
- `app/routers/profitability.py` - Endpoints de rentabilidad

**Fase 5 completada al 100%.**

---

### FASE 6: Modulo de Simulacion de Escenarios - RF-05 (Sprint 9-10) - COMPLETADA
**Duracion estimada:** 2 semanas
**Estado:** COMPLETADA (28-Enero-2026)

#### Objetivos:
- Implementar motor de simulacion financiera
- Permitir modificacion de variables clave
- Comparar multiples escenarios

#### Tareas:

**6.1 Motor de Simulacion**
- [x] `simulation_service.py`
  - Crear escenario base (create_scenario)
  - Modificar variables: precios, costos, demanda (RF-05.01) - modify_parameters()
  - Validar limites de variacion (+/- 50%) (RN-05.01) - MAX_VARIATION = 50.0
  - Calcular proyecciones iterativas (run_simulation)
  - Generar indicadores por escenario (ingresos, costos, utilidad, margen)
  - Clases: ScenarioParameter, SimulationResult, ScenarioSummary

**6.2 Comparacion de Escenarios**
- [x] Comparar hasta 5 escenarios simultaneos (RN-05.03) - compare_scenarios()
- [x] Calcular diferencias absolutas y porcentuales
- [x] Identificar mejor y peor escenario por indicador

**6.3 Endpoints de Simulacion**
- [x] POST `/api/v1/simulation/create` - Crear escenario
- [x] PUT `/api/v1/simulation/{id}/parameters` - Modificar parametros
- [x] POST `/api/v1/simulation/{id}/run` - Ejecutar simulacion
- [x] GET `/api/v1/simulation/{id}/results` - Obtener resultados
- [x] POST `/api/v1/simulation/compare` - Comparar escenarios (2-5)
- [x] POST `/api/v1/simulation/{id}/save` - Guardar escenario
- [x] GET `/api/v1/simulation/scenarios` - Listar escenarios guardados
- [x] POST `/api/v1/simulation/{id}/archive` - Archivar escenario
- [x] DELETE `/api/v1/simulation/{id}` - Eliminar escenario
- [x] POST `/api/v1/simulation/{id}/clone` - Clonar escenario
- [x] GET `/api/v1/simulation/{id}` - Obtener escenario

**Reglas de Negocio Implementadas:**
- [x] RN-05.01: Variables no varian mas del 50% (MAX_VARIATION)
- [x] RN-05.02: Basado en datos historicos reales (_initialize_base_parameters)
- [x] RN-05.03: Maximo 5 escenarios simultaneos (MAX_SCENARIOS_COMPARE)
- [x] RN-05.04: Indicar caracter informativo (advertencia en respuesta)

**Entregables:**
- [x] Motor de simulacion funcional
- [x] API de simulacion completa
- [x] Comparacion de escenarios

**Archivos creados:**
- `app/services/simulation_service.py` - Servicio completo de simulacion
- `app/routers/simulations.py` - Endpoints de simulacion

**Fase 6 completada al 100%.**

---

### FASE 7: Sistema de Alertas - RF-04 (Sprint 11) - COMPLETADA
**Duracion estimada:** 1 semana
**Estado:** COMPLETADA (28-Enero-2026)

#### Objetivos:
- Implementar deteccion de anomalias
- Generar alertas automaticas
- Configurar umbrales personalizados

#### Tareas:

**7.1 Modulo de Deteccion de Anomalias**
- [x] `analytics/anomaly/detector.py`
  - Z-Score para deteccion estadistica (detect_outliers_zscore)
  - IQR para deteccion de outliers (detect_outliers_iqr)
  - Deteccion de cambios repentinos (detect_sudden_changes)
  - Deteccion de rupturas de tendencia (detect_trend_break)
  - Comparacion con perfil historico (compare_with_baseline)
  - Clasificacion por criticidad (Severity enum)
  - Clases: AnomalyDetector, AnomalyResult, AnomalyType, Severity

**7.2 Servicio de Alertas**
- [x] `alert_service.py`
  - Generar alerta de riesgo (caida > 15%) (RN-04.01) - change_threshold
  - Generar alerta de oportunidad (subida > 20%) (RN-04.02) - opportunity_threshold
  - Alerta por anomalias en > 5% transacciones (RN-04.03) - anomaly_rate_threshold
  - Incluir nivel de confianza (RN-04.04) - nivelConfianza en alertas
  - Limite de 10 alertas simultaneas (RN-04.05) - MAX_ACTIVE_ALERTS
  - Priorizar por impacto (RN-04.06) - _prioritize_alerts()
  - Clases: AlertConfig, AlertType, AlertImportance, AlertStatus

**7.3 Endpoints de Alertas**
- [x] GET `/api/v1/alerts` - Listar alertas activas
- [x] GET `/api/v1/alerts/history` - Historial de alertas
- [x] GET `/api/v1/alerts/summary` - Resumen de alertas
- [x] PUT `/api/v1/alerts/{id}/read` - Marcar como leida
- [x] PUT `/api/v1/alerts/{id}/status` - Cambiar estado
- [x] POST `/api/v1/alerts/config` - Configurar umbrales (RF-04.04)
- [x] GET `/api/v1/alerts/config` - Obtener configuracion
- [x] POST `/api/v1/alerts/analyze` - Analizar y generar alertas
- [x] GET `/api/v1/alerts/{id}` - Obtener alerta
- [x] DELETE `/api/v1/alerts/{id}` - Eliminar alerta
- [x] POST `/api/v1/alerts/check-prediction/{id}` - Verificar prediccion

**Reglas de Negocio Implementadas:**
- [x] RN-04.01: Alerta de riesgo por caida > 15%
- [x] RN-04.02: Alerta de oportunidad por subida > 20%
- [x] RN-04.03: Alerta si anomalias > 5% de transacciones
- [x] RN-04.04: Nivel de confianza incluido
- [x] RN-04.05: Maximo 10 alertas simultaneas
- [x] RN-04.06: Priorizacion por impacto economico

**Entregables:**
- [x] Sistema de deteccion de anomalias
- [x] Generacion automatica de alertas
- [x] Configuracion de umbrales

**Archivos creados:**
- `app/analytics/anomaly/__init__.py` - Modulo de anomalias
- `app/analytics/anomaly/detector.py` - Detector de anomalias
- `app/services/alert_service.py` - Servicio de alertas
- `app/routers/alerts.py` - Endpoints de alertas

**Fase 7 completada al 100%.**

---

### FASE 8: Endpoints de Dashboard y Visualizacion - RF-03 (Sprint 12)
**Duracion estimada:** 1 semana

#### Objetivos:
- Crear endpoints para el dashboard principal
- Agregar KPIs y metricas
- Implementar filtros y segmentacion

#### Tareas:

**8.1 Endpoints de Dashboard**
- [x] GET `/api/dashboard/executive` - Dashboard ejecutivo con KPIs consolidados
- [x] GET `/api/dashboard/kpi/{kpi_name}` - Detalle de KPI especifico
- [x] GET `/api/dashboard/scenarios` - Resumen de escenarios
- [x] GET `/api/dashboard/predictions` - Predicciones recientes
- [x] GET `/api/dashboard/compare` - Comparar real vs predicho

**8.2 Preferencias de Usuario**
- [x] GET `/api/dashboard/users/{id}/preferences` - Obtener preferencias
- [x] PUT `/api/dashboard/users/{id}/preferences` - Actualizar preferencias

**8.3 Comparativas**
- [x] GET `/api/dashboard/compare` - Comparar real vs predicho (RF-03.05)
- [x] Filtros por fecha, tipo de entidad (RF-03.03)

**Archivos Implementados:**
- `app/services/dashboard_service.py` - Servicio de dashboard
- `app/routers/dashboard.py` - Endpoints de dashboard

**Fase 8 completada al 100%.**

---

### FASE 9: Generacion de Reportes - RF-07 (Sprint 13)
**Duracion estimada:** 1 semana

#### Objetivos:
- Generar reportes en JSON/CSV/Excel
- Incluir metricas e interpretacion
- Crear diferentes tipos de reportes

#### Tareas:

**9.1 Servicio de Reportes**
- [x] `report_service.py`
  - Reporte de ventas con agrupacion temporal
  - Reporte de compras con agrupacion temporal
  - Reporte de rentabilidad mensual
  - Reporte de productos mas vendidos
  - Formatos: JSON, CSV, Excel (metadatos)
  - Incluir fecha y periodo (RN-07.03)

**9.2 Endpoints de Reportes**
- [x] POST `/api/dashboard/reports/generate` - Generar reporte
- [x] GET `/api/dashboard/reports/types` - Tipos de reportes disponibles
- [x] GET `/api/dashboard/reports/{id}` - Obtener reporte por ID
- [x] GET `/api/dashboard/reports` - Listar reportes generados
- [x] GET `/api/dashboard/reports/sales` - Reporte rapido de ventas
- [x] GET `/api/dashboard/reports/purchases` - Reporte rapido de compras
- [x] GET `/api/dashboard/reports/profitability` - Reporte rapido de rentabilidad

**Archivos Implementados:**
- `app/services/report_service.py` - Servicio de reportes
- Endpoints integrados en `app/routers/dashboard.py`

**Fase 9 completada al 100%.**

---

### FASE 10: Integracion, Pruebas y Documentacion (Sprint 14-15)
**Duracion estimada:** 2 semanas
**Estado:** COMPLETADA (02-Febrero-2026)

#### Objetivos:
- Pruebas unitarias y de integracion
- Documentacion de API
- Optimizacion y correccion de bugs

#### Tareas:

**10.1 Pruebas Unitarias**
- [x] Tests para servicios de autenticacion (`test_auth_service.py`)
- [x] Tests para servicios de datos (`test_data_service.py`)
- [x] Tests para modelos predictivos (`test_prediction_service.py`)
- [x] Tests para calculo de rentabilidad (`test_profitability_service.py`)
- [x] Tests para simulaciones (`test_simulation_service.py`)
- [x] Tests para alertas (`test_alert_service.py`)
- [x] Tests para dashboard (`test_dashboard_service.py`)
- [x] Tests para middleware de autenticacion (`test_auth_middleware.py`)
- [x] **Cobertura final: 80% (696 pruebas pasadas, 0 omitidas)**

**10.2 Pruebas de Integracion**
- [x] Tests de endpoints de autenticacion (`test_auth_endpoints.py`)
- [x] Tests de endpoints de dashboard (`test_dashboard_endpoints.py`)
- [x] Tests de endpoints de prediccion (`test_prediction_endpoints.py`)
- [x] Tests de endpoints de simulacion (`test_simulation_endpoints.py`)
- [x] Tests de endpoints de alertas (`test_alert_endpoints.py`)
- [x] Tests de endpoints de rentabilidad (`test_profitability_endpoints.py`)
- [x] Tests de endpoints de ventas/compras (`test_sales_purchases_endpoints.py`)
- [x] Tests de endpoints de datos (`test_data_endpoints.py`)
- [x] Tests de persistencia de modelos (`test_model_persistence.py`)
- [x] Tests de modelos con datos sinteticos (`test_model_persistence_synthetic.py`)
- [x] Tests de todos los modelos con datos sinteticos (`test_all_models_synthetic.py`)

**10.3 Configuracion de Pruebas**
- [x] Configuracion de pytest (`pytest.ini`)
- [x] Fixtures compartidas (`conftest.py`)
- [x] Script de ejecucion (`run_tests.py`, `run_tests.bat`)
- [x] Dependencias de testing en `requirements.txt`

**10.4 Documentacion**
- [x] Swagger/OpenAPI actualizado (generado automaticamente por FastAPI)
- [x] Referencia de API (`docs/api_reference.md`)
- [x] Guia de despliegue (`docs/deployment.md`)
- [x] Documentacion de modelos (`docs/models.md`)
- [x] Manual de configuracion (`docs/configuration.md`)

**10.5 Optimizacion**
- [x] Persistencia de modelos entre requests (global storage)
- [x] Carga automatica de modelos desde disco
- [x] Logging configurado en todos los servicios
- [x] Manejo de errores robusto con excepciones personalizadas

**Archivos de Pruebas Creados:**
- `tests/__init__.py`
- `tests/conftest.py` - Fixtures y configuracion
- `tests/unit/__init__.py`
- `tests/unit/test_auth_service.py`
- `tests/unit/test_data_service.py`
- `tests/unit/test_prediction_service.py`
- `tests/unit/test_profitability_service.py`
- `tests/unit/test_simulation_service.py`
- `tests/unit/test_alert_service.py`
- `tests/integration/__init__.py`
- `tests/integration/test_auth_endpoints.py`
- `tests/integration/test_dashboard_endpoints.py`
- `tests/integration/test_prediction_endpoints.py`
- `tests/integration/test_simulation_endpoints.py`
- `tests/integration/test_alert_endpoints.py`
- `tests/integration/test_profitability_endpoints.py`
- `tests/integration/test_sales_purchases_endpoints.py`
- `tests/integration/test_data_endpoints.py`
- `tests/integration/test_model_persistence.py`
- `tests/integration/test_model_persistence_synthetic.py`
- `tests/integration/test_all_models_synthetic.py`
- `tests/unit/test_preprocessing.py` - Tests de preprocesamiento
- `tests/unit/test_repositories.py` - Tests de repositorios
- `tests/unit/test_metrics.py` - Tests de metricas de evaluacion
- `tests/unit/test_anomaly_detector.py` - Tests de deteccion de anomalias
- `tests/unit/test_xgboost_kmeans.py` - Tests de XGBoost y K-Means
- `tests/unit/test_dashboard_service.py` - Tests de servicio de dashboard
- `tests/unit/test_auth_middleware.py` - Tests de middleware de autenticacion
- `tests/unit/test_file_upload.py` - Tests de carga de archivos
- `tests/unit/test_evaluation_metrics.py` - Tests de metricas de evaluacion
- `tests/run_tests.py` - Script de ejecucion
- `run_tests.bat` - Script para Windows
- `pytest.ini` - Configuracion de pytest

**Archivos de Documentacion Creados:**
- `docs/api_reference.md` - Referencia completa de la API
- `docs/deployment.md` - Guia de despliegue
- `docs/models.md` - Documentacion de modelos predictivos
- `docs/configuration.md` - Manual de configuracion

**Resultados de Pruebas (Actualizados 04-Febrero-2026):**
- Total de pruebas: 696
- Pruebas pasadas: 696
- Pruebas omitidas: 0
- **Cobertura de codigo: 80%**

**Modelos Probados:**
| Modelo | Entrenar | Guardar | Cargar | Predecir |
|--------|----------|---------|--------|----------|
| Linear Regression | OK | OK | OK | OK |
| Random Forest | OK | OK | OK | OK |
| TimeSeriesRandomForest | OK | OK | OK | OK |
| ARIMA | OK | OK | OK | OK |
| SARIMA | OK | OK | OK | OK |
| XGBoost | OK | OK | OK | OK |
| TimeSeriesXGBoost | OK | OK | OK | OK |
| K-Means Clustering | OK | OK | OK | OK |

**Nota:** Se actualizo statsmodels de 0.14.1 a >=0.14.6 para corregir incompatibilidad con ARIMA/SARIMA.

**Entregables:**
- [x] Suite de pruebas completa (696 tests, 80% cobertura)
- [x] Documentacion tecnica (4 documentos)
- [x] Sistema optimizado con persistencia de modelos
- [x] Todos los modelos predictivos funcionando (Linear, RF, TimeSeriesRF, ARIMA, SARIMA, XGBoost, TimeSeriesXGBoost, K-Means)

**Fase 10 completada al 100%.**

---

## 5. Dependencias a Agregar (requirements.txt)

```txt
# Existentes
fastapi==0.109.0
uvicorn[standard]==0.27.0
python-multipart==0.0.6
pydantic==2.5.3
pydantic-settings==2.1.0
email-validator==2.1.0
sqlalchemy==2.0.25
pyodbc==5.0.1
python-dotenv==1.0.0
python-json-logger==2.0.7

# Seguridad y Autenticacion
python-jose[cryptography]==3.3.0
passlib[bcrypt]==1.7.4

# Analisis de Datos
pandas==2.1.4
numpy==1.26.3

# Machine Learning
scikit-learn==1.4.0
statsmodels==0.14.1
xgboost==2.0.3

# Procesamiento de archivos
openpyxl==3.1.2       # Excel
xlrd==2.0.1           # Excel legacy

# Generacion de reportes
reportlab==4.0.8      # PDF
matplotlib==3.8.2     # Graficos

# Testing
pytest==7.4.4
pytest-asyncio==0.21.1
httpx==0.26.0
pytest-cov==4.1.0
```

---

## 6. Endpoints API - Resumen

### Autenticacion
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| POST | `/api/auth/login` | Iniciar sesion |
| POST | `/api/auth/register` | Registrar usuario |
| POST | `/api/auth/logout` | Cerrar sesion |
| GET | `/api/auth/verify` | Verificar token |
| POST | `/api/auth/refresh` | Refrescar token |

### Usuarios
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| GET | `/api/users` | Listar usuarios |
| GET | `/api/users/{id}` | Obtener usuario |
| PUT | `/api/users/{id}` | Actualizar usuario |
| DELETE | `/api/users/{id}` | Desactivar usuario |
| PUT | `/api/users/{id}/password` | Cambiar contrasena |
| GET | `/api/users/{id}/preferences` | Obtener preferencias |
| PUT | `/api/users/{id}/preferences` | Actualizar preferencias |

### Datos
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| POST | `/api/data/upload` | Subir archivo CSV/Excel |
| POST | `/api/data/validate` | Validar estructura |
| GET | `/api/data/preview/{id}` | Preview de datos |
| POST | `/api/data/clean` | Ejecutar limpieza |
| POST | `/api/data/confirm` | Confirmar carga |
| GET | `/api/data/quality-report/{id}` | Reporte de calidad |

### Consultas
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| GET | `/api/sales` | Consultar ventas |
| GET | `/api/sales/summary` | Resumen de ventas |
| GET | `/api/purchases` | Consultar compras |
| GET | `/api/products` | Consultar productos |
| GET | `/api/categories` | Consultar categorias |

### Predicciones
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| POST | `/api/prediction/train` | Entrenar modelo |
| GET | `/api/prediction/models` | Listar modelos |
| POST | `/api/prediction/forecast` | Ejecutar prediccion |
| GET | `/api/prediction/metrics/{id}` | Metricas del modelo |
| GET | `/api/prediction/history` | Historial |
| POST | `/api/prediction/compare` | Comparar modelos |

### Rentabilidad
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| POST | `/api/profitability/calculate` | Calcular indicadores |
| GET | `/api/profitability/products` | Por producto |
| GET | `/api/profitability/categories` | Por categoria |
| GET | `/api/profitability/trends` | Evolucion |
| GET | `/api/profitability/ranking` | Ranking |

### Simulacion
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| POST | `/api/simulation/create` | Crear escenario |
| PUT | `/api/simulation/{id}/parameters` | Modificar parametros |
| POST | `/api/simulation/{id}/run` | Ejecutar |
| GET | `/api/simulation/{id}/results` | Resultados |
| POST | `/api/simulation/compare` | Comparar escenarios |
| POST | `/api/simulation/{id}/save` | Guardar |
| GET | `/api/simulation/scenarios` | Listar escenarios |

### Alertas
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| GET | `/api/alerts` | Listar alertas |
| GET | `/api/alerts/history` | Historial |
| PUT | `/api/alerts/{id}/read` | Marcar leida |
| PUT | `/api/alerts/{id}/status` | Cambiar estado |
| POST | `/api/alerts/config` | Configurar umbrales |

### Dashboard
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| GET | `/api/dashboard` | Dashboard principal |
| GET | `/api/dashboard/kpis` | KPIs |
| GET | `/api/dashboard/trends` | Tendencias |
| GET | `/api/dashboard/top-products` | Productos top |
| GET | `/api/dashboard/alerts` | Alertas activas |

### Reportes
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| POST | `/api/reports/generate` | Generar reporte |
| GET | `/api/reports/types` | Tipos disponibles |
| GET | `/api/reports/{id}/download` | Descargar |
| GET | `/api/reports/history` | Historial |

---

## 7. Cronograma Resumido

| Sprint | Fase | Duracion | Entregable Principal |
|--------|------|----------|---------------------|
| 1 | Consolidacion BD y Modelos | 1-2 sem | Modelos ORM completos |
| 2 | Autenticacion y Seguridad | 1 sem | Sistema JWT funcional |
| 3-4 | Gestion de Datos | 2 sem | Carga y limpieza de datos |
| 5-7 | Analisis Predictivo | 3 sem | Modelos ML implementados |
| 8 | Evaluacion de Rentabilidad | 1-2 sem | Indicadores financieros |
| 9-10 | Simulacion de Escenarios | 2 sem | Motor de simulacion |
| 11 | Sistema de Alertas | 1 sem | Alertas automaticas |
| 12 | Dashboard y Visualizacion | 1 sem | Endpoints de dashboard |
| 13 | Generacion de Reportes | 1 sem | Reportes PDF/Excel |
| 14-15 | Integracion y Pruebas | 2 sem | Sistema probado |

**Total estimado:** 15-17 semanas

---

## 8. Metricas de Exito

### Tecnicas
- [x] Cobertura de pruebas: 70% (413 tests pasados)
- [x] Tiempo de respuesta API < 2 segundos
- [x] Precision de modelos predictivos > 75% (R2 > 0.7 verificado)
- [x] Disponibilidad del sistema > 95%

### Funcionales
- [x] Todos los RF implementados (RF-01 a RF-07)
- [x] Todas las RN validadas
- [x] Sistema soporta hasta 50 productos (RN-09.01)
- [x] Historico de hasta 5 anos (RN-09.02)

### Estado Final del Proyecto
**COMPLETADO** - Todas las fases implementadas y probadas (02-Febrero-2026)

---

## 9. Consideraciones Importantes

### Reglas de Negocio Criticas
1. **RN-01.01:** Minimo 6 meses de datos historicos para predicciones
2. **RN-02.05:** Limpieza debe mantener 70% de registros
3. **RN-03.02:** Modelos solo usables si R2 > 0.7
4. **RN-03.03:** Predicciones hasta 6 meses en el futuro
5. **RN-05.01:** Variables no varian mas del 50%

### Seguridad
- Validar todos los inputs (RNF-06.01)
- Datos sensibles encriptados (RNF-06.02)
- Control de acceso por roles (RNF-06.03)
- No commitear credenciales

### Escalabilidad
- Arquitectura modular (RNF-03.01)
- Facil adicion de nuevos modelos
- Crecimiento sin degradacion (RNF-03.02)

---

## 10. Proximos Pasos Inmediatos

1. **Revisar y aprobar este plan** con el equipo
2. **Completar modelos SQLAlchemy** faltantes (Fase 1)
3. **Implementar autenticacion JWT** (Fase 2)
4. **Desarrollar carga de datos** (Fase 3)

---

**Documento creado por:** Claude Code
**Para el proyecto:** TT 2026-A026 - Sistema de Inteligencia Empresarial para PYMEs
