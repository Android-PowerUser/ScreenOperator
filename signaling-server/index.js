const WebSocket = require("ws");

const PORT = process.env.PORT || 8080;
const wss = new WebSocket.Server({ port: PORT });
const rooms = new Map();

console.log(`Signaling server running on port ${PORT}`);

wss.on("connection", (ws) => {
  let currentRoom = null;

  ws.on("message", (data) => {
    try {
      const msg = JSON.parse(data);

      if (msg.type === "join") {
        currentRoom = msg.room;
        if (!rooms.has(currentRoom)) {
          rooms.set(currentRoom, new Set());
        }
        rooms.get(currentRoom).add(ws);
        console.log(`Client joined room: ${currentRoom} (${rooms.get(currentRoom).size} clients)`);

        // Notify others in room
        rooms.get(currentRoom).forEach((client) => {
          if (client !== ws && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify({ type: "peer_joined" }));
          }
        });
        return;
      }

      // Forward all other messages to peers in same room
      if (currentRoom && rooms.has(currentRoom)) {
        rooms.get(currentRoom).forEach((client) => {
          if (client !== ws && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify(msg));
          }
        });
      }
    } catch (e) {
      console.error("Failed to process message:", e.message);
    }
  });

  ws.on("close", () => {
    if (currentRoom && rooms.has(currentRoom)) {
      rooms.get(currentRoom).delete(ws);
      // Notify peers
      rooms.get(currentRoom).forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
          client.send(JSON.stringify({ type: "peer_left" }));
        }
      });
      if (rooms.get(currentRoom).size === 0) {
        rooms.delete(currentRoom);
      }
      console.log(`Client left room: ${currentRoom}`);
    }
  });
});
