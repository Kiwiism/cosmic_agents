"use client";

import { ChevronDown, ChevronRight, LucideIcon, Moon, Sun } from "lucide-react";
import { ReactNode, useEffect, useState } from "react";

export type ConsoleNavItem<T extends string> = {
  key?: T;
  label: string;
  icon?: LucideIcon;
  badge?: string;
  defaultCollapsed?: boolean;
  children?: readonly ConsoleNavItem<T>[];
};

type ConsoleShellProps<T extends string> = {
  activeView: T;
  brandMark: string;
  brandSubtitle: string;
  eyebrow: string;
  headerTitle?: ReactNode;
  headerTabs?: ReactNode;
  headerStatus?: string;
  inspectorOpen?: boolean;
  inspectorSize?: "standard" | "wide";
  navigation: readonly ConsoleNavItem<T>[];
  onNavigate: (view: T) => void;
  sidebarStatus: ReactNode;
  theme?: "light" | "dark";
  onToggleTheme?: () => void;
  children: ReactNode;
  inspector?: ReactNode;
};

export function ConsoleShell<T extends string>({
  activeView,
  brandMark,
  brandSubtitle,
  eyebrow,
  headerTitle,
  headerTabs,
  headerStatus,
  inspectorOpen = false,
  inspectorSize = "standard",
  navigation,
  onNavigate,
  sidebarStatus,
  theme = "light",
  onToggleTheme,
  children,
  inspector,
}: ConsoleShellProps<T>) {
  const current = findNavItem(navigation, activeView);

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
          {navigation.map((item) => (
            <NavEntry item={item} activeView={activeView} onNavigate={onNavigate} key={item.key ?? item.label} />
          ))}
        </nav>
        <div className="console-sidebar-footer">
          <div className="console-sidebar-status">{sidebarStatus}</div>
          {onToggleTheme && (
            <button
              type="button"
              className="theme-toggle"
              onClick={onToggleTheme}
              title={theme === "dark" ? "Switch to day mode" : "Switch to night mode"}
              aria-label={theme === "dark" ? "Switch to day mode" : "Switch to night mode"}
            >
              {theme === "dark" ? <Sun size={17} /> : <Moon size={17} />}
            </button>
          )}
        </div>
      </aside>
      <main className="console-main">
        <header className="console-header">
          <div className="console-header-title">
            <p className="eyebrow">{eyebrow}</p>
            <h1>{headerTitle ?? current?.label}</h1>
          </div>
          {headerTabs && <div className="console-header-tabs">{headerTabs}</div>}
          {headerStatus && <div className="console-header-actions"><span className="console-status-pill">{headerStatus}</span></div>}
        </header>
        <section className="workspace">{children}</section>
      </main>
      {inspector}
    </div>
  );
}

function findNavItem<T extends string>(items: readonly ConsoleNavItem<T>[], key: T): ConsoleNavItem<T> | undefined {
  for (const item of items) {
    if (item.key === key) return item;
    const found = item.children ? findNavItem(item.children, key) : undefined;
    if (found) return found;
  }
  return undefined;
}

function containsActive<T extends string>(item: ConsoleNavItem<T>, activeView: T): boolean {
  return item.key === activeView || Boolean(item.children?.some((child) => containsActive(child, activeView)));
}

function NavEntry<T extends string>({
  item,
  activeView,
  onNavigate,
}: {
  item: ConsoleNavItem<T>;
  activeView: T;
  onNavigate: (view: T) => void;
}) {
  const Icon = item.icon;
  if (item.children?.length) {
    return <NavGroup item={item} activeView={activeView} onNavigate={onNavigate} />;
  }
  if (!item.key) return null;
  return (
    <button
      type="button"
      className={activeView === item.key ? "active" : ""}
      onClick={() => item.key && onNavigate(item.key)}
    >
      {Icon && <Icon size={18} />}
      <span>{item.label}</span>
      {item.badge && <em>{item.badge}</em>}
    </button>
  );
}

function NavGroup<T extends string>({
  item,
  activeView,
  onNavigate,
}: {
  item: ConsoleNavItem<T>;
  activeView: T;
  onNavigate: (view: T) => void;
}) {
  const Icon = item.icon;
  const active = containsActive(item, activeView);
  const [collapsed, setCollapsed] = useState(Boolean(item.defaultCollapsed) && !active);

  useEffect(() => {
    if (active) setCollapsed(false);
  }, [active]);

  return (
    <div className={`console-nav-group${active ? " open" : ""}${collapsed ? " collapsed" : ""}`}>
      <button
        type="button"
        className="console-nav-parent"
        onClick={() => setCollapsed((value) => !value)}
        aria-expanded={!collapsed}
      >
        {Icon && <Icon size={17} />}
        <span>{item.label}</span>
        {item.badge && <em>{item.badge}</em>}
        {collapsed ? <ChevronRight className="console-nav-caret" size={14} /> : <ChevronDown className="console-nav-caret" size={14} />}
      </button>
      {!collapsed && (
        <div className="console-nav-children">
          {item.children?.map((child) => (
            <NavEntry item={child} activeView={activeView} onNavigate={onNavigate} key={child.key ?? child.label} />
          ))}
        </div>
      )}
    </div>
  );
}
