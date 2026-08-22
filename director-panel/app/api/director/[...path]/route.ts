import { NextRequest, NextResponse } from "next/server";

type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(request: NextRequest, context: RouteContext) {
  const token = process.env.COSMIC_DIRECTOR_TOKEN;
  if (!token) {
    return NextResponse.json(
      { code: "PANEL_NOT_CONFIGURED", message: "Director bridge token is not configured." },
      { status: 503 },
    );
  }
  const { path } = await context.params;
  const base = (process.env.DIRECTOR_BRIDGE_URL ?? "http://127.0.0.1:8790").replace(/\/$/, "");
  const url = `${base}/internal/director/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`;
  const method = request.method;
  const body = method === "GET" || method === "HEAD" ? undefined : await request.text();
  try {
    const response = await fetch(url, {
      method,
      body: body || undefined,
      cache: "no-store",
      headers: {
        Authorization: `Bearer ${token}`,
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      signal: AbortSignal.timeout(15_000),
    });
    const payload = await response.text();
    return new NextResponse(payload, {
      status: response.status,
      headers: { "Content-Type": response.headers.get("Content-Type") ?? "application/json" },
    });
  } catch {
    return NextResponse.json(
      { code: "COSMIC_UNAVAILABLE", message: "The local Cosmic Director bridge is unavailable." },
      { status: 503 },
    );
  }
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
