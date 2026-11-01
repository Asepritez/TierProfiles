import { canonicalKit } from "../kits";
import type { ListResult, TierEntry } from "../types";
import { checkStatus, isRecord, numberOrNull, parseJson, ProviderError, stringOrNull } from "./provider";

// MCTiers, PvPTiers and SubTiers share a response format, so they share a parser:
//   { region, points, overall, rankings: { sword: { tier, pos, peak_tier, peak_pos, attained, retired } } }
// `pos` 0 is High and 1 is Low, so tier 3 pos 1 is LT3.

function rankLabel(tier: unknown, pos: unknown): string | null {
  if (typeof tier !== "number" || !Number.isInteger(tier) || tier < 1 || tier > 5) return null;
  if (pos !== 0 && pos !== 1) return null;
  return `${pos === 0 ? "HT" : "LT"}${tier}`;
}

export function parseRankings(status: number, body: string): ListResult {
  const early = checkStatus(status);
  if (early) return early;

  const data = parseJson(body);
  if (!isRecord(data) || !isRecord(data.rankings)) throw new ProviderError("bad_shape");

  const tiers: TierEntry[] = [];
  const seen = new Set<string>();

  for (const [rawKit, value] of Object.entries(data.rankings)) {
    if (!isRecord(value)) continue;
    const rank = rankLabel(value.tier, value.pos);
    if (!rank) continue;

    // MCTiers sends both "nethop" and "neth_pot", which become one kit here.
    const kit = canonicalKit(rawKit);
    if (seen.has(kit)) continue;
    seen.add(kit);

    tiers.push({
      kit,
      rank,
      peak: rankLabel(value.peak_tier, value.peak_pos),
      attained: numberOrNull(value.attained),
      retired: value.retired === true,
    });
  }

  if (tiers.length === 0) return { status: "unranked" };

  return {
    status: "ok",
    region: stringOrNull(data.region),
    country: null,
    points: numberOrNull(data.points),
    overall: numberOrNull(data.overall),
    tiers,
  };
}
