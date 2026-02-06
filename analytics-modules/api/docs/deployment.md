# Guia de Despliegue - API de Inteligencia Empresarial

## Requisitos del Sistema

### Hardware Minimo
- CPU: 2 cores
- RAM: 4 GB
- Almacenamiento: 10 GB

### Hardware Recomendado
- CPU: 4 cores
- RAM: 8 GB
- Almacenamiento: 50 GB SSD

### Software Requerido
- Python 3.10 o superior
- SQL Server 2019 o superior
- ODBC Driver 17 para SQL Server

---

## Instalacion

### 1. Clonar Repositorio

```bash
git clone <repository-url>
cd analytics-modules/api
```

### 2. Crear Entorno Virtual

```bash
# Windows
python -m venv .venv
.venv\Scripts\activate

# Linux/Mac
python3 -m venv .venv
source .venv/bin/activate
```

### 3. Instalar Dependencias

```bash
pip install -r requirements.txt
```

### 4. Configurar Variables de Entorno

Crear archivo `.env` basado en `.env.example`:

```env
# Base de datos
DB_SERVER=localhost
DB_NAME=TTA026
DB_TRUSTED_CONNECTION=yes

# Para autenticacion SQL Server (alternativa)
# DB_USERNAME=usuario
# DB_PASSWORD=contrasena
# DB_TRUSTED_CONNECTION=no

# JWT
SECRET_KEY=tu-clave-secreta-muy-segura-minimo-32-caracteres
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=60

# API
API_HOST=0.0.0.0
API_PORT=8000
DEBUG=false
```

### 5. Verificar Conexion a Base de Datos

```bash
python -c "from app.database import get_db; print('Conexion exitosa')"
```

---

## Ejecucion

### Desarrollo

```bash
# Con recarga automatica
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### Produccion

```bash
# Sin recarga, con workers multiples
uvicorn main:app --host 0.0.0.0 --port 8000 --workers 4
```

### Con Gunicorn (Linux)

```bash
gunicorn main:app -w 4 -k uvicorn.workers.UvicornWorker -b 0.0.0.0:8000
```

---

## Verificacion

### Health Check

```bash
curl http://localhost:8000/health
```

Respuesta esperada:
```json
{"status": "healthy", "database": "connected"}
```

### Documentacion API

- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

---

## Configuracion de Base de Datos

### SQL Server - Windows Authentication

1. Asegurar que el servicio SQL Server este corriendo
2. Verificar que Windows Authentication este habilitado
3. Crear la base de datos `TTA026` si no existe

### SQL Server - SQL Authentication

1. Crear usuario de base de datos:
```sql
CREATE LOGIN api_user WITH PASSWORD = 'SecurePassword123!';
CREATE USER api_user FOR LOGIN api_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::dbo TO api_user;
```

2. Configurar `.env`:
```env
DB_USERNAME=api_user
DB_PASSWORD=SecurePassword123!
DB_TRUSTED_CONNECTION=no
```

---

## Persistencia de Modelos

Los modelos entrenados se guardan en el directorio `trained_models/`:

```
trained_models/
├── linear_20260202123456.pkl
├── random_forest_20260202123457.pkl
└── ...
```

### Carga Automatica al Iniciar

Para cargar modelos automaticamente al iniciar la API, usar:

```python
# En codigo
service = PredictionService(db, auto_load=True)
```

O llamar al endpoint despues del inicio:
```bash
curl -X POST http://localhost:8000/api/v1/predictions/models/load-all \
  -H "Authorization: Bearer <token>"
```

---

## Logs

Los logs se escriben a la consola por defecto. Para archivo:

```python
# main.py
import logging
logging.basicConfig(
    filename='api.log',
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
```

---

## Docker (Opcional)

### Dockerfile

```dockerfile
FROM python:3.12-slim

WORKDIR /app

# Instalar ODBC Driver
RUN apt-get update && apt-get install -y \
    curl gnupg2 \
    && curl https://packages.microsoft.com/keys/microsoft.asc | apt-key add - \
    && curl https://packages.microsoft.com/config/debian/11/prod.list > /etc/apt/sources.list.d/mssql-release.list \
    && apt-get update \
    && ACCEPT_EULA=Y apt-get install -y msodbcsql17 unixodbc-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### docker-compose.yml

```yaml
version: '3.8'
services:
  api:
    build: .
    ports:
      - "8000:8000"
    environment:
      - DB_SERVER=sqlserver
      - DB_NAME=TTA026
      - DB_USERNAME=sa
      - DB_PASSWORD=YourPassword123!
      - SECRET_KEY=your-secret-key
    depends_on:
      - sqlserver

  sqlserver:
    image: mcr.microsoft.com/mssql/server:2019-latest
    environment:
      - ACCEPT_EULA=Y
      - SA_PASSWORD=YourPassword123!
    ports:
      - "1433:1433"
    volumes:
      - sqldata:/var/opt/mssql

volumes:
  sqldata:
```

### Ejecutar con Docker

```bash
docker-compose up -d
```

---

## Seguridad en Produccion

### Checklist

- [ ] Cambiar `SECRET_KEY` por valor seguro y unico
- [ ] Configurar HTTPS (usar reverse proxy como Nginx)
- [ ] Establecer `DEBUG=false`
- [ ] Configurar firewall para puertos necesarios
- [ ] Usar credenciales de BD dedicadas (no sa/admin)
- [ ] Habilitar logging de auditoria
- [ ] Configurar backup de base de datos
- [ ] Configurar backup de modelos entrenados

### Nginx como Reverse Proxy

```nginx
server {
    listen 80;
    server_name api.tudominio.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name api.tudominio.com;

    ssl_certificate /etc/ssl/certs/cert.pem;
    ssl_certificate_key /etc/ssl/private/key.pem;

    location / {
        proxy_pass http://localhost:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## Monitoreo

### Endpoints de Estado

- `GET /health` - Health check basico
- `GET /` - Informacion de la API

### Metricas Recomendadas

- Tiempo de respuesta por endpoint
- Errores por endpoint (4xx, 5xx)
- Uso de memoria
- Conexiones a base de datos activas
- Modelos en memoria

---

## Troubleshooting

### Error: No se puede conectar a SQL Server

1. Verificar que SQL Server este corriendo
2. Verificar nombre del servidor en `.env`
3. Verificar que el ODBC Driver este instalado
4. Para Windows Auth, verificar permisos del usuario

### Error: Token JWT invalido

1. Verificar que `SECRET_KEY` sea el mismo en todas las instancias
2. Verificar que el token no haya expirado
3. Verificar formato: `Authorization: Bearer <token>`

### Error: Modelo no encontrado

1. Verificar que el modelo exista en `trained_models/`
2. Llamar a `/predictions/models/load-all` despues de reiniciar
3. Verificar permisos de lectura en el directorio

### Error: Datos insuficientes para prediccion

1. Verificar que haya al menos 180 dias de datos (RN-01.01)
2. Verificar fechas en la consulta de entrenamiento

---

## Soporte

Para reportar problemas o solicitar ayuda:
- Issues: https://github.com/proyecto/issues
- Documentacion: Ver `/docs` en el servidor

---

**Version:** 1.0
**Ultima actualizacion:** 02-Febrero-2026
