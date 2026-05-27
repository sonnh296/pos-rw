#!/bin/bash
# clear_phase2.sh

cd "$(dirname "$0")/.."
rm -f ../frontend/public/results_csv/phase2_results.csv
echo "Cleared Phase 2 results from frontend/public/results_csv/"
