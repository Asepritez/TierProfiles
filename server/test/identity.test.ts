import { describe, expect, it } from "vitest";
import { createChallenge, MOJANG_SESSION, readToken, verifyJoin } from "../src/identity";
import { fakeFetch, ITZ, reply } from "./helpers";

const SECRET = "test-secret";
const NOW = 1_800_000_000;
const MOJANG_ID = ITZ.uuid.replaceAll("-", "");

function mojangSaysYes() {
  return fakeFetch(() => reply(200, JSON.stringify({ id: MOJANG_ID, name: "ItzRealMe" })));
}

describe("the join check", () => {
  it("gives a token once Mojang confirms the join", async () => {
    const { challenge, serverId } = await createChallenge(SECRET, NOW);
    const fake = mojangSaysYes();
    const result = await verifyJoin(SECRET, fake.fn, MOJANG_SESSION, "ItzRealMe", challenge, NOW);

    expect(result?.uuid).toBe(ITZ.uuid);
    expect(fake.calls[0]).toBe(`${MOJANG_SESSION}/session/minecraft/hasJoined?username=ItzRealMe&serverId=${serverId}`);
  });

  it("takes the UUID from Mojang, never from the caller", async () => {
    const { challenge } = await createChallenge(SECRET, NOW);
    const result = await verifyJoin(SECRET, mojangSaysYes().fn, MOJANG_SESSION, "SomeoneElse", challenge, NOW);
    expect(result?.uuid).toBe(ITZ.uuid);
  });

  it("refuses when Mojang has no record of the join", async () => {
    const { challenge } = await createChallenge(SECRET, NOW);
    const fake = fakeFetch(() => reply(204));
    expect(await verifyJoin(SECRET, fake.fn, MOJANG_SESSION, "ItzRealMe", challenge, NOW)).toBeNull();
  });

  it.each([
    ["Mojang is down", () => new Error("no route")],
    ["Mojang errors", () => reply(500, "oops")],
    ["Mojang sends garbage", () => reply(200, "<html>")],
    ["Mojang sends a bad id", () => reply(200, `{"id":"nope","name":"x"}`)],
  ])("refuses when %s", async (_label, answer) => {
    const { challenge } = await createChallenge(SECRET, NOW);
    expect(await verifyJoin(SECRET, fakeFetch(answer).fn, MOJANG_SESSION, "ItzRealMe", challenge, NOW)).toBeNull();
  });

  it("refuses a challenge we didn't make", async () => {
    const forged = (await createChallenge("another-secret", NOW)).challenge;
    expect(await verifyJoin(SECRET, mojangSaysYes().fn, MOJANG_SESSION, "ItzRealMe", forged, NOW)).toBeNull();
  });

  it("refuses an old challenge", async () => {
    const { challenge, expires } = await createChallenge(SECRET, NOW);
    expect(await verifyJoin(SECRET, mojangSaysYes().fn, MOJANG_SESSION, "ItzRealMe", challenge, expires + 1)).toBeNull();
  });

  it("refuses a tampered challenge", async () => {
    const { challenge } = await createChallenge(SECRET, NOW);
    const [id, expires, signature] = challenge.split(".");
    const later = `${id}.${Number(expires) + 9999}.${signature}`;
    expect(await verifyJoin(SECRET, mojangSaysYes().fn, MOJANG_SESSION, "ItzRealMe", later, NOW)).toBeNull();
  });
});

describe("tokens", () => {
  async function token() {
    const { challenge } = await createChallenge(SECRET, NOW);
    return (await verifyJoin(SECRET, mojangSaysYes().fn, MOJANG_SESSION, "ItzRealMe", challenge, NOW))!;
  }

  it("names the verified player", async () => {
    const issued = await token();
    expect(await readToken(SECRET, issued.token, NOW)).toBe(ITZ.uuid);
  });

  it("expires", async () => {
    const issued = await token();
    expect(await readToken(SECRET, issued.token, issued.expires + 1)).toBeNull();
  });

  it("can't be edited to name someone else", async () => {
    const issued = await token();
    const forged = issued.token.replace(ITZ.uuid, "1246a0ec-662b-45de-bb0b-d68cb1cce800");
    expect(await readToken(SECRET, forged, NOW)).toBeNull();
  });

  it("can't be stretched", async () => {
    const issued = await token();
    const [uuid, expires, signature] = issued.token.split(".");
    expect(await readToken(SECRET, `${uuid}.${Number(expires) + 99999}.${signature}`, NOW)).toBeNull();
  });

  it("isn't interchangeable with a challenge", async () => {
    const { challenge } = await createChallenge(SECRET, NOW);
    expect(await readToken(SECRET, challenge, NOW)).toBeNull();
  });

  it("ignores junk", async () => {
    expect(await readToken(SECRET, "junk", NOW)).toBeNull();
  });
});
