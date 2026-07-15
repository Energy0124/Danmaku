import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import {
  DanmakuApiError,
  LanProviderRuntimeStatus,
  ProviderSettingsDocument,
  ProviderSettingsUpdate,
  fetchProviderSettings,
  saveProviderSettings
} from "./api";

interface ProviderSettingsPanelProps {
  baseUrl: string;
  token: string;
  onRuntimeUpdated: (runtime: LanProviderRuntimeStatus) => void;
}

const defaultUpdate: ProviderSettingsUpdate = {
  dandanplay: {
    baseUrl: "https://api.dandanplay.net",
    authenticationMode: "SIGNED",
    cacheMaxAgeDays: 30
  },
  externalAnime: {
    bangumiBaseUrl: "https://api.bgm.tv/",
    bangumiUserAgent: "Danmaku/0.1 (https://github.com/Energy0124/Danmaku)"
  }
};

export function ProviderSettingsPanel({
  baseUrl,
  token,
  onRuntimeUpdated
}: ProviderSettingsPanelProps) {
  const [document, setDocument] = useState<ProviderSettingsDocument | null>(null);
  const [form, setForm] = useState<ProviderSettingsUpdate>(defaultUpdate);
  const [message, setMessage] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!token.trim()) {
      setDocument(null);
      setMessage("Enter the server pairing token above to manage provider settings.");
      return () => {
        cancelled = true;
      };
    }
    setIsLoading(true);
    setMessage("Loading provider settings...");
    void fetchProviderSettings(baseUrl, token.trim())
      .then((next) => {
        if (cancelled) return;
        setDocument(next);
        setForm(formFromDocument(next));
        setMessage("Secrets are write-only and remain protected on the server.");
        onRuntimeUpdated(next.runtime);
      })
      .catch((error) => {
        if (cancelled) return;
        setDocument(null);
        setMessage(describeSettingsError(error));
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [baseUrl, token, onRuntimeUpdated]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSaving(true);
    setMessage("Saving provider settings...");
    try {
      const next = await saveProviderSettings(baseUrl, token.trim(), form);
      setDocument(next);
      setForm(formFromDocument(next));
      setMessage("Provider settings saved and the running provider clients were refreshed.");
      onRuntimeUpdated(next.runtime);
    } catch (error) {
      setMessage(describeSettingsError(error));
    } finally {
      setIsSaving(false);
    }
  }

  const dandanplay = document?.settings.dandanplay;
  const externalAnime = document?.settings.externalAnime;
  const disabled = isLoading || isSaving || !document;

  return (
    <section className="provider-settings-shell" aria-labelledby="provider-settings-title">
      <details>
        <summary>
          <span>
            <strong id="provider-settings-title">Provider administration</strong>
            <small>Credentials, API endpoints, and runtime readiness</small>
          </span>
          <span className={document ? "admin-state ready" : "admin-state limited"}>
            {isLoading ? "Loading" : document ? "Authorized" : "Locked"}
          </span>
        </summary>

        <form className="provider-settings-form" onSubmit={submit}>
          <p className="provider-settings-message" aria-live="polite">
            {message}
          </p>

          <fieldset disabled={disabled}>
            <legend>Dandanplay</legend>
            <div className="settings-grid">
              <label className="settings-wide">
                API base URL
                <input
                  type="url"
                  required
                  value={form.dandanplay.baseUrl}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      dandanplay: { ...current.dandanplay, baseUrl: event.target.value }
                    }))
                  }
                />
              </label>
              <label>
                Authentication
                <select
                  value={form.dandanplay.authenticationMode}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      dandanplay: {
                        ...current.dandanplay,
                        authenticationMode: event.target.value as "SIGNED" | "CREDENTIAL"
                      }
                    }))
                  }
                >
                  <option value="SIGNED">Signed API / proxy</option>
                  <option value="CREDENTIAL">App credentials</option>
                </select>
              </label>
              <label>
                Cache age (days)
                <input
                  min="1"
                  required
                  type="number"
                  value={form.dandanplay.cacheMaxAgeDays}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      dandanplay: {
                        ...current.dandanplay,
                        cacheMaxAgeDays: Number(event.target.value)
                      }
                    }))
                  }
                />
              </label>
              <label>
                App ID
                <input
                  value={form.dandanplay.appId ?? ""}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      dandanplay: {
                        ...current.dandanplay,
                        appId: optionalValue(event.target.value)
                      }
                    }))
                  }
                />
              </label>
              <SecretField
                clear={form.dandanplay.clearAppSecret ?? false}
                configured={dandanplay?.hasAppSecret ?? false}
                label="App secret"
                value={form.dandanplay.appSecret ?? ""}
                onClear={(clear) =>
                  setForm((current) => ({
                    ...current,
                    dandanplay: {
                      ...current.dandanplay,
                      appSecret: clear ? undefined : current.dandanplay.appSecret,
                      clearAppSecret: clear
                    }
                  }))
                }
                onValue={(value) =>
                  setForm((current) => ({
                    ...current,
                    dandanplay: {
                      ...current.dandanplay,
                      appSecret: optionalValue(value),
                      clearAppSecret: false
                    }
                  }))
                }
              />
            </div>
          </fieldset>

          <fieldset disabled={disabled}>
            <legend>MyAnimeList</legend>
            <div className="settings-grid">
              <label>
                Client ID
                <input
                  value={form.externalAnime.myAnimeListClientId ?? ""}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      externalAnime: {
                        ...current.externalAnime,
                        myAnimeListClientId: optionalValue(event.target.value)
                      }
                    }))
                  }
                />
              </label>
              <SecretField
                clear={form.externalAnime.clearMyAnimeListClientSecret ?? false}
                configured={externalAnime?.hasMyAnimeListClientSecret ?? false}
                label="Client secret"
                value={form.externalAnime.myAnimeListClientSecret ?? ""}
                onClear={(clear) =>
                  setForm((current) => ({
                    ...current,
                    externalAnime: {
                      ...current.externalAnime,
                      myAnimeListClientSecret: clear
                        ? undefined
                        : current.externalAnime.myAnimeListClientSecret,
                      clearMyAnimeListClientSecret: clear
                    }
                  }))
                }
                onValue={(value) =>
                  setForm((current) => ({
                    ...current,
                    externalAnime: {
                      ...current.externalAnime,
                      myAnimeListClientSecret: optionalValue(value),
                      clearMyAnimeListClientSecret: false
                    }
                  }))
                }
              />
              <SecretField
                clear={form.externalAnime.clearMyAnimeListAccessToken ?? false}
                configured={externalAnime?.hasMyAnimeListAccessToken ?? false}
                label="OAuth access token"
                value={form.externalAnime.myAnimeListAccessToken ?? ""}
                onClear={(clear) =>
                  setForm((current) => ({
                    ...current,
                    externalAnime: {
                      ...current.externalAnime,
                      myAnimeListAccessToken: clear
                        ? undefined
                        : current.externalAnime.myAnimeListAccessToken,
                      clearMyAnimeListAccessToken: clear
                    }
                  }))
                }
                onValue={(value) =>
                  setForm((current) => ({
                    ...current,
                    externalAnime: {
                      ...current.externalAnime,
                      myAnimeListAccessToken: optionalValue(value),
                      clearMyAnimeListAccessToken: false
                    }
                  }))
                }
              />
            </div>
          </fieldset>

          <fieldset disabled={disabled}>
            <legend>Bangumi</legend>
            <div className="settings-grid">
              <label className="settings-wide">
                API base URL
                <input
                  type="url"
                  required
                  value={form.externalAnime.bangumiBaseUrl}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      externalAnime: {
                        ...current.externalAnime,
                        bangumiBaseUrl: event.target.value
                      }
                    }))
                  }
                />
              </label>
              <label className="settings-wide">
                User agent
                <input
                  required
                  value={form.externalAnime.bangumiUserAgent}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      externalAnime: {
                        ...current.externalAnime,
                        bangumiUserAgent: event.target.value
                      }
                    }))
                  }
                />
              </label>
              <SecretField
                clear={form.externalAnime.clearBangumiAccessToken ?? false}
                configured={externalAnime?.hasBangumiAccessToken ?? false}
                label="Access token"
                value={form.externalAnime.bangumiAccessToken ?? ""}
                onClear={(clear) =>
                  setForm((current) => ({
                    ...current,
                    externalAnime: {
                      ...current.externalAnime,
                      bangumiAccessToken: clear
                        ? undefined
                        : current.externalAnime.bangumiAccessToken,
                      clearBangumiAccessToken: clear
                    }
                  }))
                }
                onValue={(value) =>
                  setForm((current) => ({
                    ...current,
                    externalAnime: {
                      ...current.externalAnime,
                      bangumiAccessToken: optionalValue(value),
                      clearBangumiAccessToken: false
                    }
                  }))
                }
              />
            </div>
          </fieldset>

          <div className="provider-settings-actions">
            <span>Blank secret fields keep the protected value already on the server.</span>
            <button disabled={disabled} type="submit">
              {isSaving ? "Saving..." : "Save and apply"}
            </button>
          </div>
        </form>
      </details>
    </section>
  );
}

function SecretField({
  clear,
  configured,
  label,
  value,
  onClear,
  onValue
}: {
  clear: boolean;
  configured: boolean;
  label: string;
  value: string;
  onClear: (clear: boolean) => void;
  onValue: (value: string) => void;
}) {
  return (
    <div className="secret-field">
      <label>
        {label}
        <input
          autoComplete="new-password"
          disabled={clear}
          placeholder={configured ? "Protected value saved" : "Not configured"}
          type="password"
          value={value}
          onChange={(event) => onValue(event.target.value)}
        />
      </label>
      <label className="clear-secret">
        <input
          checked={clear}
          type="checkbox"
          onChange={(event) => onClear(event.target.checked)}
        />
        Clear saved value
      </label>
    </div>
  );
}

function formFromDocument(document: ProviderSettingsDocument): ProviderSettingsUpdate {
  const dandanplay = document.settings.dandanplay;
  const externalAnime = document.settings.externalAnime;
  return {
    dandanplay: {
      baseUrl: dandanplay?.baseUrl || defaultUpdate.dandanplay.baseUrl,
      appId: optionalValue(dandanplay?.appId ?? ""),
      authenticationMode:
        dandanplay?.authenticationMode === "CREDENTIAL" ? "CREDENTIAL" : "SIGNED",
      cacheMaxAgeDays:
        dandanplay?.cacheMaxAgeDays ?? defaultUpdate.dandanplay.cacheMaxAgeDays
    },
    externalAnime: {
      myAnimeListClientId: optionalValue(externalAnime?.myAnimeListClientId ?? ""),
      bangumiBaseUrl:
        externalAnime?.bangumiBaseUrl || defaultUpdate.externalAnime.bangumiBaseUrl,
      bangumiUserAgent:
        externalAnime?.bangumiUserAgent || defaultUpdate.externalAnime.bangumiUserAgent
    }
  };
}

function optionalValue(value: string): string | undefined {
  return value.trim() ? value : undefined;
}

function describeSettingsError(error: unknown): string {
  if (error instanceof DanmakuApiError && error.status === 401) {
    return "The pairing token was rejected. Check the token and reconnect.";
  }
  if (error instanceof Error) return error.message;
  return "Provider settings could not be loaded.";
}
