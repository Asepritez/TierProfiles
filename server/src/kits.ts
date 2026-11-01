// Lists disagree on gamemode names. The mod shouldn't have to know that MCTiers
// says "vanilla" where PvPTiers says "crystal" for the same kit, so we settle on
// one id here. Anything not listed passes through, with dashes turned to
// underscores, so a brand new kit still shows up without a server update.

const ALIASES: Record<string, string> = {
  // ItzRealMe's MCTiers "vanilla" and PvPTiers "crystal" share an attained
  // timestamp to the second, so these are one kit.
  vanilla: "crystal",
  nethop: "neth_pot",
  netherite_pot: "neth_pot",
  cart: "minecart",
  diamond_smp: "dia_smp",
};

export function canonicalKit(raw: string): string {
  const key = raw.trim().toLowerCase().replaceAll("-", "_").replaceAll(" ", "_");
  return ALIASES[key] ?? key;
}
