#!/bin/bash
# EasyEvent V3 - Script di compilazione ed esecuzione (Linux/macOS)
mkdir -p out
javac -d out @sources.txt && java -cp out it.easyevent.MainV3
