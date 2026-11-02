# TierProfiles

A client-side Fabric mod that shows Minecraft PvP players' tiers from every major tier list on one
profile, and lets you customize exactly what you see.

Early development. See [docs/SPEC.md](docs/SPEC.md) for the design.

Builds for Minecraft 1.21.11 and 26.3, with more versions on the way. See
[docs/PORTING.md](docs/PORTING.md).

## Privacy

Your login stays with Minecraft. We never see your account token.

Here is exactly what leaves your game:

- **Tier lookups.** The names and UUIDs of players in your tab list go straight to the five tier
  list websites (MCTiers, McPvP, PvPTiers, SubTiers and PVPHQ), the same way their own pages ask.
  If you switch Settings, More, Advanced, Look up from to Server, they go to the TierProfiles
  server instead, which asks the lists for you.
- **Your card style.** Only with Look up from set to Server, and only when you save it. It is a
  small set of preset choices, stored on the TierProfiles server so that other players see it on
  your card. In the default mode your card style never leaves your game.
- **Proving it's you.** To save a card style, the game makes the same silent `joinServer` call to
  Mojang that it makes when you join an online server, and our server asks Mojang to confirm it. Your
  access token goes only to Mojang.

- **List logos.** Each list's own logo, and MCTiers' title badges, are downloaded once from that
  list's website and kept in your config folder. You can turn this off in Settings, More, Advanced,
  and the mod uses its own letter marks instead.

We send nothing else: no chat, no server address, no position. As with any website, the list
websites, and with Server mode the server host (Cloudflare), see your IP address when the mod
connects to them.

Nothing is sent until you have seen the first-run notice, and you can turn lookups off at any time in
Settings, More. With lookups off, no data is sent and no tiers show.

## License

[MIT](LICENSE). UI approach inspired by [Tallium](https://github.com/5sig2/Tallium) by 5sig2.
