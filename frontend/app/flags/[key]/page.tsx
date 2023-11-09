"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, ApiError, when, type AuditEntry, type Flag } from "@/lib/api";

export default function FlagPage() {
  const { key } = useParams<{ key: string }>();
  const router = useRouter();
  const [flag, setFlag] = useState<Flag | null>(null);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [form, setForm] = useState({ name: "", description: "" });
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const [f, a] = await Promise.all([api<Flag>(`/flags/${key}`), api<AuditEntry[]>(`/flags/${key}/audit`)]);
        setFlag(f); setAudit(a); setForm({ name: f.name, description: f.description });
      } catch (e) { setError((e as Error).message); }
    })();
  }, [key]);

  if (!flag) return error ? <p role="alert" className="text-red-600">{error} <Link href="/" className="underline">Back</Link></p> : <p className="text-sm text-muted">Loading…</p>;

  async function save(e: React.FormEvent) {
    e.preventDefault();
    try {
      const f = await api<Flag>(`/flags/${key}`, { method: "PATCH", body: form });
      setFlag(f); setSaved(true); setError("");
      setAudit(await api<AuditEntry[]>(`/flags/${key}/audit`));
    } catch (err) { setError(err instanceof ApiError ? (Object.values(err.fields)[0] ?? err.message) : "Failed"); }
  }

  return (
    <div className="space-y-6">
      <Link href="/" className="text-sm text-muted hover:underline">← All flags</Link>
      <div className="flex items-start gap-3">
        <div className="flex-1">
          <h1 className="text-xl font-semibold">{flag.name}</h1>
          <p className="font-mono text-sm text-muted">{flag.key}</p>
        </div>
        <button className="btn btn-danger" onClick={async () => { if (confirm(`Delete flag "${flag.key}"? Apps evaluating it will get a 404.`)) { await api(`/flags/${key}`, { method: "DELETE" }); router.push("/"); } }}>Delete</button>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        {Object.entries(flag.environments).map(([env, s]) => (
          <div key={env} className="rounded-xl border border-line bg-card p-4">
            <div className="text-xs capitalize text-muted">{env}</div>
            <div className={`mt-1 text-lg font-semibold ${s.enabled ? "text-emerald-600" : "text-muted"}`}>{s.enabled ? (s.rolloutPercentage === 100 ? "On for everyone" : `On for ${s.rolloutPercentage}%`) : "Off"}</div>
            <div className="text-xs text-muted">Updated {when(s.updatedAt)}</div>
          </div>
        ))}
      </div>

      <form onSubmit={save} className="grid gap-3 rounded-xl border border-line bg-card p-4 sm:grid-cols-2">
        <label className="text-sm">Name<input className="input mt-1" value={form.name} onChange={(e) => { setForm({ ...form, name: e.target.value }); setSaved(false); }} /></label>
        <label className="text-sm">Description<input className="input mt-1" value={form.description} onChange={(e) => { setForm({ ...form, description: e.target.value }); setSaved(false); }} /></label>
        <div className="flex items-center justify-end gap-3 sm:col-span-2">
          {error && <span role="alert" className="text-sm text-red-600">{error}</span>}
          {saved && <span className="text-sm text-emerald-600">Saved</span>}
          <button className="btn btn-primary">Save details</button>
        </div>
      </form>

      <section className="rounded-xl border border-line bg-card p-4">
        <h2 className="mb-3 text-sm font-semibold">History</h2>
        <ul className="divide-y divide-line text-sm">
          {audit.map((a) => (
            <li key={a.id} className="py-2">
              <span className="rounded bg-bg px-1.5 py-0.5 text-xs font-medium">{a.action}</span>{" "}
              {a.environment && <span className="capitalize text-muted">{a.environment} · </span>}{a.detail}
              <div className="text-xs text-muted">{when(a.at)} · {a.actor}</div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
