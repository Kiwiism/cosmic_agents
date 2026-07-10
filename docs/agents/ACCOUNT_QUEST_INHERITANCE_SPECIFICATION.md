# Account Quest Inheritance Specification

Status: exploration and post-refactor implementation candidate.

Related documents:

- `docs/agents/CHARACTER_PROFILE_RUNTIME_REFACTOR_ROADMAP.md`
- `docs/agents/DOUBLE_AGENT_CHARACTER_STATE_EXTRACTION_PLAN.md`
- `docs/agents/MAPLE_ISLAND_MVP_TECHNICAL_SPECIFICATION.md`

## Product Goal

Allow a character to instantly inherit eligible quest completion when another
character on the same account has already completed that quest.

The feature should reduce repeated story and tutorial work without duplicating
valuable rewards, corrupting quest chains, bypassing class-specific progression,
or creating inconsistent profile state.

## Complexity Summary

The database lookup itself is easy. Safe quest inheritance is not.

```text
Sibling completion lookup only                     low complexity
Status-only inheritance for classified quests      medium complexity
Generic normal reward replay                       high complexity
Scripted quest replay with identical side effects  very high complexity
```

Recommended MVP: status-only completion, no repeated start/completion rewards,
and an explicit eligible-quest policy.

Do not implement the feature by calling `Quest.forceCompleteWithActions(...)`
for every sibling-completed quest.

## Current Quest Behavior

### State

Each character owns a map of `QuestStatus` values containing:

- quest ID and status.
- mob/info progress.
- medal map progress.
- NPC ID.
- completion and expiration timestamps.
- forfeited and completed counts.
- custom data.

Quest status, progress, and medal maps are persisted by character ID in:

- `queststatus`.
- `questprogress`.
- `medalmaps`.

The current schema has no durable account-level completion ledger.

### Normal Start

`Quest.start(...)`:

1. validates start status and requirements.
2. validates all start actions.
3. runs start actions.
4. changes status to `STARTED`.
5. initializes progress and time limits.
6. synchronizes party Agents where configured.

### Normal Completion

`Quest.complete(...)`:

1. requires current status `STARTED`.
2. validates completion requirements.
3. validates completion actions and reward selection.
4. changes status to `COMPLETED`.
5. runs completion actions.
6. updates follow-up quest information.
7. synchronizes party Agents.

Completion actions may:

- grant or remove items.
- grant EXP, mesos, or fame.
- teach skills.
- apply buffs.
- change other quest statuses or info values.
- alter pets.
- provide selected or random rewards.
- hint or trigger a next quest.

Scripted quests may perform additional behavior outside the typed action map.

### Force Completion

`Quest.forceComplete(...)` changes status and sends completion effects without
running completion actions. However, `Character.updateQuestStatus(...)` still:

- awards quest points for eligible quests.
- increments the character's completion count.
- emits completion packets.

`Quest.forceCompleteWithActions(...)` runs completion actions with force
semantics. Item actions may tolerate missing required take-items, making this
unsafe as a generic inheritance mechanism.

## Recommended Semantics

### Trigger

Check inheritance when the character attempts to start an eligible quest.

Do not eagerly copy every account-completed quest at login. Login copying would
increase load, flood client updates, make exclusions harder to reason about, and
complete quests the player may prefer to replay.

The initial UX may be automatic under configuration or offer a simple choice:

```text
Another character on this account has completed this quest.
Complete it immediately without repeat rewards?
```

Agent policy may choose automatically only for quests classified as safe.

### MVP Completion Mode

Introduce an explicit completion mode rather than reusing GM force methods:

```java
enum QuestCompletionMode {
    NORMAL,
    ADMIN_FORCE,
    ACCOUNT_INHERITED
}
```

For `ACCOUNT_INHERITED`:

- set the target profile's quest status to completed.
- record inheritance provenance.
- send the required quest status/client updates.
- unlock eligible downstream quest requirements.
- do not run start actions.
- do not run completion reward actions.
- do not remove required items.
- do not grant quest points again by default.
- do not increment account reward counters.
- do not synchronize the inheritance as party quest completion.
- do not execute quest scripts.

Whether the local `QuestStatus.completed` count becomes one should be explicit;
the recommended behavior is one for compatibility with completed status while
separate provenance prevents treating it as a rewarded completion.

### Reward Modes

Support only explicit policy modes:

```text
NONE
  Status and prerequisite unlock only. Recommended default.

ESSENTIAL_ONLY
  Apply specifically classified progression actions, such as a required skill
  or permanent flag. Requires per-quest review.

FULL_WHITELISTED
  Run normal rewards only for reviewed quests whose actions are deterministic,
  safe, and intended to be claimable once per character.
```

There should be no unrestricted generic `FULL` mode.

## Eligibility Policy

### Excluded By Default

- repeatable or interval quests.
- daily, weekly, or event-limited quests.
- party quests and party-instance progression.
- scripted start or completion quests.
- job-advancement and class-specific skill quests.
- quests that grant inventory slots or permanent system unlocks.
- quests with selected or random item rewards.
- medal quests and map-visit progress.
- pet quests.
- quests that modify other quest states or info values.
- quests with irreversible account/economy side effects.
- known exploitable quests.
- quests requiring event-instance membership.
- quests whose completion action is needed for later gameplay state.

### Candidate Safe Quests

- non-repeatable story/tutorial quests.
- no completion script.
- no selected/random reward.
- no skill, pet, or permanent unlock action.
- no cross-quest or custom-info mutation.
- no event/date restriction.
- current profile satisfies job and other essential eligibility policy.
- downstream progression depends only on completed status.

### Current-Character Requirements

Sibling completion should not automatically make every quest valid for every
job or level.

Recommended MVP checks:

- account and world policy match.
- quest is currently `NOT_STARTED` on the target profile.
- target profile satisfies job restrictions.
- target profile satisfies configured minimum/maximum level policy.
- quest is not expired or event-disabled.
- source completion is valid and not inherited from an untrusted legacy row.

Item, mob, field, and NPC completion requirements are intentionally bypassed for
eligible status-only inheritance. That bypass must be visible in policy and
audit data.

## Quest Classification

Add a classifier that inspects typed quest metadata:

```java
record AccountQuestInheritanceDecision(
        boolean eligible,
        QuestInheritanceReason reason,
        QuestInheritanceRewardMode rewardMode,
        boolean requiresPlayerChoice) {
}
```

Example reasons:

- `ELIGIBLE_STATUS_ONLY`.
- `NOT_COMPLETED_ON_ACCOUNT`.
- `ALREADY_STARTED_OR_COMPLETED`.
- `REPEATABLE`.
- `SCRIPTED`.
- `JOB_RESTRICTED`.
- `EVENT_RESTRICTED`.
- `REWARD_SELECTION_REQUIRED`.
- `PERMANENT_UNLOCK_ACTION`.
- `QUEST_CHAIN_SIDE_EFFECT`.
- `DENYLISTED`.
- `NOT_REVIEWED`.

Use conservative defaults: unknown or unclassified behavior is ineligible.

## Account Completion Ledger

Querying sibling `queststatus` rows can bootstrap the feature, but a dedicated
ledger is safer and survives character deletion.

Illustrative schema:

```sql
CREATE TABLE account_quest_completion (
    account_id INT NOT NULL,
    quest_id INT NOT NULL,
    first_character_id INT NULL,
    first_completed_at BIGINT NOT NULL,
    source VARCHAR(32) NOT NULL,
    PRIMARY KEY (account_id, quest_id)
);

CREATE TABLE character_quest_inheritance (
    character_id INT NOT NULL,
    quest_id INT NOT NULL,
    source_character_id INT NULL,
    inherited_at BIGINT NOT NULL,
    reward_mode VARCHAR(32) NOT NULL,
    policy_version INT NOT NULL,
    PRIMARY KEY (character_id, quest_id)
);
```

The first table answers account completion eligibility. The second provides
per-character provenance and prevents repeated inheritance processing.

Normal non-repeatable quest completion should upsert the account ledger after
the character completion transaction succeeds.

### Backfill

Backfill account completion using distinct completed, non-repeatable quest rows
joined through `characters.accountid`.

Before backfill:

- define treatment of deleted source characters.
- filter invalid and known exploitable quest IDs.
- select the earliest trustworthy completion time where duplicates exist.
- record a legacy-backfill source value.

## Runtime Services

Suggested boundaries:

```text
AccountQuestCompletionRepository
  reads/upserts durable account completion

AccountQuestInheritancePolicy
  classifies quest metadata and target-profile eligibility

AccountQuestInheritanceService
  coordinates validation, status transition, provenance, and synchronization

QuestCompletionService
  explicit NORMAL / ADMIN_FORCE / ACCOUNT_INHERITED semantics

AccountQuestCompletionCache
  account-scoped read-through set with invalidation on normal completion
```

Quest inheritance belongs to profile quest state, not actor state. Under future
profile switching, the attached profile receives the completion while account
eligibility comes from that profile's canonical account ID.

## Concurrency And Transactions

Two characters on the same account may complete or inherit concurrently.

Requirements:

- account ledger uses idempotent upsert.
- character inheritance row has a unique character/quest key.
- target quest status is rechecked under the profile mutation lock.
- provenance and quest status persistence commit together where practical.
- duplicate requests return the existing result without duplicate packets or
  rewards.
- cache invalidation occurs after commit.

Do not hold profile locks while running scripts, WZ parsing, or remote work.

## Client Synchronization

Inherited completion should send the same minimal status packets needed for the
quest log and NPC availability to update, but should avoid reward animations
that imply items or EXP were granted.

Verify:

- quest moves to completed state in the quest log.
- prerequisite NPC indicators refresh.
- no reward selection dialog opens.
- no quest timer remains.
- no duplicate completion animation or party sync occurs.
- relog shows the same state.

## Agent Behavior

Agents may use the same service after the generalized quest capability is live.

Agent policy should:

- check inheritance before planning the full quest objective.
- auto-inherit only `ELIGIBLE_STATUS_ONLY` quests when enabled.
- record a reason in the Agent decision journal.
- invalidate the current quest plan after successful inheritance.
- continue normally when inheritance is ineligible.
- never run scripts or choose rewards without an explicit capability policy.

This can shorten repeated account questing while preserving capability testing
for a fresh test account or when inheritance is disabled.

## Delivery Phases

### Phase 0: Audit And Classification API

- expose typed quest metadata needed by the classifier.
- inventory all action and requirement combinations.
- define denylist, allowlist, and policy versioning.
- add dry-run command/reporting with no mutation.

### Phase 1: Account Completion Ledger

- add schema and repository.
- upsert on normal non-repeatable completion.
- implement cache and invalidation.
- backfill trusted legacy completion.
- add database integration tests.

### Phase 2: Status-Only Inheritance MVP

- add explicit inherited completion mode.
- suppress reward actions and quest points.
- persist provenance.
- integrate at quest-start request.
- add feature flag and conservative allowlist.
- verify real-client quest-log behavior.

### Phase 3: Agent Integration

- add inheritance query to quest planning.
- support configured auto-inheritance.
- preserve fresh-account Amherst and Maple Island test modes.

### Phase 4: Reviewed Essential Rewards

- classify required skills/items/flags.
- add per-quest adapters where status-only completion breaks progression.
- test each reviewed quest chain.

### Phase 5: Optional Whitelisted Full Rewards

- allow only deterministic, reviewed quests.
- define per-character versus per-account reward entitlement.
- handle selection explicitly.
- maintain reward claim ledger and audit.

## Test Plan

### Unit

- classifier decisions for every exclusion category.
- reward mode selection.
- job/level/account/world policies.
- repeatable and scripted quest rejection.
- idempotent inheritance decisions.

### Database

- normal completion upserts ledger once.
- source character deletion does not erase account history.
- concurrent upserts remain one row.
- inheritance provenance is unique.
- target profile owner receives status.
- unrelated sibling rows remain unchanged.

### Quest Integration

- eligible status-only inheritance unlocks prerequisites.
- no EXP, mesos, fame, item, skill, buff, pet, or quest-point reward repeats.
- no required item is removed.
- ineligible quests follow the normal quest path.
- repeatable quests remain playable.
- selected-reward quests never auto-complete.
- scripted quests never execute headlessly.

### Real Client

- quest log refreshes immediately.
- NPC availability updates.
- no reward dialog or misleading animation.
- relog preserves state.
- feature disabled behaves exactly as before.

### Agent

- eligible inherited quest skips its objective plan.
- ineligible quest executes normal capabilities.
- test reset/fresh-account mode ignores account inheritance.
- profile switching credits the attached canonical profile.

## Risks

- skipping a reward that is required by a later quest.
- duplicating skills, inventory slots, or valuable items.
- bypassing job progression.
- marking scripted chains complete without script side effects.
- repeating quest points or account economy rewards.
- legacy completion rows containing invalid/exploited status.
- unexpected interaction with repeatable quest completion counts.
- confusing players when a quest disappears without dialogue.

Conservative classification, explicit provenance, and status-only default
behavior are required to control these risks.

## Definition Of Done For MVP

- account completion ledger is durable and indexed.
- only reviewed, non-repeatable quests are eligible.
- current profile eligibility is checked.
- inherited completion grants no repeated rewards or quest points.
- provenance is persisted and auditable.
- processing is idempotent and concurrency-safe.
- client quest state refreshes without relogging.
- disabled configuration preserves exact legacy behavior.
- player and Agent paths use the same inheritance service.
- fresh-account and capability-test modes can explicitly bypass inheritance.

## Recommendation

Implement status-only account inheritance after profile quest ownership has a
stable boundary in the Character Profile Runtime. The feature does not require
Double Agent, but the profile refactor makes ownership, synchronization, Agent
use, and testing substantially safer.

Treat full reward replay as a separate, whitelist-only expansion. A generic
force-complete-with-rewards implementation would be easy to write and difficult
to make trustworthy.
