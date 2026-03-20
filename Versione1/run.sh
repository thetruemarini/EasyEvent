#!/bin/bash
# Script di compilazione e avvio – EasyEvent Versione 1 (Linux/MacOS)
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src/main/java"
OUT_DIR="$SCRIPT_DIR/out"
DATA_DIR="$SCRIPT_DIR/data"

echo "=== EasyEvent V1 – Compilazione ==="
mkdir -p "$OUT_DIR"
mkdir -p "$DATA_DIR"
find "$SRC_DIR" -name "*.java" > "$SCRIPT_DIR/sources.txt"
javac -encoding UTF-8 -ea -d "$OUT_DIR" @"$SCRIPT_DIR/sources.txt"

echo "=== EasyEvent V1 – Avvio ==="
cd "$SCRIPT_DIR"
java -ea -cp "$OUT_DIR" it.easyevent.MainV1