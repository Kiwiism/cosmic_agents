const API = process.env.NEXT_PUBLIC_DATABASE_CONSOLE_API_URL ?? "http://localhost:8081";

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.detail ?? body?.message ?? `${response.status} ${response.statusText}`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function assetUrl(type: string, id: number) {
  return assetUrls(type, id)[0];
}

export function assetUrls(type: string, id: number, properties?: Record<string, unknown>) {
  const mobAction = String(properties?.imageAction ?? "stand");
  const route = type === "ITEM" ? `item/${id}/icon`
    : type === "MOB" ? `mob/${id}/render/${mobAction}`
    : type === "NPC" ? `npc/${id}/render/stand`
    : type === "SKILL" ? ""
    : type === "MAP" ? `map/${id}/miniMap`
    : `${type.toLowerCase()}/${id}/icon`;
  if (!route) return [];
  const primary = `https://maplestory.io/api/GMS/83/${route}`;
  if (type === "MOB") {
    return [...new Set([mobAction, "stand", "move", "fly"])]
      .map(action => `https://maplestory.io/api/GMS/83/mob/${id}/render/${action}`);
  }
  return [primary];
}
