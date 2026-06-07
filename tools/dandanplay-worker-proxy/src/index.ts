export interface Env {
  DANDANPLAY_APP_ID?: string;
  DANDANPLAY_APP_SECRET?: string;
  DANDANPLAY_BASE_URL?: string;
  DANDANPLAY_COMMENT_CACHE_TTL_SECONDS?: string;
}

type FetchLike = typeof fetch;

const DEFAULT_DANDANPLAY_BASE_URL = "https://api.dandanplay.net";
const MAX_MATCH_BODY_BYTES = 64 * 1024;
const MD5_HEX = /^[a-f0-9]{32}$/i;

export default {
  fetch(request: Request, env: Env): Promise<Response> {
    return handleRequest(request, env, fetch);
  },
};

export async function handleRequest(
  request: Request,
  env: Env,
  fetchImpl: FetchLike = fetch,
): Promise<Response> {
  if (request.method === "OPTIONS") {
    return withCors(new Response(null, { status: 204 }));
  }

  const url = new URL(request.url);
  if (url.pathname === "/health" && request.method === "GET") {
    return jsonResponse({ ok: true });
  }

  const credentials = loadCredentials(env);
  if (!credentials) {
    return jsonResponse({ success: false, message: "proxy credentials are not configured" }, 503);
  }

  if (url.pathname === "/api/v2/match" && request.method === "POST") {
    const body = await readMatchBody(request);
    if (!body.ok) return jsonResponse({ success: false, message: body.message }, 400);
    return proxyDandanplay({
      method: "POST",
      apiPath: "/api/v2/match",
      body: body.value,
      env,
      credentials,
      fetchImpl,
    });
  }

  const commentMatch = url.pathname.match(/^\/api\/v2\/comment\/(\d+)$/);
  if (commentMatch && request.method === "GET") {
    const episodeId = Number(commentMatch[1]);
    if (!Number.isSafeInteger(episodeId) || episodeId <= 0) {
      return jsonResponse({ success: false, message: "episodeId must be a positive integer" }, 400);
    }

    const withRelated = url.searchParams.get("withRelated");
    if (withRelated != null && withRelated !== "true" && withRelated !== "false") {
      return jsonResponse({ success: false, message: "withRelated must be true or false" }, 400);
    }

    return proxyDandanplay({
      method: "GET",
      apiPath: `/api/v2/comment/${episodeId}`,
      query: withRelated == null ? undefined : `withRelated=${withRelated}`,
      env,
      credentials,
      fetchImpl,
    });
  }

  return jsonResponse({ success: false, message: "route not found" }, 404);
}

async function proxyDandanplay(options: {
  method: "GET" | "POST";
  apiPath: string;
  query?: string;
  body?: string;
  env: Env;
  credentials: Required<Pick<Env, "DANDANPLAY_APP_ID" | "DANDANPLAY_APP_SECRET">>;
  fetchImpl: FetchLike;
}): Promise<Response> {
  const upstreamUrl = upstreamEndpoint(options.env, options.apiPath, options.query);
  const timestamp = Math.floor(Date.now() / 1000);
  const signature = await createDandanplaySignature(
    options.credentials.DANDANPLAY_APP_ID,
    timestamp,
    options.apiPath,
    options.credentials.DANDANPLAY_APP_SECRET,
  );

  const upstreamResponse = await options.fetchImpl(upstreamUrl, {
    method: options.method,
    body: options.body,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json; charset=utf-8",
      "X-AppId": options.credentials.DANDANPLAY_APP_ID,
      "X-Timestamp": timestamp.toString(),
      "X-Signature": signature,
    },
  });

  const responseHeaders = new Headers(upstreamResponse.headers);
  responseHeaders.set("Cache-Control", cacheControlHeader(options.env, options.method));
  responseHeaders.set("Content-Type", "application/json; charset=utf-8");
  return withCors(
    new Response(upstreamResponse.body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: responseHeaders,
    }),
  );
}

async function readMatchBody(request: Request): Promise<{ ok: true; value: string } | { ok: false; message: string }> {
  const body = await request.text();
  if (new TextEncoder().encode(body).byteLength > MAX_MATCH_BODY_BYTES) {
    return { ok: false, message: "match request body is too large" };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(body);
  } catch {
    return { ok: false, message: "match request body must be JSON" };
  }

  if (!isMatchRequest(parsed)) {
    return { ok: false, message: "match request is missing a valid fileName, fileHash, or fileSize" };
  }
  return { ok: true, value: JSON.stringify(parsed) };
}

function isMatchRequest(value: unknown): value is {
  fileName: string;
  fileHash: string;
  fileSize: number;
  matchMode?: string;
  videoDuration?: number;
} {
  if (value == null || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.fileName === "string" &&
    candidate.fileName.trim().length > 0 &&
    typeof candidate.fileHash === "string" &&
    MD5_HEX.test(candidate.fileHash) &&
    typeof candidate.fileSize === "number" &&
    Number.isSafeInteger(candidate.fileSize) &&
    candidate.fileSize >= 0 &&
    (candidate.videoDuration == null ||
      (typeof candidate.videoDuration === "number" &&
        Number.isFinite(candidate.videoDuration) &&
        candidate.videoDuration >= 0))
  );
}

function loadCredentials(
  env: Env,
): Required<Pick<Env, "DANDANPLAY_APP_ID" | "DANDANPLAY_APP_SECRET">> | null {
  const appId = env.DANDANPLAY_APP_ID?.trim();
  const appSecret = env.DANDANPLAY_APP_SECRET?.trim();
  if (!appId || !appSecret) return null;
  return {
    DANDANPLAY_APP_ID: appId,
    DANDANPLAY_APP_SECRET: appSecret,
  };
}

function upstreamEndpoint(env: Env, apiPath: string, query?: string): string {
  const baseUrl = new URL(env.DANDANPLAY_BASE_URL ?? DEFAULT_DANDANPLAY_BASE_URL);
  if (baseUrl.protocol !== "https:" && baseUrl.protocol !== "http:") {
    throw new Error("DANDANPLAY_BASE_URL must use http or https");
  }
  baseUrl.pathname = `${baseUrl.pathname.replace(/\/$/, "")}${apiPath}`;
  baseUrl.search = query ?? "";
  return baseUrl.toString();
}

export async function createDandanplaySignature(
  appId: string,
  timestamp: number,
  apiPath: string,
  appSecret: string,
): Promise<string> {
  const bytes = new TextEncoder().encode(`${appId}${timestamp}${apiPath.toLowerCase()}${appSecret}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return bytesToBase64(new Uint8Array(digest));
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

function cacheControlHeader(env: Env, method: "GET" | "POST"): string {
  if (method !== "GET") return "no-store";
  const ttl = Number(env.DANDANPLAY_COMMENT_CACHE_TTL_SECONDS ?? "86400");
  const safeTtl = Number.isSafeInteger(ttl) && ttl > 0 ? ttl : 86400;
  return `public, max-age=${safeTtl}`;
}

function jsonResponse(value: unknown, status = 200): Response {
  return withCors(
    new Response(JSON.stringify(value), {
      status,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    }),
  );
}

function withCors(response: Response): Response {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Content-Type");
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}
