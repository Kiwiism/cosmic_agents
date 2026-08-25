# Autonomous Henesys PQ (HPQ)

HPQ is an isolated party-quest system. It shares the standardized PQ lobby machinery, but its session state, coordinator, capacity policy, population selector, observation fixtures, tuning keys, and cleanup are HPQ-owned. Changing HPQ tuning does not change KPQ, LPQ, or OPQ.

## Normal duration

- Main HPQ: up to 10 minutes from event entry.
- Clear/reward map: no separate countdown until the party chooses its next destination.
- Optional Pig Town bonus: 5 minutes after the party leader asks Tommy to enter it.

A successful Agent party should normally clear the main objective in less than the 10-minute hard limit. The optional bonus is separate from the main objective and is not required to count HPQ as complete.

## Recommended visual observation

Use a level 10-255 GM/operator character. Level 25 is a convenient baseline.

1. Warp to the HPQ entrance map, `100000200`.
2. Leave any existing party.
3. Run `!hpqtest withme 3 12345`.
4. Wait for the two named `HPQerNN` Agents to appear.
5. Create a party, invite both Agents, and keep yourself as leader.
6. Talk to Tory normally and choose the HPQ entry option.
7. Stay with the party and observe the ordinary event.

Useful commands during the run:

- `!hpqtest status` shows names, roles, seed targets and inventories, six flower states, Moon Bunny HP, cakes, timer, bonus choice, and last-progress age.
- `!hpqtest pause` and `!hpqtest resume` freeze or resume Agent coordination.
- `!hpqtest checkpoint bunny` is a test-only shortcut to the Moon Bunny defense phase.
- `!hpqtest checkpoint ninecakes` prepares the defense phase with nine cakes so the final ordinary cake production and pickup remain visible.
- `!hpqtest complete` prepares ten cakes. In the recommended human-led run, talk to Growlie and submit them normally; in an Agent-only run, the Agent leader submits them through the normal NPC script.
- `!hpqtest bonus skip|enter` overrides the optional bonus choice for this observation session.
- `!hpqtest fail coordinator|leader|bunny|timeout` invokes a controlled live failure path. The leader option refuses to disconnect a human.
- `!hpqtest stop` safely removes the observation Agents while retaining their reusable backing characters.

For an unassisted verification, do not use checkpoints. Watch for all six required behaviors:

1. Agents kill the ordinary seed-dropping monsters.
2. They pick up the six seed types and drop them at matching moonflower reactors.
3. Exactly one Moon Bunny appears after all six reactors resolve.
4. Non-leader Agents defend the Bunny while the event leader collects rice cakes.
5. At ten cakes, the leader submits them to Growlie through the ordinary script.
6. Agents claim their reward through Tory and exit; the session and HPQ-owned party are cleaned up.

`!hpqtest start 3 12345` runs an Agent-only party. After its status reports a live event, use `!hpqtest spectate` to enter the private instance without becoming an event participant. Do not attack, loot, use NPCs, or touch reactors while attached. Use `!hpqtest return` to leave; stop and terminal cleanup also return the observer automatically before event disposal.

## Acceptance matrix

Use a different deterministic seed for each run and do not use checkpoints for the primary acceptance pass.

| Shape | Command and assembly | Leader responsibilities |
|---|---|---|
| 3 Agents | `!hpqtest start 3 31001`, then `spectate` after entry | Agent leader performs all leader-only actions |
| 4 Agents | `!hpqtest start 4 41001`, then `spectate` after entry | Agent leader performs all leader-only actions |
| 1 human + 4 Agents | `!hpqtest withme 5 51001`; create the party and invite all four Agents | Human enters through Tory, collects ten cakes, submits to Growlie, and chooses the exit |
| 1 human + 5 Agents, Agent leader | `!hpqtest agentleader 6 61001`; accept the Agent leader's normal invitation | Agent enters, collects and submits; human participates as an ordinary non-leader |

If the Agent-led invitation expires, use `!hpqtest invite`. The harness holds admission until the authoritative party contains the requested total number of members.

In the Agent-led mixed run, the human may fight and plant seeds but should leave rice cakes for the Agent leader. If the human accidentally picks one up, drop it near the Agent leader so the actual leader can reach the ten cakes required by Growlie's authored script.

For every run, record:

1. Entry succeeds and every authoritative party member reaches the same private instance.
2. Six authored seed reactors resolve and exactly one Moon Bunny appears.
3. Ten ordinary cakes are produced and the actual party leader submits them.
4. Direct reward or bonus behavior matches the session choice.
5. The completion notification appears, all Agent runtimes disconnect, and a subsequent `!hpqtest status` reports no active observation run.

After the four normal runs, perform these failure and recovery checks:

1. Start another Agent-only run and use `fail coordinator`; verify one idempotent failure cleanup.
2. Start another Agent-led run and use `fail leader`; verify the event closes and the whole owned party is released.
3. Reach the Bunny checkpoint and use `fail bunny`; verify the authored five-second exile/failure behavior.
4. Enter a live event and use `fail timeout`; verify the authored event timeout removes the party and the watchdog releases the session.
5. Manually fill the leader's relevant reward inventories before one clear; verify Tory leaves that character in place with the ordinary insufficient-space message, then frees it after space is made.
6. Repeat a normal run with previously used `HPQerNN` characters to prove no party, item, event, lobby, or activity ownership leaked.

## Bonus behavior

Agent-led sessions use the HPQ-only `ENTER_BY_DEFAULT` setting, initially `false`. `!hpqtest bonus enter` changes only the current observation session. When entering Pig Town, Agents farm ordinary configured monsters for the HPQ-only dwell duration and then use Tommy and Tory through their normal scripts.

Human leaders always control the bonus choice. Agents wait on the clear map for the configured human decision window. Choosing Tommy moves the event team into Pig Town; choosing Tory takes the direct reward exit. If the decision window expires, Agent members take the direct reward route without forcing an action on the human character.

## Liveness and cleanup

An HPQ-only watchdog runs independently of ordinary Agent ticks. It claims work only after the current execution lease expires, selects another living Agent deterministically, and fails an orphaned session when no live Agent executor remains. Termination remains idempotent and owns event disposal plus HPQ-owned party cleanup.

## Production rollout

The autonomous population director is present but initially disabled with:

`server.agents.capabilities.partyquest.hpq.AgentHpqPopulationRuntime.ENABLED: 'false'`

After the full visual smoke test passes, enable that HPQ-only switch. The default capacity is two HPQ instances per channel, with at most one background instance and one lobby reserved for human parties. These limits are entirely separate from KPQ and other PQs.

Before enabling it, also run one concurrent check: keep an autonomous/background HPQ active on a channel and confirm an ordinary human party can still obtain the reserved second event lobby. Repeat on another channel to confirm instance and capacity isolation.

The production director admits only enabled, managed population Agents that are alive, level 10-255, not already in a party, not already in HPQ, and outside the HPQ re-entry cooldown. Background HPQ always uses exact gameplay; no abstract completion or injected production items are used.
