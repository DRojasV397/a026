# Documentacion de Modelos Predictivos

## Vision General

El sistema implementa multiples algoritmos de Machine Learning para prediccion de ventas, siguiendo los requisitos funcionales RF-02.

---

## Tipos de Modelos Disponibles

| Modelo | Tipo | Caso de Uso | Requisito |
|--------|------|-------------|-----------|
| Linear Regression | Regresion | Tendencias lineales simples | RF-02.01 |
| ARIMA | Series de Tiempo | Datos sin estacionalidad | RF-02.02 |
| SARIMA | Series de Tiempo | Datos con estacionalidad | RN-03.05 |
| Random Forest | Ensemble | Patrones no lineales | RF-02.03 |
| XGBoost | Boosting | Patrones complejos | RF-02.03 |
| K-Means | Clustering | Segmentacion de productos | RF-02.04 |

---

## 1. Regresion Lineal (TimeSeriesLinearRegression)

### Descripcion

Modelo de regresion lineal adaptado para series de tiempo. Genera automaticamente features temporales para capturar tendencias y estacionalidad.

### Features Generadas

- `day_of_week`: Dia de la semana (0-6)
- `day_of_month`: Dia del mes (1-31)
- `month`: Mes (1-12)
- `quarter`: Trimestre (1-4)
- `year`: Año
- `week_of_year`: Semana del año
- `is_weekend`: Indicador de fin de semana
- `is_month_start/end`: Inicio/fin de mes
- `is_quarter_start/end`: Inicio/fin de trimestre
- `days_since_start`: Dias desde inicio (tendencia)
- `sin_day_of_year`, `cos_day_of_year`: Estacionalidad anual

### Hiperparametros

```python
{
    "fit_intercept": True,     # Incluir intercepto
    "normalize": False         # Normalizar features
}
```

### Uso via API

```json
POST /api/v1/predictions/train
{
    "model_type": "linear",
    "hyperparameters": {}
}
```

### Ventajas
- Rapido de entrenar
- Facil de interpretar
- Bueno para tendencias lineales

### Limitaciones
- No captura patrones no lineales complejos
- Sensible a outliers

---

## 2. Random Forest (TimeSeriesRandomForest)

### Descripcion

Ensemble de arboles de decision para prediccion de series de tiempo. Captura patrones no lineales y relaciones complejas entre features.

### Features Generadas

Ademas de las features temporales basicas:
- `total_lag_1, _7, _14, _30`: Valores rezagados
- `total_rolling_mean_7, _14, _30`: Medias moviles
- `total_rolling_std_7, _14, _30`: Desviacion estandar movil
- `total_diff_1, _7`: Diferencias

### Hiperparametros

```python
{
    "n_estimators": 100,       # Numero de arboles
    "max_depth": 10,           # Profundidad maxima
    "min_samples_split": 2,    # Minimo para dividir
    "min_samples_leaf": 1,     # Minimo en hojas
    "random_state": 42         # Semilla aleatoria
}
```

### Uso via API

```json
POST /api/v1/predictions/train
{
    "model_type": "random_forest",
    "hyperparameters": {
        "n_estimators": 100,
        "max_depth": 10
    }
}
```

### Ventajas
- Captura patrones no lineales
- Robusto a outliers
- Feature importance disponible

### Limitaciones
- Mas lento que regresion lineal
- Puede sobreajustar con pocos datos

---

## 3. ARIMA (ARIMAModel)

### Descripcion

AutoRegressive Integrated Moving Average. Modelo clasico de series de tiempo que captura autocorrelacion y tendencia.

### Parametros (p, d, q)

- `p`: Orden autoregresivo (AR)
- `d`: Orden de diferenciacion
- `q`: Orden de media movil (MA)

### Hiperparametros

```python
{
    "order": [1, 1, 1]         # (p, d, q)
}
```

### Auto-seleccion de Orden

El modelo puede detectar automaticamente los mejores parametros usando:
- Test ADF para estacionariedad
- ACF/PACF para p y q

### Uso via API

```json
POST /api/v1/predictions/train
{
    "model_type": "arima",
    "hyperparameters": {
        "order": [1, 1, 1]
    }
}
```

### Ventajas
- Teoricamente fundamentado
- Buenos intervalos de confianza
- Interpretable

### Limitaciones
- Asume estacionariedad
- No maneja estacionalidad compleja
- Sensible a valores atipicos

---

## 4. SARIMA (SARIMAModel)

### Descripcion

Seasonal ARIMA. Extension de ARIMA que incorpora componentes estacionales. Recomendado para datos con patrones estacionales claros (RN-03.05).

### Parametros

- `order`: (p, d, q) - Componente no estacional
- `seasonal_order`: (P, D, Q, s) - Componente estacional
  - s: Periodo de estacionalidad (7 para semanal, 12 para mensual)

### Hiperparametros

```python
{
    "order": [1, 1, 1],
    "seasonal_order": [1, 1, 1, 7]  # Estacionalidad semanal
}
```

### Deteccion de Estacionalidad

El modelo puede detectar automaticamente el periodo estacional:
```python
period = detect_seasonality(data)  # Retorna 7, 30, 365, etc.
```

### Uso via API

```json
POST /api/v1/predictions/train
{
    "model_type": "sarima",
    "hyperparameters": {
        "order": [1, 1, 1],
        "seasonal_order": [1, 1, 1, 7]
    }
}
```

### Ventajas
- Captura estacionalidad explicita
- Buenos para patrones repetitivos

### Limitaciones
- Entrenamiento mas lento
- Requiere mas datos
- Puede no converger

---

## 5. XGBoost (TimeSeriesXGBoost)

### Descripcion

Gradient Boosting optimizado. Modelo de ensemble que construye arboles secuencialmente, corrigiendo errores anteriores.

### Hiperparametros

```python
{
    "n_estimators": 100,
    "max_depth": 6,
    "learning_rate": 0.1,
    "subsample": 0.8,
    "colsample_bytree": 0.8,
    "early_stopping_rounds": 10
}
```

### Uso via API

```json
POST /api/v1/predictions/train
{
    "model_type": "xgboost",
    "hyperparameters": {
        "n_estimators": 100,
        "learning_rate": 0.1
    }
}
```

### Ventajas
- Alto rendimiento
- Maneja datos faltantes
- Regularizacion incorporada

### Limitaciones
- Requiere tuning de hiperparametros
- Puede sobreajustar

---

## 6. K-Means Clustering (KMeansClustering)

### Descripcion

Algoritmo de clustering para segmentacion de productos (RF-02.04). Agrupa productos similares basandose en caracteristicas de venta.

### Hiperparametros

```python
{
    "n_clusters": 3,           # Numero de clusters
    "random_state": 42,
    "max_iter": 300
}
```

### Metricas de Evaluacion

- Silhouette Score
- Calinski-Harabasz Index
- Davies-Bouldin Index

### Uso

```python
from app.analytics.models.kmeans_clustering import KMeansClustering

model = KMeansClustering(n_clusters=3)
result = model.segment_products(df)
```

### Regla de Negocio

- **RN-03.06:** Requiere minimo 10 productos para clustering

---

## Metricas de Evaluacion

### Metricas de Regresion

| Metrica | Formula | Interpretacion |
|---------|---------|----------------|
| R2 | 1 - SSR/SST | 1 = perfecto, 0 = malo |
| RMSE | sqrt(mean(e^2)) | Menor es mejor |
| MAE | mean(\|e\|) | Menor es mejor |
| MAPE | mean(\|e/y\|)*100 | Porcentaje de error |

### Umbral de Aceptacion

Segun **RN-03.02**, un modelo se considera usable si:
- **R2 >= 0.7** (70% de varianza explicada)

---

## Seleccion Automatica de Modelo (RF-02.06)

El endpoint `/api/v1/predictions/auto-select` entrena todos los modelos disponibles y selecciona el mejor basado en R2.

### Proceso

1. Entrenar cada tipo de modelo
2. Evaluar con datos de prueba (30%)
3. Comparar metricas
4. Seleccionar modelo con mayor R2
5. Retornar recomendacion

### Ejemplo de Respuesta

```json
{
    "success": true,
    "best_model": {
        "type": "random_forest",
        "metrics": {
            "r2_score": 0.87,
            "rmse": 1234.56
        }
    },
    "all_models": {
        "linear": {"metrics": {"r2_score": 0.75}},
        "random_forest": {"metrics": {"r2_score": 0.87}},
        "arima": {"metrics": {"r2_score": 0.72}}
    },
    "recommendation": "Random Forest tiene el mejor desempeno con R2=0.87"
}
```

---

## Persistencia de Modelos

### Guardado

Los modelos se guardan automaticamente al entrenar:

```
trained_models/
├── linear_20260202123456.pkl
├── random_forest_20260202123457.pkl
└── sarima_20260202123458.pkl
```

### Carga

```bash
# Cargar un modelo especifico
POST /api/v1/predictions/models/load
{"model_key": "random_forest_20260202123457"}

# Cargar todos los modelos
POST /api/v1/predictions/models/load-all
```

### Formato de Archivo

Los modelos se serializan con `pickle` e incluyen:
- Modelo entrenado
- Configuracion
- Metricas
- Feature names
- Metadata (fechas, estado)

---

## Recomendaciones de Uso

### Datos Minimos

- **RN-01.01:** Minimo 6 meses (180 dias) de datos historicos

### Seleccion de Modelo

| Caracteristica de Datos | Modelo Recomendado |
|------------------------|-------------------|
| Tendencia lineal simple | Linear Regression |
| Estacionalidad clara | SARIMA |
| Patrones complejos | Random Forest |
| Muchas features | XGBoost |

### Periodos de Prediccion

- **RN-03.03:** Maximo 6 meses (180 dias)
- Recomendado: 30-90 dias para mejor precision

---

## Ejemplo Completo

```python
from app.services.prediction_service import PredictionService
from app.database import get_db

# Obtener sesion de BD
db = next(get_db())

# Crear servicio
service = PredictionService(db)

# Entrenar modelo
result = service.train_model(
    model_type="random_forest",
    hyperparameters={"n_estimators": 100}
)

print(f"R2: {result['metrics']['r2_score']}")
print(f"Modelo usable: {result['meets_r2_threshold']}")

# Generar prediccion
if result['meets_r2_threshold']:
    forecast = service.forecast(
        model_type="random_forest",
        periods=30
    )
    print(f"Predicciones: {len(forecast['predictions']['predictions'])}")
```

---

**Version:** 1.0
**Ultima actualizacion:** 02-Febrero-2026
