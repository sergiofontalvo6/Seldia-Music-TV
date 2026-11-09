@echo off
REM ============================================================
REM  Seldia Music TV - Instalación por ADB en TVs locales
REM  TV1: 192.168.100.129
REM  TV2: 192.168.100.177
REM
REM  Uso:
REM    install_tv.bat            -> compila e instala en AMBAS TVs
REM    install_tv.bat tv1        -> solo TV1
REM    install_tv.bat tv2        -> solo TV2
REM    install_tv.bat disconnect -> desconecta ambas
REM ============================================================
setlocal
cd /d "%~dp0"

set ID_TV1=192.168.100.129
set ID_TV2=192.168.100.177
set APK=app\build\outputs\apk\debug\app-debug.apk
set PKG=com.seldia.music
set ACT=com.seldia.music/.ui.MainActivity

REM Localizar adb: PATH o ANDROID_HOME / ANDROID_SDK_ROOT
if defined ANDROID_HOME set ADB=%ANDROID_HOME%\platform-tools\adb.exe
if not defined ADB if defined ANDROID_SDK_ROOT set ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe
if not defined ADB set ADB=adb
echo [Seldia Music TV] usando adb: %ADB%

if /i "%~1"=="disconnect" (
    %ADB% disconnect %ID_TV1%
    %ADB% disconnect %ID_TV2%
    echo Desconectado.
    exit /b 0
)

echo [1/4] Compilando APK de depuración...
call gradlew.bat :app:assembleDebug --offline 2>nul || call gradlew.bat :app:assembleDebug
if errorlevel 1 (
    echo ERROR: gradlew assembleDebug falló. Revisa JDK 17 y Android SDK.
    exit /b 1
)
if not exist "%APK%" (
    echo ERROR: no se encontró %APK%
    exit /b 1
)
echo OK: %APK%

call :install_one %ID_TV1% "TV1"
if /i "%~1"=="tv1" goto :done
if /i "%~1"=="tv2" goto :solo_tv2

call :install_one %ID_TV2% "TV2"
goto :done

:solo_tv2
call :install_one %ID_TV2% "TV2"

:done
echo.
echo Listo. En cada TV: busca "Seldia Music TV" en el menú principal (Leanback).
echo Lanzamiento manual: adb -s ^<ip^>:5555 shell am start -n %ACT%
endlocal
exit /b 0

:install_one
set IP=%~1
set NAME=%~2
echo.
echo [Seldia Music TV] %NAME% %IP%: adb connect...
%ADB% connect %IP%
timeout /t 2 /nobreak >nul
echo Instalando APK en %NAME% (%IP%:5555)...
%ADB% -s %IP%:5555 install -r "%APK%"
if errorlevel 1 (
    echo AVISO: instalación en %NAME% falló. Comprueba que la TV tenga
    echo "Depuración ADB / USB debugging" activado y que el puerto 5555 esté abierto.
    ping -n 2 -w 1000 127.0.0.1 >nul
    exit /b 0
)
echo Lanzando Seldia Music TV en %NAME%...
%ADB% -s %IP%:5555 shell am start -n %ACT%
exit /b 0
