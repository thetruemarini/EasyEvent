@echo off
REM EasyEvent V4 - Script di compilazione ed esecuzione (Windows)
mkdir out 2>nul
javac -d out @sources.txt && java -cp out it.easyevent.MainV4
