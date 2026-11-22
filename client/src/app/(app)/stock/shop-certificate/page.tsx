import type { Metadata } from "next";
import { CertificateScreen } from "./certificate-screen";

export const metadata: Metadata = { title: "Shop certificate" };

export default function Page() {
  return <CertificateScreen />;
}
