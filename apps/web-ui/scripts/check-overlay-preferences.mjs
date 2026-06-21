import { spawn } from "node:child_process";
import { once } from "node:events";
import { existsSync } from "node:fs";
import { mkdir, rm, writeFile } from "node:fs/promises";
import net from "node:net";
import path from "node:path";
import { setTimeout as delay } from "node:timers/promises";

const args = parseArgs(process.argv.slice(2));
const baseUrl = requireArg(args, "base-url").replace(/\/+$/, "");
const token = requireArg(args, "token");
const browserPath = args.browser ?? findBrowserExecutable();
const outputDir = path.resolve(args["output-dir"] ?? path.join("build", "qa", "headless-web-ui"));
const reportPath = path.join(outputDir, "browser-interaction-qa.md");
const screenshotPath = path.join(outputDir, "web-overlay-preferences.png");

if (!browserPath || !existsSync(browserPath)) {
  throw new Error("Chrome or Edge was not found. Pass --browser with a Chromium executable path.");
}
if (typeof WebSocket === "undefined") {
  throw new Error("This QA script requires a Node runtime with a built-in WebSocket implementation.");
}

await mkdir(outputDir, { recursive: true });
const userDataDir = path.join(outputDir, "browser-profile");
await rm(userDataDir, { recursive: true, force: true });
await mkdir(userDataDir, { recursive: true });

const cdpPort = await getFreePort();
const browser = spawn(browserPath, [
  "--headless=new",
  "--disable-background-networking",
  "--disable-default-apps",
  "--disable-gpu",
  "--disable-sync",
  "--no-default-browser-check",
  "--no-first-run",
  `--remote-debugging-port=${cdpPort}`,
  `--user-data-dir=${userDataDir}`,
  "--window-size=1280,900",
  `${baseUrl}/web/`
], {
  stdio: ["ignore", "ignore", "pipe"]
});

let stderr = "";
browser.stderr?.on("data", (chunk) => {
  stderr += chunk.toString();
});

try {
  const target = await waitForPageTarget(cdpPort);
  const cdp = await connectCdp(target.webSocketDebuggerUrl);
  try {
    await cdp.send("Page.enable");
    await cdp.send("Runtime.enable");
    await cdp.send("Page.navigate", { url: `${baseUrl}/web/` });
    await waitForExpression(cdp, "document.readyState === 'complete'");

    await evaluate(cdp, `localStorage.setItem(${json(pairingStorageKey(baseUrl))}, ${json(token)});`);
    await evaluate(cdp, "location.reload();");
    await waitForExpression(cdp, "document.readyState === 'complete'");
    await waitForExpression(cdp, "Boolean(document.querySelector('form.connection-form button'))");
    await evaluate(cdp, "document.querySelector('form.connection-form button').click();");
    await waitForExpression(cdp, "Boolean(document.querySelector('.player-panel .danmaku-controls'))", 15_000);

    await setOverlayControls(cdp, {
      density: "dense",
      enabled: false,
      offsetSeconds: "2.5"
    });
    await waitForStoredPreferences(cdp, {
      density: "dense",
      enabled: false,
      offsetSeconds: "2.5"
    });

    await evaluate(cdp, "location.reload();");
    await waitForExpression(cdp, "document.readyState === 'complete'");
    await waitForExpression(cdp, "Boolean(document.querySelector('form.connection-form button'))");
    await evaluate(cdp, "document.querySelector('form.connection-form button').click();");
    await waitForExpression(cdp, "Boolean(document.querySelector('.player-panel .danmaku-controls'))", 15_000);
    await waitForExpression(cdp, overlayControlsMatchExpression({
      density: "dense",
      enabled: false,
      offsetSeconds: "2.5"
    }));

    const screenshot = await cdp.send("Page.captureScreenshot", { format: "png", fromSurface: true });
    await writeFile(screenshotPath, Buffer.from(screenshot.data, "base64"));

    await evaluate(cdp, "localStorage.setItem('danmaku.web.danmakuOverlay', '{bad-json'); location.reload();");
    await waitForExpression(cdp, "document.readyState === 'complete'");
    await waitForExpression(cdp, "Boolean(document.querySelector('form.connection-form button'))");
    await evaluate(cdp, "document.querySelector('form.connection-form button').click();");
    await waitForExpression(cdp, "Boolean(document.querySelector('.player-panel .danmaku-controls'))", 15_000);
    await waitForExpression(cdp, overlayControlsMatchExpression({
      density: "normal",
      enabled: true,
      offsetSeconds: "0"
    }));

    const report = [
      "# Browser Interaction QA",
      "",
      `- Base URL: ${baseUrl}`,
      `- Browser: ${browserPath}`,
      "- Flow: connect web UI, change danmaku overlay controls, reload, verify persisted controls",
      "- Invalid-storage fallback: PASS",
      `- Screenshot: ${screenshotPath}`,
      "",
      "Result: PASS"
    ].join("\n");
    await writeFile(reportPath, `${report}\n`, "utf8");
    console.log(`Browser interaction QA complete. Report: ${reportPath}`);
  } finally {
    cdp.close();
  }
} finally {
  if (browser.exitCode === null && browser.signalCode === null) {
    browser.kill();
    await Promise.race([once(browser, "exit"), delay(5000)]).catch(() => {});
  }
  if (browser.exitCode !== 0 && browser.exitCode !== null) {
    process.stderr.write(stderr);
  }
}

function parseArgs(rawArgs) {
  const result = {};
  for (let index = 0; index < rawArgs.length; index += 1) {
    const raw = rawArgs[index];
    if (!raw.startsWith("--")) continue;
    const [key, inlineValue] = raw.slice(2).split("=", 2);
    result[key] = inlineValue ?? rawArgs[index + 1];
    if (inlineValue == null) index += 1;
  }
  return result;
}

function requireArg(values, name) {
  const value = values[name];
  if (!value) throw new Error(`Missing required --${name} argument.`);
  return value;
}

function findBrowserExecutable() {
  const candidates = [
    process.env.CHROME_PATH,
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
  ].filter(Boolean);
  return candidates.find((candidate) => existsSync(candidate)) ?? null;
}

function getFreePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.on("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      server.close(() => {
        if (!address || typeof address === "string") {
          reject(new Error("Could not allocate a local TCP port."));
        } else {
          resolve(address.port);
        }
      });
    });
  });
}

async function waitForPageTarget(port) {
  const deadline = Date.now() + 15_000;
  let lastError = null;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json/list`);
      const targets = await response.json();
      const page = targets.find((target) => target.type === "page" && target.webSocketDebuggerUrl);
      if (page) return page;
    } catch (error) {
      lastError = error;
    }
    await delay(200);
  }
  throw new Error(`Timed out waiting for Chrome CDP target.${lastError ? ` Last error: ${lastError.message}` : ""}`);
}

async function connectCdp(url) {
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
      const payload = JSON.stringify({ id, method, params });
      return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
        socket.send(payload);
      });
    }
  };
}
async function evaluate(cdp, expression) {
  const response = await cdp.send("Runtime.evaluate", {
    awaitPromise: true,
    expression,
    returnByValue: true,
    userGesture: true
  });
  if (response.exceptionDetails) {
    throw new Error(response.exceptionDetails.text ?? "Runtime.evaluate failed.");
  }
  return response.result?.value;
}

async function waitForExpression(cdp, expression, timeoutMs = 10_000) {
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

async function setOverlayControls(cdp, preferences) {
  await evaluate(cdp, `(() => {
    ${browserDomHelpers()}
    const controls = getOverlayControls();
    setChecked(controls.enabled, ${preferences.enabled});
    setValue(controls.density, ${json(preferences.density)});
    setValue(controls.offset, ${json(preferences.offsetSeconds)});
    return true;
  })()`);
}

async function waitForStoredPreferences(cdp, preferences) {
  await waitForExpression(cdp, `(() => {
    const value = localStorage.getItem('danmaku.web.danmakuOverlay');
    if (!value) return false;
    const parsed = JSON.parse(value);
    return parsed.enabled === ${preferences.enabled}
      && parsed.density === ${json(preferences.density)}
      && parsed.offsetSeconds === ${json(preferences.offsetSeconds)};
  })()`);
}

function overlayControlsMatchExpression(preferences) {
  return `(() => {
    ${browserDomHelpers()}
    const controls = getOverlayControls();
    return controls.enabled.checked === ${preferences.enabled}
      && controls.density.value === ${json(preferences.density)}
      && controls.offset.value === ${json(preferences.offsetSeconds)};
  })()`;
}

function browserDomHelpers() {
  return `
    function getOverlayControls() {
      const labels = Array.from(document.querySelectorAll('.danmaku-controls label'));
      const labelFor = (text) => labels.find((label) => label.textContent && label.textContent.includes(text));
      const enabled = labelFor('Overlay')?.querySelector('input[type="checkbox"]');
      const density = labelFor('Density')?.querySelector('select');
      const offset = labelFor('Offset')?.querySelector('input');
      if (!enabled || !density || !offset) {
        throw new Error('Danmaku overlay controls were not found.');
      }
      return { enabled, density, offset };
    }
    function setChecked(input, checked) {
      if (input.checked !== checked) input.click();
    }
    function setValue(element, value) {
      const prototype = element instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
      const descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
      descriptor.set.call(element, value);
      element.dispatchEvent(new Event('input', { bubbles: true }));
      element.dispatchEvent(new Event('change', { bubbles: true }));
    }
  `;
}
function pairingStorageKey(url) {
  return `danmaku.web.pairing.${url.replace(/\/+$/, "")}`;
}

function json(value) {
  return JSON.stringify(value);
}
