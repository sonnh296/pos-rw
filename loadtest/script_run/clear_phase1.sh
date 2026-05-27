#!/bin/bash
# clear_phase1.sh

cd "$(dirname "$0")/.."
rm -f ../frontend/public/results_csv/phase1_results.csv
echo "Cleared Phase 1 results from frontend/public/results_csv/"
