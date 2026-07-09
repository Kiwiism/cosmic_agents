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

const NPC_RENDER_ALIASES: Record<number, number> = {
  2004: 2003,
};

export function assetUrls(type: string, id: number, properties?: Record<string, unknown>) {
  const renderId = type === "NPC" ? NPC_RENDER_ALIASES[id] ?? id : id;
  const mobAction = String(properties?.imageAction ?? "stand");
  const route = type === "ITEM" ? `item/${renderId}/icon`
    : type === "HAIR" || type === "FACE" ? `item/${renderId}/icon`
    : type === "MOB" ? `mob/${renderId}/render/${mobAction}`
    : type === "NPC" ? `npc/${renderId}/render/stand`
    : type === "SKILL" ? ""
    : type === "MAP" ? `map/${renderId}/miniMap`
    : `${type.toLowerCase()}/${renderId}/icon`;
  if (!route) return [];
  const primary = `https://maplestory.io/api/GMS/83/${route}`;
  if (type === "MOB") {
    return [...new Set([mobAction, "stand", "move", "fly"])]
      .map(action => `https://maplestory.io/api/GMS/83/mob/${renderId}/render/${action}`);
  }
  return [primary];
}

export function avatarUrl(options: {
  skinColor: number;
  hair: number;
  face: number;
  equips: number[];
  animation?: string;
  frame?: number;
}) {
  const skin = 2000 + Number(options.skinColor || 0);
  const animation = options.animation ?? "stand1";
  const frame = options.frame ?? 0;
  const items = [Number(options.hair || 0), Number(options.face || 0), ...options.equips]
    .filter((id) => Number.isFinite(id) && id > 0);
  return `https://maplestory.io/api/GMS/83/Character/${skin}/${items.join(",")}/${animation}/${frame}`;
}
