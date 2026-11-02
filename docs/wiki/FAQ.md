## Is this a cheat?

No. It only shows public data from the tier list websites, the same as their own pages. It doesn't change combat, movement or anything in the world, and servers can't tell anything is different.

## Does the server need it?

No. TierProfiles is client-side only. Install it in your own game and it works on any server.

## Someone's tier is missing. Why?

Check these in order:
1. **They're unranked** on that list. Their card says *Unranked*.
2. **You hid it:** check Settings → Lists for hidden lists and kits, and Settings → Tags → **Minimum tier**.
3. **Tag settings:** **Hide retired** or **Hide unranked** in Tags → Advanced can hide it.
4. **The list is down.** The mod leaves a failing list alone for a minute, then tries again.
5. **Lookups are off:** Settings → More → **Look up tiers online**.

## The tiers on the card don't match the tag

The tag picks one tier using your **Gamemode**, **Tier** and **Best from any list** settings (Settings → Tags). The card shows each list separately. Press `Right Shift` on the card to see everything.

## A list on the card says "Looking up..." or "No answer"

**Looking up...** means that list hasn't answered yet. Each list answers on its own, so the fast ones show first. **No answer** means the list couldn't be reached. It's asked again a little later. PVPHQ often refuses busy moments, so it's the one you'll see this on most.

## The arrow keys don't do anything

They only pick players while `Tab` is held. If another mod uses the same keys, change ours in Settings → Keys.

## The card covers something

Move it in Settings → Position, or make it smaller in Settings → Cards → Advanced → Size. It already shrinks by itself if it would reach the crosshair or run off the screen.

## My card style isn't showing for my friend

- Both of you need **Card styles** on (Settings → More → Advanced).
- Check the status at the top right of Settings → Profile says it saved.
- If your friend looked you up shortly before, it can take up to 10 minutes.

## The welcome page shows every time

Press **Sounds good**, or `Escape`, to answer it. If it still comes back, the mod can't save its settings file: check that `.minecraft/config` isn't read-only.

## How do I start fresh?

Settings → More → **Reset all settings**, then **Apply**. Your privacy answers are kept. To reset everything including those, delete `.minecraft/config/tierprofiles.json`.

## Where do I report a bug?

On the [issue tracker](https://github.com/Asepritez/TierProfiles/issues). Include your Minecraft version, the TierProfiles version (it's in the jar's file name), and what happened.
