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
  directives: Array<Record<string, string | number>>;
  outcomes: Array<Record<string, string | number | boolean>>;
  journey: Array<Record<string, string | number | Record<string, string>>>;
};
