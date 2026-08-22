export type AgentSummary = {
  characterId: number;
  name: string;
  level: number;
  jobId: number;
  mapId: number;
  online: boolean;
  runtimeActive: boolean;
};

export type DirectorAction = {
  actionId: string;
  label: string;
  availability: "RECOMMENDED" | "AVAILABLE" | "UNAVAILABLE";
  reason: string;
  activityKind: string;
  priority: number;
  destructive: boolean;
};

export type DirectorHealth = {
  status: string;
  checkedAt: string;
  ollama: {
    enabled: boolean;
    ready: boolean;
    reachable: boolean;
    modelAvailable: boolean;
    model: string;
    status: string;
  };
  socialDatabase: { enabled: boolean; available: boolean };
};

export type SpawnFact = {
  mobId: number;
  mobName: string;
  mobLevel: number;
  expectedCount: number;
  role: string;
};

export type ChatRecommendation = {
  rank: number;
  actionId: string;
  label: string;
  rationale: string;
  mapId: number;
  mapName: string;
  catalogRank: number;
  catalogWeight: number;
  recommendedMinLevel: number;
  recommendedMaxLevel: number;
  terrain: string;
  tags: string[];
  hazards: string[];
  spawns: SpawnFact[];
  selectable: boolean;
};

export type DirectorChatResult = {
  reply: string;
  proposal: Proposal | null;
  recommendations: ChatRecommendation[];
  provider: string;
  latencyMs: number;
};

export type CleanSlateTarget = {
  characterId: number;
  name: string;
  world: number;
  level: number;
  jobId: number;
  mapId: number;
  experience: number;
  mesos: number;
  ordinaryItemCount: number;
  preservedItemCount: number;
  questCount: number;
  skillCount: number;
  activeAgent: boolean;
  interactiveAllowed: boolean;
  dedicatedAccount: boolean;
  merchantStateClear: boolean;
};

export type CleanSlatePreview = {
  resetId: string;
  target: CleanSlateTarget;
  eligible: boolean;
  blockers: string[];
  resetScope: string[];
  retainedScope: string[];
  confirmationToken: string;
  confirmationPhrase: string;
  expiresAtMs: number;
};

export type CleanSlateResult = {
  resetId: string;
  success: boolean;
  message: string;
  target: CleanSlateTarget;
  warnings: string[];
  executedAtMs: number;
};

export type Proposal = {
  proposalId: string;
  source: "POLICY" | "LLM" | "OPERATOR";
  actionId: string;
  label: string;
  rationale: string;
  evidence: Record<string, string>;
  alternativeActionIds: string[];
  expectedEnergyDelta: number;
  createdAtMs: number;
  expiresAtMs: number;
  status: "PENDING" | "APPROVED" | "REJECTED" | "STALE" | "EXECUTED";
  resolution: string;
};

export type DirectorDirective = {
  directiveId: string;
  actionId: string;
  type: string;
  status: "PENDING" | "CLAIMED" | "COMPLETED" | "REJECTED" | "CANCELLED" | "EXPIRED";
  reason: string;
  resolution: string;
  createdAtMs: number;
  resolvedAtMs: number;
};

export type DirectorOutcome = {
  outcomeId: string;
  acknowledged: boolean;
  publishedAtMs: number;
  status: string;
  reason: string;
};

export type DirectorView = {
  schemaVersion: number;
  generatedAtMs: number;
  contextRevision: string;
  agent: Record<string, string | number | boolean>;
  activity: Record<string, string>;
  energy: {
    percent: number;
    band: string;
    restDebtPercent: number;
    confidencePercent: number;
    frustrationPercent: number;
  };
  profile: { profileId: string; profileVersion: number; traits: Record<string, number> };
  resources: Record<string, unknown>;
  director: { mode: string; phase: string; goalId: string; lastReason: string };
  actions: DirectorAction[];
  proposals: Proposal[];
  directives: DirectorDirective[];
  outcomes: DirectorOutcome[];
  journey: Array<Record<string, string | number | Record<string, string>>>;
};
