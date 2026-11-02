// The normalized shape the mod receives. Every list, however odd its own API,
// ends up looking like this. docs/SPEC.md describes the same shape in prose.

export const LIST_IDS = ["mctiers", "mcpvp", "pvptiers", "subtiers", "pvphq"] as const;
export type ListId = (typeof LIST_IDS)[number];

export interface TierEntry {
  /** Canonical gamemode id, see kits.ts. Unknown kits pass through unchanged. */
  kit: string;
  /** Band plus level: "HT3", "LT1", and on McPvP also "MT4" or "ST6". */
  rank: string;
  /** Best rank ever held, or null when the list doesn't say. */
  peak: string | null;
  /** Unix seconds when the rank was attained, or null when the list doesn't say. */
  attained: number | null;
  retired: boolean;
  /** PVPHQ only: competitive rating, because its tiers come from a rating ladder. */
  rating?: number;
  /** PVPHQ only: games played on this ladder. */
  games?: number;
}

export interface ListOk {
  status: "ok";
  region: string | null;
  /** Two-letter country code. Only McPvP provides this. */
  country: string | null;
  points: number | null;
  /** Position on that list's overall leaderboard. */
  overall: number | null;
  tiers: TierEntry[];
}

export interface ListUnranked {
  status: "unranked";
}

export interface ListUnavailable {
  status: "unavailable";
  reason: string;
}

export type ListResult = ListOk | ListUnranked | ListUnavailable;

export interface Profile {
  uuid: string;
  name: string | null;
  lists: Record<ListId, ListResult>;
}

/** A player as the mod asked for them. `uuid` is dashed and lower case by this point. */
export interface PlayerRef {
  uuid: string;
  name: string | null;
}
