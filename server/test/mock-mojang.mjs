// A stand-in for Mojang's session server, for testing the join flow locally without
// touching any real account. Start it, set MOJANG_SESSION_URL in .dev.vars to match, and
// it confirms every join as one fixed test player. It must never be deployed.
import { createServer } from "node:http";

const port = 8799;
const playerId = "06ec3577329945fabbdf613b1f86c8ab";

createServer((request, response) => {
  const url = new URL(request.url, `http://127.0.0.1:${port}`);
  if (url.pathname !== "/session/minecraft/hasJoined") {
    response.writeHead(404).end();
    return;
  }
  const name = url.searchParams.get("username");
  console.log(`hasJoined asked for ${name}`);
  if (name === "NeverJoined") {
    response.writeHead(204).end();
    return;
  }
  response.writeHead(200, { "content-type": "application/json" });
  response.end(JSON.stringify({ id: playerId, name }));
}).listen(port, "127.0.0.1", () => console.log(`mock Mojang on ${port}`));
