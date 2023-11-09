"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, when, type AuditEntry } from "@/lib/api";

export default function Audit() {
  const [rows, setRows] = useState<AuditEntry[] | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { api<AuditEntry[]>("/audit?limit=200").then(setRows).catch((e) => setError(e.message)); }, []);
  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold">Audit log</h1>
      {error && <p role="alert" className="text-red-600">{error}</p>}
      {rows === null && !error && <p className="text-sm text-muted">Loading…</p>}
      {rows?.length === 0 && <p className="text-sm text-muted">No changes recorded yet.</p>}
      {rows && rows.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-line bg-card">
          <table className="w-full text-sm">
            <thead className="border-b border-line text-left text-xs uppercase text-muted"><tr><th className="px-4 py-2">When</th><th>Flag</th><th>Env</th><th>Action</th><th>Detail</th><th>By</th></tr></thead>
            <tbody className="divide-y divide-line">
              {rows.map((a) => (
                <tr key={a.id}>
                  <td className="whitespace-nowrap px-4 py-2 text-muted">{when(a.at)}</td>
                  <td className="font-mono text-xs">{a.flagKey === "*" ? "—" : <Link className="hover:underline" href={`/flags/${a.flagKey}`}>{a.flagKey}</Link>}</td>
                  <td className="capitalize">{a.environment ?? "—"}</td>
                  <td><span className="rounded bg-bg px-1.5 py-0.5 text-xs font-medium">{a.action}</span></td>
                  <td className="py-2">{a.detail}</td>
                  <td className="pr-4 text-muted">{a.actor}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
