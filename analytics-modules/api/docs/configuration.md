# Manual de Configuracion - API de Inteligencia Empresarial

## Variables de Entorno

### Archivo .env

Crear archivo `.env` en la raiz del proyecto:

```env
# ================================================
# CONFIGURACION DE BASE DE DATOS
# ================================================

# Servidor SQL Server
DB_SERVER=localhost

# Nombre de la base de datos
DB_NAME=TTA026

# Autenticacion Windows (recomendado para desarrollo local)
DB_TRUSTED_CONNECTION=yes

# Autenticacion SQL Server (alternativa)
# DB_USERNAME=usuario
# DB_PASSWORD=contrasena
# DB_TRUSTED_CONNECTION=no

# Driver ODBC (por defecto: ODBC Driver 17 for SQL Server)
# DB_DRIVER=ODBC Driver 17 for SQL Server

# ================================================
# CONFIGURACION DE SEGURIDAD JWT
# ================================================

# Clave secreta para firmar tokens JWT
# IMPORTANTE: Usar valor unico y seguro en produccion
SECRET_KEY=tu-clave-secreta-muy-segura-minimo-32-caracteres

# Algoritmo de firma
ALGORITHM=HS256

# Tiempo de expiracion del token (minutos)
ACCESS_TOKEN_EXPIRE_MINUTES=60

# ================================================
# CONFIGURACION DE API
# ================================================

# Host para escuchar (0.0.0.0 para todas las interfaces)
API_HOST=0.0.0.0

# Puerto
API_PORT=8000

# Modo debug (false en produccion)
DEBUG=false

# ================================================
# CONFIGURACION DE MODELOS
# ================================================

# Directorio para guardar modelos entrenados
MODELS_DIR=trained_models

# Umbral minimo de R2 para considerar modelo usable
R2_THRESHOLD=0.7

# Maximo de dias para prediccion
MAX_FORECAST_DAYS=180

# Minimo de dias de datos historicos requeridos
MIN_HISTORICAL_DAYS=180

# ================================================
# CONFIGURACION DE ALERTAS
# ================================================

# Umbral de caida para alerta de riesgo (%)
RISK_THRESHOLD=15

# Umbral de subida para alerta de oportunidad (%)
OPPORTUNITY_THRESHOLD=20

# Umbral de anomalias para alerta (%)
ANOMALY_RATE_THRESHOLD=5

# Maximo de alertas activas simultaneas
MAX_ACTIVE_ALERTS=10

# ================================================
# CONFIGURACION DE SIMULACION
# ================================================

# Variacion maxima permitida en parametros (%)
MAX_VARIATION=50

# Maximo de escenarios para comparar
MAX_SCENARIOS_COMPARE=5

# ================================================
# LOGGING
# ================================================

# Nivel de log: DEBUG, INFO, WARNING, ERROR
LOG_LEVEL=INFO

# Formato de log
LOG_FORMAT=%(asctime)s - %(name)s - %(levelname)s - %(message)s
```

---

## Ejemplo de .env.example

```env
# Base de datos
DB_SERVER=localhost
DB_NAME=TTA026
DB_TRUSTED_CONNECTION=yes

# Seguridad
SECRET_KEY=CAMBIAR-POR-CLAVE-SEGURA
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=60

# API
API_HOST=0.0.0.0
API_PORT=8000
DEBUG=false
```

---

## Configuracion de Base de Datos

### SQL Server con Windows Authentication

```env
DB_SERVER=localhost
DB_NAME=TTA026
DB_TRUSTED_CONNECTION=yes
```

La conexion usa las credenciales del usuario de Windows actual.

### SQL Server con SQL Authentication

```env
DB_SERVER=servidor.ejemplo.com
DB_NAME=TTA026
DB_USERNAME=api_user
DB_PASSWORD=SecurePassword123!
DB_TRUSTED_CONNECTION=no
```

### String de Conexion Resultante

El sistema genera automaticamente:

```
Driver={ODBC Driver 17 for SQL Server};
Server=localhost;
Database=TTA026;
Trusted_Connection=yes;
```

---

## Configuracion de Seguridad

### Generacion de SECRET_KEY

Para generar una clave segura:

```python
import secrets
print(secrets.token_hex(32))
# Ejemplo: a3f2c1d4e5b6a7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2
```

O usando OpenSSL:

```bash
openssl rand -hex 32
```

### Duracion de Token

```env
# Token expira en 60 minutos (1 hora)
ACCESS_TOKEN_EXPIRE_MINUTES=60

# Para sesiones mas largas (8 horas)
ACCESS_TOKEN_EXPIRE_MINUTES=480

# Para desarrollo (24 horas)
ACCESS_TOKEN_EXPIRE_MINUTES=1440
```

---

## Configuracion de Modelos Predictivos

### Directorio de Modelos

```env
# Ruta relativa al directorio de trabajo
MODELS_DIR=trained_models

# Ruta absoluta (alternativa)
# MODELS_DIR=C:/data/models
```

### Umbrales de Calidad

```env
# R2 minimo para modelo usable (RN-03.02)
R2_THRESHOLD=0.7

# Cambiar a 0.8 para mayor exigencia
# R2_THRESHOLD=0.8
```

### Limites de Prediccion

```env
# Maximo 6 meses de prediccion (RN-03.03)
MAX_FORECAST_DAYS=180

# Minimo 6 meses de datos (RN-01.01)
MIN_HISTORICAL_DAYS=180
```

---

## Configuracion de Alertas

### Umbrales de Deteccion

```env
# Alerta si ventas caen mas del 15% (RN-04.01)
RISK_THRESHOLD=15

# Alerta si ventas suben mas del 20% (RN-04.02)
OPPORTUNITY_THRESHOLD=20

# Alerta si anomalias superan 5% (RN-04.03)
ANOMALY_RATE_THRESHOLD=5
```

### Limites

```env
# Maximo 10 alertas activas (RN-04.05)
MAX_ACTIVE_ALERTS=10
```

---

## Configuracion de Simulacion

### Limites de Variacion

```env
# Parametros no pueden variar mas del 50% (RN-05.01)
MAX_VARIATION=50

# Para escenarios mas conservadores
# MAX_VARIATION=30
```

### Comparacion

```env
# Maximo 5 escenarios simultaneos (RN-05.03)
MAX_SCENARIOS_COMPARE=5
```

---

## Configuracion de Logging

### Niveles de Log

```env
# Desarrollo: mostrar todo
LOG_LEVEL=DEBUG

# Produccion: solo INFO y superiores
LOG_LEVEL=INFO

# Solo errores
LOG_LEVEL=ERROR
```

### Configuracion en codigo

```python
# app/config/settings.py
import logging

logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL),
    format=settings.LOG_FORMAT
)
```

---

## Configuracion por Ambiente

### Desarrollo (development)

```env
DEBUG=true
LOG_LEVEL=DEBUG
ACCESS_TOKEN_EXPIRE_MINUTES=1440
DB_TRUSTED_CONNECTION=yes
```

### Pruebas (testing)

```env
DEBUG=true
LOG_LEVEL=INFO
DB_NAME=TTA026_Test
```

### Produccion (production)

```env
DEBUG=false
LOG_LEVEL=WARNING
ACCESS_TOKEN_EXPIRE_MINUTES=60
SECRET_KEY=<clave-muy-segura>
```

---

## Configuracion de CORS

Si se requiere acceso desde otros dominios, configurar en `main.py`:

```python
from fastapi.middleware.cors import CORSMiddleware

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "https://tudominio.com"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

---

## Configuracion de Rate Limiting

Para limitar peticiones (opcional):

```python
from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter

@app.get("/api/v1/resource")
@limiter.limit("100/minute")
async def get_resource():
    pass
```

---

## Verificacion de Configuracion

### Script de Verificacion

```python
# verify_config.py
from app.config.settings import settings

print("=== Verificacion de Configuracion ===")
print(f"DB_SERVER: {settings.DB_SERVER}")
print(f"DB_NAME: {settings.DB_NAME}")
print(f"DB_TRUSTED_CONNECTION: {settings.DB_TRUSTED_CONNECTION}")
print(f"API_PORT: {settings.API_PORT}")
print(f"DEBUG: {settings.DEBUG}")
print(f"SECRET_KEY: {'*' * 10}...{settings.SECRET_KEY[-4:]}")
print(f"TOKEN_EXPIRE: {settings.ACCESS_TOKEN_EXPIRE_MINUTES} min")
```

Ejecutar:

```bash
python verify_config.py
```

---

## Problemas Comunes

### Error: DB_SERVER no definido

```
pydantic_settings.sources.DotEnvSettingsSource: .env file not found
```

**Solucion:** Crear archivo `.env` en la raiz del proyecto.

### Error: SECRET_KEY muy corto

**Solucion:** Usar minimo 32 caracteres para la clave secreta.

### Error: ODBC Driver no encontrado

**Solucion:** Instalar ODBC Driver 17 para SQL Server desde Microsoft.

---

**Version:** 1.0
**Ultima actualizacion:** 02-Febrero-2026
