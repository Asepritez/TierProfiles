import type { PlayerRef } from "./types";

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const MINECRAFT_NAME = /^[A-Za-z0-9_]{1,16}$/;

/**
 * Accepts a dashed or plain UUID and returns it dashed and lower case, or null.
 * Only version 4 passes: real Java accounts are v4, while NPCs and Bedrock
 * players get other versions, and asking the lists about those wastes requests.
 */
export function normalizeUuid(input: string): string | null {
  const hex = input.trim().toLowerCase().replaceAll("-", "");
  if (!/^[0-9a-f]{32}$/.test(hex)) return null;
  const dashed = [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join("-");
  return UUID_V4.test(dashed) ? dashed : null;
}

export function plainUuid(dashed: string): string {
  return dashed.replaceAll("-", "");
}

/** A name that isn't a valid Minecraft name is treated as "no name given", not an error. */
export function normalizeName(input: unknown): string | null {
  return typeof input === "string" && MINECRAFT_NAME.test(input) ? input : null;
}

export function toPlayerRef(uuid: unknown, name: unknown): PlayerRef | null {
  if (typeof uuid !== "string") return null;
  const normalized = normalizeUuid(uuid);
  return normalized ? { uuid: normalized, name: normalizeName(name) } : null;
}
