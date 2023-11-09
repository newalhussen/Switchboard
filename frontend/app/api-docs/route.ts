import { NextResponse } from "next/server";

const API_URL = process.env.API_URL ?? "http://localhost:8080";

/** Sends users to the Swagger UI served by the Spring Boot API. */
export function GET() {
  return NextResponse.redirect(`${API_URL}/swagger-ui.html`);
}
