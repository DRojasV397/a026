#!/usr/bin/env python
"""
Script para ejecutar pruebas de la API.
TT 2026-A026 - Sistema de Inteligencia Empresarial para PYMEs.

Uso:
    python tests/run_tests.py                    # Ejecutar todas las pruebas
    python tests/run_tests.py --unit             # Solo pruebas unitarias
    python tests/run_tests.py --integration      # Solo pruebas de integracion
    python tests/run_tests.py --coverage         # Con reporte de cobertura
    python tests/run_tests.py --verbose          # Modo verbose
    python tests/run_tests.py --fast             # Solo pruebas rapidas (sin ML)
"""

import sys
import os
import argparse
import subprocess
from datetime import datetime

# Agregar directorio raiz al path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def run_pytest(args: list, verbose: bool = False) -> int:
    """Ejecuta pytest con los argumentos especificados."""
    cmd = ["python", "-m", "pytest"] + args

    if verbose:
        print(f"\nEjecutando: {' '.join(cmd)}\n")
        print("=" * 60)

    result = subprocess.run(cmd, cwd=os.path.dirname(os.path.dirname(__file__)))
    return result.returncode


def main():
    parser = argparse.ArgumentParser(
        description="Script para ejecutar pruebas de la API"
    )
    parser.add_argument(
        "--unit",
        action="store_true",
        help="Ejecutar solo pruebas unitarias"
    )
    parser.add_argument(
        "--integration",
        action="store_true",
        help="Ejecutar solo pruebas de integracion"
    )
    parser.add_argument(
        "--coverage",
        action="store_true",
        help="Generar reporte de cobertura"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Modo verbose"
    )
    parser.add_argument(
        "--fast",
        action="store_true",
        help="Solo pruebas rapidas (excluye ML pesado)"
    )
    parser.add_argument(
        "--html",
        action="store_true",
        help="Generar reporte HTML"
    )
    parser.add_argument(
        "--module", "-m",
        type=str,
        help="Ejecutar modulo especifico (ej: test_auth_service)"
    )
    parser.add_argument(
        "--keyword", "-k",
        type=str,
        help="Ejecutar tests que coincidan con keyword"
    )

    args = parser.parse_args()

    # Construir argumentos de pytest
    pytest_args = []

    # Seleccionar directorio de pruebas
    if args.unit:
        pytest_args.append("tests/unit/")
        print("\n[INFO] Ejecutando pruebas UNITARIAS...")
    elif args.integration:
        pytest_args.append("tests/integration/")
        print("\n[INFO] Ejecutando pruebas de INTEGRACION...")
    else:
        pytest_args.append("tests/")
        print("\n[INFO] Ejecutando TODAS las pruebas...")

    # Modulo especifico
    if args.module:
        pytest_args = [f"tests/**/{args.module}.py"]
        print(f"\n[INFO] Ejecutando modulo: {args.module}")

    # Filtro por keyword
    if args.keyword:
        pytest_args.extend(["-k", args.keyword])
        print(f"\n[INFO] Filtrando por keyword: {args.keyword}")

    # Verbose
    if args.verbose:
        pytest_args.append("-v")
        pytest_args.append("--tb=short")
    else:
        pytest_args.append("-q")

    # Pruebas rapidas (excluir tests lentos)
    if args.fast:
        pytest_args.extend(["-m", "not slow"])
        print("\n[INFO] Modo rapido: excluyendo pruebas lentas")

    # Cobertura
    if args.coverage:
        pytest_args.extend([
            "--cov=app",
            "--cov-report=term-missing",
            "--cov-report=html:coverage_report",
            "--cov-fail-under=70"  # Minimo 70% de cobertura
        ])
        print("\n[INFO] Generando reporte de cobertura...")

    # Reporte HTML
    if args.html:
        pytest_args.extend([
            "--html=test_report.html",
            "--self-contained-html"
        ])
        print("\n[INFO] Generando reporte HTML...")

    # Configuraciones adicionales
    pytest_args.extend([
        "--strict-markers",
        "-x",  # Parar en primer fallo
        "--color=yes"
    ])

    # Mostrar timestamp
    print(f"\n[START] {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    # Ejecutar pruebas
    exit_code = run_pytest(pytest_args, args.verbose)

    print("=" * 60)
    print(f"[END] {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    if exit_code == 0:
        print("\n[SUCCESS] Todas las pruebas pasaron correctamente!")
    else:
        print(f"\n[FAILED] Algunas pruebas fallaron (codigo: {exit_code})")

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
