import catalog from "./cosmetics.json";

// A card's style. Every value must be on the allowlist in cosmetics.json, which the
// mod's tests check against its own copy, so players can only choose presets.

export interface Cosmetics {
  background: string;
  border: string;
  helmet: string;
  chestplate: string;
  leggings: string;
  boots: string;
  trimPattern: string;
  trimMaterial: string;
  effect: string;
  intensity: number;
  accent: number;
  featuredKit: string | null;
  featuredList: string | null;
}

const LIST_IDS = ["mctiers", "mcpvp", "pvptiers", "subtiers", "pvphq"];
const KIT = /^[a-z0-9_]{1,24}$/;
export const SAVE_COOLDOWN_MS = 10_000;

function oneOf(allowed: string[], value: unknown): value is string {
  return typeof value === "string" && allowed.includes(value);
}

/** Returns a clean copy, or null if anything is off the list. Unknown keys are dropped. */
export function validateCosmetics(input: unknown): Cosmetics | null {
  if (typeof input !== "object" || input === null || Array.isArray(input)) return null;
  const value = input as Record<string, unknown>;
  const { intensity, accent, featuredKit, featuredList } = value;

  if (
    !oneOf(catalog.backgrounds, value.background) ||
    !oneOf(catalog.borders, value.border) ||
    !oneOf(catalog.armor, value.helmet) ||
    !oneOf(catalog.armor, value.chestplate) ||
    !oneOf(catalog.armor, value.leggings) ||
    !oneOf(catalog.armor, value.boots) ||
    !oneOf(catalog.trimPatterns, value.trimPattern) ||
    !oneOf(catalog.trimMaterials, value.trimMaterial) ||
    !oneOf(catalog.effects, value.effect)
  ) {
    return null;
  }
  if (typeof intensity !== "number" || !Number.isInteger(intensity) || intensity < 0 || intensity > 100) return null;
  if (typeof accent !== "number" || !catalog.accents.includes(accent)) return null;
  if (featuredKit != null && (typeof featuredKit !== "string" || !KIT.test(featuredKit))) return null;
  if (featuredList != null && !oneOf(LIST_IDS, featuredList)) return null;

  return {
    background: value.background,
    border: value.border,
    helmet: value.helmet,
    chestplate: value.chestplate,
    leggings: value.leggings,
    boots: value.boots,
    trimPattern: value.trimPattern,
    trimMaterial: value.trimMaterial,
    effect: value.effect,
    intensity,
    accent,
    featuredKit: (featuredKit as string | null | undefined) ?? null,
    featuredList: (featuredList as string | null | undefined) ?? null,
  };
}

/** The small part of a D1 database we use, so tests can supply a fake. */
export interface CosmeticsStore {
  read(uuid: string): Promise<{ data: string; updatedAt: number } | null>;
  write(uuid: string, data: string, updatedAt: number): Promise<void>;
}

export function d1Store(db: D1Database): CosmeticsStore {
  return {
    async read(uuid) {
      const row = await db.prepare("SELECT data, updated_at FROM cosmetics WHERE uuid = ?").bind(uuid).first<{ data: string; updated_at: number }>();
      return row ? { data: row.data, updatedAt: row.updated_at } : null;
    },
    async write(uuid, data, updatedAt) {
      await db
        .prepare("INSERT INTO cosmetics (uuid, data, updated_at) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at")
        .bind(uuid, data, updatedAt)
        .run();
    },
  };
}
