"use client";

/* Photo upload box – prototype .upload: dashed box, click (or tap on a phone to use the camera),
   shows ✔ and a preview when done. Big photos are made smaller in the browser first (about 300 KB),
   so uploads are quick on the shop's internet. The page sends the file (P1-05 file storage). */
import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";

export const MAX_SIDE = 1600;
export const TARGET_BYTES = 300 * 1024;

/** Makes a photo smaller (longest side 1600 px, JPEG) when it is bigger than about 300 KB. Other files are kept. */
export async function shrinkImage(file: File): Promise<File> {
  if (!file.type.startsWith("image/") || file.type === "image/gif" || file.size <= TARGET_BYTES) return file;
  const bitmap = await createImageBitmap(file).catch(() => null);
  if (!bitmap) return file;
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  let quality = 0.85;
  let blob: Blob | null = null;
  while (quality >= 0.5) {
    blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, "image/jpeg", quality));
    if (!blob || blob.size <= TARGET_BYTES) break;
    quality -= 0.1;
  }
  if (!blob || blob.size >= file.size) return file;
  return new File([blob], file.name.replace(/\.[^.]+$/, "") + ".jpg", { type: "image/jpeg", lastModified: Date.now() });
}

export function UploadBox({
  onFiles,
  accept = "image/*,.pdf",
  multiple,
  capture,
  done,
  children = "⇪ Take a photo / upload",
  className,
  id,
}: {
  onFiles: (files: File[]) => void;
  accept?: string;
  multiple?: boolean;
  /** "environment" opens the back camera on a phone. */
  capture?: "environment" | "user";
  /** Name of a file already attached (e.g. when editing). */
  done?: string | null;
  children?: ReactNode;
  className?: string;
  id?: string;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [picked, setPicked] = useState<{ label: string; preview: string | null } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => () => void (picked?.preview && URL.revokeObjectURL(picked.preview)), [picked]);

  const choose = async (list: FileList | null) => {
    const files = Array.from(list ?? []);
    if (!files.length) return;
    setBusy(true);
    const ready = await Promise.all(files.map(shrinkImage));
    setBusy(false);
    const first = ready[0];
    setPicked({
      label: ready.length > 1 ? `${ready.length} files selected` : first.name,
      preview: first.type.startsWith("image/") ? URL.createObjectURL(first) : null,
    });
    onFiles(ready);
    if (input.current) input.current.value = "";
  };

  const label = picked?.label ?? done;
  return (
    <>
      <div
        id={id}
        role="button"
        tabIndex={0}
        className={cn("upload", label && "done", className)}
        onClick={() => input.current?.click()}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            input.current?.click();
          }
        }}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          void choose(e.dataTransfer.files);
        }}
      >
        {busy ? (
          "Preparing the photo…"
        ) : label ? (
          <>
            ✔ {label}
            <br />
            <small>Made smaller (about 300 KB) before upload</small>
            {picked?.preview ? (
              // eslint-disable-next-line @next/next/no-img-element -- local preview of the chosen photo
              <img src={picked.preview} alt="" />
            ) : null}
          </>
        ) : (
          children
        )}
      </div>
      <input
        ref={input}
        type="file"
        accept={accept}
        multiple={multiple}
        capture={capture}
        hidden
        onChange={(e) => void choose(e.target.files)}
      />
    </>
  );
}
