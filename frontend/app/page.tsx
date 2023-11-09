"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api, ApiError, type EnvInfo, type Flag } from "@/lib/api";

function EnvCell({ flag, env, onChange }: { flag: Flag; env: string; onChange: (f: Flag) => void }) {
  const s = flag.environments[env];
  const [pct, setPct] = useState(String(s.rolloutPercentage));
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  useEffect(() => setPct(String(s.rolloutPercentage)), [s.rolloutPercentage]);

  async function save(body: { enabled?: boolean; rolloutPercentage?: number }) {
    setBusy(true);
    setErr("");
    try {
      onChange(await api<Flag>(`/flags/${flag.key}/environments/${env}`, { method: "PUT", body }));
    } catch (e) {
      setErr(e instanceof ApiError ? (Object.values(e.fields)[0] ?? e.message) : "Failed");
      setPct(String(s.rolloutPercentage));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-1.5">
      <button
        role="switch" aria-checked={s.enabled} aria-label={`${flag.key} in ${env}`} disabled={busy}
        onClick={() => save({ enabled: !s.enabled })}
        className={`relative h-6 w-11 rounded-full transition ${s.enabled ? "bg-accent" : "bg-slate-300 dark:bg-slate-600"} disabled:opacity-50`}
      >
        <span className={`absolute top-0.5 size-5 rounded-full bg-white transition-all ${s.enabled ? "left-[22px]" : "left-0.5"}`} />
      </button>
      <label className="flex items-center gap-1 text-xs text-muted">
        <input
          className="w-14 rounded border border-line bg-card px-1.5 py-0.5 text-right text-xs text-ink disabled:opacity-40" inputMode="numeric"
          aria-label={`Rollout percentage for ${flag.key} in ${env}`} value={pct} disabled={busy || !s.enabled}
          onChange={(e) => setPct(e.target.value.replace(/\D/g, "").slice(0, 3))}
          onBlur={() => { const n = Number(pct); if (pct !== "" && n !== s.rolloutPercentage) void save({ rolloutPercentage: Math.min(100, n) }); else setPct(String(s.rolloutPercentage)); }}
          onKeyDown={(e) => e.key === "Enter" && (e.target as HTMLInputElement).blur()}
        />%
      </label>
      {err && <span className="text-xs text-red-600">{err}</span>}
    </div>
  );
}

export default function Flags() {
  const [flags, setFlags] = useState<Flag[] | null>(null);
  const [envs, setEnvs] = useState<EnvInfo[]>([]);
  const [error, setError] = useState("");
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ key: "", name: "", description: "" });
  const [formErr, setFormErr] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    try {
      const [f, e] = await Promise.all([api<Flag[]>("/flags"), api<EnvInfo[]>("/environments")]);
      setFlags(f); setEnvs(e); setError("");
    } catch (e) { setError((e as Error).message); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    setFormErr({});
    try {
      await api("/flags", { method: "POST", body: form });
      setForm({ key: "", name: "", description: "" });
      setAdding(false);
      await load();
    } catch (err) {
      if (err instanceof ApiError) setFormErr(Object.keys(err.fields).length ? err.fields : { key: err.message });
    }
  }

  const replace = (f: Flag) => setFlags((all) => all?.map((x) => (x.key === f.key ? f : x)) ?? null);

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Feature flags</h1>
        <button className="btn btn-primary" onClick={() => setAdding((v) => !v)}>+ New flag</button>
      </div>
      {error && <p role="alert" className="rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

      {adding && (
        <form onSubmit={create} className="grid gap-3 rounded-xl border border-line bg-card p-4 sm:grid-cols-2" noValidate>
          <label className="text-sm">Key
            <input className="input mt-1 font-mono" value={form.key} onChange={(e) => setForm({ ...form, key: e.target.value })} placeholder="new-checkout" autoFocus />
            {formErr.key && <span className="text-xs text-red-600">{formErr.key}</span>}
          </label>
          <label className="text-sm">Name
            <input className="input mt-1" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="New checkout flow" />
            {formErr.name && <span className="text-xs text-red-600">{formErr.name}</span>}
          </label>
          <label className="text-sm sm:col-span-2">Description (optional)
            <input className="input mt-1" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </label>
          <div className="flex justify-end gap-2 sm:col-span-2">
            <button type="button" className="btn" onClick={() => setAdding(false)}>Cancel</button>
            <button className="btn btn-primary">Create flag</button>
          </div>
        </form>
      )}

      {flags === null && !error && <p className="text-sm text-muted">Loading flags…</p>}
      {flags?.length === 0 && (
        <div className="rounded-xl border border-dashed border-line p-10 text-center">
          <p className="font-medium">No flags yet</p>
          <p className="mt-1 text-sm text-muted">Create a flag, then switch it on per environment and roll it out gradually.</p>
        </div>
      )}
      {flags && flags.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-line bg-card">
          <table className="w-full text-sm">
            <thead className="border-b border-line text-left text-xs uppercase tracking-wide text-muted">
              <tr><th className="px-4 py-2">Flag</th>{envs.map((e) => <th key={e.name} className="px-4 py-2 capitalize">{e.name}</th>)}</tr>
            </thead>
            <tbody className="divide-y divide-line">
              {flags.map((f) => (
                <tr key={f.key} className="align-top">
                  <td className="px-4 py-3">
                    <Link href={`/flags/${f.key}`} className="font-medium hover:underline">{f.name}</Link>
                    <div className="font-mono text-xs text-muted">{f.key}</div>
                    {f.description && <div className="mt-0.5 max-w-xs text-xs text-muted">{f.description}</div>}
                  </td>
                  {envs.map((e) => <td key={e.name} className="px-4 py-3"><EnvCell flag={f} env={e.name} onChange={replace} /></td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
