@echo off
REM Script di compilazione e avvio – EasyEvent Versione 2
REM Attiva UTF-8 nel terminale Windows
chcp 65001 > nul

set SRC_DIR=src\main\java
set OUT_DIR=out

echo === EasyEvent V2 – Compilazione ===
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
dir /s /b "%SRC_DIR%\*.java" > sources.txt
javac -encoding UTF-8 -d "%OUT_DIR%" @sources.txt

echo === EasyEvent V2 – Avvio ===
java -cp "%OUT_DIR%" it.easyevent.MainV2
pause
