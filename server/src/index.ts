import { CircuitBreaker } from "./breaker";
import { EdgeCache, ProfileCache } from "./cache";
import { Deps, getProfiles, MAX_BATCH } from "./profiles";
import { PlayerRef } from "./types";
import { normalizeName, normalizeUuid, toPlayerRef } from "./validate";

const MAX_BODY_BYTES = 4096;

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

export async function route(request: Request, ctx: ExecutionContext): Promise<Response> {
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

  return json({ error: "not found" }, 404);
}

export default {
  fetch(request: Request, _env: unknown, ctx: ExecutionContext): Promise<Response> {
    return route(request, ctx);
  },
};
