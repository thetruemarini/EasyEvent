#!/bin/bash
# Script di compilazione e avvio – EasyEvent Versione 2
set -e

SRC_DIR="src/main/java"
OUT_DIR="out"

echo "=== EasyEvent V2 – Compilazione ==="
mkdir -p "$OUT_DIR"
find "$SRC_DIR" -name "*.java" > sources.txt
javac -encoding UTF-8 -d "$OUT_DIR" @sources.txt

echo "=== EasyEvent V2 – Avvio ==="
java -cp "$OUT_DIR" it.easyevent.MainV2
