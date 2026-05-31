#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -x ".venv/bin/python" ]; then
  echo "Creating virtual environment..."
  python3 -m venv .venv
fi

echo "Installing dependencies..."
".venv/bin/python" -m pip install -r requirements.txt

echo "Starting WealthLab server on http://0.0.0.0:8000"
exec ".venv/bin/python" -m uvicorn main:app --host 0.0.0.0 --port 8000
