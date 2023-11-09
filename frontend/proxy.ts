import { NextRequest, NextResponse } from "next/server";

/** Optimistic redirect: pages need the session cookie. The API still enforces the real check on every call. */
export function proxy(req: NextRequest) {
  const signedIn = req.cookies.has("sb_admin");
  const onLogin = req.nextUrl.pathname === "/login";
  if (!signedIn && !onLogin) return NextResponse.redirect(new URL("/login", req.url));
  if (signedIn && onLogin) return NextResponse.redirect(new URL("/", req.url));
  return NextResponse.next();
}

export const config = { matcher: ["/((?!api|_next|favicon.ico).*)"] };
