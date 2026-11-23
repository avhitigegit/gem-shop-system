"use client";

/* Unsubscribe page – opened by a customer from the link in an offer email (BRD J2, rule 8; PDPA No. 9 of 2022).
   One button: no more offers or birthday wishes. Invoices for future purchases are still sent. Shows the first name only. */
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { api, errorMessage } from "@/lib/api/client";
import { useLoad } from "@/lib/use-load";

export function UnsubscribeScreen({ token }: { token: string }) {
  const { data, error, setData } = useLoad(
    () => api.GET("/api/public/unsubscribe/{token}", { params: { path: { token } } }),
    [token],
  );
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState<string | null>(null);

  const unsubscribe = async () => {
    setBusy(true);
    setFailed(null);
    const { data: done, error: err } = await api.POST("/api/public/unsubscribe/{token}", {
      params: { path: { token } },
    });
    setBusy(false);
    if (!done) return setFailed(errorMessage(err));
    setData(done);
  };

  return (
    <div className="auth-main" style={{ minHeight: "100vh" }}>
      <div className="auth-card">
        <div className="center mb">
          {/* eslint-disable-next-line @next/next/no-img-element -- small SVG logo */}
          <img src="/logo.svg" width={48} alt="" style={{ display: "inline-block" }} />
        </div>
        <div className="card">
          <div className="card-content center" style={{ padding: 28 }}>
            {error && !data ? (
              <>
                <div style={{ fontSize: 36 }}>🔗</div>
                <h2>This link does not work</h2>
                <p className="muted mt-s">{errorMessage(error)}</p>
              </>
            ) : !data ? (
              <p className="muted">Loading…</p>
            ) : data.unsubscribed ? (
              <>
                <div style={{ fontSize: 36 }}>✓</div>
                <h2 id="unsubDone">You are unsubscribed</h2>
                <p className="muted mt-s">
                  {data.firstName ? `${data.firstName}, you` : "You"} will not get offers or birthday wishes from{" "}
                  {data.shop} any more. Invoices for future purchases are still sent.
                </p>
              </>
            ) : (
              <>
                <h2>Stop offers from {data.shop}?</h2>
                <p className="muted mt-s">
                  {data.firstName ? `Hello ${data.firstName}. ` : ""}Press the button and we will not send you offers or
                  birthday wishes by email or WhatsApp any more.
                </p>
                {failed ? (
                  <p className="mt-s text-danger" role="alert">
                    {failed}
                  </p>
                ) : null}
                <Button
                  variant="sapphire"
                  block
                  className="mt"
                  id="unsubscribe"
                  disabled={busy}
                  onClick={() => void unsubscribe()}
                >
                  {busy ? "Please wait…" : "Unsubscribe"}
                </Button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
