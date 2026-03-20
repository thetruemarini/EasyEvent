@echo off
REM Script di compilazione e avvio – EasyEvent Versione 1 (Windows)
REM Attiva UTF-8 nel terminale Windows
chcp 65001 > nul

set SCRIPT_DIR=%~dp0
set OUT_DIR=%~dp0out
set DATA_DIR=%~dp0data

echo === EasyEvent V1 – Compilazione ===
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"
dir /s /b "%SCRIPT_DIR%src\main\java\*.java" > "%SCRIPT_DIR%sources.txt"
javac -encoding UTF-8 -ea -d "%OUT_DIR%" @"%SCRIPT_DIR%sources.txt"

echo === EasyEvent V1 – Avvio ===
java -ea -cp "%OUT_DIR%" it.easyevent.MainV1
pause