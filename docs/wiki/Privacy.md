Here is exactly what leaves your game, and where it goes.

| What | Sent to | When |
|---|---|---|
| Names and UUIDs of players around you | The five tier list websites: MCTiers, McPvP, PvPTiers, SubTiers, PVPHQ | When a player shows up on screen or in the tab list |
| The name on a tab row with a made-up ID | McPvP's search | Some servers list real players under made-up IDs, such as a top players board. This finds the real player |
| What you type in Settings → Players | McPvP's search, and Mojang's name lookup | Only when you search, after you stop typing |
| The UUID of a player you pick in search | Mojang's profile lookup, for their skin | When their card shows and they aren't in your tab list |
| The same UUIDs | The TierProfiles server | To load the card styles those players picked |
| Your card style (a few preset choices) | The TierProfiles server | Only when you change it and press Apply |
| A request for each list's logo | That list's own website | Once, then it's kept in your config folder |

A UUID is a player's permanent Minecraft ID. It's public: anyone can look it up from a name.

**Your login stays with Minecraft.** To save your card style, your game makes the same sign-in check Minecraft makes when you join a server. Your account token only ever goes to Mojang. Neither TierProfiles nor its server sees it.

**Nothing else is sent:** no chat, no server address, no position, nothing about what you're doing.

As with any website, the list websites and the TierProfiles server's host (Cloudflare) see your IP address when the mod connects to them.

## What's kept on your PC

Answers are saved in `.minecraft/config/tierprofiles-cache.json` for up to 7 days, so players you've seen before show their tiers straight away after a server switch or a restart. Turning lookups off deletes them.

## You're in control

- **Nothing is sent before you answer the welcome page.**
- **Look up tiers online** (welcome page, or Settings → More): off means nothing is sent at all and no tiers show.
- **Card styles** (welcome page, or Settings → More → Advanced): off means nothing goes to the TierProfiles server for styles, and every card is plain.
- **Official list logos** (Settings → More → Advanced): off means no logos are downloaded.

## Being polite to the lists

Each list gets only a few calls at a time (six for MCTiers, fewer for the others), answers are reused for 10 minutes and kept for 7 days, and a list that keeps failing is left alone for a minute. That's fast enough to fill a lobby in seconds without flooding anyone.
