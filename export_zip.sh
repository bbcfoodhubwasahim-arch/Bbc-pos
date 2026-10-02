#!/bin/bash
# Export Project ZIP containing ONLY the current/latest APK and clean source tree.
# Excludes all old/historical APKs, caches, build files, and ignored directories.

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OUTPUT_FILE="${1:-BBC_POS_export.zip}"
python3 export_project_zip.py "$OUTPUT_FILE"
