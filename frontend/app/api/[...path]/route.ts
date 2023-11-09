import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

const API_URL = process.env.API_URL ?? "http://localhost:8080";

/** Forwards /api/* to the Spring Boot API, adding the admin token from the httpOnly session cookie. */
async function proxy(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const token = (await cookies()).get("sb_admin")?.value;
  if (!token) return NextResponse.json({ error: "Not signed in" }, { status: 401 });
  const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
  const ct = req.headers.get("content-type");
  if (ct) headers["content-type"] = ct;
  const actor = req.headers.get("x-actor");
  if (actor) headers["x-actor"] = actor;
  const hasBody = !["GET", "HEAD"].includes(req.method);
  try {
    const res = await fetch(`${API_URL}/api/v1/${path.map(encodeURIComponent).join("/")}${req.nextUrl.search}`, {
      method: req.method, headers, body: hasBody ? await req.text() : undefined, cache: "no-store",
    });
    if (res.status === 401) {
      const out = NextResponse.json({ error: "Session expired" }, { status: 401 });
      out.cookies.delete("sb_admin");
      return out;
    }
    return new NextResponse(res.status === 204 ? null : await res.text(), { status: res.status, headers: { "content-type": res.headers.get("content-type") ?? "application/json" } });
  } catch {
    return NextResponse.json({ error: "The Switchboard API is unreachable." }, { status: 502 });
  }
}

export { proxy as GET, proxy as POST, proxy as PUT, proxy as PATCH, proxy as DELETE };
