import { NextResponse } from "next/server";

export async function POST() {
  const out = NextResponse.json({ ok: true });
  out.cookies.delete("sb_admin");
  return out;
}
