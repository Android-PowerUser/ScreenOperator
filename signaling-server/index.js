const WebSocket = require("ws");

const PORT = process.env.PORT || 8080;
const wss = new WebSocket.Server({ port: PORT });

// Waiting operators (not currently paired)
const availableOperators = new Set();
// Active tasks waiting for an operator to claim
// Map<taskId, { requester: WebSocket, task: object }>
const pendingTasks = new Map();
// Active pairs (after claim)
// Map<WebSocket, WebSocket> — bidirectional mapping
const pairs = new Map();

let taskIdCounter = 1;

console.log(`Signaling server (task broker) running on port ${PORT}`);

wss.on("connection", (ws) => {
  let role = null; // "operator" or "requester"

  ws.on("message", (data) => {
    try {
      const msg = JSON.parse(data);

      // Operator registers as available
      if (msg.type === "register_operator") {
        role = "operator";
        availableOperators.add(ws);
        console.log(`Operator registered (${availableOperators.size} total)`);
        ws.send(JSON.stringify({ type: "registered", message: "Waiting for tasks..." }));
        // Send any pending tasks immediately
        pendingTasks.forEach((task, taskId) => {
          ws.send(JSON.stringify({
            type: "new_task",
            taskId: taskId,
            text: task.task.text,
            hasScreenshot: !!task.task.screenshot
          }));
        });
        return;
      }

      // ScreenOperator posts a new task
      if (msg.type === "post_task") {
        role = "requester";
        const taskId = "task_" + (taskIdCounter++);
        pendingTasks.set(taskId, { requester: ws, task: msg });
        console.log(`Task posted: ${taskId} (${availableOperators.size} operators available)`);

        // Broadcast to all available operators
        availableOperators.forEach((op) => {
          if (op.readyState === WebSocket.OPEN) {
            op.send(JSON.stringify({
              type: "new_task",
              taskId: taskId,
              text: msg.text || "",
              hasScreenshot: !!msg.screenshot
            }));
          }
        });

        // Tell requester how many operators are available
        ws.send(JSON.stringify({
          type: "task_posted",
          taskId: taskId,
          operatorsAvailable: availableOperators.size
        }));
        return;
      }

      // Operator claims a task
      if (msg.type === "claim" && msg.taskId) {
        const task = pendingTasks.get(msg.taskId);
        if (!task) {
          ws.send(JSON.stringify({ type: "claim_failed", reason: "Task already claimed or expired" }));
          return;
        }
        // Pair them
        const requester = task.requester;
        pendingTasks.delete(msg.taskId);
        availableOperators.delete(ws);
        pairs.set(ws, requester);
        pairs.set(requester, ws);

        console.log(`Task ${msg.taskId} claimed. Pair established.`);

        // Notify the claiming operator
        ws.send(JSON.stringify({ type: "claimed", taskId: msg.taskId }));
        // Notify the requester
        requester.send(JSON.stringify({ type: "task_claimed", taskId: msg.taskId }));

        // Notify all other operators that the task is gone
        availableOperators.forEach((op) => {
          if (op.readyState === WebSocket.OPEN) {
            op.send(JSON.stringify({ type: "task_taken", taskId: msg.taskId }));
          }
        });
        return;
      }

      // Forward WebRTC signaling between paired peers
      const peer = pairs.get(ws);
      if (peer && peer.readyState === WebSocket.OPEN) {
        peer.send(JSON.stringify(msg));
      }

    } catch (e) {
      console.error("Failed to process message:", e.message);
    }
  });

  ws.on("close", () => {
    availableOperators.delete(ws);
    // Clean up any pending tasks from this requester
    pendingTasks.forEach((task, taskId) => {
      if (task.requester === ws) {
        pendingTasks.delete(taskId);
        // Notify operators task is gone
        availableOperators.forEach((op) => {
          if (op.readyState === WebSocket.OPEN) {
            op.send(JSON.stringify({ type: "task_taken", taskId: taskId }));
          }
        });
      }
    });
    // Clean up pair
    const peer = pairs.get(ws);
    if (peer) {
      pairs.delete(peer);
      pairs.delete(ws);
      if (peer.readyState === WebSocket.OPEN) {
        peer.send(JSON.stringify({ type: "peer_disconnected" }));
      }
    }
    console.log(`Client disconnected (${availableOperators.size} operators remaining)`);
  });
});
