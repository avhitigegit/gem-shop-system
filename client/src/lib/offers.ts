/* Offers (P4-05, BRD J2, J3, J4, J6): types and the picture helpers. The gem drawing is turned into a PNG so it can go
   in the email / WhatsApp message; an uploaded photo is sent as it is (PNG / JPEG, up to 2 MB). */
import type { components } from "@/lib/api/schema";
import { gemSvg } from "@/components/ui/gem-picture";

export type OfferOptions = components["schemas"]["OfferOptions"];
export type OfferForm = components["schemas"]["OfferForm"];
export type OfferRow = components["schemas"]["OfferRow"];
export type OfferAudience = components["schemas"]["OfferAudience"];

/** The gem pictures to choose from (prototype email-campaign). */
export const OFFER_PICTURES = ["Blue Sapphire", "Padparadscha", "Pink Sapphire", "Moonstone", "Ruby", "Cat's Eye"];

export const MAX_PHOTO = 2 * 1024 * 1024;

/** The gem drawing as a PNG data URL (600 × 300, light round background). */
export async function gemPng(gem: string): Promise<string> {
  const w = 600;
  const h = 300;
  const svg = gemSvg(gem, "Loose gem", 220, "offerPic").replace("<svg", '<svg xmlns="http://www.w3.org/2000/svg"');
  const img = new Image();
  img.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
  await img.decode();
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext("2d")!;
  const bg = ctx.createRadialGradient(w / 2, h / 2, 10, w / 2, h / 2, w / 2);
  bg.addColorStop(0, "#ffffff");
  bg.addColorStop(1, "#e8eefa");
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, w, h);
  ctx.drawImage(img, (w - 220) / 2, (h - 220) / 2, 220, 220);
  return canvas.toDataURL("image/png");
}

/** A chosen photo file as a data URL, or an error message. */
export function photoDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    if (!/^image\/(png|jpeg)$/.test(file.type)) return reject(new Error("Choose a PNG or JPEG photo."));
    if (file.size > MAX_PHOTO) return reject(new Error("The photo is too large (2 MB at most)."));
    const r = new FileReader();
    r.onload = () => resolve(String(r.result));
    r.onerror = () => reject(new Error("The photo could not be read."));
    r.readAsDataURL(file);
  });
}

export const pct = (a: number, b: number) => (b ? `${Math.round((a / b) * 100)}%` : "–");
