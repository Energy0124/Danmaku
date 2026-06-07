import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { createDandanplaySignature, handleRequest, type Env } from "../src/index.js";

const env: Env = {
  DANDANPLAY_APP_ID: "test-app",
  DANDANPLAY_APP_SECRET: "test-secret",
  DANDANPLAY_BASE_URL: "https://api.example.test",
};

describe("dandanplay worker proxy", () => {
  it("signs comment requests and forwards the related query", async () => {
    let upstreamRequest: Request | undefined;
    const response = await handleRequest(
      new Request("https://proxy.example.test/api/v2/comment/123450001?withRelated=true"),
      env,
      async (input: Parameters<typeof fetch>[0], init?: Parameters<typeof fetch>[1]) => {
        upstreamRequest = new Request(input, init);
        return Response.json({ success: true, comments: [] });
      },
    );

    assert.equal(response.status, 200);
    assert.ok(upstreamRequest);
    const upstreamUrl = new URL(upstreamRequest!.url);
    assert.equal(upstreamUrl.toString(), "https://api.example.test/api/v2/comment/123450001?withRelated=true");
    assert.equal(upstreamRequest!.headers.get("X-AppId"), "test-app");
    assert.equal(upstreamRequest!.headers.get("X-AppSecret"), null);
    const timestamp = Number(upstreamRequest!.headers.get("X-Timestamp"));
    assert.equal(
      upstreamRequest!.headers.get("X-Signature"),
      await createDandanplaySignature("test-app", timestamp, "/api/v2/comment/123450001", "test-secret"),
    );
  });

  it("validates and forwards match requests", async () => {
    let upstreamBody = "";
    const response = await handleRequest(
      new Request("https://proxy.example.test/api/v2/match", {
        method: "POST",
        body: JSON.stringify({
          fileName: "Example S01E01.mkv",
          fileHash: "658d05841b9476ccc7420b3f0bb21c3b",
          fileSize: 123456,
          matchMode: "hashAndFileName",
        }),
      }),
      env,
      async (input: Parameters<typeof fetch>[0], init?: Parameters<typeof fetch>[1]) => {
        upstreamBody = await new Request(input, init).text();
        return Response.json({ success: true, matches: [] });
      },
    );

    assert.equal(response.status, 200);
    assert.deepEqual(JSON.parse(upstreamBody), {
      fileName: "Example S01E01.mkv",
      fileHash: "658d05841b9476ccc7420b3f0bb21c3b",
      fileSize: 123456,
      matchMode: "hashAndFileName",
    });
  });

  it("rejects missing credentials and invalid proxy input", async () => {
    const missingCredentials = await handleRequest(
      new Request("https://proxy.example.test/api/v2/comment/123450001"),
      {},
      async () => Response.json({ success: true }),
    );
    assert.equal(missingCredentials.status, 503);

    const badMatch = await handleRequest(
      new Request("https://proxy.example.test/api/v2/match", {
        method: "POST",
        body: JSON.stringify({ fileName: "bad.mkv", fileHash: "bad", fileSize: 1 }),
      }),
      env,
      async () => Response.json({ success: true }),
    );
    assert.equal(badMatch.status, 400);
  });
});
