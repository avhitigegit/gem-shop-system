"use client";

import Link from "next/link";
import { ROLE_LABEL } from "@/lib/roles";
import { useSession } from "@/lib/session";

/** "Not allowed" – prototype accessDenied(): shown instead of a page the role cannot open. */
export function AccessDenied({ message }: { message?: string }) {
  const { role } = useSession();
  return (
    <div className="card access-denied">
      <div className="card-content" style={{ padding: 32 }}>
        <div style={{ fontSize: 40 }}>🔒</div>
        <h2>Not available for the {ROLE_LABEL[role]} role</h2>
        <p className="muted mt-s">{message ?? "Your login cannot open this screen. Ask the owner if you need it."}</p>
        <Link className="btn btn-outline mt" href="/">
          Back to Home
        </Link>
      </div>
    </div>
  );
}
