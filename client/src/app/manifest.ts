import type { MetadataRoute } from "next";

/* Web app manifest (P4-02): the system can be added to the phone's home screen and opens like an app – full screen,
   the shop's navy colour and the gem logo. Push alerts need this on iPhone (iOS 16.4 or later). */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Gem Shop System",
    short_name: "Gem Shop",
    description: "Sales, stock and money system for the gem shop.",
    id: "/",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#13213f",
    theme_color: "#13213f",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icons/maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
    shortcuts: [
      { name: "Phone view", url: "/approvals/phone" },
      { name: "Approvals", url: "/approvals" },
      { name: "Alerts", url: "/approvals/alerts" },
    ],
  };
}
