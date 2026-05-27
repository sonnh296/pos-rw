#!/bin/bash
# run-phase2.sh — Phase 2: hiệu năng PLATFORM vs VIRTUAL (lock path, production-like)

cd "$(dirname "$0")/.."

ITERATIONS=${1:-2}
TOTAL_REQUESTS=${2:-5000}
HOST=${3:-localhost}
PORT=${4:-8080}

EXECUTORS=("PLATFORM" "VIRTUAL")

mkdir -p result_jtl ../frontend/public/results_csv

CSV_FILE="../frontend/public/results_csv/phase2_results.csv"

# Write CSV header if not exists
if [ ! -f "$CSV_FILE" ]; then
    echo "executor,iteration,duration_ms,throughput_rps,p95_ms" > "$CSV_FILE"
fi

# Inline python script to parse JTL and return JSON with throughput and p95
cat << 'EOF' > parse_jtl.py
import sys, csv, json

def parse_jtl(filepath):
    elapsed_times = []
    try:
        with open(filepath, 'r') as f:
            reader = csv.DictReader(f)
            min_ts = float('inf')
            max_ts = 0
            for row in reader:
                ts = int(row['timeStamp'])
                elapsed = int(row['elapsed'])
                elapsed_times.append(elapsed)
                min_ts = min(min_ts, ts)
                max_ts = max(max_ts, ts + elapsed)
        
        if not elapsed_times:
            return {"throughput": 0, "p95": 0}
            
        elapsed_times.sort()
        idx = int(0.95 * len(elapsed_times))
        p95 = elapsed_times[min(idx, len(elapsed_times)-1)]
        
        duration_sec = (max_ts - min_ts) / 1000.0
        throughput = len(elapsed_times) / duration_sec if duration_sec > 0 else 0
        
        return {"throughput": round(throughput, 2), "p95": p95}
    except Exception as e:
        return {"throughput": 0, "p95": 0}

if __name__ == "__main__":
    result = parse_jtl(sys.argv[1])
    print(json.dumps(result))
EOF

for executor in "${EXECUTORS[@]}"; do
    for ((i=1; i<=ITERATIONS; i++)); do
        echo "Running Phase 2 - Executor: $executor, Iteration: $i (Requests: $TOTAL_REQUESTS)"
        
        if [ "$executor" == "PLATFORM" ]; then
            targetPath="/api/rewards/demo/platform/lock"
        else
            targetPath="/api/rewards/demo/virtual/lock"
        fi
        
        exec_lower=$(echo "$executor" | tr '[:upper:]' '[:lower:]')
        jtlPath="result_jtl/custom_p2_${exec_lower}_${i}.jtl"
        rm -f "$jtlPath"
        
        start_time=$(python3 -c 'import time; print(int(time.time() * 1000))')
        
        jmeter -n -t jmeter_jmx/custom_test.jmx \
               -l "$jtlPath" \
               -Jthreads="$TOTAL_REQUESTS" \
               -Jloops=1 \
               -JtargetPath="$targetPath" \
               -Jport="$PORT" \
               -Jhost="$HOST" > /dev/null 2>&1
               
        end_time=$(python3 -c 'import time; print(int(time.time() * 1000))')
        duration=$((end_time - start_time))
        
        # Parse JTL using the python script
        parsed=$(python3 parse_jtl.py "$jtlPath")
        throughput=$(echo "$parsed" | grep -o '"throughput": [0-9.]*' | cut -d':' -f2 | xargs)
        p95=$(echo "$parsed" | grep -o '"p95": [0-9]*' | cut -d':' -f2 | xargs)
        
        if [ -z "$throughput" ]; then throughput=0; fi
        if [ -z "$p95" ]; then p95=0; fi
        
        # Append to CSV
        echo "${executor},${i},${duration},${throughput},${p95}" >> "$CSV_FILE"
        
        # Ngủ 10 giây để Hệ điều hành dọn dẹp các TCP Sockets cũ (tránh lỗi kẹt cổng TIME_WAIT)
        echo "Waiting 10 seconds for TCP socket cleanup..."
        sleep 1
             
    done
done

rm -f parse_jtl.py
