# Despliegue por SSH en la PC de producción

Runbook completo para dejar el Servidor RFID corriendo como Servicio de Windows
en una máquina limpia, gestionándolo todo por SSH.

Todos los comandos son **PowerShell** y se ejecutan **en la PC destino** (no en
tu portátil). Sustituye los valores entre `<>` por los reales.

> **Empieza por la sección 0.** Verifica los accesos antes de instalar nada:
> los pasos 1 en adelante modifican una máquina de producción, y no tiene
> sentido tocarla hasta saber que el despliegue puede completarse.

| Dato | Valor | Estado |
|---|---|---|
| Host destino | `192.168.2.112` (`PC_IA-AGP`) | confirmado |
| Puerto | `8390` | confirmado libre y fuera de rangos reservados |
| Usuario SSH | `smartapps` | confirmado, con privilegios de Administrador |
| Servidor SQL | `192.168.2.23` (`SmartFactory`, remoto) | confirmado |
| Driver ODBC | `ODBC Driver 17 for SQL Server` | confirmado instalado |
| Acceso a la BD | login SQL `Consulta` | ya existe con SELECT/INSERT/DELETE |
| Cuenta del servicio | `LocalSystem` | con credenciales SQL no hace falta cuenta de dominio |
| Tipos `epc` / `op` | `int` | confirmado |
| Dirección de la API | `http://192.168.2.112:8390` | ya compilada en el APK |
| IP fija de las pistolas | `<pendiente>` | necesaria para la regla de firewall |

No queda ningún bloqueante de base de datos: se usa el login SQL `Consulta`,
que ya tiene los permisos necesarios sobre `dbo.Hornos_Buffer_RFID`. Solo hace
falta su contraseña para ponerla en el `.env` (sección 6).

> **Nota sobre `Consulta`.** Es un login compartido con otras aplicaciones, así
> que su contraseña abre más que esta tabla y rotarla afectará a todo lo que la
> use. Funciona y desbloquea el despliegue; si más adelante se quiere acotar,
> un login dedicado con permiso solo sobre esta tabla sería preferible:
>
> ```sql
> USE master;
> CREATE LOGIN rfid_svc WITH PASSWORD = '<larga y aleatoria>',
>     CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;
> GO
> USE SmartFactory;
> CREATE USER rfid_svc FOR LOGIN rfid_svc;
> GRANT SELECT, INSERT, DELETE ON dbo.Hornos_Buffer_RFID TO rfid_svc;
> GO
> ```
>
> Sin `UPDATE`: el servidor asocia con `INSERT` y libera con `DELETE`, nunca
> modifica una fila existente.

Estado del entorno en `PC_IA-AGP`: `git`, `winget`, Anaconda e internet disponibles.
Falta instalar **NSSM** (sección 3.3). No hay ningún servidor RFID previo.

> **Anaconda y licencias.** Esa PC tiene Anaconda con los canales
> `repo.anaconda.com`, que exigen licencia comercial de pago. Los comandos de
> este runbook fuerzan `conda-forge`, así que no dependen de ellos ni requieren
> aceptar sus términos.

---

## 0. Verificar accesos ANTES de instalar nada

No instales nada hasta terminar esta fase. Son cuatro accesos distintos y
fallar en cualquiera detiene el despliegue — descubrirlo después significa
haber modificado una PC de producción para nada.

Ninguno de estos comandos instala ni cambia nada: solo leen.

### 0.1 ¿Entras por SSH y con privilegios?

```powershell
ssh smartapps@192.168.2.112
```

Ya dentro, debe responder `True`:

```powershell
([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
```

Si es `False`, no podrás registrar el servicio ni crear la regla de firewall.
Pide que añadan tu cuenta al grupo Administradores de esa PC antes de seguir.

### 0.2 ¿Hay algo YA corriendo en el puerto 8390?

**VERIFICADO — nada corriendo.** Se comprobó en `PC_IA-AGP`: no hay servicio,
ni tarea programada, ni nada respondiendo en 8260 ni en 8390. Es una
instalación limpia, no una migración.

Se deja el bloque documentado por si hubiera que repetir el despliegue en otra
máquina. Si algo respondiera, instalar encima provocaría un choque de puertos
o, peor, dos procesos escribiendo en la misma tabla.

```powershell
Get-NetTCPConnection -LocalPort 8390 -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object { Get-Process -Id $_.OwningProcess | Select-Object Id, ProcessName, Path }
Get-Service | Where-Object { $_.DisplayName -match "RFID|flask|python" } | Format-Table Name, Status, DisplayName
Get-ScheduledTask | Where-Object { $_.TaskName -match "RFID|python" } | Format-Table TaskName, State
Invoke-RestMethod http://localhost:8390/salud -TimeoutSec 5
```

Si algo responde, **para aquí** y averigua qué es y quién lo mantiene. El plan
de despliegue cambia: pasa a ser una migración (parar lo viejo, instalar lo
nuevo, plan de vuelta atrás) en vez de una instalación limpia.

### 0.3 ¿Llegas a SQL Server y la tabla es la que esperamos?

Con tu propia cuenta, desde la PC destino:

```powershell
sqlcmd -S 192.168.2.23 -E -d SmartFactory -Q "SELECT COUNT(*) AS filas FROM dbo.Hornos_Buffer_RFID"
```

Y confirma los tipos de columna:

```powershell
sqlcmd -S 192.168.2.23 -E -d SmartFactory -Q "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Hornos_Buffer_RFID'"
```

Debe devolver `epc` y `op` como **int**. Todo el manejo de datos del servidor
está construido sobre ese supuesto: si aparecen como `varchar`, avísame antes
de continuar porque hay que ajustar el código.

Si `sqlcmd` no está instalado, este bloque hace lo mismo con .NET, que viene de
serie en Windows PowerShell y no requiere instalar nada:

```powershell
$cs = "Server=192.168.2.23;Database=SmartFactory;Integrated Security=SSPI;TrustServerCertificate=true;Connect Timeout=5"
$cn = New-Object System.Data.SqlClient.SqlConnection $cs
try {
    $cn.Open()
    Write-Host "CONEXION OK" -ForegroundColor Green
    $cmd = $cn.CreateCommand()
    $cmd.CommandText = "SELECT SUSER_NAME() AS cuenta,
        HAS_PERMS_BY_NAME('dbo.Hornos_Buffer_RFID','OBJECT','SELECT') AS puede_leer,
        HAS_PERMS_BY_NAME('dbo.Hornos_Buffer_RFID','OBJECT','INSERT') AS puede_insertar,
        HAS_PERMS_BY_NAME('dbo.Hornos_Buffer_RFID','OBJECT','DELETE') AS puede_borrar"
    $r = $cmd.ExecuteReader()
    while ($r.Read()) { "cuenta=$($r[0])  leer=$($r[1])  insertar=$($r[2])  borrar=$($r[3])" }
    $r.Close()

    $cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='Hornos_Buffer_RFID'"
    $r = $cmd.ExecuteReader()
    while ($r.Read()) { "{0,-22} {1,-10} {2}" -f $r[0], $r[1], $r[2] }
    $r.Close()
}
catch { Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red }
finally { $cn.Close() }
```

Devuelve de una vez: si hay conexión, con qué cuenta te autenticas, si esa
cuenta tiene los tres permisos, y los tipos de las columnas.

### 0.4 ¿La CUENTA DE SERVICIO tiene permisos? (no la tuya)

El error más común del despliegue: pruebas con tu cuenta de administrador,
funciona, y el servicio falla porque corre como otra cuenta que nunca tuvo
permisos. Que tú entres no demuestra nada sobre ella.

**Primero decide qué cuenta usar.** `SmartFactory` está en `192.168.2.23`, es
decir es **remoto**, y eso condiciona la respuesta:

```powershell
whoami /fqdn      # si falla, la cuenta es LOCAL de la maquina
whoami /user
```

- Si `smartapps` **es cuenta de dominio** y el bloque de la sección 0.3 devolvió
  los tres permisos en 1, úsala y te ahorras el trámite con IT.
- Si `smartapps` **es cuenta local**, no puede autenticarse contra un SQL Server
  remoto por Windows Auth. Necesitas una cuenta de dominio dedicada, o bien
  cambiar a `SQL_WINDOWS_AUTH=false` con usuario y contraseña de SQL — peor
  opción, porque mete una contraseña en disco.

**RESULTADO EN `PC_IA-AGP`:** `whoami /fqdn` devolvió
`CN=Smart Factory Apps,OU=Produccion,DC=Hades,DC=Agp,DC=Com`, así que
`HADES\smartapps` **es cuenta de dominio** y sirve como cuenta de servicio.

Pero la conexión falló con *Login failed for user 'HADES\smartapps'*. Ese error
lo emite el propio SQL Server —la red y la autenticación de dominio funcionan—,
y al ser un fallo de *instancia* significa que no existe el LOGIN. (Si existiera
pero sin acceso a la base, el mensaje sería *Cannot open database SmartFactory
requested by the login*.)

Pásale esto al DBA, sobre la instancia de `192.168.2.23` y con cuenta
`sysadmin`:

```sql
USE master;
CREATE LOGIN [HADES\smartapps] FROM WINDOWS;
GO

USE SmartFactory;
CREATE USER [HADES\smartapps] FOR LOGIN [HADES\smartapps];
GRANT SELECT, INSERT, DELETE ON dbo.Hornos_Buffer_RFID TO [HADES\smartapps];
GO
```

Permisos mínimos: solo esa tabla y sin `UPDATE`, porque el servidor asocia con
`INSERT` y libera con `DELETE`, nunca modifica una fila existente. Pedir un
permiso acotado a un objeto suele destrabar la solicitud más rápido que pedir
`db_datawriter`.

Verificación que el DBA puede correr después — los tres deben dar 1:

```sql
USE SmartFactory;
EXECUTE AS USER = 'HADES\smartapps';
SELECT HAS_PERMS_BY_NAME('dbo.Hornos_Buffer_RFID','OBJECT','SELECT') AS puede_leer,
       HAS_PERMS_BY_NAME('dbo.Hornos_Buffer_RFID','OBJECT','INSERT') AS puede_insertar,
       HAS_PERMS_BY_NAME('dbo.Hornos_Buffer_RFID','OBJECT','DELETE') AS puede_borrar;
REVERT;
```

Desde la PC destino, para confirmarlo de punta a punta, repite el bloque .NET
de la sección 0.3.

### 0.5 ¿Los C5 llegan a esta PC? — PENDIENTE, ES EL RIESGO PRINCIPAL

El servidor está en `192.168.2.112` (gateway `192.168.2.1`) y las terminales
en `172.16.x.x`: **son subredes distintas**. En la tabla de rutas del servidor
no hay ninguna entrada hacia `172.16.x.x`, lo que no descarta la conectividad
—el gateway por defecto podría encaminarla— pero tampoco la demuestra.

Hay que probarlo en las dos direcciones. **Desde el servidor:**

```powershell
ping 172.16.60.189
Test-NetConnection 172.16.60.189 -Port 443 -InformationLevel Detailed
```

**Desde un equipo en la red de las terminales** (no desde el propio servidor:
si `SourceAddress` sale `192.168.2.112`, se está probando contra sí mismo y el
resultado no dice nada):

```powershell
ping 192.168.2.112
Test-NetConnection 192.168.2.112 -Port 8390
```

El `ping` es lo que importa ahora; el puerto fallará hasta que exista el
servicio. Si no hay ruta entre subredes, el despliegue se detiene hasta que
Redes abra el tránsito — o hay que ubicar el servidor en otra máquina.

### 0.6 ¿Puedes instalar software?

```powershell
New-Item -ItemType Directory -Force -Path C:\Apps | Out-Null
"escritura OK" | Out-File C:\Apps\prueba.txt; Get-Content C:\Apps\prueba.txt; Remove-Item C:\Apps\prueba.txt
winget --version
```

Si `winget` no existe o la política de la empresa bloquea instalaciones,
necesitarás que IT instale Miniforge y NSSM, o desplegar con `conda-pack`
(sección 5).

**Solo cuando los seis puntos estén verdes, continúa.**

---

## 1. Si el SSH no responde

Solo aplica si el paso 0.1 falló con *Connection refused*: OpenSSH Server no
está instalado o no está corriendo. Hay que ejecutar esto **desde una sesión
local** en esa PC (teclado o escritorio remoto), como administrador:

```powershell
Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
Start-Service sshd
Set-Service -Name sshd -StartupType Automatic
New-NetFirewallRule -Name sshd -DisplayName "OpenSSH Server (sshd)" -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22
```

Después vuelve al paso 0.1.

---

## 2. Diagnóstico: qué hay y qué falta

Pega este bloque completo. Te dice exactamente qué tendrás que instalar.

```powershell
Write-Host "=== Diagnostico Servidor RFID ===" -ForegroundColor Cyan
"PC:              $env:COMPUTERNAME"
"Usuario:         $env:USERNAME"
foreach ($c in "git","conda","mamba","nssm","python","winget") {
    $r = (Get-Command $c -ErrorAction SilentlyContinue).Source
    "{0,-16} {1}" -f "$c :", $(if ($r) { $r } else { "NO INSTALADO" })
}
"Drivers ODBC:    " + ((Get-OdbcDriver -Platform 64-bit -ErrorAction SilentlyContinue |
    Where-Object Name -like "*SQL Server*").Name -join ", ")
"Internet:        " + (Test-NetConnection conda.anaconda.org -Port 443 -InformationLevel Quiet)
"GitHub:          " + (Test-NetConnection github.com -Port 443 -InformationLevel Quiet)
"Puerto 8390:     " + $(if (Get-NetTCPConnection -LocalPort 8390 -ErrorAction SilentlyContinue) { "EN USO" } else { "libre" })
"Servicio previo: " + $(if (Get-Service ServidorRFID -ErrorAction SilentlyContinue) { "YA EXISTE" } else { "no existe" })
```

Apunta el resultado. Determina qué pasos de la sección 3 necesitas y, sobre
todo, si la PC tiene salida a internet — de eso depende poder instalar nada.

---

## 3. Prerequisitos

### 3.1 Gestor de entornos conda

**Si el diagnóstico ya encontró `conda`**, sáltate este paso y usa el que hay:
los comandos de la sección 5 fuerzan el canal `conda-forge`, así que sirve
igual.

**Si no hay ninguno, instala Miniforge** (no Miniconda ni Anaconda):

```powershell
winget install --id CondaForge.Miniforge3 --scope machine --accept-package-agreements --accept-source-agreements
```

> **Por qué Miniforge y no Miniconda.** Los canales por defecto de Anaconda
> (`repo.anaconda.com`) exigen licencia comercial de pago para organizaciones
> grandes, y conda ahora bloquea la instalación pidiendo aceptar esos términos.
> Miniforge usa exclusivamente `conda-forge`, que es libre. **No ejecutes
> `conda tos accept` en una PC de la empresa**: eso es aceptar los términos
> comerciales de Anaconda en nombre de AGP.

Si `winget` no está disponible, descarga el instalador desde
<https://conda-forge.org/download/> y ejecútalo en modo silencioso:

```powershell
Start-Process -Wait -FilePath "$env:USERPROFILE\Downloads\Miniforge3-Windows-x86_64.exe" -ArgumentList "/S","/InstallationType=AllUsers","/D=C:\Miniforge3"
$env:PATH = "C:\Miniforge3;C:\Miniforge3\Scripts;C:\Miniforge3\Library\bin;$env:PATH"
```

### 3.2 Git (solo si vas a clonar el repositorio)

```powershell
winget install --id Git.Git --scope machine --accept-package-agreements --accept-source-agreements
```

### 3.3 NSSM

Es el que convierte el script en servicio. No tiene instalador: es un `.exe`
que se copia.

```powershell
New-Item -ItemType Directory -Force -Path C:\Tools | Out-Null
Invoke-WebRequest -Uri "https://nssm.cc/release/nssm-2.24.zip" -OutFile "$env:TEMP\nssm.zip"
Expand-Archive "$env:TEMP\nssm.zip" -DestinationPath "$env:TEMP\nssm" -Force
Copy-Item "$env:TEMP\nssm\nssm-2.24\win64\nssm.exe" C:\Tools\nssm.exe
[Environment]::SetEnvironmentVariable("PATH", $env:PATH + ";C:\Tools", "Machine")
$env:PATH += ";C:\Tools"
nssm version
```

### 3.4 Driver ODBC de SQL Server

Si el diagnóstico no listó ningún *ODBC Driver ... for SQL Server*, instálalo —
sin él `pyodbc` no puede conectar:

```powershell
winget install --id Microsoft.msodbcsql.18 --accept-package-agreements --accept-source-agreements
```

Si instalas el 18 en vez del 17, ajusta el `.env` en la sección 6:
`SQL_ODBC_DRIVER=ODBC Driver 18 for SQL Server`.

**Cierra y reabre la sesión SSH** para que el PATH actualizado tenga efecto.

---

## 4. Traer el código

### Opción A — clonar (si la PC llega a GitHub)

```powershell
New-Item -ItemType Directory -Force -Path C:\Apps | Out-Null
Set-Location C:\Apps
git clone https://github.com/luiperilla/BP_BB_RFID.git
Set-Location C:\Apps\BP_BB_RFID\ServidorRFID
```

Si el repositorio es privado, Git pedirá credenciales. Usa un **token de acceso
personal** con permiso de solo lectura, o una **clave de despliegue**. No uses
tu contraseña de GitHub ni dejes el token escrito en ningún archivo de la PC.

### Opción B — copiar por SCP (si la PC no tiene salida a GitHub)

Esto se ejecuta **en tu portátil**, no en la PC destino:

```powershell
ssh smartapps@192.168.2.112 "mkdir C:\Apps\BP_BB_RFID"
scp -r C:\Users\dibareno\Documents\GitHub\BP_BB_RFID\ServidorRFID smartapps@192.168.2.112:C:/Apps/BP_BB_RFID/
```

Es la ruta más probable en una red de planta. Como contrapartida, las
actualizaciones futuras hay que volver a copiarlas a mano.

---

## 5. Crear el entorno conda

Desde `C:\Apps\BP_BB_RFID\ServidorRFID`:

```powershell
conda create -n servidor-rfid --override-channels -c conda-forge python=3.12 flask=3.0.3 python-dotenv=1.0.1 waitress=3.0.0 pyodbc=5.1.0 sqlalchemy=2.0.31 -y
```

`--override-channels -c conda-forge` es lo que evita los canales de pago de
Anaconda. El archivo `environment.yml` declara lo mismo, pero el comando
explícito es inmune a la configuración global que tenga la máquina — por eso es
la forma recomendada aquí.

Verifica:

```powershell
conda run -n servidor-rfid python -c "import flask, waitress, pyodbc, dotenv; print('dependencias OK'); print(pyodbc.drivers())"
```

Debe imprimir `dependencias OK` y la lista de drivers ODBC disponibles.

> **Sin internet en la PC destino:** empaqueta el entorno desde tu portátil con
> `conda-pack` (`conda pack -n servidor-rfid -o servidor-rfid.tar.gz`), cópialo
> por SCP y descomprímelo allí. Tu portátil debe ser Windows x64 igual que el
> destino.

---

## 6. Configurar el `.env`

Este archivo no está en el repositorio: hay que crearlo en la PC destino.

```powershell
@"
PUERTO=8390
ESCUCHAR_EN=0.0.0.0
HILOS=8

SQL_MOTOR=sqlserver
SQL_SERVIDOR=192.168.2.23
SQL_BASE_DATOS=SmartFactory
SQL_TABLA=Hornos_Buffer_RFID
SQL_WINDOWS_AUTH=false
SQL_USUARIO=Consulta
SQL_PASSWORD=<la contraseña del login SQL>
SQL_ODBC_DRIVER=ODBC Driver 17 for SQL Server

MODELO_PLACEHOLDER=N/A
NIVEL_LOG=INFO
"@ | Set-Content -Path .env -Encoding UTF8
```

**Este archivo contiene ahora un secreto.** Restringe sus permisos NTFS
inmediatamente, para que solo lo lean Administradores y la cuenta del servicio:

```powershell
icacls .env /inheritance:r
icacls .env /grant "BUILTIN\Administradores:(R)" "NT AUTHORITY\SYSTEM:(R)"
icacls .env
```

Y comprueba el contenido **sin volcar la contraseña a la consola** (queda en el
historial y en la sesión SSH):

```powershell
Get-Content .env | Where-Object { $_ -notmatch "PASSWORD" }
```

> **Alternativa sin contraseña en disco:** con `SQL_WINDOWS_AUTH=true` (y
> `SQL_USUARIO`/`SQL_PASSWORD` vacíos) el servicio se autentica con la cuenta de
> dominio bajo la que corre, y no hay ningún secreto en el archivo. Requiere que
> el DBA cree el login para `HADES\smartapps` y que tengas la contraseña de esa
> cuenta para instalar el servicio.

---

## 7. Probar a mano antes de instalar el servicio

```powershell
conda run -n servidor-rfid --no-capture-output python servidor_rfid.py
```

Debe terminar en `Servidor listo y escuchando`. Si la conexión a SQL falla, el
proceso se detiene ahí mismo con el error en pantalla — resuélvelo antes de
seguir. Errores típicos:

| Mensaje | Causa |
|---|---|
| `Data source name not found` | El nombre en `SQL_ODBC_DRIVER` no coincide con el instalado |
| `Login failed for user` | Tu cuenta no tiene permisos en `SmartFactory` |
| `Invalid object name` | La tabla no existe o el nombre está mal escrito |

Desde **otra ventana SSH**, comprueba que responde:

```powershell
Invoke-RestMethod http://localhost:8390/salud
```

Detén la prueba con `Ctrl+C` antes de continuar.

---

## 8. Instalar el servicio

```powershell
.\instalar_servicio.ps1 -CuentaServicio "HADES\smartapps"
```

Pedirá la contraseña de esa cuenta de forma interactiva (no queda en el
historial ni en disco). El script localiza solo el entorno conda, registra el
servicio con arranque automático, reinicio ante fallo y rotación de logs.

Si PowerShell bloquea la ejecución del script:

```powershell
Unblock-File .\instalar_servicio.ps1
powershell -ExecutionPolicy Bypass -File .\instalar_servicio.ps1 -CuentaServicio "HADES\smartapps"
```

---

## 9. Abrir el puerto solo a los C5

Como las pistolas tienen **IP fija**, la regla puede listar exactamente esas
direcciones en vez de abrir una subred entera. Es la diferencia entre exponer
el servicio a uno o dos equipos concretos y exponerlo a cientos:

```powershell
$PISTOLAS = @("192.168.2.150", "192.168.2.151")   # IPs fijas de los C5
New-NetFirewallRule -DisplayName "Servidor RFID 8390" -Direction Inbound `
    -Protocol TCP -LocalPort 8390 -RemoteAddress $PISTOLAS -Action Allow
```

Para añadir una pistola nueva más adelante:

```powershell
$actuales = (Get-NetFirewallRule -DisplayName "Servidor RFID 8390" | Get-NetFirewallAddressFilter).RemoteAddress
Set-NetFirewallRule -DisplayName "Servidor RFID 8390" -RemoteAddress ($actuales + "192.168.2.152")
```

No abras el puerto a toda la red: los endpoints `/eliminar_*` no tienen
autenticación, así que esta regla es hoy el único control de acceso al
servicio.

---

## 10. Verificación final

```powershell
Get-Service ServidorRFID | Format-List Name, Status, StartType
Invoke-RestMethod http://localhost:8390/salud
Get-Content .\logs\servidor_rfid.log -Tail 20
```

Prueba de humo del ciclo completo (usa una OP de prueba, no una real):

```powershell
$b = "http://localhost:8390"
Invoke-RestMethod "$b/guardar_datos" -Method Post -ContentType "application/json" -Body '{"OP":"999999","CLV_MODELO":"N/A","EPC":"999999"}'
Invoke-RestMethod "$b/consulta_por_epc?epc=999999"
Invoke-RestMethod "$b/eliminar_por_epc?epc=999999" -Method Delete
```

Prueba de reinicio — lo que de verdad valida que es un servicio:

```powershell
Restart-Computer -Force
# esperar ~2 min, reconectar por SSH
Invoke-RestMethod http://localhost:8390/salud
```

Por último, comprueba desde un C5 real que la pantalla de Asociar carga el
spinner de modelo y guarda un tag.

---

## 11. Operación

| Acción | Comando |
|---|---|
| Estado | `Get-Service ServidorRFID` |
| Reiniciar | `Restart-Service ServidorRFID` |
| Detener | `Stop-Service ServidorRFID` |
| Ver logs en vivo | `Get-Content .\logs\servidor_rfid.log -Wait -Tail 30` |
| Errores de arranque | `Get-Content .\logs\servicio_stderr.log -Tail 40` |
| Cambiar configuración | editar `.env` y `Restart-Service ServidorRFID` |

**Actualizar el código:**

```powershell
Set-Location C:\Apps\BP_BB_RFID
Stop-Service ServidorRFID
git pull
Restart-Service ServidorRFID
Invoke-RestMethod http://localhost:8390/salud
```

**Desinstalar / revertir:**

```powershell
Set-Location C:\Apps\BP_BB_RFID\ServidorRFID
.\instalar_servicio.ps1 -Desinstalar
Remove-NetFirewallRule -DisplayName "Servidor RFID 8390"
conda env remove -n servidor-rfid -y
```

---

## Pendientes conocidos

- **Sin autenticación y en HTTP plano.** Cualquiera que alcance el puerto 8390
  puede borrar asociaciones. La regla de firewall acota el riesgo pero no lo
  elimina.
- **El keystore de firma de la app** está en el repositorio con su contraseña
  en `app/build.gradle`. Hay que rotar la clave y limpiar el historial de Git;
  no afecta a este servidor, pero sigue abierto.
