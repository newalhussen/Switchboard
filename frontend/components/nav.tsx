"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { api } from "@/lib/api";

export function Nav() {
  const path = usePathname();
  const router = useRouter();
  const link = (href: string, label: string) => (
    <Link href={href} className={`rounded-md px-2.5 py-1 text-sm ${path === href ? "bg-bg font-medium" : "text-muted hover:text-ink"}`}>{label}</Link>
  );
  return (
    <header className="border-b border-line bg-card">
      <div className="mx-auto flex max-w-6xl items-center gap-2 px-4 py-3">
        <Link href="/" className="mr-3 flex items-center gap-2 font-semibold">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2.2" strokeLinecap="round" aria-hidden="true"><rect x="2" y="6" width="20" height="12" rx="6" /><circle cx="16" cy="12" r="3" fill="var(--accent)" /></svg>
          Switchboard
        </Link>
        {path !== "/login" && (
          <>
            {link("/", "Flags")}
            {link("/audit", "Audit log")}
            {link("/keys", "SDK keys")}
            <a href="/api-docs" target="_blank" rel="noreferrer" className="rounded-md px-2.5 py-1 text-sm text-muted hover:text-ink">API docs ↗</a>
            <button className="btn ml-auto" onClick={async () => { await api("/auth/logout", { method: "POST" }); router.push("/login"); }}>Sign out</button>
          </>
        )}
      </div>
    </header>
  );
}
