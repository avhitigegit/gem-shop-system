"use client";

/* Shared parts of the sign-in pages (prototype auth/*.html): the navy side panel and the small helpers. */
import Link from "next/link";
import type { ReactNode } from "react";

export function AuthSide({ title, children, foot }: { title: ReactNode; children: ReactNode; foot?: ReactNode }) {
  return (
    <div className="auth-side">
      <div className="row">
        {/* eslint-disable-next-line @next/next/no-img-element -- small SVG logo */}
        <img src="/logo.svg" width={44} alt="" />
        {title}
      </div>
      <div>{children}</div>
      <small>{foot ?? "Gem Shop System · NUVI Engineering PVT Ltd"}</small>
    </div>
  );
}

/** Only allow going back to a page of this app after sign-in (never to another site). */
export function safeNext(next: string | null): string | null {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/login") ? next : null;
}

/** The small centred card of the password pages (prototype auth/forgot-password.html). */
export function AuthBox({ children }: { children: ReactNode }) {
  return (
    <div className="auth-main" style={{ minHeight: "100vh" }}>
      <div className="auth-card">
        <div className="center mb">
          {/* eslint-disable-next-line @next/next/no-img-element -- small SVG logo */}
          <img src="/logo.svg" width={48} alt="" style={{ display: "inline-block" }} />
        </div>
        <div className="card">
          <div className="card-content" style={{ padding: 28 }}>
            {children}
          </div>
        </div>
        <p className="center mt-s">
          <Link href="/login">← Back to sign in</Link>
        </p>
      </div>
    </div>
  );
}
