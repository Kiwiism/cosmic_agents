#!/usr/bin/env python3
"""
Parser for Cosmic MonitoredChrLogger CP_USER_MOVE packet dumps.

Used as both:
  * a LIBRARY: import LINE_RE, parse_packet, ts_ms, load_log
  * a CLI:    `py tools/scripts/parse_movement_log.py <path-to-log>`

Log line format produced by MonitoredChrLogger (see src/main/java/net/packet/logging/MonitoredChrLogger.java):
    HH:MM:SS.mmm [thread] INFO  logging.MonitoredChrLogger - {acct}-{chrName} {packetId}(0x{packetIdHex})-{HEX BYTES}

Wire format after the `{packetId}-`:
    9 bytes  — header skipped by MovePlayerHandler (portal/field key + echo pos)
    1 byte   — numCommands
    For each command byte:
        cmd 0 / 5 / 17                              : ABSOLUTE  — xpos(h) ypos(h) xwobble(h) ywobble(h) fh(h) state(B) dur(H)   = 15B
        cmd 1 / 2 / 6 / 12 / 13 / 16 / 18 / 19 / 20 / 22 : RELATIVE  — xpos(h) ypos(h) state(B) dur(H)                          =  9B
        cmd 3 / 4 / 7 / 8 / 9 / 11                 : TELEPORT  — xpos(h) ypos(h) xwobble(h) ywobble(h) state(B)                = 11B
        cmd 14                                     : JUMP-DOWN flag, 9B raw
        cmd 10                                     : CHANGE-EQUIP, 1B
        cmd 15                                     : JUMP-DOWN-MOVE — xpos ypos xw yw fh ofh state dur                         = 17B
        cmd 21                                     : ARAN, 3B

For ABS/TELEPORT/JUMPDOWN, xwobble/ywobble are the `pixelsPerSecond` velocity
components at that waypoint — NOT jitter. This is the primary source used to
back out client physics constants.
"""
import re
import struct
import sys
from datetime import datetime
from pathlib import Path

LINE_RE = re.compile(
    r'^(\d{2}:\d{2}:\d{2}\.\d{3}).*-\s+\S+-\S+\s+(\d+)(?:\(0x[0-9A-Fa-f]+\))?-([0-9A-Fa-f ]+)\s*$'
)

ABS_CMDS = {0, 5, 17}
REL_CMDS = {1, 2, 6, 12, 13, 16, 18, 19, 20, 22}
TEL_CMDS = {3, 4, 7, 8, 9, 11}

def _u8(b, i):  return b[i], i + 1
def _s16(b, i): return struct.unpack_from('<h', b, i)[0], i + 2
def _u16(b, i): return struct.unpack_from('<H', b, i)[0], i + 2


def parse_packet(payload: bytes):
    """Return a list of fragment dicts parsed from one CP_USER_MOVE payload."""
    if len(payload) < 10:
        return []
    i = 9  # MovePlayerHandler.p.skip(9)
    n, i = _u8(payload, i)
    frags = []
    for _ in range(n):
        if i >= len(payload):
            break
        cmd, i = _u8(payload, i)
        f = {'cmd': cmd}
        try:
            if cmd in ABS_CMDS:
                f['x'], i = _s16(payload, i)
                f['y'], i = _s16(payload, i)
                f['vx'], i = _s16(payload, i)
                f['vy'], i = _s16(payload, i)
                f['fh'], i = _s16(payload, i)
                f['state'], i = _u8(payload, i)
                f['dur'], i = _u16(payload, i)
                f['type'] = 'ABS'
            elif cmd in REL_CMDS:
                f['dx'], i = _s16(payload, i)
                f['dy'], i = _s16(payload, i)
                f['state'], i = _u8(payload, i)
                f['dur'], i = _u16(payload, i)
                f['type'] = 'REL'
            elif cmd in TEL_CMDS:
                f['x'], i = _s16(payload, i)
                f['y'], i = _s16(payload, i)
                f['vx'], i = _s16(payload, i)
                f['vy'], i = _s16(payload, i)
                f['state'], i = _u8(payload, i)
                f['type'] = 'TEL'
            elif cmd == 14:
                i += 9
                f['type'] = 'JD14'
            elif cmd == 10:
                i += 1
                f['type'] = 'EQUIP'
            elif cmd == 15:
                f['x'], i = _s16(payload, i)
                f['y'], i = _s16(payload, i)
                f['vx'], i = _s16(payload, i)
                f['vy'], i = _s16(payload, i)
                f['fh'], i = _s16(payload, i)
                f['ofh'], i = _s16(payload, i)
                f['state'], i = _u8(payload, i)
                f['dur'], i = _u16(payload, i)
                f['type'] = 'JUMPDOWN'
            elif cmd == 21:
                i += 3
                f['type'] = 'ARAN'
            else:
                f['type'] = f'UNK({cmd})'
                break
        except struct.error:
            break
        frags.append(f)
    return frags


def ts_ms(hms: str) -> int:
    dt = datetime.strptime(hms, '%H:%M:%S.%f')
    return (dt.hour * 3600 + dt.minute * 60 + dt.second) * 1000 + dt.microsecond // 1000


def load_log(path):
    """Parse an entire log file. Returns list of {ts_ms, pid, frags, raw_len}."""
    rows = []
    for line in Path(path).read_text().splitlines():
        m = LINE_RE.match(line)
        if not m:
            continue
        ts, pid, hexstr = m.groups()
        payload = bytes.fromhex(hexstr.replace(' ', ''))
        rows.append({
            'ts_ms': ts_ms(ts),
            'pid': int(pid),
            'frags': parse_packet(payload),
            'raw_len': len(payload),
        })
    return rows


def format_frag(f) -> str:
    if f['type'] in ('ABS', 'JUMPDOWN'):
        return (f"{f['type'][:3]} p=({f['x']:+5d},{f['y']:+5d}) "
                f"v=({f['vx']:+5d},{f['vy']:+5d}) fh={f.get('fh','?')} "
                f"st={f['state']} d={f['dur']}")
    if f['type'] == 'REL':
        return f"REL d=({f['dx']:+5d},{f['dy']:+5d}) st={f['state']} d={f['dur']}"
    if f['type'] == 'TEL':
        return (f"TEL p=({f['x']:+5d},{f['y']:+5d}) "
                f"v=({f['vx']:+5d},{f['vy']:+5d}) st={f['state']}")
    return f['type']


def main(argv):
    if len(argv) < 2:
        print("usage: parse_movement_log.py <log-path> [<log-path> ...]", file=sys.stderr)
        return 2
    for path in argv[1:]:
        rows = load_log(path)
        print(f"=== {path} ({len(rows)} packets) ===")
        if not rows:
            continue
        t0 = rows[0]['ts_ms']
        for r in rows:
            dt = r['ts_ms'] - t0
            print(f"+{dt:5d}ms pid={r['pid']} len={r['raw_len']}: "
                  + " | ".join(format_frag(f) for f in r['frags']))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
