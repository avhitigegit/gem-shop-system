import type { Metadata } from "next";
import { PageHead } from "@/components/ui/steps";
import { HealthCheck } from "./health-check";

export const metadata: Metadata = {
  title: "System health",
};

export default function HealthPage() {
  return (
    <main className="mx-auto w-full max-w-xl px-4 py-10">
      <PageHead title="System health" sub="Checks that the frontend can reach the backend API and the database." />
      <HealthCheck />
    </main>
  );
}
