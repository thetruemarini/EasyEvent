@echo off
REM Script di compilazione e avvio – EasyEvent Versione 2 (Windows)
REM Attiva UTF-8 nel terminale Windows
chcp 65001 > nul

set SCRIPT_DIR=%~dp0
set OUT_DIR=%~dp0out

echo === EasyEvent V2 – Compilazione ===
cd /d "%SCRIPT_DIR%"
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
dir /s /b "%SCRIPT_DIR%src\main\java\*.java" > "%SCRIPT_DIR%sources.txt"
javac -encoding UTF-8 -d "%OUT_DIR%" @"%SCRIPT_DIR%sources.txt"

echo === EasyEvent V2 – Avvio ===
cd /d "%SCRIPT_DIR%"
java -cp "%OUT_DIR%" it.easyevent.MainV2
pause