"use client";

import {
  Activity, AlertTriangle, BatteryMedium, Bot, Check, ChevronRight,
  CircleDot, Clock3, Gauge, History, MapPin, MessageSquareText, Pause,
  Play, RefreshCw, RotateCcw, Send, ShieldCheck, Sparkles, Users, X,
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { demoAgents, demoView } from "@/lib/demo";
import type {
  AgentSummary, ChatRecommendation, DirectorAction, DirectorChatResult,
  CleanSlatePreview, CleanSlateResult, DirectorHealth, DirectorView, Proposal,
} from "@/lib/types";

type Section = "agents" | "overview" | "proposals" | "chat";
type ChatMessage = {
  role: "operator" | "director";
  text: string;
  recommendations?: ChatRecommendation[];
  provider?: string;
};

class DirectorRequestError extends Error {
  constructor(message: string, readonly code: string, readonly status: number) {
    super(message);
  }
}

const modes = [
  ["OBSERVE", "Observe"], ["MANUAL", "Manual"], ["ASSISTED", "Assisted"],
] as const;

export default function DirectorPanel() {
  const [agents, setAgents] = useState<AgentSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [view, setView] = useState<DirectorView | null>(null);
  const [health, setHealth] = useState<DirectorHealth | null>(null);
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");
  const [notice, setNotice] = useState("");
  const [section, setSection] = useState<Section>("overview");
  const [chatInput, setChatInput] = useState("");
  const [resetOpen, setResetOpen] = useState(false);
  const [resetReason, setResetReason] = useState("");
  const [resetPhrase, setResetPhrase] = useState("");
  const [resetPreview, setResetPreview] = useState<CleanSlatePreview | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: "director", text: "Ask for an explanation or request a high-level proposal. I cannot execute without approval." },
  ]);

  const request = useCallback(async (path: string, init?: RequestInit) => {
    const response = await fetch(`/api/director/${path}`, {
      cache: "no-store",
      ...init,
      headers: init?.body ? { "Content-Type": "application/json", ...init.headers } : init?.headers,
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = payload.error ?? payload;
      throw new DirectorRequestError(
        error.message ?? "Director request failed",
        error.code ?? payload.code ?? "DIRECTOR_REQUEST_FAILED",
        response.status,
      );
    }
    return payload;
  }, []);

  const loadView = useCallback(async (agentId: number, quiet = false) => {
    try {
      const payload = await request(`agents/${agentId}`) as DirectorView;
      setView(payload);
      setConnected(true);
      if (!quiet) setNotice("");
    } catch (error) {
      if (error instanceof DirectorRequestError && error.code === "AGENT_OFFLINE") {
        setView(null);
        if (!quiet) setNotice(error.message);
        return;
      }
      if (!(error instanceof DirectorRequestError) || error.status >= 500) setConnected(false);
      if (!quiet) setNotice(error instanceof Error ? error.message : "Cosmic is unavailable");
      if (!(error instanceof DirectorRequestError) || error.status >= 500) {
        setView({ ...demoView, agent: { ...demoView.agent, characterId: agentId } });
      }
    }
  }, [request]);

  const loadRoster = useCallback(async (quiet = false) => {
    try {
      const payload = await request("agents");
      const rows = payload.agents as AgentSummary[];
      setAgents(rows);
      setConnected(true);
      setSelectedId((current) => current ??
        (rows.find((agent) => agent.runtimeActive) ?? rows[0])?.characterId ?? null);
      if (!quiet) setNotice("");
      return rows;
    } catch (error) {
      setConnected(false);
      if (!quiet) setNotice(error instanceof Error ? error.message : "Cosmic is unavailable");
      throw error;
    }
  }, [request]);

  const loadHealth = useCallback(async () => {
    try {
      setHealth(await request("health") as DirectorHealth);
    } catch {
      setHealth(null);
    }
  }, [request]);

  useEffect(() => {
    let active = true;
    Promise.all([request("agents"), request("health")]).then(([payload, healthPayload]) => {
      if (!active) return;
      const rows = payload.agents as AgentSummary[];
      setAgents(rows);
      setHealth(healthPayload as DirectorHealth);
      setConnected(true);
      const first = rows.find((agent) => agent.runtimeActive) ?? rows[0];
      setSelectedId(first?.characterId ?? null);
      setLoading(false);
    }).catch(() => {
      if (!active) return;
      setAgents(demoAgents);
      setSelectedId(demoAgents[0].characterId);
      setView(demoView);
      setConnected(false);
      setNotice("Cosmic bridge is offline — showing clearly marked preview data.");
      setLoading(false);
    });
    return () => { active = false; };
  }, [request]);

  useEffect(() => {
    if (!connected) return;
    const timer = window.setInterval(() => {
      loadRoster(true).catch(() => undefined);
      loadHealth();
    }, 5000);
    return () => window.clearInterval(timer);
  }, [connected, loadHealth, loadRoster]);

  useEffect(() => {
    if (selectedId == null || !connected) return;
    const selectedAgent = agents.find((agent) => agent.characterId === selectedId);
    if (selectedAgent && !selectedAgent.runtimeActive) {
      setView(null);
      return;
    }
    loadView(selectedId);
    const timer = window.setInterval(() => loadView(selectedId, true), 2500);
    return () => window.clearInterval(timer);
  }, [selectedId, connected, agents, loadView]);

  const mutate = async (path: string, method: "POST" | "PUT", body: object, label: string) => {
    if (!connected || selectedId == null) return false;
    setBusy(label);
    setNotice("");
    try {
      await request(path, { method, body: JSON.stringify(body) });
      await loadView(selectedId, true);
      return true;
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Action failed");
    } finally {
      setBusy("");
    }
    return false;
  };

  const selected = agents.find((agent) => agent.characterId === selectedId) ?? agents[0];
  const pending = useMemo(() => view?.proposals.filter((proposal) => proposal.status === "PENDING") ?? [], [view]);
  const available = useMemo(() => view?.actions.filter((action) => action.availability !== "UNAVAILABLE") ?? [], [view]);
  const llmLabel = !connected ? "LLM UNKNOWN"
    : !health ? "LLM CHECKING"
    : health.ollama.ready ? health.ollama.model
    : health.ollama.status.replaceAll("_", " ");
  const llmReady = health?.ollama.ready ?? false;

  const selectAgent = (agent: AgentSummary) => {
    setSelectedId(agent.characterId);
    setView(null);
    setSection("overview");
    setResetOpen(false);
    setResetPreview(null);
    setResetReason("");
    setResetPhrase("");
    if (connected && !agent.runtimeActive) setNotice("Load this offline Agent before opening its live controls.");
  };

  const openReset = () => {
    setResetOpen(true);
    setResetPreview(null);
    setResetReason("");
    setResetPhrase("");
    setNotice("");
  };

  const previewReset = async () => {
    if (!selected || !resetReason.trim()) return;
    setBusy("reset-preview");
    setNotice("");
    try {
      const preview = await request(`agents/${selected.characterId}/reset/preview`, {
        method: "POST",
        body: JSON.stringify({ reason: resetReason.trim() }),
      }) as CleanSlatePreview;
      setResetPreview(preview);
      setResetPhrase("");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Could not inspect reset eligibility");
    } finally {
      setBusy("");
    }
  };

  const executeReset = async () => {
    if (!selected || !resetPreview || resetPhrase !== resetPreview.confirmationPhrase) return;
    setBusy("reset-execute");
    setNotice("");
    try {
      const result = await request(`agents/${selected.characterId}/reset/execute`, {
        method: "POST",
        body: JSON.stringify({
          resetId: resetPreview.resetId,
          confirmationToken: resetPreview.confirmationToken,
          confirmationPhrase: resetPhrase,
        }),
      }) as CleanSlateResult;
      setResetOpen(false);
      setResetPreview(null);
      setResetReason("");
      setResetPhrase("");
      await loadRoster(true);
      setNotice(result.warnings.length > 0
        ? `${result.message}. ${result.warnings.join(" ")}`
        : result.message);
    } catch (error) {
      setResetPreview(null);
      setResetPhrase("");
      setNotice(error instanceof Error ? error.message : "Clean-slate reset failed");
    } finally {
      setBusy("");
    }
  };

  const submitChat = async (event: FormEvent) => {
    event.preventDefault();
    const message = chatInput.trim();
    if (!message || !connected || selectedId == null) return;
    setMessages((current) => [...current, { role: "operator", text: message }]);
    setChatInput("");
    setBusy("chat");
    try {
      const result = await request(`agents/${selectedId}/chat`, {
        method: "POST", body: JSON.stringify({ message }),
      }) as DirectorChatResult;
      setMessages((current) => [...current, {
        role: "director", text: result.reply,
        recommendations: result.recommendations, provider: result.provider,
      }]);
      await loadView(selectedId, true);
    } catch (error) {
      setMessages((current) => [...current, {
        role: "director", text: error instanceof Error ? error.message : "I could not create a proposal.",
      }]);
    } finally {
      setBusy("");
    }
  };

  const selectRecommendation = async (recommendation: ChatRecommendation) => {
    if (!connected || selectedId == null || !recommendation.selectable) return;
    setBusy(`recommendation:${recommendation.actionId}`);
    setNotice("");
    try {
      await request(`agents/${selectedId}/proposals`, {
        method: "POST",
        body: JSON.stringify({
          actionId: recommendation.actionId,
          rationale: recommendation.rationale,
          expectedEnergyDelta: 0,
        }),
      });
      await loadView(selectedId, true);
      setSection("proposals");
      setNotice(`${recommendation.mapName} is ready for final approval.`);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Could not create the map proposal");
    } finally {
      setBusy("");
    }
  };

  const runAction = (action: DirectorAction) => {
    const confirmed = !action.destructive || window.confirm(
      `${action.label} is destructive and may discard active progress. Continue?`,
    );
    if (!confirmed || !view) return;
    mutate(`agents/${selectedId}/actions`, "POST", {
      actionId: action.actionId,
      contextRevision: view.contextRevision,
      reason: "manual Director panel selection",
      confirmDestructive: action.destructive,
    }, action.actionId);
  };

  const approveProposal = (proposal: Proposal) => {
    const destructive = view?.actions.find((action) => action.actionId === proposal.actionId)?.destructive ?? false;
    const confirmed = !destructive || window.confirm(
      `${proposal.label} is destructive and may discard active progress. Approve it?`,
    );
    if (!confirmed) return;
    mutate(`agents/${selectedId}/proposals/${proposal.proposalId}/approve`, "POST", {
      confirmDestructive: destructive,
    }, "approve");
  };

  if (loading) return <div className="boot"><Bot size={28} /><span>Opening Director control plane…</span></div>;

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand"><span className="brand-mark"><CircleDot size={18} /></span><div><b>COSMIC</b><span>AGENT DIRECTOR</span></div></div>
        <div className="top-status">
          {!connected && <span className="preview-pill">PREVIEW DATA</span>}
          <span className={`status-pill ${connected ? "live" : "offline"}`}><i />{connected ? "COSMIC LIVE" : "BRIDGE OFFLINE"}</span>
          <span className={`status-pill model ${llmReady ? "ready" : "unavailable"}`} title={health?.ollama.model ?? "Director LLM status unavailable"}><Sparkles size={13} /> {llmLabel}</span>
        </div>
        <button className="icon-button" onClick={() => selectedId && loadView(selectedId)} aria-label="Refresh"><RefreshCw size={17} /></button>
      </header>

      {notice && <div className="notice"><AlertTriangle size={15} /><span>{notice}</span><button onClick={() => setNotice("")}><X size={14} /></button></div>}

      <nav className="mobile-tabs" aria-label="Panel sections">
        {(["agents", "overview", "proposals", "chat"] as Section[]).map((item) => (
          <button key={item} className={section === item ? "active" : ""} onClick={() => setSection(item)}>
            {item === "agents" ? <Users /> : item === "overview" ? <Gauge /> : item === "proposals" ? <Sparkles /> : <MessageSquareText />}
            <span>{item}</span>{item === "proposals" && pending.length > 0 && <em>{pending.length}</em>}
          </button>
        ))}
      </nav>

      <div className="workspace">
        <aside className={`agent-rail panel-section ${section === "agents" ? "mobile-active" : ""}`}>
          <div className="section-heading"><div><span>ACTIVE ROSTER</span><strong>{agents.length} agents</strong></div><button aria-label="Roster options">•••</button></div>
          <div className="agent-list">
            {agents.map((agent) => <AgentRow key={agent.characterId} agent={agent} selected={agent.characterId === selectedId} onClick={() => selectAgent(agent)} />)}
          </div>
          <div className="rail-foot"><ShieldCheck size={15} /><span>Agent OS owns all execution</span></div>
        </aside>

        <section className={`command-deck panel-section ${section === "overview" ? "mobile-active" : ""}`}>
          {selected && !selected.runtimeActive && connected ? (
            <div className="empty-state"><Bot size={42} /><h2>{selected.name} is offline</h2><p>Load the stored character into a neutral, safely parked Director session, or review a guarded gameplay reset.</p><div className="empty-actions"><button className="primary" onClick={async () => { if (await mutate(`agents/${selected.characterId}/spawn`, "POST", { world: 0, channel: 1 }, "spawn")) await loadRoster(true); }}>Load on channel 1</button><button className="danger-secondary" disabled={!!busy} onClick={openReset}><RotateCcw size={14} /> Review clean slate</button></div></div>
          ) : view ? <>
            <div className="agent-hero">
              <div className="avatar">{String(view.agent.name).slice(0, 1)}</div>
              <div className="hero-copy"><div><span className="eyebrow">SELECTED AGENT</span><span className={`live-dot ${connected ? "" : "muted"}`}>{connected ? "LIVE" : "PREVIEW"}</span></div><h1>{String(view.agent.name)}</h1><p>Lv. {view.agent.level} · Job {view.agent.jobId} <span>•</span> <MapPin size={13} /> {view.agent.mapId}</p></div>
              <div className="mode-control"><label htmlFor="mode">DIRECTOR MODE</label><select id="mode" value={view.director.mode} disabled={!connected || !!busy} onChange={(event) => mutate(`agents/${selectedId}/mode`, "PUT", { mode: event.target.value, reason: "changed from Director panel" }, "mode")}><option value="DISABLED" disabled>Disabled</option>{modes.map(([value, label]) => <option key={value} value={value}>{label}</option>)}<option value="AUTONOMOUS" disabled>Auto policy · rollout gated</option><option value="EMERGENCY_HOLD" disabled>Emergency hold</option></select></div>
            </div>

            <div className="metric-grid">
              <EnergyCard energy={view.energy} />
              <Metric icon={<Activity />} label="ACTIVITY" value={view.activity.kind || "IDLE"} detail={view.activity.now} />
              <Metric icon={<ShieldCheck />} label="RESOURCES" value={`${view.resources.hpPotions ?? 0} HP · ${view.resources.mpPotions ?? 0} MP`} detail={`${Number(view.agent.meso).toLocaleString()} mesos`} />
              <Metric icon={<Bot />} label="PROFILE" value={view.profile.profileId || "UNASSIGNED"} detail={`Risk ${view.profile.traits.riskTolerance ?? 0} · Curiosity ${view.profile.traits.curiosity ?? 0}`} />
            </div>

            <div className="activity-card">
              <div className="card-title"><div><span>NOW / NEXT</span><h2>Executive activity</h2></div><span className="phase">{view.director.phase}</span></div>
              <div className="activity-flow"><div><i className="pulse" /><span>NOW</span><strong>{view.activity.now}</strong><small>{view.director.lastReason}</small></div><ChevronRight /><div><i /><span>NEXT</span><strong>{view.activity.next}</strong><small>{view.activity.waitingOn || "Awaiting a safe decision boundary"}</small></div></div>
            </div>

            <div className="action-area">
              <div className="card-title"><div><span>VALIDATED BY AGENT OS</span><h2>Available actions</h2></div><button className="secondary" disabled={!connected || !!busy} onClick={() => mutate(`agents/${selectedId}/proposals/policy`, "POST", {}, "policy")}><Sparkles size={14} /> Ask policy</button></div>
              <div className="action-grid">{available.slice(0, 6).map((action) => <ActionCard key={action.actionId} action={action} disabled={!connected || !!busy} onExecute={() => runAction(action)} />)}</div>
            </div>

            <OperationalQueue view={view} disabled={!connected || !!busy} onCancel={(directiveId) => mutate(`agents/${selectedId}/directives/${directiveId}/cancel`, "POST", { reason: "cancelled from Director panel" }, "cancel")} onAcknowledge={(outcomeId) => mutate(`agents/${selectedId}/outcomes/${outcomeId}/acknowledge`, "POST", { reason: "acknowledged from Director panel" }, "acknowledge")} />
            <Timeline view={view} />
          </> : <div className="empty-state"><RefreshCw className="spin" /><p>Reading live Agent state…</p></div>}
        </section>

        <aside className={`decision-rail panel-section ${section === "proposals" || section === "chat" ? "mobile-active" : ""}`}>
          <div className={`proposal-pane ${section === "chat" ? "mobile-hidden" : ""}`}>
            <div className="section-heading"><div><span>ASSISTED DECISIONS</span><strong>Proposal queue</strong></div>{pending.length > 0 && <b>{pending.length}</b>}</div>
            <div className="proposal-list">{pending.length ? pending.map((proposal) => <ProposalCard key={proposal.proposalId} proposal={proposal} disabled={!connected || !!busy} onApprove={() => approveProposal(proposal)} onReject={() => mutate(`agents/${selectedId}/proposals/${proposal.proposalId}/reject`, "POST", { reason: "rejected from Director panel" }, "reject")} />) : <div className="no-proposal"><Check size={18} /><strong>No decision waiting</strong><span>Ask the Director or request a policy proposal.</span></div>}</div>
          </div>
          <div className={`chat-pane ${section === "proposals" ? "mobile-hidden" : ""}`}>
            <div className="section-heading"><div><span>DIRECTOR CONVERSATION</span><strong>Plan with the Director</strong></div><MessageSquareText size={17} /></div>
            <div className="messages">{messages.map((message, index) => <div key={index} className={`message ${message.role}`}><span>{message.role === "operator" ? "YOU" : `DIRECTOR${message.provider ? ` · ${message.provider}` : ""}`}</span><p>{message.text}</p>{message.recommendations?.map((recommendation) => <MapRecommendationCard key={recommendation.actionId} recommendation={recommendation} disabled={!connected || !!busy} onSelect={() => selectRecommendation(recommendation)} />)}</div>)}</div>
            <form className="chat-form" onSubmit={submitChat}><input value={chatInput} onChange={(event) => setChatInput(event.target.value)} disabled={!connected || busy === "chat"} placeholder={connected ? "Try: For lv16, what top 3 maps should we grind?" : "Connect Cosmic to use Director chat"} aria-label="Director message" /><button disabled={!chatInput.trim() || !connected || busy === "chat"} aria-label="Send"><Send size={16} /></button></form>
          </div>
        </aside>
      </div>
      {resetOpen && selected && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) setResetOpen(false); }}><section className="reset-modal" role="dialog" aria-modal="true" aria-labelledby="reset-title"><div className="reset-modal-head"><div><span>GUARDED ADMINISTRATION</span><h2 id="reset-title">Reset {selected.name} to a clean slate</h2></div><button aria-label="Close reset dialog" disabled={!!busy} onClick={() => setResetOpen(false)}><X size={17} /></button></div>{!resetPreview ? <><div className="reset-warning"><AlertTriangle size={18} /><p>This is an offline-only gameplay reset. It cannot be undone from this panel. No reset occurs until a preview is reviewed and its exact phrase is entered.</p></div><label className="reset-reason">Reason for reset<textarea value={resetReason} onChange={(event) => setResetReason(event.target.value)} maxLength={512} placeholder="Example: Restarting this Agent for the initial four-profile observation" /></label><div className="reset-modal-actions"><button className="secondary" disabled={!!busy} onClick={() => setResetOpen(false)}>Cancel</button><button className="primary" disabled={!resetReason.trim() || !!busy} onClick={previewReset}>{busy === "reset-preview" ? "Inspecting…" : "Generate reset preview"}</button></div></> : <><div className="reset-summary"><div><span>CURRENT</span><strong>Lv. {resetPreview.target.level} · Job {resetPreview.target.jobId}</strong><small>Map {resetPreview.target.mapId} · {resetPreview.target.questCount} quests · {resetPreview.target.skillCount} skills · {resetPreview.target.ordinaryItemCount} ordinary items</small></div><div><span>RESULT</span><strong>Lv. 1 Beginner</strong><small>Henesys · starter equipment · clean Agent OS progress</small></div></div>{resetPreview.blockers.length > 0 && <div className="reset-blockers"><strong>Reset blocked</strong>{resetPreview.blockers.map((blocker) => <p key={blocker}><X size={12} /> {blocker}</p>)}</div>}<div className="scope-grid"><div><strong>Will reset</strong>{resetPreview.resetScope.map((item) => <p key={item}><RotateCcw size={11} /> {item}</p>)}</div><div><strong>Will retain</strong>{resetPreview.retainedScope.map((item) => <p key={item}><ShieldCheck size={11} /> {item}</p>)}</div></div>{resetPreview.eligible && <label className="reset-reason confirmation">Type <code>{resetPreview.confirmationPhrase}</code> to confirm<input autoComplete="off" value={resetPhrase} onChange={(event) => setResetPhrase(event.target.value)} /></label>}<div className="reset-modal-actions"><button className="secondary" disabled={!!busy} onClick={() => { setResetPreview(null); setResetPhrase(""); }}>Back</button>{resetPreview.eligible && <button className="danger" disabled={resetPhrase !== resetPreview.confirmationPhrase || !!busy} onClick={executeReset}>{busy === "reset-execute" ? "Resetting…" : "Reset gameplay state"}</button>}</div></>}</section></div>}
    </main>
  );
}

function AgentRow({ agent, selected, onClick }: { agent: AgentSummary; selected: boolean; onClick: () => void }) {
  return <button className={`agent-row ${selected ? "selected" : ""}`} onClick={onClick}><span className="mini-avatar">{agent.name.slice(0, 1)}</span><span className="agent-copy"><strong>{agent.name}</strong><small>Lv. {agent.level} · Job {agent.jobId}</small></span><span className={`presence ${agent.runtimeActive ? "active" : agent.online ? "online" : ""}`} title={agent.runtimeActive ? "Director runtime active" : agent.online ? "Online" : "Offline"} />{selected && <ChevronRight size={15} />}</button>;
}

function EnergyCard({ energy }: { energy: DirectorView["energy"] }) {
  return <div className="metric energy-card"><div className="metric-label"><BatteryMedium /><span>ENERGY</span><b>{energy.band}</b></div><strong>{energy.percent}<small>%</small></strong><div className="energy-track"><i style={{ width: `${energy.percent}%` }} /></div><p>Rest debt {energy.restDebtPercent}%</p></div>;
}

function Metric({ icon, label, value, detail }: { icon: React.ReactNode; label: string; value: string; detail: string }) {
  return <div className="metric"><div className="metric-label">{icon}<span>{label}</span></div><strong className="metric-value">{value}</strong><p>{detail}</p></div>;
}

function ActionCard({ action, disabled, onExecute }: { action: DirectorAction; disabled: boolean; onExecute: () => void }) {
  return <article className={`action-card ${action.availability.toLowerCase()}`}><div><span>{action.activityKind || action.actionId.split(":")[0]}</span>{action.availability === "RECOMMENDED" && <b>RECOMMENDED</b>}</div><h3>{action.label}</h3><p>{action.reason}</p><button disabled={disabled} onClick={onExecute}>{action.destructive ? <AlertTriangle /> : <Play />} {action.destructive ? "Review" : "Run manually"}</button></article>;
}

function ProposalCard({ proposal, disabled, onApprove, onReject }: { proposal: Proposal; disabled: boolean; onApprove: () => void; onReject: () => void }) {
  const seconds = Math.max(0, Math.floor((proposal.expiresAtMs - Date.now()) / 1000));
  return <article className="proposal-card"><div className="proposal-source"><span><Sparkles size={13} /> {proposal.source}</span><time><Clock3 size={12} /> {Math.ceil(seconds / 60)}m</time></div><h3>{proposal.label}</h3><p>{proposal.rationale}</p><dl><div><dt>Energy</dt><dd className={proposal.expectedEnergyDelta >= 0 ? "positive" : "negative"}>{proposal.expectedEnergyDelta > 0 ? "+" : ""}{proposal.expectedEnergyDelta}%</dd></div><div><dt>Current</dt><dd>{proposal.evidence.energyBand ?? "—"}</dd></div></dl><details><summary>Evidence & alternatives</summary><p>{Object.entries(proposal.evidence).map(([key, value]) => `${key}: ${value}`).join(" · ")}</p></details><div className="proposal-actions"><button className="reject" disabled={disabled} onClick={onReject}><X size={14} /> Reject</button><button className="approve" disabled={disabled} onClick={onApprove}><Check size={14} /> Approve</button></div></article>;
}

function MapRecommendationCard({ recommendation, disabled, onSelect }: { recommendation: ChatRecommendation; disabled: boolean; onSelect: () => void }) {
  const mobs = recommendation.spawns.slice(0, 4)
    .map((spawn) => `${spawn.mobName} L${spawn.mobLevel} ×${spawn.expectedCount}`).join(" · ");
  return <article className="map-recommendation"><div className="map-rank"><b>#{recommendation.rank}</b><span>CATALOG RANK {recommendation.catalogRank}</span></div><h3>{recommendation.mapName}</h3><small>Map {recommendation.mapId} · Lv. {recommendation.recommendedMinLevel}–{recommendation.recommendedMaxLevel} · {recommendation.terrain.replaceAll("-", " ")}</small><p>{recommendation.rationale}</p><div className="map-tags">{recommendation.tags.slice(0, 3).map((tag) => <span key={tag}>{tag.replaceAll("-", " ")}</span>)}</div><dl><dt>Spawns</dt><dd>{mobs}</dd>{recommendation.hazards.length > 0 && <><dt>Watch for</dt><dd>{recommendation.hazards.join(" · ")}</dd></>}</dl><button disabled={disabled || !recommendation.selectable} onClick={onSelect}><MapPin size={13} /> {recommendation.selectable ? "Select for approval" : "Not executable right now"}</button></article>;
}

function OperationalQueue({ view, disabled, onCancel, onAcknowledge }: { view: DirectorView; disabled: boolean; onCancel: (directiveId: string) => void; onAcknowledge: (outcomeId: string) => void }) {
  const directives = view.directives.filter((directive) => directive.status === "PENDING" || directive.status === "CLAIMED");
  const outcomes = view.outcomes.filter((outcome) => !outcome.acknowledged);
  if (directives.length === 0 && outcomes.length === 0) return null;
  return <div className="operations"><div className="card-title"><div><span>EXECUTION HANDOFF</span><h2>Operational queue</h2></div><Pause size={17} /></div>{directives.map((directive) => <div className="operation-row" key={directive.directiveId}><div><strong>{directive.actionId || directive.type}</strong><p>{directive.status} · {directive.reason}</p></div>{directive.status === "PENDING" && <button disabled={disabled} onClick={() => onCancel(directive.directiveId)}><X size={12} /> Cancel queued</button>}</div>)}{outcomes.map((outcome) => <div className="operation-row" key={outcome.outcomeId}><div><strong>{outcome.status} outcome</strong><p>{outcome.reason}</p></div><button disabled={disabled} onClick={() => onAcknowledge(outcome.outcomeId)}><Check size={12} /> Acknowledge</button></div>)}</div>;
}

function Timeline({ view }: { view: DirectorView }) {
  const entries = [...view.journey].slice(-4).reverse();
  return <div className="timeline"><div className="card-title"><div><span>AUDIT TRAIL</span><h2>Recent journey</h2></div><History size={17} /></div>{entries.length ? entries.map((entry, index) => <div className="timeline-row" key={`${entry.eventId}-${index}`}><i /><div><strong>{String(entry.type).replaceAll("_", " ")}</strong><p>{String(entry.reason)}</p></div><time>{new Date(Number(entry.occurredAtMs)).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</time></div>) : <p className="timeline-empty">No recent journey events.</p>}</div>;
}
