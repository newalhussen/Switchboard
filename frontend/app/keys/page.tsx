"use client";

import { useEffect, useState } from "react";
import { api, ApiError, type EnvInfo } from "@/lib/api";

export default function Keys() {
  const [envs, setEnvs] = useState<EnvInfo[] | null>(null);
  const [fresh, setFresh] = useState<{ environment: string; sdkKey: string } | null>(null);
  const [error, setError] = useState("");
  const load = () => api<EnvInfo[]>("/environments").then(setEnvs).catch((e) => setError(e.message));
  useEffect(() => { void load(); }, []);

  async function rotate(env: string) {
    if (!confirm(`Rotate the ${env} SDK key? Applications using the current key will stop working until updated.`)) return;
    try {
      setFresh(await api("/environments/" + env + "/rotate-key", { method: "POST" }));
      await load();
    } catch (e) { setError(e instanceof ApiError ? e.message : "Failed"); }
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-semibold">SDK keys</h1>
        <p className="text-sm text-muted">Each environment has one key. Applications send it as <code>Authorization: Bearer &lt;key&gt;</code> to <code>/api/v1/evaluate</code>. Keys are stored hashed and shown only once.</p>
      </div>
      {error && <p role="alert" className="text-red-600">{error}</p>}
      {fresh && (
        <div className="rounded-xl border border-emerald-400 bg-emerald-50 p-4 text-sm text-emerald-900 dark:bg-emerald-950 dark:text-emerald-100">
          <p className="font-medium">New {fresh.environment} key. Copy it now, you won’t see it again.</p>
          <div className="mt-2 flex items-center gap-2">
            <code className="flex-1 break-all rounded bg-white/60 p-2 dark:bg-black/30">{fresh.sdkKey}</code>
            <button className="btn" onClick={() => navigator.clipboard?.writeText(fresh.sdkKey)}>Copy</button>
          </div>
        </div>
      )}
      <ul className="divide-y divide-line rounded-xl border border-line bg-card">
        {envs?.map((e) => (
          <li key={e.name} className="flex items-center gap-4 px-4 py-3">
            <div className="flex-1"><div className="font-medium capitalize">{e.name}</div><div className="font-mono text-xs text-muted">{e.keyPrefix}</div></div>
            <button className="btn" onClick={() => rotate(e.name)}>Rotate key</button>
          </li>
        ))}
      </ul>
    </div>
  );
}
