"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiError } from "@/lib/api";

export default function Login() {
  const router = useRouter();
  const [token, setToken] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api("/auth/login", { method: "POST", body: { token: token.trim() } });
      router.push("/");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Sign in failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="mx-auto mt-16 max-w-sm space-y-4 rounded-xl border border-line bg-card p-6">
      <h1 className="text-lg font-semibold">Sign in</h1>
      <p className="text-sm text-muted">Enter the admin token configured as <code>SWITCHBOARD_ADMIN_TOKEN</code> on the API.</p>
      <label className="block text-sm">Admin token
        <input type="password" className="input mt-1" value={token} onChange={(e) => setToken(e.target.value)} autoFocus autoComplete="current-password" />
      </label>
      {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      <button className="btn btn-primary w-full justify-center" disabled={busy || !token}>{busy ? "Checking…" : "Sign in"}</button>
    </form>
  );
}
