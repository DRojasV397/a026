"""
Seed: crea usuarios administradores directamente en la DB.
Basado en la logica de AuthService.hash_password y admin.py.

Usuarios creados:
  - drojasv1800@mail.com  / Test1234!  / Rol: Administrador
  - rriverob1800@mail.com / Test1234!  / Rol: Administrador
"""

import sys
import os

sys.path.insert(0, "/opt/app/analytics-modules/api")
os.chdir("/opt/app/analytics-modules/api")

import bcrypt
from sqlalchemy.orm import Session
from app.database import db_manager
from app.models.usuario import Usuario, Rol, UsuarioRol

USERS = [
    {
        "nombreCompleto": "Diego Rojas V.",
        "nombreUsuario": "drojasv1800",
        "email": "drojasv1800@mail.com",
        "password": "Test1234!",
    },
    {
        "nombreCompleto": "R. Rivera B.",
        "nombreUsuario": "rriverob1800",
        "email": "rriverob1800@mail.com",
        "password": "Test1234!",
    },
]

ROL_ADMINISTRADOR = "Administrador"
ROL_SECUNDARIO = "Secundario"


def hash_password(password: str) -> str:
    salt = bcrypt.gensalt()
    hashed = bcrypt.hashpw(password.encode("utf-8"), salt)
    return hashed.decode("utf-8")


def seed(db: Session):
    # 1. Asegurar que los roles existen
    print("[1/3] Verificando roles...")
    for rol_nombre in [ROL_ADMINISTRADOR, ROL_SECUNDARIO]:
        existing = db.query(Rol).filter(Rol.nombre == rol_nombre).first()
        if not existing:
            db.add(Rol(nombre=rol_nombre))
            db.flush()
            print(f"      Rol creado: {rol_nombre}")
        else:
            print(f"      Rol ya existe: {rol_nombre}")
    db.commit()

    rol_admin = db.query(Rol).filter(Rol.nombre == ROL_ADMINISTRADOR).first()
    print(f"      idRol Administrador: {rol_admin.idRol}")

    # 2. Crear usuarios
    print("\n[2/3] Creando usuarios...")
    created_ids = []
    for u in USERS:
        existing = db.query(Usuario).filter(
            (Usuario.email == u["email"]) | (Usuario.nombreUsuario == u["nombreUsuario"])
        ).first()

        if existing:
            print(f"      [SKIP] Ya existe: {u['email']} (id={existing.idUsuario})")
            created_ids.append(existing.idUsuario)
            continue

        nuevo = Usuario(
            nombreCompleto=u["nombreCompleto"],
            nombreUsuario=u["nombreUsuario"],
            email=u["email"],
            hashPassword=hash_password(u["password"]),
            estado="activo",
        )
        db.add(nuevo)
        db.flush()
        print(f"      [OK] Creado: {u['email']} (id={nuevo.idUsuario})")
        created_ids.append(nuevo.idUsuario)

    db.commit()

    # 3. Asignar rol Administrador
    print("\n[3/3] Asignando rol Administrador...")
    for user_id in created_ids:
        existing_rol = db.query(UsuarioRol).filter(
            UsuarioRol.idUsuario == user_id,
            UsuarioRol.idRol == rol_admin.idRol
        ).first()

        if existing_rol:
            print(f"      [SKIP] Usuario {user_id} ya tiene rol Administrador")
            continue

        db.add(UsuarioRol(idUsuario=user_id, idRol=rol_admin.idRol))
        print(f"      [OK] Rol asignado a usuario id={user_id}")

    db.commit()

    # 4. Resumen
    print("\n" + "=" * 55)
    print("USUARIOS LISTOS:")
    for u in USERS:
        row = db.query(Usuario).filter(Usuario.email == u["email"]).first()
        roles = (
            db.query(Rol)
            .join(UsuarioRol, Rol.idRol == UsuarioRol.idRol)
            .filter(UsuarioRol.idUsuario == row.idUsuario)
            .all()
        )
        role_names = [r.nombre for r in roles]
        print(f"  Email:    {row.email}")
        print(f"  Usuario:  {row.nombreUsuario}")
        print(f"  Password: {u['password']}")
        print(f"  Roles:    {role_names}")
        print()
    print("=" * 55)


if __name__ == "__main__":
    try:
        with db_manager.get_session_context() as db:
            seed(db)
        print("[OK] Seed completado exitosamente")
    except Exception as e:
        import traceback
        print(f"[ERROR] {e}")
        traceback.print_exc()
        sys.exit(1)
