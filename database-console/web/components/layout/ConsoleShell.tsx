"use client";

import { CircleUserRound, LucideIcon } from "lucide-react";
import { ReactNode } from "react";

export type ConsoleNavItem<T extends string> = {
  key: T;
  label: string;
  icon: LucideIcon;
};

type ConsoleShellProps<T extends string> = {
  activeView: T;
  brandMark: string;
  brandSubtitle: string;
  eyebrow: string;
  headerStatus: string;
  inspectorOpen?: boolean;
  inspectorSize?: "standard" | "wide";
  navigation: readonly ConsoleNavItem<T>[];
  onNavigate: (view: T) => void;
  sidebarStatus: ReactNode;
  children: ReactNode;
  inspector?: ReactNode;
};

export function ConsoleShell<T extends string>({
  activeView,
  brandMark,
  brandSubtitle,
  eyebrow,
  headerStatus,
  inspectorOpen = false,
  inspectorSize = "standard",
  navigation,
  onNavigate,
  sidebarStatus,
  children,
  inspector,
}: ConsoleShellProps<T>) {
  const current = navigation.find((item) => item.key === activeView);

  return (
    <div className={`console-shell${inspectorOpen ? ` has-inspector inspector-${inspectorSize}` : ""}`}>
      <aside className="console-sidebar">
        <div className="console-brand">
          <div className="console-brand-mark">{brandMark}</div>
          <div>
            <strong>Cosmic</strong>
            <small>{brandSubtitle}</small>
          </div>
        </div>
        <nav className="console-navigation" aria-label={`${brandSubtitle} navigation`}>
          {navigation.map(({ key, label, icon: Icon }) => (
            <button
              type="button"
              className={activeView === key ? "active" : ""}
              key={key}
              onClick={() => onNavigate(key)}
            >
              <Icon size={18} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
        <div className="console-sidebar-status">{sidebarStatus}</div>
      </aside>
      <main className="console-main">
        <header className="console-header">
          <div>
            <p className="eyebrow">{eyebrow}</p>
            <h1>{current?.label}</h1>
          </div>
          <div className="console-header-actions">
            <span className="console-status-pill">{headerStatus}</span>
            <CircleUserRound size={30} />
          </div>
        </header>
        <section className="workspace">{children}</section>
      </main>
      {inspector}
    </div>
  );
}
