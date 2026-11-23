"use client";

/* Tables – prototype .table: filters above, empty message, totals row, Export PDF / Excel.
   On a phone, stackOnPhone turns each row into a small card ("Label: value"). */
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { todayInShop } from "@/lib/format";
import { cn } from "@/lib/utils";

export type Column<T> = {
  key: string;
  header: ReactNode;
  /** Text used on phones and in the Excel file when the header is not plain text. */
  label?: string;
  cell: (row: T, index: number) => ReactNode;
  /** Numbers: right aligned, never wrapped. */
  num?: boolean;
  className?: string;
};

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  empty = "Nothing to show yet.",
  footer,
  stackOnPhone,
  id,
  onRowClick,
  marked,
}: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T, index: number) => string;
  empty?: ReactNode;
  /** Totals row: one cell per column. */
  footer?: ReactNode[];
  stackOnPhone?: boolean;
  id?: string;
  onRowClick?: (row: T) => void;
  /** Marks a row, e.g. the one just saved (gold background). */
  marked?: (row: T) => boolean;
}) {
  if (!rows.length) return <div className="empty">{empty}</div>;
  return (
    <div className="table-wrap">
      <table className={cn("table", stackOnPhone && "stack-sm")} id={id}>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={cn(c.num && "num", c.className)}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr
              key={rowKey(r, i)}
              onClick={onRowClick ? () => onRowClick(r) : undefined}
              style={{
                cursor: onRowClick ? "pointer" : undefined,
                background: marked?.(r) ? "var(--gold-soft)" : undefined,
              }}
            >
              {columns.map((c) => (
                <td
                  key={c.key}
                  className={cn(c.num && "num", c.className)}
                  data-label={c.label ?? (typeof c.header === "string" ? c.header : c.key)}
                >
                  {c.cell(r, i)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
        {footer && (
          <tfoot>
            <tr>
              {footer.map((f, i) => (
                <td key={columns[i]?.key ?? i} className={cn(columns[i]?.num && "num")}>
                  {f}
                </td>
              ))}
            </tr>
          </tfoot>
        )}
      </table>
    </div>
  );
}

/** Row of filters above a table (search, dates, drop-downs). */
export function Filters({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("filters", className)}>{children}</div>;
}

/** The "📭 nothing here yet" card for a page with no data. */
export function EmptyState({ title, text, action }: { title: string; text: string; action?: ReactNode }) {
  return (
    <div className="card" style={{ maxWidth: 560, margin: "40px auto" }}>
      <div className="card-content center" style={{ padding: "40px 24px" }}>
        <div style={{ fontSize: 42, lineHeight: 1 }}>📭</div>
        <h2 style={{ margin: "12px 0 6px" }}>{title}</h2>
        <p className="muted" style={{ margin: "0 0 18px" }}>
          {text}
        </p>
        {action}
      </div>
    </div>
  );
}

/** Excel file (CSV that opens in Excel) of the table as shown on the screen. */
export function tableToCsv(table: HTMLTableElement) {
  const cell = (c: Element) => '"' + (c as HTMLElement).innerText.replace(/\s+/g, " ").trim().replace(/"/g, '""') + '"';
  return Array.from(table.querySelectorAll("tr"))
    .map((tr) => Array.from(tr.querySelectorAll("th,td")).map(cell).join(","))
    .join("\r\n");
}

export const exportFileName = (name: string, ext: string) => `${name.replace(/\s+/g, "_")}_${todayInShop()}.${ext}`;

/** ⇩ Export PDF · ⇩ Export Excel. Pass onPdf / onExcel to use the backend file; otherwise PDF = print, Excel = the table on screen. */
export function ExportButtons({
  tableId,
  name,
  onPdf,
  onExcel,
}: {
  tableId?: string;
  name: string;
  onPdf?: () => void;
  onExcel?: () => void;
}) {
  const toast = useToast();
  const pdf = () => {
    if (onPdf) return onPdf();
    toast("Preparing PDF", `${name} – choose "Save as PDF" in the print window.`, "success");
    setTimeout(() => window.print(), 400);
  };
  const excel = () => {
    if (onExcel) return onExcel();
    const table = tableId ? (document.getElementById(tableId) as HTMLTableElement | null) : null;
    if (!table) {
      toast("Nothing to export", undefined, "error");
      return;
    }
    const blob = new Blob(["﻿" + tableToCsv(table)], { type: "text/csv;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = exportFileName(name, "csv");
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(a.href);
    toast("Excel export ready", `${a.download} downloaded.`, "success");
  };
  return (
    <>
      <Button variant="outline" onClick={pdf}>
        ⇩ Export PDF
      </Button>
      <Button variant="outline" onClick={excel}>
        ⇩ Export Excel
      </Button>
    </>
  );
}
