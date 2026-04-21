#!/bin/bash
# ================================================
# EasyEvent V5 - Script di compilazione ed esecuzione
# Linux / macOS  |  Richiede: JDK 17+ nel PATH
# ================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src"
OUT_DIR="$SCRIPT_DIR/out"
DATA_DIR="$SCRIPT_DIR/data"

echo "=== EasyEvent V5 - Build & Run ==="

mkdir -p "$OUT_DIR"
mkdir -p "$DATA_DIR"
mkdir -p "$SCRIPT_DIR/batch_examples"

echo "[1/3] Ricerca sorgenti Java..."
find "$SRC_DIR" -name "*.java" > sources.txt
COUNT=$(wc -l < "$SCRIPT_DIR/sources.txt")
echo "      Trovati $COUNT file .java"

echo "[2/3] Compilazione..."
javac -encoding UTF-8 -d "$OUT_DIR" @"$SCRIPT_DIR/sources.txt"
echo "      Compilazione completata."

echo "[3/3] Avvio applicazione..."
echo ""
cd "$SCRIPT_DIR"
java -ea -cp "$OUT_DIR" easyevent.MainV5