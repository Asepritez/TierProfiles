import { normalizeName, normalizeUuid } from "./validate";

// Proving who someone is without a login screen, the same way a Minecraft server does:
//   1. We hand out a random challenge.
//   2. The game tells Mojang "I'm joining the server called <challenge>". That call
//      carries the player's access token, and it goes only to Mojang.
//   3. We ask Mojang whether that name really did that. Mojang answers with their UUID.
//   4. We give back a short-lived token that lets them save that one UUID's card.
//
// Challenges and tokens are signed rather than stored, so this needs no database.

const CHALLENGE_SECONDS = 120;
const TOKEN_SECONDS = 15 * 60;
const MOJANG_TIMEOUT_MS = 5000;
export const MOJANG_SESSION = "https://sessionserver.mojang.com";

async function hmac(secret: string, text: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(text));
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** Compares without leaking how many characters matched. */
function sameText(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i += 1) difference |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return difference === 0;
}

function randomHex(bytes: number): string {
  return [...crypto.getRandomValues(new Uint8Array(bytes))].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/**
 * A challenge is <random>.<expiry>.<signature>. The game passes the signature-free part
 * to Mojang as the server id, so that part has to look like an ordinary one.
 */
export async function createChallenge(secret: string, nowSeconds: number): Promise<{ challenge: string; serverId: string; expires: number }> {
  const expires = nowSeconds + CHALLENGE_SECONDS;
  const serverId = randomHex(16);
  const body = `${serverId}.${expires}`;
  return { challenge: `${body}.${await hmac(secret, `challenge:${body}`)}`, serverId, expires };
}

async function readChallenge(secret: string, challenge: string, nowSeconds: number): Promise<string | null> {
  const parts = challenge.split(".");
  if (parts.length !== 3) return null;
  const [serverId, expires, signature] = parts as [string, string, string];
  const expected = await hmac(secret, `challenge:${serverId}.${expires}`);
  if (!sameText(expected, signature) || Number(expires) < nowSeconds) return null;
  return serverId;
}

export interface Verified {
  uuid: string;
  name: string;
}

/** Asks Mojang who really joined. Returns null for anything but a clear yes. */
async function askMojang(fetcher: typeof fetch, base: string, name: string, serverId: string): Promise<Verified | null> {
  const url = `${base}/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`;
  let response: Response;
  try {
    response = await fetcher(url, { signal: AbortSignal.timeout(MOJANG_TIMEOUT_MS) });
  } catch {
    return null;
  }
  if (response.status !== 200) return null;

  try {
    const body = (await response.json()) as { id?: unknown; name?: unknown };
    const uuid = typeof body.id === "string" ? normalizeUuid(body.id) : null;
    const verifiedName = normalizeName(body.name);
    return uuid && verifiedName ? { uuid, name: verifiedName } : null;
  } catch {
    return null;
  }
}

export async function verifyJoin(
  secret: string,
  fetcher: typeof fetch,
  mojangBase: string,
  name: string,
  challenge: string,
  nowSeconds: number,
): Promise<{ token: string; uuid: string; expires: number } | null> {
  const serverId = await readChallenge(secret, challenge, nowSeconds);
  if (!serverId) return null;
  const verified = await askMojang(fetcher, mojangBase, name, serverId);
  if (!verified) return null;

  const expires = nowSeconds + TOKEN_SECONDS;
  const body = `${verified.uuid}.${expires}`;
  return { token: `${body}.${await hmac(secret, `token:${body}`)}`, uuid: verified.uuid, expires };
}

/** Returns the UUID a token may edit, or null. */
export async function readToken(secret: string, token: string, nowSeconds: number): Promise<string | null> {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [uuid, expires, signature] = parts as [string, string, string];
  const expected = await hmac(secret, `token:${uuid}.${expires}`);
  if (!sameText(expected, signature) || Number(expires) < nowSeconds) return null;
  return uuid;
}
