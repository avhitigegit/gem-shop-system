"use client";

/* Gem picture when there is no photo – the prototype drawing (prototype/assets/js/app.js → gemSVG),
   coloured by gem type and drawn as a loose stone, ring, pendant, earrings, bracelet or necklace. */
import { useId } from "react";

export const GEM_COLORS: Record<string, [string, string, string]> = {
  "Blue Sapphire": ["#1f4fa3", "#5b8ef0", "#0c2a66"],
  "Yellow Sapphire": ["#e8b923", "#fde68a", "#a16207"],
  "Pink Sapphire": ["#e0559b", "#fbcfe8", "#9d174d"],
  Padparadscha: ["#f08a5d", "#ffd3b6", "#b4502d"],
  "Star Sapphire": ["#5b6f95", "#c7d2e8", "#2f3e5c"],
  Ruby: ["#c0172f", "#fb7185", "#7f0d1d"],
  "Cat's Eye": ["#c99a2e", "#fde7a7", "#7a5a12"],
  Alexandrite: ["#1f8a70", "#c084fc", "#0f4d3f"],
  Moonstone: ["#c9d6ea", "#ffffff", "#8aa0c4"],
  Spinel: ["#b4508c", "#e9b4d6", "#6b2251"],
  Garnet: ["#7a1426", "#d04a5f", "#430a14"],
  Topaz: ["#4fb3d9", "#c8ecfa", "#1c6f91"],
  Other: ["#64748b", "#cbd5e1", "#334155"],
};

const esc = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
const f = (v: number) => v.toFixed(1);

/** The SVG markup (same drawing as the prototype). */
export function gemSvg(gem: string, type: string | null | undefined, size: number, id: string) {
  const c = GEM_COLORS[gem] ?? GEM_COLORS.Other;
  const cab = /Star|Cat|Moonstone/.test(gem);
  const defs = `<defs><radialGradient id="${id}" cx="40%" cy="35%" r="70%"><stop offset="0" stop-color="${c[1]}"/><stop offset=".55" stop-color="${c[0]}"/><stop offset="1" stop-color="${c[2]}"/></radialGradient></defs>`;
  const stone = (cx: number, cy: number, r: number) => {
    if (cab) {
      let extra = "";
      if (/Star/.test(gem))
        extra = `<g stroke="#fff" stroke-opacity=".8" stroke-width="${r / 14}" stroke-linecap="round"><line x1="${cx}" y1="${cy - r * 0.7}" x2="${cx}" y2="${cy + r * 0.7}"/><line x1="${cx - r * 0.6}" y1="${cy - r * 0.35}" x2="${cx + r * 0.6}" y2="${cy + r * 0.35}"/><line x1="${cx - r * 0.6}" y1="${cy + r * 0.35}" x2="${cx + r * 0.6}" y2="${cy - r * 0.35}"/></g>`;
      if (/Cat/.test(gem))
        extra = `<ellipse cx="${cx}" cy="${cy}" rx="${r * 0.09}" ry="${r * 0.78}" fill="#fffbe6" opacity=".85"/>`;
      if (/Moonstone/.test(gem))
        extra = `<ellipse cx="${cx - r * 0.15}" cy="${cy - r * 0.2}" rx="${r * 0.55}" ry="${r * 0.35}" fill="#9ec5ff" opacity=".55"/>`;
      return `<ellipse cx="${cx}" cy="${cy}" rx="${r}" ry="${r * 0.82}" fill="url(#${id})" stroke="${c[2]}" stroke-width="1"/>${extra}<ellipse cx="${cx - r * 0.35}" cy="${cy - r * 0.38}" rx="${r * 0.25}" ry="${r * 0.14}" fill="#fff" opacity=".6"/>`;
    }
    const pts = [
      [0, -1],
      [0.72, -0.62],
      [1, 0],
      [0.72, 0.62],
      [0, 1],
      [-0.72, 0.62],
      [-1, 0],
      [-0.72, -0.62],
    ].map((p) => [cx + p[0] * r, cy + p[1] * r * 0.86]);
    const inner = pts.map((p) => [cx + (p[0] - cx) * 0.5, cy + (p[1] - cy) * 0.5]);
    const poly = (a: number[][]) => a.map((p) => p.map(f).join(",")).join(" ");
    let facets = "";
    for (let i = 0; i < 8; i++)
      facets += `<line x1="${f(pts[i][0])}" y1="${f(pts[i][1])}" x2="${f(inner[i][0])}" y2="${f(inner[i][1])}"/>`;
    return `<polygon points="${poly(pts)}" fill="url(#${id})" stroke="${c[2]}" stroke-width="1"/><polygon points="${poly(inner)}" fill="${c[1]}" fill-opacity=".35" stroke="#fff" stroke-opacity=".55" stroke-width=".8"/><g stroke="#fff" stroke-opacity=".45" stroke-width=".7">${facets}</g><polygon points="${f(cx - r * 0.45)},${f(cy - r * 0.5)} ${f(cx - r * 0.1)},${f(cy - r * 0.62)} ${f(cx - r * 0.25)},${f(cy - r * 0.2)}" fill="#fff" opacity=".7"/>`;
  };
  const metal = "#d4af37",
    metal2 = "#a8862a";
  let body: string;
  switch (type) {
    case "Ring":
      body = `<ellipse cx="50" cy="66" rx="26" ry="22" fill="none" stroke="${metal}" stroke-width="6"/><ellipse cx="50" cy="66" rx="26" ry="22" fill="none" stroke="${metal2}" stroke-width="1.2" stroke-dasharray="2 3"/><path d="M38 44 L44 36 L56 36 L62 44" fill="${metal}" stroke="${metal2}"/>${stone(50, 30, 17)}`;
      break;
    case "Pendant":
      body = `<path d="M18 8 Q50 40 82 8" fill="none" stroke="${metal}" stroke-width="2" stroke-dasharray="3 2"/><circle cx="50" cy="30" r="4" fill="none" stroke="${metal}" stroke-width="2"/><path d="M50 34 L50 38" stroke="${metal}" stroke-width="2"/>${stone(50, 60, 22)}`;
      break;
    case "Earrings":
      body = `<path d="M30 10 q-6 8 0 14" fill="none" stroke="${metal}" stroke-width="2"/><path d="M70 10 q-6 8 0 14" fill="none" stroke="${metal}" stroke-width="2"/><line x1="30" y1="24" x2="30" y2="36" stroke="${metal}" stroke-width="2"/><line x1="70" y1="24" x2="70" y2="36" stroke="${metal}" stroke-width="2"/>${stone(30, 54, 15)}${stone(70, 54, 15)}`;
      break;
    case "Bracelet":
      body = `<ellipse cx="50" cy="55" rx="40" ry="26" fill="none" stroke="${metal}" stroke-width="3"/>${[
        [14, 50],
        [28, 33],
        [50, 29],
        [72, 33],
        [86, 50],
        [72, 77],
        [50, 81],
        [28, 77],
      ]
        .map((p) => stone(p[0], p[1], 8))
        .join("")}`;
      break;
    case "Necklace":
      body = `<path d="M10 10 Q50 75 90 10" fill="none" stroke="${metal}" stroke-width="2.5"/>${stone(28, 42, 7)}${stone(72, 42, 7)}${stone(39, 52, 8)}${stone(61, 52, 8)}${stone(50, 64, 12)}`;
      break;
    default:
      body = stone(50, 52, 34);
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="${size}" height="${size}" role="img" aria-label="${esc(gem)} ${esc(type || "")}">${defs}${body}</svg>`;
}

/** Just the drawing (for table rows and lists). */
export function GemSvg({
  gem,
  type,
  size = 96,
}: {
  gem: string | null | undefined;
  type?: string | null;
  size?: number;
}) {
  const id = "g" + useId().replace(/[^a-zA-Z0-9_-]/g, "");
  return (
    <span
      style={{ display: "inline-flex", lineHeight: 0 }}
      dangerouslySetInnerHTML={{ __html: gemSvg(gem || "Other", type, size, id) }}
    />
  );
}

/** Photo if there is one, otherwise the drawing, on the light round background. */
export function GemPicture({
  gem,
  type,
  photoUrl,
  size = 96,
  box,
  alt,
}: {
  gem: string | null | undefined;
  type?: string | null;
  photoUrl?: string | null;
  size?: number;
  box?: number;
  alt?: string;
}) {
  const b = box ?? size;
  return (
    <div className="gem-pic" style={{ width: b, height: b, overflow: "hidden" }}>
      {photoUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- photos come from the backend file store
        <img
          src={photoUrl}
          alt={alt ?? `${gem ?? "Gem"} photo`}
          style={{ width: "100%", height: "100%", objectFit: "cover" }}
        />
      ) : (
        <GemSvg gem={gem} type={type} size={size} />
      )}
    </div>
  );
}
