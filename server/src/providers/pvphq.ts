import { canonicalKit } from "../kits";
import type { ListResult, PlayerRef, TierEntry } from "../types";
import { checkStatus, isRecord, numberOrNull, parseJson, Provider, ProviderError } from "./provider";

// UNVERIFIED. This endpoint has never answered us: it hung on every request
// during development while the rest of pvphq.com stayed up. The field names come
// from the MIT mod MicahXX/HQTiers, so expect to revisit this once it returns.
//
// Expected shape: { data: { sword: { rating, peakRating, gamesPlayed, grantedTier } } }
// where a missing grantedTier means the player hasn't finished placement.

const SPELLED_OUT: [RegExp, string][] = [
  [/^HIGH[_ -]?TIER[_ -]?/, "HT"],
  [/^MID[_ -]?TIER[_ -]?/, "MT"],
  [/^LOW[_ -]?TIER[_ -]?/, "LT"],
];

/** HQTiers accepts both "HT3" and "High Tier 3", so we do too. */
function normalizeRank(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  let rank = raw.trim().toUpperCase();
  for (const [pattern, short] of SPELLED_OUT) rank = rank.replace(pattern, short);
  rank = rank.replaceAll(/[_ -]/g, "");
  return /^(HT|MT|LT)[1-5]$/.test(rank) ? rank : null;
}

export const pvphq: Provider = {
  id: "pvphq",

  url(player: PlayerRef): string {
    return `https://pvphq.com/api/ranked/${player.uuid}`;
  },

  parse(status: number, body: string): ListResult {
    const early = checkStatus(status);
    if (early) return early;
    if (body.trim() === "" || body.trim() === "null") return { status: "unranked" };

    const root = parseJson(body);
    if (!isRecord(root)) throw new ProviderError("bad_shape");
    if (!isRecord(root.data)) return { status: "unranked" };

    const tiers: TierEntry[] = [];
    for (const [rawKit, value] of Object.entries(root.data)) {
      if (!isRecord(value)) continue;
      const rank = normalizeRank(value.grantedTier);
      if (!rank) continue;
      tiers.push({
        kit: canonicalKit(rawKit),
        rank,
        peak: null,
        attained: null,
        retired: false,
        rating: numberOrNull(value.rating) ?? undefined,
        games: numberOrNull(value.gamesPlayed) ?? undefined,
      });
    }

    if (tiers.length === 0) return { status: "unranked" };

    return {
      status: "ok",
      region: null,
      country: null,
      points: null,
      overall: numberOrNull(root.globalPosition),
      tiers,
    };
  },
};
