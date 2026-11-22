import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

// Same font as the prototype (prototype/assets/css/app.css). Full theme comes in P1-03.
const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: {
    default: "Gem Shop System",
    template: "%s · Gem Shop System",
  },
  description: "Sales, stock and money system for the gem shop.",
};

export const viewport: Viewport = {
  themeColor: "#13213f",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${inter.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col">{children}</body>
    </html>
  );
}
