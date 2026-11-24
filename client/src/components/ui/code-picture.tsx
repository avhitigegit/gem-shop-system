"use client";

/* QR code and Code 39 barcode pictures from the backend (GET /api/codes/qr, /api/codes/barcode – ZXing).
   The pictures need the login token, so they are fetched and shown from a local blob link. */
import { useEffect, useState } from "react";
import { authFetch } from "@/lib/auth";

function useCodeImage(path: string) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let live = true;
    let made: string | null = null;
    void authFetch(new Request(path))
      .then((r) => (r.ok ? r.blob() : null))
      .then((b) => {
        if (!live || !b) return;
        made = URL.createObjectURL(b);
        setUrl(made);
      })
      .catch(() => undefined);
    return () => {
      live = false;
      if (made) URL.revokeObjectURL(made);
    };
  }, [path]);
  return url;
}

export function QrCode({ text, size = 84 }: { text: string; size?: number }) {
  const url = useCodeImage(`/api/codes/qr?size=${Math.max(120, size * 3)}&text=${encodeURIComponent(text)}`);
  return url ? (
    // eslint-disable-next-line @next/next/no-img-element -- picture made by the backend
    <img
      src={url}
      loading="lazy"
      alt={`QR code ${text}`}
      width={size}
      height={size}
      style={{ imageRendering: "pixelated" }}
    />
  ) : (
    <span style={{ display: "inline-block", width: size, height: size }} aria-hidden="true" />
  );
}

export function Barcode({ text, height = 42 }: { text: string; height?: number }) {
  const url = useCodeImage(
    `/api/codes/barcode?width=600&height=${Math.max(40, height * 2)}&text=${encodeURIComponent(text)}`,
  );
  return url ? (
    // eslint-disable-next-line @next/next/no-img-element -- picture made by the backend
    <img
      src={url}
      loading="lazy"
      alt={`Barcode ${text}`}
      style={{ width: "100%", height, objectFit: "fill", display: "block" }}
    />
  ) : (
    <span style={{ display: "block", height }} aria-hidden="true" />
  );
}
