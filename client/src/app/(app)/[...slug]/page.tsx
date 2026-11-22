import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { pageInfo } from "@/lib/nav";

/* Any menu page that is not built yet: says which step builds it and links to the same prototype screen.
   Each heading replaces these with the real page (a real page always wins over this catch-all). */

function find(slug: string[]) {
  const path = "/" + slug.join("/");
  const info = pageInfo(path);
  const label = info.item?.label ?? info.hidden?.label;
  const proto = info.item?.proto ?? info.hidden?.proto;
  return label && proto ? { info, label, proto } : null;
}

export async function generateMetadata(props: PageProps<"/[...slug]">): Promise<Metadata> {
  const { slug } = await props.params;
  return { title: find(slug)?.label ?? "Not found" };
}

export default async function LaterPage(props: PageProps<"/[...slug]">) {
  const { slug } = await props.params;
  const page = find(slug);
  if (!page) notFound();
  const icon = page.info.item?.icon ?? page.info.section.icon;
  return (
    <div className="card coming-soon">
      <div className="card-content center" style={{ padding: "40px 24px" }}>
        <div style={{ fontSize: 42, lineHeight: 1 }}>{icon}</div>
        <h2 style={{ margin: "12px 0 6px" }}>{page.label}</h2>
        <p className="muted" style={{ margin: "0 0 18px" }}>
          This screen is built in a later step. It will look and work like the prototype screen.
        </p>
        <a className="btn btn-outline" href={`/prototype/${page.proto.split(" ")[0]}`} target="_blank" rel="noreferrer">
          See it in the prototype ↗
        </a>{" "}
        <Link className="btn btn-sapphire" href="/">
          Back to Home
        </Link>
      </div>
    </div>
  );
}
