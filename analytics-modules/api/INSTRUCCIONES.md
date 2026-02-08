# Instrucciones para Ejecutar el Proyecto

## ✅ Estado del Proyecto

**El proyecto ha sido completamente recuperado desde los archivos de caché `.pyc` y está 100% funcional.**

---

## 🚀 Cómo Ejecutar

### Opción 1: Con el entorno virtual (Recomendado)

```bash
# 1. Activar el entorno virtual
.venv\Scripts\activate

# 2. Ejecutar el servidor
python main.py
```

### Opción 2: Directamente con el Python del venv

```bash
# Ejecutar sin activar el entorno
.venv\Scripts\python.exe main.py
```

### Opción 3: Con uvicorn directamente

```bash
# Activar entorno virtual
.venv\Scripts\activate

# Ejecutar con uvicorn
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

---

## 📍 Endpoints Disponibles

Una vez iniciado el servidor, podrás acceder a:

- **API Root**: http://localhost:8000/
- **Health Check**: http://localhost:8000/health
- **Documentación Swagger**: http://localhost:8000/docs
- **Documentación ReDoc**: http://localhost:8000/redoc

### Endpoints de la API (prefijo: `/api/v1`)

#### Usuarios:
- `GET /api/v1/usuarios/` - Listar usuarios
- `POST /api/v1/usuarios/` - Crear usuario
- `GET /api/v1/usuarios/{id}` - Obtener usuario por ID
- `GET /api/v1/usuarios/username/{username}` - Obtener por username
- `PUT /api/v1/usuarios/{id}` - Actualizar usuario
- `DELETE /api/v1/usuarios/{id}` - Eliminar usuario

#### Roles:
- `GET /api/v1/roles/` - Listar roles
- `POST /api/v1/roles/` - Crear rol
- `GET /api/v1/roles/{id}` - Obtener rol por ID
- `DELETE /api/v1/roles/{id}` - Eliminar rol

#### Productos:
- `GET /api/v1/productos/` - Listar productos
- `POST /api/v1/productos/` - Crear producto
- `GET /api/v1/productos/{id}` - Obtener producto por ID
- `PUT /api/v1/productos/{id}` - Actualizar producto
- `DELETE /api/v1/productos/{id}` - Eliminar producto

#### Categorías:
- `GET /api/v1/categorias/` - Listar categorías
- `POST /api/v1/categorias/` - Crear categoría
- `GET /api/v1/categorias/{id}` - Obtener categoría por ID
- `PUT /api/v1/categorias/{id}` - Actualizar categoría
- `DELETE /api/v1/categorias/{id}` - Eliminar categoría

---

## 🧪 Pruebas con cURL

```bash
# Health check
curl http://localhost:8000/health

# Listar usuarios
curl http://localhost:8000/api/v1/usuarios/

# Crear un usuario (ejemplo)
curl -X POST http://localhost:8000/api/v1/usuarios/ \
  -H "Content-Type: application/json" \
  -d '{
    "nombreCompleto": "Juan Pérez",
    "nombreUsuario": "jperez",
    "email": "juan.perez@ejemplo.com",
    "hashPassword": "password123",
    "telefono": "5551234567",
    "empresa": "Empresa Demo"
  }'

# Listar productos
curl http://localhost:8000/api/v1/productos/

# Crear una categoría
curl -X POST http://localhost:8000/api/v1/categorias/ \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Electrónicos",
    "descripcion": "Productos electrónicos y tecnología"
  }'
```

---

## 📊 Verificación de la Base de Datos

La aplicación está configurada para conectarse a SQL Server:

- **Servidor**: localhost
- **Base de datos**: TTA026
- **Autenticación**: Windows (Trusted_Connection)

### Estado de la conexión:

✅ **CONECTADO** - La aplicación se conectó exitosamente a la base de datos durante las pruebas.

Para verificar la conexión:
```bash
curl http://localhost:8000/health
```

Deberías ver:
```json
{
    "status": "healthy",
    "database": "connected"
}
```

---

## 📁 Estructura del Proyecto Recuperado

```
api/
├── main.py                    # Punto de entrada de la aplicación ✅
├── requirements.txt           # Dependencias ✅
├── .env                       # Configuración ✅
├── app/
│   ├── __init__.py
│   ├── config/               # Configuración
│   │   ├── __init__.py
│   │   └── settings.py       ✅
│   ├── database/             # Conexión a BD
│   │   ├── __init__.py
│   │   └── connection.py     ✅
│   ├── models/               # Modelos SQLAlchemy (6 archivos)
│   │   ├── __init__.py       ✅
│   │   ├── usuario.py        ✅ (Usuario, Rol, UsuarioRol, PreferenciaUsuario)
│   │   ├── producto.py       ✅ (Producto, Categoria)
│   │   ├── venta.py          ✅ (Venta, DetalleVenta)
│   │   ├── compra.py         ✅ (Compra, DetalleCompra)
│   │   ├── prediccion.py     ✅ (Modelo, VersionModelo, Prediccion, Escenario, etc.)
│   │   └── rentabilidad.py   ✅ (Rentabilidad, ResultadoFinanciero, Reporte, Alerta)
│   ├── repositories/         # Capa de acceso a datos (3 archivos)
│   │   ├── __init__.py       ✅
│   │   ├── base_repository.py        ✅
│   │   ├── usuario_repository.py     ✅
│   │   └── producto_repository.py    ✅
│   ├── schemas/              # DTOs Pydantic (3 archivos)
│   │   ├── __init__.py       ✅
│   │   ├── usuario.py        ✅
│   │   ├── producto.py       ✅
│   │   └── venta.py          ✅
│   ├── services/             # Lógica de negocio (2 archivos)
│   │   ├── __init__.py       ✅
│   │   ├── usuario_service.py    ✅
│   │   └── producto_service.py   ✅
│   └── routers/              # Endpoints FastAPI (2 archivos)
│       ├── __init__.py       ✅
│       ├── usuarios.py       ✅
│       └── productos.py      ✅
```

**Total**: 27 archivos Python recuperados

---

## 🔧 Configuración

La configuración se encuentra en el archivo `.env`:

```ini
# Base de datos
DB_SERVER=localhost
DB_NAME=TTA026
DB_DRIVER=ODBC Driver 17 for SQL Server

# Aplicación
APP_NAME=Sistema BI - API
APP_VERSION=1.0.0
API_PREFIX=/api/v1
DEBUG=True
LOG_LEVEL=INFO

# CORS
ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080,http://localhost:8000
```

Puedes modificar estos valores según tus necesidades.

---

## 📝 Notas Importantes

1. **Entorno Virtual**: Asegúrate de usar el Python del entorno virtual (`.venv/Scripts/python.exe`) para tener todas las dependencias correctas.

2. **Base de Datos**: El proyecto está configurado para SQL Server con autenticación de Windows. Si necesitas usar autenticación SQL, modifica el archivo `app/database/connection.py`.

3. **Logs**: Los logs se muestran en la consola. Puedes ajustar el nivel de log en `.env` con `LOG_LEVEL`.

4. **Hot Reload**: El servidor está configurado con `reload=True` en modo debug, por lo que se recargará automáticamente al hacer cambios en el código.

---

## ✅ Verificaciones Realizadas

Durante las pruebas, se verificó que:

- ✅ Todos los módulos se importan correctamente
- ✅ La configuración se carga desde `.env`
- ✅ La conexión a SQL Server funciona
- ✅ El servidor FastAPI inicia correctamente
- ✅ Los endpoints responden correctamente
- ✅ La documentación Swagger está disponible
- ✅ El health check funciona

---

## 🎉 ¡Listo para Usar!

Tu proyecto ha sido completamente recuperado y está funcionando al 100%. Puedes empezar a trabajar en él inmediatamente.

Para cualquier duda, consulta la documentación interactiva en: http://localhost:8000/docs
