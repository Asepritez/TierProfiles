import { describe, expect, it } from "vitest";
import { canonicalKit } from "../src/kits";
import { PROVIDERS } from "../src/providers";
import { mcpvp } from "../src/providers/mcpvp";
import { ProviderError } from "../src/providers/provider";
import { pvphq } from "../src/providers/pvphq";
import { parseRankings } from "../src/providers/rankings";
import { ListOk, ListResult } from "../src/types";
import { fixture, ITZ, JOKING } from "./helpers";

function ok(result: ListResult): ListOk {
  if (result.status !== "ok") throw new Error(`expected ok, got ${JSON.stringify(result)}`);
  return result;
}

function tier(result: ListOk, kit: string) {
  return result.tiers.find((t) => t.kit === kit);
}

describe("MCTiers-style parser, from real responses", () => {
  it("reads MCTiers, including pos, peak and retired", () => {
    const result = ok(parseRankings(200, fixture("mctiers-ranked.json")));
    expect(result.region).toBe("NA");
    expect(result.points).toBe(330);
    expect(result.overall).toBe(2);
    expect(tier(result, "sword")).toEqual({ kit: "sword", rank: "HT3", peak: "HT2", attained: 1772946009, retired: false });
    expect(tier(result, "axe")).toMatchObject({ rank: "LT2", peak: "LT2", retired: true });
  });

  it("gives a null peak when the list sends null", () => {
    expect(tier(ok(parseRankings(200, fixture("mctiers-ranked.json"))), "neth_pot")?.peak).toBeNull();
  });

  it("merges nethop and neth_pot into one kit", () => {
    const result = ok(parseRankings(200, fixture("mctiers-ranked.json")));
    expect(result.tiers.filter((t) => t.kit === "neth_pot")).toHaveLength(1);
    expect(tier(result, "nethop")).toBeUndefined();
  });

  it("calls MCTiers vanilla the same thing PvPTiers calls crystal", () => {
    expect(tier(ok(parseRankings(200, fixture("mctiers-ranked.json"))), "crystal")?.attained).toBe(1729201725);
    expect(tier(ok(parseRankings(200, fixture("pvptiers-ranked.json"))), "crystal")?.attained).toBe(1729201725);
  });

  it("reads SubTiers without passing extra fields through", () => {
    const result = ok(parseRankings(200, fixture("subtiers-full.json")));
    expect(tier(result, "dia_smp")).toMatchObject({ rank: "LT1", retired: true });
    expect(JSON.stringify(result)).not.toContain("discord");
    expect(JSON.stringify(result)).not.toContain("badges");
  });

  it("treats a 404 as unranked, with or without a body", () => {
    expect(parseRankings(404, fixture("mctiers-unranked.json"))).toEqual({ status: "unranked" });
    expect(parseRankings(404, fixture("pvptiers-unranked.json"))).toEqual({ status: "unranked" });
    expect(parseRankings(404, fixture("subtiers-unranked.json"))).toEqual({ status: "unranked" });
  });

  it("treats an empty rankings object as unranked", () => {
    expect(parseRankings(200, `{"rankings":{}}`)).toEqual({ status: "unranked" });
  });

  it("skips a malformed kit but keeps the rest", () => {
    const body = `{"rankings":{"sword":{"tier":9,"pos":0},"axe":"nope","mace":{"tier":2,"pos":1}}}`;
    expect(ok(parseRankings(200, body)).tiers.map((t) => t.kit)).toEqual(["mace"]);
  });

  it.each([
    [500, "oops", "http_500"],
    [429, "slow down", "http_429"],
    [200, "<html>", "bad_json"],
    [200, `{"message":"new format"}`, "bad_shape"],
  ])("throws on status %i body %s", (status, body, reason) => {
    expect(() => parseRankings(status, body)).toThrow(new ProviderError(reason));
  });
});

describe("McPvP", () => {
  it("finds the player by UUID and reads every kit", () => {
    const result = ok(mcpvp.parse(200, fixture("mcpvp-full.json"), JOKING));
    expect(result.region).toBe("EU");
    expect(result.points).toBe(280);
    expect(result.overall).toBe(1);
    expect(result.country).toBeTruthy();
    expect(tier(result, "sword")).toEqual({ kit: "sword", rank: "HT3", peak: "HT3", attained: null, retired: false });
    expect(tier(result, "spear")?.rank).toBe("MT3");
  });

  it("uses our kit names", () => {
    const result = ok(mcpvp.parse(200, fixture("mcpvp-full.json"), JOKING));
    expect(tier(result, "minecart")?.rank).toBe("LT1");
    expect(tier(result, "dia_smp")?.rank).toBe("HT2");
    expect(tier(result, "neth_pot")?.rank).toBe("LT3");
    expect(tier(result, "early_game")?.rank).toBe("HT2");
  });

  it("is not fooled by similar names: search hits with no UUID match are unranked", () => {
    expect(mcpvp.parse(200, fixture("mcpvp-search-miss.json"), ITZ)).toEqual({ status: "unranked" });
  });

  it("returns unranked for empty results", () => {
    expect(mcpvp.parse(200, fixture("mcpvp-unranked.json"), ITZ)).toEqual({ status: "unranked" });
  });

  it("won't claim unranked when a full page may have hidden the player", () => {
    const players = Array.from({ length: 50 }, (_, i) => ({ uuid: `other${i}`, kitRanks: {} }));
    expect(mcpvp.parse(200, JSON.stringify({ players }), ITZ)).toEqual({ status: "unavailable", reason: "search_truncated" });
  });

  it("reads a retired kit and accepts ST6", () => {
    const uuid = ITZ.uuid.replaceAll("-", "");
    const body = JSON.stringify({
      players: [{ uuid, kitRanks: { sword: "ST6" }, kitPeakRanks: { sword: "HT5" }, kitRetired: { sword: true } }],
    });
    expect(ok(mcpvp.parse(200, body, ITZ)).tiers[0]).toMatchObject({ rank: "ST6", peak: "HT5", retired: true });
  });

  it("can't search without a usable name, and says why", () => {
    expect(mcpvp.url({ uuid: ITZ.uuid, name: null })).toEqual({ status: "unavailable", reason: "name_required" });
    expect(mcpvp.url({ uuid: ITZ.uuid, name: "ab" })).toEqual({ status: "unavailable", reason: "name_too_short" });
    expect(mcpvp.url(ITZ)).toBe("https://www.mcpvp.com/tiers/search?q=ItzRealMe");
  });
});

describe("PVPHQ, against a made-up response built from HQTiers field names", () => {
  // Not a capture: this API has never answered us. See the note in pvphq.ts.
  const body = JSON.stringify({
    rank: "HT2",
    globalPosition: 41,
    data: {
      SWORD: { rating: 1800, peakRating: 1900, gamesPlayed: 50, grantedTier: "High Tier 3" },
      AXE: { rating: 1500, gamesPlayed: 12, grantedTier: "LT4" },
      MACE: { rating: 1000, gamesPlayed: 2, grantedTier: null },
    },
  });

  it("normalizes tiers and leaves out ladders still in placement", () => {
    const result = ok(pvphq.parse(200, body, ITZ));
    expect(result.tiers).toHaveLength(2);
    expect(tier(result, "sword")).toMatchObject({ rank: "HT3", rating: 1800, games: 50 });
    expect(tier(result, "axe")?.rank).toBe("LT4");
  });

  it("handles every way of saying nothing", () => {
    expect(pvphq.parse(404, "", ITZ)).toEqual({ status: "unranked" });
    expect(pvphq.parse(200, "null", ITZ)).toEqual({ status: "unranked" });
    expect(pvphq.parse(200, `{"data":{}}`, ITZ)).toEqual({ status: "unranked" });
  });

  it("treats the 502 it was actually serving as a failure", () => {
    expect(() => pvphq.parse(502, "error code: 502", ITZ)).toThrow(new ProviderError("http_502"));
  });
});

describe("URLs", () => {
  const urls = Object.fromEntries(PROVIDERS.map((p) => [p.id, p.url(ITZ)]));

  it("sends PvPTiers a plain UUID and everyone else a dashed one", () => {
    expect(urls.pvptiers).toBe("https://pvptiers.com/api/profile/06ec3577329945fabbdf613b1f86c8ab");
    expect(urls.mctiers).toBe(`https://mctiers.gg/api/profile/${ITZ.uuid}`);
    expect(urls.subtiers).toBe(`https://subtiers.net/api/v2/profile/${ITZ.uuid}`);
    expect(urls.pvphq).toBe(`https://pvphq.com/api/ranked/${ITZ.uuid}`);
  });

  it("asks MCTiers first and PVPHQ last", () => {
    expect(PROVIDERS[0]?.id).toBe("mctiers");
    expect(PROVIDERS.at(-1)?.id).toBe("pvphq");
  });
});

describe("canonicalKit", () => {
  it.each([
    ["vanilla", "crystal"],
    ["nethop", "neth_pot"],
    ["netherite-pot", "neth_pot"],
    ["cart", "minecart"],
    ["diamond-smp", "dia_smp"],
    ["end-game", "end_game"],
    ["SWORD", "sword"],
    ["brand-new-kit", "brand_new_kit"],
  ])("%s becomes %s", (raw, expected) => {
    expect(canonicalKit(raw)).toBe(expected);
  });
});
