# Seldia Music TV - Instalación por ADB en TVs locales (PowerShell)
# TV1: 192.168.100.177  TV2: 192.168.100.129  (ver script .bat para IPs canónicas)
# Uso:
#   .\install_tv.ps1            -> compila e instala en AMBAS
#   .\install_tv.ps1 -Target tv1
#   .\install_tv.ps1 -Target tv2
#   .\install_tv.ps1 -Disconnect
param(
  [ValidateSet('both','tv1','tv2')][string]$Target = 'both',
  [switch]$Disconnect
)

$ErrorActionPreference = 'Continue'
$Apk = 'app\build\outputs\apk\debug\app-debug.apk'
$Pkg = 'com.seldia.music'
$Act = 'com.seldia.music/.ui.MainActivity'
$TV1 = '192.168.100.129'
$TV2 = '192.168.100.177'

$Adb = $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT | Where-Object { $_ } | ForEach-Object { Join-Path $_ 'platform-tools\adb.exe' } | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $Adb) { $Adb = 'adb' }
Write-Host "[Seldia Music TV] adb: $Adb"

if ($Disconnect) {
  & $Adb disconnect $TV1
  & $Adb disconnect $TV2
  return
}

Write-Host '[1/3] Compilando...'
& .\gradlew.bat ':app:assembleDebug'
if (-not (Test-Path $Apk)) { Write-Error "No se encontró $Apk"; exit 1 }

function Install-One($Ip, $Name) {
  Write-Host "`n[$Name] $Ip : adb connect..."
  & $Adb connect $Ip
  Start-Sleep -Seconds 2
  Write-Host "Instalando en $Name..."
  & $Adb -s "$($Ip):5555" install -r $Apk
  if ($LASTEXITCODE -eq 0) {
    Write-Host "Lanzando en $Name..."
    & $Adb -s "$($Ip):5555" shell am start -n $Act
  } else {
    Write-Warning "Fallo instalación en $Name. Activa depuración ADB en la TV."
  }
}

if ($Target -in @('both','tv1')) { Install-One $TV1 'TV1' }
if ($Target -in @('both','tv2')) { Install-One $TV2 'TV2' }

Write-Host "`nListo. Busca 'Seldia Music TV' en el menú principal del TV (Leanback)."
