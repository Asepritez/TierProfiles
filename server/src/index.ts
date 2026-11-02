import { CircuitBreaker } from "./breaker";
import { EdgeCache, ProfileCache } from "./cache";
import { CosmeticsStore, d1Store, SAVE_COOLDOWN_MS, validateCosmetics } from "./cosmetics";
import { createChallenge, MOJANG_SESSION, readToken, verifyJoin } from "./identity";
import { Deps, getProfiles, MAX_BATCH } from "./profiles";
import { PlayerRef } from "./types";
import { normalizeName, normalizeUuid, toPlayerRef } from "./validate";

const MAX_BODY_BYTES = 4096;

export interface Env {
  /** Signs challenges and tokens. Set with `wrangler secret put`, or .dev.vars locally. */
  AUTH_SECRET?: string;
  /** Only ever set locally, to point the join check at a stand-in for Mojang. */
  MOJANG_SESSION_URL?: string;
  DB?: D1Database;
}

// These live outside the handler on purpose: a Worker instance serves many
// requests, so module-level state is how anything survives between them.
let cache: ProfileCache | undefined;
const breaker = new CircuitBreaker();

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

function badRequest(error: string): Response {
  return json({ error }, 400);
}

function edgeCache(): EdgeCache | null {
  const shared = (globalThis as { caches?: { default?: EdgeCache } }).caches;
  return shared?.default ?? null;
}

function dependencies(ctx: ExecutionContext): Deps {
  cache ??= new ProfileCache(edgeCache());
  return {
    cache,
    breaker,
    fetch: (input, init) => fetch(input, init),
    now: () => Date.now(),
    defer: (work) => ctx.waitUntil(work),
  };
}

async function handleProfile(uuidPart: string, url: URL, ctx: ExecutionContext): Promise<Response> {
  const uuid = normalizeUuid(uuidPart);
  if (!uuid) return badRequest("uuid must be a version 4 UUID");

  const player: PlayerRef = { uuid, name: normalizeName(url.searchParams.get("name")) };
  const [profile] = await getProfiles([player], dependencies(ctx));
  return json(profile);
}

async function handleProfiles(request: Request, ctx: ExecutionContext): Promise<Response> {
  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) return json({ error: "body too large" }, 413);

  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    return badRequest("body must be JSON");
  }

  const list = (body as { players?: unknown } | null)?.players;
  if (!Array.isArray(list) || list.length === 0) return badRequest("players must be a non-empty array");
  if (list.length > MAX_BATCH) return badRequest(`at most ${MAX_BATCH} players per request`);

  const players: PlayerRef[] = [];
  const seen = new Set<string>();
  for (const item of list) {
    const value = item as { uuid?: unknown; name?: unknown } | null;
    const player = toPlayerRef(value?.uuid, value?.name);
    if (!player) return badRequest("every player needs a version 4 uuid");
    if (!seen.has(player.uuid)) {
      seen.add(player.uuid);
      players.push(player);
    }
  }

  return json({ profiles: await getProfiles(players, dependencies(ctx)) });
}

function seconds(): number {
  return Math.floor(Date.now() / 1000);
}

async function handleChallenge(env: Env): Promise<Response> {
  if (!env.AUTH_SECRET) return json({ error: "identity is not set up" }, 503);
  return json(await createChallenge(env.AUTH_SECRET, seconds()));
}

async function handleVerify(request: Request, env: Env): Promise<Response> {
  if (!env.AUTH_SECRET) return json({ error: "identity is not set up" }, 503);

  let body: { name?: unknown; challenge?: unknown } | null;
  try {
    body = JSON.parse(await request.text());
  } catch {
    return badRequest("body must be JSON");
  }
  const name = normalizeName(body?.name);
  if (!name || typeof body?.challenge !== "string") return badRequest("name and challenge are required");

  const result = await verifyJoin(env.AUTH_SECRET, (input, init) => fetch(input, init), env.MOJANG_SESSION_URL ?? MOJANG_SESSION, name, body.challenge, seconds());
  return result ? json(result) : json({ error: "Mojang did not confirm this join" }, 403);
}

async function handleGetCosmetics(uuidPart: string, store: CosmeticsStore | null): Promise<Response> {
  const uuid = normalizeUuid(uuidPart);
  if (!uuid) return badRequest("uuid must be a version 4 UUID");
  if (!store) return json({ error: "storage is not set up" }, 503);

  const row = await store.read(uuid);
  return json({ uuid, cosmetics: row ? JSON.parse(row.data) : null });
}

async function handlePutCosmetics(request: Request, env: Env, store: CosmeticsStore | null): Promise<Response> {
  if (!env.AUTH_SECRET || !store) return json({ error: "storage is not set up" }, 503);

  const header = request.headers.get("authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice("Bearer ".length) : "";
  // The UUID comes only from the signed token, so nobody can write anyone else's card.
  const uuid = await readToken(env.AUTH_SECRET, token, seconds());
  if (!uuid) return json({ error: "sign-in needed" }, 401);

  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) return json({ error: "body too large" }, 413);
  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    return badRequest("body must be JSON");
  }
  const cosmetics = validateCosmetics(body);
  if (!cosmetics) return badRequest("every choice must be one of the presets");

  const now = Date.now();
  const existing = await store.read(uuid);
  if (existing && now - existing.updatedAt < SAVE_COOLDOWN_MS) return json({ error: "saving too often" }, 429);

  await store.write(uuid, JSON.stringify(cosmetics), now);
  return json({ uuid, cosmetics });
}

export async function route(request: Request, ctx: ExecutionContext, env: Env = {}, store: CosmeticsStore | null = null): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "");

  if (path === "" && request.method === "GET") {
    return json({ name: "tierprofiles", version: "0.1.0", repo: "https://github.com/Asepritez/TierProfiles" });
  }

  const profileMatch = path.match(/^\/v1\/profile\/([^/]+)$/);
  if (profileMatch?.[1]) {
    if (request.method !== "GET") return json({ error: "method not allowed" }, 405);
    return handleProfile(profileMatch[1], url, ctx);
  }

  if (path === "/v1/profiles") {
    if (request.method !== "POST") return json({ error: "method not allowed" }, 405);
    return handleProfiles(request, ctx);
  }

  if (path === "/v1/auth/challenge") {
    return request.method === "POST" ? handleChallenge(env) : json({ error: "method not allowed" }, 405);
  }
  if (path === "/v1/auth/verify") {
    return request.method === "POST" ? handleVerify(request, env) : json({ error: "method not allowed" }, 405);
  }
  if (path === "/v1/cosmetics") {
    return request.method === "PUT" ? handlePutCosmetics(request, env, store) : json({ error: "method not allowed" }, 405);
  }
  const cosmeticsMatch = path.match(/^\/v1\/cosmetics\/([^/]+)$/);
  if (cosmeticsMatch?.[1]) {
    return request.method === "GET" ? handleGetCosmetics(cosmeticsMatch[1], store) : json({ error: "method not allowed" }, 405);
  }

  return json({ error: "not found" }, 404);
}

export default {
  fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    return route(request, ctx, env, env.DB ? d1Store(env.DB) : null);
  },
};
