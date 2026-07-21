# Agent Conversation System

The Agent conversation system is an ancillary, deterministic dialogue layer. It can describe what
Agents are doing and exchange same-map conversation without owning movement, quest, combat, reactor,
or inventory capabilities.

## Design

The implementation is split into five stages:

1. **Semantic acts** describe speaker, listener, topic, act, audience, parameters, and a deterministic
   variation seed. Objective intention announcements use the same model as conversations.
2. **Topic models and realization** select utility-scored storylets and turn them into text through
   registered templates. Personality expressiveness affects presentation while seeds keep results
   reproducible.
3. **Pair sessions and direct delivery** run bounded Agent-to-Agent sessions beside the capability
   scheduler. Turns are delivered through private coordination envelopes; this hidden delivery is the
   logical conversation.
4. **Observer projection** optionally mirrors a delivered turn into map chat. By default, unobserved
   maps do not create ambient sessions, and visible map messages are rate-limited.
5. **Content plugins and controls** let plans expose neutral activity facts and register content-owned
   topics with `ServiceLoader`. Operators can inspect sessions, metrics, and topic switches live.

The current model is a conversation state machine with utility-selected storylets and deterministic
template generation. It deliberately does not require an LLM.

## Safety boundaries

- Conversation ticks never acquire capability ownership and never pause the active objective.
- A topic model may inspect state but must not mutate quests, reactors, inventory, movement, or combat.
- Presentation hints such as `quiz_hesitation` are metadata only. No gameplay runtime consumes them.
- Pio conversation is cosmetic and cannot select or reserve reactor boxes.
- An extension failure is counted and isolated; it cannot fail the Agent's main tick.
- Sessions have participant, map, turn, timeout, cooldown, and per-map concurrency bounds.
- Private coordination inboxes are released with their sessions, preventing route accumulation during
  long-running Agent churn.
- Public chat is presentation. Direct coordination delivery is the source of conversation state.

## Configuration

The `server` section of `config.yaml` contains the defaults:

- `AGENT_DIALOGUE_SYSTEM_ENABLED`: master semantic-dialogue switch.
- `AGENT_DIALOGUE_ENABLED_TOPICS`: comma-separated default topic allow-list.
- `AGENT_CONVERSATION_ENABLED`: ambient pair-session switch.
- `AGENT_CONVERSATION_SIMULATE_UNOBSERVED`: permit hidden sessions without a real observer.
- `AGENT_CONVERSATION_TICK_INTERVAL_MS`: ancillary bookkeeping cadence.
- `AGENT_CONVERSATION_ATTEMPT_INTERVAL_MS`: partner-search interval.
- `AGENT_CONVERSATION_SESSION_COOLDOWN_MS`: rest between sessions.
- `AGENT_CONVERSATION_TURN_INTERVAL_MS`: base spacing between turns.
- `AGENT_CONVERSATION_SESSION_TIMEOUT_MS`: hard stale-session lifetime.
- `AGENT_CONVERSATION_MAX_TURNS`: bounded turns, including framing.
- `AGENT_CONVERSATION_MAX_VISIBLE_SESSIONS_PER_MAP`: per-map pair-session cap.
- `AGENT_DIALOGUE_MAP_MESSAGE_INTERVAL_MS`: public map-chat rate limit.

Live GM controls:

```text
!agentchat status
!agentchat topics
!agentchat sessions
!agentchat metrics
!agentchat topic <topic-id> on
!agentchat topic <topic-id> off
!agentchat topic <topic-id> default
```

Maple Island topics are independently switchable:

```text
maple_island.pio_boxes
maple_island.rain_quiz
maple_island.yoona_quiz
```

Generic topics include `greeting`, `quest_progress`, `hunting`, `travel`, `encouragement`,
`farewell`, `daily_life`, `slow_spawn`, and `quest_relief`. Objective announcements use
`objective_intention`.

## Adding content

Region plans remain outside the generic conversation engine:

1. Implement `AgentConversationActivityProvider` to translate plan state into
   `AgentConversationActivity`. Use only neutral facts such as active, hunting, age, recent completion,
   and opaque objective identity.
2. Implement `AgentConversationTopicProvider` with one or more topic models. Topic IDs should be
   namespaced, for example `region.topic`.
3. Register providers in the matching files under `META-INF/services`.
4. Keep templates and quest-aware branching in the content package. Never import the content plan
   from the generic conversation package.
5. Add the topic to `AGENT_DIALOGUE_ENABLED_TOPICS` when it should be enabled by default.

## Current scope

Sessions currently support deterministic pairs, short-lived direct messages, observer projection,
and no long-term memory. Multi-Agent linked objectives, gameplay coordination, LLM generation, and
conversation-driven pacing are intentionally not part of this stage. They can be added later as new
models or coordination policies without changing the semantic or projection layers.
