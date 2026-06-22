import { setTimeout as delay } from "node:timers/promises";

export async function connectCdp(url) {
  const socket = new WebSocket(url);
  await new Promise((resolve, reject) => {
    socket.addEventListener("open", resolve, { once: true });
    socket.addEventListener("error", reject, { once: true });
  });

  let nextId = 1;
  const pending = new Map();
  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    if (!message.id) return;
    const request = pending.get(message.id);
    if (!request) return;
    pending.delete(message.id);
    if (message.error) {
      request.reject(new Error(`${message.error.message}: ${message.error.data ?? ""}`.trim()));
    } else {
      request.resolve(message.result ?? {});
    }
  });

  return {
    close() {
      socket.close();
    },
    send(method, params = {}) {
      const id = nextId;
      nextId += 1;
      return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
        socket.send(JSON.stringify({ id, method, params }));
      });
    }
  };
}

export async function evaluate(cdp, expression) {
  const response = await cdp.send("Runtime.evaluate", {
    awaitPromise: true,
    expression,
    returnByValue: true,
    userGesture: true
  });
  if (response.exceptionDetails) {
    const exception = response.exceptionDetails.exception;
    const description = exception?.description ?? exception?.value ?? response.exceptionDetails.text;
    throw new Error(description ?? "Runtime.evaluate failed.");
  }
  return response.result?.value;
}

export async function installScript(cdp, source) {
  await cdp.send("Page.addScriptToEvaluateOnNewDocument", { source });
  await cdp.send("Runtime.evaluate", { awaitPromise: true, expression: source });
}

export async function waitForExpression(cdp, expression, timeoutMs = 10_000) {
  const deadline = Date.now() + timeoutMs;
  let lastError = null;
  while (Date.now() < deadline) {
    try {
      if (await evaluate(cdp, expression)) return;
    } catch (error) {
      lastError = error;
    }
    await delay(200);
  }
  throw new Error(`Timed out waiting for browser expression: ${expression}${lastError ? ` (${lastError.message})` : ""}`);
}
