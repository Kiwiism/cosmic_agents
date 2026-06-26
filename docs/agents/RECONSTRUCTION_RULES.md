# Agent Reconstruction Rules

The reconstruction model is: move behavior from one large bot bin into specialized Agent bins without changing observable behavior.

Rules:

1. Preserve NuTNNuT source/master bot behavior until a later removal phase explicitly changes it.
2. Refactor first, redesign behavior later.
3. Do not rename packages only and call it migrated.
4. Every moved subsystem needs parity tests before old code is deleted.
5. Temporary compatibility wrappers must be obvious and removable.
6. New Agent modules must not depend on `server.bots` as a permanent design.
7. Cosmic writes should gradually move behind `server.agents.integration` gateways.
8. Dialogue, metrics, and side effects should move toward events/listeners.
9. Profiles and policies should make old behavior replaceable without changing runtime infrastructure.
10. Removal decisions happen only after the behavior is isolated and documented.

Initial reconstruction order:

1. Runtime shell and registry.
2. Command parser/router boundaries. Initial parser and GM command bridge completed; old bot runtime remains underneath.
3. Movement and navigation.
4. Combat.
5. Loot and supplies.
6. Inventory, equipment, trade, shop, and build.
7. Quest, NPC, and PQ.
8. Dialogue, social, and LLM.
9. SPI/Cosmic gateway attachment.
10. Delete old bot-shaped runtime once all callers use Agent modules.
