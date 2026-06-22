import { spawn } from "node:child_process";
import { once } from "node:events";
import { existsSync } from "node:fs";
import net from "node:net";
import { setTimeout as delay } from "node:timers/promises";

export function findBrowserExecutable() {
  const candidates = [
    process.env.CHROME_PATH,
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
  ].filter(Boolean);
  return candidates.find((candidate) => existsSync(candidate)) ?? null;
}

export async function launchChromium({
  browserPath,
  cdpPort,
  startUrl,
  userDataDir,
  windowSize = "1280,900"
}) {
  const resolvedCdpPort = cdpPort ?? await getFreePort();
  const browser = spawn(browserPath, [
    "--headless=new",
    "--disable-background-networking",
    "--disable-default-apps",
    "--disable-gpu",
    "--disable-sync",
    "--no-default-browser-check",
    "--no-first-run",
    `--remote-debugging-port=${resolvedCdpPort}`,
    `--user-data-dir=${userDataDir}`,
    `--window-size=${windowSize}`,
    startUrl
  ], {
    stdio: ["ignore", "ignore", "pipe"]
  });

  let stderr = "";
  browser.stderr?.on("data", (chunk) => {
    stderr += chunk.toString();
  });

  return {
    browser,
    cdpPort: resolvedCdpPort,
    get stderr() {
      return stderr;
    },
    async stop() {
      if (browser.exitCode === null && browser.signalCode === null) {
        browser.kill();
        await Promise.race([once(browser, "exit"), delay(5000)]).catch(() => {});
      }
      if (browser.exitCode !== 0 && browser.exitCode !== null) {
        process.stderr.write(stderr);
      }
    }
  };
}

export async function waitForPageTarget(port) {
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
