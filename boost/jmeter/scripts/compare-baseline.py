#!/usr/bin/env python3
"""So sánh JTL hiện tại với baseline. Dùng cho regression gate trong CI.

Logic:
- Nếu KHÔNG có baseline: print warning, chỉ check absolute threshold (P95, error%).
- Nếu có baseline: check thêm % regression (P95 không được xấu hơn baseline quá 15%).
- Exit 0 nếu pass, 1 nếu fail.
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path


def parse_jtl(path: Path):
    if not path.exists():
        return None
    elapsed = []
    ok = 0
    fail = 0
    min_ts = None
    max_ts = None
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                ts = int(row["timeStamp"])
                ms = int(row["elapsed"])
            except (KeyError, ValueError):
                continue
            elapsed.append(ms)
            if row.get("success", "").lower() == "true":
                ok += 1
            else:
                fail += 1
            min_ts = ts if min_ts is None else min(min_ts, ts)
            max_ts = ts if max_ts is None else max(max_ts, ts)
    total = ok + fail
    if total == 0:
        return None
    elapsed.sort()
    p95 = elapsed[int(len(elapsed) * 0.95) - 1] if len(elapsed) > 1 else elapsed[0]
    p99 = elapsed[int(len(elapsed) * 0.99) - 1] if len(elapsed) > 1 else elapsed[0]
    avg = sum(elapsed) / total
    error_pct = fail * 100.0 / total
    dur_s = (max_ts - min_ts) / 1000.0 if max_ts and min_ts and max_ts > min_ts else 0
    tps = total / dur_s if dur_s else 0
    return {
        "samples": total,
        "ok": ok,
        "fail": fail,
        "error_pct": round(error_pct, 2),
        "avg_ms": round(avg, 2),
        "p95_ms": p95,
        "p99_ms": p99,
        "throughput_rps": round(tps, 2),
    }


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--current", required=True, type=Path)
    p.add_argument("--baseline", required=True, type=Path)
    p.add_argument("--p95-threshold", type=int, default=500)
    p.add_argument("--error-threshold", type=float, default=1.0)
    p.add_argument("--regression-pct", type=float, default=15.0,
                   help="P95 không được xấu hơn baseline quá % này")
    args = p.parse_args()

    cur = parse_jtl(args.current)
    if cur is None:
        print(f"[gate] ❌ không đọc được current JTL: {args.current}")
        return 1
    print(f"[gate] current : {cur}")

    # Absolute gates
    absolute_fail = False
    if cur["p95_ms"] > args.p95_threshold:
        print(f"[gate] ❌ P95 {cur['p95_ms']}ms > ngưỡng {args.p95_threshold}ms")
        absolute_fail = True
    if cur["error_pct"] > args.error_threshold:
        print(f"[gate] ❌ Error% {cur['error_pct']} > ngưỡng {args.error_threshold}")
        absolute_fail = True

    # Baseline compare
    base = parse_jtl(args.baseline)
    if base is None:
        print(f"[gate] ⚠ chưa có baseline tại {args.baseline}; "
              f"sao chép JTL hiện tại làm baseline để lần sau so sánh.")
        args.baseline.parent.mkdir(parents=True, exist_ok=True)
        args.baseline.write_bytes(args.current.read_bytes())
    else:
        print(f"[gate] baseline: {base}")
        if base["p95_ms"] > 0:
            delta_pct = (cur["p95_ms"] - base["p95_ms"]) * 100.0 / base["p95_ms"]
            print(f"[gate] P95 delta vs baseline: {delta_pct:+.1f}%")
            if delta_pct > args.regression_pct:
                print(f"[gate] ❌ P95 regression {delta_pct:.1f}% > {args.regression_pct}%")
                absolute_fail = True

    return 1 if absolute_fail else 0


if __name__ == "__main__":
    sys.exit(main())
