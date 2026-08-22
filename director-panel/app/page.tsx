"use client";

import {
  Activity, AlertTriangle, BatteryMedium, Bot, Check, ChevronRight,
  CircleDot, Clock3, Gauge, History, MapPin, MessageSquareText, Pause,
  Play, RefreshCw, Send, ShieldCheck, Sparkles, Users, X,
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { demoAgents, demoView } from "@/lib/demo";
import type { AgentSummary, DirectorAction, DirectorView, Proposal } from "@/lib/types";

type Section = "agents" | "overview" | "proposals" | "chat";
type ChatMessage = { role: "operator" | "director"; text: string };

const modes = [
  ["OBSERVE", "Observe"], ["MANUAL", "Manual"], ["ASSISTED", "Assisted"],
] as const;

export default function DirectorPanel() {
  const [agents, setAgents] = useState<AgentSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [view, setView] = useState<DirectorView | null>(null);
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");
  const [notice, setNotice] = useState("");
  const [section, setSection] = useState<Section>("overview");
  const [chatInput, setChatInput] = useState("");
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
      throw new Error(error.message ?? "Director request failed");
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
      setConnected(false);
      if (!quiet) setNotice(error instanceof Error ? error.message : "Cosmic is unavailable");
      setView({ ...demoView, agent: { ...demoView.agent, characterId: agentId } });
    }
  }, [request]);

  useEffect(() => {
    let active = true;
    request("agents").then((payload) => {
      if (!active) return;
      const rows = payload.agents as AgentSummary[];
      setAgents(rows);
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
    if (selectedId == null || !connected) return;
    loadView(selectedId);
    const timer = window.setInterval(() => loadView(selectedId, true), 2500);
    return () => window.clearInterval(timer);
  }, [selectedId, connected, loadView]);

  const mutate = async (path: string, method: "POST" | "PUT", body: object, label: string) => {
    if (!connected || selectedId == null) return;
    setBusy(label);
    setNotice("");
    try {
      await request(path, { method, body: JSON.stringify(body) });
      await loadView(selectedId, true);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Action failed");
    } finally {
      setBusy("");
    }
  };

  const selected = agents.find((agent) => agent.characterId === selectedId) ?? agents[0];
  const pending = useMemo(() => view?.proposals.filter((proposal) => proposal.status === "PENDING") ?? [], [view]);
  const available = useMemo(() => view?.actions.filter((action) => action.availability !== "UNAVAILABLE") ?? [], [view]);

  const selectAgent = (agent: AgentSummary) => {
    setSelectedId(agent.characterId);
    setView(null);
    setSection("overview");
    if (connected && !agent.runtimeActive) setNotice("Load this offline Agent before opening its live controls.");
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
      });
      setMessages((current) => [...current, { role: "director", text: result.reply }]);
      await loadView(selectedId, true);
    } catch (error) {
      setMessages((current) => [...current, {
        role: "director", text: error instanceof Error ? error.message : "I could not create a proposal.",
      }]);
    } finally {
      setBusy("");
    }
  };

  if (loading) return <div className="boot"><Bot size={28} /><span>Opening Director control plane…</span></div>;

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand"><span className="brand-mark"><CircleDot size={18} /></span><div><b>COSMIC</b><span>AGENT DIRECTOR</span></div></div>
        <div className="top-status">
          {!connected && <span className="preview-pill">PREVIEW DATA</span>}
          <span className={`status-pill ${connected ? "live" : "offline"}`}><i />{connected ? "COSMIC LIVE" : "BRIDGE OFFLINE"}</span>
          <span className="status-pill model"><Sparkles size={13} /> QWEN LOCAL</span>
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
            <div className="empty-state"><Bot size={42} /><h2>{selected.name} is offline</h2><p>Load the stored character into a neutral, safely parked Director session.</p><button className="primary" onClick={() => mutate(`agents/${selected.characterId}/spawn`, "POST", { world: 0, channel: 1 }, "spawn")}>Load on channel 1</button></div>
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
              <div className="action-grid">{available.slice(0, 6).map((action) => <ActionCard key={action.actionId} action={action} disabled={!connected || !!busy} onExecute={() => mutate(`agents/${selectedId}/actions`, "POST", { actionId: action.actionId, contextRevision: view.contextRevision, reason: "manual Director panel selection", confirmDestructive: false }, action.actionId)} />)}</div>
            </div>

            <Timeline view={view} />
          </> : <div className="empty-state"><RefreshCw className="spin" /><p>Reading live Agent state…</p></div>}
        </section>

        <aside className={`decision-rail panel-section ${section === "proposals" || section === "chat" ? "mobile-active" : ""}`}>
          <div className={`proposal-pane ${section === "chat" ? "mobile-hidden" : ""}`}>
            <div className="section-heading"><div><span>ASSISTED DECISIONS</span><strong>Proposal queue</strong></div>{pending.length > 0 && <b>{pending.length}</b>}</div>
            <div className="proposal-list">{pending.length ? pending.map((proposal) => <ProposalCard key={proposal.proposalId} proposal={proposal} disabled={!connected || !!busy} onApprove={() => mutate(`agents/${selectedId}/proposals/${proposal.proposalId}/approve`, "POST", { confirmDestructive: false }, "approve")} onReject={() => mutate(`agents/${selectedId}/proposals/${proposal.proposalId}/reject`, "POST", { reason: "rejected from Director panel" }, "reject")} />) : <div className="no-proposal"><Check size={18} /><strong>No decision waiting</strong><span>Ask the Director or request a policy proposal.</span></div>}</div>
          </div>
          <div className={`chat-pane ${section === "proposals" ? "mobile-hidden" : ""}`}>
            <div className="section-heading"><div><span>DIRECTOR CONVERSATION</span><strong>Plan with the Director</strong></div><MessageSquareText size={17} /></div>
            <div className="messages">{messages.map((message, index) => <div key={index} className={`message ${message.role}`}><span>{message.role === "operator" ? "YOU" : "DIRECTOR"}</span><p>{message.text}</p></div>)}</div>
            <form className="chat-form" onSubmit={submitChat}><input value={chatInput} onChange={(event) => setChatInput(event.target.value)} disabled={!connected || busy === "chat"} placeholder={connected ? "Ask why, or request a proposal…" : "Connect Cosmic to use Director chat"} aria-label="Director message" /><button disabled={!chatInput.trim() || !connected || busy === "chat"} aria-label="Send"><Send size={16} /></button></form>
          </div>
        </aside>
      </div>
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

function Timeline({ view }: { view: DirectorView }) {
  const entries = [...view.journey].slice(-4).reverse();
  return <div className="timeline"><div className="card-title"><div><span>AUDIT TRAIL</span><h2>Recent journey</h2></div><History size={17} /></div>{entries.length ? entries.map((entry, index) => <div className="timeline-row" key={`${entry.eventId}-${index}`}><i /><div><strong>{String(entry.type).replaceAll("_", " ")}</strong><p>{String(entry.reason)}</p></div><time>{new Date(Number(entry.occurredAtMs)).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</time></div>) : <p className="timeline-empty">No recent journey events.</p>}</div>;
}
