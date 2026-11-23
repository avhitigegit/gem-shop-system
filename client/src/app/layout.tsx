import type { Metadata, Viewport } from "next";
import { Providers } from "./providers";
import "./globals.css";

// Fonts: the same list as the prototype (prototype/assets/css/app.css) – "Inter", then the computer's own
// font (Segoe UI on Windows). No web font is downloaded, so it looks exactly like the approved prototype.

export const metadata: Metadata = {
  title: {
    default: "Gem Shop System",
    template: "%s · Gem Shop System",
  },
  description: "Sales, stock and money system for the gem shop.",
  icons: { icon: "/logo.svg", apple: "/icons/apple-touch-icon.png" },
  // Added to the phone's home screen it opens full screen, like an app (P4-02).
  appleWebApp: { capable: true, title: "Gem Shop", statusBarStyle: "black-translucent" },
};

export const viewport: Viewport = {
  themeColor: "#13213f",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="antialiased">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
