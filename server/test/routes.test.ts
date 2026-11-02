import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { route } from "../src/index";
import { normalizeName, normalizeUuid } from "../src/validate";
import { allNotFound, fakeFetch, ITZ, JOKING } from "./helpers";

const ctx = { waitUntil() {}, passThroughOnException() {} } as unknown as ExecutionContext;

function get(path: string): Promise<Response> {
  return route(new Request(`http://localhost${path}`), ctx);
}

function post(path: string, body: unknown): Promise<Response> {
  return route(
    new Request(`http://localhost${path}`, {
      method: "POST",
      body: typeof body === "string" ? body : JSON.stringify(body),
    }),
    ctx,
  );
}

describe("routes", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fakeFetch(allNotFound).fn);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns a profile for a dashed or plain UUID", async () => {
    for (const id of [ITZ.uuid, ITZ.uuid.replaceAll("-", "")]) {
      const response = await get(`/v1/profile/${id}?name=ItzRealMe`);
      expect(response.status).toBe(200);
      const body = (await response.json()) as { uuid: string; name: string; lists: Record<string, unknown> };
      expect(body.uuid).toBe(ITZ.uuid);
      expect(body.name).toBe("ItzRealMe");
      expect(Object.keys(body.lists)).toEqual(["mcpvp", "pvptiers", "subtiers", "pvphq"]);
    }
  });

  it("rejects anything that isn't a version 4 UUID", async () => {
    expect((await get("/v1/profile/banana")).status).toBe(400);
    // A Bedrock-style UUID, as seen in McPvP's results.
    expect((await get("/v1/profile/0000000000000000000901f1e3339ed4")).status).toBe(400);
  });

  it("merges a batch, dropping repeats", async () => {
    const response = await post("/v1/profiles", { players: [ITZ, JOKING, ITZ] });
    expect(response.status).toBe(200);
    const body = (await response.json()) as { profiles: unknown[] };
    expect(body.profiles).toHaveLength(2);
  });

  it.each([
    ["not json", "{nope"],
    ["no players", {}],
    ["empty players", { players: [] }],
    ["a bad uuid", { players: [{ uuid: "banana" }] }],
    ["nine players", { players: Array(9).fill(ITZ) }],
  ])("rejects %s", async (_label, body) => {
    expect((await post("/v1/profiles", body)).status).toBe(400);
  });

  it("refuses a huge body", async () => {
    expect((await post("/v1/profiles", "x".repeat(5000))).status).toBe(413);
  });

  it("answers wrong methods and unknown paths properly", async () => {
    expect((await post(`/v1/profile/${ITZ.uuid}`, {})).status).toBe(405);
    expect((await get("/v1/profiles")).status).toBe(405);
    expect((await get("/nope")).status).toBe(404);
    expect((await get("/")).status).toBe(200);
  });
});

describe("validation", () => {
  it("keeps only real Minecraft names", () => {
    expect(normalizeName("Dream_1")).toBe("Dream_1");
    expect(normalizeName(".BedrockGuy")).toBeNull();
    expect(normalizeName("a".repeat(17))).toBeNull();
    expect(normalizeName(42)).toBeNull();
  });

  it("checks the variant bits as well as the version", () => {
    expect(normalizeUuid("06ec3577-3299-45fa-7bdf-613b1f86c8ab")).toBeNull();
    expect(normalizeUuid("06EC3577-3299-45FA-BBDF-613B1F86C8AB")).toBe(ITZ.uuid);
  });
});
