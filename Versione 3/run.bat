@echo off
REM EasyEvent V3 - Script di compilazione ed esecuzione (Windows)
mkdir out 2>nul
javac -d out @sources.txt && java -cp out it.easyevent.MainV3
