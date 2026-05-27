#!/usr/bin/env python3
"""Convert POST /api/benchmark/ramp JSON to phase2_results.csv."""
import json
import sys


def main() -> None:
    out_path = sys.argv[1]
    data = json.load(sys.stdin)
    lines = ["executor,rps,duration_ms,throughput_rps,p95_ms,p99_ms"]
    for step in data.get("steps", []):
        rps = step["requestsPerSecond"]
        for name, stats in step["results"].items():
            lines.append(
                f"{name},{rps},{stats['durationMs']},{stats['throughputRps']},"
                f"{stats['latencyP95Ms']},{stats['latencyP99Ms']}"
            )
    text = "\n".join(lines) + "\n"
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"Wrote {out_path} ({len(lines) - 1} rows)")
    for step in data["steps"]:
        rps = step["requestsPerSecond"]
        p = step["results"].get("PLATFORM", {})
        v = step["results"].get("VIRTUAL", {})
        print(
            f"  RPS {rps:>5}: PLATFORM p99={p.get('latencyP99Ms', '-')}ms | "
            f"VIRTUAL p99={v.get('latencyP99Ms', '-')}ms"
        )


if __name__ == "__main__":
    main()
