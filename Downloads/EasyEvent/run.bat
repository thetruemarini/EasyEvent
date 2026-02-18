@echo off
REM ================================================
REM EasyEvent - Script di compilazione ed esecuzione
REM Versione 1 - Windows
REM Richiede: JDK 11+ nel PATH
REM ================================================

echo === EasyEvent V1 - Build e Run ===

REM Crea directory
if not exist out mkdir out
if not exist data mkdir data

echo [1/3] Ricerca sorgenti Java...
dir /s /b src\main\java\*.java > sources.txt

echo [2/3] Compilazione...
javac -d out -encoding UTF-8 @sources.txt
if errorlevel 1 (
    echo ERRORE: Compilazione fallita.
    pause
    exit /b 1
)
echo Compilazione completata.

echo [3/3] Avvio applicazione...
echo.
java -ea -cp out it.easyevent.MainV1
pause
