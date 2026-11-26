#!/usr/bin/env python3
"""Summarise Android gfxinfo framestats from paired captures on the same device."""
import argparse
import csv
import json
import math
from pathlib import Path
import re
from statistics import fmean, median


def capture(text, hz):
    header = None
    samples = {}
    for row in csv.reader(text.splitlines()):
        row = [field.strip() for field in row]
        if row and row[0] == "---PROFILEDATA---":
            header = None
        if row and row[0] == "Flags" and {"IntendedVsync", "FrameCompleted"}.issubset(row):
            header = row
            continue
        if header is None or len(row) < len(header):
            continue
        frame = dict(zip(header, row))
        try:
            if int(frame["Flags"]) != 0:
                continue
            start, end = int(frame["IntendedVsync"]), int(frame["FrameCompleted"])
            if not 0 < start < end < 2**63 - 1:
                continue
            deadline = int(frame.get("FrameDeadline", "0"))
            vsync_id = int(frame.get("FrameTimelineVsyncId", "-1"))
            key = (vsync_id, start, end) if vsync_id > 0 else (start, end)
            samples[key] = ((end - start) / 1_000_000, end > deadline if start < deadline < 2**63 - 1 else None)
        except (ValueError, KeyError):
            continue

    if not samples:
        raise ValueError("No completed application frames in capture")
    values = sorted(duration for duration, _ in samples.values())
    missed = [missed for _, missed in samples.values() if missed is not None]
    total = re.search(r"^Total frames rendered:\s*(\d+)", text, re.MULTILINE)
    janky = re.search(r"^Janky frames:\s*(\d+)", text, re.MULTILINE)
    budget = 1000 / hz
    return {
        "recorded_frames": len(values),
        "nominal_budget_ms": round(budget, 2),
        "average_ms": round(fmean(values), 2),
        "median_ms": round(median(values), 2),
        "p95_ms": round(values[math.ceil(0.95 * len(values)) - 1], 2),
        "p99_ms": round(values[math.ceil(0.99 * len(values)) - 1], 2),
        "over_nominal_budget_frames": sum(duration > budget for duration in values),
        "frames_with_deadlines": len(missed),
        "missed_frame_deadline_count": sum(missed) if missed else None,
        "android_window_frames_rendered": int(total.group(1)) if total else None,
        "android_window_janky_frames": int(janky.group(1)) if janky else None,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--hz", type=float, required=True)
    args = parser.parse_args()
    if not math.isfinite(args.hz) or args.hz <= 0:
        parser.error("--hz must be finite and positive")
    try:
        result = {name: capture(path.read_text(), args.hz)
                  for name, path in (("baseline", args.baseline), ("candidate", args.candidate))}
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
