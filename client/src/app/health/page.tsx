import type { Metadata } from "next";
import { HealthCheck } from "./health-check";

export const metadata: Metadata = {
  title: "System health",
};

export default function HealthPage() {
  return (
    <main className="mx-auto w-full max-w-xl flex-1 px-4 py-10">
      <h1 className="mb-1 text-[22px] font-bold">System health</h1>
      <p className="mb-6 text-sm text-muted-foreground">
        Checks that the frontend can reach the backend API and the database.
      </p>
      <HealthCheck />
    </main>
  );
}
