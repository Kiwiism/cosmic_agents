"use client";

import { ChevronRight, RotateCcw } from "lucide-react";
import { FormEvent, useState } from "react";
import { api } from "../../lib/api";

type ConsoleAuthProps = {
  mark: string;
  productName: string;
  secureLabel: string;
  setup: boolean;
  onReady: () => void;
};

export function ConsoleAuth({
  mark,
  productName,
  secureLabel,
  setup,
  onReady,
}: ConsoleAuthProps) {
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      if (setup) {
        await api("/api/setup", {
          method: "POST",
          body: JSON.stringify({
            username: form.get("username"),
            displayName: form.get("displayName"),
            password: form.get("password"),
          }),
        });
      }
      await api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({
          username: form.get("username"),
          password: form.get("password"),
        }),
      });
      onReady();
    } catch (failure) {
      setError((failure as Error).message);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <div className="console-brand-mark large">{mark}</div>
        <p className="eyebrow">{setup ? "FIRST RUN" : secureLabel}</p>
        <h1>{setup ? `Create ${productName} owner` : productName}</h1>
        {setup && (
          <label>
            Display name
            <input name="displayName" defaultValue="admin" required minLength={2} />
          </label>
        )}
        <label>
          Username
          <input name="username" defaultValue={setup ? "admin" : ""} required autoFocus />
        </label>
        <label>
          Password
          <input name="password" type="password" required minLength={setup ? 5 : 1} />
        </label>
        {error && <div className="form-error">{error}</div>}
        <button className="primary">
          {setup ? "Create owner" : "Sign in"}
          <ChevronRight size={16} />
        </button>
      </form>
    </div>
  );
}

export function ConsoleConnectionError({
  mark,
  productName,
  message,
}: {
  mark: string;
  productName: string;
  message: string;
}) {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="console-brand-mark large">{mark}</div>
        <p className="eyebrow">CONNECTION REQUIRED</p>
        <h1>{productName} API unavailable</h1>
        <p className="muted">{message}</p>
        <button className="primary" onClick={() => location.reload()}>
          <RotateCcw size={16} />
          Try again
        </button>
      </div>
    </div>
  );
}
