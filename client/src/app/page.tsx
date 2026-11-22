import Link from "next/link";
import { Button } from "@/components/ui/button";

// Placeholder until the real screens start (P1-03 app frame, P1-02 login).
export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col justify-center px-4 py-10">
      <h1 className="mb-1 text-[22px] font-bold">Gem Shop System</h1>
      <p className="mb-6 text-sm text-muted-foreground">
        Project skeleton is running. The screens are built from the approved prototype, heading by heading.
      </p>
      <div className="flex flex-wrap gap-2">
        <Button asChild>
          <Link href="/health">System health</Link>
        </Button>
        <Button asChild variant="outline">
          <a href="/prototype/index.html">Open the prototype</a>
        </Button>
      </div>
    </main>
  );
}
