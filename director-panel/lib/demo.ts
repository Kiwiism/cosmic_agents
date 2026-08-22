import type { AgentSummary, DirectorView } from "./types";

export const demoAgents: AgentSummary[] = [
  { characterId: 101, name: "Mira", level: 24, jobId: 200, mapId: 101030110, online: true, runtimeActive: true },
  { characterId: 102, name: "Rook", level: 22, jobId: 100, mapId: 102020300, online: true, runtimeActive: true },
  { characterId: 103, name: "Kite", level: 19, jobId: 400, mapId: 103000000, online: false, runtimeActive: false },
  { characterId: 104, name: "June", level: 27, jobId: 300, mapId: 910000000, online: true, runtimeActive: true },
];

export const demoView: DirectorView = {
  schemaVersion: 1,
  generatedAtMs: Date.now(),
  contextRevision: "preview-revision",
  agent: { characterId: 101, name: "Mira", level: 24, jobId: 200, mapId: 101030110, hp: 612, maxHp: 834, mp: 1218, maxMp: 1460, meso: 48231, alive: true, careerStage: "VICTORIA_TRAINING" },
  activity: { kind: "QUESTING", now: "Questing — victoria-training", next: "Resupply critical resources", waitingOn: "", blockedBy: "", retained: "", lastEvent: "Completed a safe quest handoff" },
  energy: { percent: 38, band: "LOW", restDebtPercent: 64, confidencePercent: 71, frustrationPercent: 12 },
  profile: { profileId: "explorer-v1", profileVersion: 1, traits: { activity: 72, patience: 58, curiosity: 91, sociability: 67, riskTolerance: 44, routinePreference: 24 } },
  resources: { exp: 48122, remainingAp: 0, remainingSp: 3, hpPotions: 17, mpPotions: 8, weaponType: "WAND", ammunition: 0, freeInventorySlots: { EQUIP: 11, USE: 7, ETC: 3 } },
  director: { mode: "ASSISTED", phase: "WAITING", goalId: "operator-directed-world-lifecycle", lastReason: "Waiting for proposal approval" },
  actions: [
    { actionId: "support:resupply", label: "Resupply critical resources", availability: "RECOMMENDED", reason: "MP potions are below the preferred quest reserve", activityKind: "", priority: 800, destructive: false },
    { actionId: "town-life:101000000", label: "Visit Ellinia", availability: "AVAILABLE", reason: "A bounded town visit will recover energy", activityKind: "TOWN_LIFE", priority: 200, destructive: false },
    { actionId: "hunting-map:101030110", label: "Hunt — The Tree Dungeon", availability: "AVAILABLE", reason: "Good level fit with moderate map complexity", activityKind: "HUNTING", priority: 599, destructive: false },
  ],
  proposals: [{ proposalId: "preview-proposal", source: "LLM", actionId: "support:resupply", label: "Resupply critical resources", rationale: "Mira is low on energy and MP supplies. A short resupply creates a safe recovery window before the next quest stage.", evidence: { energyBand: "LOW", energyPercent: "38", availability: "RECOMMENDED" }, alternativeActionIds: ["town-life:101000000"], expectedEnergyDelta: 8, createdAtMs: Date.now(), expiresAtMs: Date.now() + 300000, status: "PENDING", resolution: "" }],
  directives: [{ directiveId: "journey-42", actionId: "quest-plan:victoria-training", type: "START_ACTIVITY", status: "COMPLETED", reason: "Continue progression", resolution: "Quest stage completed", createdAtMs: Date.now() - 900000, resolvedAtMs: Date.now() - 300000 }],
  outcomes: [],
  journey: [{ sequence: 42, eventId: "event-42", occurredAtMs: Date.now() - 300000, type: "ACTIVITY_COMPLETED", activityKind: "QUESTING", source: "world-director", reason: "Quest stage completed", evidence: {} }],
};
