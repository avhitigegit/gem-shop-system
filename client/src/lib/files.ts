"use client";

/* Uploads and downloads (P1-05).
   - uploadFile: sends a photo / PDF from a photo or scan box. The backend checks the type, makes photos about
     300 KB with a thumbnail, and answers with private links (url, thumbUrl) that work for about an hour.
     Keep the returned id and send it with the form (gem, expense, …) – unsaved uploads are removed after a day.
   - downloadFile: "Export PDF" / "Export Excel" buttons and invoice PDFs. These need the login token, so a plain
     <a href> does not work; this fetches the file and saves it under the name the backend gives. */
import { api } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { authFetch } from "@/lib/auth";

export type FileKind = components["schemas"]["FileView"]["kind"];
export type FileView = components["schemas"]["FileView"];

/** Biggest upload the backend takes. Checked here too, so the person gets the message before waiting. */
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

/** What the file box accepts (the backend checks the real type again). */
export const ACCEPT: Record<FileKind, string> = {
  PRODUCT_PHOTO: "image/jpeg,image/png,image/webp",
  LOGO: "image/jpeg,image/png,image/webp",
  BILL_PHOTO: "image/jpeg,image/png,image/webp,application/pdf",
  CERTIFICATE: "image/jpeg,image/png,image/webp,application/pdf",
};

export async function uploadFile(file: File, kind: FileKind) {
  if (file.size > MAX_UPLOAD_BYTES) {
    return {
      data: undefined,
      error: { code: "FILE_TOO_LARGE", message: "The file is too large. The limit is 10 MB." },
    };
  }
  const form = new FormData();
  form.append("file", file);
  return api.POST("/api/files", {
    params: { query: { kind } },
    // the browser sets the multipart boundary itself
    body: form as unknown as { file: string },
    bodySerializer: (b) => b as unknown as FormData,
  });
}

/** Remove an upload before the form is saved ("✕" on the photo). */
export const discardFile = (id: string) => api.DELETE("/api/files/{id}", { params: { path: { id } } });

/** Fetches a file from the API with the login token and saves it, e.g. downloadFile("/api/activity-log/export?format=pdf"). */
export async function downloadFile(path: string, fallbackName = "download"): Promise<boolean> {
  const res = await authFetch(new Request(path));
  if (!res.ok) return false;
  const blob = await res.blob();
  const name = fileName(res.headers.get("Content-Disposition")) ?? fallbackName;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
  return true;
}

/** File name from Content-Disposition (filename*=UTF-8''… first, then filename="…"). */
export function fileName(disposition: string | null): string | null {
  if (!disposition) return null;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (star) return decodeURIComponent(star[1]);
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain ? plain[1] : null;
}
