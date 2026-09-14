"""
Servidor de asociación RFID — Chainway C5

Endpoints implementados (mismos que la app ya espera, sin tocar el código Java):
  GET    /modelo?orden=X          -> devuelve un valor para el spinner de modelo
  POST   /guardar_datos           -> guarda/actualiza la asociación (epc, op, fecha)
  GET    /modelos_y_epc?orden=X   -> lista de [modelo, epc] asociados a esa OP
  GET    /consulta_por_epc?epc=X  -> info de un tag específico
  DELETE /eliminar_datos?orden=X  -> libera todos los tags de una OP
  DELETE /eliminar_por_epc?epc=X  -> libera un tag específico
  GET    /listar_pendientes       -> asociaciones activas (para el Excel/Power App)
  GET    /salud                   -> chequeo de vida del servicio (para monitoreo)

Tiene DOS MODOS, elegidos con SQL_MOTOR en el archivo .env:
  - "sqlite"    -> modo PRUEBA. Crea un archivo local (test_asociaciones.db)
                   en esta misma carpeta. No toca la base de datos real.
  - "sqlserver" -> modo PRODUCCIÓN. Se conecta a la base de datos real
                   (SmartFactory.dbo.Hornos_Buffer_RFID).

ACCESO A DATOS: se usa SQLAlchemy con un engine y su pool de conexiones, que es
el estándar del resto de aplicaciones. El engine se crea una sola vez al
importar el módulo y reparte conexiones del pool a cada petición, en vez de
abrir y cerrar una conexión nueva cada vez.

TIPOS DE DATOS: en la tabla real, 'epc' y 'op' son columnas INT. Este servidor
convierte y valida ambos valores a entero antes de tocar la base de datos, de
modo que los parámetros viajan ya tipados y no dependen de conversiones
implícitas de SQL Server.

Se ejecuta con waitress (servidor apto para producción), no con el servidor de
desarrollo de Flask. Todas las rutas se resuelven contra la carpeta de este
archivo, por lo que funciona igual sin importar el directorio de trabajo —
requisito para correr como Servicio de Windows.
"""

import logging
import os
import re
import sys
from logging.handlers import RotatingFileHandler
from pathlib import Path

from dotenv import load_dotenv
from flask import Flask, jsonify, request
from sqlalchemy import create_engine, text
from sqlalchemy.engine import URL

# ============================================================
# RUTAS ANCLADAS AL ARCHIVO
# Un Servicio de Windows arranca con el directorio de trabajo en
# C:\Windows\System32, no en esta carpeta. Con rutas relativas el .env no
# se encontraría y el servidor arrancaría en modo prueba silenciosamente,
# escribiendo en una base de datos fantasma.
# ============================================================

BASE_DIR = Path(__file__).resolve().parent

load_dotenv(BASE_DIR / ".env")

app = Flask(__name__)

# ============================================================
# CONFIGURACIÓN — se lee del archivo .env, no se edita aquí.
# Ver .env.example para saber qué variables llenar.
# ============================================================

PUERTO = int(os.environ.get("PUERTO", "8390"))
ESCUCHAR_EN = os.environ.get("ESCUCHAR_EN", "0.0.0.0")
HILOS = int(os.environ.get("HILOS", "8"))

# "sqlite" (prueba, en tu PC) o "sqlserver" (producción, PC oficial)
SQL_MOTOR = os.environ.get("SQL_MOTOR", "sqlite").strip().lower()

# --- Configuración para modo prueba (sqlite) ---
SQLITE_ARCHIVO = os.environ.get("SQLITE_ARCHIVO", "test_asociaciones.db")

# --- Configuración para modo producción (sqlserver) ---
SERVIDOR = os.environ.get("SQL_SERVIDOR", "localhost")
BASE_DATOS = os.environ.get("SQL_BASE_DATOS", "SmartFactory")
TABLA = os.environ.get("SQL_TABLA", "Hornos_Buffer_RFID")
USAR_WINDOWS_AUTH = os.environ.get("SQL_WINDOWS_AUTH", "false").strip().lower() == "true"
USUARIO_SQL = os.environ.get("SQL_USUARIO", "")      # solo si SQL_WINDOWS_AUTH=false
PASSWORD_SQL = os.environ.get("SQL_PASSWORD", "")    # solo si SQL_WINDOWS_AUTH=false
ODBC_DRIVER = os.environ.get("SQL_ODBC_DRIVER", "ODBC Driver 17 for SQL Server")

NIVEL_LOG = os.environ.get("NIVEL_LOG", "INFO").strip().upper()

if SQL_MOTOR not in ("sqlite", "sqlserver"):
    raise RuntimeError('SQL_MOTOR debe ser "sqlite" o "sqlserver" en el archivo .env')

if SQL_MOTOR == "sqlserver" and not USAR_WINDOWS_AUTH and (not USUARIO_SQL or not PASSWORD_SQL):
    raise RuntimeError(
        "SQL_WINDOWS_AUTH=false requiere SQL_USUARIO y SQL_PASSWORD en el archivo .env"
    )

# El nombre de tabla se interpola en las consultas, así que se valida para que
# solo pueda ser un identificador simple. No viene del usuario (sale del .env),
# pero un .env mal escrito no debe poder inyectar SQL arbitrario.
if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", TABLA):
    raise RuntimeError(f"SQL_TABLA inválida: {TABLA!r}. Solo letras, números y guion bajo.")

# La ruta del SQLite también se ancla a esta carpeta si viene relativa.
RUTA_SQLITE = Path(SQLITE_ARCHIVO)
if not RUTA_SQLITE.is_absolute():
    RUTA_SQLITE = BASE_DIR / RUTA_SQLITE

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

# Función de fecha del motor correspondiente.
AHORA_SQL = "CURRENT_TIMESTAMP" if SQL_MOTOR == "sqlite" else "GETDATE()"


# ============================================================
# LOGGING
# Como Servicio de Windows no hay consola donde mirar: si algo falla y solo
# hubiera print(), el error se pierde. Todo va a un archivo rotativo dentro
# de logs/ y además a stderr (que NSSM puede redirigir).
# ============================================================

def configurar_logging():
    carpeta = BASE_DIR / "logs"
    carpeta.mkdir(exist_ok=True)

    formato = logging.Formatter(
        "%(asctime)s [%(levelname)s] %(message)s", datefmt="%Y-%m-%d %H:%M:%S"
    )

    archivo = RotatingFileHandler(
        carpeta / "servidor_rfid.log", maxBytes=2_000_000, backupCount=5, encoding="utf-8"
    )
    archivo.setFormatter(formato)

    # La consola de Windows usa cp1252 por defecto y rompe los acentos de los
    # mensajes. Se fuerza UTF-8 para que el archivo que NSSM captura en
    # servicio_stderr.log sea legible.
    try:
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass  # stderr puede no ser reconfigurable según cómo se lance el proceso

    consola = logging.StreamHandler()
    consola.setFormatter(formato)

    raiz = logging.getLogger()
    raiz.setLevel(getattr(logging, NIVEL_LOG, logging.INFO))
    raiz.handlers.clear()
    raiz.addHandler(archivo)
    raiz.addHandler(consola)


log = logging.getLogger("servidor_rfid")


# ============================================================
# VALIDACIÓN DE ENTRADA
# epc y op son INT en la tabla real. Se convierten aquí, en un solo lugar,
# para que los parámetros lleguen a la base de datos ya tipados.
# ============================================================

class DatoInvalido(ValueError):
    """El valor recibido no sirve como entero para una columna INT."""


def a_entero(valor, campo):
    if valor is None:
        raise DatoInvalido(f"Falta el campo {campo}")
    texto = str(valor).strip()
    if not texto:
        raise DatoInvalido(f"Falta el campo {campo}")
    try:
        return int(texto)
    except ValueError:
        raise DatoInvalido(f"{campo} debe ser un número entero (se recibió {texto!r})")


# ============================================================
# ENGINE DE SQLALCHEMY
# ============================================================

def construir_url():
    """Arma la URL de conexión. Se usa URL.create en vez de concatenar texto
    porque escapa correctamente la contraseña: una clave con '@', ':' o '/'
    rompería una URL construida a mano, y ese fallo aparecería como un error
    de conexión incomprensible."""
    if SQL_MOTOR == "sqlite":
        return f"sqlite:///{RUTA_SQLITE}"

    consulta = {"driver": ODBC_DRIVER, "TrustServerCertificate": "yes"}

    if USAR_WINDOWS_AUTH:
        # Sin usuario ni contraseña: se usa la identidad de Windows del proceso,
        # es decir la cuenta bajo la que corre el servicio.
        consulta["Trusted_Connection"] = "yes"
        return URL.create(
            "mssql+pyodbc", host=SERVIDOR, database=BASE_DATOS, query=consulta
        )

    return URL.create(
        "mssql+pyodbc",
        username=USUARIO_SQL,
        password=PASSWORD_SQL,
        host=SERVIDOR,
        database=BASE_DATOS,
        query=consulta,
    )


def crear_engine():
    """Un único engine para todo el proceso. create_engine importa el driver
    (pyodbc) pero NO abre ninguna conexión: el pool se llena bajo demanda, así
    que importar este módulo no falla porque la base esté caída o mal
    configurada. Eso se comprueba explícitamente en main()."""
    if SQL_MOTOR == "sqlite":
        return create_engine(construir_url(), future=True)

    return create_engine(
        construir_url(),
        future=True,
        pool_size=5,
        max_overflow=5,
        # Comprueba la conexión antes de entregarla. Imprescindible contra un
        # SQL Server remoto: los cortafuegos cierran conexiones ociosas y, sin
        # esto, la primera petición tras un rato de inactividad fallaría.
        pool_pre_ping=True,
        # Recicla conexiones cada 30 min para no acumular sesiones eternas.
        pool_recycle=1800,
    )


engine = crear_engine()


def inicializar_sqlite():
    """Crea la tabla de pruebas si no existe, y avisa si quedó con el esquema
    viejo (epc/op como VARCHAR). SQLite guardaría '123' como texto en una
    columna VARCHAR y las búsquedas con el entero 123 no encontrarían nada —
    un fallo silencioso difícil de diagnosticar."""
    if SQL_MOTOR != "sqlite":
        return

    existia = RUTA_SQLITE.exists()
    with engine.begin() as conn:
        conn.execute(text(
            f"""
            CREATE TABLE IF NOT EXISTS {TABLA} (
                epc INTEGER PRIMARY KEY,
                op INTEGER NOT NULL,
                fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """
        ))
        if existia:
            columnas = conn.execute(text(f"PRAGMA table_info({TABLA})")).fetchall()
            tipos = {c[1].lower(): (c[2] or "").upper() for c in columnas}
            if tipos and tipos.get("epc") != "INTEGER":
                log.warning(
                    "La base de prueba %s tiene el esquema antiguo (epc como %s en "
                    "vez de INTEGER). Bórrala para que se regenere correctamente.",
                    RUTA_SQLITE, tipos.get("epc"),
                )


def epc_ya_asociado(conn, epc):
    """Consulta si ese EPC ya tiene una asociación activa. Devuelve la OP
    actual si existe, o None si el tag está libre para asociarse."""
    fila = conn.execute(
        text(f"SELECT op FROM {TABLA} WHERE epc = :epc"), {"epc": epc}
    ).fetchone()
    return fila[0] if fila else None


def asociar_tag_nuevo(conn, epc, op):
    """INSERT de una asociación nueva. Solo se llama después de confirmar
    (con epc_ya_asociado) que ese tag está libre — por eso no hace falta
    MERGE/UPSERT, un tag activo nunca se sobrescribe directamente."""
    conn.execute(
        text(
            f"INSERT INTO {TABLA} (epc, op, fecha_actualizacion) "
            f"VALUES (:epc, :op, {AHORA_SQL})"
        ),
        {"epc": epc, "op": op},
    )


# ============================================================
# ENDPOINTS
# ============================================================

@app.route("/salud", methods=["GET"])
def salud():
    """Chequeo de vida: confirma que el servicio responde Y que la base de
    datos contesta. Sirve para monitoreo y para validar el despliegue."""
    try:
        with engine.connect() as conn:
            conn.execute(text(f"SELECT COUNT(*) FROM {TABLA}")).scalar()
        return jsonify({"estado": "ok", "motor": SQL_MOTOR}), 200
    except Exception as e:
        log.exception("Falló el chequeo de salud")
        return jsonify({"estado": "error", "motor": SQL_MOTOR, "detalle": str(e)}), 503


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
        with engine.connect() as conn:
            filas = conn.execute(text(
                f"SELECT epc, op, fecha_actualizacion FROM {TABLA} "
                f"ORDER BY fecha_actualizacion DESC"
            )).fetchall()
        resultado = [
            {"epc": str(f[0]), "op": str(f[1]), "fecha": str(f[2])} for f in filas
        ]
        return jsonify(resultado), 200
    except Exception as e:
        log.exception("Error en /listar_pendientes")
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

    # CLV_MODELO llega en 'datos' (la app lo manda) pero NO se guarda.
    # clv_modelo = datos.get("CLV_MODELO")  # <- disponible si se necesita a futuro
    try:
        op = a_entero(datos.get("OP"), "OP")
        epc = a_entero(datos.get("EPC"), "EPC")
    except DatoInvalido as e:
        log.warning("Petición rechazada en /guardar_datos: %s", e)
        return jsonify({"error": str(e)}), 400

    try:
        # engine.begin() abre una transacción y hace commit al salir del with
        # (o rollback si hay excepción): la comprobación y el insert quedan en
        # la misma transacción.
        with engine.begin() as conn:
            op_existente = epc_ya_asociado(conn, epc)
            if op_existente is not None:
                log.info("EPC %s ya está asociado a la OP %s", epc, op_existente)
                return jsonify({
                    "error": "Ya está asociado, hay que liberarlo primero"
                }), 409
            asociar_tag_nuevo(conn, epc, op)
        log.info("Asociado EPC %s -> OP %s", epc, op)
        return jsonify({"mensaje": "Guardado correctamente"}), 200
    except Exception as e:
        log.exception("Error en /guardar_datos")
        return jsonify({"error": str(e)}), 500


@app.route("/modelos_y_epc", methods=["GET"])
def modelos_y_epc():
    """Devuelve [[modelo, epc], ...] de los tags asociados a una OP."""
    try:
        orden = a_entero(request.args.get("orden"), "orden")
    except DatoInvalido:
        return jsonify([]), 400

    try:
        with engine.connect() as conn:
            filas = conn.execute(
                text(f"SELECT epc FROM {TABLA} WHERE op = :op"), {"op": orden}
            ).fetchall()
        return jsonify([[MODELO_PLACEHOLDER, str(fila[0])] for fila in filas]), 200
    except Exception as e:
        log.exception("Error en /modelos_y_epc")
        return jsonify({"error": str(e)}), 500


@app.route("/consulta_por_epc", methods=["GET"])
def consulta_por_epc():
    """Devuelve la info de un tag específico.
    IMPORTANTE: la app (ConsultarFragment.java, función consultarPorEPC) espera
    un OBJETO JSON con las claves exactas "OP" y "CLV_MODEL" — NO un array como
    los demás endpoints. Si no hay resultado, la app también espera un objeto
    (con OP vacío), porque hace new JSONObject(json) directamente sin revisar
    si viene vacío antes."""
    try:
        epc = a_entero(request.args.get("epc"), "epc")
    except DatoInvalido:
        return jsonify({"OP": "", "CLV_MODEL": ""}), 400

    try:
        with engine.connect() as conn:
            fila = conn.execute(
                text(f"SELECT op FROM {TABLA} WHERE epc = :epc"), {"epc": epc}
            ).fetchone()
        if fila:
            return jsonify({"OP": str(fila[0]), "CLV_MODEL": MODELO_PLACEHOLDER}), 200
        return jsonify({"OP": "", "CLV_MODEL": ""}), 200
    except Exception as e:
        log.exception("Error en /consulta_por_epc")
        # La app hace new JSONObject(json) sin mirar el código de estado, así
        # que hasta el error se devuelve con la forma que ella espera.
        return jsonify({"OP": "", "CLV_MODEL": "", "error": str(e)}), 500


@app.route("/eliminar_datos", methods=["DELETE"])
def eliminar_por_op():
    """Libera (elimina) todos los tags asociados a una OP."""
    try:
        orden = a_entero(request.args.get("orden"), "orden")
    except DatoInvalido as e:
        return jsonify({"error": str(e)}), 400

    try:
        with engine.begin() as conn:
            resultado = conn.execute(
                text(f"DELETE FROM {TABLA} WHERE op = :op"), {"op": orden}
            )
            borrados = resultado.rowcount
        if borrados == 0:
            # Antes se devolvía 200 siempre: la app mostraba "Registros
            # eliminados" aunque la OP no existiera, y el operador creía haber
            # liberado tags que en realidad seguían asociados.
            log.info("Nada que liberar para la OP %s", orden)
            return jsonify({"error": "No hay tags asociados a esa OP"}), 404
        log.info("Liberados %s tag(s) de la OP %s", borrados, orden)
        return jsonify({"mensaje": "Registros eliminados", "eliminados": borrados}), 200
    except Exception as e:
        log.exception("Error en /eliminar_datos")
        return jsonify({"error": str(e)}), 500


@app.route("/eliminar_por_epc", methods=["DELETE"])
def eliminar_por_epc():
    """Libera (elimina) un tag específico."""
    try:
        epc = a_entero(request.args.get("epc"), "epc")
    except DatoInvalido as e:
        return jsonify({"error": str(e)}), 400

    try:
        with engine.begin() as conn:
            resultado = conn.execute(
                text(f"DELETE FROM {TABLA} WHERE epc = :epc"), {"epc": epc}
            )
            borrados = resultado.rowcount
        if borrados == 0:
            log.info("El EPC %s no estaba asociado", epc)
            return jsonify({"error": "Ese tag no está asociado"}), 404
        log.info("Liberado EPC %s", epc)
        return jsonify({"mensaje": "Registro eliminado"}), 200
    except Exception as e:
        log.exception("Error en /eliminar_por_epc")
        return jsonify({"error": str(e)}), 500


# ============================================================
# ARRANQUE
# ============================================================

def main():
    configurar_logging()

    log.info("=" * 60)
    log.info("Servidor RFID iniciando en %s:%s (modo: %s)", ESCUCHAR_EN, PUERTO, SQL_MOTOR)
    log.info("Carpeta base: %s", BASE_DIR)

    if SQL_MOTOR == "sqlite":
        log.info("Base de datos de PRUEBA local: %s", RUTA_SQLITE)
        log.info("No se está tocando la base de datos real de la empresa.")
        inicializar_sqlite()
    else:
        autenticacion = "Windows (cuenta del servicio)" if USAR_WINDOWS_AUTH \
            else f"credenciales SQL (usuario {USUARIO_SQL})"
        log.info(
            "PRODUCCIÓN -> %s / %s / %s | autenticación: %s",
            SERVIDOR, BASE_DATOS, TABLA, autenticacion,
        )

    # Se comprueba la base al arrancar: si el driver o las credenciales están
    # mal, el servicio falla de inmediato y con un mensaje claro en el log, en
    # vez de quedarse aceptando peticiones y devolver error 500 a cada
    # operador del piso.
    try:
        with engine.connect() as conn:
            filas = conn.execute(text(f"SELECT COUNT(*) FROM {TABLA}")).scalar()
        log.info("Conexión verificada. La tabla tiene %s asociaciones activas.", filas)
    except Exception:
        log.exception("NO SE PUDO CONECTAR A LA BASE DE DATOS. El servicio no arranca.")
        raise

    from waitress import serve

    log.info("Servidor listo y escuchando.")
    serve(app, host=ESCUCHAR_EN, port=PUERTO, threads=HILOS)


if __name__ == "__main__":
    main()
