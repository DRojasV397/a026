#!/usr/bin/env python3
"""
cleanup_models.py — Limpia modelos predictivos, versiones, packs y archivos .pkl.

Uso:
    python cleanup_models.py                         # Dry-run: muestra resumen de lo que se borraría
    python cleanup_models.py --execute               # Borra todo (pide confirmación)
    python cleanup_models.py --user-id 3             # Dry-run solo para usuario 3
    python cleanup_models.py --user-id 3 --execute   # Borra modelos del usuario 3
    python cleanup_models.py --pack-key pack_ventas_20260320190406 --execute  # Borra un pack específico

Orden de borrado (FK-safe para SQL Server / MSSQL):
    1. Alerta              → FK -> Prediccion
    2. Prediccion          → FK -> VersionModelo
    3. ResultadoFinanciero → FK -> VersionModelo  (nullable)
    4. ModeloPack          → FK -> VersionModelo  (dos FKs)
    5. ParametroEscenario  → FK -> Escenario
    6. ResultadoEscenario  → FK -> Escenario
    7. Escenario           → FK -> VersionModelo  (nullable)
    8. VersionModelo       → FK -> Modelo
    9. Modelo              → raíz
   10. Archivos .pkl       → trained_models/<model_key>.pkl
"""

import sys
import os
import argparse
from pathlib import Path

# Permitir imports desde el paquete app/
sys.path.insert(0, str(Path(__file__).parent))

from sqlalchemy import text
from app.database import db_manager  # noqa: E402 — necesita sys.path arriba


# ─────────────────────────────────────────────────────────────────────────────
# Directorio de modelos pkl
# ─────────────────────────────────────────────────────────────────────────────
MODELS_DIR = Path(__file__).parent / "trained_models"


# ─────────────────────────────────────────────────────────────────────────────
# Construcción de filtros SQL
# ─────────────────────────────────────────────────────────────────────────────

def _modelo_filter(user_id: int | None, pack_key: str | None) -> tuple[str, dict]:
    """
    Devuelve (cláusula WHERE para Modelo, parámetros).
    Si pack_key está presente, filtra solo los modelos referenciados por ese pack.
    """
    if pack_key:
        # Los modelos del pack son los referenciados por idVersionVentas e idVersionCompras
        where = (
            "idModelo IN ("
            "  SELECT vm.idModelo FROM VersionModelo vm"
            "  JOIN ModeloPack mp ON vm.idVersion = mp.idVersionVentas"
            "                     OR vm.idVersion = mp.idVersionCompras"
            "  WHERE mp.packKey = :pack_key"
            ")"
        )
        params: dict = {"pack_key": pack_key}
    elif user_id is not None:
        where = "creadoPor = :user_id"
        params = {"user_id": user_id}
    else:
        where = "1=1"
        params = {}
    return where, params


def _version_subquery(modelo_where: str) -> str:
    """Sub-consulta de idVersion a partir de un filtro de Modelo."""
    return f"SELECT idVersion FROM VersionModelo WHERE idModelo IN (SELECT idModelo FROM Modelo WHERE {modelo_where})"


def _pred_subquery(version_sub: str) -> str:
    return f"SELECT idPred FROM Prediccion WHERE idVersion IN ({version_sub})"


def _escenario_subquery(version_sub: str) -> str:
    return f"SELECT idEscenario FROM Escenario WHERE baseVersion IN ({version_sub})"


# ─────────────────────────────────────────────────────────────────────────────
# Conteo (dry-run)
# ─────────────────────────────────────────────────────────────────────────────

def count_records(session, user_id: int | None, pack_key: str | None) -> dict:
    """Retorna un dict con los conteos de registros que serían eliminados."""
    modelo_where, params = _modelo_filter(user_id, pack_key)
    version_sub  = _version_subquery(modelo_where)
    pred_sub     = _pred_subquery(version_sub)
    escenario_sub = _escenario_subquery(version_sub)

    def count(query: str) -> int:
        return session.execute(text(query), params).scalar() or 0

    return {
        "Modelo":              count(f"SELECT COUNT(*) FROM Modelo WHERE {modelo_where}"),
        "VersionModelo":       count(f"SELECT COUNT(*) FROM VersionModelo WHERE idModelo IN (SELECT idModelo FROM Modelo WHERE {modelo_where})"),
        "Prediccion":          count(f"SELECT COUNT(*) FROM Prediccion WHERE idVersion IN ({version_sub})"),
        "Alerta":              count(f"SELECT COUNT(*) FROM Alerta WHERE idPred IN ({pred_sub})"),
        "ResultadoFinanciero": count(f"SELECT COUNT(*) FROM ResultadoFinanciero WHERE idVersion IN ({version_sub})"),
        "ModeloPack":          count(f"SELECT COUNT(*) FROM ModeloPack WHERE idVersionVentas IN ({version_sub}) OR idVersionCompras IN ({version_sub})"),
        "Escenario":           count(f"SELECT COUNT(*) FROM Escenario WHERE baseVersion IN ({version_sub})"),
        "ParametroEscenario":  count(f"SELECT COUNT(*) FROM ParametroEscenario WHERE idEscenario IN ({escenario_sub})"),
        "ResultadoEscenario":  count(f"SELECT COUNT(*) FROM ResultadoEscenario WHERE idEscenario IN ({escenario_sub})"),
    }


# ─────────────────────────────────────────────────────────────────────────────
# Obtener model keys y pack keys antes de borrar
# ─────────────────────────────────────────────────────────────────────────────

def get_model_keys(session, user_id: int | None, pack_key: str | None) -> list[str]:
    """Devuelve los modelKey de los Modelo que serían eliminados."""
    modelo_where, params = _modelo_filter(user_id, pack_key)
    rows = session.execute(
        text(f"SELECT modelKey FROM Modelo WHERE {modelo_where} AND modelKey IS NOT NULL"),
        params,
    ).fetchall()
    return [row[0] for row in rows]


def get_pack_keys(session, user_id: int | None, pack_key: str | None) -> list[str]:
    """Devuelve los packKey de los ModeloPack que serían eliminados."""
    modelo_where, params = _modelo_filter(user_id, pack_key)
    version_sub = _version_subquery(modelo_where)
    rows = session.execute(
        text(
            f"SELECT packKey FROM ModeloPack "
            f"WHERE idVersionVentas IN ({version_sub}) OR idVersionCompras IN ({version_sub})"
        ),
        params,
    ).fetchall()
    return [row[0] for row in rows]


# ─────────────────────────────────────────────────────────────────────────────
# Borrado efectivo
# ─────────────────────────────────────────────────────────────────────────────

def delete_records(session, user_id: int | None, pack_key: str | None) -> dict:
    """
    Borra los registros en orden FK-safe.
    Retorna dict con los rowcount de cada DELETE.
    """
    modelo_where, params = _modelo_filter(user_id, pack_key)
    version_sub   = _version_subquery(modelo_where)
    pred_sub      = _pred_subquery(version_sub)
    escenario_sub = _escenario_subquery(version_sub)

    def exe(query: str) -> int:
        result = session.execute(text(query), params)
        return result.rowcount

    deleted = {}

    # 1. Alerta  (FK -> Prediccion)
    deleted["Alerta"] = exe(f"DELETE FROM Alerta WHERE idPred IN ({pred_sub})")

    # 2. Prediccion (FK -> VersionModelo)
    deleted["Prediccion"] = exe(f"DELETE FROM Prediccion WHERE idVersion IN ({version_sub})")

    # 3. ResultadoFinanciero (FK -> VersionModelo, nullable — SET NULL no está definido → delete explícito)
    deleted["ResultadoFinanciero"] = exe(f"DELETE FROM ResultadoFinanciero WHERE idVersion IN ({version_sub})")

    # 4. ModeloPack (FK -> VersionModelo x2)
    deleted["ModeloPack"] = exe(
        f"DELETE FROM ModeloPack WHERE idVersionVentas IN ({version_sub}) OR idVersionCompras IN ({version_sub})"
    )

    # 5. ParametroEscenario (FK -> Escenario, cascade pero explícito para control)
    deleted["ParametroEscenario"] = exe(f"DELETE FROM ParametroEscenario WHERE idEscenario IN ({escenario_sub})")

    # 6. ResultadoEscenario (FK -> Escenario, cascade pero explícito)
    deleted["ResultadoEscenario"] = exe(f"DELETE FROM ResultadoEscenario WHERE idEscenario IN ({escenario_sub})")

    # 7. Escenario (FK -> VersionModelo)
    deleted["Escenario"] = exe(f"DELETE FROM Escenario WHERE baseVersion IN ({version_sub})")

    # 8. VersionModelo (FK -> Modelo, cascade all/delete-orphan — pero hacemos explícito para seguridad)
    deleted["VersionModelo"] = exe(f"DELETE FROM VersionModelo WHERE idModelo IN (SELECT idModelo FROM Modelo WHERE {modelo_where})")

    # 9. Modelo
    deleted["Modelo"] = exe(f"DELETE FROM Modelo WHERE {modelo_where}")

    return deleted


# ─────────────────────────────────────────────────────────────────────────────
# Limpieza de archivos pkl
# ─────────────────────────────────────────────────────────────────────────────

def delete_pkl_files(model_keys: list[str], pack_keys: list[str], dry_run: bool) -> list[str]:
    """
    Elimina (o lista) los archivos .pkl para los model_keys y pack_keys dados.

    Los nombres de archivo son: <key>.pkl (igual que el modelKey / packKey de la BD).
    Retorna lista de archivos afectados.
    """
    all_keys = set(model_keys) | set(pack_keys)
    affected: list[str] = []

    if not MODELS_DIR.exists():
        print(f"  Directorio {MODELS_DIR} no encontrado — no se buscan archivos pkl.")
        return affected

    for key in all_keys:
        pkl_path = MODELS_DIR / f"{key}.pkl"
        if pkl_path.exists():
            affected.append(str(pkl_path))
            if not dry_run:
                pkl_path.unlink()

    # También buscar archivos huérfanos (archivos pkl que ya no tienen entrada en BD)
    # Solo en modo completo (sin filtro) para no borrar archivos de otros usuarios
    return affected


# ─────────────────────────────────────────────────────────────────────────────
# Presentación
# ─────────────────────────────────────────────────────────────────────────────

def print_summary(counts: dict, title: str = "Registros a eliminar"):
    total = sum(counts.values())
    print(f"\n{'─' * 50}")
    print(f"  {title}")
    print(f"{'─' * 50}")
    for table, n in counts.items():
        marker = "  " if n == 0 else "  "
        print(f"  {marker}{table:<25} {n:>6} registros")
    print(f"{'─' * 50}")
    print(f"  {'TOTAL':<25} {total:>6} registros")
    print(f"{'─' * 50}\n")


# ─────────────────────────────────────────────────────────────────────────────
# Entrypoint
# ─────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Limpia modelos predictivos, packs y archivos pkl de la BD.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Ejecuta el borrado. Sin esta bandera solo muestra resumen (dry-run).",
    )
    parser.add_argument(
        "--user-id",
        type=int,
        default=None,
        metavar="N",
        help="Filtra por ID de usuario creador. Sin este parámetro se borran TODOS los modelos.",
    )
    parser.add_argument(
        "--pack-key",
        type=str,
        default=None,
        metavar="KEY",
        help="Filtra por packKey específico (ej. pack_ventas_20260320190406). "
             "Solo se borran los modelos referenciados por ese pack.",
    )
    parser.add_argument(
        "--yes",
        action="store_true",
        help="Omite la confirmación interactiva (útil en scripts no interactivos).",
    )
    args = parser.parse_args()

    # Validación: --pack-key y --user-id son mutuamente excluyentes
    if args.pack_key and args.user_id is not None:
        parser.error("--pack-key y --user-id no pueden usarse juntos.")

    # ── Conexión ──
    print("\nConectando a la base de datos...")
    if not db_manager.test_connection():
        print("ERROR: No se pudo conectar a la base de datos. Verifica la configuración.")
        sys.exit(1)
    print("Conexión exitosa.")

    # ── Contexto del filtro ──
    if args.pack_key:
        scope_msg = f"Pack: {args.pack_key}"
    elif args.user_id is not None:
        scope_msg = f"Usuario ID: {args.user_id}"
    else:
        scope_msg = "TODOS los modelos y packs"

    print(f"\nAlcance: {scope_msg}")

    # ── Dry-run: conteo ──
    with db_manager.get_session_context() as session:
        counts = count_records(session, args.user_id, args.pack_key)
        model_keys = get_model_keys(session, args.user_id, args.pack_key)
        pack_keys  = get_pack_keys(session, args.user_id, args.pack_key)

    print_summary(counts, title="Registros a eliminar (dry-run)")

    # ── Archivos pkl ──
    pkl_files = delete_pkl_files(model_keys, pack_keys, dry_run=True)
    if pkl_files:
        print(f"  Archivos .pkl a eliminar ({len(pkl_files)}):")
        for f in pkl_files:
            print(f"    {f}")
    else:
        print("  Sin archivos .pkl que eliminar.")
    print()

    if not args.execute:
        print("Modo DRY-RUN: no se realizaron cambios.")
        print("Usa --execute para borrar los registros mostrados arriba.\n")
        return

    # ── Confirmación ──
    total = sum(counts.values())
    if total == 0 and not pkl_files:
        print("Nada que borrar.\n")
        return

    if not args.yes:
        print(f"¡ATENCIÓN! Se borrarán {total} registros de la BD y {len(pkl_files)} archivos pkl.")
        confirm = input("Escribe 'SI' para continuar: ").strip()
        if confirm != "SI":
            print("Operación cancelada.\n")
            return

    # ── Ejecución ──
    print("\nEjecutando borrado...")
    with db_manager.get_session_context() as session:
        deleted = delete_records(session, args.user_id, args.pack_key)

    print_summary(deleted, title="Registros eliminados")

    # ── Archivos pkl ──
    deleted_pkls = delete_pkl_files(model_keys, pack_keys, dry_run=False)
    if deleted_pkls:
        print(f"  Archivos .pkl eliminados ({len(deleted_pkls)}):")
        for f in deleted_pkls:
            print(f"    {f}")
    else:
        print("  Sin archivos .pkl eliminados.")

    print("\nLimpieza completada.\n")


if __name__ == "__main__":
    main()
