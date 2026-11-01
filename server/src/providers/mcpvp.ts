import { canonicalKit } from "../kits";
import type { ListResult, PlayerRef, TierEntry } from "../types";
import { plainUuid } from "../validate";
import { checkStatus, isRecord, numberOrNull, parseJson, Provider, ProviderError, stringOrNull } from "./provider";

// McPvP only offers a name search, and it's a loose one: searching "ItzRealMe"
// returns 28 other people and not the player himself. The UUID is the only
// reliable way to tell whether the person we want is in the results.

const MIN_QUERY_LENGTH = 3; // shorter queries get a 422
const RESULT_CAP = 50; // results are sorted by points and cut off here

const RANK = /^(HT|MT|LT|ST)[1-6]$/;

function validRank(value: unknown): string | null {
  return typeof value === "string" && RANK.test(value) ? value : null;
}

export const mcpvp: Provider = {
  id: "mcpvp",

  url(player: PlayerRef): string | ListResult {
    if (!player.name) return { status: "unavailable", reason: "name_required" };
    if (player.name.length < MIN_QUERY_LENGTH) return { status: "unavailable", reason: "name_too_short" };
    return `https://www.mcpvp.com/tiers/search?q=${encodeURIComponent(player.name)}`;
  },

  parse(status: number, body: string, player: PlayerRef): ListResult {
    const early = checkStatus(status);
    if (early) return early;

    const data = parseJson(body);
    if (!isRecord(data) || !Array.isArray(data.players)) throw new ProviderError("bad_shape");

    const wanted = plainUuid(player.uuid);
    const match = data.players.find((p) => isRecord(p) && p.uuid === wanted);

    if (!isRecord(match)) {
      // A full page sorted by points could have pushed a low-ranked player off
      // the end, so we can't honestly say they're unranked.
      if (data.players.length >= RESULT_CAP) return { status: "unavailable", reason: "search_truncated" };
      return { status: "unranked" };
    }

    if (!isRecord(match.kitRanks)) throw new ProviderError("bad_shape");
    const peaks = isRecord(match.kitPeakRanks) ? match.kitPeakRanks : {};
    const retired = isRecord(match.kitRetired) ? match.kitRetired : {};

    const tiers: TierEntry[] = [];
    for (const [rawKit, rawRank] of Object.entries(match.kitRanks)) {
      const rank = validRank(rawRank);
      if (!rank) continue;
      tiers.push({
        kit: canonicalKit(rawKit),
        rank,
        peak: validRank(peaks[rawKit]),
        attained: null,
        retired: retired[rawKit] === true,
      });
    }

    if (tiers.length === 0) return { status: "unranked" };

    return {
      status: "ok",
      region: stringOrNull(match.region),
      country: stringOrNull(match.country),
      points: numberOrNull(match.points),
      overall: numberOrNull(match.rank),
      tiers,
    };
  },
};
