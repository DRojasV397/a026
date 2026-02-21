"""
Script para crear un usuario generico de prueba via la API.
Ejecutar con la API corriendo: python seed_user.py

IMPORTANTE: Antes de ejecutar este script, asegurate de que la columna
hashPassword en la tabla Usuario sea VARCHAR(255) en lugar de VARCHAR(50).

SQL Server:
  ALTER TABLE Usuario ALTER COLUMN hashPassword VARCHAR(255);

Usuario de prueba:
  Email:    testuser@empresa.com
  Password: Test1234!
"""

import requests
import sys
from datetime import datetime

API_URL = "http://localhost:8000/api/v1"

# Generar username unico con timestamp
timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
username = f"testuser{timestamp}"
email = f"testuser{timestamp}@empresa.com"

user_data = {
    "nombreCompleto": f"Test User {timestamp}",
    "nombreUsuario": username,
    "email": email,
    "password": "Test1234!",
    "confirmPassword": "Test1234!"
}


def main():
    print("=" * 60)
    print("SCRIPT DE CREACION DE USUARIO DE PRUEBA")
    print("=" * 60)

    # 1. Registrar usuario
    print(f"\n[1/3] Registrando usuario: {username}")
    print(f"      Email: {email}")

    resp = requests.post(f"{API_URL}/auth/register", json=user_data)

    if resp.status_code == 201:
        print(f"[OK] Usuario registrado exitosamente")
        result = resp.json()
        print(f"  ID Usuario: {result.get('idUsuario')}")
        print(f"  Username: {result.get('nombreUsuario')}")
    elif resp.status_code == 400 and "ya existe" in resp.text:
        print("[WARNING] El usuario ya existe (esto no deberia pasar con timestamp)")
        print(f"  Intentaremos hacer login de todas formas...")
    else:
        print(f"[ERROR] Error al registrar: {resp.status_code}")
        print(f"  Detalle: {resp.text}")
        print("\n[DIAGNOSTICO]:")
        print("  Si ves 'internal server error', verifica que la columna")
        print("  hashPassword sea VARCHAR(255) en SQL Server:")
        print("  ALTER TABLE Usuario ALTER COLUMN hashPassword VARCHAR(255);")
        sys.exit(1)

    # 2. Probar login
    print(f"\n[2/3] Probando login con credenciales...")
    login_resp = requests.post(f"{API_URL}/auth/login/json", json={
        "username": email,
        "password": "Test1234!"
    })

    if login_resp.status_code == 200:
        data = login_resp.json()
        print("[OK] Login exitoso!")
        print(f"  Usuario: {data['user']['nombreCompleto']}")
        print(f"  Email: {data['user']['email']}")
        print(f"  Roles: {data['user'].get('roles', [])}")
        print(f"  Token: {data['access_token'][:50]}...")
        print(f"  Expira en: {data['expires_in']} segundos")
    else:
        print(f"[ERROR] Login fallido: {login_resp.status_code}")
        print(f"  Detalle: {login_resp.text}")
        print("\n[DIAGNOSTICO]:")
        if resp.status_code == 201:
            print("  - El usuario se creo correctamente")
            print("  - Pero el login fallo (hash de contrasena truncado)")
            print("  - SOLUCION: Ejecuta este SQL y vuelve a intentar:")
            print(f"    ALTER TABLE Usuario ALTER COLUMN hashPassword VARCHAR(255);")
            print(f"    DELETE FROM Usuario WHERE nombreUsuario = '{username}';")
        sys.exit(1)

    # 3. Mostrar credenciales
    print("\n[3/3] Resumen de credenciales")
    print("=" * 60)
    print("CREDENCIALES PARA LA APLICACION DE ESCRITORIO:")
    print(f"  Email:    {email}")
    print(f"  Password: Test1234!")
    print("=" * 60)
    print("\n[OK] Script completado exitosamente")


if __name__ == "__main__":
    try:
        main()
    except requests.exceptions.ConnectionError:
        print("\n[ERROR] No se puede conectar a la API")
        print("  Asegurate de que la API este corriendo en http://localhost:8000")
        print("  Comando: cd analytics-modules/api && python main.py")
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n\n[CANCELADO] Script cancelado por el usuario")
        sys.exit(1)
    except Exception as e:
        print(f"\n[ERROR] Error inesperado: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
