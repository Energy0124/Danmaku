import { existsSync } from "node:fs";
import { mkdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { connectCdp, evaluate, installScript, waitForExpression } from "./browserQa/cdpClient.mjs";
import { findBrowserExecutable, launchChromium, waitForPageTarget } from "./browserQa/chromium.mjs";

const args = parseArgs(process.argv.slice(2));
const baseUrl = requireArg(args, "base-url").replace(/\/+$/, "");
const token = requireArg(args, "token");
const browserPath = args.browser ?? findBrowserExecutable();
const outputDir = path.resolve(args["output-dir"] ?? path.join("build", "qa", "headless-web-ui"));
const reportPath = path.join(outputDir, "browser-interaction-qa.md");
const overlayScreenshotPath = path.join(outputDir, "web-overlay-preferences.png");
const providerScreenshotPath = path.join(outputDir, "web-accounts-tracking.png");

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

const browserSession = await launchChromium({
  browserPath,
  startUrl: `${baseUrl}/web/`,
  userDataDir
});

try {
  const target = await waitForPageTarget(browserSession.cdpPort);
  const cdp = await connectCdp(target.webSocketDebuggerUrl);
  try {
    await cdp.send("Page.enable");
    await cdp.send("Runtime.enable");
    await installQaFetchOverrides(cdp);
    await cdp.send("Page.navigate", { url: `${baseUrl}/web/` });
    await waitForExpression(cdp, "document.readyState === 'complete'");
    await installQaFetchOverrides(cdp);

    await connectWebUi(cdp);
    await verifyOverlayPreferences(cdp);
    await verifyInvalidOverlayStorageFallback(cdp);
    await verifyAccountsAndTrackingControls(cdp);

    await capturePng(cdp, providerScreenshotPath);
    await writeReport();
    console.log(`Browser interaction QA complete. Report: ${reportPath}`);
  } finally {
    cdp.close();
  }
} finally {
  await browserSession.stop();
}

async function connectWebUi(cdp) {
  await reloadAndConnect(cdp);
}

async function reloadAndConnect(cdp) {
  await evaluate(cdp, "location.reload();");
  await waitForExpression(cdp, "document.readyState === 'complete'");
  await waitForExpression(cdp, "Boolean(document.querySelector('form.connection-form button'))");
  await evaluate(cdp, `(() => {
    const input = document.querySelector('form.connection-form input[type="password"]');
    const descriptor = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
    descriptor.set.call(input, ${json(token)});
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    document.querySelector('form.connection-form button').click();
  })()`);
  await waitForExpression(cdp, "Boolean(document.querySelector('.player-panel .danmaku-controls'))", 15_000);
  await waitForExpression(
    cdp,
    "document.querySelector('.provider-settings-shell .admin-state')?.textContent.includes('Authorized')",
    15_000
  );
}

async function verifyOverlayPreferences(cdp) {
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

  await reloadAndConnect(cdp);
  await waitForExpression(cdp, overlayControlsMatchExpression({
    density: "dense",
    enabled: false,
    offsetSeconds: "2.5"
  }));
  await capturePng(cdp, overlayScreenshotPath);
}

async function verifyInvalidOverlayStorageFallback(cdp) {
  await evaluate(cdp, "localStorage.setItem('danmaku.web.danmakuOverlay', '{bad-json');");
  await reloadAndConnect(cdp);
  await waitForExpression(cdp, overlayControlsMatchExpression({
    density: "normal",
    enabled: true,
    offsetSeconds: "0"
  }));
}

async function verifyAccountsAndTrackingControls(cdp) {
  await waitForExpression(cdp, "document.body.textContent.includes('Connected as qa-mal')");
  await waitForExpression(cdp, "document.body.textContent.includes('Bangumi') && document.body.textContent.includes('Not connected')");
  await evaluate(cdp, `(() => {
    ${providerDomHelpers()}
    const controls = getAccountControls();
    setValue(controls.token, 'qa-bangumi-token');
    controls.connect.click();
    return true;
  })()`);
  await waitForExpression(cdp, "document.body.textContent.includes('Bangumi connected.') && document.body.textContent.includes('Connected as qa-bangumi')");

  await evaluate(cdp, `(() => {
    const details = document.querySelector('.tracking-admin-shell:not(.provider-accounts-shell) details');
    if (!details) throw new Error('Tracking administration was not found.');
    details.open = true;
    details.dispatchEvent(new Event('toggle'));
    return true;
  })()`);
  await waitForExpression(cdp, "Boolean(document.querySelector('.tracking-conflicts button'))");
  await evaluate(cdp, "document.querySelector('.tracking-conflicts button').click();");
  await waitForExpression(cdp, "document.body.textContent.includes('2 local episode(s) marked watched.')");

  await evaluate(cdp, `(() => {
    ${providerDomHelpers()}
    const controls = getTrackingSearchControls();
    setValue(controls.title, 'Frieren');
    controls.search.click();
    return true;
  })()`);
  await waitForExpression(cdp, "Boolean(document.querySelector('.provider-search-results button'))");
  await evaluate(cdp, "document.querySelector('.provider-search-results button').click();");
  await waitForExpression(cdp, "document.body.textContent.includes('Series mapping saved.') && document.body.textContent.includes('1 ready')");

  await evaluate(cdp, `(() => {
    ${providerDomHelpers()}
    const controls = getSyncControls();
    if (!controls.sync.disabled) throw new Error('Sync must stay disabled before acknowledgement.');
    controls.review.click();
    return true;
  })()`);
  await waitForExpression(cdp, `(() => {
    ${providerDomHelpers()}
    return !getSyncControls().sync.disabled;
  })()`);
  await evaluate(cdp, `(() => {
    ${providerDomHelpers()}
    getSyncControls().sync.click();
    return true;
  })()`);
  await waitForExpression(cdp, "document.body.textContent.includes('Sync complete: 1 succeeded')");
}

async function installQaFetchOverrides(cdp) {
  const source = `(() => {
      const originalFetch = window.fetch.bind(window);
      const jsonResponse = (body, init = {}) => new Response(JSON.stringify(body), {
        status: init.status ?? 200,
        headers: {
          "Content-Type": "application/json; charset=utf-8",
          ...(init.headers ?? {})
        }
      });
      const accountDocument = (bangumiConnected = false) => ({
        myAnimeList: {
          state: "CONNECTED",
          userId: "1001",
          displayName: "qa-mal",
          lastVerifiedAtEpochMs: 1234567890
        },
        bangumi: bangumiConnected
          ? {
              state: "CONNECTED",
              userId: "2002",
              displayName: "qa-bangumi",
              lastVerifiedAtEpochMs: 1234567890
            }
          : { state: "DISCONNECTED" },
        bangumiTokenUrl: "https://next.bgm.tv/demo/access-token"
      });
      const trackingDocument = ({ conflict = false, update = false } = {}) => {
        const animeId = { provider: "MY_ANIME_LIST", value: 52991 };
        const mapping = {
          localSeriesId: "series-frieren",
          animeId,
          source: "MANUAL",
          confidence: 1,
          mappedAtEpochMs: 1234567890
        };
        const updateCandidate = {
          localSeriesId: "series-frieren",
          localSeriesIds: ["series-frieren"],
          seriesTitle: "Frieren",
          episodeCount: 28,
          mapping,
          update: {
            animeId,
            status: "WATCHING",
            watchedEpisodes: 5,
            trackingEnabled: true,
            ratingEnabled: false
          }
        };
        return {
          generatedAtEpochMs: 1234567890,
          series: [{
            id: "series-frieren",
            title: "Frieren",
            localSeriesIds: ["series-frieren"],
            localSeriesTitles: ["Frieren"],
            episodeCount: 28,
            mappings: [mapping]
          }],
          mappings: [mapping],
          listEntries: conflict ? [{
            animeId,
            status: "WATCHING",
            watchedEpisodes: 5,
            score: 8,
            updatedAtEpochMs: 1234567890
          }] : [],
          plan: {
            summary: {
              updateCount: update ? 1 : 0,
              skippedCount: 0,
              conflictCount: conflict ? 1 : 0,
              failureCount: 0,
              myAnimeListUpdateCount: update ? 1 : 0,
              bangumiUpdateCount: 0
            },
            updates: update ? [updateCandidate] : [],
            skipped: [],
            conflicts: conflict ? [{
              ...updateCandidate,
              localUpdate: {
                animeId,
                status: "WATCHING",
                watchedEpisodes: 3,
                trackingEnabled: true,
                ratingEnabled: false
              },
              externalEntry: {
                animeId,
                status: "WATCHING",
                watchedEpisodes: 5,
                score: 8,
                updatedAtEpochMs: 1234567890
              },
              reason: "EXTERNAL_PROGRESS_AHEAD"
            }] : [],
            mappingConflicts: [],
            failures: []
          }
        };
      };

      window.fetch = async (input, init = {}) => {
        const rawUrl = typeof input === "string" ? input : input.url;
        const url = new URL(rawUrl, window.location.href);

        if (url.pathname === "/api/providers/settings") {
          const authorization = new Headers(init.headers).get("Authorization");
          if (!authorization?.startsWith("Bearer ")) {
            return jsonResponse({}, { status: 401 });
          }
          return jsonResponse({
            settings: {
              dandanplay: {
                baseUrl: "https://api.dandanplay.net",
                appId: null,
                hasAppSecret: false,
                authenticationMode: "SIGNED",
                cacheMaxAgeDays: 30
              },
              externalAnime: {
                myAnimeListClientId: "qa-client",
                hasMyAnimeListClientSecret: false,
                hasMyAnimeListAccessToken: true,
                bangumiBaseUrl: "https://api.bgm.tv/",
                bangumiUserAgent: "Danmaku Browser QA/1.0",
                hasBangumiAccessToken: true
              }
            },
            runtime: {
              dandanplay: {
                matchAvailable: false,
                commentFetchAvailable: false,
                authenticated: false,
                reasonCode: "qa-missing-credentials"
              },
              myAnimeList: {
                searchAvailable: true,
                listReadAvailable: true,
                listWriteAvailable: true,
                authenticated: true,
                reasonCode: "qa-ready"
              },
              bangumi: {
                searchAvailable: true,
                listReadAvailable: true,
                listWriteAvailable: true,
                authenticated: true,
                reasonCode: "qa-ready"
              }
            }
          });
        }

        if (url.pathname === "/api/providers/runtime") {
          return jsonResponse({
            dandanplay: {
              matchAvailable: false,
              commentFetchAvailable: false,
              authenticated: false,
              reasonCode: "qa-missing-credentials"
            },
            myAnimeList: {
              searchAvailable: true,
              listReadAvailable: true,
              listWriteAvailable: true,
              authenticated: true,
              reasonCode: "qa-ready"
            },
            bangumi: {
              searchAvailable: true,
              listReadAvailable: true,
              listWriteAvailable: true,
              authenticated: true,
              reasonCode: "qa-ready"
            }
          });
        }

        if (url.pathname === "/api/providers/search") {
          const providerParam = url.searchParams.get("providers") ?? "MY_ANIME_LIST";
          const provider = providerParam.includes("BANGUMI") ? "BANGUMI" : "MY_ANIME_LIST";
          return jsonResponse([
            {
              anime: {
                id: { provider, value: provider === "BANGUMI" ? 400602 : 52991 },
                titles: {
                  primary: "Frieren: Beyond Journey's End",
                  chinese: "葬送的芙莉莲",
                  english: "Frieren: Beyond Journey's End",
                  japanese: "葬送のフリーレン",
                  alternateNames: ["Sousou no Frieren"]
                },
                episodeCount: 28,
                startYear: 2023,
                imageUrl: null,
                summary: "QA provider search result",
                externalLinks: []
              },
              confidence: 0.98,
              matchedTitle: "Frieren",
              evidence: ["qa-provider-search"]
            }
          ]);
        }

        if (url.pathname === "/api/providers/accounts") {
          return jsonResponse(accountDocument(false));
        }

        if (url.pathname === "/api/providers/accounts/bangumi") {
          return jsonResponse(accountDocument(true));
        }

        if (url.pathname === "/api/providers/tracking") {
          return jsonResponse(trackingDocument({ conflict: true }));
        }

        if (url.pathname === "/api/providers/tracking/conflicts/import") {
          return jsonResponse({ importedCount: 2, document: trackingDocument() });
        }

        if (url.pathname === "/api/providers/tracking/mapping") {
          return jsonResponse(trackingDocument({ update: true }));
        }

        if (url.pathname === "/api/providers/tracking/sync") {
          return jsonResponse({
            document: trackingDocument(),
            successCount: 1,
            conflictCount: 0,
            missingCount: 0,
            errors: []
          });
        }

        return originalFetch(input, init);
      };
    })()`;
  await installScript(cdp, source);
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

function providerDomHelpers() {
  return `
    function labelControl(container, text, selector) {
      const labels = Array.from(container.querySelectorAll('label'));
      const label = labels.find((candidate) => candidate.textContent && candidate.textContent.includes(text));
      const control = label?.querySelector(selector);
      if (!control) throw new Error(text + ' control was not found.');
      return control;
    }
    function buttonFor(container, text) {
      const button = Array.from(container.querySelectorAll('button'))
        .find((candidate) => candidate.textContent && candidate.textContent.trim() === text);
      if (!button) throw new Error(text + ' button was not found.');
      return button;
    }
    function getAccountControls() {
      const panel = document.querySelector('.provider-accounts-shell');
      if (!panel) throw new Error('Account panel was not found.');
      const token = panel.querySelector('input[aria-label="Bangumi access token"]');
      if (!token) throw new Error('Bangumi token control was not found.');
      return { token, connect: buttonFor(panel, 'Connect Bangumi') };
    }
    function getTrackingSearchControls() {
      const form = document.querySelector('.tracking-mapping-form');
      if (!form) throw new Error('Tracking mapping form was not found.');
      const title = labelControl(form, 'Search title', 'input');
      return { title, search: buttonFor(form, 'Search provider') };
    }
    function getSyncControls() {
      const gate = document.querySelector('.tracking-sync-gate');
      if (!gate) throw new Error('Tracking sync gate was not found.');
      const review = gate.querySelector('input[type="checkbox"]');
      if (!review) throw new Error('Tracking review checkbox was not found.');
      return { review, sync: buttonFor(gate, 'Sync previewed updates') };
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

async function capturePng(cdp, filePath) {
  const screenshot = await cdp.send("Page.captureScreenshot", { format: "png", fromSurface: true });
  await writeFile(filePath, Buffer.from(screenshot.data, "base64"));
}

async function writeReport() {
  const report = [
    "# Browser Interaction QA",
    "",
    `- Base URL: ${baseUrl}`,
    `- Browser: ${browserPath}`,
    "- Overlay flow: change danmaku overlay controls, reload, verify persisted controls",
    "- Accounts flow: account status, guided Bangumi token validation, connected identity",
    "- Tracking flow: provider-ahead import, mapping search, reviewed sync gate",
    "- Invalid-storage fallback: PASS",
    `- Overlay screenshot: ${overlayScreenshotPath}`,
    `- Accounts/tracking screenshot: ${providerScreenshotPath}`,
    "",
    "Result: PASS"
  ].join("\n");
  await writeFile(reportPath, `${report}\n`, "utf8");
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


function json(value) {
  return JSON.stringify(value);
}
