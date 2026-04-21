@echo off
REM ================================================
REM EasyEvent V5 - Script di compilazione ed esecuzione
REM Windows  |  Richiede: JDK 17+ nel PATH
REM ================================================
chcp 65001 > nul

set SRC_DIR=src
set OUT_DIR=out

echo === EasyEvent V5 - Build e Run ===

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
if not exist "data" mkdir "data"
if not exist "batch_examples" mkdir "batch_examples"

echo [1/3] Ricerca sorgenti Java...
dir /s /b "%SRC_DIR%\*.java" > sources.txt

echo [2/3] Compilazione...
javac -encoding UTF-8 -d "%OUT_DIR%" @sources.txt
if errorlevel 1 (
    echo ERRORE: Compilazione fallita.
    pause
    exit /b 1
)
echo Compilazione completata.

echo [3/3] Avvio applicazione...
echo.
java -ea -cp "%OUT_DIR%" easyevent.MainV5
pause