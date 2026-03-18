#!/bin/bash
# ================================================
# EasyEvent - Script di compilazione ed esecuzione
# Versione 1
# Richiede: JDK 11+ (testato su Java 21)
# ================================================

set -e

echo "=== EasyEvent V1 - Build & Run ==="

# Rileva la directory dello script
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src/main/java"
OUT_DIR="$SCRIPT_DIR/out"
DATA_DIR="$SCRIPT_DIR/data"

# Crea directory output e data
mkdir -p "$OUT_DIR"
mkdir -p "$DATA_DIR"

echo "[1/3] Ricerca sorgenti Java..."
find "$SRC_DIR" -name "*.java" > "$SCRIPT_DIR/sources.txt"
COUNT=$(wc -l < "$SCRIPT_DIR/sources.txt")
echo "      Trovati $COUNT file .java"

echo "[2/3] Compilazione..."
javac -d "$OUT_DIR" -encoding UTF-8 @"$SCRIPT_DIR/sources.txt"
echo "      Compilazione completata."

echo "[3/3] Avvio applicazione..."
echo ""
cd "$SCRIPT_DIR"
java -ea -cp "$OUT_DIR" it.easyevent.MainV1
