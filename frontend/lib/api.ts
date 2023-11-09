export interface EnvSettings { enabled: boolean; rolloutPercentage: number; updatedAt: string | null }
export interface Flag { key: string; name: string; description: string; createdAt: string; updatedAt: string; environments: Record<string, EnvSettings> }
export interface AuditEntry { id: number; at: string; flagKey: string; environment: string | null; action: string; actor: string; detail: string }
export interface EnvInfo { name: string; keyPrefix: string }

export class ApiError extends Error {
  constructor(message: string, public status: number, public fields: Record<string, string> = {}) {
    super(message);
  }
}

export async function api<T>(path: string, init?: { method?: string; body?: unknown }): Promise<T> {
  const res = await fetch(`/api${path}`, {
    method: init?.method ?? "GET",
    headers: init?.body !== undefined ? { "content-type": "application/json" } : undefined,
    body: init?.body !== undefined ? JSON.stringify(init.body) : undefined,
    cache: "no-store",
  }).catch(() => { throw new ApiError("Cannot reach the server.", 0); });
  if (res.status === 401 && typeof window !== "undefined" && !path.startsWith("/auth/")) {
    window.location.href = "/login";
  }
  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new ApiError(data.error ?? `Request failed (${res.status})`, res.status, data.fields ?? {});
  return data as T;
}

export const when = (iso: string | null) => (iso ? new Date(iso).toLocaleString() : "—");
