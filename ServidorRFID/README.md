# Servidor RFID — Hornos / Buffer

API que usan las terminales Chainway C5 para asociar y liberar tags RFID contra
la tabla `SmartFactory.dbo.Hornos_Buffer_RFID`.

Corre como **Servicio de Windows** en la PC oficial, con `waitress` como
servidor HTTP y **SQLAlchemy** (engine + pool de conexiones) contra SQL Server.

---

## Instalación en la PC de producción

> Para una máquina limpia gestionada por SSH — instalar prerequisitos, traer el
> código, firewall y verificación — sigue **[DESPLIEGUE.md](DESPLIEGUE.md)**,
> que es el runbook completo. Esta sección es el resumen.

Todo desde esta carpeta, en PowerShell **como Administrador**.

**1. Entorno y dependencias.** Se recomienda **conda**, porque trae su propio
Python 3.12 sin necesidad de instalar Python en la máquina (`pyodbc` no publica
paquetes para 3.13+):

```powershell
conda create -n servidor-rfid --override-channels -c conda-forge python=3.12 flask=3.0.3 python-dotenv=1.0.1 waitress=3.0.0 pyodbc=5.1.0 sqlalchemy=2.0.31 -y
```

`--override-channels -c conda-forge` no es opcional: los canales por defecto de
Anaconda exigen licencia comercial de pago para organizaciones grandes. El
archivo `environment.yml` declara lo mismo de forma reproducible.

Como alternativa, con un Python 3.12 ya instalado en el sistema:

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
```

El instalador del servicio detecta solo cuál de los dos estás usando.

**2. Configuración.** Copia la plantilla y edita los valores reales:

```powershell
copy .env.example .env
notepad .env
```

Para producción, como mínimo:

```ini
SQL_MOTOR=sqlserver
SQL_SERVIDOR=192.168.2.23
SQL_WINDOWS_AUTH=false
SQL_USUARIO=Consulta
SQL_PASSWORD=<la contraseña>
```

El `.env` no se sube al repositorio. Es el único sitio donde vive la
configuración real del servidor. Como contiene la contraseña, restringe sus
permisos tras crearlo:

```powershell
icacls .env /inheritance:r
icacls .env /grant "BUILTIN\Administradores:(R)" "NT AUTHORITY\SYSTEM:(R)"
```

Con `SQL_WINDOWS_AUTH=true` y usuario/contraseña vacíos se usa en su lugar la
identidad de dominio del servicio, sin secretos en disco.

**3. Prueba manual antes de instalar el servicio:**

```powershell
conda run -n servidor-rfid --no-capture-output python servidor_rfid.py
```

Si la conexión a la base falla, el proceso se detiene de inmediato con el error
en pantalla. Cuando veas `Servidor listo y escuchando`, ciérralo con `Ctrl+C`.

**4. Instala el servicio** (requiere [NSSM](https://nssm.cc/download) en el PATH):

```powershell
.\instalar_servicio.ps1
```

Con credenciales SQL en el `.env`, el servicio corre como `LocalSystem` y no
necesita cuenta de dominio. Si en cambio usas `SQL_WINDOWS_AUTH=true`, hay que
instalarlo con la cuenta cuyo login exista en SQL Server:

```powershell
.\instalar_servicio.ps1 -CuentaServicio "HADES\smartapps"
```

**5. Verifica:**

```powershell
curl http://localhost:8390/salud
```

Debe responder `{"estado":"ok","motor":"sqlserver"}`.

**6. Abre el puerto solo para las pistolas**, que tienen IP fija — no para toda
la red:

```powershell
$PISTOLAS = @("192.168.2.150")   # IPs fijas reales de los C5
New-NetFirewallRule -DisplayName "Servidor RFID 8390" -Direction Inbound `
    -Protocol TCP -LocalPort 8390 -RemoteAddress $PISTOLAS -Action Allow
```

---

## Probar en tu propia PC sin tocar producción

Con `SQL_MOTOR=sqlite` (el valor por defecto) el servidor crea un archivo local
`test_asociaciones.db` y no se conecta a la base de la empresa:

```powershell
copy .env.example .env
conda run -n servidor-rfid --no-capture-output python servidor_rfid.py
```

---

## Operación diaria

| Acción | Comando |
|---|---|
| Ver estado | `nssm status ServidorRFID` |
| Reiniciar | `nssm restart ServidorRFID` |
| Detener | `nssm stop ServidorRFID` |
| Cambiar configuración | editar `.env` y reiniciar el servicio |
| Desinstalar | `.\instalar_servicio.ps1 -Desinstalar` |

**Logs:**

- `logs\servidor_rfid.log` — actividad de la aplicación (asociaciones,
  liberaciones, errores). Rota cada 2 MB, guarda 5 archivos.
- `logs\servicio_stderr.log` — fallos de arranque, anteriores al logging.
  Es el primer sitio donde mirar si el servicio no levanta.

---

## Endpoints

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/modelo?orden=X` | Llena el spinner de modelo en la app |
| `POST` | `/guardar_datos` | Asocia un tag a una OP — `{OP, CLV_MODELO, EPC}` |
| `GET` | `/modelos_y_epc?orden=X` | Tags asociados a una OP |
| `GET` | `/consulta_por_epc?epc=X` | Consulta un tag concreto |
| `DELETE` | `/eliminar_datos?orden=X` | Libera todos los tags de una OP |
| `DELETE` | `/eliminar_por_epc?epc=X` | Libera un tag |
| `GET` | `/listar_pendientes` | Asociaciones activas (Excel / Power App) |
| `GET` | `/salud` | Chequeo de vida para monitoreo |

`epc` y `op` son enteros: la tabla real los define como `INT`. El servidor
rechaza con `400` cualquier valor que no sea un número entero.

---

## Notas de diseño

**El modelo no se guarda.** La tabla real solo tiene `epc`, `op` y
`fecha_actualizacion`. La app sigue pidiendo un modelo en el spinner antes de
dejar asociar, así que `/modelo` devuelve un valor fijo (`MODELO_PLACEHOLDER`)
para que su validación pase, y `/guardar_datos` recibe `CLV_MODELO` pero lo
ignora. Se prefirió esto a modificar el código Java de la app.

**Un tag, una OP.** `/guardar_datos` rechaza con `409` un EPC que ya tenga una
asociación activa. Para reasociarlo hay que liberarlo primero desde el C5.

**Liberar es borrar.** No hay campo de estado: la fila se elimina. Por eso todo
lo que devuelve `/listar_pendientes` está, por definición, pendiente de buscar.

---

## Pendiente de seguridad

La app se comunica por **HTTP plano y sin autenticación** — cualquiera en la red
puede llamar a `/eliminar_datos`. La regla de firewall del paso 6 limita quién
puede llegar al puerto, pero no sustituye a autenticar las peticiones. Conviene
resolverlo antes de ampliar el alcance del sistema.
