#!/usr/bin/env python3
"""
Back out client physics constants from parsed CP_USER_MOVE fragments.

Usage:
    py tools/scripts/analyze_physics.py <monitored-packets-log> [<more-logs> ...]

For each log, prints:
  * walk velocity samples (ground state 2/3, fh != 0)
  * jump kicks (REL cmd=1) — initial (vx, vy) impulse
  * max/min vertical velocity observed
  * gravity samples computed as Δvy/Δt between consecutive airborne ABS frames
  * air-borne horizontal velocity distribution
  * climb-state fragments (states 16/17 for rope/ladder)

Physics interpretation notes:
  * wobble fields in ABS/TELEPORT/JUMPDOWN fragments are velocity in px/s
  * duration is the time the client took to reach THAT waypoint from the
    previous one — use that as Δt for gravity computation.
  * REL cmd=1 (jump) carries the initial impulse (vx, vy) at the moment of
    jump release — vy is typically −555 for ground jumps, ±162 / −277 for
    rope jumps from state 16/17.
  * terminal fall velocity is observable as sustained vy=+670 across multiple
    510 ms packets in long-fall logs.
"""
import sys
from pathlib import Path

# Ensure sibling parse_movement_log is importable regardless of CWD
sys.path.insert(0, str(Path(__file__).resolve().parent))
from parse_movement_log import load_log, format_frag  # noqa: E402


def dump(path):
    rows = load_log(path)
    print(f"\n=================== {path} ===================")
    print(f"Parsed {len(rows)} packets")
    if not rows:
        return rows
    t0 = rows[0]['ts_ms']
    for r in rows:
        dt = r['ts_ms'] - t0
        print(f"+{dt:5d}ms: " + " | ".join(format_frag(f) for f in r['frags']))
    return rows


def analyze(path, rows=None):
    if rows is None:
        rows = load_log(path)
    all_frags = [f for r in rows for f in r['frags']]

    print(f"\n----- {path} analysis -----")

    walk_vx = sorted({abs(f['vx']) for f in all_frags
                      if f.get('type') == 'ABS'
                      and f.get('state') in (2, 3)
                      and f.get('fh', 0) != 0})
    print(f"Walk |vx| (ground state 2/3): {walk_vx}")

    jump_kicks = [(f['dx'], f['dy']) for f in all_frags
                  if f.get('type') == 'REL' and f['cmd'] == 1]
    print(f"Jump kicks (REL cmd=1), count={len(jump_kicks)}")
    if jump_kicks:
        print(f"  unique |dx|: {sorted({abs(x) for x, _ in jump_kicks})}")
        print(f"  unique |dy|: {sorted({abs(y) for _, y in jump_kicks})}")

    abs_vy = [f['vy'] for f in all_frags if f.get('type') == 'ABS']
    if abs_vy:
        print(f"Max +vy (fall): {max(abs_vy)}   Min vy (most-neg, mid-jump): {min(abs_vy)}")

    # Gravity samples: consecutive airborne ABS frames within one packet,
    # same jumping state (6/7), fh==0, duration >= 30ms (noise filter)
    samples = []
    for r in rows:
        prev = None
        for f in r['frags']:
            is_air = (f.get('type') == 'ABS'
                      and f.get('fh', -1) == 0
                      and f.get('state') in (6, 7))
            if is_air:
                if prev is not None and f.get('dur', 0) >= 30:
                    dvy = f['vy'] - prev['vy']
                    g = dvy / (f['dur'] / 1000.0)
                    samples.append((prev['vy'], f['vy'], f['dur'], g))
                prev = f
            else:
                prev = None
    if samples:
        print("Gravity samples (prev_vy -> new_vy  Δt  g px/s²):")
        for p, n, d, g in samples[:20]:
            print(f"  {p:+5d} -> {n:+5d}  Δt={d:4d}ms  g={g:+8.1f}")
        gs = [s[3] for s in samples]
        filt = [g for g in gs if 1500 < g < 2500]
        if filt:
            mean = sum(filt) / len(filt)
            print(f"  filtered (1500<g<2500): n={len(filt)} "
                  f"mean={mean:.1f} min={min(filt):.1f} max={max(filt):.1f}")

    air_vx = sorted({abs(f['vx']) for f in all_frags
                     if f.get('type') == 'ABS'
                     and f.get('state') in (6, 7)
                     and f.get('fh', 0) == 0})
    print(f"Air |vx| (airborne state 6/7): {air_vx}")

    climb = [f for f in all_frags
             if f.get('type') == 'ABS'
             and f.get('state') in (16, 17)
             and abs(f.get('vx', 0)) < 5]
    if climb:
        print(f"Climb-state fragments (state 16/17): n={len(climb)} "
              f"(rope/ladder — Δy across 510ms intervals gives climb speed)")

    states = sorted({f.get('state') for f in all_frags if 'state' in f})
    print(f"Distinct states seen: {states}")


def main(argv):
    if len(argv) < 2:
        print("usage: analyze_physics.py [--no-dump] <log-path> [<log-path> ...]", file=sys.stderr)
        return 2
    args = argv[1:]
    no_dump = False
    if args[0] == '--no-dump':
        no_dump = True
        args = args[1:]
    for path in args:
        rows = None if no_dump else dump(path)
        analyze(path, rows)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
