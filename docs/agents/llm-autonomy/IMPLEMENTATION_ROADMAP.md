# LLM Autonomy Implementation Roadmap

This roadmap prepares full autonomy without disturbing the current Agent
restructure.

## Before Restructure Completes

Documentation and generated data only:

- [ ] NPC catalog prep.
- [ ] Quest catalog schema and exporter.
- [ ] Mob/drop catalog schema and exporter.
- [ ] Item/shop catalog schema and exporter.
- [ ] Map/portal/travel catalog schema and exporter.
- [ ] Economy observation schema.
- [ ] Agent profile schema.
- [ ] LLM command contract.
- [ ] Perception and memory schemas.
- [ ] Risk/manual-review policy.

Do not wire catalogs into live agent decision-making yet.

## After Capability Boundaries Exist

Read-only integration:

- [ ] `AgentKnowledgeRepository`.
- [ ] `AgentPerceptionSnapshot`.
- [ ] `AgentMemoryRepository`.
- [ ] Read-only LLM tools.
- [ ] Batch status summaries.

No quest/shop/combat execution through LLM yet.

## Controlled Command Integration

Start with low-risk commands:

- [ ] request status
- [ ] assign idle role
- [ ] navigate to safe map
- [ ] move near safe NPC
- [ ] farm low-risk mob
- [ ] vendor trash with protected item rules

Then gated commands:

- [ ] accept quest
- [ ] complete quest
- [ ] buy NPC shop item
- [ ] scan FM room
- [ ] record market observations

Then economy commands:

- [ ] buy market item with budget
- [ ] list market item
- [ ] plan profit task
- [ ] assign economy roles to groups

## Full Autonomy Milestones

### Milestone 1: Knowledgeable Planner

The LLM can answer:

- where is this NPC?
- what quest can this agent do?
- where does this item drop?
- where can this item be bought?
- how do I route from map A to map B?

### Milestone 2: Task Director

The LLM can assign:

- quest chains
- farming tasks
- training tasks
- supply runs
- FM scouting

### Milestone 3: Adaptive Agents

Agents react to:

- low supplies
- bad routes
- death risk
- crowded maps
- blocked quests
- market opportunities

### Milestone 4: Economy Participants

Agents can:

- scan FM
- remember prices
- sell loot
- buy supplies
- farm for profit
- perform low-risk trades

### Milestone 5: Emergent Population

The LLM manages groups with different roles:

- questers
- grinders
- farmers
- FM scouts
- suppliers
- social helpers
- traders

Agents vary by profile, mood, memory, and local context.

## Required Safety Gates

Before full autonomy:

- [ ] command audit logging
- [ ] per-agent budgets
- [ ] market rate limits
- [ ] script-sensitive NPC blocking
- [ ] manual-review catalog gates
- [ ] stuck/death emergency handling
- [ ] command cancellation
- [ ] LLM output schema validation
- [ ] rollback or safe-stop behavior
