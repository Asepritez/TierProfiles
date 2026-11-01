import type { ListId, ListResult, PlayerRef } from "../types";
import { plainUuid } from "../validate";
import { mcpvp } from "./mcpvp";
import type { Provider } from "./provider";
import { pvphq } from "./pvphq";
import { parseRankings } from "./rankings";

const mctiers: Provider = {
  id: "mctiers",
  url: (player: PlayerRef) => `https://mctiers.gg/api/profile/${player.uuid}`,
  parse: parseRankings,
};

const pvptiers: Provider = {
  id: "pvptiers",
  // This one answers 400 to a dashed UUID.
  url: (player: PlayerRef) => `https://pvptiers.com/api/profile/${plainUuid(player.uuid)}`,
  parse: parseRankings,
};

const subtiers: Provider = {
  id: "subtiers",
  url: (player: PlayerRef) => `https://subtiers.net/api/v2/profile/${player.uuid}`,
  parse: parseRankings,
};

/**
 * Order matters: when a request runs out of subrequests, the lists at the end
 * are the ones that go without. PVPHQ is last because it's the least reliable.
 */
export const PROVIDERS: readonly Provider[] = [mctiers, mcpvp, pvptiers, subtiers, pvphq];

export type { ListId, ListResult };
