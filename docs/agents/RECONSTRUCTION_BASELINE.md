# Agent Reconstruction Baseline

Branch: `reconstruction/source-master-agent-base`

Source baseline:

- Remote: `source`
- Repository: `nutnnut/Cosmic`
- Ref: `source/master`
- Commit: `5f0447172e501d85bc406be2599f0a010a82a2d2`

Comparison baseline:

- Remote: `cosmic`
- Repository: `P0nk/Cosmic`
- Ref: `cosmic/master`
- Commit: `fec53bc7714dc0f1ae3f50b2986cdf2727e0912a`

Original bot inventory at this baseline:

- Original production bot files: 63 under `src/main/java/server/bots`.
- Original bot tests and harnesses: 30 under `src/test/java/server/bots`.
- Bot client/command integration files exist outside `server.bots`.
- `src/main/java/server/agents` is a new neutral reconstruction target created on this branch.

Reconstruction progress captured by this baseline snapshot:

- Foundation package skeleton exists.
- Command parser behavior now lives in `server.agents.commands.AgentCommandParser`.
- `server.bots.BotCommandParser` remains as a temporary compatibility adapter.
- GM bot command entry classes still keep their visible command names, but their bodies now route through `server.agents.commands` bridge/executor classes.
- Reply queue behavior now lives in `server.agents.commands.AgentReplyQueue`.
- Legacy chat queue behavior is now reached through Agent-owned dialogue/runtime modules and Agent integration adapters; `server.bots.BotChatManager` has been removed.
- Named random dialogue pools now live in `server.agents.capabilities.dialogue.AgentDialogueCatalog`.
- The old chat manager no longer contains inline `randomReply(List.of(...))` pools; those variants are cataloged under Agent dialogue.
- Away/logout, support/heal/buff/proactive toggle, SP variant, help, equipment recommendation, fame, skill-report fallback, and trade-choice fallback fixed prompt and confirmation lines also live in `AgentDialogueCatalog`.
- Follow-target chat classification, group supply request classification, and direct HP/MP/potion/ammo supply command classification now live in `server.agents.capabilities.dialogue.AgentChatCommandClassifier`.
- Movement-mode chat classification (`follow`, `stop`, `move here`, `grind`, patrol/farm-here, fidget) also lives in `AgentChatCommandClassifier`.
- Meso/movement-stat queries, respec commands, proactive-offer toggles, support/heal/buff toggles, logout/relog/away session requests, and upgrade-request classification also live in `AgentChatCommandClassifier`.
- Session-confirmation command normalization now lives in `AgentChatCommandClassifier`.
- Item query string normalization now lives in `server.agents.capabilities.dialogue.AgentItemQueryNormalizer`.
- Trade/drop/item query classification and pending drop-choice classification now live in `server.agents.capabilities.dialogue.AgentTradeDialogueClassifier`.
- Bare trade-invite, sell-trash, and Maker utility command classification now lives in `server.agents.capabilities.dialogue.AgentUtilityDialogueClassifier`.
- Equipment and auto-equip chat classification now lives in `server.agents.capabilities.dialogue.AgentEquipmentDialogueClassifier`.
- Greeting and fame-target chat classification now lives in `server.agents.capabilities.dialogue.AgentSocialDialogueClassifier`.
- Job/build/AP/SP choice classification, skill-tree choice resolution, and job advancement resolution now lives in `server.agents.capabilities.dialogue.AgentBuildDialogueClassifier`.
- Help, report, buff-list, and debug query classification now lives in `server.agents.capabilities.dialogue.AgentChatCommandClassifier`.
- Stats/range/build/crit/EXP/supply/meso/movement, drop-or-trade prompt, selected catalog-template text, AP-build replies, job display, skill-tree prompt, and learned-skill report formatting now lives in `server.agents.capabilities.dialogue.AgentDialogueReportFormatter`.
- Chat parsing, direct response strings, dialogue classification, report formatting, and reply runtime ownership have moved into Agent-owned dialogue and integration modules. Remaining bot-side chat references are compatibility entry points or historical parity tests.
- At this snapshot, bot runtime behavior still lived under `server.bots` and
  had not been functionally changed.

Current state note:

- The active reconstruction has since removed production/test
  `src/main/java/server/bots/**` and `src/test/java/server/bots/**`.
  Runtime/session code now uses `server.agents.runtime.AgentRuntimeEntry`
  directly, while historical `server.bots` paths remain in this document only
  as baseline references.

Important baseline note:

`source/master` already includes NuTNNuT's merge commit `8987c5762 Merge branch 'experimental'`. For this reconstruction, that merged master is treated as the behavior baseline to preserve. Features can be removed later only after they are reconstructed into explicit Agent modules and their impact is understood.

Scope for this branch:

- Reconstruct bot behavior into a clean Agent architecture.
- Keep behavior exact during reconstruction.
- Do not pull in unrelated Cosmic gameplay or tooling changes as design goals.
- Do not remove bot features yet unless a later explicit cleanup phase chooses to.
- Do not let old bot ownership, manager, or task concepts define the final architecture.
