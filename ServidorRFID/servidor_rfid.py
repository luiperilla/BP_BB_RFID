"""
Servidor de asociación RFID — Chainway C5

Endpoints implementados (mismos que la app ya espera, sin tocar el código Java):
  GET    /modelo?orden=X          -> devuelve un valor para el spinner de modelo
  POST   /guardar_datos           -> guarda/actualiza la asociación (epc, op, fecha)
  GET    /modelos_y_epc?orden=X   -> lista de [modelo, epc] asociados a esa OP
  GET    /consulta_por_epc?epc=X  -> info de un tag específico
  DELETE /eliminar_datos?orden=X  -> libera todos los tags de una OP
  DELETE /eliminar_por_epc?epc=X  -> libera un tag específico

Tiene DOS MODOS, elegidos con SQL_MOTOR en el archivo .env:
  - "sqlite"    -> modo PRUEBA. Crea un archivo local (test_asociaciones.db)
                   en esta misma carpeta. No toca la base de datos real.
                   Ideal para probar desde tu propia PC antes de migrar.
  - "sqlserver" -> modo PRODUCCIÓN. Se conecta a la base de datos real
                   (SmartFactory.dbo.Hornos_Buffer_RFID). Usar solo en la
                   PC oficial, cuando ya se probó todo en modo prueba.
"""

import os
import sqlite3
from dotenv import load_dotenv
from flask import Flask, request, jsonify

load_dotenv()  # carga las variables desde el archivo .env (debe estar junto a este script)

app = Flask(__name__)

# ============================================================
# CONFIGURACIÓN — se lee del archivo .env, no se edita aquí.
# Ver .env.example para saber qué variables llenar.
# ============================================================

PUERTO = int(os.environ.get("PUERTO", "8260"))

# "sqlite" (prueba, en tu PC) o "sqlserver" (producción, PC oficial)
SQL_MOTOR = os.environ.get("SQL_MOTOR", "sqlite").strip().lower()

# --- Configuración para modo prueba (sqlite) ---
SQLITE_ARCHIVO = os.environ.get("SQLITE_ARCHIVO", "test_asociaciones.db")

# --- Configuración para modo producción (sqlserver) ---
SERVIDOR = os.environ.get("SQL_SERVIDOR", "localhost")
BASE_DATOS = os.environ.get("SQL_BASE_DATOS", "SmartFactory")
TABLA = os.environ.get("SQL_TABLA", "Hornos_Buffer_RFID")
USAR_WINDOWS_AUTH = os.environ.get("SQL_WINDOWS_AUTH", "true").strip().lower() == "true"
USUARIO_SQL = os.environ.get("SQL_USUARIO", "")      # solo si SQL_WINDOWS_AUTH=false
PASSWORD_SQL = os.environ.get("SQL_PASSWORD", "")    # solo si SQL_WINDOWS_AUTH=false
ODBC_DRIVER = os.environ.get("SQL_ODBC_DRIVER", "ODBC Driver 17 for SQL Server")

if SQL_MOTOR not in ("sqlite", "sqlserver"):
    raise RuntimeError('SQL_MOTOR debe ser "sqlite" o "sqlserver" en el archivo .env')

if SQL_MOTOR == "sqlserver" and not USAR_WINDOWS_AUTH and (not USUARIO_SQL or not PASSWORD_SQL):
    raise RuntimeError(
        "SQL_WINDOWS_AUTH=false requiere SQL_USUARIO y SQL_PASSWORD en el archivo .env"
    )

# ------------------------------------------------------------
# NOTA SOBRE CLV_MODELO (clave de modelo):
# La tabla real (Hornos_Buffer_RFID) solo tiene epc, op y fecha_actualizacion.
# Se decidió NO guardar el modelo en la base de datos (no era necesario
# para este proceso). Sin embargo, la app (AsociarFragment.java) SÍ sigue
# pidiendo un modelo en el spinner antes de dejar asociar, y lo envía en
# el JSON como "CLV_MODELO" al llamar /guardar_datos.
#
# En vez de modificar el código de la app (para no tocar nada que pueda
# necesitarse más adelante), este servidor simplemente:
#   - Responde MODELO_PLACEHOLDER cuando la app pide /modelo, para que
#     el spinner tenga una opción y la validación de la app pase.
#   - Recibe CLV_MODELO en /guardar_datos, pero lo ignora (no lo guarda).
# ------------------------------------------------------------
MODELO_PLACEHOLDER = os.environ.get("MODELO_PLACEHOLDER", "N/A")


# ============================================================
# CONEXIÓN A LA BASE DE DATOS (abstrae sqlite vs sql server)
# ============================================================

def obtener_conexion():
    if SQL_MOTOR == "sqlite":
        conn = sqlite3.connect(SQLITE_ARCHIVO)
        conn.execute(
            f"""
            CREATE TABLE IF NOT EXISTS {TABLA} (
                epc VARCHAR(100) PRIMARY KEY,
                op VARCHAR(50) NOT NULL,
                fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """
        )
        return conn
    else:
        import pyodbc  # solo se importa si de verdad se usa modo producción
        if USAR_WINDOWS_AUTH:
            cadena = (
                f"DRIVER={{{ODBC_DRIVER}}};"
                f"SERVER={SERVIDOR};"
                f"DATABASE={BASE_DATOS};"
                f"Trusted_Connection=yes;"
                f"TrustServerCertificate=yes;"
            )
        else:
            cadena = (
                f"DRIVER={{{ODBC_DRIVER}}};"
                f"SERVER={SERVIDOR};"
                f"DATABASE={BASE_DATOS};"
                f"UID={USUARIO_SQL};"
                f"PWD={PASSWORD_SQL};"
                f"TrustServerCertificate=yes;"
            )
        return pyodbc.connect(cadena)


def epc_ya_asociado(conn, epc):
    """Consulta si ese EPC ya tiene una asociación activa. Devuelve la OP
    actual si existe, o None si el tag está libre para asociarse."""
    cursor = conn.cursor()
    cursor.execute(f"SELECT op FROM {TABLA} WHERE epc = ?", (epc,) if SQL_MOTOR == "sqlite" else epc)
    fila = cursor.fetchone()
    if fila:
        return str(fila[0])
    return None


def asociar_tag_nuevo(conn, epc, op):
    """INSERT de una asociación nueva. Solo se llama después de confirmar
    (con epc_ya_asociado) que ese tag está libre — por eso ya no hace
    falta MERGE/UPSERT, un tag activo nunca se sobrescribe directamente."""
    cursor = conn.cursor()
    if SQL_MOTOR == "sqlite":
        cursor.execute(
            f"INSERT INTO {TABLA} (epc, op, fecha_actualizacion) VALUES (?, ?, CURRENT_TIMESTAMP)",
            (epc, op),
        )
    else:
        cursor.execute(
            f"INSERT INTO {TABLA} (epc, op, fecha_actualizacion) VALUES (CAST(? AS INT), CAST(? AS INT), GETDATE())",
            epc, op,
        )
    conn.commit()


# ============================================================
# ENDPOINTS
# ============================================================

@app.route("/modelo", methods=["GET"])
def modelo_por_orden():
    """La app llama esto para llenar el spinner al escribir una OP.
    Como no guardamos modelo, devolvemos un valor fijo para que el
    spinner tenga una opción seleccionable y la validación de la app pase."""
    orden = request.args.get("orden", "")
    if not orden:
        return jsonify([]), 400
    return jsonify([MODELO_PLACEHOLDER])


@app.route("/listar_pendientes", methods=["GET"])
def listar_pendientes():
    """Devuelve TODAS las asociaciones actualmente activas (aún no liberadas).
    Como 'Liberar' elimina la fila de la tabla, todo lo que aparece aquí
    está, por definición, pendiente de ser buscado y retirado en Hornos.
    Se usa desde el Excel/Power App de Hornos para ver qué OP hay
    pendientes por buscar, antes de escribirlas en el C5."""
    try:
        conn = obtener_conexion()
        cursor = conn.cursor()
        cursor.execute(f"SELECT epc, op, fecha_actualizacion FROM {TABLA} ORDER BY fecha_actualizacion DESC")
        filas = cursor.fetchall()
        conn.close()
        if SQL_MOTOR == "sqlite":
            resultado = [
                {"epc": str(f[0]), "op": str(f[1]), "fecha": str(f[2])}
                for f in filas
            ]
        else:
            resultado = [
                {"epc": str(f.epc), "op": str(f.op), "fecha": str(f.fecha_actualizacion)}
                for f in filas
            ]
        return jsonify(resultado), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/guardar_datos", methods=["POST"])
def guardar_datos():
    """Recibe {OP, CLV_MODELO, EPC} de la app. Crea una asociación NUEVA.

    REGLA DE NEGOCIO: un tag solo puede estar asociado a UNA pieza (OP) a
    la vez. Si el EPC ya tiene una OP activa, se rechaza la petición —
    primero hay que Liberar ese tag desde el C5 antes de reasociarlo."""
    datos = request.get_json(force=True, silent=True)
    if not datos:
        return jsonify({"error": "JSON inválido o vacío"}), 400

    op = datos.get("OP", "").strip()
    epc = datos.get("EPC", "").strip()
    # CLV_MODELO llega en 'datos' (la app lo manda) pero NO se guarda.
    # clv_modelo = datos.get("CLV_MODELO", "").strip()  # <- disponible si se necesita a futuro

    if not op or not epc:
        return jsonify({"error": "Faltan campos OP o EPC"}), 400

    try:
        conn = obtener_conexion()
        op_existente = epc_ya_asociado(conn, epc)
        if op_existente is not None:
            conn.close()
            return jsonify({
                "error": "Ya está asociado, hay que liberarlo primero"
            }), 409
        asociar_tag_nuevo(conn, epc, op)
        conn.close()
        return jsonify({"mensaje": "Guardado correctamente"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/modelos_y_epc", methods=["GET"])
def modelos_y_epc():
    """Devuelve [[modelo, epc], ...] de los tags asociados a una OP."""
    orden = request.args.get("orden", "")
    if not orden:
        return jsonify([]), 400

    try:
        conn = obtener_conexion()
        cursor = conn.cursor()
        cursor.execute(f"SELECT epc FROM {TABLA} WHERE op = ?", (orden,) if SQL_MOTOR == "sqlite" else orden)
        filas = cursor.fetchall()
        conn.close()
        if SQL_MOTOR == "sqlite":
            resultado = [[MODELO_PLACEHOLDER, fila[0]] for fila in filas]
        else:
            resultado = [[MODELO_PLACEHOLDER, fila.epc] for fila in filas]
        return jsonify(resultado), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/consulta_por_epc", methods=["GET"])
def consulta_por_epc():
    """Devuelve la info de un tag específico.
    IMPORTANTE: la app (ConsultarFragment.java, función consultarPorEPC) espera
    un OBJETO JSON con las claves exactas "OP" y "CLV_MODEL" — NO un array como
    los demás endpoints. Si no hay resultado, la app también espera un objeto
    (con OP vacío), porque hace new JSONObject(json) directamente sin revisar
    si viene vacío antes."""
    epc = request.args.get("epc", "")
    if not epc:
        return jsonify({"OP": "", "CLV_MODEL": ""}), 400

    try:
        conn = obtener_conexion()
        cursor = conn.cursor()
        cursor.execute(f"SELECT epc, op FROM {TABLA} WHERE epc = ?", (epc,) if SQL_MOTOR == "sqlite" else epc)
        fila = cursor.fetchone()
        conn.close()
        if fila:
            op_valor = fila[1] if SQL_MOTOR == "sqlite" else fila.op
            return jsonify({"OP": op_valor, "CLV_MODEL": MODELO_PLACEHOLDER}), 200
        return jsonify({"OP": "", "CLV_MODEL": ""}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/eliminar_datos", methods=["DELETE"])
def eliminar_por_op():
    """Libera (elimina) todos los tags asociados a una OP."""
    orden = request.args.get("orden", "")
    if not orden:
        return jsonify({"error": "Falta el parámetro orden"}), 400

    try:
        conn = obtener_conexion()
        cursor = conn.cursor()
        cursor.execute(f"DELETE FROM {TABLA} WHERE op = ?", (orden,) if SQL_MOTOR == "sqlite" else orden)
        conn.commit()
        conn.close()
        return jsonify({"mensaje": "Registros eliminados"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/eliminar_por_epc", methods=["DELETE"])
def eliminar_por_epc():
    """Libera (elimina) un tag específico."""
    epc = request.args.get("epc", "")
    if not epc:
        return jsonify({"error": "Falta el parámetro epc"}), 400

    try:
        conn = obtener_conexion()
        cursor = conn.cursor()
        cursor.execute(f"DELETE FROM {TABLA} WHERE epc = ?", (epc,) if SQL_MOTOR == "sqlite" else epc)
        conn.commit()
        conn.close()
        return jsonify({"mensaje": "Registro eliminado"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    print(f"Servidor RFID corriendo en el puerto {PUERTO} (modo: {SQL_MOTOR})...")
    if SQL_MOTOR == "sqlite":
        print(f"  -> Base de datos de PRUEBA local: {SQLITE_ARCHIVO}")
        print("  -> No se está tocando la base de datos real de la empresa.")
    app.run(host="0.0.0.0", port=PUERTO)
