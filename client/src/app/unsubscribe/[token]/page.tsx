import type { Metadata } from "next";
import { Suspense } from "react";
import { UnsubscribeScreen } from "./unsubscribe-screen";

export const metadata: Metadata = { title: "Unsubscribe", robots: { index: false, follow: false } };

/** The unsubscribe link in every offer (P4-03, BRD J2): works without signing in. */
export default function Page(props: PageProps<"/unsubscribe/[token]">) {
  return (
    <Suspense fallback={<div className="auth-main" style={{ minHeight: "100vh" }} aria-busy="true" />}>
      <WithToken params={props.params} />
    </Suspense>
  );
}

async function WithToken({ params }: { params: PageProps<"/unsubscribe/[token]">["params"] }) {
  const { token } = await params;
  return <UnsubscribeScreen token={decodeURIComponent(token)} />;
}
