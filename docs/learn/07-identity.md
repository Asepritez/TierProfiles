# 07: Proving who you are, without a login

Anyone can view cards, but only you should be able to change yours. The usual answer is "log in", which
means a website, a password or a Microsoft sign-in. This mod uses a trick Minecraft servers have used for
years instead.

## How a Minecraft server checks you

When you join an online server, your game says to Mojang: "I'm joining the server called X". That call
carries your access token, and it goes only to Mojang. The server then asks Mojang: "did this name just
join X?" Mojang answers yes, with your UUID. The server never sees your token.

## Our version

1. The mod asks our server for a **challenge**: a random value that serves as the "server name".
2. `MojangJoiner` makes the same `joinServer` call the game makes, with the challenge. This is the only
   code in the mod that touches your access token, and it hands it straight to the game's own session
   service, which talks only to Mojang.
3. Our server asks Mojang's `hasJoined` whether that name joined that challenge. Mojang replies with
   the UUID.
4. Our server gives back a **token** that's good for 15 minutes and only for that UUID.

`CosmeticsServiceTest.theAccessTokenNeverReachesOurServer` checks every request the mod sends to our
server and fails if the token appears anywhere in it.

## Signed, not stored

The server keeps no list of challenges or tokens. Each one carries its own expiry, plus a **signature**:
an HMAC, a fingerprint made from the contents and a secret only the server knows. Change one character,
such as the UUID or the expiry, and the fingerprint no longer matches. So the server can trust a token
without looking anything up, which suits the free plan.

The UUID a save writes to comes only from the signed token, never from the request body. There's a test
that tries to write someone else's card and checks it lands on the sender's own UUID instead.

## Only presets

A card style is a set of choices from fixed lists: background, border, armor and so on. The server
refuses anything off the list in `cosmetics.json`, and drops unknown fields. There's no free text, so
there's nothing to moderate. A mod test reads that same file and fails if the mod's lists ever drift
from the server's.

## What couldn't be tested

The real Mojang step needs a real account, which this project must never use. So tests use stand-ins:

- unit tests fake Mojang's answers, including "no", errors and garbage
- the end-to-end run uses `server/test/mock-mojang.mjs`, a tiny stand-in for Mojang's session server,
  plus a fake joiner in the mod

So this has been run through the real game, the real Worker and a real local database, but **never
against the real Mojang**. The first real sign-in should be watched by a person.
