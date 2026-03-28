import py_compile, sys
files = [
    "tests/integration/test_admin_endpoints.py",
    "tests/integration/test_productos_endpoints.py",
    "tests/integration/test_permisos_modulos.py",
    "tests/integration/test_e2e_flows.py",
    "tests/conftest.py",
]
ok = True
for f in files:
    try:
        py_compile.compile(f, doraise=True)
        print(f"OK  {f}")
    except py_compile.PyCompileError as e:
        print(f"ERR {f}: {e}")
        ok = False
sys.exit(0 if ok else 1)
