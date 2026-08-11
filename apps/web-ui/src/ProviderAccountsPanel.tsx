import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import {
  DanmakuApiError,
  ProviderAccountStatus,
  ProviderAccountsDocument,
  connectBangumiAccount,
  disconnectProviderAccount,
  fetchProviderAccounts
} from "./api";

export function ProviderAccountsPanel({
  baseUrl,
  token
}: {
  baseUrl: string;
  token: string;
}) {
  const [accounts, setAccounts] = useState<ProviderAccountsDocument | null>(null);
  const [bangumiToken, setBangumiToken] = useState("");
  const [message, setMessage] = useState("Enter the pairing token to manage accounts.");
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    setAccounts(null);
    if (!token.trim()) {
      setMessage("Enter the pairing token to manage accounts.");
      return;
    }
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [baseUrl, token]);

  async function load() {
    setIsBusy(true);
    setMessage("Loading account status...");
    try {
      setAccounts(await fetchProviderAccounts(baseUrl, token));
      setMessage("Account credentials stay encrypted on the server.");
    } catch (error) {
      setMessage(describeError(error));
    } finally {
      setIsBusy(false);
    }
  }

  async function connectBangumi() {
    if (!bangumiToken.trim()) return;
    setIsBusy(true);
    setMessage("Validating the Bangumi account...");
    try {
      setAccounts(await connectBangumiAccount(baseUrl, token, bangumiToken.trim()));
      setBangumiToken("");
      setMessage("Bangumi connected.");
    } catch (error) {
      setMessage(describeError(error));
    } finally {
      setIsBusy(false);
    }
  }

  async function disconnect(provider: "myanimelist" | "bangumi") {
    setIsBusy(true);
    setMessage("Disconnecting account...");
    try {
      setAccounts(await disconnectProviderAccount(baseUrl, token, provider));
      setMessage("Account disconnected. Series mappings and local progress were kept.");
    } catch (error) {
      setMessage(describeError(error));
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <section className="tracking-admin-shell provider-accounts-shell">
      <details open>
        <summary>
          <span>
            <strong>Accounts</strong>
            <small>Connect once; tokens are validated and stored by the server.</small>
          </span>
          <span className={accounts ? "admin-state ready" : "admin-state limited"}>
            {accounts ? "Ready" : "Locked"}
          </span>
        </summary>
        <div className="tracking-admin-content">
          <p className="tracking-admin-message">{message}</p>
          {accounts ? (
            <div className="tracking-mapping-list">
              <AccountRow
                account={accounts.myAnimeList}
                name="MyAnimeList"
                onDisconnect={() => void disconnect("myanimelist")}
              >
                <p>
                  Browser sign-in is available in the Windows app under Settings → Accounts &amp;
                  tracking. The app owns the secure loopback callback.
                </p>
              </AccountRow>
              <AccountRow
                account={accounts.bangumi}
                name="Bangumi"
                onDisconnect={() => void disconnect("bangumi")}
              >
                {accounts.bangumi.state !== "CONNECTED" ? (
                  <div className="tracking-actions">
                    <a href={accounts.bangumiTokenUrl} rel="noreferrer" target="_blank">
                      Create a Bangumi token
                    </a>
                    <input
                      aria-label="Bangumi access token"
                      autoComplete="off"
                      placeholder="Paste token"
                      type="password"
                      value={bangumiToken}
                      onChange={(event) => setBangumiToken(event.target.value)}
                    />
                    <button
                      disabled={isBusy || !bangumiToken.trim()}
                      onClick={() => void connectBangumi()}
                      type="button"
                    >
                      Connect Bangumi
                    </button>
                  </div>
                ) : null}
              </AccountRow>
              <button disabled={isBusy} onClick={() => void load()} type="button">
                Refresh account status
              </button>
            </div>
          ) : null}
        </div>
      </details>
    </section>
  );
}

function AccountRow({
  account,
  children,
  name,
  onDisconnect
}: {
  account: ProviderAccountStatus;
  children: ReactNode;
  name: string;
  onDisconnect: () => void;
}) {
  return (
    <div className="provider-account-row">
      <span>
        <strong>{name}</strong>
        <small>{accountLabel(account)}</small>
      </span>
      {account.state === "CONNECTED" ? (
        <button onClick={onDisconnect} type="button">Disconnect</button>
      ) : null}
      {children}
    </div>
  );
}

function accountLabel(account: ProviderAccountStatus): string {
  if (account.state === "CONNECTED") {
    return "Connected as " + (account.displayName || account.userId || "account");
  }
  if (account.state === "NEEDS_RECONNECT") return "Reconnect required";
  if (account.state === "UNAVAILABLE") return "Unavailable in this server build";
  return "Not connected";
}

function describeError(error: unknown): string {
  if (error instanceof DanmakuApiError && error.status === 401) {
    return "The pairing token was rejected.";
  }
  return error instanceof Error ? error.message : "Account operation failed.";
}
