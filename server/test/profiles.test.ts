import { describe, expect, it } from "vitest";
import { getProfiles, SUBREQUEST_BUDGET } from "../src/profiles";
import { PlayerRef } from "../src/types";
import { allNotFound, FakeEdge, fakeFetch, fixture, hostOf, ITZ, JOKING, makeDeps, reply } from "./helpers";

const MINUTE = 60_000;

function liveLists(url: string) {
  switch (hostOf(url)) {
    case "mctiers.gg":
      return reply(200, fixture("mctiers-full.json"));
    case "www.mcpvp.com":
      return reply(200, fixture("mcpvp-full.json"));
    case "pvptiers.com":
      return reply(200, fixture("pvptiers-full.json"));
    case "subtiers.net":
      return reply(200, fixture("subtiers-full.json"));
    default:
      return new Error("connection reset");
  }
}

function randomPlayers(count: number): PlayerRef[] {
  return Array.from({ length: count }, (_, i) => ({
    uuid: `00000000-0000-4000-8000-00000000000${i}`,
    name: `Player${i}`,
  }));
}

describe("merging", () => {
  it("merges every list for one player", async () => {
    const { fn } = fakeFetch(liveLists);
    const [profile] = await getProfiles([JOKING], makeDeps(fn));
    expect(profile?.lists.mctiers.status).toBe("ok");
    expect(profile?.lists.mcpvp.status).toBe("ok");
    expect(profile?.lists.pvptiers.status).toBe("ok");
    expect(profile?.lists.subtiers.status).toBe("ok");
    expect(profile?.lists.pvphq).toEqual({ status: "unavailable", reason: "network" });
  });

  it("names the cause of each failure, and one list failing leaves the others alone", async () => {
    const { fn } = fakeFetch((url) => {
      if (hostOf(url) === "mctiers.gg") return reply(500, "boom");
      if (hostOf(url) === "pvptiers.com") return reply(200, "<html>captcha</html>");
      return liveLists(url);
    });
    const [profile] = await getProfiles([JOKING], makeDeps(fn));
    expect(profile?.lists.mctiers).toEqual({ status: "unavailable", reason: "http_500" });
    expect(profile?.lists.pvptiers).toEqual({ status: "unavailable", reason: "bad_json" });
    expect(profile?.lists.mcpvp.status).toBe("ok");
    expect(profile?.lists.subtiers.status).toBe("ok");
  });

  it("gives up on a list that hangs, as PVPHQ does", async () => {
    const hanging = ((url: string, init: RequestInit) => {
      if (hostOf(url) !== "pvphq.com") return Promise.resolve(new Response("", { status: 404 }));
      return new Promise((_, reject) => {
        init.signal?.addEventListener("abort", () => reject(init.signal?.reason));
      });
    }) as unknown as typeof fetch;
    const deps = makeDeps(hanging);
    deps.timeoutMs = 20;
    const [profile] = await getProfiles([JOKING], deps);
    expect(profile?.lists.pvphq).toEqual({ status: "unavailable", reason: "timeout" });
    expect(profile?.lists.mctiers).toEqual({ status: "unranked" });
  });

  it("still works without the name McPvP needs", async () => {
    const { fn, calls } = fakeFetch(allNotFound);
    const [profile] = await getProfiles([{ uuid: JOKING.uuid, name: null }], makeDeps(fn));
    expect(profile?.lists.mcpvp).toEqual({ status: "unavailable", reason: "name_required" });
    expect(calls.some((url) => url.includes("mcpvp"))).toBe(false);
  });
});

describe("caching", () => {
  async function lookup(clock: { now: number }, edge: FakeEdge, route: Parameters<typeof fakeFetch>[0]) {
    const fake = fakeFetch(route);
    const deps = makeDeps(fake.fn, { edge, clock });
    const [profile] = await getProfiles([JOKING], deps);
    await deps.settle();
    return { profile, calls: fake.calls };
  }

  const isList = (host: string) => (url: string) => hostOf(url) === host;

  it("stores one entry per player, not one per list", async () => {
    const edge = new FakeEdge();
    await lookup({ now: 1_000_000 }, edge, liveLists);
    expect(edge.stored.size).toBe(1);
    expect(edge.puts).toBe(1);
  });

  it("serves good results from cache for 30 minutes, then asks again", async () => {
    const edge = new FakeEdge();
    const clock = { now: 1_000_000 };
    await lookup(clock, edge, liveLists);

    clock.now += 29 * MINUTE;
    let later = await lookup(clock, edge, liveLists);
    expect(later.calls.some(isList("mctiers.gg"))).toBe(false);
    expect(later.profile?.lists.mctiers.status).toBe("ok");

    clock.now += 2 * MINUTE;
    later = await lookup(clock, edge, liveLists);
    expect(later.calls.some(isList("mctiers.gg"))).toBe(true);
  });

  it("remembers 'not ranked' for 2 hours", async () => {
    const edge = new FakeEdge();
    const clock = { now: 1_000_000 };
    await lookup(clock, edge, allNotFound);

    clock.now += 119 * MINUTE;
    expect((await lookup(clock, edge, allNotFound)).calls).toHaveLength(0);

    clock.now += 2 * MINUTE;
    expect((await lookup(clock, edge, allNotFound)).calls).toHaveLength(5);
  });

  it("retries a failed list after a minute without refetching the good ones", async () => {
    const edge = new FakeEdge();
    const clock = { now: 1_000_000 };
    await lookup(clock, edge, liveLists);

    clock.now += 61 * 1000;
    const again = await lookup(clock, edge, liveLists);
    expect(again.calls.map(hostOf)).toEqual(["pvphq.com"]);
  });

  it("finds entries in the edge cache after the Worker restarts and memory is gone", async () => {
    const edge = new FakeEdge();
    const clock = { now: 1_000_000 };
    await lookup(clock, edge, allNotFound);
    const reborn = await lookup(clock, edge, () => {
      throw new Error("should not be called");
    });
    expect(reborn.profile?.lists.mctiers).toEqual({ status: "unranked" });
    expect(edge.matches).toBe(2);
  });

  it("still caches with no edge cache at all, as may happen on workers.dev", async () => {
    const fake = fakeFetch(allNotFound);
    const deps = makeDeps(fake.fn, { edge: null });
    await getProfiles([JOKING], deps);
    await getProfiles([JOKING], deps);
    expect(fake.calls).toHaveLength(5);
  });

  it("carries on if the edge cache throws", async () => {
    const broken = {
      match: () => Promise.reject(new Error("cache down")),
      put: () => Promise.reject(new Error("cache down")),
    };
    const fake = fakeFetch(allNotFound);
    const deps = makeDeps(fake.fn, { edge: broken });
    const [profile] = await getProfiles([JOKING], deps);
    await deps.settle();
    expect(profile?.lists.mctiers).toEqual({ status: "unranked" });
  });
});

describe("circuit breaker", () => {
  it("stops calling a list after two failures, and tries again a minute later", async () => {
    const clock = { now: 1_000_000 };
    const fake = fakeFetch((url) => (hostOf(url) === "pvphq.com" ? new Error("hang") : reply(404)));
    const deps = makeDeps(fake.fn, { clock });
    const toPvphq = () => fake.calls.filter((url) => hostOf(url) === "pvphq.com").length;

    await getProfiles(randomPlayers(2), deps);
    expect(toPvphq()).toBe(2);

    const [later] = await getProfiles([JOKING], deps);
    expect(toPvphq()).toBe(2);
    expect(later?.lists.pvphq).toEqual({ status: "unavailable", reason: "circuit_open" });

    clock.now += 61_000;
    await getProfiles([JOKING], deps);
    expect(toPvphq()).toBe(3);
  });

  it("doesn't blame a list for a player with no name", async () => {
    const fake = fakeFetch(allNotFound);
    const deps = makeDeps(fake.fn);
    await getProfiles(randomPlayers(4).map((p) => ({ uuid: p.uuid, name: null })), deps);
    expect(deps.breaker.allows("mcpvp", 1_800_000_000_000)).toBe(true);
  });
});

describe("the 50 subrequest limit", () => {
  it("keeps a full batch inside the budget, cache calls included", async () => {
    const edge = new FakeEdge();
    const fake = fakeFetch(allNotFound);
    const deps = makeDeps(fake.fn, { edge });
    const profiles = await getProfiles(randomPlayers(8), deps);
    await deps.settle();

    expect(edge.matches + edge.puts + fake.calls.length).toBeLessThanOrEqual(SUBREQUEST_BUDGET);
    expect(SUBREQUEST_BUDGET).toBeLessThanOrEqual(50);
    expect(profiles).toHaveLength(8);
  });

  it("gives every player the four reliable lists and lets PVPHQ go short", async () => {
    const fake = fakeFetch(allNotFound);
    const deps = makeDeps(fake.fn, { edge: new FakeEdge() });
    const profiles = await getProfiles(randomPlayers(8), deps);

    for (const profile of profiles) {
      expect(profile.lists.mctiers.status).toBe("unranked");
      expect(profile.lists.mcpvp.status).toBe("unranked");
      expect(profile.lists.pvptiers.status).toBe("unranked");
      expect(profile.lists.subtiers.status).toBe("unranked");
    }
    expect(profiles.some((p) => p.lists.pvphq.status === "unavailable")).toBe(true);
  });

  it("doesn't cache 'busy', so the next request can fill the gap", async () => {
    const edge = new FakeEdge();
    const fake = fakeFetch(allNotFound);
    const deps = makeDeps(fake.fn, { edge });
    const players = randomPlayers(8);
    await getProfiles(players, deps);
    await deps.settle();

    fake.calls.length = 0;
    const second = await getProfiles(players, deps);
    expect(fake.calls.map(hostOf).every((host) => host === "pvphq.com")).toBe(true);
    expect(second.every((p) => p.lists.pvphq.status === "unranked")).toBe(true);
  });
});

describe("batches", () => {
  it("keeps two players apart in one request", async () => {
    const { fn } = fakeFetch((url) => (url.includes(ITZ.uuid) ? reply(200, fixture("mctiers-ranked.json")) : reply(404)));
    const [itz, joking] = await getProfiles([ITZ, JOKING], makeDeps(fn));
    expect(itz?.lists.mctiers.status).toBe("ok");
    expect(joking?.lists.mctiers.status).toBe("unranked");
  });
});
