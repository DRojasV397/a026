# Parámetros de Modelos Predictivos

Este documento lista todos los parámetros configurables para cada modelo de análisis predictivo implementado en el sistema.

---

## 1. Linear Regression (Regresión Lineal)

**Archivo**: [analytics-modules/api/app/analytics/models/linear_regression.py](analytics-modules/api/app/analytics/models/linear_regression.py)

**Descripción**: Modelo de regresión lineal con soporte para regularización y características polinomiales.

### Parámetros

| Parámetro | Tipo | Valores | Default | Descripción |
|-----------|------|---------|---------|-------------|
| `regularization` | str | "none", "ridge", "lasso", "elasticnet" | "none" | Tipo de regularización aplicada |
| `alpha` | float | > 0 | 1.0 | Parámetro de regularización (fuerza de penalización) |
| `l1_ratio` | float | 0.0 - 1.0 | 0.5 | Proporción ElasticNet (solo para elasticnet) |
| `polynomial_degree` | int | 1 - 5 | 1 | Grado de características polinomiales |
| `normalize` | bool | true/false | false | Normalizar características antes del ajuste |

### Ejemplo de Configuración

```json
{
  "modelo": "linear_regression",
  "parametros": {
    "regularization": "ridge",
    "alpha": 0.1,
    "polynomial_degree": 2,
    "normalize": true
  }
}
```

### Notas

- **regularization="none"**: Regresión lineal clásica sin penalización
- **regularization="ridge"**: Penalización L2, reduce overfitting
- **regularization="lasso"**: Penalización L1, puede forzar coeficientes a cero (selección de features)
- **regularization="elasticnet"**: Combinación de L1 y L2, controlada por `l1_ratio`
- **polynomial_degree**: Aumenta complejidad del modelo, útil para relaciones no lineales
- **alpha**: Valores más altos = más regularización = modelo más simple

---

## 2. ARIMA (AutoRegressive Integrated Moving Average)

**Archivo**: [analytics-modules/api/app/analytics/models/arima_model.py](analytics-modules/api/app/analytics/models/arima_model.py)

**Descripción**: Modelo ARIMA para series temporales con detección automática de orden.

### Parámetros

| Parámetro | Tipo | Valores | Default | Descripción |
|-----------|------|---------|---------|-------------|
| `order` | tuple | (p, d, q) | (1, 1, 1) | Orden del modelo ARIMA |
| `auto_order` | bool | true/false | false | Búsqueda automática del mejor orden |
| `max_p` | int | 1 - 5 | 3 | Máximo orden AR (auto_order=true) |
| `max_d` | int | 0 - 2 | 2 | Máximo orden de diferenciación |
| `max_q` | int | 1 - 5 | 3 | Máximo orden MA (auto_order=true) |
| `information_criterion` | str | "aic", "bic" | "aic" | Criterio de selección de modelo |

### Componentes del Order (p, d, q)

- **p**: Orden autorregresivo (AR) - número de lags de la variable
- **d**: Grado de diferenciación (I) - número de diferencias para hacer estacionaria la serie
- **q**: Orden de media móvil (MA) - número de lags de errores de predicción

### Ejemplo de Configuración Manual

```json
{
  "modelo": "arima",
  "parametros": {
    "order": [2, 1, 1],
    "auto_order": false
  }
}
```

### Ejemplo de Configuración Automática

```json
{
  "modelo": "arima",
  "parametros": {
    "auto_order": true,
    "max_p": 5,
    "max_d": 2,
    "max_q": 5,
    "information_criterion": "aic"
  }
}
```

### Notas

- **auto_order=true**: El modelo busca la mejor combinación (p,d,q) dentro de los límites especificados
- **information_criterion**:
  - "aic" (Akaike): Prefiere modelos más complejos
  - "bic" (Bayesian): Penaliza más la complejidad
- **d=0**: Serie ya estacionaria
- **d=1**: Una diferencia (tendencia lineal)
- **d=2**: Dos diferencias (tendencia cuadrática)

---

## 3. SARIMA (Seasonal ARIMA)

**Archivo**: [analytics-modules/api/app/analytics/models/sarima_model.py](analytics-modules/api/app/analytics/models/sarima_model.py)

**Descripción**: Extensión de ARIMA con componentes estacionales para series con patrones periódicos.

### Parámetros

| Parámetro | Tipo | Valores | Default | Descripción |
|-----------|------|---------|---------|-------------|
| `order` | tuple | (p, d, q) | (1, 1, 1) | Orden no estacional del modelo |
| `seasonal_order` | tuple | (P, D, Q, s) | (1, 1, 1, 12) | Orden estacional del modelo |
| `auto_order` | bool | true/false | false | Búsqueda automática de órdenes |
| `seasonal_period` | int | 1 - 365 | 12 | Periodo estacional (meses/días) |
| `max_p` | int | 1 - 3 | 2 | Máximo orden AR no estacional |
| `max_d` | int | 0 - 2 | 1 | Máximo diferenciación no estacional |
| `max_q` | int | 1 - 3 | 2 | Máximo orden MA no estacional |
| `max_P` | int | 1 - 2 | 1 | Máximo orden AR estacional |
| `max_D` | int | 0 - 1 | 1 | Máximo diferenciación estacional |
| `max_Q` | int | 1 - 2 | 1 | Máximo orden MA estacional |
| `information_criterion` | str | "aic", "bic" | "aic" | Criterio de selección |

### Componentes del Seasonal Order (P, D, Q, s)

- **P**: Orden autorregresivo estacional
- **D**: Diferenciación estacional
- **Q**: Orden de media móvil estacional
- **s**: Periodo de estacionalidad (12=mensual, 7=semanal, 365=anual)

### Ejemplo de Configuración Manual (Datos Mensuales)

```json
{
  "modelo": "sarima",
  "parametros": {
    "order": [1, 1, 1],
    "seasonal_order": [1, 1, 1, 12],
    "auto_order": false
  }
}
```

### Ejemplo de Configuración Automática (Datos Diarios)

```json
{
  "modelo": "sarima",
  "parametros": {
    "auto_order": true,
    "seasonal_period": 7,
    "max_p": 3,
    "max_d": 1,
    "max_q": 3,
    "max_P": 2,
    "max_D": 1,
    "max_Q": 2,
    "information_criterion": "bic"
  }
}
```

### Periodos Estacionales Comunes

| Tipo de Dato | seasonal_period | Descripción |
|--------------|-----------------|-------------|
| Ventas diarias | 7 | Patrón semanal |
| Ventas diarias | 365 | Patrón anual |
| Ventas mensuales | 12 | Patrón anual |
| Ventas trimestrales | 4 | Patrón anual |

### Notas

- SARIMA es ideal para datos con patrones repetitivos (ventas navideñas, fin de semana, etc.)
- **seasonal_period** debe coincidir con la frecuencia real de los datos
- La búsqueda automática (`auto_order=true`) puede ser computacionalmente costosa

---

## 4. Random Forest (Bosque Aleatorio)

**Archivo**: [analytics-modules/api/app/analytics/models/random_forest.py](analytics-modules/api/app/analytics/models/random_forest.py)

**Descripción**: Ensemble de árboles de decisión para regresión con validación cruzada.

### Parámetros

| Parámetro | Tipo | Valores | Default | Descripción |
|-----------|------|---------|---------|-------------|
| `n_estimators` | int | 10 - 1000 | 100 | Número de árboles en el bosque |
| `max_depth` | int | 1 - 50 o null | null | Profundidad máxima de cada árbol |
| `min_samples_split` | int | 2 - 20 | 2 | Mínimo de muestras para dividir un nodo |
| `min_samples_leaf` | int | 1 - 10 | 1 | Mínimo de muestras en una hoja |
| `max_features` | str/float | "sqrt", "log2", float | "sqrt" | Número de features por split |
| `bootstrap` | bool | true/false | true | Usar bootstrap para muestras de entrenamiento |
| `oob_score` | bool | true/false | false | Calcular out-of-bag score |
| `n_jobs` | int | -1 a N | -1 | Núcleos CPU para paralelización (-1=todos) |
| `random_state` | int | cualquiera | 42 | Semilla para reproducibilidad |

### Ejemplo de Configuración Básica

```json
{
  "modelo": "random_forest",
  "parametros": {
    "n_estimators": 100,
    "max_depth": 10,
    "min_samples_split": 5,
    "random_state": 42
  }
}
```

### Ejemplo de Configuración Optimizada

```json
{
  "modelo": "random_forest",
  "parametros": {
    "n_estimators": 200,
    "max_depth": 15,
    "min_samples_split": 10,
    "min_samples_leaf": 4,
    "max_features": "sqrt",
    "bootstrap": true,
    "oob_score": true,
    "n_jobs": -1
  }
}
```

### Ajuste de Hiperparámetros

El modelo incluye búsqueda automática de hiperparámetros mediante GridSearchCV:

```python
param_grid = {
    'n_estimators': [50, 100, 200],
    'max_depth': [None, 10, 20, 30],
    'min_samples_split': [2, 5, 10],
    'min_samples_leaf': [1, 2, 4]
}
```

### Notas sobre Parámetros

- **n_estimators**: Más árboles = mejor rendimiento pero más lento. 100-200 es un buen balance
- **max_depth**:
  - `null` = sin límite (puede causar overfitting)
  - Valores bajos (5-10) = modelos más generales
  - Valores altos (20-30) = captura más detalles
- **min_samples_split**: Valores altos previenen overfitting
- **min_samples_leaf**: Control adicional contra overfitting
- **max_features**:
  - "sqrt": √n_features (recomendado para regresión)
  - "log2": log₂(n_features)
  - float (0.0-1.0): proporción de features
- **bootstrap**:
  - `true` = cada árbol usa muestra aleatoria (más diversidad)
  - `false` = todos usan todo el dataset
- **oob_score**: Validación sin datos separados (solo si bootstrap=true)
- **n_jobs=-1**: Usa todos los cores disponibles (más rápido)

---

## Uso en API

### Endpoint de Entrenamiento

```
POST /api/v1/predictions/train
```

### Ejemplo de Request Completo

```json
{
  "tipo_entidad": "producto",
  "id_entidad": 123,
  "fecha_inicio": "2023-01-01",
  "fecha_fin": "2023-12-31",
  "modelo": "sarima",
  "parametros": {
    "order": [1, 1, 1],
    "seasonal_order": [1, 1, 1, 12],
    "auto_order": false
  },
  "horizonte": 30,
  "intervalo_confianza": 0.95
}
```

### Parámetros Comunes (Todos los Modelos)

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `tipo_entidad` | str | Sí | "producto", "proveedor", "cliente" |
| `id_entidad` | int | Sí | ID de la entidad a predecir |
| `fecha_inicio` | date | Sí | Inicio del periodo de entrenamiento |
| `fecha_fin` | date | Sí | Fin del periodo de entrenamiento |
| `modelo` | str | Sí | "linear_regression", "arima", "sarima", "random_forest" |
| `parametros` | dict | No | Configuración específica del modelo |
| `horizonte` | int | No | Días a futuro para predecir (default: 30) |
| `intervalo_confianza` | float | No | Nivel de confianza 0-1 (default: 0.95) |

---

## Guía de Selección de Modelo

| Escenario | Modelo Recomendado | Razón |
|-----------|-------------------|--------|
| Datos sin tendencia ni estacionalidad | Linear Regression | Simple y rápido |
| Datos con tendencia lineal | Linear Regression (degree=1) | Captura tendencia básica |
| Datos con tendencia no lineal | Linear Regression (degree>1) o Random Forest | Captura relaciones complejas |
| Serie temporal con tendencia | ARIMA | Modelado específico de series temporales |
| Serie temporal con estacionalidad | SARIMA | Captura patrones estacionales |
| Múltiples features/variables | Random Forest | Maneja bien múltiples predictores |
| Dataset pequeño (<100 registros) | Linear Regression o ARIMA | Modelos más simples |
| Dataset grande (>1000 registros) | Random Forest o SARIMA | Pueden capturar más complejidad |
| Se requiere interpretabilidad | Linear Regression | Coeficientes claros |
| Se requiere precisión máxima | Random Forest (con tuning) | Mejor rendimiento general |

---

## Métricas de Evaluación

Todos los modelos retornan las siguientes métricas:

- **MAE** (Mean Absolute Error): Error promedio absoluto
- **MSE** (Mean Squared Error): Error cuadrático medio
- **RMSE** (Root Mean Squared Error): Raíz del error cuadrático medio
- **R²** (Coefficient of Determination): Proporción de varianza explicada (0-1)
- **MAPE** (Mean Absolute Percentage Error): Error porcentual promedio

---

## Referencias

- **Linear Regression**: [sklearn.linear_model](https://scikit-learn.org/stable/modules/linear_model.html)
- **ARIMA/SARIMA**: [statsmodels.tsa](https://www.statsmodels.org/stable/tsa.html)
- **Random Forest**: [sklearn.ensemble.RandomForestRegressor](https://scikit-learn.org/stable/modules/generated/sklearn.ensemble.RandomForestRegressor.html)

---

**Última actualización**: 2026-02-09
