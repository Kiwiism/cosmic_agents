# TypeSafe System One (Jev) judgments

Optional typed-decision layer for the places where the server otherwise approximates language
understanding with regexes, word sets, or parsed LLM prose. Jev answers closed questions
(Choice / Noul / Score) over a JSON `state` and returns probabilities and confidence in about
100 ms; it never generates text and it is not used for anything numeric. Code keeps every
rule, threshold, and side effect. Every feature is off by default and falls back to the
existing deterministic behaviour whenever the API is unconfigured, slow, failing, or unsure.

## Setup

1. Put the key in the environment the server process inherits (never in a committed file):
   `TYPESAFE_API_KEY=...`. Optional: `TYPESAFE_ENDPOINT`, `TYPESAFE_MODEL` (default
   `jev-latest`), `TYPESAFE_TIMEOUT_MS` (default 1500). See `.env.example`.
2. Turn on the judgments you want:

| Switch | Where | Values | What it gates |
|---|---|---|---|
| `AGENT_TYPESAFE_CHAT_INTENT_MODE` | `agent-engine.yaml` | OFF / SHADOW / LIVE | companion chat that no classifier matched |
| `AGENT_TYPESAFE_DIRECTOR_RANKING_ENABLED` | `agent-engine.yaml` | true / false | Director panel action selection and training-map ranking |
| `AGENT_TYPESAFE_PARTY_QUEST_DIALOGUE_MODE` | `agent-engine.yaml` | OFF / SHADOW / LIVE | human questions during KPQ Stage 1 |
| `AGENT_TYPESAFE_TRADE_REPLY_MODE` | `agent-engine.yaml` | OFF / SHADOW / LIVE | free-form replies to a pending item offer |
| `USE_TYPESAFE_NAME_SCREEN` | `config.yaml` | true / false | second opinion on new character names |
| `USE_TYPESAFE_REPORT_TRIAGE` | `config.yaml` | true / false | severity / category triage of player reports |

Thresholds, budgets and timeouts are `tuning:` keys in `agent-engine.yaml` under the
declaring class names (`server.agents.integration.typesafe.*`,
`server.agents.capabilities.dialogue.jev.*`, ...).

3. Start chat, party-quest dialogue, and trade replies in **SHADOW**. These paths log
   judgments for deterministic misses (`[typesafe-chat]`, `[typesafe-kpq]`,
   `[typesafe-offer]`) without acting. Tune the confidence/probability thresholds on those
   logs before switching to LIVE. Director ranking and moderation have boolean switches;
   they do not support shadow mode. Name screening logs rejections, and report triage logs
   completed judgments.
   `Server.diagnosticLines()` carries a `TypeSafe:` line with request, failure, breaker and
   token counters.

## Components

```
server.agents.integration.typesafe
  TypeSafeSettings              env-driven key / endpoint / model / timeout
  JevRequest, JevQuestion       request shape (state + named Choice/Noul/Score questions)
  JevResponse, JevAnswer        typed answers, probabilities, confidence, usage, latency
  JevTransport, JevHttpTransport POST /v1/systemone; one immediate retry on 529/5xx/IO, none on 429
  JevCircuitBreaker             opens after N consecutive failures for OPEN_MS
  JevUsageMeter                 per-kind requests/tokens/latency/cost + observed rate
  JevCostModel, JevUsageReporter  price -> money; periodic [typesafe-usage] log + CSV
  cost.JevCostProjector, cost.JevRequestSamples  spend projection tool (tools/typesafe-cost.bat)
  JevClient                     process-wide facade; ask() never throws, askAsync() for core callers
  JevJudgmentMode               OFF / SHADOW / LIVE
  TypeSafeDirectorProposalProvider  Director provider wrapping the Ollama provider as fallback
  state.JevState                prioritised state blocks + token budget (drops lowest priority first)
  state.JevStateBands           numbers -> words (hp bands, level gaps, relative time, counts)
  state.JevTokenEstimator       pre-send estimate; the API's usage field is authoritative
server.agents.integration.cosmic.typesafe
  AgentChatJevStateFactory      Cosmic adapter: message, speaker, bot, channel, last command
server.agents.capabilities.dialogue.jev
  AgentChatIntent, AgentChatIntentArgument   closed intent set with what/not_for/examples
  AgentChatIntentCatalog        one fan-out request; answers -> canonical command phrase
  AgentChatIntentJudge          pure decision: ACT / ASK / IGNORE / UNAVAILABLE
  AgentChatIntentRuntime        async gateway wiring (SYSTEM_ONE_NETWORK lane), re-dispatch
server.agents.capabilities.partyquest.dialogue
  AgentPartyQuestDialogueJudge  Nouls over a message + stage context
server.agents.capabilities.trade.jev
  AgentOfferReplyJudge          accept / decline / question / unrelated for pending offers
server.security.typesafe
  JevNameScreen                 impersonates_staff / offensive Nouls at character creation
  JevReportTriage               severity Score + category Choice for player reports
```

## How the chat intent path works

`AgentChatRouteCoordinator.handleAgentChat` runs the existing dispatcher first. Only when it
reports `false` does `AgentChatIntentRuntime.judgeUnmatched` build the state, submit one
request on the `SYSTEM_ONE_NETWORK` async lane, and read the answers:

* `intent` (Choice over every `AgentChatIntent`, including `chat_not_a_command`) plus the
  argument Choices (`info_topic`, `item_category`, `support_target`, ...) asked speculatively
  in the same request, and `addressed_to_bot` (Noul).
* The canonical phrase is the one the deterministic classifiers already understand
  (`grind`, `trade scrolls`, `unequip hat`, `need hp pot`); an argument intent is only as
  confident as its weakest answer. `AgentChatIntentCatalogCompatibilityTest` pins every
  canonical phrase to a classifier predicate so a confident judgment can never be dropped.
* `confidence >= ACT_CONFIDENCE` re-dispatches the phrase through `AgentChatMailboxDispatcher`;
  `>= ASK_CONFIDENCE` queues `did you mean '...'?`; otherwise the message continues to the
  social-dialogue fallback exactly as before. Timeouts and failures count as unhandled.

## Design rules these components follow

* Never on a packet or mailbox thread: Agent callers use the async gateway, core callers use
  `JevClient.askAsync`, and name screening (login thread, not latency-sensitive) is the one
  bounded blocking call.
* State is small and scoped; numbers arrive as words, ids as names, untrusted player text in
  one clearly named field. Block priorities and the token budget are explicit
  (`JevState.build`), and dropped blocks are logged.
* One request per event with all questions together; only the answers the chosen branch
  needs are read.
* Thresholds live in `agent-engine.yaml`, documented like every other tuning key. No numeric
  constants in agent sources.
* `config.yaml` and `agent-engine.yaml` are decoded as US-ASCII; keep additions 7-bit.

## Cost: projecting before, watching while running

Only input tokens are billed (`PRICE_USD_PER_MILLION_INPUT_TOKENS` in `agent-engine.yaml`,
$0.042 as published). Every request carries a usage `kind` (`chat-intent`, `party-quest`,
`offer-reply`, `director-select`, `director-rank`, `name-screen`, `report-triage`) so spend is
attributed per judgment.

**Before enabling anything** — project spend for an expected bot population:

```
tools\typesafe-cost.bat --bots 20 --commands-per-bot-hour 6 --hours-online 12 --live
```

`--live` sends one representative request per kind (about seven requests, well under a cent)
and uses the API's own `input_tokens`; without it the same production request builders are
measured with the offline estimator. Other knobs: `--miss-rate` (share of commands the regexes
miss and Jev is asked about, default 0.25), `--pq-sessions`, `--pq-messages`, `--offers`,
`--free-form-rate`, `--new-characters`, `--reports`, `--director-queries`. Measured on
2026-09-20 with jev-1.13.0: chat-intent ~3.5k tokens, director-rank (5 maps) ~1.6k,
director-select ~0.7k, report-triage ~0.75k, offer-reply ~0.6k, name-screen ~0.5k,
party-quest ~0.4k; 20 bots at 6 commands/hour for 12 hours with 25% misses came to about
$0.06/day.

**While the server runs**

* `!typesafe` (GM 5) — totals, cost so far, observed tokens/hour with the day/month projection
  at that rate, and one line per kind (requests, failures, avg tokens, avg/max latency, cost).
* `!typesafe project <bots> [commands/bot/hour] [hours online]` — the same projection as the
  tool, using tokens-per-request measured by this server so far (offline estimates fill gaps).
* `!typesafe report` — writes the report to the log and the CSV immediately.
* Every `LOG_INTERVAL_MINUTES` (default 30) `JevUsageReporter` logs `[typesafe-usage]` lines
  and appends one CSV row per kind plus a total to `logs/typesafe-usage.csv`
  (`timestamp,kind,requests,ok,failed,input_tokens,output_tokens,avg_input_tokens,avg_latency_ms,max_latency_ms,cost_usd`).
* `!serverhealth` / `Server.diagnosticLines()` carries the compact `TypeSafe:` line with the
  same counters, cost, rate and projection.

## Contribution validation

Run the focused tests with Java 21 and the Maven wrapper:

```powershell
.\mvnw.cmd -B '-Dtest=*Jev*,*TypeSafe*,*AgentChatIntent*,*AgentOfferReply*,*AgentPartyQuestDialogue*,AgentPendingOfferChatRouteServiceTest' test
```

Run `./mvnw -B verify` (Windows: `.\mvnw.cmd -B verify`) for the full suite and package.
Map-dependent tests require the local `wz` game-data directory, which is not tracked in Git.
The Jev tests use fake transports and a loopback HTTP server; no API key is required.

Regression coverage includes malformed HTTP 200 responses, retry behavior, circuit-breaker
fallback, typed intent routing, uncertain Director rankings, and delayed trade judgments
whose original offers have changed. Trade callbacks recheck the item, expiry, direction,
recipient, and map inside the mailbox before applying a reply. Director action selection
requires both action confidence and the actionable-request probability to reach
`MIN_SELECT_CONFIDENCE`; every map candidate must meet that confidence threshold too.

The HTTP request and response shapes were checked against the
[TypeSafe quick start](https://docs.typesafe.ai/introduction/quickstart).
Live API accuracy, latency, and in-game SHADOW/LIVE behavior still require an operator smoke
test before enabling these features in production.
