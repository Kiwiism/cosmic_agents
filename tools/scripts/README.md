---
tools:
  - id: parse_movement_log
    path: tools/scripts/parse_movement_log.py
    kind: library+cli
    runtime: python3
    purpose: >
      Parse Cosmic MonitoredChrLogger dumps of CP_USER_MOVE packets into
      structured movement fragments (position, velocity, foothold, stance,
      duration).
    input:
      files: [logs/monitored-packets*.log]
      line_format: "HH:MM:SS.mmm [thread] INFO  logging.MonitoredChrLogger - {acct}-{chr} {packetId}(0x{packetIdHex})-{HEX BYTES}"
    output: stdout (human-readable, one line per packet)
    exports: [LINE_RE, parse_packet, ts_ms, load_log, format_frag]
    cli: "py tools/scripts/parse_movement_log.py <log-path> [<log-path> ...]"
    references:
      - src/main/java/net/packet/logging/MonitoredChrLogger.java
      - src/main/java/net/server/channel/handlers/MovePlayerHandler.java
      - src/main/java/net/server/channel/handlers/AbstractMovementPacketHandler.java

  - id: analyze_physics
    path: tools/scripts/analyze_physics.py
    kind: cli
    runtime: python3
    depends_on: [parse_movement_log]
    purpose: >
      Back out client physics constants (walk velocity, gravity, jump kick,
      max fall, rope-jump kick, climb speed) from parsed movement fragments.
    cli: "py tools/scripts/analyze_physics.py [--no-dump] <log-path> [<log-path> ...]"
    prints:
      - walk |vx| samples (ground state 2/3)
      - jump kicks (REL cmd=1) with unique |dx| / |dy|
      - max +vy (fall) and min vy (jump)
      - gravity samples Δvy/Δt for airborne state 6/7 frames
      - air-borne |vx| distribution
      - climb-state fragments (state 16/17 rope/ladder)
      - distinct stance codes observed
    physics_notes:
      wobble_field: "Despite the name in the Java parser, xwobble/ywobble are the client's pixelsPerSecond velocity at that waypoint."
      duration: "fragment.dur is the time the client took to reach THAT waypoint from the previous one — use as Δt for gravity."
      jump_cmd: "REL cmd=1 carries the initial (vx, vy) impulse at jump release."
      terminal_vy: "Max fall is observable as sustained vy=+670 across consecutive 510ms packets."
      gravity_filter: "Landing snaps produce negative/huge g samples; filter 1500<g<2500 for clean grav readings."
    known_findings:
      walk_speed_pxs: 125
      gravity_pxs2: 2000
      jump_initial_vy: 555
      max_fall_pxs: 670
      climb_speed_pxs: 100
      ground_jump_kick: "(±walk_vx, -555)"
      rope_jump_kick: "(±162, -277) — fixed vx, not walk-derived"
      down_jump: "starts with vy≈0, gravity only"

workflow_example:
  description: Calibrate BotPhysicsEngine against a new movement capture
  steps:
    - "py tools/scripts/parse_movement_log.py logs/monitored-packets-<scenario>.log  # sanity-check parsing"
    - "py tools/scripts/analyze_physics.py --no-dump logs/monitored-packets-<scenario>.log  # extract constants"
    - "compare outputs to src/main/java/server/bots/BotPhysicsEngine.java::Config"
    - "edit Config values; keep old values in `// was ...` comments"
    - "mvn compile to verify"

capture_howto:
  where_to_toggle: "/monitor <chrName> (in-game GM command — see MonitoredChrLogger.toggleMonitored)"
  log_destination: logs/monitored-packets*.log
  blocked_opcodes: [GENERAL_CHAT, TAKE_DAMAGE, MOVE_PET, MOVE_LIFE, NPC_ACTION, FACE_EXPRESSION]
---

# Cosmic movement-log analysis tools

Two small Python scripts used to reverse-engineer real-client physics from
MonitoredChrLogger packet dumps. All metadata for agents/users is in the YAML
frontmatter above; this body is just for quick human skim.

## Files
- `parse_movement_log.py` — parser (library + CLI)
- `analyze_physics.py`    — physics extractor (depends on the parser)

## Quick usage
```bash
# Dump packets from one or more logs:
py tools/scripts/parse_movement_log.py logs/monitored-packets-<scenario>.log

# Extract physics constants (skip per-packet dump):
py tools/scripts/analyze_physics.py --no-dump logs/monitored-packets-<scenario>.log
```

## Capturing a new log
In-game, a GM toggles monitoring on a character via `MonitoredChrLogger.toggleMonitored(chrId)`
(typically wired to a GM command). Packets are appended to `logs/monitored-packets*.log`.
To get useful physics data:
1. Capture at **speed=100, jump=100** (BASE stats) for baseline calibration.
2. Record distinct scenarios in separate files (`-walk`, `-longfall`, `-ropeclimb`, etc.)
   so each analysis run is focused.
3. Walk a flat stretch → then jump → then climb a rope → then jump off the rope →
   then drop from a high platform for terminal-velocity samples.

## Fragment glossary
| Type | Source cmd | Fields |
|---|---|---|
| ABS   | 0, 5, 17              | x, y, vx, vy, fh, state, dur |
| REL   | 1, 2, 6, 12, 13, 16, 18, 19, 20, 22 | dx, dy, state, dur |
| TEL   | 3, 4, 7, 8, 9, 11     | x, y, vx, vy, state |
| JUMPDOWN | 15                 | x, y, vx, vy, fh, ofh, state, dur |
| JD14  | 14                    | 9B raw (jump-down flag) |
| EQUIP | 10                    | 1B (change equip slot) |
| ARAN  | 21                    | 3B |

Stance codes seen in captures (non-exhaustive):
`2/3` walk R/L · `4/5` stand R/L · `6/7` jump R/L · `8/9` prone R/L ·
`10/11` fly/chair · `16/17` rope-climb R/L
