import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import catalog from "../src/cosmetics.json";
import { CosmeticsStore, validateCosmetics } from "../src/cosmetics";
import { createChallenge, verifyJoin, MOJANG_SESSION } from "../src/identity";
import { Env, route } from "../src/index";
import { fakeFetch, ITZ, JOKING, reply } from "./helpers";

const look = {
  background: "moon",
  border: "shimmer",
  helmet: "netherite",
  chestplate: "netherite",
  leggings: "netherite",
  boots: "netherite",
  trimPattern: "rib",
  trimMaterial: "amethyst",
  effect: "sparkles",
  intensity: 70,
  accent: catalog.accents[0],
  featuredKit: "sword",
  featuredList: "mctiers",
};

describe("validation", () => {
  it("accepts every preset", () => {
    expect(validateCosmetics(look)).toEqual(look);
  });

  it("drops unknown keys, so nothing unexpected is stored", () => {
    expect(validateCosmetics({ ...look, nickname: "free text!" })).toEqual(look);
  });

  it.each([
    ["an unknown background", { background: "<script>" }],
    ["an unknown border", { border: "rainbow" }],
    ["unknown armor", { helmet: "unobtainium" }],
    ["an unknown trim", { trimPattern: "custom" }],
    ["an unknown material", { trimMaterial: "dirt" }],
    ["an unknown effect", { effect: "fireworks" }],
    ["too much intensity", { intensity: 101 }],
    ["a fractional intensity", { intensity: 1.5 }],
    ["a colour off the list", { accent: 0x123456 }],
    ["a bad kit", { featuredKit: "<b>x</b>" }],
    ["a bad list", { featuredList: "myserver" }],
    ["a missing field", { border: undefined }],
  ])("refuses %s", (_label, change) => {
    expect(validateCosmetics({ ...look, ...change })).toBeNull();
  });

  it("refuses things that aren't objects", () => {
    expect(validateCosmetics(null)).toBeNull();
    expect(validateCosmetics("moon")).toBeNull();
    expect(validateCosmetics([look])).toBeNull();
  });
});

class MemoryStore implements CosmeticsStore {
  rows = new Map<string, { data: string; updatedAt: number }>();
  async read(uuid: string) {
    return this.rows.get(uuid) ?? null;
  }
  async write(uuid: string, data: string, updatedAt: number) {
    this.rows.set(uuid, { data, updatedAt });
  }
}

describe("the cosmetics API", () => {
  const SECRET = "test-secret";
  const ctx = { waitUntil() {}, passThroughOnException() {} } as unknown as ExecutionContext;
  let store: MemoryStore;
  let env: Env;
  let now: number;

  beforeEach(() => {
    store = new MemoryStore();
    env = { AUTH_SECRET: SECRET };
    now = 1_800_000_000_000;
    vi.spyOn(Date, "now").mockImplementation(() => now);
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  async function tokenFor(uuid: string): Promise<string> {
    const { challenge } = await createChallenge(SECRET, now / 1000);
    const mojang = fakeFetch(() => reply(200, JSON.stringify({ id: uuid.replaceAll("-", ""), name: "Tester" })));
    return (await verifyJoin(SECRET, mojang.fn, MOJANG_SESSION, "Tester", challenge, now / 1000))!.token;
  }

  function put(body: unknown, token?: string): Promise<Response> {
    const headers: Record<string, string> = token ? { authorization: `Bearer ${token}` } : {};
    return route(new Request("http://localhost/v1/cosmetics", { method: "PUT", headers, body: JSON.stringify(body) }), ctx, env, store);
  }

  function get(uuid: string): Promise<Response> {
    return route(new Request(`http://localhost/v1/cosmetics/${uuid}`), ctx, env, store);
  }

  it("saves and shows a card style", async () => {
    expect((await put(look, await tokenFor(ITZ.uuid))).status).toBe(200);
    const response = await get(ITZ.uuid);
    expect(await response.json()).toEqual({ uuid: ITZ.uuid, cosmetics: look });
  });

  it("gives null for a player with no style", async () => {
    expect(await (await get(JOKING.uuid)).json()).toEqual({ uuid: JOKING.uuid, cosmetics: null });
  });

  it("refuses a save with no token", async () => {
    expect((await put(look)).status).toBe(401);
    expect(store.rows.size).toBe(0);
  });

  it("refuses a forged token", async () => {
    expect((await put(look, "1246a0ec.123.abc")).status).toBe(401);
  });

  it("writes only to the token's own UUID, whatever the body claims", async () => {
    await put({ ...look, uuid: JOKING.uuid }, await tokenFor(ITZ.uuid));
    expect([...store.rows.keys()]).toEqual([ITZ.uuid]);
  });

  it("refuses presets off the list", async () => {
    expect((await put({ ...look, background: "custom" }, await tokenFor(ITZ.uuid))).status).toBe(400);
    expect(store.rows.size).toBe(0);
  });

  it("limits how often one player can write", async () => {
    const token = await tokenFor(ITZ.uuid);
    expect((await put(look, token)).status).toBe(200);
    expect((await put(look, token)).status).toBe(429);
    now += 11_000;
    expect((await put(look, token)).status).toBe(200);
  });

  it("issues a challenge the mod can pass to Mojang", async () => {
    const response = await route(new Request("http://localhost/v1/auth/challenge", { method: "POST" }), ctx, env, store);
    const body = (await response.json()) as { serverId: string; challenge: string };
    expect(body.serverId).toMatch(/^[0-9a-f]{32}$/);
    expect(body.challenge.startsWith(body.serverId)).toBe(true);
  });

  it("verifies a join end to end", async () => {
    const challenge = (await (await route(new Request("http://localhost/v1/auth/challenge", { method: "POST" }), ctx, env, store)).json()) as { challenge: string };
    vi.stubGlobal("fetch", fakeFetch(() => reply(200, JSON.stringify({ id: ITZ.uuid.replaceAll("-", ""), name: "ItzRealMe" }))).fn);

    const response = await route(
      new Request("http://localhost/v1/auth/verify", { method: "POST", body: JSON.stringify({ name: "ItzRealMe", challenge: challenge.challenge }) }),
      ctx, env, store,
    );
    const body = (await response.json()) as { token: string; uuid: string };
    expect(response.status).toBe(200);
    expect(body.uuid).toBe(ITZ.uuid);
    expect((await put(look, body.token)).status).toBe(200);
  });

  it("says plainly when Mojang didn't confirm", async () => {
    vi.stubGlobal("fetch", fakeFetch(() => reply(204)).fn);
    const { challenge } = await createChallenge(SECRET, now / 1000);
    const response = await route(
      new Request("http://localhost/v1/auth/verify", { method: "POST", body: JSON.stringify({ name: "Offline", challenge }) }),
      ctx, env, store,
    );
    expect(response.status).toBe(403);
  });

  it("refuses to run identity with no secret configured", async () => {
    const response = await route(new Request("http://localhost/v1/auth/challenge", { method: "POST" }), ctx, {}, store);
    expect(response.status).toBe(503);
  });

  it("answers 503, not a crash, with no database", async () => {
    expect((await route(new Request(`http://localhost/v1/cosmetics/${ITZ.uuid}`), ctx, env, null)).status).toBe(503);
  });
});
