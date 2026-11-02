# TierProfiles

A client-side Fabric mod that shows Minecraft PvP players' tiers from every major tier list on one
profile, and lets you customize exactly what you see.

Early development. See [docs/SPEC.md](docs/SPEC.md) for the design.

Builds for Minecraft 1.21.11 and 26.3, with more versions on the way. See
[docs/PORTING.md](docs/PORTING.md).

Each jar is named `tierprofiles-<version>+mc<minecraft>.jar`. What changed in each version is in
[CHANGELOG.md](CHANGELOG.md).

## Privacy

Your login stays with Minecraft. We never see your account token.

Here is exactly what leaves your game:

- **Tier lookups.** The names and UUIDs of players in your tab list go straight to the five tier
  list websites (MCTiers, McPvP, PvPTiers, SubTiers and PVPHQ), the same way their own pages ask.
  If you switch Settings, More, Advanced, Look up from to Server, they go to the TierProfiles
  server instead, which asks the lists for you.
- **Rows with made-up IDs.** Some servers fill the tab list with real players' names under made-up
  IDs, such as a board of top players. For those rows, the name shown goes to McPvP's search to find
  the real player, and their tiers are looked up as above.
- **Card styles.** The same UUIDs go to the TierProfiles server, so you see the backgrounds and armor
  other players picked for their cards. Your own style, a small set of preset choices, is sent only
  when you change it in Settings. Turn this off on the welcome page or in Settings, More, Advanced,
  Card styles, and every card stays plain.
- **Proving it's you.** To save a card style, the game makes the same silent `joinServer` call to
  Mojang that it makes when you join an online server, and our server asks Mojang to confirm it. Your
  access token goes only to Mojang.

- **List logos.** Each list's own logo, and MCTiers' title badges, are downloaded once from that
  list's website and kept in your config folder. You can turn this off in Settings, More, Advanced,
  and the mod uses its own letter marks instead.

Answers are kept on your PC in `config/tierprofiles-cache.json` for up to 7 days, so players you've
seen before show their tiers at once. Turning lookups off deletes them.

We send nothing else: no chat, no server address, no position. As with any website, the list
websites and the TierProfiles server host (Cloudflare) see your IP address when the mod
connects to them.

Nothing is sent until you have seen the first-run notice, and you can turn lookups off at any time in
Settings, More. With lookups off, no data is sent and no tiers show.

## License

[MIT](LICENSE). UI approach inspired by [Tallium](https://github.com/5sig2/Tallium) by 5sig2.
