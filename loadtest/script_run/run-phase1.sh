#!/bin/bash
# run-phase1.sh

cd "$(dirname "$0")/.."

ITERATIONS=${1:-100}
HOST=${2:-localhost}
PORT=${3:-8080}

EXECUTORS=("SINGLE" "PLATFORM" "VIRTUAL")
MODES=("LOCK" "NO_LOCK")

mkdir -p ../frontend/public/results_csv

CSV_FILE="../frontend/public/results_csv/phase1_results.csv"

# Write CSV header if not exists
if [ ! -f "$CSV_FILE" ]; then
    echo "executor,mode,iteration,amounts,expected_val,actual_val,is_accurate,duration_ms" > "$CSV_FILE"
fi

for executor in "${EXECUTORS[@]}"; do
    for mode in "${MODES[@]}"; do
        for ((i=1; i<=ITERATIONS; i++)); do
            echo "Running Phase 1 - Executor: $executor, Mode: $mode, Iteration: $i"
            
            uuid=$(uuidgen | cut -c1-8)
            customerId="t-p1-${mode}-${uuid}"
            
            exec_lower=$(echo "$executor" | tr '[:upper:]' '[:lower:]')
            mode_lower=$(echo "$mode" | tr '[:upper:]' '[:lower:]' | tr '_' '-')
            targetUrl="http://${HOST}:${PORT}/api/rewards/${exec_lower}/${mode_lower}"
            
            start_time=$(python3 -c 'import time; print(int(time.time() * 1000))')
            
            expectedPoints=0
            amounts_str=""
            
            # Bắn 5 request song song bằng curl
            for j in {1..5}; do
                amt=$(( (RANDOM % 90) + 10 ))
                expectedPoints=$((expectedPoints + amt * 10))
                
                if [ $j -eq 5 ]; then
                    amounts_str="${amounts_str}${amt}"
                else
                    amounts_str="${amounts_str}${amt}|"
                fi
                
                # Chạy ẩn (background)
                curl -s -X POST "$targetUrl" \
                     -H "Content-Type: application/json" \
                     -d "{\"customerId\":\"$customerId\", \"transactionId\":\"txn-${uuid}-${j}\", \"amount\":$amt}" > /dev/null &
            done
            
            # Đợi cả 5 request hoàn thành
            wait
                   
            end_time=$(python3 -c 'import time; print(int(time.time() * 1000))')
            duration=$((end_time - start_time))
            
            # Query backend
            actualPoints=$(curl -s "http://${HOST}:${PORT}/api/rewards/points/${customerId}" | grep -o '"primaryPoints":[0-9]*' | cut -d':' -f2)
            
            if [ -z "$actualPoints" ]; then actualPoints=0; fi
            isAccurate="false"
            if [ "$actualPoints" -eq "$expectedPoints" ]; then isAccurate="true"; fi
            
            # Append to CSV
            echo "${executor},${mode},${i},${amounts_str},${expectedPoints},${actualPoints},${isAccurate},${duration}" >> "$CSV_FILE"
                 
        done
    done
done
