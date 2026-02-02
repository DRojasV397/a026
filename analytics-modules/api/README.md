# Sistema BI - API REST con FastAPI

API REST modular y escalable para el Sistema de Business Intelligence Predictiva, desarrollada con FastAPI y Python, conectada a SQL Server.

## 📋 Características

- ✅ **Arquitectura modular** con separación de responsabilidades (DAO, DTO, Repositorios, Servicios, Routers)
- ✅ **Programación Orientada a Objetos** en toda la aplicación
- ✅ **Patrón Repository** para abstracción de acceso a datos
- ✅ **Patrón Singleton** para gestión de conexiones
- ✅ **SQL Server** como base de datos
- ✅ **SQLAlchemy ORM** para mapeo objeto-relacional
- ✅ **Pydantic** para validación de datos
- ✅ **Documentación automática** con Swagger UI y ReDoc
- ✅ **Preparado para OAuth2** (futuras implementaciones)
- ✅ **Pool de conexiones** optimizado
- ✅ **Logging** estructurado
- ✅ **CORS** configurado

## 🏗️ Estructura del Proyecto

```
analytics-modules/api/
├── app/
│   ├── config/              # Configuración de la aplicación
│   │   ├── __init__.py
│   │   └── settings.py      # Settings con Pydantic
│   ├── database/            # Gestión de base de datos
│   │   ├── __init__.py
│   │   └── connection.py    # DatabaseManager (Singleton)
│   ├── models/              # Modelos DAO (SQLAlchemy)
│   │   ├── __init__.py
│   │   ├── usuario.py
│   │   ├── producto.py
│   │   ├── venta.py
│   │   ├── compra.py
│   │   ├── prediccion.py
│   │   └── rentabilidad.py
│   ├── schemas/             # Esquemas DTO (Pydantic)
│   │   ├── __init__.py
│   │   ├── usuario.py
│   │   ├── producto.py
│   │   └── venta.py
│   ├── repositories/        # Repositorios (Patrón Repository)
│   │   ├── __init__.py
│   │   ├── base_repository.py
│   │   ├── usuario_repository.py
│   │   └── producto_repository.py
│   ├── services/            # Lógica de negocio
│   │   ├── __init__.py
│   │   ├── usuario_service.py
│   │   └── producto_service.py
│   ├── routers/             # Endpoints de la API
│   │   ├── __init__.py
│   │   ├── usuarios.py
│   │   └── productos.py
│   └── __init__.py
├── tests/                   # Tests unitarios
├── main.py                  # Punto de entrada de la aplicación
├── requirements.txt         # Dependencias
├── .env.example            # Ejemplo de variables de entorno
├── .gitignore
└── README.md
```

## 🔧 Instalación

### Prerrequisitos

- Python 3.10 o superior
- SQL Server (local o remoto)
- ODBC Driver 17 for SQL Server

### Pasos de instalación

1. **Clonar el repositorio y navegar a la carpeta de la API**

```bash
cd analytics-modules/api
```

2. **Crear entorno virtual**

```bash
python -m venv venv
```

3. **Activar entorno virtual**

**Windows:**
```bash
venv\Scripts\activate
```

**Linux/Mac:**
```bash
source venv/bin/activate
```

4. **Instalar dependencias**

```bash
pip install -r requirements.txt
```

5. **Configurar variables de entorno**

Copiar el archivo `.env.example` a `.env` y configurar:

```bash
copy .env.example .env
```

Editar `.env` con tus configuraciones:

```env
DB_SERVER=localhost
DB_PORT=1433
DB_NAME=SistemaBI
DB_USER=sa
DB_PASSWORD=tu_password
```

6. **Verificar que la base de datos exista**

Ejecutar el script SQL de creación de la base de datos ubicado en:
```
database/scripts/crear_base_datos.sql
```

## 🚀 Ejecución

### Modo desarrollo (con auto-reload)

```bash
python main.py
```

O usando uvicorn directamente:

```bash
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### Modo producción

```bash
uvicorn main:app --host 0.0.0.0 --port 8000 --workers 4
```

La API estará disponible en: `http://localhost:8000`

## 📚 Documentación

Una vez ejecutada la aplicación, accede a:

- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc
- **Health Check**: http://localhost:8000/health

## 🔌 Endpoints Principales

### Usuarios

- `POST /api/v1/usuarios` - Crear usuario
- `GET /api/v1/usuarios` - Listar usuarios
- `GET /api/v1/usuarios/{id}` - Obtener usuario por ID
- `GET /api/v1/usuarios/username/{username}` - Obtener usuario por username
- `PUT /api/v1/usuarios/{id}` - Actualizar usuario
- `DELETE /api/v1/usuarios/{id}` - Eliminar usuario
- `POST /api/v1/usuarios/{id}/roles` - Asignar rol a usuario
- `DELETE /api/v1/usuarios/{id}/roles/{rol_id}` - Remover rol de usuario

### Roles

- `POST /api/v1/roles` - Crear rol
- `GET /api/v1/roles` - Listar roles
- `GET /api/v1/roles/{id}` - Obtener rol por ID
- `DELETE /api/v1/roles/{id}` - Eliminar rol

### Productos

- `POST /api/v1/productos` - Crear producto
- `GET /api/v1/productos` - Listar productos (con filtros)
- `GET /api/v1/productos/{id}` - Obtener producto por ID
- `PUT /api/v1/productos/{id}` - Actualizar producto
- `DELETE /api/v1/productos/{id}` - Eliminar producto

### Categorías

- `POST /api/v1/categorias` - Crear categoría
- `GET /api/v1/categorias` - Listar categorías
- `GET /api/v1/categorias/{id}` - Obtener categoría por ID
- `PUT /api/v1/categorias/{id}` - Actualizar categoría
- `DELETE /api/v1/categorias/{id}` - Eliminar categoría

## 🧪 Testing

```bash
pytest tests/
```

Con cobertura:

```bash
pytest --cov=app tests/
```

## 🏛️ Arquitectura

### Capas de la Aplicación

1. **Routers** (`app/routers/`): Endpoints de la API, manejo de requests/responses
2. **Services** (`app/services/`): Lógica de negocio, validaciones
3. **Repositories** (`app/repositories/`): Acceso a datos, operaciones CRUD
4. **Models** (`app/models/`): Modelos DAO (SQLAlchemy)
5. **Schemas** (`app/schemas/`): Modelos DTO (Pydantic)
6. **Database** (`app/database/`): Gestión de conexiones
7. **Config** (`app/config/`): Configuración centralizada

### Patrones de Diseño Utilizados

- **Repository Pattern**: Abstracción del acceso a datos
- **Singleton Pattern**: Gestión única de conexión a BD
- **Dependency Injection**: FastAPI Depends para inyección de dependencias
- **DTO Pattern**: Transferencia de datos con Pydantic
- **Service Layer**: Separación de lógica de negocio

## 🔐 Seguridad (Futuro)

La API está preparada para implementar OAuth2 con JWT. La configuración ya está lista en:

- `app/config/settings.py`: SECRET_KEY, ALGORITHM
- Librerías instaladas: `python-jose`, `passlib`

## 📝 Variables de Entorno

| Variable | Descripción | Default |
|----------|-------------|---------|
| `DB_SERVER` | Servidor SQL Server | localhost |
| `DB_PORT` | Puerto SQL Server | 1433 |
| `DB_NAME` | Nombre de la base de datos | SistemaBI |
| `DB_USER` | Usuario de la base de datos | sa |
| `DB_PASSWORD` | Contraseña | - |
| `DB_DRIVER` | Driver ODBC | ODBC Driver 17 for SQL Server |
| `API_PREFIX` | Prefijo de la API | /api/v1 |
| `DEBUG` | Modo debug | True |
| `LOG_LEVEL` | Nivel de logging | INFO |

## 🤝 Contribución

1. Fork del proyecto
2. Crear feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to branch (`git push origin feature/AmazingFeature`)
5. Abrir Pull Request

## 📄 Licencia

Este proyecto es parte del Sistema de Business Intelligence para Trabajo de Titulación.

## 👥 Autores

- Equipo de Desarrollo - Sistema BI

## 🔗 Enlaces Útiles

- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [SQLAlchemy Documentation](https://docs.sqlalchemy.org/)
- [Pydantic Documentation](https://docs.pydantic.dev/)
- [SQL Server Documentation](https://docs.microsoft.com/en-us/sql/)
