import { NextRequest, NextResponse } from "next/server";

const API_URL = process.env.API_URL ?? "http://localhost:8080";

export async function POST(req: NextRequest) {
  const { token } = (await req.json().catch(() => ({}))) as { token?: string };
  if (!token) return NextResponse.json({ error: "Enter the admin token" }, { status: 400 });
  try {
    const res = await fetch(`${API_URL}/api/v1/environments`, { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" });
    if (!res.ok) return NextResponse.json({ error: "That token is not valid" }, { status: 401 });
  } catch {
    return NextResponse.json({ error: "The Switchboard API is unreachable." }, { status: 502 });
  }
  const out = NextResponse.json({ ok: true });
  out.cookies.set("sb_admin", token, { httpOnly: true, sameSite: "strict", secure: process.env.NODE_ENV === "production", path: "/", maxAge: 60 * 60 * 8 });
  return out;
}
