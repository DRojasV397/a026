"""
Generador de datos sintéticos para pruebas manuales del módulo de datos.

Genera 4 archivos CSV listos para subir a través del flujo:
  Upload → Validate → Clean → Confirm

Uso:
    python generate_seed_data.py

Archivos generados (en ./seed_data/):
    productos.csv   → tipo "productos"
    ventas.csv      → tipo "ventas"
    compras.csv     → tipo "compras"
    inventario.csv  → tipo "inventario"
"""

import csv
import os
import random
from datetime import date, timedelta

random.seed(42)   # Reproducible

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "seed_data")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ── Catálogo de productos ─────────────────────────────────────────────────────

PRODUCTOS = [
    # (sku, nombre, categoria, descripcion, precio, costo)
    ("ELECT-001", "Laptop ProBook X15",            "Electrónica",       "Laptop 15.6\" Intel i7 16GB RAM 512GB SSD",          18500.00, 12000.00),
    ("ELECT-002", "Tablet Samsung Galaxy Tab A8",  "Electrónica",       "Tablet 10.5\" 4GB RAM 64GB Android 12",               5800.00,  3500.00),
    ("ELECT-003", "Smartphone Redmi Note 13 Pro",  "Electrónica",       "Smartphone 6.67\" 128GB AMOLED 200MP",                4200.00,  2800.00),
    ("ELECT-004", "Monitor LED Full HD 27\"",      "Electrónica",       "Monitor IPS 144Hz HDMI DisplayPort ajustable",        4500.00,  2900.00),
    ("ACESS-001", "Mouse Inalámbrico Logitech M310","Accesorios",       "Mouse ergonómico 2.4GHz 3 años batería",               480.00,   280.00),
    ("ACESS-002", "Teclado Mecánico Redragon K552","Accesorios",       "Teclado TKL RGB switches azules retroiluminado",       950.00,   550.00),
    ("ACESS-003", "Hub USB-C 7 en 1",              "Accesorios",       "Hub HDMI 4K USB3.0 lector SD PD100W",                  620.00,   350.00),
    ("ACESS-004", "Cable HDMI 4K 2m",              "Accesorios",       "Cable HDMI 2.0 4K 60Hz trenzado 2 metros",             180.00,    80.00),
    ("ACESS-005", "Mochila Laptop 15.6\"",         "Accesorios",       "Mochila ejecutiva impermeable antirrobo USB",           890.00,   450.00),
    ("COMP-001",  "SSD Samsung 870 EVO 1TB",       "Cómputo",          "SSD SATA III 560MB/s lectura MLC V-NAND",             1450.00,   900.00),
    ("COMP-002",  "Memoria RAM DDR4 16GB 3200MHz", "Cómputo",          "RAM Crucial Ballistix 3200MHz CL16 dual channel",      750.00,   420.00),
    ("COMP-003",  "Tarjeta Gráfica RTX 3060 12GB", "Cómputo",          "GPU NVIDIA 12GB GDDR6 192-bit DLSS 3",                8900.00,  5800.00),
    ("COMP-004",  "Fuente de Poder 650W 80Plus",   "Cómputo",          "Fuente modular 80 Plus Bronze garantía 5 años",       1200.00,   700.00),
    ("AUDIO-001", "Audífonos Sony WH-1000XM5",     "Audio",            "Audífonos ANC premium 30h batería Bluetooth 5.2",     6500.00,  4000.00),
    ("AUDIO-002", "Bocina JBL Charge 5",           "Audio",            "Bocina portátil 40W IP67 20h batería PartyBoost",     2200.00,  1300.00),
    ("AUDIO-003", "Micrófono USB Blue Yeti",       "Audio",            "Micrófono condensador cardioide 48kHz estudio",       2800.00,  1700.00),
    ("AUDIO-004", "Audífonos Gamer HyperX Cloud II","Audio",           "Audífonos gaming surround 7.1 USB desmontable",       1800.00,  1100.00),
    ("SMART-001", "Foco Inteligente Philips Hue E27","Hogar Inteligente","Foco LED RGB WiFi 10W compatible Alexa Google",      450.00,   220.00),
    ("SMART-002", "Enchufe Inteligente WiFi 15A",  "Hogar Inteligente","Enchufe WiFi monitoreo energía control remoto",        320.00,   150.00),
    ("SMART-003", "Cámara IP Indoor 1080p WiFi",   "Hogar Inteligente","Cámara seguridad IA detección personas visión nocturna",780.00,  420.00),
]

PROVEEDORES = [
    "Distribuidora TecnoMax SA de CV",
    "Mayorista ElectroPro MX",
    "Importaciones GlobalTech",
    "Distribuciones Norte SA",
    "Proveedor Central Electrónico",
]

CLIENTES = [
    "TecnoFácil Monterrey",
    "CyberStore CDMX",
    "Grupo Imagen Digital",
    "InfoSystems Guadalajara",
    "Públicas individuales",
    "Compras Empresariales SA",
    "DigitalHub Puebla",
    "NetZone Querétaro",
]

# ── Helpers ───────────────────────────────────────────────────────────────────

def date_range(start: date, end: date):
    """Genera fechas aleatorias entre start y end."""
    delta = (end - start).days
    return start + timedelta(days=random.randint(0, delta))


def round2(v):
    return round(v, 2)


# ── 1. productos.csv ──────────────────────────────────────────────────────────

def generate_productos():
    path = os.path.join(OUTPUT_DIR, "productos.csv")
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["sku", "nombre", "categoria", "descripcion", "precio", "costo"])
        for row in PRODUCTOS:
            writer.writerow(row)
    print(f"✓ {path}  ({len(PRODUCTOS)} productos)")


# ── 2. ventas.csv ─────────────────────────────────────────────────────────────

def generate_ventas():
    """
    ~300 registros de ventas (enero–diciembre 2025).
    Estacionalidad: mayor volumen en nov-dic, menor en ene-feb.
    """
    START = date(2025, 1, 1)
    END   = date(2025, 12, 31)

    # Peso mensual (ene=1 … dic=12)  — estacionalidad
    monthly_weight = [1.0, 0.9, 1.1, 1.1, 1.2, 1.0,
                      1.2, 1.3, 1.2, 1.3, 1.8, 2.2]
    total_records  = 300
    base_per_month = total_records // 12  # ~25

    records = []
    for month in range(1, 13):
        month_start = date(2025, month, 1)
        month_end   = date(2025, month, 28) if month == 2 else \
                      date(2025, month, 30) if month in (4,6,9,11) else \
                      date(2025, month, 31)

        count = int(base_per_month * monthly_weight[month - 1])
        for _ in range(count):
            d    = date_range(month_start, month_end)
            prod = random.choice(PRODUCTOS)
            qty  = random.randint(1, 5)
            unit_price = round2(prod[4] * random.uniform(0.95, 1.05))  # ±5% variación
            total = round2(qty * unit_price)
            cliente = random.choice(CLIENTES)
            records.append((d, prod[0], prod[1], qty, unit_price, total, cliente))

    records.sort(key=lambda r: r[0])

    path = os.path.join(OUTPUT_DIR, "ventas.csv")
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["fecha", "producto_sku", "producto_nombre", "cantidad",
                         "precio_unitario", "total", "cliente"])
        for r in records:
            writer.writerow(r)
    print(f"✓ {path}  ({len(records)} ventas)")
    return records


# ── 3. compras.csv ────────────────────────────────────────────────────────────

def generate_compras():
    """
    ~120 registros de compras (enero–diciembre 2025).
    Las compras anticipan la demanda: picos en oct-nov.
    """
    START = date(2025, 1, 1)
    END   = date(2025, 12, 31)

    monthly_weight = [1.0, 1.0, 1.1, 1.0, 1.1, 1.0,
                      1.1, 1.2, 1.3, 1.5, 1.4, 1.0]
    total_records  = 120
    base_per_month = total_records // 12  # ~10

    records = []
    for month in range(1, 13):
        month_start = date(2025, month, 1)
        month_end   = date(2025, month, 28) if month == 2 else \
                      date(2025, month, 30) if month in (4,6,9,11) else \
                      date(2025, month, 31)

        count = max(1, int(base_per_month * monthly_weight[month - 1]))
        for _ in range(count):
            d        = date_range(month_start, month_end)
            proveedor = random.choice(PROVEEDORES)

            # Cada compra cubre varios productos (lote)
            n_prods = random.randint(2, 6)
            total = 0.0
            for _ in range(n_prods):
                prod = random.choice(PRODUCTOS)
                qty  = random.randint(5, 30)
                costo = round2(prod[5] * random.uniform(0.92, 1.00))
                total += qty * costo
            total = round2(total)
            records.append((d, proveedor, total))

    records.sort(key=lambda r: r[0])

    path = os.path.join(OUTPUT_DIR, "compras.csv")
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["fecha", "proveedor", "total"])
        for r in records:
            writer.writerow(r)
    print(f"✓ {path}  ({len(records)} compras)")


# ── 4. inventario.csv ─────────────────────────────────────────────────────────

def generate_inventario():
    """Inventario actual por producto."""
    path = os.path.join(OUTPUT_DIR, "inventario.csv")
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["sku", "nombre", "cantidad", "ubicacion", "minimo", "maximo"])
        for p in PRODUCTOS:
            cantidad = random.randint(5, 120)
            minimo   = random.randint(5, 15)
            maximo   = minimo * random.randint(8, 15)
            ubicacion = random.choice(["Almacén A", "Almacén B", "Almacén C", "Bodega Principal"])
            writer.writerow([p[0], p[1], cantidad, ubicacion, minimo, maximo])
    print(f"✓ {path}  ({len(PRODUCTOS)} productos en inventario)")


# ── Main ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print(f"\nGenerando datos sintéticos en: {OUTPUT_DIR}\n")
    generate_productos()
    generate_ventas()
    generate_compras()
    generate_inventario()
    print(f"""
Listo. Flujo de prueba por archivo:
─────────────────────────────────────────────────────────────────
1. productos.csv   → tipo: "productos"
   Columnas mínimas:  sku, nombre, precio
   Columnas opcionales: categoria, descripcion, costo

2. ventas.csv      → tipo: "ventas"
   Columnas mínimas:  fecha, total
   Columnas opcionales: producto_sku, cantidad, precio_unitario, cliente

3. compras.csv     → tipo: "compras"
   Columnas mínimas:  fecha, total
   Columnas opcionales: proveedor

4. inventario.csv  → tipo: "inventario"
   Columnas mínimas:  sku, cantidad
   Columnas opcionales: ubicacion, minimo, maximo

Flujo en la app:
  Subir archivo → Validar → (Limpiar) → Vista previa → Confirmar
─────────────────────────────────────────────────────────────────
""")
