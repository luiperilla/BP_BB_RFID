<#
.SYNOPSIS
    Instala el Servidor RFID como Servicio de Windows usando NSSM.

.DESCRIPTION
    Deja el servidor corriendo permanentemente: arranca solo al encender la PC,
    se reinicia solo si el proceso muere y escribe sus logs en la carpeta logs/.

    Requisitos previos (ver DESPLIEGUE.md para el paso a paso por SSH):
      1. Entorno conda creado:  conda env create -f environment.yml
         (o, si se prefiere venv, una carpeta .venv en esta misma carpeta)
      2. Archivo .env creado y configurado (copiar de .env.example).
      3. NSSM en el PATH, o indicar su ruta con -Nssm.  https://nssm.cc/download

    Ejecutar en una sesión de PowerShell CON PRIVILEGIOS DE ADMINISTRADOR.
    Por SSH basta con que la cuenta pertenezca al grupo Administradores.

.PARAMETER CuentaServicio
    Cuenta de dominio bajo la que corre el servicio, en formato DOMINIO\usuario.
    Es lo que hace funcionar la autenticación integrada de Windows contra SQL
    Server. Si se omite, el servicio corre como LocalSystem, que normalmente NO
    tiene permisos sobre la base de datos.

.PARAMETER EntornoConda
    Nombre (o ruta completa) del entorno conda. Por defecto "servidor-rfid".
    Se ignora si existe una carpeta .venv, que tiene prioridad.

.EXAMPLE
    .\instalar_servicio.ps1 -CuentaServicio "AGP\svc_rfid"

.EXAMPLE
    .\instalar_servicio.ps1 -Desinstalar
#>

[CmdletBinding()]
param(
    [string]$NombreServicio = "ServidorRFID",
    [string]$CuentaServicio,
    [string]$EntornoConda = "servidor-rfid",
    [string]$Nssm = "nssm",
    [switch]$Desinstalar
)

$ErrorActionPreference = "Stop"

# --- Comprobar que somos administrador -------------------------------------
$identidad = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identidad)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Se necesitan privilegios de Administrador para instalar o quitar un servicio."
}

# --- Comprobar que NSSM existe ---------------------------------------------
$rutaNssm = (Get-Command $Nssm -ErrorAction SilentlyContinue).Source
if (-not $rutaNssm) {
    throw "No se encontró NSSM ('$Nssm'). Descárgalo de https://nssm.cc/download y ponlo en el PATH, o pasa la ruta con -Nssm."
}

# --- Desinstalación ---------------------------------------------------------
if ($Desinstalar) {
    & $rutaNssm stop $NombreServicio
    & $rutaNssm remove $NombreServicio confirm
    Write-Host "Servicio '$NombreServicio' eliminado." -ForegroundColor Green
    return
}

# --- Rutas (ancladas a la carpeta de este script) ---------------------------
$CarpetaBase = $PSScriptRoot
$Script      = Join-Path $CarpetaBase "servidor_rfid.py"
$CarpetaLogs = Join-Path $CarpetaBase "logs"
$ArchivoEnv  = Join-Path $CarpetaBase ".env"

if (-not (Test-Path $Script)) {
    throw "No se encontró servidor_rfid.py en $CarpetaBase"
}
if (-not (Test-Path $ArchivoEnv)) {
    throw "Falta el archivo .env en $CarpetaBase. Cópialo de .env.example y complétalo antes de instalar."
}

# --- Localizar el intérprete de Python --------------------------------------
# Se prefiere .venv si existe; si no, se resuelve el entorno conda. En ambos
# casos el servicio apunta al python.exe por RUTA ABSOLUTA: un Servicio de
# Windows no ejecuta perfiles ni 'conda activate', así que no puede depender
# del PATH del usuario.
$Python = $null
$PrefijoConda = $null

$venvPython = Join-Path $CarpetaBase ".venv\Scripts\python.exe"
if (Test-Path $venvPython) {
    $Python = $venvPython
    Write-Host "Usando el entorno venv: $Python" -ForegroundColor Cyan
}
else {
    $conda = (Get-Command conda -ErrorAction SilentlyContinue).Source
    if (-not $conda) {
        throw "No hay .venv ni se encontró 'conda' en el PATH. Crea el entorno con: conda env create -f environment.yml"
    }

    # 'conda run' devuelve la ruta real del intérprete del entorno. Se descartan
    # las líneas vacías y cualquier aviso que conda pueda imprimir antes.
    $selector = if ($EntornoConda -match '[\\/]') { "-p" } else { "-n" }
    $Python = (& $conda run $selector $EntornoConda python -c "import sys; print(sys.executable)" 2>$null |
        Where-Object { $_ -and $_.Trim().EndsWith("python.exe") } |
        Select-Object -Last 1)
    if ($Python) { $Python = $Python.Trim() }

    if (-not $Python -or -not (Test-Path $Python)) {
        throw "No se pudo resolver el entorno conda '$EntornoConda'. Créalo con: conda env create -f environment.yml"
    }
    $PrefijoConda = Split-Path $Python -Parent
    Write-Host "Usando el entorno conda '$EntornoConda': $Python" -ForegroundColor Cyan
}

New-Item -ItemType Directory -Force -Path $CarpetaLogs | Out-Null

# --- Instalar ---------------------------------------------------------------
Write-Host "Instalando el servicio '$NombreServicio'..." -ForegroundColor Cyan

& $rutaNssm install $NombreServicio $Python $Script
& $rutaNssm set $NombreServicio AppDirectory $CarpetaBase
& $rutaNssm set $NombreServicio DisplayName "Servidor RFID (Hornos / Buffer)"
& $rutaNssm set $NombreServicio Description "API de asociacion de tags RFID para las terminales Chainway C5."
& $rutaNssm set $NombreServicio Start SERVICE_AUTO_START

# En un entorno conda, algunas librerías cargan DLLs desde Library\bin, que
# normalmente añade 'conda activate'. Como el servicio llama a python.exe
# directamente, esas rutas se inyectan aquí en el PATH del proceso.
if ($PrefijoConda) {
    $rutasConda = @(
        $PrefijoConda,
        (Join-Path $PrefijoConda "Library\mingw-w64\bin"),
        (Join-Path $PrefijoConda "Library\usr\bin"),
        (Join-Path $PrefijoConda "Library\bin"),
        (Join-Path $PrefijoConda "Scripts"),
        (Join-Path $PrefijoConda "bin")
    ) -join ";"
    & $rutaNssm set $NombreServicio AppEnvironmentExtra "PATH=$rutasConda;$env:PATH"
}

# Reinicio automático si el proceso muere: espera 5 s y vuelve a levantarlo.
& $rutaNssm set $NombreServicio AppExit Default Restart
& $rutaNssm set $NombreServicio AppRestartDelay 5000

# Salida de consola a archivo, con rotación (además del log propio de la
# aplicación). Aquí caen los errores de arranque anteriores al logging.
& $rutaNssm set $NombreServicio AppStdout (Join-Path $CarpetaLogs "servicio_stdout.log")
& $rutaNssm set $NombreServicio AppStderr (Join-Path $CarpetaLogs "servicio_stderr.log")
& $rutaNssm set $NombreServicio AppRotateFiles 1
& $rutaNssm set $NombreServicio AppRotateBytes 2000000

# Al detener el servicio, dar 15 s para cerrar conexiones antes de matarlo.
& $rutaNssm set $NombreServicio AppStopMethodConsole 15000

# --- Cuenta de servicio -----------------------------------------------------
if ($CuentaServicio) {
    # La contraseña se pide de forma interactiva: no queda en el historial de
    # PowerShell ni en ningún archivo del repositorio.
    $clave = Read-Host "Contraseña de $CuentaServicio" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($clave)
    try {
        $enClaro = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
        & $rutaNssm set $NombreServicio ObjectName $CuentaServicio $enClaro
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        Remove-Variable enClaro -ErrorAction SilentlyContinue
    }
    Write-Host "El servicio correra como $CuentaServicio." -ForegroundColor Green
}
else {
    Write-Warning "Sin -CuentaServicio el servicio corre como LocalSystem, que normalmente NO tiene acceso a SQL Server con autenticacion de Windows."
}

# --- Arrancar ---------------------------------------------------------------
& $rutaNssm start $NombreServicio
Start-Sleep -Seconds 5
& $rutaNssm status $NombreServicio

Write-Host ""
Write-Host "Comprueba que responde con:" -ForegroundColor Green
Write-Host '    Invoke-RestMethod http://localhost:8390/salud'
Write-Host ""
Write-Host "Logs de la aplicacion: $CarpetaLogs\servidor_rfid.log"
Write-Host "Errores de arranque:   $CarpetaLogs\servicio_stderr.log"
Write-Host ""
Write-Host "No olvides abrir el puerto solo para la subred de los C5:" -ForegroundColor Yellow
Write-Host '    New-NetFirewallRule -DisplayName "Servidor RFID 8390" -Direction Inbound -Protocol TCP -LocalPort 8390 -RemoteAddress 172.16.60.0/24 -Action Allow'
